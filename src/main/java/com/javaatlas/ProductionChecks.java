package com.javaatlas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Refuses to start in production with unsafe settings, and warns about missing ones. */
@Component
public class ProductionChecks implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionChecks.class);

    private final AppProperties props;
    private final Environment env;

    public ProductionChecks(AppProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.cookieSecure()) {
            log.warn("COOKIE_SECURE is false: fine for local development, but it must be true on a live HTTPS site.");
            return;
        }
        String dbPassword = env.getProperty("spring.datasource.password", "");
        String lower = dbPassword.toLowerCase(java.util.Locale.ROOT);
        if (dbPassword.length() < 12 || lower.contains("change-this") || lower.contains("javaatlas") || lower.contains("password")) {
            throw new IllegalStateException("Use a strong database password (12+ characters) in production, not the example value.");
        }
        if (!props.siteUrl().startsWith("https://")) {
            throw new IllegalStateException("APP_SITE_URL must be your https:// address in production.");
        }
        if (props.razorpay().enabled() && !props.razorpay().webhookEnabled()) {
            log.warn("RAZORPAY_WEBHOOK_SECRET is not set: payments still work, but the webhook backup won't.");
        }
        if (!props.admin().require2fa()) {
            log.warn("ADMIN_REQUIRE_2FA is off. Two-factor sign-in is strongly recommended for the admin account.");
        }
        if (AppProperties.has(props.admin().password())) {
            log.warn("ADMIN_PASSWORD is still set. After your first admin sign-in, remove it from the environment.");
        }
    }
}
