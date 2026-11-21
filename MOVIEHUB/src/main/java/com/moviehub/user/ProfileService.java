package com.moviehub.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.moviehub.common.exception.BadRequestException;
import com.moviehub.common.exception.ResourceNotFoundException;
import com.moviehub.review.ReviewRepository;
import com.moviehub.storage.AvatarStorage;
import com.moviehub.user.dto.ProfileDtos.UpdateProfileRequest;
import com.moviehub.user.dto.ProfileDtos.UserStats;
import com.moviehub.user.dto.UserResponse;
import com.moviehub.watchlist.WatchlistRepository;

import lombok.RequiredArgsConstructor;

/** The current user's own account: profile, avatar, stats and deletion. */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AvatarStorage avatarStorage;
    private final PasswordEncoder passwordEncoder;
    private final WatchlistRepository watchlistRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public UserResponse get(long userId) {
        return userMapper.toResponse(load(userId));
    }

    @Transactional
    public UserResponse update(long userId, UpdateProfileRequest request) {
        User user = load(userId);
        user.setFirstName(request.firstName().strip());
        user.setLastName(request.lastName().strip());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateAvatar(long userId, MultipartFile file) {
        User user = load(userId);
        String previous = user.getAvatarUrl();
        String url = avatarStorage.store(userId, file);
        user.setAvatarUrl(url);
        afterCommit(() -> avatarStorage.delete(previous));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse removeAvatar(long userId) {
        User user = load(userId);
        String previous = user.getAvatarUrl();
        user.setAvatarUrl(null);
        afterCommit(() -> avatarStorage.delete(previous));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserStats stats(long userId) {
        return new UserStats(watchlistRepository.countByUserId(userId), reviewRepository.countByUserId(userId));
    }

    /** Permanently deletes the account; watchlist, reviews and sessions go with it (ON DELETE CASCADE). */
    @Transactional
    public void deleteAccount(long userId, String password) {
        User user = load(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Administrator accounts cannot be deleted from here");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Password is incorrect");
        }
        String avatar = user.getAvatarUrl();
        userRepository.delete(user);
        afterCommit(() -> avatarStorage.delete(avatar));
    }

    private User load(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /** File cleanup only once the database change is committed (no orphan reference if it rolls back). */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
