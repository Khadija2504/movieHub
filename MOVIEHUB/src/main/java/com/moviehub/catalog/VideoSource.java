package com.moviehub.catalog;

import com.moviehub.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A video file of a movie: an adaptive HLS stream ({@code quality} null) or an MP4 of a fixed height.
 * {@code url} is an https:// address or {@code local:<path>} under the streaming videos directory.
 */
@Entity
@Table(name = "movie_video_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoSource extends BaseEntity {

    public static final String LOCAL_PREFIX = "local:";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false, updatable = false)
    private Movie movie;

    @Column(nullable = false, length = 500)
    private String url;

    /** Picture height of an MP4 (480, 720, 1080); null for an adaptive HLS stream. */
    private Integer quality;

    public VideoSource(Movie movie, String url, Integer quality) {
        this.movie = movie;
        this.url = url;
        this.quality = quality;
    }

    public boolean isHls() {
        return path().toLowerCase(java.util.Locale.ROOT).endsWith(".m3u8");
    }

    public boolean isLocal() {
        return url.startsWith(LOCAL_PREFIX);
    }

    /** The URL without its query string (to read the file extension). */
    private String path() {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }
}
