package com.moviehub.cinema;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moviehub.booking.BookingSeatRepository;
import com.moviehub.catalog.dto.CatalogDtos.MediaSummary;
import com.moviehub.cinema.CinemaDtos.CinemaDetails;
import com.moviehub.cinema.CinemaDtos.CinemaSchedule;
import com.moviehub.cinema.CinemaDtos.CinemaShowtimes;
import com.moviehub.cinema.CinemaDtos.CinemaSummary;
import com.moviehub.cinema.CinemaDtos.MovieSchedule;
import com.moviehub.cinema.CinemaDtos.MovieShowtimes;
import com.moviehub.cinema.CinemaDtos.ScreeningDetails;
import com.moviehub.cinema.CinemaDtos.SeatMap;
import com.moviehub.cinema.CinemaDtos.SeatStatus;
import com.moviehub.cinema.CinemaDtos.SeatView;
import com.moviehub.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/** Read-only, public side of cinemas: listings, showtimes, screening details and seat maps. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CinemaService {

    private static final int NOW_SHOWING_LIMIT = 20;

    private final CinemaRepository cinemaRepository;
    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final CinemaMapper mapper;
    private final Clock clock = Clock.systemUTC();

    // ---------------------------------------------------------------- cinemas

    public List<CinemaSummary> list(String city) {
        List<Cinema> cinemas = StringUtils.hasText(city)
                ? cinemaRepository.findActiveWithRoomsByCity(city.strip())
                : cinemaRepository.findActiveWithRooms();
        return cinemas.stream().map(mapper::toSummary).toList();
    }

    public List<String> cities() {
        return cinemaRepository.findActiveCities();
    }

    public CinemaDetails get(long id) {
        return mapper.toDetails(activeCinema(id), false);
    }

    /**
     * Showtimes of a cinema for one day (cinema local time). Without a date, the first day that has showtimes.
     * Screenings that already started are not listed.
     */
    public CinemaSchedule schedule(long cinemaId, LocalDate date) {
        Cinema cinema = activeCinema(cinemaId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, cinema.zone());
        Instant until = today.plusDays(ScreeningRules.SCHEDULE_DAYS).atStartOfDay(cinema.zone()).toInstant();

        List<Screening> upcoming = screeningRepository.findScheduledInCinema(cinemaId, now, until);
        List<LocalDate> dates = upcoming.stream().map(CinemaService::localDate).distinct().sorted().toList();
        LocalDate selected = date != null ? date : dates.isEmpty() ? today : dates.get(0);

        List<Screening> ofDay = upcoming.stream().filter(s -> localDate(s).equals(selected)).toList();
        Map<Long, Integer> taken = takenSeats(ofDay);
        Map<Long, List<Screening>> byMovie = groupBy(ofDay, s -> s.getMovie().getId());
        List<MovieShowtimes> movies = byMovie.values().stream()
                .map(list -> new MovieShowtimes(mapper.toMovie(list.get(0).getMovie()),
                        list.stream().map(s -> toShowtime(s, taken)).toList()))
                // Most programmed movies first, then alphabetical.
                .sorted(Comparator.<MovieShowtimes>comparingInt(m -> -m.showtimes().size())
                        .thenComparing(m -> m.movie().title(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new CinemaSchedule(selected, dates, movies);
    }

    // ---------------------------------------------------------------- movies

    /** Movies with upcoming screenings, the most programmed first. */
    public List<MediaSummary> nowShowing() {
        Instant now = clock.instant();
        return screeningRepository
                .findMoviesShowing(now, now.plus(Duration.ofDays(ScreeningRules.SCHEDULE_DAYS)),
                        PageRequest.of(0, NOW_SHOWING_LIMIT))
                .stream().map(mapper::toMediaSummary).toList();
    }

    /** Showtimes of a movie in every cinema for one day, optionally in one city. */
    public MovieSchedule movieSchedule(int tmdbId, LocalDate date, String city) {
        Instant now = clock.instant();
        // One extra day: cinemas in different zones do not share the same "today".
        List<Screening> upcoming = screeningRepository.findScheduledForMovie(tmdbId, now,
                now.plus(Duration.ofDays(ScreeningRules.SCHEDULE_DAYS + 1L)));

        List<String> cities = upcoming.stream().map(s -> s.getRoom().getCinema().getCity()).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (StringUtils.hasText(city)) {
            upcoming = upcoming.stream()
                    .filter(s -> s.getRoom().getCinema().getCity().equalsIgnoreCase(city.strip())).toList();
        }

        List<LocalDate> dates = upcoming.stream().map(CinemaService::localDate).distinct().sorted()
                .limit(ScreeningRules.SCHEDULE_DAYS).toList();
        LocalDate selected = date != null ? date : dates.isEmpty() ? LocalDate.ofInstant(now, clock.getZone())
                : dates.get(0);

        List<Screening> ofDay = upcoming.stream().filter(s -> localDate(s).equals(selected)).toList();
        Map<Long, Integer> taken = takenSeats(ofDay);
        Map<Long, List<Screening>> byCinema = groupBy(ofDay, s -> s.getRoom().getCinema().getId());
        List<CinemaShowtimes> cinemas = byCinema.values().stream()
                .map(list -> new CinemaShowtimes(mapper.toRef(list.get(0).getRoom().getCinema()),
                        list.stream().map(s -> toShowtime(s, taken)).toList()))
                .sorted(Comparator.comparing((CinemaShowtimes c) -> c.cinema().city(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(c -> c.cinema().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new MovieSchedule(selected, dates, cities, cinemas);
    }

    // ---------------------------------------------------------------- screenings

    public ScreeningDetails screening(long id) {
        Screening screening = visibleScreening(id);
        return mapper.toDetails(screening, screening.isBookable(clock.instant()));
    }

    /** Every seat of the room with its status for this screening. */
    public SeatMap seats(long screeningId) {
        Screening screening = visibleScreening(screeningId);
        Room room = screening.getRoom();
        // Held (being paid) and sold seats are both TAKEN.
        Set<Long> taken = bookingSeatRepository.findTakenSeatIds(screening.getId());
        List<SeatView> seats = seatRepository.findByRoomIdOrderByGridRowAscGridColumnAsc(room.getId()).stream()
                .map(seat -> new SeatView(seat.getId(), seat.label(), seat.getRowLabel(), seat.getNumber(),
                        seat.getGridRow(), seat.getGridColumn(), seat.getType(),
                        taken.contains(seat.getId()) ? SeatStatus.TAKEN : SeatStatus.AVAILABLE))
                .toList();
        int available = (int) seats.stream().filter(s -> s.status() == SeatStatus.AVAILABLE).count();
        return new SeatMap(screening.getId(), room.getRowCount(), room.getColumnCount(), room.getCapacity(),
                available, seats);
    }

    // ---------------------------------------------------------------- helpers

    private Cinema activeCinema(long id) {
        return cinemaRepository.findWithRoomsById(id).filter(Cinema::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", id));
    }

    /** Screenings of deactivated cinemas are hidden from the public. */
    private Screening visibleScreening(long id) {
        return screeningRepository.findDetailedById(id)
                .filter(s -> s.getRoom().getCinema().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Screening", id));
    }

    private Map<Long, Integer> takenSeats(List<Screening> screenings) {
        return bookingSeatRepository.countTaken(screenings.stream().map(Screening::getId).toList());
    }

    private CinemaDtos.Showtime toShowtime(Screening s, Map<Long, Integer> taken) {
        return mapper.toShowtime(s, Math.max(0, s.getRoom().getCapacity() - taken.getOrDefault(s.getId(), 0)));
    }

    private static LocalDate localDate(Screening s) {
        return LocalDateTime.ofInstant(s.getStartsAt(), s.getRoom().getCinema().zone()).toLocalDate();
    }

    /** Groups while keeping the input order (screenings are sorted by start time). */
    private static <K> Map<K, List<Screening>> groupBy(List<Screening> screenings, Function<Screening, K> key) {
        Map<K, List<Screening>> groups = new LinkedHashMap<>();
        for (Screening s : screenings) {
            groups.computeIfAbsent(key.apply(s), k -> new ArrayList<>()).add(s);
        }
        return groups;
    }
}
