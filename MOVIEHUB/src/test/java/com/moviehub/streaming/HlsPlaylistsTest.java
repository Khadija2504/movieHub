package com.moviehub.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class HlsPlaylistsTest {

    private static final String MASTER = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",URI="audio/en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=2149280,RESOLUTION=1280x720,AUDIO="aac"
            url_0/hd.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=246440,RESOLUTION=320x184
            url_2/ld.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=6221600,RESOLUTION=1920x1080
            https://other.example/fhd.m3u8
            """;

    @Test
    void keepsEverythingWithoutALimit() {
        var result = HlsPlaylists.filterMaster(MASTER, 0, null);
        assertThat(result.removedUris()).isEmpty();
        assertThat(result.playlist()).contains("url_0/hd.m3u8", "url_2/ld.m3u8", "fhd.m3u8");
    }

    @Test
    void dropsVariantsAboveThePlanQuality() {
        var result = HlsPlaylists.filterMaster(MASTER, 720, null);
        assertThat(result.removedUris()).containsExactly("https://other.example/fhd.m3u8");
        assertThat(result.playlist()).contains("url_0/hd.m3u8", "url_2/ld.m3u8").doesNotContain("fhd")
                .doesNotContain("RESOLUTION=1920x1080");
    }

    @Test
    void keepsTheSmallestVariantWhenNoneFits() {
        var result = HlsPlaylists.filterMaster(MASTER, 144, null);
        assertThat(result.playlist()).contains("url_2/ld.m3u8").doesNotContain("hd.m3u8");
    }

    @Test
    void makesExternalUrisAbsolute() {
        var result = HlsPlaylists.filterMaster(MASTER, 0, URI.create("https://cdn.example/movie/master.m3u8"));
        assertThat(result.playlist())
                .contains("https://cdn.example/movie/url_0/hd.m3u8")
                .contains("URI=\"https://cdn.example/movie/audio/en.m3u8\"")
                .contains("https://other.example/fhd.m3u8");
        assertThat(HlsPlaylists.isMaster(MASTER)).isTrue();
        assertThat(HlsPlaylists.isMaster("#EXTM3U\n#EXTINF:4,\nseg.ts")).isFalse();
    }
}
