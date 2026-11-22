package com.moviehub.cinema;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface RoomRepository extends JpaRepository<Room, Long> {

    /** Locks the room row so that two screenings cannot be scheduled into the same slot concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r join fetch r.cinema where r.id = :id")
    Optional<Room> findByIdForUpdate(long id);

    boolean existsByCinemaIdAndNameIgnoreCase(Long cinemaId, String name);
}
