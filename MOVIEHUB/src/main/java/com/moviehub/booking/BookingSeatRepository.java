package com.moviehub.booking;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    /** Ids of the seats held or sold for a screening. */
    @Query("select bs.seat.id from BookingSeat bs where bs.screening.id = :screeningId and bs.active = true")
    Set<Long> findTakenSeatIds(long screeningId);

    @Query("""
            select bs.screening.id, count(bs) from BookingSeat bs
            where bs.screening.id in :screeningIds and bs.active = true
            group by bs.screening.id""")
    List<Object[]> countTakenRows(Collection<Long> screeningIds);

    /** Number of held or sold seats per screening (screenings without any are missing from the map). */
    default Map<Long, Integer> countTaken(Collection<Long> screeningIds) {
        if (screeningIds.isEmpty()) {
            return Map.of();
        }
        return countTakenRows(screeningIds).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));
    }
}
