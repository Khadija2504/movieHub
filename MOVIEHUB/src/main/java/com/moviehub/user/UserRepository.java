package com.moviehub.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Callers must pass a normalized (lower-case) email, see {@link User#normalizeEmail(String)}. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
