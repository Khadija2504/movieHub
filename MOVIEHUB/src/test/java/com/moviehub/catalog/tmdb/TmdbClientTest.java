package com.moviehub.catalog.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.moviehub.common.exception.ExternalServiceException;
import com.moviehub.common.exception.ResourceNotFoundException;
import com.moviehub.config.AppProperties;

import static org.hamcrest.Matchers.startsWith;

class TmdbClientTest {

    private static final String BASE = "https://api.themoviedb.org/3";

    private MockRestServiceServer server;

    private TmdbClient client(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        if (TmdbClient.isBearerToken(apiKey)) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        var tmdb = new AppProperties.Tmdb(apiKey, BASE, "https://image.tmdb.org/t/p", "en-US",
                Duration.ofSeconds(1), Duration.ofSeconds(1), null);
        return new TmdbClient(builder.build(), new AppProperties(null, tmdb, null, null));
    }

    @Test
    void sendsV3KeyAsQueryParameterAndParsesPage() {
        TmdbClient tmdb = client("abc123");
        server.expect(requestTo(startsWith(BASE + "/discover/movie")))
                .andExpect(queryParam("api_key", "abc123"))
                .andExpect(queryParam("language", "en-US"))
                .andExpect(queryParam("sort_by", "popularity.desc"))
                .andRespond(withSuccess("""
                        {"page":1,"total_pages":3,"total_results":55,
                         "results":[{"id":550,"title":"Fight Club","release_date":"1999-10-15","vote_average":8.4}]}
                        """, MediaType.APPLICATION_JSON));

        var page = tmdb.discover("movie", Map.of("sort_by", "popularity.desc"));

        assertThat(page.totalResults()).isEqualTo(55);
        assertThat(page.results()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(550);
            assertThat(item.title()).isEqualTo("Fight Club");
        });
        server.verify();
    }

    @Test
    void sendsV4TokenAsBearerHeader() {
        TmdbClient tmdb = client("eyJhbGciOiJIUzI1NiJ9.payload.signature");
        server.expect(requestTo(startsWith(BASE + "/genre/movie/list")))
                .andExpect(header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature"))
                .andRespond(withSuccess("{\"genres\":[{\"id\":28,\"name\":\"Action\"}]}", MediaType.APPLICATION_JSON));

        assertThat(tmdb.genres("movie").genres()).extracting("name").containsExactly("Action");
        server.verify();
    }

    @Test
    void mapsNotFound() {
        TmdbClient tmdb = client("abc123");
        server.expect(requestTo(startsWith(BASE + "/movie/999"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> tmdb.movie(999)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mapsServerErrorsToBadGateway() {
        TmdbClient tmdb = client("abc123");
        server.expect(requestTo(startsWith(BASE + "/movie/1"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> tmdb.movie(1))
                .isInstanceOfSatisfying(ExternalServiceException.class, e -> assertThat(e.isUnavailable()).isFalse());
    }

    @Test
    void failsFastWhenNotConfigured() {
        TmdbClient tmdb = client("");

        assertThatThrownBy(() -> tmdb.movie(1))
                .isInstanceOfSatisfying(ExternalServiceException.class, e -> assertThat(e.isUnavailable()).isTrue());
    }
}
