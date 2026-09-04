#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=scripts/ci/lib/release-candidate-common.sh
source "${script_dir}/lib/release-candidate-common.sh"

for name in \
    RELEASE_CANDIDATE_ID RELEASE_REASON RELEASE_MANIFEST_OUTPUT \
    BACKEND_SHA FRONTEND_SHA BACKEND_DEVELOP_START FRONTEND_DEVELOP_START \
    BACKEND_DEVELOP_END FRONTEND_DEVELOP_END V1_SHA256 \
    BACKEND_RULESET_SHA256 FRONTEND_RULESET_SHA256 \
    BACKEND_TEST_COUNT BACKEND_JAR_SHA256 BACKEND_ARTIFACT_SCAN_SHA256 FRONTEND_DIST_MANIFEST_SHA256 FRONTEND_ARTIFACT_SCAN_SHA256 \
    FRONTEND_CONTRACT_SHA256 RUNTIME_DOCUMENT_SHA256 COMPARISON_REPORT_SHA256 \
    FRONTEND_OPERATION_COUNT RUNTIME_OPERATION_COUNT MATCHED_OPERATION_COUNT MISSING_OPERATION_COUNT \
    RUNTIME_OPENAPI_VERSION FULL_STACK_EVIDENCE_SHA256 \
    FULL_STACK_CONFIRMED_PROJECT_COUNT FULL_STACK_CONFIRMED_MILESTONE_COUNT FULL_STACK_CONFIRMED_TASK_COUNT \
    STAGE0_ACCEPTANCE_SHA256 STAGE0_MATRIX_SHA256 STAGE0_RISK_REGISTER_SHA256 STAGE0_SCHEMA_SHA256 \
    WORKFLOW_RUN_ID WORKFLOW_RUN_ATTEMPT WORKFLOW_SHA WORKFLOW_ACTOR WORKFLOW_EXECUTED_AT; do
    release_require_env "$name"
done

release_validate_candidate_id "$RELEASE_CANDIDATE_ID"
release_validate_reason "$RELEASE_REASON"
for value in \
    "$BACKEND_SHA" "$FRONTEND_SHA" "$BACKEND_DEVELOP_START" "$FRONTEND_DEVELOP_START" \
    "$BACKEND_DEVELOP_END" "$FRONTEND_DEVELOP_END" "$WORKFLOW_SHA"; do
    release_validate_sha "$value"
done
for value in \
    "$V1_SHA256" "$BACKEND_RULESET_SHA256" "$FRONTEND_RULESET_SHA256" \
    "$BACKEND_JAR_SHA256" "$BACKEND_ARTIFACT_SCAN_SHA256" "$FRONTEND_DIST_MANIFEST_SHA256" "$FRONTEND_ARTIFACT_SCAN_SHA256" \
    "$FRONTEND_CONTRACT_SHA256" "$RUNTIME_DOCUMENT_SHA256" "$COMPARISON_REPORT_SHA256" \
    "$FULL_STACK_EVIDENCE_SHA256" "$STAGE0_ACCEPTANCE_SHA256" "$STAGE0_MATRIX_SHA256" \
    "$STAGE0_RISK_REGISTER_SHA256" "$STAGE0_SCHEMA_SHA256"; do
    release_validate_sha256 "$value"
done
[[ "$BACKEND_TEST_COUNT" =~ ^[1-9][0-9]*$ ]] || release_fail "invalid_backend_test_count"
for value in \
    "$FRONTEND_OPERATION_COUNT" "$RUNTIME_OPERATION_COUNT" \
    "$MATCHED_OPERATION_COUNT" "$MISSING_OPERATION_COUNT"; do
    [[ "$value" =~ ^[0-9]+$ ]] || release_fail "invalid_api_contract_operation_count"
done
[[ "$FRONTEND_OPERATION_COUNT" -ge 1 ]] || release_fail "invalid_frontend_operation_count"
[[ "$MISSING_OPERATION_COUNT" == "0" ]] || release_fail "api_contract_has_missing_operation"
[[ "$MATCHED_OPERATION_COUNT" == "$FRONTEND_OPERATION_COUNT" ]] \
    || release_fail "api_contract_match_count_mismatch"
[[ "$RUNTIME_OPENAPI_VERSION" =~ ^3\.[0-9]+([.][0-9]+)?$ ]] \
    || release_fail "invalid_runtime_openapi_version"
for value in \
    "$FULL_STACK_CONFIRMED_PROJECT_COUNT" "$FULL_STACK_CONFIRMED_MILESTONE_COUNT" "$FULL_STACK_CONFIRMED_TASK_COUNT"; do
    [[ "$value" =~ ^[0-9]+$ ]] || release_fail "invalid_full_stack_count"
done
[[ "$FULL_STACK_CONFIRMED_PROJECT_COUNT" == "1" ]] || release_fail "full_stack_project_count_mismatch"
[[ "$FULL_STACK_CONFIRMED_MILESTONE_COUNT" -ge 1 ]] || release_fail "full_stack_milestone_count_invalid"
[[ "$FULL_STACK_CONFIRMED_TASK_COUNT" -ge 1 ]] || release_fail "full_stack_task_count_invalid"
[[ "$WORKFLOW_RUN_ID" =~ ^[1-9][0-9]*$ ]] || release_fail "invalid_workflow_run_id"
[[ "$WORKFLOW_RUN_ATTEMPT" =~ ^[1-9][0-9]*$ ]] || release_fail "invalid_workflow_run_attempt"

command -v jq >/dev/null 2>&1 || release_fail "missing_command_jq"

mkdir -p "$(dirname -- "$RELEASE_MANIFEST_OUTPUT")"
jq -n \
    --arg candidateId "$RELEASE_CANDIDATE_ID" \
    --arg reason "$RELEASE_REASON" \
    --arg backendSha "$BACKEND_SHA" \
    --arg frontendSha "$FRONTEND_SHA" \
    --arg backendDevelopStart "$BACKEND_DEVELOP_START" \
    --arg frontendDevelopStart "$FRONTEND_DEVELOP_START" \
    --arg backendDevelopEnd "$BACKEND_DEVELOP_END" \
    --arg frontendDevelopEnd "$FRONTEND_DEVELOP_END" \
    --arg v1Sha256 "$V1_SHA256" \
    --arg backendRulesetSha256 "$BACKEND_RULESET_SHA256" \
    --arg frontendRulesetSha256 "$FRONTEND_RULESET_SHA256" \
    --arg backendJarSha256 "$BACKEND_JAR_SHA256" \
    --arg backendArtifactScanSha256 "$BACKEND_ARTIFACT_SCAN_SHA256" \
    --arg frontendDistManifestSha256 "$FRONTEND_DIST_MANIFEST_SHA256" \
    --arg frontendArtifactScanSha256 "$FRONTEND_ARTIFACT_SCAN_SHA256" \
    --arg frontendContractSha256 "$FRONTEND_CONTRACT_SHA256" \
    --arg runtimeDocumentSha256 "$RUNTIME_DOCUMENT_SHA256" \
    --arg comparisonReportSha256 "$COMPARISON_REPORT_SHA256" \
    --arg runtimeOpenapiVersion "$RUNTIME_OPENAPI_VERSION" \
    --arg fullStackEvidenceSha256 "$FULL_STACK_EVIDENCE_SHA256" \
    --arg stage0AcceptanceSha256 "$STAGE0_ACCEPTANCE_SHA256" \
    --arg stage0MatrixSha256 "$STAGE0_MATRIX_SHA256" \
    --arg stage0RiskRegisterSha256 "$STAGE0_RISK_REGISTER_SHA256" \
    --arg stage0SchemaSha256 "$STAGE0_SCHEMA_SHA256" \
    --arg workflowRunId "$WORKFLOW_RUN_ID" \
    --arg workflowRunAttempt "$WORKFLOW_RUN_ATTEMPT" \
    --arg workflowSha "$WORKFLOW_SHA" \
    --arg workflowActor "$WORKFLOW_ACTOR" \
    --arg workflowExecutedAt "$WORKFLOW_EXECUTED_AT" \
    --argjson backendTestCount "$BACKEND_TEST_COUNT" \
    --argjson frontendOperationCount "$FRONTEND_OPERATION_COUNT" \
    --argjson runtimeOperationCount "$RUNTIME_OPERATION_COUNT" \
    --argjson matchedOperationCount "$MATCHED_OPERATION_COUNT" \
    --argjson missingOperationCount "$MISSING_OPERATION_COUNT" \
    --argjson fullStackConfirmedProjectCount "$FULL_STACK_CONFIRMED_PROJECT_COUNT" \
    --argjson fullStackConfirmedMilestoneCount "$FULL_STACK_CONFIRMED_MILESTONE_COUNT" \
    --argjson fullStackConfirmedTaskCount "$FULL_STACK_CONFIRMED_TASK_COUNT" \
    '{
        schemaVersion: 4,
        candidateId: $candidateId,
        reason: $reason,
        status: "PASS",
        backend: {
            repository: "Zhi-Hua-Yuan/LearningManage",
            sha: $backendSha,
            developShaAtStart: $backendDevelopStart,
            developShaAtEnd: $backendDevelopEnd,
            testCount: $backendTestCount,
            jarSha256: $backendJarSha256,
            rulesetContractSha256: $backendRulesetSha256
        },
        frontend: {
            repository: "Zhi-Hua-Yuan/learning-manage-frontend",
            sha: $frontendSha,
            developShaAtStart: $frontendDevelopStart,
            developShaAtEnd: $frontendDevelopEnd,
            distManifestSha256: $frontendDistManifestSha256,
            rulesetContractSha256: $frontendRulesetSha256
        },
        artifactScanning: {
            status: "PASS",
            backendReportSha256: $backendArtifactScanSha256,
            frontendReportSha256: $frontendArtifactScanSha256
        },
        interfaceContract: {
            existenceGate: "PASS",
            frontendSchemaVersion: 1,
            frontendBasePath: "/api",
            runtimeOpenapiVersion: $runtimeOpenapiVersion,
            frontendOperationCount: $frontendOperationCount,
            runtimeOperationCount: $runtimeOperationCount,
            matchedOperationCount: $matchedOperationCount,
            missingOperationCount: $missingOperationCount,
            frontendContractSha256: $frontendContractSha256,
            runtimeDocumentSha256: $runtimeDocumentSha256,
            comparisonReportSha256: $comparisonReportSha256
        },
        flyway: {
            publishedV1Sha256: $v1Sha256,
            emptyDatabase: "PASS",
            existingDatabase: "PASS"
        },
        docker: {
            backendRuntime: "PASS",
            applicationFlywayEnabled: false
        },
        fullStackRuntime: {
            gate: "PASS",
            frontendViaNginx: "PASS",
            apiProxy: "PASS",
            aiProvider: "deterministic-ci-stub",
            aiBreakdownFlow: "PASS",
            cancelFlow: "PASS",
            confirmFlow: "PASS",
            idempotentReplay: "PASS",
            confirmedProjectCount: $fullStackConfirmedProjectCount,
            confirmedMilestoneCount: $fullStackConfirmedMilestoneCount,
            confirmedTaskCount: $fullStackConfirmedTaskCount,
            evidenceSha256: $fullStackEvidenceSha256
        },
        stage0Acceptance: {
            bindingStatus: "BOUND",
            contractStatus: "PROVISIONAL",
            acceptanceDocumentSha256: $stage0AcceptanceSha256,
            matrixSha256: $stage0MatrixSha256,
            riskRegisterSha256: $stage0RiskRegisterSha256,
            schemaSha256: $stage0SchemaSha256,
            requiredFailureCount: 0,
            pendingClosingGateCount: 2
        },
        workflow: {
            runId: $workflowRunId,
            runAttempt: $workflowRunAttempt,
            workflowSha: $workflowSha,
            triggeredBy: $workflowActor,
            executedAt: $workflowExecutedAt
        }
    }' > "$RELEASE_MANIFEST_OUTPUT"

manifest_sha256="$(release_file_sha256 "$RELEASE_MANIFEST_OUTPUT")"
printf '%s  %s\n' "$manifest_sha256" "$(basename -- "$RELEASE_MANIFEST_OUTPUT")" \
    > "${RELEASE_MANIFEST_OUTPUT}.sha256"

release_emit_output "manifest_path" "$RELEASE_MANIFEST_OUTPUT"
release_emit_output "manifest_sha256" "$manifest_sha256"
printf 'release.status=PASS\n'
