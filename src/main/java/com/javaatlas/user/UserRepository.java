package com.javaatlas.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    java.util.List<AppUser> findAllByRole(String role);

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
