package com.moviehub.config;

import java.util.Arrays;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    /** Key = "methodName[arg1, arg2, ...]" so different methods sharing one cache never collide. */
    @Bean
    KeyGenerator tmdbKeyGenerator() {
        return (target, method, params) -> method.getName() + Arrays.deepToString(params);
    }
}
