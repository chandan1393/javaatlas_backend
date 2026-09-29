package com.javaatlas.course;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javaatlas.common.ApiException;
import com.javaatlas.course.CourseDtos.CourseDetail;
import com.javaatlas.course.CourseDtos.CourseSummary;
import com.javaatlas.course.CourseDtos.LectureItem;
import com.javaatlas.course.CourseDtos.LectureView;
import com.javaatlas.course.CourseDtos.MyCourse;
import com.javaatlas.course.CourseDtos.Order;
import com.javaatlas.course.CourseDtos.SectionItem;
import com.javaatlas.payment.Enrollment;
import com.javaatlas.payment.EnrollmentRepository;
import com.javaatlas.security.Session;

/** Catalog, course pages, lecture access and learner progress. */
@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courses;
    private final LectureRepository lectures;
    private final EnrollmentRepository enrollments;
    private final LectureProgressRepository progress;

    public CourseService(CourseRepository courses, LectureRepository lectures, EnrollmentRepository enrollments,
                         LectureProgressRepository progress, com.javaatlas.video.BunnyStreamService video) {
        this.video = video;
        this.courses = courses;
        this.lectures = lectures;
        this.enrollments = enrollments;
        this.progress = progress;
    }

    private final com.javaatlas.video.BunnyStreamService video;

    public List<CourseSummary> catalog() {
        return courses.findByPublishedTrueOrderBySortOrderAscIdAsc().stream().map(CourseService::summary).toList();
    }

    public CourseDetail detail(String slug, Optional<Session> session) {
        Course course = visible(slug, session);
        boolean enrolled = session.map(s -> hasAccess(s, course)).orElse(false);
        List<SectionItem> sections = course.getSections().stream()
                .map(s -> new SectionItem(s.getTitle(), s.getLectures().stream()
                        .map(l -> new LectureItem(l.getId(), l.getTitle(), l.getDurationMin(), l.isFreePreview(), l.getVideoUrl() != null))
                        .toList()))
                .toList();
        List<Long> completed = enrolled ? completedIds(session.get().userId(), course) : List.of();
        return new CourseDetail(course.getSlug(), course.getTitle(), course.getSubtitle(), course.getDescription(),
                course.getLevel(), course.getPriceInr(), course.allLectures().size(), course.totalMinutes(),
                course.outcomeList(), sections, enrolled, course.isPublished(), completed,
                video.playbackUrl(course.getTrailerUrl()), course.videoCount(), course.videoMinutes());
    }

    public LectureView lecture(String slug, long lectureId, Optional<Session> session) {
        Course course = visible(slug, session);
        List<Lecture> all = course.allLectures();
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId() == lectureId) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "lecture_not_found", "That lecture doesn’t exist.");
        }
        Lecture lecture = all.get(index);
        boolean enrolled = session.map(s -> hasAccess(s, course)).orElse(false);
        if (!lecture.isFreePreview() && !enrolled) {
            throw new ApiException(HttpStatus.FORBIDDEN, session.isPresent() ? "locked" : "login_required",
                    "Enroll in this course to open this lecture.");
        }
        boolean completed = enrolled && progress.existsByUserIdAndLectureId(session.get().userId(), lecture.getId());
        Long prev = index > 0 ? all.get(index - 1).getId() : null;
        Long next = index < all.size() - 1 ? all.get(index + 1).getId() : null;
        return new LectureView(lecture.getId(), lecture.getTitle(), lecture.getSection().getTitle(), lecture.getDurationMin(),
                lecture.isFreePreview(), video.playbackUrl(lecture.getVideoUrl()), lecture.getContent(), prev, next, completed);
    }

    public List<MyCourse> myCourses(long userId) {
        Map<Long, Course> owned = new LinkedHashMap<>();
        for (Enrollment e : enrollments.findByUserIdAndStatusOrderByPaidAtDesc(userId, Enrollment.Status.PAID)) {
            owned.putIfAbsent(e.getCourse().getId(), e.getCourse());
        }
        return owned.values().stream().map(c -> {
            List<Lecture> all = c.allLectures();
            Set<Long> done = new HashSet<>(completedIds(userId, c));
            Long next = all.stream().filter(l -> !done.contains(l.getId())).findFirst()
                    .or(() -> all.stream().findFirst()).map(Lecture::getId).orElse(null);
            return new MyCourse(c.getSlug(), c.getTitle(), c.getSubtitle(), all.size(), done.size(), next);
        }).toList();
    }

    public List<String> enrolledSlugs(long userId) {
        return enrollments.findByUserIdAndStatusOrderByPaidAtDesc(userId, Enrollment.Status.PAID).stream()
                .map(e -> e.getCourse().getSlug()).distinct().toList();
    }

    public List<Order> orders(long userId) {
        return enrollments.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(e -> new Order(e.getRazorpayOrderId(), e.getCourse().getSlug(), e.getCourse().getTitle(), e.getAmountPaise(),
                        e.getStatus().name(), e.getCreatedAt(), e.getPaidAt(), e.getRazorpayPaymentId()))
                .toList();
    }

    @Transactional
    public boolean setProgress(Session session, long lectureId, boolean done) {
        Lecture lecture = lectures.findById(lectureId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "lecture_not_found", "That lecture doesn’t exist."));
        if (!hasAccess(session, lecture.getSection().getCourse())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "locked", "Enroll in this course to track progress.");
        }
        if (done) {
            if (!progress.existsByUserIdAndLectureId(session.userId(), lectureId)) {
                try {
                    progress.saveAndFlush(new LectureProgress(session.userId(), lectureId));
                } catch (DataIntegrityViolationException e) {
                    // already marked by a parallel request
                }
            }
        } else {
            progress.deleteByUserIdAndLectureId(session.userId(), lectureId);
        }
        return done;
    }

    private boolean hasAccess(Session session, Course course) {
        return session.admin() || enrollments.existsByUserIdAndCourse_IdAndStatus(session.userId(), course.getId(), Enrollment.Status.PAID);
    }

    private List<Long> completedIds(long userId, Course course) {
        List<Long> ids = course.allLectures().stream().map(Lecture::getId).toList();
        return ids.isEmpty() ? List.of() : progress.findCompletedLectureIds(userId, ids);
    }

    private Course visible(String slug, Optional<Session> session) {
        Course course = courses.findBySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "That course doesn’t exist."));
        if (!course.isPublished() && !session.map(Session::admin).orElse(false)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "That course doesn’t exist.");
        }
        return course;
    }

    static CourseSummary summary(Course c) {
        return new CourseSummary(c.getSlug(), c.getTitle(), c.getSubtitle(), c.getLevel(), c.getPriceInr(),
                c.allLectures().size(), c.totalMinutes(), c.outcomeList(), c.videoCount(), c.videoMinutes());
    }
}
