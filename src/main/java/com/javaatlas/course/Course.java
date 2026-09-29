package com.javaatlas.course;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** A paid (or free) course: sections, each with ordered lectures. */
@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 300)
    private String subtitle = "";

    @Column(nullable = false)
    private String description = "";

    /** "What you'll learn", one item per line. */
    @Column(nullable = false)
    private String outcomes = "";

    @Column(nullable = false, length = 20)
    private String level = "Beginner";

    @Column(name = "price_inr", nullable = false)
    private int priceInr;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** A public intro video shown on the course page (YouTube/Vimeo/.mp4 link or "bunny:<video id>"). */
    @Column(name = "trailer_url", length = 500)
    private String trailerUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<CourseSection> sections = new ArrayList<>();

    protected Course() {
    }

    public static Course create(String slug) {
        Course c = new Course();
        c.slug = slug;
        return c;
    }

    public void update(String slug, String title, String subtitle, String description, List<String> outcomes,
                       String level, int priceInr, boolean published, int sortOrder) {
        this.slug = slug;
        this.title = title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.description = description == null ? "" : description;
        this.outcomes = outcomes == null ? "" : String.join("\n", outcomes.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
        this.level = level;
        this.priceInr = priceInr;
        this.published = published;
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }

    /** Used by the starter-course seeder. */
    Course addSection(String sectionTitle, Lecture... lectures) {
        CourseSection section = new CourseSection(this, sectionTitle, sections.size());
        for (Lecture l : lectures) {
            section.add(l);
        }
        sections.add(section);
        return this;
    }

    public List<String> outcomeList() {
        return Arrays.stream(outcomes.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public List<Lecture> allLectures() {
        return sections.stream().flatMap(s -> s.getLectures().stream()).toList();
    }

    public int totalMinutes() {
        return allLectures().stream().mapToInt(Lecture::getDurationMin).sum();
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getDescription() { return description; }
    public String getLevel() { return level; }
    public int getPriceInr() { return priceInr; }
    public boolean isPublished() { return published; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CourseSection> getSections() { return sections; }
    public String getTrailerUrl() { return trailerUrl; }

    public void setTrailerUrl(String trailerUrl) {
        this.trailerUrl = trailerUrl == null || trailerUrl.isBlank() ? null : trailerUrl.trim();
    }

    public int videoCount() {
        return (int) allLectures().stream().filter(l -> l.getVideoUrl() != null).count();
    }

    public int videoMinutes() {
        return allLectures().stream().filter(l -> l.getVideoUrl() != null).mapToInt(Lecture::getDurationMin).sum();
    }
}
