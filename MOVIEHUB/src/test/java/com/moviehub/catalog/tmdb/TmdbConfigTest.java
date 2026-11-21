package com.moviehub.catalog.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TmdbConfigTest {

    @Test
    void parsesProxyWithOrWithoutScheme() {
        var withScheme = TmdbConfig.parseProxy("http://127.0.0.1:12001");
        assertThat(withScheme.getHostString()).isEqualTo("127.0.0.1");
        assertThat(withScheme.getPort()).isEqualTo(12001);

        var bare = TmdbConfig.parseProxy(" proxy.local:8080 ");
        assertThat(bare.getHostString()).isEqualTo("proxy.local");
        assertThat(bare.getPort()).isEqualTo(8080);
    }

    @Test
    void rejectsProxyWithoutPort() {
        assertThatThrownBy(() -> TmdbConfig.parseProxy("http://127.0.0.1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
