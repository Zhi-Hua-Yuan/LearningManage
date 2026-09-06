package com.spt.learningmanage.flyway;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayCiScriptStaticTest {

    private static final List<String> BASH_SCRIPTS = List.of(
            "scripts/flyway-admin.sh",
            "scripts/ci/lib/ci-common.sh",
            "scripts/ci/lib/release-candidate-common.sh",
            "scripts/ci/assert-ci-database-target.sh",
            "scripts/ci/wait-for-mysql.sh",
            "scripts/ci/provision-ci-databases.sh",
            "scripts/ci/verify-empty-database.sh",
            "scripts/ci/verify-existing-database.sh",
            "scripts/ci/verify-v2-negative-preflight.sh",
            "scripts/ci/verify-v2-recovery.sh",
            "scripts/ci/lib/v3-test-common.sh",
            "scripts/ci/verify-v3-negative-preflight.sh",
            "scripts/ci/verify-v3-equivalent-duplicates.sh",
            "scripts/ci/verify-v3-recovery.sh",
            "scripts/ci/verify-published-migrations.sh",
            "scripts/ci/verify-stage2-acceptance.sh",
            "scripts/ci/verify-docker-runtime.sh",
            "scripts/ci/verify-runtime-api-contract.sh",
            "scripts/ci/verify-ai-breakdown-flow.sh",
            "scripts/ci/validate-release-candidate.sh",
            "scripts/ci/create-release-manifest.sh",
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
        for (String relativePath : List.of(
                "Dockerfile",
                "deploy/docker-compose.ci.yml",
                "deploy/docker-compose.release-gate.yml")) {
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
                "scripts/ci/verify-v2-negative-preflight.sh",
                "scripts/ci/verify-v2-recovery.sh",
                "scripts/ci/verify-v3-negative-preflight.sh",
                "scripts/ci/verify-v3-equivalent-duplicates.sh",
                "scripts/ci/verify-v3-recovery.sh",
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
                "published_invalid_base", "dockerfile_contract", "ci_compose_contract",
                "release_short_sha", "release_branch_name", "release_path_traversal",
                "release_candidate_id_too_long", "release_multiline_reason")) {
            assertTrue(selfTest.contains(caseName), caseName);
        }

        Map<?, ?> workflow = new Yaml().load(read(".github/workflows/release-gate.yml"));
        assertTrue(workflow.containsKey("jobs"));
    }

    @Test
    void crossRepositoryGateVerifiesAndPackagesFrontendApiContract() throws IOException {
        String workflow = read(".github/workflows/release-gate.yml");

        assertTrue(workflow.contains("npm run contract:test && npm run contract:verify"));
        assertTrue(workflow.contains("npm run contract:export"));
        assertTrue(workflow.contains("contracts/frontend-api-contract.schema.json"));
        assertTrue(workflow.contains("frontend-api-contract.sha256"));
        assertTrue(workflow.contains("frontend_contract_sha256"));
        assertTrue(workflow.contains("frontend_operation_count"));
        assertTrue(workflow.contains("frontend_contract_schema_version"));
    }

    @Test
    void runtimeApiContractGateExportsAndComparesFrozenInputs() throws IOException {
        String workflow = read(".github/workflows/release-gate.yml");
        String script = read("scripts/ci/verify-runtime-api-contract.sh");
        String manifestScript = read("scripts/ci/create-release-manifest.sh");
        String manifestSchema = read("docs/stage0/ci/release-candidate-manifest.schema.json");

        assertTrue(workflow.contains("verify-runtime-api-contract.sh"));
        assertTrue(workflow.contains("CI_RUNTIME_OPENAPI_URL"));
        assertTrue(workflow.contains("release-api-contract-"));
        assertTrue(workflow.contains("schemaVersion == 4"));
        assertTrue(workflow.contains("matched_operation_count"));
        assertTrue(script.contains("/api/v3/api-docs") || workflow.contains("/api/v3/api-docs"));
        assertTrue(script.contains("frontend_operation_missing_from_runtime_openapi"));
        assertTrue(script.contains("runtime-openapi.json"));
        assertTrue(manifestScript.contains("interfaceContract"));
        assertTrue(manifestScript.contains("schemaVersion: 4"));
        assertTrue(manifestSchema.contains("\"interfaceContract\""));
        assertTrue(manifestSchema.contains("\"fullStackRuntime\""));
        assertTrue(manifestSchema.contains("\"const\": 4"));
    }

    @Test
    void fullStackGateUsesNginxAndDeterministicAiStubWithoutCredentials() throws IOException {
        String workflow = read(".github/workflows/release-gate.yml");
        String compose = read("deploy/docker-compose.release-gate.yml");
        String flow = read("scripts/ci/verify-ai-breakdown-flow.sh");
        String stub = read("scripts/ci/stubs/ai-chat-completions-stub.py");

        assertTrue(workflow.contains("verify-ai-breakdown-flow.sh"));
        assertTrue(workflow.contains("CI_RUNTIME_OPENAPI_URL: http://127.0.0.1:18080/api/v3/api-docs"));
        assertTrue(workflow.contains("CI_NGINX_IMAGE: nginx:1.27-alpine@sha256:"));
        assertTrue(workflow.contains("CI_AI_STUB_IMAGE: python:3.12.8-alpine@sha256:"));
        assertTrue(compose.contains("127.0.0.1:18080:80"));
        assertTrue(compose.contains("AI_BASE_URL: http://ai-stub:8080/compatible-mode/v1"));
        assertTrue(compose.contains("FLYWAY_ENABLED: \"false\""));
        assertTrue(compose.contains("internal: true"));
        assertTrue(compose.contains("ci-host-access"));
        assertTrue(compose.contains("ci-edge-access"));
        assertTrue(flow.contains("/api/ai/breakdown/preview"));
        assertTrue(flow.contains("idempotentReplay"));
        assertTrue(flow.contains("full-stack-ai-flow-evidence.json"));
        assertTrue(stub.contains("/compatible-mode/v1/chat/completions"));
        assertFalse(stub.toLowerCase().contains("dashscope"));
        assertFalse(stub.toLowerCase().contains("authorization"));
    }

    @Test
    void earlierGatesRemainWiredAfterV5BecomesCurrentHead() throws IOException {
        String backendWorkflow = read(".github/workflows/backend-ci.yml");
        String releaseWorkflow = read(".github/workflows/release-gate.yml");
        String provision = read("scripts/ci/provision-ci-databases.sh");
        String stage2Acceptance = read("scripts/ci/verify-stage2-acceptance.sh");

        for (String workflow : List.of(backendWorkflow, releaseWorkflow)) {
            assertTrue(workflow.contains("verify-v2-negative-preflight.sh"));
            assertTrue(workflow.contains("verify-v2-recovery.sh"));
            assertTrue(workflow.contains("verify-v3-negative-preflight.sh"));
            assertTrue(workflow.contains("verify-v3-equivalent-duplicates.sh"));
            assertTrue(workflow.contains("verify-v3-recovery.sh"));
            assertTrue(workflow.contains("verify-stage2-acceptance.sh"));
            assertTrue(workflow.contains("verify-ai-invocation-boundary.sh"));
            assertTrue(workflow.contains("verify-stage2-wp2-protocol-stub.sh"));
            assertTrue(workflow.contains("CI_EXPECTED_HISTORY_TOTAL: '4'"));
            assertTrue(workflow.contains("CI_EXPECTED_TEST_COUNT: '797'"));
        }
        assertTrue(provision.contains("CREATE TEMPORARY TABLES"));
        assertTrue(stage2Acceptance.contains("stage2_wp2_pass_without_full_ci"));
        assertTrue(stage2Acceptance.contains("stage2_wp4_pass_without_candidate_ci"));
        assertTrue(stage2Acceptance.contains("stage2_wp6_pass_without_candidate_ci"));
    }

    @Test
    void v2RecoveryGateBacksUpBeforeMigrationAndRestoresLegacyShape() throws IOException {
        String script = read("scripts/ci/verify-v2-recovery.sh");

        int backup = script.indexOf("mysqldump");
        int migrate = script.indexOf("migrate \"$source_database\"");
        int restore = script.indexOf("--database=\"$restore_database\" <\"$backup_file\"");

        assertTrue(backup >= 0);
        assertTrue(migrate > backup);
        assertTrue(restore > migrate);
        assertTrue(script.contains("ci_assert_ci_target"));
        assertTrue(script.contains("ci_assert_ci_database_name \"$source_database\""));
        assertTrue(script.contains("ci_assert_ci_database_name \"$restore_database\""));
        assertTrue(script.contains("recovery.verify.success"));
        assertTrue(script.contains("recovery.backup.sha256"));
        assertTrue(script.contains("--skip-add-locks"));
        assertFalse(script.contains("DROP DATABASE"));
    }

    private static String read(String relativePath) throws IOException {
        Path path = FlywayTestSupport.projectRoot().resolve(relativePath);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
