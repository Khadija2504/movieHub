package com.moviehub.cinema;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    @EntityGraph(attributePaths = "rooms")
    @Query("select c from Cinema c where c.active = true order by c.city, c.name")
    List<Cinema> findActiveWithRooms();

    @EntityGraph(attributePaths = "rooms")
    @Query("select c from Cinema c where c.active = true and lower(c.city) = lower(:city) order by c.name")
    List<Cinema> findActiveWithRoomsByCity(String city);

    @EntityGraph(attributePaths = "rooms")
    @Query("select c from Cinema c order by c.city, c.name")
    List<Cinema> findAllWithRooms();

    @EntityGraph(attributePaths = "rooms")
    @Query("select c from Cinema c where c.id = :id")
    Optional<Cinema> findWithRoomsById(long id);

    @Query("select distinct c.city from Cinema c where c.active = true order by c.city")
    List<String> findActiveCities();
}
