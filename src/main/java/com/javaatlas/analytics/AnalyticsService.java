package com.javaatlas.analytics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Records anonymous usage events. Nothing personal is stored: the visitor id is a hash of IP address, browser
 * and a random salt that changes every day, and old salts are deleted, so ids can't be linked across days or
 * traced back to anyone. Bots and browsers sending Do Not Track / Global Privacy Control are ignored.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final Pattern BOT = Pattern.compile("(?i)bot|crawl|spider|slurp|facebookexternalhit|headless|lighthouse|preview|monitor|curl|wget|python-requests");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient db;
    private final ZoneId zone;
    private final Map<LocalDate, String> salts = new ConcurrentHashMap<>();

    public AnalyticsService(JdbcClient db, @Value("${app.timezone:Asia/Kolkata}") String zone) {
        this.db = db;
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() {
        return zone;
    }

    public record Event(String type, String path, String ref, String query, Integer results, String source, String campaign, String device) {
    }

    /** True when this request should not be counted. */
    public boolean ignore(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        return ua == null || ua.isBlank() || BOT.matcher(ua).find()
                || "1".equals(req.getHeader("DNT")) || "1".equals(req.getHeader("Sec-GPC"));
    }

    public void record(HttpServletRequest req, Event e) {
        if (ignore(req)) return;
        try {
            db.sql("""
                    INSERT INTO analytics_event (visitor, type, path, ref, query, results, source, campaign, device, country)
                    VALUES (:v, :type, :path, :ref, :query, :results, :source, :campaign, :device, :country)
                    """)
                    .param("v", visitor(req))
                    .param("type", e.type())
                    .param("path", cut(e.path(), 300))
                    .param("ref", cut(e.ref(), 120))
                    .param("query", e.query() == null ? null : cut(e.query().trim().toLowerCase(Locale.ROOT), 120))
                    .param("results", e.results())
                    .param("source", cut(e.source(), 60))
                    .param("campaign", cut(e.campaign(), 80))
                    .param("device", e.device() != null ? e.device() : device(req.getHeader("User-Agent")))
                    .param("country", country(req))
                    .update();
        } catch (RuntimeException ex) {
            log.warn("Couldn't record an analytics event: {}", ex.getMessage());   // analytics must never break the site
        }
    }

    private String visitor(HttpServletRequest req) {
        LocalDate today = LocalDate.now(zone);
        String salt = salts.computeIfAbsent(today, this::saltFor);
        return sha256(salt + "|" + req.getRemoteAddr() + "|" + req.getHeader("User-Agent")).substring(0, 16);
    }

    /** The day's random salt, shared by all instances through the database. */
    private String saltFor(LocalDate day) {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        db.sql("INSERT INTO analytics_salt (day, salt) VALUES (:d, :s) ON CONFLICT (day) DO NOTHING")
                .param("d", Date.valueOf(day)).param("s", HexFormat.of().formatHex(b)).update();
        return db.sql("SELECT salt FROM analytics_salt WHERE day = :d").param("d", Date.valueOf(day)).query(String.class).single();
    }

    public static String device(String ua) {
        if (ua == null) return "desktop";
        if (ua.matches("(?i).*(ipad|tablet).*")) return "tablet";
        if (ua.matches("(?i).*(mobi|android|iphone).*")) return "mobile";
        return "desktop";
    }

    /** Country from a CDN header (Cloudflare or CloudFront) when the site is behind one. */
    private static String country(HttpServletRequest req) {
        String c = req.getHeader("CF-IPCountry");
        if (c == null) c = req.getHeader("CloudFront-Viewer-Country");
        return c != null && c.matches("[A-Z]{2}") && !c.equals("XX") ? c : null;
    }

    /** Daily at 03:30: drop salts older than a day (making old ids untraceable) and events older than 13 months. */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanUp() {
        LocalDate today = LocalDate.now(zone);
        db.sql("DELETE FROM analytics_salt WHERE day < :d").param("d", Date.valueOf(today.minusDays(1))).update();
        salts.keySet().removeIf(d -> d.isBefore(today.minusDays(1)));
        int removed = db.sql("DELETE FROM analytics_event WHERE ts < now() - interval '395 days'").update();
        if (removed > 0) log.info("Removed {} analytics events older than 13 months", removed);
    }

    private static String cut(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
