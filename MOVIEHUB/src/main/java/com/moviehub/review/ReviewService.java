package com.moviehub.review;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moviehub.catalog.MediaType;
import com.moviehub.catalog.TitleLookup;
import com.moviehub.catalog.tmdb.TmdbMapper;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.common.exception.ConflictException;
import com.moviehub.review.ReviewDtos.Author;
import com.moviehub.review.ReviewDtos.ReviewRequest;
import com.moviehub.review.ReviewDtos.ReviewResponse;
import com.moviehub.review.ReviewDtos.ReviewSummary;
import com.moviehub.user.User;
import com.moviehub.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int PAGE_SIZE = 10;
    /** Changes made within this window after creation don't show the "edited" label. */
    private static final Duration EDIT_GRACE = Duration.ofMinutes(1);

    private final ReviewRepository repository;
    private final UserRepository userRepository;
    private final TitleLookup titleLookup;
    private final TmdbMapper tmdbMapper;

    // ---------------------------------------------------------------- public

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> forTitle(MediaType type, int tmdbId, int page) {
        var pageable = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(repository.findByMediaTypeAndTmdbIdAndStatus(type, tmdbId, Review.Status.VISIBLE, pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReviewSummary summary(MediaType type, int tmdbId) {
        List<Long> distribution = new ArrayList<>(Collections.nCopies(10, 0L));
        long count = 0;
        long total = 0;
        for (Object[] row : repository.countByRating(type, tmdbId)) {
            int rating = ((Number) row[0]).intValue();
            long n = ((Number) row[1]).longValue();
            distribution.set(rating - 1, n);
            count += n;
            total += rating * n;
        }
        Double average = count == 0 ? null : Math.round(total * 10.0 / count) / 10.0;
        return new ReviewSummary(average, count, distribution);
    }

    // ---------------------------------------------------------------- current user

    @Transactional(readOnly = true)
    public Optional<ReviewResponse> mine(long userId, MediaType type, int tmdbId) {
        return repository.findByUserIdAndMediaTypeAndTmdbId(userId, type, tmdbId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> byUser(long userId, int page) {
        var pageable = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return PageResponse.of(repository.findByUserId(userId, pageable), this::toResponse);
    }

    /** Creates or updates the user's review of a title. A hidden (moderated) review stays hidden when edited. */
    @Transactional
    public ReviewResponse upsert(long userId, MediaType type, int tmdbId, ReviewRequest request) {
        Review review = repository.findByUserIdAndMediaTypeAndTmdbId(userId, type, tmdbId)
                .orElseGet(() -> new Review(userRepository.getReferenceById(userId), titleLookup.lookup(type, tmdbId)));
        review.setRating(request.rating().shortValue());
        review.setComment(StringUtils.hasText(request.comment()) ? request.comment().strip() : null);
        try {
            return toResponse(repository.saveAndFlush(review));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("You already reviewed this title");
        }
    }

    @Transactional
    public void delete(long userId, MediaType type, int tmdbId) {
        repository.findByUserIdAndMediaTypeAndTmdbId(userId, type, tmdbId).ifPresent(repository::delete);
    }

    // ---------------------------------------------------------------- mapping

    private ReviewResponse toResponse(Review r) {
        User u = r.getUser();
        String lastInitial = StringUtils.hasText(u.getLastName()) ? " " + u.getLastName().charAt(0) + "." : "";
        boolean edited = r.getCreatedAt() != null && r.getUpdatedAt() != null
                && Duration.between(r.getCreatedAt(), r.getUpdatedAt()).compareTo(EDIT_GRACE) > 0;
        return new ReviewResponse(r.getId(), r.getMediaType(), r.getTmdbId(), r.getTitle(),
                tmdbMapper.image(r.getPosterPath(), "w185"), r.getRating(), r.getComment(),
                new Author(u.getId(), u.getFirstName() + lastInitial, u.getAvatarUrl()),
                edited, r.getCreatedAt(), r.getUpdatedAt());
    }
}
