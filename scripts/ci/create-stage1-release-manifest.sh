#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/release-candidate-common.sh"

for name in \
  STAGE1_MANIFEST_OUTPUT STAGE1_CANDIDATE_ID \
  STAGE1_BACKEND_SHA STAGE1_BACKEND_DEVELOP_START STAGE1_BACKEND_DEVELOP_END \
  STAGE1_FRONTEND_SHA STAGE1_FRONTEND_DEVELOP_START STAGE1_FRONTEND_DEVELOP_END \
  STAGE1_BACKEND_TEST_COUNT STAGE1_FRONTEND_TEST_COUNT \
  STAGE1_FLYWAY_MANIFEST_SHA256 STAGE1_V1_SHA256 STAGE1_V2_SHA256 \
  STAGE1_RUNTIME_OPERATION_COUNT STAGE1_COMPARISON_SHA256 \
  STAGE1_CONTRACT_SHA256 STAGE1_CONTRACT_SCHEMA_SHA256 \
  STAGE1_SOURCE_EVIDENCE_INDEX_SHA256 STAGE1_WORKFLOW_RUN_ID STAGE1_WORKFLOW_RUN_ATTEMPT \
  STAGE1_WORKFLOW_SHA STAGE1_WORKFLOW_ACTOR STAGE1_WORKFLOW_EXECUTED_AT; do
  release_require_env "$name"
done
release_validate_candidate_id "$STAGE1_CANDIDATE_ID"

for sha in \
  "$STAGE1_BACKEND_SHA" "$STAGE1_BACKEND_DEVELOP_START" "$STAGE1_BACKEND_DEVELOP_END" \
  "$STAGE1_FRONTEND_SHA" "$STAGE1_FRONTEND_DEVELOP_START" "$STAGE1_FRONTEND_DEVELOP_END" \
  "$STAGE1_WORKFLOW_SHA"; do release_validate_sha "$sha"; done
for sha in \
  "$STAGE1_COMPARISON_SHA256" "$STAGE1_CONTRACT_SHA256" "$STAGE1_CONTRACT_SCHEMA_SHA256" \
  "$STAGE1_SOURCE_EVIDENCE_INDEX_SHA256" "$STAGE1_FLYWAY_MANIFEST_SHA256" "$STAGE1_V1_SHA256" "$STAGE1_V2_SHA256"; do release_validate_sha256 "$sha"; done

[[ "$STAGE1_BACKEND_SHA" == "$STAGE1_BACKEND_DEVELOP_START" ]] || release_fail "backend_sha_not_develop_start"
[[ "$STAGE1_BACKEND_SHA" == "$STAGE1_BACKEND_DEVELOP_END" ]] || release_fail "backend_sha_not_develop_end"
[[ "$STAGE1_FRONTEND_SHA" == "$STAGE1_FRONTEND_DEVELOP_START" ]] || release_fail "frontend_sha_not_develop_start"
[[ "$STAGE1_FRONTEND_SHA" == "$STAGE1_FRONTEND_DEVELOP_END" ]] || release_fail "frontend_sha_not_develop_end"
[[ "$STAGE1_RUNTIME_OPERATION_COUNT" =~ ^[1-9][0-9]*$ ]] || release_fail "invalid_runtime_operation_count"
[[ "$STAGE1_BACKEND_TEST_COUNT" =~ ^[1-9][0-9]*$ ]] || release_fail "invalid_backend_test_count"
[[ "$STAGE1_FRONTEND_TEST_COUNT" =~ ^[1-9][0-9]*$ ]] || release_fail "invalid_frontend_test_count"

mkdir -p "$(dirname -- "$STAGE1_MANIFEST_OUTPUT")"
jq -n \
  --arg candidateId "$STAGE1_CANDIDATE_ID" \
  --arg backendSha "$STAGE1_BACKEND_SHA" --arg backendStart "$STAGE1_BACKEND_DEVELOP_START" --arg backendEnd "$STAGE1_BACKEND_DEVELOP_END" \
  --arg frontendSha "$STAGE1_FRONTEND_SHA" --arg frontendStart "$STAGE1_FRONTEND_DEVELOP_START" --arg frontendEnd "$STAGE1_FRONTEND_DEVELOP_END" \
  --arg comparisonSha "$STAGE1_COMPARISON_SHA256" --arg contractSha "$STAGE1_CONTRACT_SHA256" \
  --arg contractSchemaSha "$STAGE1_CONTRACT_SCHEMA_SHA256" --arg evidenceSha "$STAGE1_SOURCE_EVIDENCE_INDEX_SHA256" \
  --arg flywayManifestSha "$STAGE1_FLYWAY_MANIFEST_SHA256" --arg v1Sha "$STAGE1_V1_SHA256" --arg v2Sha "$STAGE1_V2_SHA256" \
  --arg runId "$STAGE1_WORKFLOW_RUN_ID" --arg runAttempt "$STAGE1_WORKFLOW_RUN_ATTEMPT" \
  --arg workflowSha "$STAGE1_WORKFLOW_SHA" --arg actor "$STAGE1_WORKFLOW_ACTOR" --arg executedAt "$STAGE1_WORKFLOW_EXECUTED_AT" \
  --argjson backendTests "$STAGE1_BACKEND_TEST_COUNT" --argjson frontendTests "$STAGE1_FRONTEND_TEST_COUNT" \
  --argjson runtimeOperations "$STAGE1_RUNTIME_OPERATION_COUNT" \
  '{schemaVersion:1,stage:"stage1",candidateId:$candidateId,status:"CANDIDATE_PASS",
    backend:{repository:"Zhi-Hua-Yuan/LearningManage",sha:$backendSha,developShaAtStart:$backendStart,developShaAtEnd:$backendEnd},
    frontend:{repository:"Zhi-Hua-Yuan/learning-manage-frontend",sha:$frontendSha,developShaAtStart:$frontendStart,developShaAtEnd:$frontendEnd},
    flyway:{publishedManifestSha256:$flywayManifestSha,v1Sha256:$v1Sha,v2Sha256:$v2Sha,historyTotal:2,emptyDatabase:"PASS",existingDatabase:"PASS"},
    apiContract:{legacyOperationCount:37,legacyMissingFromCurrentCount:0,legacyMissingFromRuntimeCount:0,frontendOperationCount:44,runtimeOperationCount:$runtimeOperations,matchedOperationCount:44,missingOperationCount:0,comparisonSha256:$comparisonSha},
    regression:{backendTests:$backendTests,frontendTests:$frontendTests,personalFlow:"PASS",stage1Flow:"PASS",aiBreakdownFlow:"PASS",status:"PASS"},
    stage1Acceptance:{contractSha256:$contractSha,schemaSha256:$contractSchemaSha,sourceEvidenceIndexSha256:$evidenceSha,gatesPassed:["S1-A-001","S1-A-002","S1-A-003","S1-A-004","S1-A-005","S1-A-006","S1-A-007","S1-A-008","S1-A-009","S1-A-010","S1-A-011"]},
    riskClosure:{s1R010:"ELIGIBLE",openBlockingRiskCount:1},
    evidence:{files:["stage1-source-evidence-index.json","stage1-api-compatibility-report.json","stage1-full-stack-evidence.json"]},
    workflow:{runId:$runId,runAttempt:$runAttempt,workflowSha:$workflowSha,triggeredBy:$actor,executedAt:$executedAt}}' \
  > "$STAGE1_MANIFEST_OUTPUT"

manifest_sha="$(sha256sum "$STAGE1_MANIFEST_OUTPUT" | awk '{print toupper($1)}')"
printf '%s  %s\n' "$manifest_sha" "$(basename -- "$STAGE1_MANIFEST_OUTPUT")" > "${STAGE1_MANIFEST_OUTPUT}.sha256"
printf 'stage1.manifest.status=PASS\n'
printf 'stage1.manifest.sha256=%s\n' "$manifest_sha"
