package com.moviehub.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviehub.auth.dto.AuthResponse;
import com.moviehub.auth.dto.LoginRequest;
import com.moviehub.auth.dto.RegisterRequest;
import com.moviehub.common.exception.ConflictException;
import com.moviehub.config.AppProperties;
import com.moviehub.security.JwtService;
import com.moviehub.security.UserPrincipal;
import com.moviehub.user.Role;
import com.moviehub.user.User;
import com.moviehub.user.UserMapper;
import com.moviehub.user.UserRepository;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID_REFRESH = "Invalid or expired refresh token";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AppProperties props;
    private final Clock clock = Clock.systemUTC();

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtService jwtService,
            UserMapper userMapper, AppProperties props) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.props = props;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.firstName(), request.lastName(), Role.USER);
        user.setLastLoginAt(clock.instant());
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException / DisabledException, mapped to 401 / 403 by GlobalExceptionHandler.
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(User.normalizeEmail(request.email()), request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.getReferenceById(principal.id());
        user.setLastLoginAt(clock.instant());
        return issueTokens(user);
    }

    /**
     * Rotates the refresh token: the presented token is revoked and a new pair is issued.
     * Presenting an already revoked token means it was stolen or replayed, so every session of that user is revoked.
     */
    @Transactional(noRollbackFor = {BadCredentialsException.class, DisabledException.class})
    public AuthResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHashWithUser(hash(rawRefreshToken))
                .orElseThrow(() -> new BadCredentialsException(INVALID_REFRESH));

        if (token.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(token.getUser().getId(), now);
            throw new BadCredentialsException(INVALID_REFRESH);
        }
        if (token.isExpired(now)) {
            throw new BadCredentialsException(INVALID_REFRESH);
        }
        if (!token.getUser().isActive()) {
            token.revoke(now);
            throw new DisabledException("This account has been suspended");
        }

        token.revoke(now);
        return issueTokens(token.getUser());
    }

    /** Revokes the given refresh token. Unknown tokens are ignored so logout always succeeds. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHashWithUser(hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private AuthResponse issueTokens(User user) {
        String rawRefreshToken = newOpaqueToken();
        Instant expiresAt = clock.instant().plus(props.jwt().refreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(user, hash(rawRefreshToken), expiresAt));

        String accessToken = jwtService.generateAccessToken(UserPrincipal.from(user));
        return new AuthResponse(accessToken, rawRefreshToken, "Bearer",
                jwtService.getAccessTokenTtl().toSeconds(), userMapper.toResponse(user));
    }

    private static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
