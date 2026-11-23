package com.moviehub.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"screening", "screening.movie", "screening.room", "screening.room.cinema"})
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findDetailedById(long id);

    @EntityGraph(attributePaths = {"screening", "screening.movie", "screening.room", "screening.room.cinema"})
    @Query("select b from Booking b where b.reference = :reference")
    Optional<Booking> findDetailedByReference(String reference);

    Optional<Booking> findByPaymentId(String paymentId);

    boolean existsByReference(String reference);

    /** Confirmed or pending bookings of screenings that have not started yet, soonest first. */
    @EntityGraph(attributePaths = {"screening", "screening.movie", "screening.room", "screening.room.cinema"})
    @Query(value = """
            select b from Booking b
            where b.user.id = :userId and b.screening.startsAt > :now
              and b.status in (com.moviehub.booking.BookingStatus.CONFIRMED, com.moviehub.booking.BookingStatus.PENDING)
            order by b.screening.startsAt""",
            countQuery = """
            select count(b) from Booking b
            where b.user.id = :userId and b.screening.startsAt > :now
              and b.status in (com.moviehub.booking.BookingStatus.CONFIRMED, com.moviehub.booking.BookingStatus.PENDING)""")
    Page<Booking> findUpcoming(long userId, Instant now, Pageable pageable);

    /** Everything else: past screenings, cancelled and expired bookings, most recent screening first. */
    @EntityGraph(attributePaths = {"screening", "screening.movie", "screening.room", "screening.room.cinema"})
    @Query(value = """
            select b from Booking b
            where b.user.id = :userId and (b.screening.startsAt <= :now
              or b.status in (com.moviehub.booking.BookingStatus.CANCELLED, com.moviehub.booking.BookingStatus.EXPIRED))
            order by b.screening.startsAt desc""",
            countQuery = """
            select count(b) from Booking b
            where b.user.id = :userId and (b.screening.startsAt <= :now
              or b.status in (com.moviehub.booking.BookingStatus.CANCELLED, com.moviehub.booking.BookingStatus.EXPIRED))""")
    Page<Booking> findPast(long userId, Instant now, Pageable pageable);

    @Query("""
            select count(b) from Booking b
            where b.user.id = :userId and b.status = com.moviehub.booking.BookingStatus.CONFIRMED
              and b.screening.startsAt > :now""")
    long countUpcomingConfirmed(long userId, Instant now);

    /** Pending bookings of the user for a screening (replaced when they pick other seats). */
    List<Booking> findByUserIdAndScreeningIdAndStatus(long userId, long screeningId, BookingStatus status);

    /** Pending bookings whose hold ended before {@code before}. */
    @Query("""
            select b from Booking b
            where b.status = com.moviehub.booking.BookingStatus.PENDING and b.expiresAt < :before
            order by b.expiresAt""")
    List<Booking> findPendingExpiredBefore(Instant before, Pageable pageable);

    @Query("""
            select b from Booking b
            where b.screening.id = :screeningId and b.status in
              (com.moviehub.booking.BookingStatus.CONFIRMED, com.moviehub.booking.BookingStatus.PENDING)""")
    List<Booking> findOpenByScreening(long screeningId);
}
