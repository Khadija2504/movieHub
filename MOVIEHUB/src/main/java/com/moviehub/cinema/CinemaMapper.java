package com.moviehub.cinema;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.moviehub.catalog.MediaType;
import com.moviehub.catalog.Movie;
import com.moviehub.catalog.dto.CatalogDtos.MediaSummary;
import com.moviehub.catalog.tmdb.TmdbMapper;
import com.moviehub.cinema.CinemaDtos.CinemaDetails;
import com.moviehub.cinema.CinemaDtos.CinemaRef;
import com.moviehub.cinema.CinemaDtos.CinemaSummary;
import com.moviehub.cinema.CinemaDtos.RoomSummary;
import com.moviehub.cinema.CinemaDtos.ScreeningDetails;
import com.moviehub.cinema.CinemaDtos.ScreeningMovie;
import com.moviehub.cinema.CinemaDtos.Showtime;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CinemaMapper {

    private static final String POSTER = "w500";
    private static final String BACKDROP = "w1280";

    private final TmdbMapper tmdbMapper;

    public CinemaSummary toSummary(Cinema c) {
        List<Room> rooms = c.getRooms().stream().filter(Room::isActive).toList();
        List<ScreenType> types = rooms.stream().map(Room::getScreenType).distinct().sorted().toList();
        int seats = rooms.stream().mapToInt(Room::getCapacity).sum();
        return new CinemaSummary(c.getId(), c.getName(), c.getCity(), c.getAddress(), c.getPostalCode(),
                c.getLatitude(), c.getLongitude(), rooms.size(), seats, types);
    }

    /** @param includeInactiveRooms true for the admin, false for the public page */
    public CinemaDetails toDetails(Cinema c, boolean includeInactiveRooms) {
        List<RoomSummary> rooms = c.getRooms().stream()
                .filter(r -> includeInactiveRooms || r.isActive())
                .map(this::toRoom).toList();
        return new CinemaDetails(c.getId(), c.getName(), c.getCity(), c.getAddress(), c.getPostalCode(), c.getPhone(),
                c.getDescription(), c.getLatitude(), c.getLongitude(), c.getTimeZone(), c.isActive(), rooms);
    }

    public RoomSummary toRoom(Room r) {
        return new RoomSummary(r.getId(), r.getName(), r.getScreenType(), r.getCapacity(), r.isActive());
    }

    public CinemaRef toRef(Cinema c) {
        return new CinemaRef(c.getId(), c.getName(), c.getCity(), c.getAddress(), c.getLatitude(), c.getLongitude(),
                c.getTimeZone());
    }

    public ScreeningMovie toMovie(Movie m) {
        return new ScreeningMovie(m.getTmdbId(), m.getTitle(), tmdbMapper.image(m.getPosterPath(), POSTER),
                tmdbMapper.image(m.getBackdropPath(), BACKDROP), m.getRuntime(), round(m.getVoteAverage()),
                m.getReleaseDate() == null ? null : m.getReleaseDate().getYear());
    }

    public MediaSummary toMediaSummary(Movie m) {
        return new MediaSummary(m.getTmdbId(), MediaType.MOVIE, m.getTitle(), m.getOverview(),
                tmdbMapper.image(m.getPosterPath(), POSTER), tmdbMapper.image(m.getBackdropPath(), BACKDROP),
                m.getReleaseDate(), m.getReleaseDate() == null ? null : m.getReleaseDate().getYear(),
                round(m.getVoteAverage()), null, List.of());
    }

    public Showtime toShowtime(Screening s, int seatsLeft) {
        Room room = s.getRoom();
        return new Showtime(s.getId(), s.getStartsAt(), localStart(s), s.getFormat(), s.getLanguage(),
                s.getSubtitles(), room.getId(), room.getName(), room.getScreenType(), s.getPrice(), seatsLeft);
    }

    public ScreeningDetails toDetails(Screening s, boolean bookable) {
        Cinema cinema = s.getRoom().getCinema();
        // The movie itself ends before the room is free again (endsAt includes cleaning time).
        Integer runtime = s.getMovie().getRuntime();
        var movieEnd = runtime != null && runtime > 0 ? s.getStartsAt().plus(Duration.ofMinutes(runtime))
                : s.getEndsAt().minus(ScreeningRules.CLEANING_TIME);
        return new ScreeningDetails(s.getId(), s.getStartsAt(), movieEnd, localStart(s),
                LocalDateTime.ofInstant(movieEnd, cinema.zone()), s.getFormat(), s.getLanguage(), s.getSubtitles(),
                s.getPrice(), s.getVipPrice(), s.getStatus(), bookable, toMovie(s.getMovie()), toRef(cinema),
                toRoom(s.getRoom()));
    }

    private static LocalDateTime localStart(Screening s) {
        return LocalDateTime.ofInstant(s.getStartsAt(), s.getRoom().getCinema().zone());
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
