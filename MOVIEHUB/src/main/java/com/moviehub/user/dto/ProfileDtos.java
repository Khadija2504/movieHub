package com.moviehub.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "First name is required")
            @Size(max = 100, message = "First name must be at most 100 characters")
            String firstName,

            @NotBlank(message = "Last name is required")
            @Size(max = 100, message = "Last name must be at most 100 characters")
            String lastName) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required")
            String currentPassword,

            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one digit")
            String newPassword) {
    }

    public record DeleteAccountRequest(@NotBlank(message = "Password is required") String password) {
    }

    /** @param upcomingBookingCount confirmed cinema bookings of screenings that have not started */
    public record UserStats(long watchlistCount, long reviewCount, long upcomingBookingCount) {
    }
}
