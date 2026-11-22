package com.moviehub.cinema;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.moviehub.catalog.Movie;
import com.moviehub.catalog.MovieRepository;
import com.moviehub.catalog.tmdb.TmdbClient;

/** Cinemas, rooms and screenings: admin rules and public listings. Database changes are rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class CinemaApiIntegrationTest {

    private static final int TMDB_ID = 990_000_001;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Autowired
    MockMvc mvc;

    @Autowired
    MovieRepository movieRepository;

    @MockitoBean
    TmdbClient tmdb;

    private long cinemaId;
    private long roomId;
    private final LocalDate tomorrow = LocalDate.now(PARIS).plusDays(1);

    @BeforeEach
    void setUp() throws Exception {
        Movie movie = new Movie(TMDB_ID);
        movie.setTitle("Test Feature");
        movie.setRuntime(100);
        movie.setPosterPath("/poster.jpg");
        movieRepository.save(movie);

        String cinema = mvc.perform(post("/api/admin/cinemas").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Test Cinema","city":"Testville","address":"1 Main St","timeZone":"Europe/Paris",
                 "latitude":48.85,"longitude":2.35,"active":true}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        cinemaId = ((Number) JsonPath.read(cinema, "$.id")).longValue();

        String room = mvc.perform(post("/api/admin/cinemas/{id}/rooms", cinemaId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Screen 1","screenType":"IMAX",
                                 "layout":{"rows":3,"seatsPerRow":6,"aislesAfter":[3],"vipRows":["C"],"accessibleSeats":["A6"]}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(18))
                .andReturn().getResponse().getContentAsString();
        roomId = ((Number) JsonPath.read(room, "$.id")).longValue();
    }

    private String screening(String localStart, String price, String vipPrice) {
        return """
                {"roomId":%d,"movieTmdbId":%d,"startsAt":"%s","format":"TWO_D","language":"en","subtitles":"fr",
                 "price":%s,"vipPrice":%s}""".formatted(roomId, TMDB_ID, localStart, price, vipPrice);
    }

    private long schedule(String time) throws Exception {
        String body = mvc.perform(post("/api/admin/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content(screening(tomorrow + "T" + time, "12.50", "16.50")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void rejectsOverlappingScreeningsButAllowsBackToBack() throws Exception {
        schedule("18:00"); // 100 min + 15 min cleaning: room busy until 19:55

        mvc.perform(post("/api/admin/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content(screening(tomorrow + "T19:00", "12.50", "16.50")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Screen 1 is busy from 18:00 to 19:55 (Test Feature)"));
        mvc.perform(post("/api/admin/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content(screening(tomorrow + "T16:30", "12.50", "16.50")))
                .andExpect(status().isConflict());

        schedule("19:55");
    }

    @Test
    void validatesScreeningRequests() throws Exception {
        mvc.perform(post("/api/admin/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content(screening(LocalDate.now(PARIS).minusDays(1) + "T18:00", "12.50", "16.50")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content(screening(tomorrow + "T18:00", "12.50", "9.00")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/cinemas").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"X","city":"Y","address":"Z","timeZone":"Mars/Olympus","active":true}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicListingsShowUpcomingShowtimes() throws Exception {
        long first = schedule("14:00");
        schedule("20:30");

        mvc.perform(get("/api/cinemas").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].screenTypes[0]".formatted(cinemaId)).value("IMAX"))
                .andExpect(jsonPath("$[?(@.id == %d)].seatCount".formatted(cinemaId)).value(18));
        mvc.perform(get("/api/cinemas/cities").with(anonymous()))
                .andExpect(jsonPath("$", hasItem("Testville")));

        mvc.perform(get("/api/cinemas/{id}/screenings", cinemaId).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(tomorrow.toString()))
                .andExpect(jsonPath("$.dates", hasItem(tomorrow.toString())))
                .andExpect(jsonPath("$.movies[0].movie.tmdbId").value(TMDB_ID))
                .andExpect(jsonPath("$.movies[0].showtimes", hasSize(2)))
                .andExpect(jsonPath("$.movies[0].showtimes[0].localStart").value(tomorrow + "T14:00:00"))
                .andExpect(jsonPath("$.movies[0].showtimes[0].seatsLeft").value(18))
                .andExpect(jsonPath("$.movies[0].showtimes[0].language").value("EN"));

        mvc.perform(get("/api/movies/now-showing").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(TMDB_ID)));
        mvc.perform(get("/api/movies/{id}/screenings", TMDB_ID).param("city", "testville").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cities", hasItem("Testville")))
                .andExpect(jsonPath("$.cinemas[0].cinema.name").value("Test Cinema"))
                .andExpect(jsonPath("$.cinemas[0].showtimes", hasSize(2)));

        mvc.perform(get("/api/screenings/{id}", first).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookable").value(true))
                .andExpect(jsonPath("$.localEnd").value(tomorrow + "T15:40:00"))
                .andExpect(jsonPath("$.room.screenType").value("IMAX"))
                .andExpect(jsonPath("$.cinema.timeZone").value("Europe/Paris"));

        mvc.perform(get("/api/screenings/{id}/seats", first).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns").value(7))
                .andExpect(jsonPath("$.available").value(18))
                .andExpect(jsonPath("$.seats", hasSize(18)))
                .andExpect(jsonPath("$.seats[?(@.label == 'A4')].gridColumn").value(4))
                .andExpect(jsonPath("$.seats[?(@.label == 'C1')].type").value("VIP"))
                .andExpect(jsonPath("$.seats[?(@.label == 'A6')].type").value("ACCESSIBLE"));
    }

    @Test
    void cancelledScreeningsAndInactiveCinemasAreHidden() throws Exception {
        long id = schedule("14:00");

        mvc.perform(post("/api/admin/screenings/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/cinemas/{id}/screenings", cinemaId).param("date", tomorrow.toString()).with(anonymous()))
                .andExpect(jsonPath("$.movies", hasSize(0)));
        mvc.perform(get("/api/screenings/{id}", id).with(anonymous()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.bookable").value(false));
        // The freed slot can be reused.
        schedule("14:00");

        mvc.perform(put("/api/admin/cinemas/{id}", cinemaId).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Test Cinema","city":"Testville","address":"1 Main St","timeZone":"Europe/Paris","active":false}"""))
                .andExpect(status().isOk());
        mvc.perform(get("/api/cinemas/{id}", cinemaId).with(anonymous())).andExpect(status().isNotFound());
        mvc.perform(get("/api/cinemas").with(anonymous()))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) cinemaId))));
        mvc.perform(get("/api/screenings/{id}", id).with(anonymous())).andExpect(status().isNotFound());
    }

    @Test
    void roomsWithScreeningsCannotBeDeleted() throws Exception {
        schedule("14:00");
        mvc.perform(delete("/api/admin/rooms/{id}", roomId)).andExpect(status().isConflict());

        String other = mvc.perform(post("/api/admin/cinemas/{id}/rooms", cinemaId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Screen 2","screenType":"STANDARD","layout":{"rows":2,"seatsPerRow":4}}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/admin/rooms/{id}", ((Number) JsonPath.read(other, "$.id")).longValue()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/admin/cinemas/{id}/rooms", cinemaId).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"screen 1","screenType":"STANDARD","layout":{"rows":2,"seatsPerRow":4}}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        mvc.perform(post("/api/admin/cinemas").with(user("jane").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/screenings").with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownIdsReturn404() throws Exception {
        mvc.perform(get("/api/cinemas/999999999").with(anonymous())).andExpect(status().isNotFound());
        mvc.perform(get("/api/screenings/999999999/seats").with(anonymous())).andExpect(status().isNotFound());
    }

}
