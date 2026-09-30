package com.javaatlas.course;

import com.javaatlas.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin -> Course templates: ready-made course outlines (see CourseCatalog) that can be added as drafts.
 * Under /api/admin, so only a signed-in admin (with two-factor sign-in when required) can use it.
 */
@RestController
@RequestMapping("/api/admin/course-templates")
public class CourseTemplateController {

    private static final Logger log = LoggerFactory.getLogger(CourseTemplateController.class);

    private final CourseRepository courses;

    public CourseTemplateController(CourseRepository courses) {
        this.courses = courses;
    }

    public record TemplateSummary(String slug, String title, String subtitle, String level, int priceInr,
                                  int sections, int lectures, int minutes, int freePreviews, List<String> outcomes,
                                  Long existingCourseId) {
    }

    public record Added(long id, String slug) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TemplateSummary> list() {
        return CourseCatalog.all().stream().map(c -> {
            List<Lecture> lectures = c.getSections().stream().flatMap(s -> s.getLectures().stream()).toList();
            return new TemplateSummary(c.getSlug(), c.getTitle(), c.getSubtitle(), c.getLevel(), c.getPriceInr(),
                    c.getSections().size(), lectures.size(),
                    lectures.stream().mapToInt(Lecture::getDurationMin).sum(),
                    (int) lectures.stream().filter(Lecture::isFreePreview).count(),
                    c.outcomeList(),
                    courses.findBySlug(c.getSlug()).map(Course::getId).orElse(null));
        }).toList();
    }

    @PostMapping("/{slug}")
    @Transactional
    public Added add(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        Course template = CourseCatalog.all().stream()
                .filter(c -> c.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "There's no course template with that name."));
        if (courses.findBySlug(slug).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "exists",
                    "A course with the URL name \"" + slug + "\" already exists. Open it from Content, or rename it first.");
        }
        Course saved = courses.save(template);          // saved as an unpublished draft, with all sections and lectures
        log.info("Admin {} added course template {} as draft course {}", jwt.getClaimAsString("email"), slug, saved.getId());
        return new Added(saved.getId(), saved.getSlug());
    }
}
