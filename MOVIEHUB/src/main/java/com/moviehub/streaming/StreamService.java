package com.moviehub.streaming;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviehub.catalog.Movie;
import com.moviehub.catalog.MovieRepository;
import com.moviehub.catalog.VideoSource;
import com.moviehub.catalog.VideoSourceRepository;
import com.moviehub.catalog.tmdb.TmdbMapper;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.common.exception.ForbiddenException;
import com.moviehub.common.exception.ResourceNotFoundException;
import com.moviehub.library.LibraryItem;
import com.moviehub.library.LibraryRepository;
import com.moviehub.streaming.AccessService.Access;
import com.moviehub.streaming.AccessService.UserAccess;
import com.moviehub.streaming.StreamTokens.Grant;
import com.moviehub.streaming.StreamingDtos.AccessResponse;
import com.moviehub.streaming.StreamingDtos.LibraryEntry;
import com.moviehub.streaming.StreamingDtos.ProgressResponse;
import com.moviehub.streaming.StreamingDtos.SourceType;
import com.moviehub.streaming.StreamingDtos.StreamSession;
import com.moviehub.streaming.StreamingDtos.StreamSource;
import com.moviehub.streaming.StreamingDtos.WatchedMovie;
import com.moviehub.user.UserRepository;

import lombok.RequiredArgsConstructor;

/** Watching movies: access checks, signed play URLs, watch progress, history and the library. */
@Service
@RequiredArgsConstructor
public class StreamService {

    static final String PLAY_PATH = "/api/stream/play/";
    static final String HLS_FILE = "master.m3u8";
    static final String MP4_FILE = "video.mp4";
    /** Below this, a started movie is not worth "Continue watching". */
    private static final int MIN_PROGRESS_SECONDS = 30;
    private static final int HISTORY_PAGE_SIZE = 20;
    private static final int LIBRARY_PAGE_SIZE = 24;

    private final MovieRepository movieRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final LibraryRepository libraryRepository;
    private final WatchProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final AccessService accessService;
    private final StreamTokens tokens;
    private final VideoFiles videoFiles;
    private final StreamingProperties props;
    private final TmdbMapper tmdbMapper;
    private final Clock clock = Clock.systemUTC();

    public enum LibraryFilter {
        ALL,
        PURCHASED,
        RENTED
    }

    // ---------------------------------------------------------------- access

    @Transactional(readOnly = true)
    public AccessResponse access(long userId, int tmdbId) {
        Optional<Movie> found = movieRepository.findByTmdbId(tmdbId);
        if (found.isEmpty() || !streamable(found.get())) {
            return new AccessResponse(tmdbId, false, false, null, null, null, false, false, false, null, null, 0,
                    null);
        }
        Movie movie = found.get();
        UserAccess user = accessService.forUser(userId);
        Access access = user.check(movie);
        LibraryItem item = user.item(movie);
        Optional<WatchProgress> progress = progressRepository.findByUserIdAndMovieId(userId, movie.getId());
        return new AccessResponse(tmdbId, true, access.granted(), access.via(), access.rentalEndsAt(),
                access.maxQuality(), item != null && item.isOwned(), user.subscribed(),
                movie.isActive() && movie.isInSubscription(), movie.getPurchasePrice(), movie.getRentalPrice(),
                progress.map(WatchProgress::resumeAt).orElse(0), progress.map(StreamService::percent).orElse(null));
    }

    /** Starts watching: checks access and hands out short-lived signed URLs of the movie's videos. */
    @Transactional(readOnly = true)
    public StreamSession session(long userId, int tmdbId) {
        Movie movie = movieRepository.findByTmdbId(tmdbId).filter(this::streamable)
                .orElseThrow(() -> new ResourceNotFoundException("This movie is not available to stream"));
        Access access = accessService.check(userId, movie);
        if (!access.granted()) {
            throw new ForbiddenException("Rent, buy or subscribe to watch this movie");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.urlTtl());
        if (access.rentalEndsAt() != null && access.rentalEndsAt().isBefore(expiresAt)) {
            expiresAt = access.rentalEndsAt();
        }
        int maxQuality = access.maxQuality() == null ? 0 : access.maxQuality();
        List<StreamSource> sources = new ArrayList<>();
        for (VideoSource source : playable(videoSourceRepository.findByMovieId(movie.getId()), maxQuality)) {
            String token = tokens.issue(new Grant(userId, source.getId(), maxQuality, expiresAt));
            boolean hls = source.isHls();
            sources.add(new StreamSource(PLAY_PATH + token + "/" + (hls ? HLS_FILE : MP4_FILE),
                    hls ? SourceType.HLS : SourceType.MP4, hls ? null : source.getQuality()));
        }
        int resumeAt = progressRepository.findByUserIdAndMovieId(userId, movie.getId())
                .map(WatchProgress::resumeAt).orElse(0);
        return new StreamSession(movie.getTmdbId(), movie.getTitle(), tmdbMapper.image(movie.getPosterPath(), "w342"),
                tmdbMapper.image(movie.getBackdropPath(), "w1280"), access.via(), access.rentalEndsAt(),
                access.maxQuality(), sources, expiresAt, resumeAt, movie.getVideoCredit());
    }

    /**
     * The sources a user may play: the HLS stream (filtered later by quality) first, then the MP4s up to the
     * allowed quality (best first). If every MP4 is too big, the smallest one is still offered.
     */
    static List<VideoSource> playable(List<VideoSource> all, int maxQuality) {
        List<VideoSource> result = new ArrayList<>(all.stream().filter(VideoSource::isHls).limit(1).toList());
        List<VideoSource> mp4 = all.stream().filter(s -> !s.isHls() && s.getQuality() != null)
                .sorted(Comparator.comparing(VideoSource::getQuality).reversed()).toList();
        List<VideoSource> allowed = mp4.stream().filter(s -> maxQuality == 0 || s.getQuality() <= maxQuality).toList();
        result.addAll(allowed.isEmpty() && !mp4.isEmpty() ? List.of(mp4.get(mp4.size() - 1)) : allowed);
        return result;
    }

    // ---------------------------------------------------------------- play

    /** What a play URL answers. */
    public sealed interface PlayResult {

        record Redirect(String url) implements PlayResult {
        }

        record Playlist(String content) implements PlayResult {
        }

        record File(Path path, String contentType) implements PlayResult {
        }
    }

    /**
     * Serves a play URL. The signed token is the authorisation (checked here); the access itself was checked
     * when the token was issued.
     *
     * @param path file asked for, relative to the stream ("master.m3u8", "video.mp4", "720p/seg-001.ts", ...)
     */
    @Transactional(readOnly = true)
    public PlayResult play(String token, String path) {
        Grant grant = tokens.verify(token)
                .orElseThrow(() -> new ForbiddenException("Invalid video link"));
        if (!clock.instant().isBefore(grant.expiresAt())) {
            throw new ForbiddenException("This video link has expired. Reload the player.");
        }
        VideoSource source = videoSourceRepository.findById(grant.sourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Video not found"));

        if (!source.isHls()) {
            if (!MP4_FILE.equals(path)) {
                throw new ResourceNotFoundException("Video not found");
            }
            if (!source.isLocal()) {
                return new PlayResult.Redirect(source.getUrl());
            }
            return existingFile(videoFiles.localFile(source));
        }

        if (HLS_FILE.equals(path)) {
            if (source.isLocal()) {
                String master = videoFiles.readLocal(videoFiles.localFile(source));
                return new PlayResult.Playlist(
                        HlsPlaylists.filterMaster(master, grant.maxQuality(), null).playlist());
            }
            String master = videoFiles.readRemote(source.getUrl());
            if (!HlsPlaylists.isMaster(master)) {
                // A single-quality stream: send the player to it directly.
                return new PlayResult.Redirect(source.getUrl());
            }
            return new PlayResult.Playlist(
                    HlsPlaylists.filterMaster(master, grant.maxQuality(), URI.create(source.getUrl())).playlist());
        }

        // Other files of a local HLS stream (media playlists, segments).
        if (!source.isLocal()) {
            throw new ResourceNotFoundException("Video not found");
        }
        Path master = videoFiles.localFile(source);
        List<String> forbidden = HlsPlaylists.filterMaster(videoFiles.readLocal(master), grant.maxQuality(), null)
                .removedUris();
        if (forbidden.contains(path)) {
            throw new ForbiddenException("This quality is not included in your plan");
        }
        Path file = videoFiles.localStreamFile(source, path)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found"));
        return new PlayResult.File(file, VideoFiles.contentType(file));
    }

    private static PlayResult existingFile(Path file) {
        if (!java.nio.file.Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Video file not found");
        }
        return new PlayResult.File(file, "video/mp4");
    }

    // ---------------------------------------------------------------- progress & history

    @Transactional
    public ProgressResponse saveProgress(long userId, int tmdbId, int position, int duration) {
        Movie movie = movieRepository.findByTmdbId(tmdbId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", tmdbId));
        if (!accessService.check(userId, movie).granted()) {
            throw new ForbiddenException("You no longer have access to this movie");
        }
        WatchProgress progress = progressRepository.findByUserIdAndMovieId(userId, movie.getId())
                .orElseGet(() -> new WatchProgress(userRepository.getReferenceById(userId), movie));
        progress.update(position, duration, clock.instant());
        progressRepository.save(progress);
        return new ProgressResponse(progress.getPositionSeconds(), progress.getDurationSeconds(),
                progress.isCompleted(), progress.resumeAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<WatchedMovie> history(long userId, int page) {
        UserAccess access = accessService.forUser(userId);
        var pageable = PageRequest.of(Math.max(page, 1) - 1, HISTORY_PAGE_SIZE);
        return PageResponse.of(progressRepository.findByUserIdOrderByLastWatchedAtDesc(userId, pageable),
                w -> toWatched(w, access));
    }

    /** Started and not finished, still watchable, most recent first. */
    @Transactional(readOnly = true)
    public List<WatchedMovie> continueWatching(long userId, int limit) {
        UserAccess access = accessService.forUser(userId);
        return progressRepository.findInProgress(userId, MIN_PROGRESS_SECONDS, PageRequest.of(0, limit)).stream()
                .map(w -> toWatched(w, access)).filter(WatchedMovie::canWatch).toList();
    }

    @Transactional
    public void removeFromHistory(long userId, int tmdbId) {
        movieRepository.findByTmdbId(tmdbId)
                .flatMap(m -> progressRepository.findByUserIdAndMovieId(userId, m.getId()))
                .ifPresent(progressRepository::delete);
    }

    @Transactional
    public void clearHistory(long userId) {
        progressRepository.deleteAllByUser(userId);
    }

    // ---------------------------------------------------------------- library

    @Transactional(readOnly = true)
    public PageResponse<LibraryEntry> library(long userId, LibraryFilter filter, int page) {
        var pageable = PageRequest.of(Math.max(page, 1) - 1, LIBRARY_PAGE_SIZE);
        var items = switch (filter) {
            case ALL -> libraryRepository.findByUserIdOrderByAcquiredAtDesc(userId, pageable);
            case PURCHASED -> libraryRepository.findByUserIdAndKindOrderByAcquiredAtDesc(userId,
                    LibraryItem.Kind.PURCHASE, pageable);
            case RENTED -> libraryRepository.findByUserIdAndKindOrderByAcquiredAtDesc(userId, LibraryItem.Kind.RENTAL,
                    pageable);
        };
        Map<Long, WatchProgress> progress = progressRepository
                .findByUserIdOrderByLastWatchedAtDesc(userId, PageRequest.of(0, 500)).stream()
                .collect(Collectors.toMap(w -> w.getMovie().getId(), Function.identity()));
        Instant now = clock.instant();
        return PageResponse.of(items, item -> {
            Movie m = item.getMovie();
            WatchProgress w = progress.get(m.getId());
            return new LibraryEntry(m.getTmdbId(), m.getTitle(), tmdbMapper.image(m.getPosterPath(), "w342"),
                    tmdbMapper.image(m.getBackdropPath(), "w780"), year(m), m.getRuntime(), item.getKind(),
                    item.getAcquiredAt(), item.getExpiresAt(), item.grantsAccess(now) && m.isStreamable(),
                    streamable(m) ? m.getRentalPrice() : null, w == null ? null : percent(w));
        });
    }

    @Transactional(readOnly = true)
    public long libraryCount(long userId) {
        return libraryRepository.countAccessible(userId, clock.instant());
    }

    // ---------------------------------------------------------------- helpers

    private boolean streamable(Movie movie) {
        return movie.isStreamable() && videoSourceRepository.existsByMovieId(movie.getId());
    }

    private WatchedMovie toWatched(WatchProgress w, UserAccess access) {
        Movie m = w.getMovie();
        return new WatchedMovie(m.getTmdbId(), m.getTitle(), tmdbMapper.image(m.getPosterPath(), "w342"),
                tmdbMapper.image(m.getBackdropPath(), "w780"), year(m), w.getPositionSeconds(),
                w.getDurationSeconds(), percent(w), w.isCompleted(), w.getLastWatchedAt(),
                access.check(m).granted());
    }

    private static int percent(WatchProgress w) {
        return w.isCompleted() ? 100 : (int) Math.round(100.0 * w.getPositionSeconds() / w.getDurationSeconds());
    }

    private static Integer year(Movie m) {
        return m.getReleaseDate() == null ? null : m.getReleaseDate().getYear();
    }
}
