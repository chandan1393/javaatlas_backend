package com.javaatlas.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.javaatlas.AppProperties;

/**
 * Creates or secures the owner's admin account at startup, from ADMIN_EMAIL and ADMIN_PASSWORD.
 *
 * - No account with that email: it's created as an admin (ADMIN_PASSWORD required, 12+ characters).
 * - A normal account already uses that email (someone may have registered it first): it becomes the admin
 *   account only when ADMIN_PASSWORD is set, which replaces its password, clears 2FA and signs out its sessions.
 * - ADMIN_PASSWORD_RESET=true: recovery. Resets the admin password to ADMIN_PASSWORD and clears 2FA.
 * - Any other account holding the admin role is turned back into a normal account.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = props.admin();
        if (!AppProperties.has(admin.email())) {
            log.warn("ADMIN_EMAIL is not set, so there is no admin account and the admin area is closed.");
            return;
        }
        String password = admin.password();
        boolean passwordOk = AppProperties.has(password) && password.length() >= 12;
        if (AppProperties.has(password) && !passwordOk) {
            log.error("ADMIN_PASSWORD must be at least 12 characters. It was ignored.");
        }

        users.findAllByRole(AppUser.ROLE_ADMIN).stream()
                .filter(u -> !u.getEmail().equals(admin.email()))
                .forEach(u -> {
                    u.makeUser();
                    u.disableTotp();
                    u.revokeSessions();
                    log.warn("Removed the admin role from {} (only ADMIN_EMAIL can be an admin).", u.getEmail());
                });

        AppUser user = users.findByEmail(admin.email()).orElse(null);
        if (user == null) {
            if (!passwordOk) {
                log.error("Set ADMIN_PASSWORD (12+ characters) to create the admin account for {}.", admin.email());
                return;
            }
            user = new AppUser(admin.email(), "Admin", encoder.encode(password));
            user.makeAdmin();
            users.save(user);
            log.info("Created the admin account {}. Sign in at /admin/login, then you can remove ADMIN_PASSWORD.", admin.email());
            return;
        }
        if (!user.isAdmin()) {
            if (!passwordOk) {
                log.error("{} is a normal account. Set ADMIN_PASSWORD to take it over as the admin account.", admin.email());
                return;
            }
            user.changePasswordHash(encoder.encode(password));
            user.makeAdmin();
            user.disableTotp();
            user.revokeSessions();
            log.warn("{} existed as a normal account; it is now the admin account with the password from ADMIN_PASSWORD.", admin.email());
            return;
        }
        if (admin.passwordReset()) {
            if (!passwordOk) {
                log.error("ADMIN_PASSWORD_RESET is on but ADMIN_PASSWORD is missing or too short.");
                return;
            }
            user.changePasswordHash(encoder.encode(password));
            user.disableTotp();
            user.revokeSessions();
            log.warn("Admin password reset and two-factor sign-in cleared for {}. Set ADMIN_PASSWORD_RESET back to false.", admin.email());
        }
    }
}
