package com.moviehub.watchlist;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.moviehub.catalog.MediaType;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {

    Page<WatchlistItem> findByUserId(Long userId, Pageable pageable);

    Page<WatchlistItem> findByUserIdAndMediaType(Long userId, MediaType mediaType, Pageable pageable);

    Optional<WatchlistItem> findByUserIdAndMediaTypeAndTmdbId(Long userId, MediaType mediaType, Integer tmdbId);

    long countByUserId(Long userId);

    /** Lightweight projection: which titles are saved (used to show the bookmark state on cards). */
    @Query("select new com.moviehub.watchlist.WatchlistDtos$TitleKey(w.mediaType, w.tmdbId) "
            + "from WatchlistItem w where w.user.id = :userId")
    List<WatchlistDtos.TitleKey> findKeysByUserId(@Param("userId") Long userId);
}
