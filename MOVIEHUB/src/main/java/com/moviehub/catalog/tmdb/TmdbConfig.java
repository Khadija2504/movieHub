package com.moviehub.catalog.tmdb;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.moviehub.config.AppProperties;

@Configuration
public class TmdbConfig {

    /** RestClient preconfigured for TMDB: base URL, timeouts and (for v4 tokens) the Bearer header. */
    @Bean
    RestClient tmdbRestClient(RestClient.Builder builder, AppProperties props) {
        AppProperties.Tmdb tmdb = props.tmdb();

        HttpClient.Builder http = HttpClient.newBuilder().connectTimeout(tmdb.connectTimeout());
        if (tmdb.proxy() != null && !tmdb.proxy().isBlank()) {
            http.proxy(ProxySelector.of(parseProxy(tmdb.proxy())));
        }
        HttpClient httpClient = http.build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(tmdb.readTimeout());

        RestClient.Builder b = builder.clone()
                .baseUrl(tmdb.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (TmdbClient.isBearerToken(tmdb.apiKey())) {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tmdb.apiKey());
        }
        return b.build();
    }

    /** Accepts "http://host:port" or "host:port". */
    static InetSocketAddress parseProxy(String value) {
        String v = value.strip();
        URI uri = URI.create(v.contains("://") ? v : "http://" + v);
        if (uri.getHost() == null || uri.getPort() < 0) {
            throw new IllegalStateException("app.tmdb.proxy must look like http://host:port, got: " + value);
        }
        return InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
    }
}
