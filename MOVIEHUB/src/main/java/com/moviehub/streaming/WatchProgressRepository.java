package com.moviehub.streaming;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchProgressRepository extends JpaRepository<WatchProgress, Long> {

    Optional<WatchProgress> findByUserIdAndMovieId(Long userId, Long movieId);

    @EntityGraph(attributePaths = "movie")
    Page<WatchProgress> findByUserIdOrderByLastWatchedAtDesc(Long userId, Pageable pageable);

    /** Started, not finished, most recent first. */
    @EntityGraph(attributePaths = "movie")
    @Query("select w from WatchProgress w where w.user.id = :userId and w.completed = false "
            + "and w.positionSeconds >= :minPosition order by w.lastWatchedAt desc")
    List<WatchProgress> findInProgress(@Param("userId") Long userId, @Param("minPosition") int minPosition,
            Pageable pageable);

    @Modifying
    @Query("delete from WatchProgress w where w.user.id = :userId")
    int deleteAllByUser(@Param("userId") Long userId);
}
