package com.javaatlas.course;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javaatlas.common.ApiException;
import com.javaatlas.course.CourseDtos.AdminCourse;
import com.javaatlas.course.CourseDtos.AdminLecture;
import com.javaatlas.course.CourseDtos.AdminOrder;
import com.javaatlas.course.CourseDtos.AdminOrders;
import com.javaatlas.course.CourseDtos.AdminSection;
import com.javaatlas.payment.Enrollment;
import com.javaatlas.payment.EnrollmentRepository;
import com.javaatlas.user.AppUser;
import com.javaatlas.user.UserRepository;

/**
 * Creates and edits courses from the admin screen. Sections and lectures are saved as a whole:
 * items with an id are updated, items without one are added, and missing ones are deleted.
 * Lecture ids stay the same when you edit, so learners keep their progress.
 */
@Service
@Transactional
public class CourseAdminService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;

    public CourseAdminService(CourseRepository courses, EnrollmentRepository enrollments, UserRepository users) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AdminCourse> list() {
        return courses.findAllByOrderBySortOrderAscIdAsc().stream().map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public AdminCourse get(long id) {
        return toAdmin(find(id));
    }

    public AdminCourse create(AdminCourse in) {
        Course course = Course.create(in.slug());
        apply(course, in);
        return toAdmin(courses.saveAndFlush(course));
    }

    public AdminCourse update(long id, AdminCourse in) {
        Course course = find(id);
        apply(course, in);
        return toAdmin(courses.saveAndFlush(course));
    }

    public void delete(long id) {
        Course course = find(id);
        if (enrollments.existsByCourse_Id(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "has_orders",
                    "This course has orders, so it can’t be deleted. Unpublish it instead to hide it.");
        }
        courses.delete(course);
    }

    @Transactional(readOnly = true)
    public AdminOrders orders() {
        List<Enrollment> recent = enrollments.findTop200ByOrderByCreatedAtDesc();
        Map<Long, String> emails = users.findAllById(recent.stream().map(Enrollment::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getEmail));
        List<AdminOrder> rows = recent.stream()
                .map(e -> new AdminOrder(e.getRazorpayOrderId(), emails.getOrDefault(e.getUserId(), "(deleted user)"),
                        e.getCourse().getTitle(), e.getAmountPaise(), e.getStatus().name(), e.getCreatedAt(), e.getPaidAt(),
                        e.getRazorpayPaymentId()))
                .toList();
        long revenue = Objects.requireNonNullElse(enrollments.sumAmountPaiseByStatus(Enrollment.Status.PAID), 0L);
        return new AdminOrders(enrollments.countByStatus(Enrollment.Status.PAID), revenue, rows);
    }

    private void apply(Course course, AdminCourse in) {
        courses.findBySlug(in.slug())
                .filter(other -> !other.getId().equals(course.getId()))
                .ifPresent(other -> {
                    throw new ApiException(HttpStatus.CONFLICT, "slug_taken", "Another course already uses the URL name “" + in.slug() + "”.");
                });
        course.update(in.slug(), in.title().trim(), in.subtitle(), in.description(), in.outcomes(), in.level(),
                in.priceInr(), in.published(), in.sortOrder());
        course.setTrailerUrl(in.trailerUrl());

        Map<Long, CourseSection> oldSections = course.getSections().stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(CourseSection::getId, Function.identity()));
        List<CourseSection> newSections = new ArrayList<>();
        for (AdminSection s : in.sections()) {
            CourseSection section = s.id() == null ? null : oldSections.remove(s.id());
            if (section == null) {
                section = new CourseSection(course, s.title().trim(), newSections.size());
            } else {
                section.update(s.title().trim(), newSections.size());
            }
            Map<Long, Lecture> oldLectures = new HashMap<>();
            for (Lecture l : section.getLectures()) {
                if (l.getId() != null) {
                    oldLectures.put(l.getId(), l);
                }
            }
            List<Lecture> newLectures = new ArrayList<>();
            for (AdminLecture a : s.lectures()) {
                Lecture lecture = a.id() == null ? null : oldLectures.remove(a.id());
                if (lecture == null) {
                    lecture = Lecture.of(a.title().trim(), a.durationMin(), a.freePreview(), a.content());
                }
                lecture.update(a.title().trim(), a.durationMin(), a.freePreview(), a.videoUrl(), a.content());
                lecture.attach(section, newLectures.size());
                newLectures.add(lecture);
            }
            section.getLectures().clear();
            section.getLectures().addAll(newLectures);
            newSections.add(section);
        }
        course.getSections().clear();
        course.getSections().addAll(newSections);
    }

    private Course find(long id) {
        return courses.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "That course doesn’t exist."));
    }

    private AdminCourse toAdmin(Course c) {
        List<AdminSection> sections = c.getSections().stream()
                .map(s -> new AdminSection(s.getId(), s.getTitle(), s.getLectures().stream()
                        .map(l -> new AdminLecture(l.getId(), l.getTitle(), l.getDurationMin(), l.isFreePreview(), l.getVideoUrl(), l.getContent()))
                        .toList()))
                .toList();
        return new AdminCourse(c.getId(), c.getSlug(), c.getTitle(), c.getSubtitle(), c.getDescription(), c.outcomeList(), c.getLevel(),
                c.getPriceInr(), c.isPublished(), c.getSortOrder(), sections, c.getTrailerUrl(),
                enrollments.countByCourse_IdAndStatus(c.getId(), Enrollment.Status.PAID));
    }
}
