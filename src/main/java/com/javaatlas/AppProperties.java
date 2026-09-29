package com.javaatlas;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under "app." in application.properties (and the dev/prod profile files). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        boolean cookieSecure,
        List<String> corsOrigins,
        Admin admin,
        boolean seedCourses,
        String siteUrl,
        String mailFrom,
        Razorpay razorpay,
        Ai ai,
        Video video) {

    public AppProperties {
        if (jwt == null) jwt = new Jwt("", Duration.ofDays(7));
        corsOrigins = corsOrigins == null ? List.of() : corsOrigins.stream().filter(AppProperties::has).map(String::trim).toList();
        if (admin == null) admin = new Admin("", "", false, true, 8, List.of());
        siteUrl = has(siteUrl) ? siteUrl.trim().replaceAll("/+$", "") : "http://localhost:4200";
        mailFrom = mailFrom == null ? "" : mailFrom.trim();
        if (razorpay == null) razorpay = new Razorpay("", "", "");
        if (video == null) video = new Video("", "", "", 30);
        if (ai == null) ai = new Ai("", "claude-haiku-4-5-20251001", 800, 20, 2000);
    }

    /**
     * The owner's admin account, created or taken over at startup from ADMIN_EMAIL and ADMIN_PASSWORD.
     * Public sign-up can never create an admin.
     */
    public record Admin(String email, String password, boolean passwordReset, boolean require2fa, int sessionHours, List<String> allowedIps) {
        public Admin {
            email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
            if (sessionHours <= 0) sessionHours = 8;
            allowedIps = allowedIps == null ? List.of() : allowedIps.stream().filter(AppProperties::has).map(String::trim).toList();
        }
    }

    public record Jwt(String secret, Duration ttl) {
        public Jwt {
            if (ttl == null) ttl = Duration.ofDays(7);
        }
    }

    public record Razorpay(String keyId, String keySecret, String webhookSecret) {
        public boolean enabled() {
            return has(keyId) && has(keySecret);
        }

        public boolean webhookEnabled() {
            return has(webhookSecret);
        }
    }

    public record Ai(String apiKey, String model, int maxTokens, int perIpPerHour, int dailyLimit) {
        public boolean enabled() {
            return has(apiKey);
        }
    }

    /**
     * Bunny Stream video hosting. With the library id and API key set, admins can upload videos from the course
     * editor; with the token key set too, every playback link is signed and expires (recommended for paid courses).
     */
    public record Video(String bunnyLibraryId, String bunnyApiKey, String bunnyTokenKey, int tokenTtlMinutes) {
        public Video {
            bunnyLibraryId = bunnyLibraryId == null ? "" : bunnyLibraryId.trim();
            bunnyApiKey = bunnyApiKey == null ? "" : bunnyApiKey.trim();
            bunnyTokenKey = bunnyTokenKey == null ? "" : bunnyTokenKey.trim();
            if (tokenTtlMinutes <= 0) tokenTtlMinutes = 30;
        }

        public boolean uploadsEnabled() {
            return has(bunnyLibraryId) && has(bunnyApiKey);
        }

        public boolean signedPlayback() {
            return has(bunnyLibraryId) && has(bunnyTokenKey);
        }
    }

    public static boolean has(String s) {
        return s != null && !s.isBlank();
    }
}
