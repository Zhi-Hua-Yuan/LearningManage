package com.spt.learningmanage.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreDatabaseHealthIndicatorTest {
    @Test
    void readinessGaugeTracksTheActualDatabaseProbe() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoreDatabaseHealthIndicator indicator = new CoreDatabaseHealthIndicator(jdbc, registry);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        assertEquals(Status.UP, indicator.health().getStatus());
        assertEquals(1D, registry.get("learning.core.readiness").gauge().value());

        when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertEquals(Status.DOWN, indicator.health().getStatus());
        assertEquals(0D, registry.get("learning.core.readiness").gauge().value());
    }
}
