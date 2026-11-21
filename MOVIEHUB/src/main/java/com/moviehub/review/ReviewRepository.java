package com.moviehub.review;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.moviehub.catalog.MediaType;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Public list for a title (author loaded for display). */
    @EntityGraph(attributePaths = "user")
    Page<Review> findByMediaTypeAndTmdbIdAndStatus(MediaType mediaType, Integer tmdbId, Review.Status status,
            Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<Review> findByUserIdAndMediaTypeAndTmdbId(Long userId, MediaType mediaType, Integer tmdbId);

    @EntityGraph(attributePaths = "user")
    Page<Review> findByUserId(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    /** [rating, count] rows of visible reviews for a title. */
    @Query("select r.rating, count(r) from Review r where r.mediaType = :type and r.tmdbId = :tmdbId "
            + "and r.status = com.moviehub.review.Review.Status.VISIBLE group by r.rating")
    List<Object[]> countByRating(@Param("type") MediaType type, @Param("tmdbId") Integer tmdbId);
}
