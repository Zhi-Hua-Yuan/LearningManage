package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayCiScriptStaticTest {

    private static final List<String> BASH_SCRIPTS = List.of(
            "scripts/flyway-admin.sh",
            "scripts/ci/lib/ci-common.sh",
            "scripts/ci/assert-ci-database-target.sh",
            "scripts/ci/wait-for-mysql.sh",
            "scripts/ci/provision-ci-databases.sh",
            "scripts/ci/verify-empty-database.sh",
            "scripts/ci/verify-existing-database.sh",
            "scripts/ci/verify-published-migrations.sh",
            "scripts/ci/verify-docker-runtime.sh",
            "scripts/ci/tests/static-guards-test.sh"
    );

    @Test
    void bashScriptsUseTheFrozenSafetyPreambleAndLf() throws IOException {
        for (String relativePath : BASH_SCRIPTS) {
            byte[] content = Files.readAllBytes(FlywayTestSupport.projectRoot().resolve(relativePath));
            String text = new String(content, StandardCharsets.UTF_8);

            assertTrue(text.startsWith("#!/usr/bin/env bash\n"), relativePath);
            assertTrue(text.contains("set -Eeuo pipefail"), relativePath);
            assertFalse(text.contains("\r"), relativePath);
            assertFalse(text.contains("set -x"), relativePath);
        }
    }

    @Test
    void dockerCiContractFilesUseLf() throws IOException {
        for (String relativePath : List.of("Dockerfile", "deploy/docker-compose.ci.yml")) {
            String text = read(relativePath);
            assertFalse(text.contains("\r"), relativePath);
        }
    }

    @Test
    void ciTargetGuardRejectsMainInstanceAndProductionIdentities() throws IOException {
        String common = read("scripts/ci/lib/ci-common.sh");

        assertTrue(common.contains("CI_DB_GATE_AUTHORIZED"));
        assertTrue(common.contains("127.0.0.1"));
        assertTrue(common.contains("database_port_3306_forbidden"));
        assertTrue(common.contains("^learning_manage_ci_(empty|legacy)"));
        assertTrue(common.contains("learning_manage_ci_migrator"));
        assertTrue(common.contains("learning_manage_ci_app"));
        assertTrue(common.contains("expected_database_mismatch"));
    }

    @Test
    void databaseVerificationScriptsApplyTheGuardBeforeMysqlCalls() throws IOException {
        for (String relativePath : List.of(
                "scripts/ci/wait-for-mysql.sh",
                "scripts/ci/provision-ci-databases.sh",
                "scripts/ci/verify-empty-database.sh",
                "scripts/ci/verify-existing-database.sh",
                "scripts/ci/verify-docker-runtime.sh")) {
            String script = read(relativePath);
            int guard = script.indexOf("ci_assert_ci_target");
            int firstMysqlCall = script.indexOf("ci_mysql_");

            assertTrue(guard >= 0, relativePath);
            assertTrue(firstMysqlCall < 0 || guard < firstMysqlCall, relativePath);
            assertFalse(script.matches("(?is).*flyway\\s+(clean|repair).*"), relativePath);
            assertFalse(script.contains("DROP DATABASE"), relativePath);
            assertFalse(script.contains("--password="), relativePath);
        }
    }

    @Test
    void bashAndPowerShellFlywayWrappersExposeTheSameActions() throws IOException {
        String bash = read("scripts/flyway-admin.sh");
        String powershell = read("scripts/flyway-admin.ps1");

        for (String action : List.of("info", "validate", "baseline", "migrate")) {
            assertTrue(bash.contains(action), action);
            assertTrue(powershell.contains("'" + action + "'"), action);
        }
        for (String forbidden : List.of("clean", "repair")) {
            assertFalse(bash.contains(forbidden), forbidden);
            assertFalse(powershell.contains("'" + forbidden + "'"), forbidden);
        }
    }

    @Test
    void staticGuardSelfTestCoversRequiredNegativeCases() throws IOException {
        String selfTest = read("scripts/ci/tests/static-guards-test.sh");

        for (String caseName : List.of(
                "missing_authorization", "main_database", "port_3306", "localhost_host",
                "external_host", "invalid_database_name", "expected_name_mismatch",
                "production_migrator", "flyway_clean", "flyway_repair",
                "published_invalid_base", "dockerfile_contract", "ci_compose_contract")) {
            assertTrue(selfTest.contains(caseName), caseName);
        }
    }

    private static String read(String relativePath) throws IOException {
        Path path = FlywayTestSupport.projectRoot().resolve(relativePath);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
