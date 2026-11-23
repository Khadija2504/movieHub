package com.moviehub.cinema;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.moviehub.catalog.Movie;

import jakarta.persistence.LockModeType;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    /** Scheduled screenings in the room that overlap [start, end). */
    @EntityGraph(attributePaths = "movie")
    @Query("""
            select s from Screening s
            where s.room.id = :roomId and s.status = com.moviehub.cinema.Screening.Status.SCHEDULED
              and s.startsAt < :end and s.endsAt > :start
            order by s.startsAt""")
    List<Screening> findOverlapping(long roomId, Instant start, Instant end);

    boolean existsByRoomId(Long roomId);

    /** Scheduled screenings of active rooms in a cinema, starting in [from, to). */
    @EntityGraph(attributePaths = {"movie", "room", "room.cinema"})
    @Query("""
            select s from Screening s
            where s.room.cinema.id = :cinemaId and s.status = com.moviehub.cinema.Screening.Status.SCHEDULED
              and s.room.active = true and s.startsAt >= :from and s.startsAt < :to
            order by s.startsAt""")
    List<Screening> findScheduledInCinema(long cinemaId, Instant from, Instant to);

    /** Scheduled screenings of a movie (by TMDB id) in active cinemas, starting in [from, to). */
    @EntityGraph(attributePaths = {"movie", "room", "room.cinema"})
    @Query("""
            select s from Screening s
            where s.movie.tmdbId = :tmdbId and s.status = com.moviehub.cinema.Screening.Status.SCHEDULED
              and s.room.active = true and s.room.cinema.active = true and s.startsAt >= :from and s.startsAt < :to
            order by s.startsAt""")
    List<Screening> findScheduledForMovie(int tmdbId, Instant from, Instant to);

    /** Movies with scheduled screenings in [from, to), the most programmed first. */
    @Query("""
            select s.movie from Screening s
            where s.status = com.moviehub.cinema.Screening.Status.SCHEDULED
              and s.room.active = true and s.room.cinema.active = true and s.startsAt >= :from and s.startsAt < :to
            group by s.movie
            order by count(s) desc, s.movie.title""")
    List<Movie> findMoviesShowing(Instant from, Instant to, Pageable pageable);

    @EntityGraph(attributePaths = {"movie", "room", "room.cinema"})
    @Query("select s from Screening s where s.id = :id")
    Optional<Screening> findDetailedById(long id);

    /** Locks the screening row: bookings of one screening are made one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Screening s join fetch s.movie join fetch s.room r join fetch r.cinema
            where s.id = :id""")
    Optional<Screening> findByIdForUpdate(long id);

    /** All screenings (any status) of a cinema starting in [from, to), for the admin. */
    @EntityGraph(attributePaths = {"movie", "room", "room.cinema"})
    @Query("""
            select s from Screening s
            where s.room.cinema.id = :cinemaId and s.startsAt >= :from and s.startsAt < :to
            order by s.startsAt""")
    List<Screening> findAllInCinema(long cinemaId, Instant from, Instant to);

    boolean existsByRoomIdAndStartsAtGreaterThanEqualAndStartsAtLessThan(Long roomId, Instant from, Instant to);
}
