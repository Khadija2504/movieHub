package com.moviehub;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.moviehub.catalog.tmdb.TmdbClient;
import com.moviehub.catalog.tmdb.TmdbModels;

/** Watchlist, reviews and profile, against the docker-compose database (rolled back), TMDB mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.storage.dir=build/test-storage")
class UserFeaturesIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TmdbClient tmdb;

    private static TmdbModels.MovieDetails movie(int id, String title) {
        return new TmdbModels.MovieDetails(id, title, null, "o", "/poster.jpg", null, "2014-11-05", 169, "Released",
                "en", 8.4, 30000, List.of(), null, null, null);
    }

    private String register(String email) throws Exception {
        String body = """
                {"firstName":"Jane","lastName":"Doe","email":"%s","password":"secret123"}
                """.formatted(email);
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.accessToken");
    }

    @BeforeEach
    void stubTmdb() {
        when(tmdb.movie(157336)).thenReturn(movie(157336, "Interstellar"));
        when(tmdb.movie(27205)).thenReturn(movie(27205, "Inception"));
    }

    @Nested
    class Watchlist {

        @Test
        void addIsIdempotentAndUsesServerSideTitle() throws Exception {
            String token = register("wl1@example.com");
            String body = "{\"mediaType\":\"MOVIE\",\"tmdbId\":157336}";

            mvc.perform(post("/api/users/me/watchlist").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Interstellar"))
                    .andExpect(jsonPath("$.year").value(2014))
                    .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w500/poster.jpg"));

            mvc.perform(post("/api/users/me/watchlist").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/users/me/watchlist/keys").header("Authorization", token))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].mediaType").value("MOVIE"))
                    .andExpect(jsonPath("$[0].tmdbId").value(157336));
        }

        @Test
        void listsAndRemoves() throws Exception {
            String token = register("wl2@example.com");
            for (int id : new int[] {157336, 27205}) {
                mvc.perform(post("/api/users/me/watchlist").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mediaType\":\"MOVIE\",\"tmdbId\":" + id + "}"));
            }
            mvc.perform(get("/api/users/me/watchlist").header("Authorization", token))
                    .andExpect(jsonPath("$.totalResults").value(2));

            mvc.perform(delete("/api/users/me/watchlist/MOVIE/157336").header("Authorization", token))
                    .andExpect(status().isNoContent());

            mvc.perform(get("/api/users/me/watchlist").param("type", "MOVIE").header("Authorization", token))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].title").value("Inception"));
        }

        @Test
        void requiresLogin() throws Exception {
            mvc.perform(get("/api/users/me/watchlist")).andExpect(status().isUnauthorized());
        }

        @Test
        void rejectsInvalidBody() throws Exception {
            String token = register("wl3@example.com");
            mvc.perform(post("/api/users/me/watchlist").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"mediaType\":\"MOVIE\",\"tmdbId\":-4}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Reviews {

        private void review(String token, int rating, String comment) throws Exception {
            String body = comment == null ? "{\"rating\":%d}".formatted(rating)
                    : "{\"rating\":%d,\"comment\":\"%s\"}".formatted(rating, comment);
            mvc.perform(put("/api/reviews/MOVIE/157336").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }

        @Test
        void upsertSummaryAndPublicList() throws Exception {
            String alice = register("alice@example.com");
            String bob = register("bob@example.com");

            review(alice, 9, "Stunning visuals");
            review(bob, 6, null);
            review(alice, 10, "  Even better the second time  "); // update, not a second review

            mvc.perform(get("/api/reviews/MOVIE/157336/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(2))
                    .andExpect(jsonPath("$.average").value(8.0))
                    .andExpect(jsonPath("$.distribution[9]").value(1))
                    .andExpect(jsonPath("$.distribution[5]").value(1));

            // Public, no token; author shows "Jane D." and never the email.
            mvc.perform(get("/api/reviews/MOVIE/157336"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].author.displayName").value("Jane D."))
                    .andExpect(jsonPath("$.content[0].author.email").doesNotExist());

            mvc.perform(get("/api/reviews/MOVIE/157336/mine").header("Authorization", alice))
                    .andExpect(jsonPath("$.rating").value(10))
                    .andExpect(jsonPath("$.comment").value("Even better the second time"))
                    .andExpect(jsonPath("$.title").value("Interstellar"));
        }

        @Test
        void emptySummaryAndMissingReview() throws Exception {
            mvc.perform(get("/api/reviews/MOVIE/27205/summary"))
                    .andExpect(jsonPath("$.count").value(0))
                    .andExpect(jsonPath("$.average").value(nullValue()));
            String token = register("carol@example.com");
            mvc.perform(get("/api/reviews/MOVIE/27205/mine").header("Authorization", token))
                    .andExpect(status().isNoContent());
        }

        @Test
        void validatesRating() throws Exception {
            String token = register("dave@example.com");
            mvc.perform(put("/api/reviews/MOVIE/157336").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":11}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.rating").exists());
        }

        @Test
        void writingRequiresLoginAndDeleteRemovesIt() throws Exception {
            mvc.perform(put("/api/reviews/MOVIE/157336").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                    .andExpect(status().isUnauthorized());

            String token = register("erin@example.com");
            review(token, 7, "Good");
            mvc.perform(get("/api/users/me/reviews").header("Authorization", token))
                    .andExpect(jsonPath("$.totalResults").value(1));
            mvc.perform(delete("/api/reviews/MOVIE/157336").header("Authorization", token))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/reviews/MOVIE/157336/summary")).andExpect(jsonPath("$.count").value(0));
        }
    }

    @Nested
    class Profile {

        @Test
        void updatesNameAndCountsStats() throws Exception {
            String token = register("frank@example.com");
            mvc.perform(put("/api/users/me").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\" Frank \",\"lastName\":\"Ocean\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Frank"));

            mvc.perform(post("/api/users/me/watchlist").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"mediaType\":\"MOVIE\",\"tmdbId\":157336}"));
            mvc.perform(get("/api/users/me/stats").header("Authorization", token))
                    .andExpect(jsonPath("$.watchlistCount").value(1))
                    .andExpect(jsonPath("$.reviewCount").value(0));
        }

        @Test
        void changesPasswordAndRevokesOldSessions() throws Exception {
            String body = "{\"firstName\":\"G\",\"lastName\":\"H\",\"email\":\"gina@example.com\",\"password\":\"secret123\"}";
            String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getContentAsString();
            String token = "Bearer " + JsonPath.read(registered, "$.accessToken");
            String oldRefresh = JsonPath.read(registered, "$.refreshToken");

            mvc.perform(put("/api/users/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"wrong-pass1\",\"newPassword\":\"newsecret456\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Current password is incorrect"));

            mvc.perform(put("/api/users/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"secret123\",\"newPassword\":\"newsecret456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty());

            mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"gina@example.com\",\"password\":\"newsecret456\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void uploadsAvatarAndRejectsNonImages() throws Exception {
            String token = register("hana@example.com");
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

            String response = mvc.perform(multipart("/api/users/me/avatar")
                            .file(new MockMultipartFile("file", "me.png", "image/png", png)).header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarUrl", startsWith("/api/files/avatars/")))
                    .andReturn().getResponse().getContentAsString();
            String url = JsonPath.read(response, "$.avatarUrl");

            mvc.perform(get(url)).andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"));

            // A script renamed to .png is still rejected (magic bytes are checked, not the name / content type).
            mvc.perform(multipart("/api/users/me/avatar")
                            .file(new MockMultipartFile("file", "evil.png", "image/png", "<script>".getBytes()))
                            .header("Authorization", token))
                    .andExpect(status().isBadRequest());

            // Path traversal is blocked (by the security firewall before reaching the controller)...
            mvc.perform(get("/api/files/avatars/..%2F..%2Fapplication.yml")).andExpect(status().is4xxClientError());
            // ...and names we did not generate are simply not found.
            mvc.perform(get("/api/files/avatars/application.yml")).andExpect(status().isNotFound());
        }

        @Test
        void deletesAccountWithPasswordConfirmation() throws Exception {
            String token = register("ivan@example.com");
            mvc.perform(delete("/api/users/me").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"nope-nope1\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(delete("/api/users/me").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"secret123\"}"))
                    .andExpect(status().isNoContent());
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"ivan@example.com\",\"password\":\"secret123\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
