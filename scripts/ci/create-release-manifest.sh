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
    BACKEND_TEST_COUNT BACKEND_JAR_SHA256 FRONTEND_DIST_MANIFEST_SHA256 \
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
    "$BACKEND_JAR_SHA256" "$FRONTEND_DIST_MANIFEST_SHA256"; do
    release_validate_sha256 "$value"
done
[[ "$BACKEND_TEST_COUNT" =~ ^[1-9][0-9]*$ ]] || release_fail "invalid_backend_test_count"
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
    --arg frontendDistManifestSha256 "$FRONTEND_DIST_MANIFEST_SHA256" \
    --arg workflowRunId "$WORKFLOW_RUN_ID" \
    --arg workflowRunAttempt "$WORKFLOW_RUN_ATTEMPT" \
    --arg workflowSha "$WORKFLOW_SHA" \
    --arg workflowActor "$WORKFLOW_ACTOR" \
    --arg workflowExecutedAt "$WORKFLOW_EXECUTED_AT" \
    --argjson backendTestCount "$BACKEND_TEST_COUNT" \
    '{
        schemaVersion: 1,
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
        flyway: {
            publishedV1Sha256: $v1Sha256,
            emptyDatabase: "PASS",
            existingDatabase: "PASS"
        },
        docker: {
            backendRuntime: "PASS",
            applicationFlywayEnabled: false
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
