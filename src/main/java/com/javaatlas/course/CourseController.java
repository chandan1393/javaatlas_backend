package com.javaatlas.course;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javaatlas.AppProperties;
import com.javaatlas.course.CourseDtos.CourseDetail;
import com.javaatlas.course.CourseDtos.CourseSummary;
import com.javaatlas.course.CourseDtos.LectureView;
import com.javaatlas.security.SessionReader;

import jakarta.servlet.http.HttpServletRequest;

/** Public course pages. Signed-in learners who own a course also get its locked lectures. */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    public record Catalog(boolean checkout, List<CourseSummary> courses) {
    }

    private final CourseService service;
    private final SessionReader sessions;
    private final AppProperties props;

    public CourseController(CourseService service, SessionReader sessions, AppProperties props) {
        this.service = service;
        this.sessions = sessions;
        this.props = props;
    }

    @GetMapping
    public Catalog catalog() {
        return new Catalog(props.razorpay().enabled(), service.catalog());
    }

    @GetMapping("/{slug}")
    public CourseDetail detail(@PathVariable String slug, HttpServletRequest request) {
        return service.detail(slug, sessions.read(request));
    }

    @GetMapping("/{slug}/lectures/{lectureId}")
    public LectureView lecture(@PathVariable String slug, @PathVariable long lectureId, HttpServletRequest request) {
        return service.lecture(slug, lectureId, sessions.read(request));
    }
}
