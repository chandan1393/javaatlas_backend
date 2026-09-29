package com.javaatlas.course;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A lecture the learner marked as complete. */
@Entity
@Table(name = "lecture_progress")
public class LectureProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lecture_id", nullable = false)
    private Long lectureId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected LectureProgress() {
    }

    public LectureProgress(Long userId, Long lectureId) {
        this.userId = userId;
        this.lectureId = lectureId;
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getLectureId() { return lectureId; }
    public Instant getCompletedAt() { return completedAt; }
}
