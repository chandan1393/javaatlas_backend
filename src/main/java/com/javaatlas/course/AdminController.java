package com.javaatlas.course;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javaatlas.course.CourseDtos.AdminCourse;
import com.javaatlas.course.CourseDtos.AdminOrders;

import jakarta.validation.Valid;

/** Admin API: role ADMIN, and two-factor sign-in when ADMIN_REQUIRE_2FA is on (see SessionValidationFilter). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final CourseAdminService admin;

    public AdminController(CourseAdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/courses")
    public List<AdminCourse> courses() {
        return admin.list();
    }

    @GetMapping("/courses/{id}")
    public AdminCourse course(@PathVariable long id) {
        return admin.get(id);
    }

    @PostMapping("/courses")
    public AdminCourse create(@Valid @RequestBody AdminCourse course, @AuthenticationPrincipal Jwt jwt) {
        log.info("Admin {} created course {}", jwt.getClaimAsString("email"), course.slug());
        return admin.create(course);
    }

    @PutMapping("/courses/{id}")
    public AdminCourse update(@PathVariable long id, @Valid @RequestBody AdminCourse course, @AuthenticationPrincipal Jwt jwt) {
        log.info("Admin {} updated course {} ({})", jwt.getClaimAsString("email"), id, course.slug());
        return admin.update(id, course);
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        log.warn("Admin {} deleted course {}", jwt.getClaimAsString("email"), id);
        admin.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders")
    public AdminOrders orders() {
        return admin.orders();
    }
}
