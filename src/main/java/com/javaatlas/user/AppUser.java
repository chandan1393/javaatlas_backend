package com.javaatlas.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role = ROLE_USER;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Base32 secret for two-factor codes; set during setup, active once totpEnabled is true. */
    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    /** Last accepted 30-second step, so a code can't be used twice. */
    @Column(name = "totp_last_step", nullable = false)
    private long totpLastStep;

    /** Increased to sign out every existing session (password change, "sign out everywhere"). */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    protected AppUser() {
    }

    public AppUser(String email, String name, String passwordHash) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public void makeAdmin() {
        this.role = ROLE_ADMIN;
    }

    public void makeUser() {
        this.role = ROLE_USER;
    }

    /** Signs out every session issued before now. */
    public void revokeSessions() {
        this.tokenVersion++;
    }

    public void startTotpSetup(String secret) {
        this.totpSecret = secret;
        this.totpEnabled = false;
        this.totpLastStep = 0;
    }

    public void enableTotp(long step) {
        this.totpEnabled = true;
        this.totpLastStep = step;
    }

    public void disableTotp() {
        this.totpSecret = null;
        this.totpEnabled = false;
        this.totpLastStep = 0;
    }

    /** Records a used code; false if this step (or a later one) was already used. */
    public boolean acceptTotpStep(long step) {
        if (step <= totpLastStep) return false;
        this.totpLastStep = step;
        return true;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTotpSecret() { return totpSecret; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public int getTokenVersion() { return tokenVersion; }
}
