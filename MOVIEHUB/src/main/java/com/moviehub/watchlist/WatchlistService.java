package com.moviehub.watchlist;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviehub.catalog.MediaType;
import com.moviehub.catalog.TitleLookup;
import com.moviehub.catalog.tmdb.TmdbMapper;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.common.exception.BadRequestException;
import com.moviehub.common.exception.ConflictException;
import com.moviehub.user.UserRepository;
import com.moviehub.watchlist.WatchlistDtos.TitleKey;
import com.moviehub.watchlist.WatchlistDtos.WatchlistItemResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    /** Keeps the "keys" endpoint and the list page small. */
    static final int MAX_ITEMS = 500;
    private static final int PAGE_SIZE = 24;

    private final WatchlistRepository repository;
    private final UserRepository userRepository;
    private final TitleLookup titleLookup;
    private final TmdbMapper tmdbMapper;

    @Transactional(readOnly = true)
    public PageResponse<WatchlistItemResponse> list(long userId, MediaType type, int page) {
        var pageable = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        var items = type == null
                ? repository.findByUserId(userId, pageable)
                : repository.findByUserIdAndMediaType(userId, type, pageable);
        return PageResponse.of(items, this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<TitleKey> keys(long userId) {
        return repository.findKeysByUserId(userId);
    }

    /**
     * Adds a title (idempotent: adding twice returns the existing item).
     *
     * @return the item, and whether it was created by this call
     */
    @Transactional
    public AddResult add(long userId, MediaType type, int tmdbId) {
        var existing = repository.findByUserIdAndMediaTypeAndTmdbId(userId, type, tmdbId);
        if (existing.isPresent()) {
            return new AddResult(toResponse(existing.get()), false);
        }
        if (repository.countByUserId(userId) >= MAX_ITEMS) {
            throw new BadRequestException("Your watchlist is full (" + MAX_ITEMS + " titles). Remove some titles first.");
        }
        var snapshot = titleLookup.lookup(type, tmdbId);
        try {
            var item = repository.saveAndFlush(new WatchlistItem(userRepository.getReferenceById(userId), snapshot));
            return new AddResult(toResponse(item), true);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent "add" clicks: the unique constraint caught the second one.
            throw new ConflictException("This title is already in your watchlist");
        }
    }

    public record AddResult(WatchlistItemResponse item, boolean created) {
    }

    /** Removes a title. Removing a title that is not in the watchlist is a no-op. */
    @Transactional
    public void remove(long userId, MediaType type, int tmdbId) {
        repository.findByUserIdAndMediaTypeAndTmdbId(userId, type, tmdbId).ifPresent(repository::delete);
    }

    private WatchlistItemResponse toResponse(WatchlistItem item) {
        return new WatchlistItemResponse(item.getId(), item.getMediaType(), item.getTmdbId(), item.getTitle(),
                tmdbMapper.image(item.getPosterPath(), "w500"), item.getReleaseYear(), item.getCreatedAt());
    }
}
