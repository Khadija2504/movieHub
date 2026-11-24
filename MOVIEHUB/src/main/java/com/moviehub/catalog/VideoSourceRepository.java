package com.moviehub.catalog;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoSourceRepository extends JpaRepository<VideoSource, Long> {

    @Query("select v from VideoSource v where v.movie.id = :movieId order by v.quality asc nulls first, v.id asc")
    List<VideoSource> findByMovieId(@Param("movieId") Long movieId);

    /** Movies (local ids) among these that have at least one video. */
    @Query("select distinct v.movie.id from VideoSource v where v.movie.id in :movieIds")
    List<Long> findMovieIdsWithVideo(@Param("movieIds") Collection<Long> movieIds);

    boolean existsByMovieId(Long movieId);

    @Modifying
    @Query("delete from VideoSource v where v.movie.id = :movieId")
    void deleteByMovieId(@Param("movieId") Long movieId);
}
