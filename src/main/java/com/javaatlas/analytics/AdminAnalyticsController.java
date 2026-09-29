package com.javaatlas.analytics;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner's analytics (admin only, behind two-factor sign-in). Everything is computed from this site's own
 * database: anonymous events plus accounts, orders, lesson progress and study time.
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    public record Kpis(long visitors, long pageviews, long searches, long signups, long orders, long revenuePaise,
                       long aiQuestions, long lessonCompletions, long studyMinutes, long activeLearners) {
    }

    public record DayRow(String day, long visitors, long pageviews, long searches, long signups, long orders, long revenuePaise) {
    }

    public record Count(String key, long count, long visitors) {
    }

    public record SearchRow(String query, long count, Integer results) {
    }

    public record Step(String step, long count) {
    }

    public record CourseRow(String slug, String title, long views, long orders, long revenuePaise) {
    }

    public record Report(int days, String timezone, Kpis current, Kpis previous, long activeNow, List<Count> activePages,
                         List<DayRow> daily, List<Count> pages, List<Count> referrers, List<Count> campaigns, List<Count> devices,
                         List<Count> countries, List<SearchRow> searches, List<SearchRow> noResults, List<Step> funnel,
                         List<Count> hours, List<Count> aiTools, List<Count> completions, List<CourseRow> courses) {
    }

    private record Window(Timestamp from, Timestamp to, LocalDate fromDay, LocalDate toDay) {
    }

    private final JdbcClient db;
    private final ZoneId zone;

    public AdminAnalyticsController(JdbcClient db, AnalyticsService analytics) {
        this.db = db;
        this.zone = analytics.zone();
    }

    /** days: 1 (today), 7, 30 or 90. The previous period of the same length is included for comparison. */
    @GetMapping
    public Report report(@RequestParam(defaultValue = "30") int days) {
        int d = List.of(1, 7, 30, 90, 365).contains(days) ? days : 30;
        Window w = window(d, 0);
        Window prev = window(d, d);
        return new Report(d, zone.getId(), kpis(w), kpis(prev), activeNow(), activePages(), daily(w),
                pages(w), referrers(w), campaigns(w), group(w, "device"), group(w, "country"),
                searches(w, false), searches(w, true), funnel(w), hours(w), byType(w, "ai", "source"),
                byType(w, "lesson_complete", "path"), courses(w));
    }

    /** Daily totals as a CSV file, for spreadsheets. */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(@RequestParam(defaultValue = "30") int days) {
        int d = List.of(1, 7, 30, 90, 365).contains(days) ? days : 30;
        StringBuilder csv = new StringBuilder("date,visitors,page_views,searches,signups,paid_orders,revenue_inr\n");
        for (DayRow r : daily(window(d, 0))) {
            csv.append(r.day()).append(',').append(r.visitors()).append(',').append(r.pageviews()).append(',').append(r.searches())
                    .append(',').append(r.signups()).append(',').append(r.orders()).append(',').append(String.format("%.2f", r.revenuePaise() / 100.0)).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"javaatlas-analytics-" + d + "-days.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

    private Window window(int days, int offsetDays) {
        LocalDate today = LocalDate.now(zone);
        LocalDate toDay = today.minusDays(offsetDays);
        LocalDate fromDay = toDay.minusDays(days - 1L);
        Instant from = fromDay.atStartOfDay(zone).toInstant();
        Instant to = offsetDays == 0 ? Instant.now() : toDay.plusDays(1).atStartOfDay(zone).toInstant();
        return new Window(Timestamp.from(from), Timestamp.from(to), fromDay, toDay);
    }

    private Kpis kpis(Window w) {
        long[] e = db.sql("""
                SELECT count(DISTINCT visitor) FILTER (WHERE type = 'pageview'),
                       count(*) FILTER (WHERE type = 'pageview'),
                       count(*) FILTER (WHERE type = 'search'),
                       count(*) FILTER (WHERE type = 'ai'),
                       count(*) FILTER (WHERE type = 'lesson_complete')
                FROM analytics_event WHERE ts >= :from AND ts < :to
                """).param("from", w.from()).param("to", w.to())
                .query((rs, i) -> new long[] { rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5) }).single();
        long signups = db.sql("SELECT count(*) FROM app_user WHERE role = 'USER' AND created_at >= :from AND created_at < :to")
                .param("from", w.from()).param("to", w.to()).query(Long.class).single();
        long[] o = db.sql("""
                SELECT count(*), coalesce(sum(amount_paise), 0) FROM enrollment
                WHERE status = 'PAID' AND amount_paise > 0 AND paid_at >= :from AND paid_at < :to
                """).param("from", w.from()).param("to", w.to()).query((rs, i) -> new long[] { rs.getLong(1), rs.getLong(2) }).single();
        long[] study = db.sql("SELECT coalesce(sum(seconds), 0) / 60, count(DISTINCT user_id) FROM study_day WHERE day >= :a AND day <= :b")
                .param("a", java.sql.Date.valueOf(w.fromDay())).param("b", java.sql.Date.valueOf(w.toDay()))
                .query((rs, i) -> new long[] { rs.getLong(1), rs.getLong(2) }).single();
        return new Kpis(e[0], e[1], e[2], signups, o[0], o[1], e[3], e[4], study[0], study[1]);
    }

    private List<DayRow> daily(Window w) {
        Map<LocalDate, long[]> rows = new LinkedHashMap<>();
        for (LocalDate d = w.fromDay(); !d.isAfter(w.toDay()); d = d.plusDays(1)) rows.put(d, new long[6]);
        db.sql("""
                SELECT (ts AT TIME ZONE :tz)::date, count(DISTINCT visitor) FILTER (WHERE type = 'pageview'),
                       count(*) FILTER (WHERE type = 'pageview'), count(*) FILTER (WHERE type = 'search')
                FROM analytics_event WHERE ts >= :from AND ts < :to GROUP BY 1
                """).param("tz", zone.getId()).param("from", w.from()).param("to", w.to())
                .query((rs, i) -> {
                    long[] r = rows.get(rs.getDate(1).toLocalDate());
                    if (r != null) { r[0] = rs.getLong(2); r[1] = rs.getLong(3); r[2] = rs.getLong(4); }
                    return null;
                }).list();
        db.sql("SELECT (created_at AT TIME ZONE :tz)::date, count(*) FROM app_user WHERE role = 'USER' AND created_at >= :from AND created_at < :to GROUP BY 1")
                .param("tz", zone.getId()).param("from", w.from()).param("to", w.to())
                .query((rs, i) -> {
                    long[] r = rows.get(rs.getDate(1).toLocalDate());
                    if (r != null) r[3] = rs.getLong(2);
                    return null;
                }).list();
        db.sql("""
                SELECT (paid_at AT TIME ZONE :tz)::date, count(*), coalesce(sum(amount_paise), 0) FROM enrollment
                WHERE status = 'PAID' AND amount_paise > 0 AND paid_at >= :from AND paid_at < :to GROUP BY 1
                """).param("tz", zone.getId()).param("from", w.from()).param("to", w.to())
                .query((rs, i) -> {
                    long[] r = rows.get(rs.getDate(1).toLocalDate());
                    if (r != null) { r[4] = rs.getLong(2); r[5] = rs.getLong(3); }
                    return null;
                }).list();
        List<DayRow> out = new ArrayList<>();
        rows.forEach((d, r) -> out.add(new DayRow(d.toString(), r[0], r[1], r[2], r[3], r[4], r[5])));
        return out;
    }

    private List<Count> pages(Window w) {
        return counts("""
                SELECT path, count(*), count(DISTINCT visitor) FROM analytics_event
                WHERE type = 'pageview' AND path IS NOT NULL AND ts >= :from AND ts < :to
                GROUP BY path ORDER BY 2 DESC LIMIT 30
                """, w);
    }

    private List<Count> referrers(Window w) {
        return counts("""
                SELECT ref, count(*), count(DISTINCT visitor) FROM analytics_event
                WHERE type = 'pageview' AND ref IS NOT NULL AND ts >= :from AND ts < :to
                GROUP BY ref ORDER BY 3 DESC LIMIT 20
                """, w);
    }

    private List<Count> campaigns(Window w) {
        return counts("""
                SELECT coalesce(source, '(none)') || CASE WHEN campaign IS NULL THEN '' ELSE ' / ' || campaign END, count(*), count(DISTINCT visitor)
                FROM analytics_event WHERE type = 'pageview' AND (source IS NOT NULL OR campaign IS NOT NULL) AND ts >= :from AND ts < :to
                GROUP BY 1 ORDER BY 3 DESC LIMIT 20
                """, w);
    }

    /** column is a fixed identifier chosen in code ("device" or "country"), never user input. */
    private List<Count> group(Window w, String column) {
        String col = column.equals("country") ? "country" : "device";
        return counts("SELECT " + col + ", count(*), count(DISTINCT visitor) FROM analytics_event WHERE type = 'pageview' AND " + col
                + " IS NOT NULL AND ts >= :from AND ts < :to GROUP BY 1 ORDER BY 3 DESC LIMIT 20", w);
    }

    private List<Count> byType(Window w, String type, String column) {
        String col = column.equals("path") ? "path" : "source";
        return db.sql("SELECT " + col + ", count(*), count(DISTINCT visitor) FROM analytics_event WHERE type = :type AND " + col
                        + " IS NOT NULL AND ts >= :from AND ts < :to GROUP BY 1 ORDER BY 2 DESC LIMIT 20")
                .param("type", type).param("from", w.from()).param("to", w.to())
                .query((rs, i) -> new Count(rs.getString(1), rs.getLong(2), rs.getLong(3))).list();
    }

    private List<SearchRow> searches(Window w, boolean onlyEmpty) {
        return db.sql("SELECT query, count(*), min(results) FROM analytics_event WHERE type = 'search' AND query IS NOT NULL"
                        + (onlyEmpty ? " AND results = 0" : "") + " AND ts >= :from AND ts < :to GROUP BY query ORDER BY 2 DESC LIMIT 30")
                .param("from", w.from()).param("to", w.to())
                .query((rs, i) -> new SearchRow(rs.getString(1), rs.getLong(2), (Integer) rs.getObject(3))).list();
    }

    private List<Step> funnel(Window w) {
        long[] v = db.sql("""
                SELECT count(DISTINCT visitor) FILTER (WHERE type = 'pageview'),
                       count(DISTINCT visitor) FILTER (WHERE type = 'pageview' AND path LIKE '/learn/%'),
                       count(DISTINCT visitor) FILTER (WHERE type = 'pageview' AND path LIKE '/courses/%' AND path NOT LIKE '%/learn%'),
                       count(DISTINCT visitor) FILTER (WHERE type = 'checkout_start')
                FROM analytics_event WHERE ts >= :from AND ts < :to
                """).param("from", w.from()).param("to", w.to())
                .query((rs, i) -> new long[] { rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4) }).single();
        Kpis k = kpis(w);
        return List.of(new Step("Visitors", v[0]), new Step("Read a lesson", v[1]), new Step("Signed up", k.signups()),
                new Step("Viewed a course", v[2]), new Step("Started checkout", v[3]), new Step("Paid", k.orders()));
    }

    private List<Count> hours(Window w) {
        long[] h = new long[24];
        db.sql("SELECT extract(hour FROM ts AT TIME ZONE :tz)::int, count(*) FROM analytics_event WHERE type = 'pageview' AND ts >= :from AND ts < :to GROUP BY 1")
                .param("tz", zone.getId()).param("from", w.from()).param("to", w.to())
                .query((rs, i) -> { h[rs.getInt(1)] = rs.getLong(2); return null; }).list();
        List<Count> out = new ArrayList<>();
        for (int i = 0; i < 24; i++) out.add(new Count(String.valueOf(i), h[i], 0));
        return out;
    }

    private List<CourseRow> courses(Window w) {
        return db.sql("""
                SELECT c.slug, c.title,
                  (SELECT count(*) FROM analytics_event e WHERE e.type = 'pageview' AND e.path = '/courses/' || c.slug AND e.ts >= :from AND e.ts < :to),
                  (SELECT count(*) FROM enrollment n WHERE n.course_id = c.id AND n.status = 'PAID' AND n.amount_paise > 0 AND n.paid_at >= :from AND n.paid_at < :to),
                  (SELECT coalesce(sum(n.amount_paise), 0) FROM enrollment n WHERE n.course_id = c.id AND n.status = 'PAID' AND n.paid_at >= :from AND n.paid_at < :to)
                FROM course c ORDER BY 3 DESC, c.sort_order
                """).param("from", w.from()).param("to", w.to())
                .query((rs, i) -> new CourseRow(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5))).list();
    }

    private long activeNow() {
        return db.sql("SELECT count(DISTINCT visitor) FROM analytics_event WHERE type = 'pageview' AND ts > now() - interval '5 minutes'")
                .query(Long.class).single();
    }

    private List<Count> activePages() {
        return db.sql("""
                SELECT path, count(*), count(DISTINCT visitor) FROM analytics_event
                WHERE type = 'pageview' AND ts > now() - interval '5 minutes' GROUP BY path ORDER BY 3 DESC LIMIT 8
                """).query((rs, i) -> new Count(rs.getString(1), rs.getLong(2), rs.getLong(3))).list();
    }

    private List<Count> counts(String sql, Window w) {
        return db.sql(sql).param("from", w.from()).param("to", w.to())
                .query((rs, i) -> new Count(rs.getString(1), rs.getLong(2), rs.getLong(3))).list();
    }
}
