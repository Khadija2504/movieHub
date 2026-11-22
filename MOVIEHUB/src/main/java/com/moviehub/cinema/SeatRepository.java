package com.moviehub.cinema;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByRoomIdOrderByGridRowAscGridColumnAsc(Long roomId);

    /** Bulk delete; clears the persistence context so no deleted seat stays managed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Seat s where s.room.id = :roomId")
    void deleteByRoomId(long roomId);
}
