package com.javaatlas.feedback;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.javaatlas.common.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** The owner's feedback inbox (admin only, behind two-factor sign-in). */
@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    private static final Logger log = LoggerFactory.getLogger(AdminFeedbackController.class);
    private static final Set<String> STATUSES = Set.of("NEW", "IN_PROGRESS", "RESOLVED", "ARCHIVED");
    private static final int PAGE_SIZE = 30;

    public record Item(long id, Timestamp createdAt, String type, Integer rating, Boolean helpful, String reasons, String message,
                       String name, String email, String page, String lessonId, String device, String status, String adminNote) {
    }

    public record Inbox(List<Item> items, long total, Map<String, Long> counts) {
    }

    public record LessonScore(String lessonId, long helpful, long notHelpful, Map<String, Long> reasons) {
    }

    public record Summary(long newCount, Map<String, Long> byType, Double avgRating, long ratings, List<LessonScore> lessons) {
    }

    public record Update(@Size(max = 16) String status, @Size(max = 2000) String adminNote) {
    }

    private final JdbcClient db;

    public AdminFeedbackController(JdbcClient db) {
        this.db = db;
    }

    /** status: NEW, IN_PROGRESS, RESOLVED, ARCHIVED or ALL; type: a feedback type or empty; q: text search. */
    @GetMapping
    public Inbox inbox(@RequestParam(defaultValue = "NEW") String status, @RequestParam(defaultValue = "") String type,
                       @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        String where = " WHERE (:status = 'ALL' OR status = :status) AND (:type = '' OR type = :type)"
                + " AND (:q = '' OR message ILIKE :like OR email ILIKE :like OR page ILIKE :like OR lesson_id ILIKE :like)";
        String st = STATUSES.contains(status) ? status : "ALL";
        String ty = FeedbackController.TYPES.contains(type) ? type : "";
        String query = q.strip();
        String like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Item> items = db.sql("SELECT id, created_at, type, rating, helpful, reasons, message, name, email, page, lesson_id, device, status, admin_note FROM feedback"
                        + where + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
                .param("status", st).param("type", ty).param("q", query).param("like", like)
                .param("limit", PAGE_SIZE).param("offset", Math.max(0, page) * PAGE_SIZE)
                .query((rs, i) -> new Item(rs.getLong(1), rs.getTimestamp(2), rs.getString(3), (Integer) rs.getObject(4, Integer.class),
                        (Boolean) rs.getObject(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
                        rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14)))
                .list();
        long total = db.sql("SELECT count(*) FROM feedback" + where)
                .param("status", st).param("type", ty).param("q", query).param("like", like).query(Long.class).single();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String s : List.of("NEW", "IN_PROGRESS", "RESOLVED", "ARCHIVED")) counts.put(s, 0L);
        db.sql("SELECT status, count(*) FROM feedback GROUP BY status").query((rs, i) -> counts.put(rs.getString(1), rs.getLong(2))).list();
        return new Inbox(items, total, counts);
    }

    @GetMapping("/summary")
    public Summary summary() {
        long fresh = db.sql("SELECT count(*) FROM feedback WHERE status = 'NEW'").query(Long.class).single();
        Map<String, Long> byType = new LinkedHashMap<>();
        db.sql("SELECT type, count(*) FROM feedback GROUP BY type ORDER BY 2 DESC").query((rs, i) -> byType.put(rs.getString(1), rs.getLong(2))).list();
        Object[] rating = db.sql("SELECT avg(rating), count(rating) FROM feedback WHERE rating IS NOT NULL")
                .query((rs, i) -> new Object[] { rs.getObject(1) == null ? null : rs.getDouble(1), rs.getLong(2) }).single();
        List<LessonScore> lessons = new ArrayList<>();
        db.sql("""
                SELECT lesson_id, count(*) FILTER (WHERE helpful), count(*) FILTER (WHERE NOT helpful), string_agg(reasons, ',')
                FROM feedback WHERE type = 'lesson' AND lesson_id IS NOT NULL
                GROUP BY lesson_id ORDER BY 3 DESC, 2 ASC LIMIT 40
                """).query((rs, i) -> {
                    Map<String, Long> reasons = new LinkedHashMap<>();
                    String all = rs.getString(4);
                    if (all != null) for (String r : all.split(",")) if (!r.isBlank()) reasons.merge(r, 1L, Long::sum);
                    lessons.add(new LessonScore(rs.getString(1), rs.getLong(2), rs.getLong(3), reasons));
                    return null;
                }).list();
        return new Summary(fresh, byType, (Double) rating[0], (Long) rating[1], lessons);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable long id, @Valid @RequestBody Update u, @AuthenticationPrincipal Jwt jwt) {
        if (u.status() != null && !STATUSES.contains(u.status())) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "Unknown status.");
        int n = db.sql("""
                UPDATE feedback SET status = coalesce(:status, status), admin_note = coalesce(:note, admin_note), updated_at = now() WHERE id = :id
                """).param("status", u.status()).param("note", u.adminNote()).param("id", id).update();
        if (n == 0) throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "That feedback doesn’t exist.");
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        db.sql("DELETE FROM feedback WHERE id = :id").param("id", id).update();
        log.info("Admin {} deleted feedback {}", jwt.getClaimAsString("email"), id);
        return ResponseEntity.noContent().build();
    }

    /** All feedback as CSV. Cells starting with = + - @ are prefixed so spreadsheets don't run them as formulas. */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export() {
        StringBuilder csv = new StringBuilder("id,date,type,rating,helpful,reasons,status,page,lesson,name,email,message,admin_note\n");
        db.sql("SELECT id, created_at, type, rating, helpful, reasons, status, page, lesson_id, name, email, message, admin_note FROM feedback ORDER BY created_at DESC")
                .query((rs, i) -> {
                    for (int c = 1; c <= 13; c++) {
                        Object v = rs.getObject(c);
                        csv.append(cell(v == null ? "" : String.valueOf(v))).append(c < 13 ? ',' : '\n');
                    }
                    return null;
                }).list();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"javaatlas-feedback.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

    static String cell(String v) {
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
