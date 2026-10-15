package com.example.integrationprocess.api;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        String databaseVersion = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        return Map.of(
                "status", "UP",
                "application", "integration-process-bootstrap",
                "database", "MariaDB",
                "databaseVersion", databaseVersion
        );
    }
}
