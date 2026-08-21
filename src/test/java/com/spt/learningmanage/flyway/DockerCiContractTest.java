package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerCiContractTest {

    @Test
    void dockerfileUsesTheTestedJarAndNonRootRuntime() throws IOException {
        String dockerfile = read("Dockerfile");

        assertTrue(dockerfile.contains("ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-alpine"));
        assertTrue(dockerfile.contains("FROM ${RUNTIME_IMAGE}"));
        assertTrue(dockerfile.contains("COPY --chown=app:app target/LearningManage-0.0.1-SNAPSHOT.jar /app/app.jar"));
        assertTrue(dockerfile.contains("USER app"));
        assertTrue(dockerfile.contains("ENV SPRING_PROFILES_ACTIVE=prod"));
        assertTrue(dockerfile.contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\"]"));

        assertFalse(dockerfile.contains("target/*.jar"));
        assertFalse(dockerfile.contains("--spring.profiles.active=prod"));
        assertFalse(dockerfile.contains("docker.1panel.live"));
        assertFalse(dockerfile.matches("(?is).*password.*"));
        assertFalse(dockerfile.contains("ADD "));
    }

    @Test
    void ciComposeIsIsolatedAndFlywayOffForApplication() throws IOException {
        String compose = read("deploy/docker-compose.ci.yml");

        assertTrue(compose.contains("services:"));
        assertTrue(compose.contains("mysql:"));
        assertTrue(compose.contains("backend:"));
        assertTrue(compose.contains("127.0.0.1:13306:3306"));
        assertTrue(compose.contains("127.0.0.1:18123:8123"));
        assertTrue(compose.contains("CI_MYSQL_IMAGE"));
        assertTrue(compose.contains("CI_BACKEND_IMAGE"));
        assertTrue(compose.contains("CI_EMPTY_DB_NAME"));
        assertTrue(compose.contains("FLYWAY_ENABLED: \"false\""));
        assertTrue(compose.contains("condition: service_healthy"));
        assertTrue(compose.contains("mysqladmin ping"));

        assertFalse(compose.contains("learning_manage_app"));
        assertFalse(compose.contains("learning_manage_migrator"));
        assertFalse(compose.contains("mysql-data"));
        assertFalse(compose.contains("frontend:"));
        assertFalse(compose.contains("restart:"));
        assertFalse(compose.contains("\"3306:3306\""));
        assertFalse(compose.contains("FLYWAY_ENABLED: true"));
        assertFalse(compose.matches("(?is).*\\bclean\\b|.*\\brepair\\b"));
    }

    private static String read(String relativePath) throws IOException {
        Path path = FlywayTestSupport.projectRoot().resolve(relativePath);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
