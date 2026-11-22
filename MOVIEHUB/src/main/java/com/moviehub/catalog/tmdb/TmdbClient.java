package com.moviehub.catalog.tmdb;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import com.moviehub.catalog.tmdb.TmdbModels.GenreList;
import com.moviehub.catalog.tmdb.TmdbModels.MediaItem;
import com.moviehub.catalog.tmdb.TmdbModels.MovieDetails;
import com.moviehub.catalog.tmdb.TmdbModels.Page;
import com.moviehub.catalog.tmdb.TmdbModels.Season;
import com.moviehub.catalog.tmdb.TmdbModels.SeriesDetails;
import com.moviehub.common.exception.ExternalServiceException;
import com.moviehub.common.exception.ResourceNotFoundException;
import com.moviehub.config.AppProperties;

/**
 * Thin, cached HTTP client for the TMDB v3 API. Every public method is cached (cache "tmdb", 1h),
 * keyed by method name + arguments (see CacheConfig).
 */
@Component
@CacheConfig(cacheNames = "tmdb", keyGenerator = "tmdbKeyGenerator")
public class TmdbClient {

    private static final ParameterizedTypeReference<Page<MediaItem>> MEDIA_PAGE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final AppProperties.Tmdb config;
    private final boolean useBearerToken;

    /** @param tmdbRestClient built by {@link TmdbConfig} (base URL, timeouts, Bearer header for v4 tokens) */
    public TmdbClient(RestClient tmdbRestClient, AppProperties props) {
        this.restClient = tmdbRestClient;
        this.config = props.tmdb();
        this.useBearerToken = isBearerToken(config.apiKey());
    }

    /** v4 "Read Access Tokens" are JWTs (long, dotted); v3 API keys are 32 hex chars sent as ?api_key=. */
    static boolean isBearerToken(String apiKey) {
        return StringUtils.hasText(apiKey) && apiKey.contains(".");
    }

    public boolean isConfigured() {
        return StringUtils.hasText(config.apiKey());
    }

    // ---------------------------------------------------------------- lists

    /** @param type "movie" or "tv"; @param params TMDB discover filters (sort_by, with_genres, ...) */
    @Cacheable
    public Page<MediaItem> discover(String type, Map<String, Object> params) {
        return get("/discover/" + type, params, MEDIA_PAGE);
    }

    /** @param type "all", "movie" or "tv"; @param window "day" or "week" */
    @Cacheable
    public Page<MediaItem> trending(String type, String window, int page) {
        return get("/trending/" + type + "/" + window, Map.of("page", page), MEDIA_PAGE);
    }

    /** Movies currently in theatres (used to seed demo screenings). */
    @Cacheable
    public Page<MediaItem> nowPlaying(int page) {
        return get("/movie/now_playing", Map.of("page", page), MEDIA_PAGE);
    }

    @Cacheable
    public Page<MediaItem> searchMulti(String query, int page) {
        return get("/search/multi", Map.of("query", query, "page", page, "include_adult", false), MEDIA_PAGE);
    }

    /** @param type "movie" or "tv" */
    @Cacheable
    public GenreList genres(String type) {
        return get("/genre/" + type + "/list", Map.of(), ParameterizedTypeReference.forType(GenreList.class));
    }

    // ---------------------------------------------------------------- details

    @Cacheable
    public MovieDetails movie(int tmdbId) {
        return get("/movie/" + tmdbId, Map.of("append_to_response", "credits,videos,recommendations"),
                ParameterizedTypeReference.forType(MovieDetails.class));
    }

    @Cacheable
    public SeriesDetails series(int tmdbId) {
        return get("/tv/" + tmdbId, Map.of("append_to_response", "credits,videos,recommendations"),
                ParameterizedTypeReference.forType(SeriesDetails.class));
    }

    @Cacheable
    public Season season(int seriesTmdbId, int seasonNumber) {
        return get("/tv/" + seriesTmdbId + "/season/" + seasonNumber, Map.of(),
                ParameterizedTypeReference.forType(Season.class));
    }

    // ---------------------------------------------------------------- plumbing

    private <T> T get(String path, Map<String, Object> params, ParameterizedTypeReference<T> type) {
        if (!isConfigured()) {
            throw ExternalServiceException.unavailable("Movie catalog is not configured (TMDB_API_KEY is missing)");
        }
        Map<String, Object> query = new LinkedHashMap<>(params);
        query.putIfAbsent("language", config.language());
        if (!useBearerToken) {
            query.put("api_key", config.apiKey());
        }

        try {
            return restClient.get()
                    .uri(uri -> buildUri(uri, path, query))
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (req, res) -> {
                        throw new ResourceNotFoundException("Title not found");
                    })
                    .onStatus(status -> status.value() == HttpStatus.UNAUTHORIZED.value(), (req, res) -> {
                        throw ExternalServiceException.unavailable("Movie catalog rejected the API key (check TMDB_API_KEY)");
                    })
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw ExternalServiceException.failed("Movie catalog error (TMDB " + res.getStatusCode().value() + ")", null);
                    })
                    .body(type);
        } catch (ResourceAccessException e) {
            throw ExternalServiceException.failed("Movie catalog is unreachable", e);
        }
    }

    private static URI buildUri(UriBuilder uri, String path, Map<String, Object> query) {
        uri.path(path);
        query.forEach((key, value) -> {
            if (value != null) {
                uri.queryParam(key, value);
            }
        });
        return uri.build();
    }
}
