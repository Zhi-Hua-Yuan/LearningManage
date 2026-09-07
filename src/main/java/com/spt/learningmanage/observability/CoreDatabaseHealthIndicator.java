package com.spt.learningmanage.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicInteger;

@Component("coreDatabase")
public class CoreDatabaseHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger readiness = new AtomicInteger();

    public CoreDatabaseHealthIndicator(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("learning.core.readiness", readiness, AtomicInteger::get)
                .description("Last CoreDatabase readiness probe result: 1=UP, 0=DOWN")
                .register(meterRegistry);
    }

    @Override
    public Health health() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            boolean up = value != null && value == 1;
            readiness.set(up ? 1 : 0);
            return up ? Health.up().build() : Health.down().build();
        } catch (RuntimeException exception) {
            readiness.set(0);
            return Health.down().build();
        }
    }
}
