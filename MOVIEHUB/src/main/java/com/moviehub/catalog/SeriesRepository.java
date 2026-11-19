package com.moviehub.catalog;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeriesRepository extends JpaRepository<Series, Long> {

    Optional<Series> findByTmdbId(Integer tmdbId);

    Page<Series> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
