package com.moviehub.auth.dto;

import com.moviehub.user.dto.UserResponse;

/**
 * @param expiresIn access token lifetime in seconds
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {
}
