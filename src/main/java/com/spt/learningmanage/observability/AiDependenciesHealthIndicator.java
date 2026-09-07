package com.spt.learningmanage.observability;

import com.spt.learningmanage.model.vo.ops.DependencyStatusVO;
import com.spt.learningmanage.service.AiDependencyHealthService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("aiDependencies")
public class AiDependenciesHealthIndicator implements HealthIndicator {
    private final AiDependencyHealthService service;

    public AiDependenciesHealthIndicator(AiDependencyHealthService service) {
        this.service = service;
    }

    @Override
    public Health health() {
        Map<String, DependencyStatusVO> snapshot = service.snapshot();
        boolean down = snapshot.values().stream().anyMatch(value -> "DOWN".equals(value.status()));
        boolean degraded = snapshot.values().stream().anyMatch(value -> "DEGRADED".equals(value.status()));
        Status status = down ? new Status("DOWN") : degraded ? new Status("DEGRADED") : Status.UP;
        Health.Builder builder = Health.status(status);
        snapshot.forEach((name, value) -> builder.withDetail(name, value.status()));
        return builder.build();
    }
}
