package com.moviehub.health;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record HealthResponse(String status, String database, Instant timestamp) {
    }

    @GetMapping
    @Operation(summary = "Check that the API and the database are reachable")
    public ResponseEntity<HealthResponse> health() {
        boolean dbUp;
        try {
            dbUp = Integer.valueOf(1).equals(jdbcTemplate.queryForObject("SELECT 1", Integer.class));
        } catch (Exception e) {
            dbUp = false;
        }
        HealthResponse body = new HealthResponse(dbUp ? "UP" : "DEGRADED", dbUp ? "UP" : "DOWN", Instant.now());
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
