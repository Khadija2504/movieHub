package com.moviehub.streaming;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.moviehub.catalog.Movie;
import com.moviehub.catalog.MovieRepository;
import com.moviehub.catalog.VideoSource;
import com.moviehub.catalog.VideoSourceRepository;
import com.moviehub.catalog.tmdb.TmdbClient;
import com.moviehub.store.StoreService;

import jakarta.persistence.EntityManager;

/**
 * Purchases, rentals, subscriptions (with renewals), access checks, signed play URLs, quality limits and watch
 * progress. Payments use the demo provider; videos are small files in a temporary folder. Database changes are
 * rolled back.
 */
@SpringBootTest(properties = {"app.store.jobs=false", "app.bookings.expiry-job=false",
        "app.payments.stripe.secret-key=", "app.payments.stripe.publishable-key="})
@AutoConfigureMockMvc
@Transactional
class StreamingApiIntegrationTest {

    private static final int TMDB_ID = 990_000_101;
    private static final int RENT_ONLY_TMDB_ID = 990_000_102;
    private static final Path VIDEOS = createVideos();

    @DynamicPropertySource
    static void videos(DynamicPropertyRegistry registry) {
        registry.add("app.streaming.videos-dir", VIDEOS::toString);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MovieRepository movieRepository;

    @Autowired
    VideoSourceRepository videoSourceRepository;

    @Autowired
    StoreService storeService;

    @Autowired
    StreamTokens tokens;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @MockitoBean
    TmdbClient tmdb;

    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        movie(TMDB_ID, "Streaming Feature", true);
        movie(RENT_ONLY_TMDB_ID, "New Release", false);
        alice = register("alice.stream@example.com");
        bob = register("bob.stream@example.com");
    }

    @Test
    void rentThenBuyAndPlay() throws Exception {
        mvc.perform(get("/api/stream/{id}/access", TMDB_ID).header("Authorization", alice))
                .andExpect(jsonPath("$.streamable").value(true))
                .andExpect(jsonPath("$.canWatch").value(false))
                .andExpect(jsonPath("$.rentalPrice").value(0.99));
        mvc.perform(get("/api/stream/{id}", TMDB_ID).header("Authorization", alice))
                .andExpect(status().isForbidden());

        long rental = pay(alice, order(alice, "RENTAL", TMDB_ID));
        mvc.perform(get("/api/orders/{id}", rental).header("Authorization", alice))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.reference", startsWith("MO-")))
                .andExpect(jsonPath("$.movie.tmdbId").value(TMDB_ID));
        mvc.perform(get("/api/orders/{id}", rental).header("Authorization", bob)).andExpect(status().isNotFound());

        mvc.perform(get("/api/stream/{id}/access", TMDB_ID).header("Authorization", alice))
                .andExpect(jsonPath("$.canWatch").value(true))
                .andExpect(jsonPath("$.via").value("RENTAL"))
                .andExpect(jsonPath("$.rentalEndsAt").isNotEmpty());
        mvc.perform(post("/api/orders").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("RENTAL", TMDB_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You are already renting this movie"));

        // The session hands out signed URLs: the HLS stream first, then the MP4.
        String session = mvc.perform(get("/api/stream/{id}", TMDB_ID).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.via").value("RENTAL"))
                .andExpect(jsonPath("$.maxQuality").doesNotExist())
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[0].type").value("HLS"))
                .andExpect(jsonPath("$.sources[1].quality").value(480))
                .andExpect(jsonPath("$.credit").value("Test credit"))
                .andReturn().getResponse().getContentAsString();
        String hls = JsonPath.read(session, "$.sources[0].url");
        String mp4 = JsonPath.read(session, "$.sources[1].url");

        // No login needed for the files: the token is the proof. A rental plays in every quality.
        mvc.perform(get(hls))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.apple.mpegurl"))
                .andExpect(content().string(containsString("1080p/index.m3u8")));
        mvc.perform(get(hls.replace("master.m3u8", "1080p/index.m3u8"))).andExpect(status().isOk());
        mvc.perform(get(mp4)).andExpect(status().isOk()).andExpect(content().contentType("video/mp4"));
        mvc.perform(get(mp4).header(HttpHeaders.RANGE, "bytes=0-99"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-99/2000"));

        // Buying turns the rental into a purchase.
        pay(alice, order(alice, "PURCHASE", TMDB_ID));
        mvc.perform(get("/api/stream/{id}/access", TMDB_ID).header("Authorization", alice))
                .andExpect(jsonPath("$.via").value("PURCHASE"))
                .andExpect(jsonPath("$.owned").value(true));
        mvc.perform(post("/api/orders").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("PURCHASE", TMDB_ID)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/users/me/library").header("Authorization", alice))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].kind").value("PURCHASE"))
                .andExpect(jsonPath("$.content[0].active").value(true));
        mvc.perform(get("/api/users/me/library?filter=RENTED").header("Authorization", alice))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/orders").header("Authorization", alice))
                .andExpect(jsonPath("$.content", hasSize(2)));
        mvc.perform(get("/api/users/me/stats").header("Authorization", alice))
                .andExpect(jsonPath("$.libraryCount").value(1));
        // Bob has nothing.
        mvc.perform(get("/api/stream/{id}", TMDB_ID).header("Authorization", bob)).andExpect(status().isForbidden());
    }

    @Test
    void subscriptionLimitsQualityAndRenews() throws Exception {
        mvc.perform(get("/api/subscriptions/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.contains("BASIC", "STANDARD", "PREMIUM")));
        mvc.perform(get("/api/subscriptions/me").header("Authorization", alice)).andExpect(status().isNoContent());

        String order = mvc.perform(post("/api/subscriptions").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planId\":" + planId("BASIC") + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.amount").value(4.99))
                .andReturn().getResponse().getContentAsString();
        pay(alice, id(order));
        mvc.perform(get("/api/subscriptions/me").header("Authorization", alice))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.plan.code").value("BASIC"))
                .andExpect(jsonPath("$.renewsAt").isNotEmpty());
        mvc.perform(post("/api/subscriptions").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + planId("PREMIUM") + "}"))
                .andExpect(status().isConflict());

        // Basic = 480p: the 720p and 1080p variants are removed, and cannot be fetched directly.
        String session = mvc.perform(get("/api/stream/{id}", TMDB_ID).header("Authorization", alice))
                .andExpect(jsonPath("$.via").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.maxQuality").value(480))
                .andReturn().getResponse().getContentAsString();
        String hls = JsonPath.read(session, "$.sources[0].url");
        mvc.perform(get(hls))
                .andExpect(content().string(containsString("360p/index.m3u8")))
                .andExpect(content().string(not(containsString("720p"))))
                .andExpect(content().string(not(containsString("1080p"))));
        mvc.perform(get(hls.replace("master.m3u8", "1080p/index.m3u8"))).andExpect(status().isForbidden());
        mvc.perform(get(hls.replace("master.m3u8", "360p/seg0.ts")))
                .andExpect(status().isOk()).andExpect(content().contentType("video/mp2t"));

        // Not included in subscriptions: rent or buy only.
        mvc.perform(get("/api/stream/{id}", RENT_ONLY_TMDB_ID).header("Authorization", alice))
                .andExpect(status().isForbidden());

        // Cancel / renew / change plan.
        mvc.perform(post("/api/subscriptions/me/cancel").header("Authorization", alice))
                .andExpect(jsonPath("$.autoRenew").value(false))
                .andExpect(jsonPath("$.endsAt").isNotEmpty())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/subscriptions/me/renew").header("Authorization", alice))
                .andExpect(jsonPath("$.autoRenew").value(true));
        mvc.perform(put("/api/subscriptions/me/plan").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planId\":" + planId("PREMIUM") + "}"))
                .andExpect(jsonPath("$.plan.code").value("BASIC"))
                .andExpect(jsonPath("$.nextPlan.code").value("PREMIUM"));

        // End of the period: renewed on the new plan, charged with the saved (demo) card.
        long subscriptionId = endPeriod(alice);
        storeService.renewIfDue(subscriptionId);
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/subscriptions/me").header("Authorization", alice))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan.code").value("PREMIUM"))
                .andExpect(jsonPath("$.nextPlan").doesNotExist());
        mvc.perform(get("/api/orders").header("Authorization", alice))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].renewal").value(true))
                .andExpect(jsonPath("$.content[0].amount").value(12.99));

        // Cancelled, then the period ends: the subscription ends and access is gone.
        mvc.perform(post("/api/subscriptions/me/cancel").header("Authorization", alice));
        storeService.renewIfDue(endPeriod(alice));
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/subscriptions/me").header("Authorization", alice))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.endReason").value("CANCELLED"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/stream/{id}", TMDB_ID).header("Authorization", alice)).andExpect(status().isForbidden());
    }

    @Test
    void savesProgressAndHistory() throws Exception {
        pay(alice, order(alice, "PURCHASE", TMDB_ID));

        mvc.perform(put("/api/stream/{id}/progress", TMDB_ID).header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"position\":120,\"duration\":1000}"))
                .andExpect(jsonPath("$.resumeAt").value(120))
                .andExpect(jsonPath("$.completed").value(false));
        mvc.perform(get("/api/stream/{id}", TMDB_ID).header("Authorization", alice))
                .andExpect(jsonPath("$.resumeAt").value(120));
        mvc.perform(get("/api/users/me/continue-watching").header("Authorization", alice))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].percent").value(12));

        // Near the end = finished: starts over next time and leaves "Continue watching".
        mvc.perform(put("/api/stream/{id}/progress", TMDB_ID).header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"position\":960,\"duration\":1000}"))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.resumeAt").value(0));
        mvc.perform(get("/api/users/me/continue-watching").header("Authorization", alice))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/users/me/history").header("Authorization", alice))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].percent").value(100))
                .andExpect(jsonPath("$.content[0].canWatch").value(true));

        // Without access, progress is refused.
        mvc.perform(put("/api/stream/{id}/progress", TMDB_ID).header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"position\":10,\"duration\":1000}"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/users/me/history/{id}", TMDB_ID).header("Authorization", alice))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me/history").header("Authorization", alice))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void playUrlsAreSignedAndShortLived() throws Exception {
        long sourceId = videoSourceRepository.findByMovieId(movieRepository.findByTmdbId(TMDB_ID).get().getId())
                .get(0).getId();
        String valid = tokens.issue(new StreamTokens.Grant(1, sourceId, 0, Instant.now().plusSeconds(60)));
        String expired = tokens.issue(new StreamTokens.Grant(1, sourceId, 0, Instant.now().minusSeconds(1)));

        mvc.perform(get("/api/stream/play/{t}/master.m3u8", valid)).andExpect(status().isOk());
        mvc.perform(get("/api/stream/play/{t}/master.m3u8", expired)).andExpect(status().isForbidden());
        String tampered = valid.substring(0, valid.indexOf('.') - 2) + "xx" + valid.substring(valid.indexOf('.'));
        mvc.perform(get("/api/stream/play/{t}/master.m3u8", tampered)).andExpect(status().isForbidden());
        // Nothing outside the stream's folder, and only video file types.
        mvc.perform(get("/api/stream/play/" + valid + "/..%2Fsecret.ts")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/stream/play/" + valid + "/notes.txt")).andExpect(status().isNotFound());
    }

    @Test
    void unpaidCheckoutsExpire() throws Exception {
        long orderId = order(alice, "RENTAL", TMDB_ID);
        mvc.perform(post("/api/orders/{id}/payment", orderId).header("Authorization", alice))
                .andExpect(jsonPath("$.provider").value("DEMO"));
        jdbc.update("update store_orders set expires_at = now() - interval '1 minute' where id = ?", orderId);
        entityManager.clear();

        storeService.expireIfStale(orderId);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", alice))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(post("/api/orders/{id}/confirm", orderId).header("Authorization", alice))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/stream/{id}/access", TMDB_ID).header("Authorization", alice))
                .andExpect(jsonPath("$.canWatch").value(false));
    }

    // ---------------------------------------------------------------- helpers

    private void movie(int tmdbId, String title, boolean inSubscription) {
        Movie movie = new Movie(tmdbId);
        movie.setTitle(title);
        movie.setStreamable(true);
        movie.setInSubscription(inSubscription);
        movie.setPurchasePrice(new BigDecimal("3.99"));
        movie.setRentalPrice(new BigDecimal("0.99"));
        movie.setVideoCredit("Test credit");
        movieRepository.save(movie);
        videoSourceRepository.save(new VideoSource(movie, "local:test/master.m3u8", null));
        videoSourceRepository.save(new VideoSource(movie, "local:test/480.mp4", 480));
    }

    private long order(String user, String kind, int tmdbId) throws Exception {
        return id(mvc.perform(post("/api/orders").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(kind, tmdbId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString());
    }

    private static String orderJson(String kind, int tmdbId) {
        return "{\"kind\":\"" + kind + "\",\"movieId\":" + tmdbId + "}";
    }

    /** Pays an order with the demo provider. */
    private long pay(String user, long orderId) throws Exception {
        mvc.perform(post("/api/orders/{id}/payment", orderId).header("Authorization", user))
                .andExpect(status().isOk());
        mvc.perform(post("/api/orders/{id}/confirm", orderId).header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        return orderId;
    }

    private long planId(String code) {
        return jdbc.queryForObject("select id from subscription_plans where code = ?", Long.class, code);
    }

    /** Moves the end of the user's running subscription period into the past. */
    private long endPeriod(String user) throws Exception {
        entityManager.flush();
        long id = id(mvc.perform(get("/api/subscriptions/me").header("Authorization", user)).andReturn()
                .getResponse().getContentAsString());
        jdbc.update("update user_subscriptions set current_period_end = now() - interval '1 minute' where id = ?", id);
        entityManager.clear();
        return id;
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

    /** test/master.m3u8 with 360p / 720p / 1080p variants, a segment, a stray text file and a 2000-byte MP4. */
    private static Path createVideos() {
        try {
            Path root = Files.createTempDirectory("moviehub-videos");
            Path test = Files.createDirectories(root.resolve("test"));
            Files.writeString(test.resolve("master.m3u8"), String.join("\n", List.of("#EXTM3U",
                    "#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360", "360p/index.m3u8",
                    "#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720", "720p/index.m3u8",
                    "#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080", "1080p/index.m3u8")));
            for (String quality : List.of("360p", "720p", "1080p")) {
                Path dir = Files.createDirectories(test.resolve(quality));
                Files.writeString(dir.resolve("index.m3u8"), "#EXTM3U\n#EXTINF:4.0,\nseg0.ts\n#EXT-X-ENDLIST\n");
                Files.write(dir.resolve("seg0.ts"), new byte[188]);
            }
            Files.writeString(test.resolve("notes.txt"), "not a video");
            Files.writeString(root.resolve("secret.ts"), "outside the stream folder");
            Files.write(test.resolve("480.mp4"), new byte[2000]);
            return root;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
