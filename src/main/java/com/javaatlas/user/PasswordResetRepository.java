package com.javaatlas.user;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

    Optional<PasswordReset> findByTokenHash(String tokenHash);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    long deleteByUserId(Long userId);
}
