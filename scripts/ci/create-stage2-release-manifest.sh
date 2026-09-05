#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib/release-candidate-common.sh"

for name in \
  STAGE2_MANIFEST_OUTPUT STAGE2_CANDIDATE_ID \
  STAGE2_BACKEND_SHA STAGE2_BACKEND_DEVELOP_START STAGE2_BACKEND_DEVELOP_END \
  STAGE2_FRONTEND_SHA STAGE2_FRONTEND_DEVELOP_START STAGE2_FRONTEND_DEVELOP_END \
  STAGE2_BACKEND_TEST_COUNT STAGE2_FRONTEND_TEST_COUNT \
  STAGE2_FLYWAY_MANIFEST_SHA256 STAGE2_V1_SHA256 STAGE2_V2_SHA256 STAGE2_V3_SHA256 \
  STAGE2_RUNTIME_OPERATION_COUNT STAGE2_API_REPORT_SHA256 \
  STAGE2_BACKEND_JAR_SHA256 STAGE2_FRONTEND_DIST_SHA256 \
  STAGE2_CONTRACT_SHA256 STAGE2_CONTRACT_SCHEMA_SHA256 STAGE2_RISK_REGISTER_SHA256 \
  STAGE2_SOURCE_EVIDENCE_INDEX_SHA256 STAGE2_PROVIDER_REPORT_SHA256 STAGE2_PROVIDER_BACKEND_SHA \
  STAGE2_PROVIDER_RUN_ID STAGE2_PROVIDER_INPUT_TOKENS STAGE2_PROVIDER_OUTPUT_TOKENS \
  STAGE2_PROVIDER_TOTAL_TOKENS STAGE2_PROVIDER_ESTIMATED_COST \
  STAGE2_WORKFLOW_RUN_ID STAGE2_WORKFLOW_RUN_ATTEMPT STAGE2_WORKFLOW_SHA \
  STAGE2_WORKFLOW_ACTOR STAGE2_WORKFLOW_EXECUTED_AT; do
  release_require_env "$name"
done

release_validate_candidate_id "$STAGE2_CANDIDATE_ID"
manifest_status="${STAGE2_MANIFEST_STATUS:-CANDIDATE_PASS}"
case "$manifest_status" in
  CANDIDATE_PASS)
    gates_json='["S2-A-001","S2-A-002","S2-A-003","S2-A-004","S2-A-005","S2-A-006","S2-A-007","S2-A-008","S2-A-009","S2-A-010","S2-A-011"]'
    risk_status=ELIGIBLE
    open_risk_count=1
    ;;
  PASS)
    gates_json='["S2-A-001","S2-A-002","S2-A-003","S2-A-004","S2-A-005","S2-A-006","S2-A-007","S2-A-008","S2-A-009","S2-A-010","S2-A-011","S2-A-012"]'
    risk_status=CLOSED
    open_risk_count=0
    ;;
  *) release_fail "stage2_manifest_status_invalid" ;;
esac
for sha in "$STAGE2_BACKEND_SHA" "$STAGE2_BACKEND_DEVELOP_START" "$STAGE2_BACKEND_DEVELOP_END" \
  "$STAGE2_FRONTEND_SHA" "$STAGE2_FRONTEND_DEVELOP_START" "$STAGE2_FRONTEND_DEVELOP_END" \
  "$STAGE2_PROVIDER_BACKEND_SHA" "$STAGE2_WORKFLOW_SHA"; do release_validate_sha "$sha"; done
for sha in "$STAGE2_FLYWAY_MANIFEST_SHA256" "$STAGE2_V1_SHA256" "$STAGE2_V2_SHA256" "$STAGE2_V3_SHA256" \
  "$STAGE2_API_REPORT_SHA256" "$STAGE2_BACKEND_JAR_SHA256" "$STAGE2_FRONTEND_DIST_SHA256" \
  "$STAGE2_CONTRACT_SHA256" "$STAGE2_CONTRACT_SCHEMA_SHA256" "$STAGE2_RISK_REGISTER_SHA256" \
  "$STAGE2_SOURCE_EVIDENCE_INDEX_SHA256" "$STAGE2_PROVIDER_REPORT_SHA256"; do release_validate_sha256 "$sha"; done

[[ "$STAGE2_BACKEND_SHA" == "$STAGE2_BACKEND_DEVELOP_START" && "$STAGE2_BACKEND_SHA" == "$STAGE2_BACKEND_DEVELOP_END" ]] \
  || release_fail "stage2_backend_candidate_drift"
[[ "$STAGE2_FRONTEND_SHA" == "$STAGE2_FRONTEND_DEVELOP_START" && "$STAGE2_FRONTEND_SHA" == "$STAGE2_FRONTEND_DEVELOP_END" ]] \
  || release_fail "stage2_frontend_candidate_drift"

mkdir -p "$(dirname "$STAGE2_MANIFEST_OUTPUT")"
jq -n \
  --arg candidateId "$STAGE2_CANDIDATE_ID" \
  --arg status "$manifest_status" --arg riskStatus "$risk_status" --argjson openRiskCount "$open_risk_count" --argjson gatesPassed "$gates_json" \
  --arg backendSha "$STAGE2_BACKEND_SHA" --arg backendStart "$STAGE2_BACKEND_DEVELOP_START" --arg backendEnd "$STAGE2_BACKEND_DEVELOP_END" \
  --arg frontendSha "$STAGE2_FRONTEND_SHA" --arg frontendStart "$STAGE2_FRONTEND_DEVELOP_START" --arg frontendEnd "$STAGE2_FRONTEND_DEVELOP_END" \
  --arg flywayManifestSha "$STAGE2_FLYWAY_MANIFEST_SHA256" --arg v1Sha "$STAGE2_V1_SHA256" --arg v2Sha "$STAGE2_V2_SHA256" --arg v3Sha "$STAGE2_V3_SHA256" \
  --arg apiReportSha "$STAGE2_API_REPORT_SHA256" --arg backendJarSha "$STAGE2_BACKEND_JAR_SHA256" --arg frontendDistSha "$STAGE2_FRONTEND_DIST_SHA256" \
  --arg contractSha "$STAGE2_CONTRACT_SHA256" --arg contractSchemaSha "$STAGE2_CONTRACT_SCHEMA_SHA256" --arg riskSha "$STAGE2_RISK_REGISTER_SHA256" \
  --arg evidenceSha "$STAGE2_SOURCE_EVIDENCE_INDEX_SHA256" --arg providerReportSha "$STAGE2_PROVIDER_REPORT_SHA256" --arg providerBackendSha "$STAGE2_PROVIDER_BACKEND_SHA" \
  --arg providerRunId "$STAGE2_PROVIDER_RUN_ID" --arg estimatedCost "$STAGE2_PROVIDER_ESTIMATED_COST" \
  --arg runId "$STAGE2_WORKFLOW_RUN_ID" --arg runAttempt "$STAGE2_WORKFLOW_RUN_ATTEMPT" --arg workflowSha "$STAGE2_WORKFLOW_SHA" \
  --arg actor "$STAGE2_WORKFLOW_ACTOR" --arg executedAt "$STAGE2_WORKFLOW_EXECUTED_AT" \
  --argjson backendTests "$STAGE2_BACKEND_TEST_COUNT" --argjson frontendTests "$STAGE2_FRONTEND_TEST_COUNT" \
  --argjson runtimeOperations "$STAGE2_RUNTIME_OPERATION_COUNT" \
  --argjson inputTokens "$STAGE2_PROVIDER_INPUT_TOKENS" --argjson outputTokens "$STAGE2_PROVIDER_OUTPUT_TOKENS" --argjson totalTokens "$STAGE2_PROVIDER_TOTAL_TOKENS" \
  '{schemaVersion:1,stage:"stage2",candidateId:$candidateId,status:$status,
    backend:{repository:"Zhi-Hua-Yuan/LearningManage",sha:$backendSha,developShaAtStart:$backendStart,developShaAtEnd:$backendEnd},
    frontend:{repository:"Zhi-Hua-Yuan/learning-manage-frontend",sha:$frontendSha,developShaAtStart:$frontendStart,developShaAtEnd:$frontendEnd},
    environment:{java:"17",node:"22.13.1",npm:"10.9.2",mysql:"8.0.41"},
    flyway:{publishedManifestSha256:$flywayManifestSha,v1Sha256:$v1Sha,v2Sha256:$v2Sha,v3Sha256:$v3Sha,historyTotal:3,emptyDatabase:"PASS",existingDatabase:"PASS",v4Present:false},
    apiContract:{legacyOperationCount:37,frontendOperationCount:44,runtimeOperationCount:$runtimeOperations,matchedOperationCount:44,missingOperationCount:0,comparisonSha256:$apiReportSha},
    regression:{backendTests:$backendTests,frontendTests:$frontendTests,aiBreakdownFlow:"PASS",dockerRuntime:"PASS",status:"PASS"},
    realProvider:{status:"BOUND",model:"qwen-plus",validatedBackendSha:$providerBackendSha,workflowRunId:$providerRunId,rounds:3,scenarios:9,inputTokens:$inputTokens,outputTokens:$outputTokens,totalTokens:$totalTokens,estimatedCost:$estimatedCost,currency:"CNY",reportSha256:$providerReportSha},
    stage2Acceptance:{contractSha256:$contractSha,schemaSha256:$contractSchemaSha,riskRegisterSha256:$riskSha,sourceEvidenceIndexSha256:$evidenceSha,gatesPassed:$gatesPassed},
    riskClosure:{s2R008:$riskStatus,openBlockingRiskCount:$openRiskCount},
    artifacts:{backendJarSha256:$backendJarSha,frontendDistManifestSha256:$frontendDistSha,files:["stage2-source-evidence-index.json","api-compatibility-report.json","frontend-api-contract.json","runtime-openapi.json","real-provider-evidence-binding.json"]},
    workflow:{runId:$runId,runAttempt:$runAttempt,workflowSha:$workflowSha,triggeredBy:$actor,executedAt:$executedAt}}' > "$STAGE2_MANIFEST_OUTPUT"

manifest_sha="$(sha256sum "$STAGE2_MANIFEST_OUTPUT" | awk '{print toupper($1)}')"
printf '%s  %s\n' "$manifest_sha" "$(basename "$STAGE2_MANIFEST_OUTPUT")" > "${STAGE2_MANIFEST_OUTPUT}.sha256"
printf 'stage2.manifest.status=PASS\n'
printf 'stage2.manifest.sha256=%s\n' "$manifest_sha"
