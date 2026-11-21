package com.moviehub.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.moviehub.auth.AuthService;
import com.moviehub.auth.dto.AuthResponse;
import com.moviehub.security.UserPrincipal;
import com.moviehub.user.dto.ProfileDtos.ChangePasswordRequest;
import com.moviehub.user.dto.ProfileDtos.DeleteAccountRequest;
import com.moviehub.user.dto.ProfileDtos.UpdateProfileRequest;
import com.moviehub.user.dto.ProfileDtos.UserStats;
import com.moviehub.user.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The current user's account. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final ProfileService profileService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "Get the currently authenticated user")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return profileService.get(principal.id());
    }

    @PutMapping
    @Operation(summary = "Update my name")
    public UserResponse update(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(principal.id(), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Change my password: signs out other sessions and returns new tokens for this one")
    public AuthResponse changePassword(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(principal.id(), request.currentPassword(), request.newPassword());
    }

    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload my avatar (PNG, JPEG or WebP, max 2 MB)")
    public UserResponse uploadAvatar(@AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        return profileService.updateAvatar(principal.id(), file);
    }

    @DeleteMapping("/avatar")
    @Operation(summary = "Remove my avatar")
    public UserResponse removeAvatar(@AuthenticationPrincipal UserPrincipal principal) {
        return profileService.removeAvatar(principal.id());
    }

    @GetMapping("/stats")
    @Operation(summary = "Counters for my dashboard")
    public UserStats stats(@AuthenticationPrincipal UserPrincipal principal) {
        return profileService.stats(principal.id());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete my account (password confirmation required)")
    public void deleteAccount(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request) {
        profileService.deleteAccount(principal.id(), request.password());
    }
}
