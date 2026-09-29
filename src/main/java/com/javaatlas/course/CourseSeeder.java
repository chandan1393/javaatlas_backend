package com.javaatlas.course;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.javaatlas.AppProperties;

/** Adds the starter courses the first time the app starts with an empty course table. */
@Component
public class CourseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CourseSeeder.class);

    private final CourseRepository courses;
    private final AppProperties props;

    public CourseSeeder(CourseRepository courses, AppProperties props) {
        this.courses = courses;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.seedCourses() && courses.count() == 0) {
            courses.saveAll(SeedCourses.all());
            log.info("Added {} starter courses. Edit or unpublish them from /admin.", courses.count());
        }
    }
}
