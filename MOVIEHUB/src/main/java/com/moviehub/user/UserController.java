package com.moviehub.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.common.exception.ResourceNotFoundException;
import com.moviehub.security.UserPrincipal;
import com.moviehub.user.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @GetMapping("/me")
    @Transactional(readOnly = true)
    @Operation(summary = "Get the currently authenticated user")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return userRepository.findById(principal.id())
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.id()));
    }
}
