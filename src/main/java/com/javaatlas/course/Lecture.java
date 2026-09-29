package com.javaatlas.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One lesson inside a course. Content is Markdown-style text (headings with ##, "- " bullets,
 * `code` and ``` fenced code blocks). videoUrl can be YouTube, Vimeo, Bunny Stream or an .mp4 link.
 */
@Entity
@Table(name = "lecture")
public class Lecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id")
    private CourseSection section;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "free_preview", nullable = false)
    private boolean freePreview;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(nullable = false)
    private String content = "";

    protected Lecture() {
    }

    public static Lecture of(String title, int durationMin, boolean freePreview, String content) {
        Lecture l = new Lecture();
        l.update(title, durationMin, freePreview, null, content);
        return l;
    }

    void attach(CourseSection section, int sortOrder) {
        this.section = section;
        this.sortOrder = sortOrder;
    }

    void update(String title, int durationMin, boolean freePreview, String videoUrl, String content) {
        this.title = title;
        this.durationMin = durationMin;
        this.freePreview = freePreview;
        this.videoUrl = videoUrl == null || videoUrl.isBlank() ? null : videoUrl.trim();
        this.content = content == null ? "" : content;
    }

    public Long getId() { return id; }
    public CourseSection getSection() { return section; }
    public int getSortOrder() { return sortOrder; }
    public String getTitle() { return title; }
    public int getDurationMin() { return durationMin; }
    public boolean isFreePreview() { return freePreview; }
    public String getVideoUrl() { return videoUrl; }
    public String getContent() { return content; }
}
