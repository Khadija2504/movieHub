package com.moviehub.cinema.admin;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moviehub.catalog.Movie;
import com.moviehub.catalog.MovieRepository;
import com.moviehub.catalog.admin.CatalogAdminService;
import com.moviehub.cinema.Cinema;
import com.moviehub.cinema.CinemaDtos.CinemaDetails;
import com.moviehub.cinema.CinemaDtos.RoomSummary;
import com.moviehub.cinema.CinemaMapper;
import com.moviehub.cinema.CinemaRepository;
import com.moviehub.cinema.Room;
import com.moviehub.cinema.RoomRepository;
import com.moviehub.cinema.Screening;
import com.moviehub.cinema.ScreeningRepository;
import com.moviehub.cinema.ScreeningRules;
import com.moviehub.cinema.Seat;
import com.moviehub.cinema.SeatLayout;
import com.moviehub.cinema.SeatRepository;
import com.moviehub.cinema.admin.AdminCinemaDtos.AdminScreening;
import com.moviehub.cinema.admin.AdminCinemaDtos.CinemaRequest;
import com.moviehub.cinema.admin.AdminCinemaDtos.CreateRoomRequest;
import com.moviehub.cinema.admin.AdminCinemaDtos.ScreeningRequest;
import com.moviehub.cinema.admin.AdminCinemaDtos.UpdateRoomRequest;
import com.moviehub.common.exception.BadRequestException;
import com.moviehub.common.exception.ConflictException;
import com.moviehub.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/** Admin side of cinemas: cinemas, rooms (with their seats) and the screening schedule. */
@Service
@RequiredArgsConstructor
public class CinemaAdminService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CinemaRepository cinemaRepository;
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final ScreeningRepository screeningRepository;
    private final MovieRepository movieRepository;
    private final CatalogAdminService catalogAdminService;
    private final CinemaMapper mapper;
    private final Clock clock = Clock.systemUTC();

    // ---------------------------------------------------------------- cinemas

    @Transactional(readOnly = true)
    public List<CinemaDetails> listCinemas() {
        return cinemaRepository.findAllWithRooms().stream().map(c -> mapper.toDetails(c, true)).toList();
    }

    @Transactional(readOnly = true)
    public CinemaDetails getCinema(long id) {
        return mapper.toDetails(cinemaWithRooms(id), true);
    }

    @Transactional
    public CinemaDetails createCinema(CinemaRequest request) {
        Cinema cinema = new Cinema(request.name().strip(), request.city().strip(), request.address().strip(),
                validZone(request.timeZone()));
        apply(cinema, request);
        return mapper.toDetails(cinemaRepository.save(cinema), true);
    }

    @Transactional
    public CinemaDetails updateCinema(long id, CinemaRequest request) {
        Cinema cinema = cinemaWithRooms(id);
        cinema.setName(request.name().strip());
        cinema.setCity(request.city().strip());
        cinema.setAddress(request.address().strip());
        cinema.setTimeZone(validZone(request.timeZone()));
        apply(cinema, request);
        return mapper.toDetails(cinema, true);
    }

    private static void apply(Cinema cinema, CinemaRequest request) {
        cinema.setPostalCode(trimToNull(request.postalCode()));
        cinema.setPhone(trimToNull(request.phone()));
        cinema.setDescription(trimToNull(request.description()));
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new BadRequestException("Latitude and longitude must be given together");
        }
        cinema.setLatitude(request.latitude());
        cinema.setLongitude(request.longitude());
        cinema.setActive(request.active());
    }

    // ---------------------------------------------------------------- rooms

    @Transactional
    public RoomSummary createRoom(long cinemaId, CreateRoomRequest request) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", cinemaId));
        String name = request.name().strip();
        if (roomRepository.existsByCinemaIdAndNameIgnoreCase(cinemaId, name)) {
            throw new ConflictException("This cinema already has a room named " + name);
        }
        var l = request.layout();
        SeatLayout layout = SeatLayout.generate(l.rows(), l.seatsPerRow(), l.aislesAfter(), l.vipRows(),
                l.accessibleSeats());
        Room room = roomRepository.save(new Room(cinema, name, request.screenType(), layout));
        seatRepository.saveAll(layout.seats().stream().map(spec -> new Seat(room, spec)).toList());
        return mapper.toRoom(room);
    }

    @Transactional
    public RoomSummary updateRoom(long roomId, UpdateRoomRequest request) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("Room", roomId));
        String name = request.name().strip();
        if (!room.getName().equalsIgnoreCase(name)
                && roomRepository.existsByCinemaIdAndNameIgnoreCase(room.getCinema().getId(), name)) {
            throw new ConflictException("This cinema already has a room named " + name);
        }
        room.setName(name);
        room.setScreenType(request.screenType());
        room.setActive(request.active());
        return mapper.toRoom(room);
    }

    /** Only rooms that never had a screening can be deleted; others are deactivated instead. */
    @Transactional
    public void deleteRoom(long roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new ResourceNotFoundException("Room", roomId);
        }
        if (screeningRepository.existsByRoomId(roomId)) {
            throw new ConflictException("This room has screenings: deactivate it instead of deleting it");
        }
        seatRepository.deleteByRoomId(roomId);
        roomRepository.deleteById(roomId);
    }

    // ---------------------------------------------------------------- screenings

    /** Schedule of a cinema for one local day, including cancelled screenings. */
    @Transactional(readOnly = true)
    public List<AdminScreening> screenings(long cinemaId, LocalDate date) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", cinemaId));
        ZoneId zone = cinema.zone();
        return screeningRepository.findAllInCinema(cinemaId, date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant()).stream().map(this::toAdmin).toList();
    }

    /**
     * Schedules a screening. The movie is imported from TMDB if it is not in the catalog yet. The room is locked
     * while checking for overlaps, so two admins cannot book the same slot at the same time.
     */
    @Transactional
    public AdminScreening createScreening(ScreeningRequest request) {
        if (request.vipPrice().compareTo(request.price()) < 0) {
            throw new BadRequestException("The VIP price cannot be lower than the standard price");
        }
        Room room = roomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", request.roomId()));
        if (!room.isActive() || !room.getCinema().isActive()) {
            throw new BadRequestException("Screenings can only be scheduled in active rooms of active cinemas");
        }
        Movie movie = catalogMovie(request.movieTmdbId());

        ZoneId zone = room.getCinema().zone();
        Instant start = request.startsAt().atZone(zone).toInstant();
        if (!start.isAfter(clock.instant())) {
            throw new BadRequestException("A screening must start in the future");
        }
        Instant end = start.plus(ScreeningRules.slot(movie.getRuntime()));

        List<Screening> overlapping = screeningRepository.findOverlapping(room.getId(), start, end);
        if (!overlapping.isEmpty()) {
            Screening other = overlapping.get(0);
            throw new ConflictException("%s is busy from %s to %s (%s)".formatted(room.getName(),
                    TIME.format(other.getStartsAt().atZone(zone)), TIME.format(other.getEndsAt().atZone(zone)),
                    other.getMovie().getTitle()));
        }

        Screening screening = new Screening(room, movie, start, end);
        screening.setFormat(request.format());
        screening.setLanguage(request.language().toUpperCase(Locale.ROOT));
        screening.setSubtitles(StringUtils.hasText(request.subtitles())
                ? request.subtitles().toUpperCase(Locale.ROOT) : null);
        screening.setPrice(request.price());
        screening.setVipPrice(request.vipPrice());
        return toAdmin(screeningRepository.save(screening));
    }

    /** Cancels an upcoming screening. Refunding its bookings is added with the booking module (Phase 5). */
    @Transactional
    public AdminScreening cancelScreening(long id) {
        Screening screening = screeningRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Screening", id));
        if (screening.getStatus() == Screening.Status.CANCELLED) {
            return toAdmin(screening);
        }
        if (!screening.getStartsAt().isAfter(clock.instant())) {
            throw new BadRequestException("A screening that has already started cannot be cancelled");
        }
        screening.setStatus(Screening.Status.CANCELLED);
        return toAdmin(screening);
    }

    // ---------------------------------------------------------------- helpers

    private Movie catalogMovie(int tmdbId) {
        return movieRepository.findByTmdbId(tmdbId).orElseGet(() -> {
            catalogAdminService.importMovie(tmdbId);
            return movieRepository.findByTmdbId(tmdbId)
                    .orElseThrow(() -> new ResourceNotFoundException("Movie", tmdbId));
        });
    }

    private Cinema cinemaWithRooms(long id) {
        return cinemaRepository.findWithRoomsById(id).orElseThrow(() -> new ResourceNotFoundException("Cinema", id));
    }

    private AdminScreening toAdmin(Screening s) {
        ZoneId zone = s.getRoom().getCinema().zone();
        return new AdminScreening(s.getId(), s.getRoom().getId(), s.getRoom().getName(), s.getMovie().getTmdbId(),
                s.getMovie().getTitle(), s.getStartsAt(), s.getEndsAt(), LocalDateTime.ofInstant(s.getStartsAt(), zone),
                LocalDateTime.ofInstant(s.getEndsAt(), zone), s.getFormat(), s.getLanguage(), s.getSubtitles(),
                s.getPrice(), s.getVipPrice(), s.getStatus());
    }

    private static String validZone(String zone) {
        try {
            return ZoneId.of(zone.strip()).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown time zone: " + zone);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
