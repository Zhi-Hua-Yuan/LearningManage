package com.spt.learningmanage.observability;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Stage7ObservabilityContractTest {
    private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

    @Test
    void managementPlaneIsPrivateAndMinimallyExposed() throws Exception {
        String application = read("src/main/resources/application.yml");
        String compose = read("deploy/docker-compose.yml");
        String stage7Compose = read("deploy/docker-compose.stage7-gate.yml");
        assertTrue(application.contains("address: ${MANAGEMENT_ADDRESS:127.0.0.1}"));
        assertTrue(application.contains("include: health,prometheus"));
        assertTrue(application.contains("order: down,out-of-service,degraded,unknown,up"));
        assertTrue(application.contains("down: 503"));
        assertTrue(application.contains("out-of-service: 503"));
        assertTrue(application.contains("degraded: 200"));
        assertTrue(application.contains("include: readinessState,coreDatabase"));
        assertTrue(application.contains("connection-timeout: ${DB_CONNECTION_TIMEOUT_MS:5000}"));
        assertFalse(application.contains("include: \"*\""));
        assertTrue(compose.contains("MANAGEMENT_ADDRESS: 0.0.0.0"));
        assertTrue(compose.contains("OTEL_EXPORTER_OTLP_ENDPOINT: http://tempo:4318/v1/traces"));
        assertTrue(stage7Compose.contains("AI_EMBEDDING_QUERY_BASE_URL: http://ai-stub:8080/api/v1"));
        assertTrue(stage7Compose.contains("AI_RAG_VECTOR_SCORE_THRESHOLD: '-1.0'"));
    }

    @Test
    void dashboardsAndAlertsCoverEveryStage7Domain() throws Exception {
        Path dashboards = ROOT.resolve("deploy/observability/grafana/dashboards");
        List<Path> files;
        try (var paths = Files.list(dashboards)) {
            files = paths.filter(path -> path.toString().endsWith(".json")).toList();
        }
        assertEquals(6, files.size());
        String alerts = read("deploy/observability/alerts.yml");
        assertTrue(alerts.contains("LearningManageCoreReadinessDown"));
        assertTrue(alerts.contains("LearningManageKnowledgeDeadEvent"));
        assertTrue(alerts.contains("LearningManageCleanupFailure"));
        assertTrue(alerts.contains("LearningManageDailyCostHardLimit"));
        String alertTests = read("deploy/observability/alerts.test.yml");
        assertTrue(alertTests.contains("core readiness alert fires and recovers"));
        assertTrue(alertTests.contains("cost alerts respect configured thresholds"));
    }

    @Test
    void redisAclDisablesAnonymousDefaultUser() throws Exception {
        String entrypoint = read("deploy/redis/redis-entrypoint-stage7.sh");
        assertTrue(entrypoint.contains("user default off"));
        assertTrue(entrypoint.contains("user learning_app on"));
        assertTrue(entrypoint.contains("sha256sum"));
        assertFalse(entrypoint.contains("replace_with"));
    }

    @Test
    void releaseGatePinsEveryRuntimeImageByVersionAndDigest() throws Exception {
        String workflow = read(".github/workflows/stage7-production-ops.yml");
        assertTrue(workflow.contains("redis:7.4-alpine@sha256:"));
        assertTrue(workflow.contains("qdrant/qdrant:v1.18.2@sha256:"));
        assertTrue(workflow.contains("python:3.12.8-alpine@sha256:"));
        assertTrue(workflow.contains("prom/prometheus:v2.54.1@sha256:"));
        assertTrue(workflow.contains("grafana/tempo:2.6.1@sha256:"));
        assertTrue(workflow.contains("grafana/grafana:11.2.2@sha256:"));
        assertTrue(workflow.contains("nginx:1.29.1-alpine@sha256:"));
        assertTrue(workflow.contains("CI_RAG_ENABLED: 'true'"));
        assertTrue(workflow.contains("CI_AGENT_WORKER_ENABLED: 'true'"));
        String runtimeGate = read("scripts/ci/verify-stage7-runtime.sh");
        assertTrue(runtimeGate.contains("verify-stage7-enabled-ai.py"));
    }

    private String read(String path) throws Exception {
        return Files.readString(ROOT.resolve(path), StandardCharsets.UTF_8);
    }
}
