package com.moviehub.user.dto;

import java.time.Instant;

import com.moviehub.user.Role;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        Role role,
        Instant createdAt) {
}
