package com.javaatlas.payment;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javaatlas.course.CourseDtos.MyCourse;
import com.javaatlas.course.CourseDtos.Order;
import com.javaatlas.course.CourseService;
import com.javaatlas.security.Session;

/** The signed-in learner's courses, orders and lecture progress. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    public record MyEnrollments(List<String> courseIds) {
    }

    public record ProgressRequest(boolean done) {
    }

    private final CourseService courses;

    public MeController(CourseService courses) {
        this.courses = courses;
    }

    @GetMapping("/enrollments")
    public MyEnrollments enrollments(@AuthenticationPrincipal Jwt jwt) {
        return new MyEnrollments(courses.enrolledSlugs(Session.of(jwt).userId()));
    }

    @GetMapping("/courses")
    public List<MyCourse> myCourses(@AuthenticationPrincipal Jwt jwt) {
        return courses.myCourses(Session.of(jwt).userId());
    }

    @GetMapping("/orders")
    public List<Order> orders(@AuthenticationPrincipal Jwt jwt) {
        return courses.orders(Session.of(jwt).userId());
    }

    @PostMapping("/lectures/{lectureId}/progress")
    public ProgressRequest progress(@PathVariable long lectureId, @RequestBody ProgressRequest req, @AuthenticationPrincipal Jwt jwt) {
        return new ProgressRequest(courses.setProgress(Session.of(jwt), lectureId, req.done()));
    }
}
