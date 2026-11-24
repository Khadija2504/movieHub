package com.moviehub.library;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryRepository extends JpaRepository<LibraryItem, Long> {

    Optional<LibraryItem> findByUserIdAndMovieId(Long userId, Long movieId);

    List<LibraryItem> findByUserId(Long userId);

    @EntityGraph(attributePaths = "movie")
    Page<LibraryItem> findByUserIdOrderByAcquiredAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "movie")
    Page<LibraryItem> findByUserIdAndKindOrderByAcquiredAtDesc(Long userId, LibraryItem.Kind kind, Pageable pageable);

    /** Purchases plus rentals still running. */
    @Query("select count(l) from LibraryItem l where l.user.id = :userId "
            + "and (l.expiresAt is null or l.expiresAt > :now)")
    long countAccessible(@Param("userId") Long userId, @Param("now") Instant now);
}
