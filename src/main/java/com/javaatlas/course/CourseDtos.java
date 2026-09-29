package com.javaatlas.course;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** JSON shapes sent to and from the website. */
public final class CourseDtos {

    private CourseDtos() {
    }

    /** A lecture video: an https link (YouTube, Vimeo, Bunny, .mp4) or "bunny:<video id>" for a Bunny Stream upload. */
    public static final String VIDEO_PATTERN = "(https://.+|bunny:[0-9a-fA-F-]{36})";

    public record CourseSummary(String slug, String title, String subtitle, String level, int priceInr,
                                int lectureCount, int totalMinutes, List<String> outcomes, int videoCount, int videoMinutes) {
    }

    public record LectureItem(long id, String title, int durationMin, boolean freePreview, boolean hasVideo) {
    }

    public record SectionItem(String title, List<LectureItem> lectures) {
    }

    public record CourseDetail(String slug, String title, String subtitle, String description, String level, int priceInr,
                               int lectureCount, int totalMinutes, List<String> outcomes, List<SectionItem> sections,
                               boolean enrolled, boolean published, List<Long> completedLectureIds,
                               String trailerUrl, int videoCount, int videoMinutes) {
    }

    public record LectureView(long id, String title, String sectionTitle, int durationMin, boolean freePreview,
                              String videoUrl, String content, Long prevId, Long nextId, boolean completed) {
    }

    public record MyCourse(String slug, String title, String subtitle, int lectureCount, int completedCount, Long nextLectureId) {
    }

    public record Order(String orderId, String courseSlug, String courseTitle, int amountPaise, String status,
                        Instant createdAt, Instant paidAt, String paymentId) {
    }

    // ---- admin ----

    public record AdminLecture(
            Long id,
            @NotBlank(message = "Every lecture needs a title.") @Size(max = 160) String title,
            @Min(0) @Max(600) int durationMin,
            boolean freePreview,
            @Size(max = 500) @Pattern(regexp = VIDEO_PATTERN, message = "Video links must start with https://") String videoUrl,
            @Size(max = 200_000) String content) {
    }

    public record AdminSection(
            Long id,
            @NotBlank(message = "Every section needs a title.") @Size(max = 160) String title,
            @NotNull @Size(max = 300) List<@Valid AdminLecture> lectures) {
    }

    public record AdminCourse(
            Long id,
            @NotBlank(message = "Add a URL name (slug).")
            @Size(max = 80)
            @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "The URL name can only use lowercase letters, numbers and dashes.") String slug,
            @NotBlank(message = "Add a course title.") @Size(max = 160) String title,
            @Size(max = 300) String subtitle,
            @Size(max = 20_000) String description,
            @Size(max = 40) List<@Size(max = 300) String> outcomes,
            @NotBlank @Pattern(regexp = "Beginner|Intermediate|Advanced") String level,
            @Min(0) @Max(500_000) int priceInr,
            boolean published,
            int sortOrder,
            @NotNull @Size(max = 100) List<@Valid AdminSection> sections,
            @Size(max = 500) @Pattern(regexp = VIDEO_PATTERN, message = "The trailer link must start with https://") String trailerUrl,
            long enrollments) {
    }

    public record AdminOrder(String orderId, String userEmail, String courseTitle, int amountPaise, String status,
                             Instant createdAt, Instant paidAt, String paymentId) {
    }

    public record AdminOrders(long paidCount, long revenuePaise, List<AdminOrder> orders) {
    }
}
