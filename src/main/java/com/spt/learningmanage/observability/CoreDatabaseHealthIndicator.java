package com.spt.learningmanage.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("coreDatabase")
public class CoreDatabaseHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbcTemplate;

    public CoreDatabaseHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return value != null && value == 1 ? Health.up().build() : Health.down().build();
        } catch (RuntimeException exception) {
            return Health.down().build();
        }
    }
}
