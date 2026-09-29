package com.javaatlas.course;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LectureProgressRepository extends JpaRepository<LectureProgress, Long> {

    boolean existsByUserIdAndLectureId(Long userId, Long lectureId);

    long deleteByUserIdAndLectureId(Long userId, Long lectureId);

    @Query("select p.lectureId from LectureProgress p where p.userId = :userId and p.lectureId in :lectureIds")
    List<Long> findCompletedLectureIds(@Param("userId") Long userId, @Param("lectureIds") Collection<Long> lectureIds);
}
