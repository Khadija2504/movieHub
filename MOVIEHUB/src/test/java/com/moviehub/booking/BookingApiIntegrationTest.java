package com.moviehub.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.moviehub.catalog.Movie;
import com.moviehub.catalog.MovieRepository;
import com.moviehub.catalog.tmdb.TmdbClient;

import jakarta.persistence.EntityManager;

/**
 * Seat holds, demo payments, cancellations, expiry and tickets. Stripe is not configured, so the demo provider
 * is used. Database changes are rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"app.bookings.expiry-job=false", "app.payments.stripe.secret-key=",
        "app.payments.stripe.publishable-key="})
class BookingApiIntegrationTest {

    private static final int TMDB_ID = 990_000_002;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    @Autowired
    MockMvc mvc;

    @Autowired
    MovieRepository movieRepository;

    @Autowired
    BookingService bookingService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @MockitoBean
    TmdbClient tmdb;

    private long screeningId;
    /** Seat ids by label (A1..C6); row C is VIP. */
    private final java.util.Map<String, Long> seats = new java.util.HashMap<>();
    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        Movie movie = new Movie(TMDB_ID);
        movie.setTitle("Booking Feature");
        movie.setRuntime(100);
        movieRepository.save(movie);

        long cinemaId = id(mvc.perform(post("/api/admin/cinemas").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Booking Cinema","city":"Testville","address":"1 Main St","timeZone":"Europe/Paris",
                         "active":true}""")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long roomId = id(mvc.perform(post("/api/admin/cinemas/{id}/rooms", cinemaId).with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Screen 1","screenType":"STANDARD",
                         "layout":{"rows":3,"seatsPerRow":6,"aislesAfter":[3],"vipRows":["C"],"accessibleSeats":[]}}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        screeningId = id(mvc.perform(post("/api/admin/screenings").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"roomId":%d,"movieTmdbId":%d,"startsAt":"%sT20:00","format":"TWO_D","language":"en",
                         "price":10.00,"vipPrice":14.50}""".formatted(roomId, TMDB_ID, LocalDate.now(PARIS).plusDays(2))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        String map = mvc.perform(get("/api/screenings/{id}/seats", screeningId)).andReturn().getResponse()
                .getContentAsString();
        List<String> labels = JsonPath.read(map, "$.seats[*].label");
        List<Number> ids = JsonPath.read(map, "$.seats[*].id");
        for (int i = 0; i < labels.size(); i++) {
            seats.put(labels.get(i), ids.get(i).longValue());
        }
        alice = register("alice.booking@example.com");
        bob = register("bob.booking@example.com");
    }

    @Test
    void holdPayAndUseATicket() throws Exception {
        String held = mvc.perform(post("/api/bookings").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content(request("A1", "C2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reference", startsWith("MH-")))
                .andExpect(jsonPath("$.seats[*].label", contains("A1", "C2")))
                .andExpect(jsonPath("$.total").value(24.5)) // 10.00 + VIP 14.50
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.ticketCode").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long bookingId = id(held);

        // Held seats are taken for everyone else.
        mvc.perform(get("/api/screenings/{id}/seats", screeningId))
                .andExpect(jsonPath("$.available").value(16))
                .andExpect(jsonPath("$.seats[?(@.label == 'A1')].status").value("TAKEN"));
        mvc.perform(post("/api/bookings").header("Authorization", bob).contentType(MediaType.APPLICATION_JSON)
                        .content(request("A1", "A2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Seat A1 was just taken. Please choose another seat."));

        // Nobody else can see or pay it.
        mvc.perform(get("/api/bookings/{id}", bookingId).header("Authorization", bob)).andExpect(status().isNotFound());

        mvc.perform(post("/api/bookings/{id}/payment", bookingId).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("DEMO"))
                .andExpect(jsonPath("$.amount").value(24.5))
                .andExpect(jsonPath("$.currency").value("EUR"));
        String confirmed = mvc.perform(post("/api/bookings/{id}/confirm", bookingId).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.ticketCode", startsWith("MH1.")))
                .andReturn().getResponse().getContentAsString();

        byte[] png = mvc.perform(get("/api/bookings/{id}/ticket.png", bookingId).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(png).startsWith(0x89, 'P', 'N', 'G');

        mvc.perform(get("/api/bookings").header("Authorization", alice))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(bookingId));
        mvc.perform(get("/api/users/me/stats").header("Authorization", alice))
                .andExpect(jsonPath("$.upcomingBookingCount").value(1));

        // Entrance: valid once.
        String code = JsonPath.read(confirmed, "$.ticketCode");
        mvc.perform(post("/api/admin/tickets/check-in").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.booking.reference").value(JsonPath.<String>read(confirmed, "$.reference")));
        mvc.perform(post("/api/admin/tickets/check-in").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("This ticket has already been used"));
        mvc.perform(post("/api/admin/tickets/verify").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.substring(0, code.length() - 2) + "xx\"}"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Invalid ticket code"));
        mvc.perform(post("/api/admin/tickets/verify").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancellingAPaidBookingRefundsItAndFreesTheSeats() throws Exception {
        long bookingId = paidBooking(alice, "B3");

        mvc.perform(post("/api/bookings/{id}/cancel", bookingId).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refunded").value(true))
                .andExpect(jsonPath("$.ticketCode").doesNotExist());
        mvc.perform(get("/api/screenings/{id}/seats", screeningId)).andExpect(jsonPath("$.available").value(18));
        mvc.perform(get("/api/bookings").param("scope", "PAST").header("Authorization", alice))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));

        // The seat can be booked again.
        mvc.perform(post("/api/bookings").header("Authorization", bob).contentType(MediaType.APPLICATION_JSON)
                .content(request("B3"))).andExpect(status().isCreated());
    }

    @Test
    void choosingSeatsAgainReplacesThePreviousHold() throws Exception {
        long first = id(mvc.perform(post("/api/bookings").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content(request("A1", "A2")))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                .content(request("A2", "A3"))).andExpect(status().isCreated());

        mvc.perform(get("/api/bookings/{id}", first).header("Authorization", alice))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/screenings/{id}/seats", screeningId)).andExpect(jsonPath("$.available").value(16));
    }

    @Test
    void unpaidHoldsExpire() throws Exception {
        long bookingId = id(mvc.perform(post("/api/bookings").header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON).content(request("A1")))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/bookings/{id}/payment", bookingId).header("Authorization", alice));

        jdbc.update("update bookings set expires_at = now() - interval '1 minute' where id = ?", bookingId);
        entityManager.clear();
        assertThat(bookingService.staleHoldIds(100)).contains(bookingId);
        bookingService.expireIfStale(bookingId);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/bookings/{id}", bookingId).header("Authorization", alice))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(post("/api/bookings/{id}/confirm", bookingId).header("Authorization", alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Your seat hold has expired. Please choose your seats again."));
        mvc.perform(get("/api/screenings/{id}/seats", screeningId)).andExpect(jsonPath("$.available").value(18));
    }

    @Test
    void cancellingTheScreeningCancelsAndRefundsBookings() throws Exception {
        long bookingId = paidBooking(alice, "C1");

        mvc.perform(post("/api/admin/screenings/{id}/cancel", screeningId).with(ADMIN)).andExpect(status().isOk());

        mvc.perform(get("/api/bookings/{id}", bookingId).header("Authorization", alice))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refunded").value(true));
    }

    @Test
    void validatesBookingRequests() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(request("A1")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                .content("{\"screeningId\":%d,\"seatIds\":[]}".formatted(screeningId)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                .content("{\"screeningId\":%d,\"seatIds\":[%d,%d]}".formatted(screeningId, seats.get("A1"),
                        seats.get("A1"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                .content("{\"screeningId\":%d,\"seatIds\":[-5]}".formatted(screeningId)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                .content(request("A1", "A2", "A3", "A4", "A5", "A6", "B1", "B2", "B3", "B4", "B5")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                .content("{\"screeningId\":999999999,\"seatIds\":[1]}"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    private long paidBooking(String token, String... labels) throws Exception {
        long bookingId = id(mvc.perform(post("/api/bookings").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(request(labels)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/bookings/{id}/payment", bookingId).header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/bookings/{id}/confirm", bookingId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        return bookingId;
    }

    private String request(String... labels) {
        String ids = String.join(",", java.util.Arrays.stream(labels).map(l -> seats.get(l).toString()).toList());
        return "{\"screeningId\":%d,\"seatIds\":[%s]}".formatted(screeningId, ids);
    }

    private String register(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Jane","lastName":"Doe","email":"%s","password":"secret123"}""".formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.accessToken");
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
