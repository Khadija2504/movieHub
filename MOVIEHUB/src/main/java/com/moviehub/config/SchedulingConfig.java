package com.moviehub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs (e.g. releasing unpaid seat holds). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
