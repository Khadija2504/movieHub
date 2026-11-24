package com.moviehub.streaming;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.moviehub.catalog.MovieRepository;
import com.moviehub.catalog.VideoSourceRepository;
import com.moviehub.catalog.admin.AdminCatalogDtos.MovieOfferRequest;
import com.moviehub.catalog.admin.AdminCatalogDtos.VideoSourceDto;
import com.moviehub.catalog.admin.CatalogAdminService;

import lombok.RequiredArgsConstructor;

/**
 * Development helper, enabled with DEMO_DATA=true: imports a few films that may be streamed for free and attaches
 * their videos. Blender open movies (CC BY, credited in the player) and public-domain classics hosted by the
 * Internet Archive; Big Buck Bunny uses Mux's public multi-quality HLS test stream. A movie that already has
 * videos is left alone, so changes made by an admin are kept.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@RequiredArgsConstructor
public class DemoStreamingSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoStreamingSeeder.class);
    private static final String ARCHIVE = "https://archive.org/download/";
    private static final String BLENDER = "Blender Foundation, CC BY 3.0";
    private static final String PUBLIC_DOMAIN = "Public domain, via the Internet Archive (archive.org)";

    record DemoFilm(int tmdbId, List<VideoSourceDto> videos, String credit, String buy, String rent,
            boolean inSubscription) {
    }

    static final List<DemoFilm> FILMS = List.of(
            new DemoFilm(10378, List.of(new VideoSourceDto("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", null)),
                    "Big Buck Bunny © " + BLENDER + " - peach.blender.org", "1.99", "0.99", true),
            new DemoFilm(45745, List.of(
                    new VideoSourceDto(ARCHIVE + "Sintel/sintel-2048-stereo.mp4", 872),
                    new VideoSourceDto(ARCHIVE + "Sintel/sintel-2048-stereo_512kb.mp4", 240)),
                    "Sintel © " + BLENDER + " - durian.blender.org", "1.99", "0.99", true),
            new DemoFilm(133701, List.of(
                    new VideoSourceDto(ARCHIVE + "Tears-of-Steel/tears_of_steel_1080p.mp4", 1080),
                    new VideoSourceDto(ARCHIVE + "Tears-of-Steel/tears_of_steel_720p.mp4", 720)),
                    "Tears of Steel © " + BLENDER + " - mango.blender.org", "1.99", "0.99", true),
            classic(3085, "his_girl_friday", "his_girl_friday", true),
            classic(16093, "CarnivalofSouls", "CarnivalOfSouls", true),
            classic(20367, "Detour", "Detour", true),
            classic(961, "The_General_Buster_Keaton", "The_General", true),
            // Buy or rent only, to show that path.
            classic(15856, "house_on_haunted_hill_ipod", "house_on_haunted_hill", false));

    private final CatalogAdminService catalogAdmin;
    private final MovieRepository movieRepository;
    private final VideoSourceRepository videoSourceRepository;

    private static DemoFilm classic(int tmdbId, String item, String file, boolean inSubscription) {
        return new DemoFilm(tmdbId, List.of(
                new VideoSourceDto(ARCHIVE + item + "/" + file + ".mp4", 480),
                new VideoSourceDto(ARCHIVE + item + "/" + file + "_512kb.mp4", 240)),
                PUBLIC_DOMAIN, "2.99", "0.99", inSubscription);
    }

    @Override
    public void run(ApplicationArguments args) {
        int added = 0;
        for (DemoFilm film : FILMS) {
            try {
                var existing = movieRepository.findByTmdbId(film.tmdbId());
                if (existing.isPresent() && videoSourceRepository.existsByMovieId(existing.get().getId())) {
                    continue;
                }
                var movie = catalogAdmin.importMovie(film.tmdbId());
                catalogAdmin.updateMovieOffer(movie.id(), new MovieOfferRequest(true, new BigDecimal(film.buy()),
                        new BigDecimal(film.rent()), film.inSubscription(), film.videos(), film.credit(), true));
                added++;
            } catch (RuntimeException e) {
                log.warn("Demo data: could not set up streaming for TMDB movie {}: {}", film.tmdbId(), e.getMessage());
            }
        }
        if (added > 0) {
            log.info("Demo data: {} streamable film(s) added", added);
        }
    }
}
