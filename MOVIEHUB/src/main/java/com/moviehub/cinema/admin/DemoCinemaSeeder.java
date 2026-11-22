package com.moviehub.cinema.admin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.moviehub.catalog.Movie;
import com.moviehub.catalog.MovieRepository;
import com.moviehub.catalog.admin.CatalogAdminService;
import com.moviehub.catalog.tmdb.TmdbClient;
import com.moviehub.catalog.tmdb.TmdbModels.MediaItem;
import com.moviehub.cinema.Cinema;
import com.moviehub.cinema.CinemaRepository;
import com.moviehub.cinema.Room;
import com.moviehub.cinema.ScreenType;
import com.moviehub.cinema.ScreeningFormat;
import com.moviehub.cinema.ScreeningRepository;
import com.moviehub.cinema.ScreeningRules;
import com.moviehub.cinema.admin.AdminCinemaDtos.CinemaRequest;
import com.moviehub.cinema.admin.AdminCinemaDtos.CreateRoomRequest;
import com.moviehub.cinema.admin.AdminCinemaDtos.ScreeningRequest;
import com.moviehub.cinema.admin.AdminCinemaDtos.SeatLayoutRequest;

import lombok.RequiredArgsConstructor;

/**
 * Development helper, enabled with DEMO_DATA=true. Creates three fictional cinemas the first time, then on
 * every start fills the next {@value #DAYS} days with screenings of movies currently in theatres (from TMDB),
 * for rooms that have nothing scheduled on a day. Everything goes through {@link CinemaAdminService}, so the
 * normal rules (no overlaps, future start) apply.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class DemoCinemaSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoCinemaSeeder.class);

    static final int DAYS = 7;
    private static final int MOVIES = 8;
    private static final int MIN_RUNTIME = 70;
    private static final LocalTime FIRST_SHOW = LocalTime.of(11, 0);
    private static final LocalTime LAST_SHOW = LocalTime.of(22, 30);
    private static final String ZONE = "Europe/Paris";

    private final CinemaAdminService admin;
    private final CatalogAdminService catalogAdmin;
    private final CinemaRepository cinemaRepository;
    private final ScreeningRepository screeningRepository;
    private final MovieRepository movieRepository;
    private final TmdbClient tmdb;
    private final Clock clock = Clock.systemUTC();

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (cinemaRepository.count() == 0) {
                createCinemas();
            }
            List<Movie> movies = nowPlaying();
            if (movies.isEmpty()) {
                log.warn("Demo data: no movies available from TMDB, screenings not generated");
                return;
            }
            int created = scheduleScreenings(movies);
            log.info("Demo data: {} screening(s) added for the next {} days", created, DAYS);
        } catch (RuntimeException e) {
            log.warn("Demo data skipped: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- cinemas

    private void createCinemas() {
        var paris = admin.createCinema(new CinemaRequest("MovieHub Paris Bastille", "Paris", "12 Rue de la Roquette",
                "75011", "+33 1 00 00 00 01",
                "Our flagship: an IMAX auditorium with laser projection, a Dolby Atmos room and reclining VIP seats.",
                48.8538, 2.3713, ZONE, true));
        room(paris.id(), "Screen 1", ScreenType.IMAX, 12, 22, List.of(4, 18), List.of("I", "J"));
        room(paris.id(), "Screen 2", ScreenType.DOLBY, 10, 18, List.of(3, 15), List.of("H"));
        room(paris.id(), "Screen 3", ScreenType.STANDARD, 8, 14, List.of(7), List.of());

        var lyon = admin.createCinema(new CinemaRequest("MovieHub Lyon Confluence", "Lyon", "Quai Rambaud",
                "69002", "+33 4 00 00 00 02",
                "Riverside multiplex with a 4DX motion room and free parking for evening shows.",
                45.7413, 4.8183, ZONE, true));
        room(lyon.id(), "Screen 1", ScreenType.FOUR_DX, 8, 16, List.of(8), List.of());
        room(lyon.id(), "Screen 2", ScreenType.STANDARD, 10, 18, List.of(4, 14), List.of("J"));

        var marseille = admin.createCinema(new CinemaRequest("MovieHub Marseille Vieux-Port", "Marseille",
                "Quai du Port", "13002", "+33 4 00 00 00 03",
                "A cosy cinema by the old harbour, with a Dolby Atmos room and late-night screenings.",
                43.2962, 5.3694, ZONE, true));
        room(marseille.id(), "Screen 1", ScreenType.DOLBY, 10, 16, List.of(4, 12), List.of("I", "J"));
        room(marseille.id(), "Screen 2", ScreenType.STANDARD, 9, 14, List.of(7), List.of());
        log.info("Demo data: created 3 cinemas");
    }

    private void room(long cinemaId, String name, ScreenType type, int rows, int seatsPerRow, List<Integer> aisles,
            List<String> vipRows) {
        // Two wheelchair spaces at the end of the first row.
        List<String> accessible = List.of("A" + (seatsPerRow - 1), "A" + seatsPerRow);
        admin.createRoom(cinemaId, new CreateRoomRequest(name, type,
                new SeatLayoutRequest(rows, seatsPerRow, aisles, vipRows, accessible)));
    }

    // ---------------------------------------------------------------- movies

    private List<Movie> nowPlaying() {
        List<Movie> movies = new ArrayList<>();
        List<MediaItem> candidates = new ArrayList<>(tmdb.nowPlaying(1).results());
        try {
            candidates.addAll(tmdb.nowPlaying(2).results());
        } catch (RuntimeException e) {
            log.debug("Demo data: second page of movies unavailable: {}", e.getMessage());
        }
        for (MediaItem item : candidates) {
            if (movies.size() == MOVIES) {
                break;
            }
            if (item.posterPath() == null) {
                continue;
            }
            try {
                catalogAdmin.importMovie(item.id());
                movieRepository.findByTmdbId(item.id())
                        // Skip shorts and specials: only feature films get screenings.
                        .filter(m -> m.getRuntime() != null && m.getRuntime() >= MIN_RUNTIME)
                        .ifPresent(movies::add);
            } catch (RuntimeException e) {
                log.debug("Demo data: could not import movie {}: {}", item.id(), e.getMessage());
            }
        }
        return movies;
    }

    // ---------------------------------------------------------------- screenings

    private int scheduleScreenings(List<Movie> movies) {
        int created = 0;
        Instant earliest = clock.instant().plus(Duration.ofMinutes(30));
        for (Cinema cinema : cinemaRepository.findActiveWithRooms()) {
            ZoneId zone = cinema.zone();
            LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
            List<Room> rooms = cinema.getRooms().stream().filter(Room::isActive).toList();
            for (int r = 0; r < rooms.size(); r++) {
                Room room = rooms.get(r);
                for (int d = 0; d < DAYS; d++) {
                    LocalDate day = today.plusDays(d);
                    Instant dayStart = day.atStartOfDay(zone).toInstant();
                    if (screeningRepository.existsByRoomIdAndStartsAtGreaterThanEqualAndStartsAtLessThan(room.getId(),
                            dayStart, day.plusDays(1).atStartOfDay(zone).toInstant())) {
                        continue;
                    }
                    created += scheduleDay(room, day, zone, r, d, movies, earliest);
                }
            }
        }
        return created;
    }

    /** Back-to-back screenings from late morning to late evening, staggered between rooms. */
    private int scheduleDay(Room room, LocalDate day, ZoneId zone, int roomIndex, int dayIndex, List<Movie> movies,
            Instant earliest) {
        int created = 0;
        LocalDateTime start = day.atTime(FIRST_SHOW).plusMinutes(20L * roomIndex);
        for (int slot = 0; !start.toLocalTime().isAfter(LAST_SHOW) && start.toLocalDate().equals(day); slot++) {
            Movie movie = movies.get((roomIndex * 3 + dayIndex + slot) % movies.size());
            if (start.atZone(zone).toInstant().isAfter(earliest)) {
                boolean dubbed = slot % 3 == 2;
                admin.createScreening(new ScreeningRequest(room.getId(), movie.getTmdbId(), start,
                        room.getScreenType() == ScreenType.IMAX && slot % 2 == 1 ? ScreeningFormat.THREE_D
                                : ScreeningFormat.TWO_D,
                        dubbed ? "FR" : "EN", dubbed ? null : "FR", price(room.getScreenType()),
                        price(room.getScreenType()).add(BigDecimal.valueOf(4))));
                created++;
            }
            start = roundUpToQuarter(start.plus(ScreeningRules.slot(movie.getRuntime())));
        }
        return created;
    }

    private static BigDecimal price(ScreenType type) {
        return switch (type) {
            case STANDARD -> new BigDecimal("11.50");
            case DOLBY -> new BigDecimal("14.00");
            case IMAX -> new BigDecimal("16.50");
            case FOUR_DX -> new BigDecimal("18.90");
        };
    }

    private static LocalDateTime roundUpToQuarter(LocalDateTime time) {
        int extra = (15 - time.getMinute() % 15) % 15;
        return time.plusMinutes(extra).withSecond(0).withNano(0);
    }
}
