package com.moviehub.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.moviehub.catalog.tmdb.TmdbClient;
import com.moviehub.catalog.tmdb.TmdbModels;
import com.moviehub.common.exception.ExternalServiceException;
import com.moviehub.common.exception.ResourceNotFoundException;

/** Catalog endpoints with TMDB mocked out. Database changes are rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TmdbClient tmdb;

    private static TmdbModels.MovieDetails movieDetails(int id) {
        return new TmdbModels.MovieDetails(id, "Test Movie", null, "Overview", "/p.jpg", "/b.jpg", "2024-03-01", 120,
                "Released", "en", 7.8, 500, List.of(), null, null, null);
    }

    @Test
    void browseIsPublicAndTranslatesFilters() throws Exception {
        when(tmdb.discover(eq("movie"), anyMap())).thenReturn(new TmdbModels.Page<>(2, List.of(
                new TmdbModels.MediaItem(1, "A", null, "", null, null, "2024-01-01", null, 8.0, 900, List.of(28), null)),
                10, 200));

        mvc.perform(get("/api/movies").param("genre", "28").param("sort", "TOP_RATED").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.content[0].mediaType").value("MOVIE"))
                .andExpect(jsonPath("$.content[0].year").value(2024));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(tmdb).discover(eq("movie"), params.capture());
        assertThat(params.getValue())
                .containsEntry("sort_by", "vote_average.desc")
                .containsEntry("with_genres", 28)
                .containsEntry("page", 2)
                .containsKey("vote_count.gte");
    }

    @Test
    void invalidSortIsBadRequest() throws Exception {
        mvc.perform(get("/api/movies").param("sort", "nope")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownTitleIs404() throws Exception {
        when(tmdb.movie(anyInt())).thenThrow(new ResourceNotFoundException("Title not found"));

        mvc.perform(get("/api/movies/123456789"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Title not found"));
    }

    @Test
    void missingApiKeyIs503() throws Exception {
        when(tmdb.searchMulti("dune", 1))
                .thenThrow(ExternalServiceException.unavailable("Movie catalog is not configured"));

        mvc.perform(get("/api/search").param("q", "dune")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void blankSearchReturnsEmptyPageWithoutCallingTmdb() throws Exception {
        mvc.perform(get("/api/search").param("q", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        mvc.perform(post("/api/admin/catalog/movies/import/550")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void importedMovieShowsItsOfferInDetails() throws Exception {
        when(tmdb.movie(550)).thenReturn(movieDetails(550));

        // Not imported yet: no offer.
        mvc.perform(get("/api/movies/550"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer.inCatalog").value(false));

        String imported = mvc.perform(post("/api/admin/catalog/movies/import/550"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tmdbId").value(550))
                .andExpect(jsonPath("$.title").value("Test Movie"))
                .andReturn().getResponse().getContentAsString();
        Integer localId = JsonPath.read(imported, "$.id");

        mvc.perform(put("/api/admin/catalog/movies/" + localId + "/offer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"streamable":true,"purchasePrice":9.99,"rentalPrice":3.99,"active":true}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/movies/550"))
                .andExpect(jsonPath("$.offer.inCatalog").value(true))
                .andExpect(jsonPath("$.offer.streamable").value(true))
                .andExpect(jsonPath("$.offer.purchasePrice").value(9.99))
                .andExpect(jsonPath("$.offer.rentalPrice").value(3.99));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsNegativePrice() throws Exception {
        when(tmdb.movie(551)).thenReturn(movieDetails(551));
        String imported = mvc.perform(post("/api/admin/catalog/movies/import/551"))
                .andReturn().getResponse().getContentAsString();
        Integer localId = JsonPath.read(imported, "$.id");

        mvc.perform(put("/api/admin/catalog/movies/" + localId + "/offer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streamable\":false,\"purchasePrice\":-1,\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.purchasePrice").exists());
    }
}
