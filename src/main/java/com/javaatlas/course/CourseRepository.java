package com.javaatlas.course;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findBySlug(String slug);

    List<Course> findByPublishedTrueOrderBySortOrderAscIdAsc();

    List<Course> findAllByOrderBySortOrderAscIdAsc();
}
