package com.javaatlas.learning;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javaatlas.common.ApiException;
import com.javaatlas.common.RateLimiter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern.Flag;
import jakarta.validation.constraints.Size;

/**
 * Free-lesson progress, study time and study goal for signed-in learners, so progress follows them to any
 * device and the "My learning" page can estimate a finish date. All queries use bound parameters.
 */
@RestController
@RequestMapping("/api/me/learning")
public class LearningController {

    private static final Pattern LESSON_ID = Pattern.compile("[a-z0-9-]{1,80}");
    private static final int MAX_LESSONS = 1000;
    private static final int MAX_SECONDS_PER_CALL = 300;
    private static final int MAX_SECONDS_PER_DAY = 16 * 3600;

    public record Completed(@NotNull @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9-]{1,80}") String lessonId, Instant completedAt) {
    }

    public record Day(@NotNull LocalDate day, @Min(0) @Max(MAX_SECONDS_PER_DAY) int seconds) {
    }

    public record Goal(
            @Min(value = 5, message = "Choose at least 5 minutes a day.") @Max(value = 480, message = "Choose at most 8 hours a day.") int minutesPerDay,
            @Min(1) @Max(7) int daysPerWeek,
            @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9-]{1,80}", flags = Flag.CASE_INSENSITIVE) String pathId,
            LocalDate targetDate) {
    }

    public record SyncRequest(@Size(max = MAX_LESSONS) List<@Valid Completed> completed, @Size(max = 400) List<@Valid Day> days) {
    }

    public record TimeRequest(@NotNull LocalDate day, @Min(1) @Max(MAX_SECONDS_PER_CALL) int seconds) {
    }

    public record LearningData(List<Completed> completed, List<Day> days, Goal goal) {
    }

    private final JdbcClient db;
    private final RateLimiter limiter;

    public LearningController(JdbcClient db, RateLimiter limiter) {
        this.db = db;
        this.limiter = limiter;
    }

    @GetMapping
    public LearningData get(@AuthenticationPrincipal Jwt jwt) {
        return load(userId(jwt));
    }

    /** Merges what the browser has (for example progress made before signing in) and returns everything. */
    @PostMapping("/sync")
    @Transactional
    public LearningData sync(@Valid @RequestBody SyncRequest req, @AuthenticationPrincipal Jwt jwt) {
        long user = userId(jwt);
        limit(user, "sync", 60);
        if (req.completed() != null) {
            for (Completed c : req.completed()) {
                Instant at = c.completedAt() == null || c.completedAt().isAfter(Instant.now()) ? Instant.now() : c.completedAt();
                db.sql("""
                        INSERT INTO lesson_progress (user_id, lesson_id, completed_at) VALUES (:u, :l, :at)
                        ON CONFLICT (user_id, lesson_id) DO UPDATE SET completed_at = LEAST(lesson_progress.completed_at, EXCLUDED.completed_at)
                        """).param("u", user).param("l", c.lessonId()).param("at", java.sql.Timestamp.from(at)).update();
            }
        }
        if (req.days() != null) {
            LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
            for (Day d : req.days()) {
                if (d.day().isAfter(tomorrow) || d.seconds() <= 0) continue;
                db.sql("""
                        INSERT INTO study_day (user_id, day, seconds) VALUES (:u, :d, :s)
                        ON CONFLICT (user_id, day) DO UPDATE SET seconds = GREATEST(study_day.seconds, EXCLUDED.seconds)
                        """).param("u", user).param("d", Date.valueOf(d.day())).param("s", d.seconds()).update();
            }
        }
        return load(user);
    }

    @PutMapping("/lessons/{lessonId}")
    public ResponseEntity<Void> complete(@PathVariable String lessonId, @AuthenticationPrincipal Jwt jwt) {
        long user = userId(jwt);
        checkLesson(lessonId);
        limit(user, "lesson", 600);
        Integer count = db.sql("SELECT count(*) FROM lesson_progress WHERE user_id = :u").param("u", user).query(Integer.class).single();
        if (count >= MAX_LESSONS) throw new ApiException(HttpStatus.BAD_REQUEST, "too_many", "Too many lessons.");
        db.sql("INSERT INTO lesson_progress (user_id, lesson_id) VALUES (:u, :l) ON CONFLICT DO NOTHING")
                .param("u", user).param("l", lessonId).update();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/lessons/{lessonId}")
    public ResponseEntity<Void> uncomplete(@PathVariable String lessonId, @AuthenticationPrincipal Jwt jwt) {
        long user = userId(jwt);
        checkLesson(lessonId);
        db.sql("DELETE FROM lesson_progress WHERE user_id = :u AND lesson_id = :l").param("u", user).param("l", lessonId).update();
        return ResponseEntity.noContent().build();
    }

    /** Adds study time for the learner's local date (sent about once a minute while they're actively reading). */
    @PostMapping("/time")
    public ResponseEntity<Void> time(@Valid @RequestBody TimeRequest req, @AuthenticationPrincipal Jwt jwt) {
        long user = userId(jwt);
        limit(user, "time", 240);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (req.day().isBefore(today.minusDays(1)) || req.day().isAfter(today.plusDays(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "That day isn’t today.");
        }
        db.sql("""
                INSERT INTO study_day (user_id, day, seconds) VALUES (:u, :d, :s)
                ON CONFLICT (user_id, day) DO UPDATE SET seconds = LEAST(:max, study_day.seconds + EXCLUDED.seconds)
                """).param("u", user).param("d", Date.valueOf(req.day())).param("s", req.seconds()).param("max", MAX_SECONDS_PER_DAY).update();
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/goal")
    public Goal goal(@Valid @RequestBody Goal goal, @AuthenticationPrincipal Jwt jwt) {
        long user = userId(jwt);
        limit(user, "goal", 60);
        if (goal.targetDate() != null && (goal.targetDate().isBefore(LocalDate.now(ZoneOffset.UTC)) || goal.targetDate().isAfter(LocalDate.now(ZoneOffset.UTC).plusYears(3)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "Pick a target date between today and three years from now.");
        }
        db.sql("""
                INSERT INTO learning_goal (user_id, minutes_per_day, days_per_week, path_id, target_date, updated_at)
                VALUES (:u, :m, :d, :p, :t, now())
                ON CONFLICT (user_id) DO UPDATE SET minutes_per_day = EXCLUDED.minutes_per_day, days_per_week = EXCLUDED.days_per_week,
                    path_id = EXCLUDED.path_id, target_date = EXCLUDED.target_date, updated_at = now()
                """).param("u", user).param("m", goal.minutesPerDay()).param("d", goal.daysPerWeek())
                .param("p", goal.pathId() == null || goal.pathId().isBlank() ? null : goal.pathId().toLowerCase())
                .param("t", goal.targetDate() == null ? null : Date.valueOf(goal.targetDate())).update();
        return goal;
    }

    private LearningData load(long user) {
        List<Completed> completed = db.sql("SELECT lesson_id, completed_at FROM lesson_progress WHERE user_id = :u ORDER BY completed_at")
                .param("u", user)
                .query((rs, i) -> new Completed(rs.getString(1), rs.getTimestamp(2).toInstant()))
                .list();
        List<Day> days = db.sql("SELECT day, seconds FROM study_day WHERE user_id = :u AND day >= :from ORDER BY day")
                .param("u", user).param("from", Date.valueOf(LocalDate.now(ZoneOffset.UTC).minusDays(370)))
                .query((rs, i) -> new Day(rs.getDate(1).toLocalDate(), rs.getInt(2)))
                .list();
        Goal goal = db.sql("SELECT minutes_per_day, days_per_week, path_id, target_date FROM learning_goal WHERE user_id = :u")
                .param("u", user)
                .query((rs, i) -> new Goal(rs.getInt(1), rs.getInt(2), rs.getString(3),
                        rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate()))
                .optional().orElse(new Goal(30, 5, null, null));
        return new LearningData(completed, days, goal);
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private static void checkLesson(String id) {
        if (!LESSON_ID.matcher(id).matches()) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "Unknown lesson.");
    }

    private void limit(long user, String what, int perHour) {
        if (!limiter.allow("learning-" + what + ":" + user, perHour, Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many requests. Please slow down.");
        }
    }
}
