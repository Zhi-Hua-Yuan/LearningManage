#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib/release-candidate-common.sh"
for name in STAGE3_RELEASE_MANIFEST_OUTPUT STAGE3_CANDIDATE_ID STAGE3_BACKEND_SHA STAGE3_FRONTEND_SHA \
  STAGE3_DATASET_MANIFEST STAGE3_PROMPT_MANIFEST STAGE3_CANDIDATE_SUMMARY STAGE3_HUMAN_REVIEW \
  STAGE3_EVIDENCE_INDEX STAGE3_BACKEND_TEST_COUNT STAGE3_FRONTEND_TEST_COUNT \
  STAGE3_WORKFLOW_RUN_ID STAGE3_WORKFLOW_RUN_ATTEMPT STAGE3_WORKFLOW_SHA STAGE3_WORKFLOW_ACTOR STAGE3_WORKFLOW_EXECUTED_AT; do
  release_require_env "$name"
done
release_validate_candidate_id "$STAGE3_CANDIDATE_ID"
for sha in "$STAGE3_BACKEND_SHA" "$STAGE3_FRONTEND_SHA" "$STAGE3_WORKFLOW_SHA"; do release_validate_sha "$sha"; done
for file in "$STAGE3_DATASET_MANIFEST" "$STAGE3_PROMPT_MANIFEST" "$STAGE3_CANDIDATE_SUMMARY" "$STAGE3_HUMAN_REVIEW" "$STAGE3_EVIDENCE_INDEX"; do
  [[ -s "$file" ]] || release_fail "stage3_required_evidence_missing"
done
jq -e '.status == "PASS" and .run.regressionRounds == 3 and .run.holdoutRounds == 3' "$STAGE3_CANDIDATE_SUMMARY" >/dev/null \
  || release_fail "stage3_candidate_summary_not_passed"
jq -e '.status == "PASS" and .agreementRate >= 0.8' "$STAGE3_HUMAN_REVIEW" >/dev/null \
  || release_fail "stage3_human_review_not_passed"

hash_file() { sha256sum "$1" | awk '{print toupper($1)}'; }
dataset_sha="$(hash_file "$STAGE3_DATASET_MANIFEST")"
prompt_sha="$(hash_file "$STAGE3_PROMPT_MANIFEST")"
summary_sha="$(hash_file "$STAGE3_CANDIDATE_SUMMARY")"
review_sha="$(hash_file "$STAGE3_HUMAN_REVIEW")"
evidence_sha="$(hash_file "$STAGE3_EVIDENCE_INDEX")"
dataset_content_sha="$(jq -r '.combinedSha256' "$STAGE3_DATASET_MANIFEST")"
prompt_count="$(jq '.prompts | length' "$STAGE3_PROMPT_MANIFEST")"
quality_cases="$(jq '.qualityCases' "$STAGE3_DATASET_MANIFEST")"
failure_cases="$(jq '.failureInjectionCases' "$STAGE3_DATASET_MANIFEST")"

mkdir -p "$(dirname -- "$STAGE3_RELEASE_MANIFEST_OUTPUT")"
jq -n \
  --arg candidateId "$STAGE3_CANDIDATE_ID" --arg backendSha "$STAGE3_BACKEND_SHA" --arg frontendSha "$STAGE3_FRONTEND_SHA" \
  --arg datasetManifestSha "$dataset_sha" --arg datasetContentSha "$dataset_content_sha" --arg promptManifestSha "$prompt_sha" \
  --arg candidateSummarySha "$summary_sha" --arg humanReviewSha "$review_sha" --arg evidenceIndexSha "$evidence_sha" \
  --arg runId "$STAGE3_WORKFLOW_RUN_ID" --arg runAttempt "$STAGE3_WORKFLOW_RUN_ATTEMPT" --arg workflowSha "$STAGE3_WORKFLOW_SHA" \
  --arg actor "$STAGE3_WORKFLOW_ACTOR" --arg executedAt "$STAGE3_WORKFLOW_EXECUTED_AT" \
  --argjson promptCount "$prompt_count" --argjson qualityCases "$quality_cases" --argjson failureCases "$failure_cases" \
  --argjson backendTests "$STAGE3_BACKEND_TEST_COUNT" --argjson frontendTests "$STAGE3_FRONTEND_TEST_COUNT" \
  '{schemaVersion:1,stage:"stage3",candidateId:$candidateId,status:"PASS",
    backend:{repository:"Zhi-Hua-Yuan/LearningManage",sha:$backendSha},
    frontend:{repository:"Zhi-Hua-Yuan/learning-manage-frontend",sha:$frontendSha},
    flyway:{head:"V3",historyTotal:3,v4Present:false},
    evaluation:{qualityCases:$qualityCases,failureInjectionCases:$failureCases,promptCodes:$promptCount,regressionRounds:3,holdoutRounds:3,model:"qwen-plus",graderModel:"qwen-max",datasetManifestSha256:$datasetManifestSha,datasetContentSha256:$datasetContentSha,promptManifestSha256:$promptManifestSha,candidateSummarySha256:$candidateSummarySha,humanReviewSha256:$humanReviewSha},
    regression:{backendTests:$backendTests,frontendTests:$frontendTests,status:"PASS"},
    evidence:{indexSha256:$evidenceIndexSha,rawRetentionDays:30},
    workflow:{runId:$runId,runAttempt:$runAttempt,workflowSha:$workflowSha,triggeredBy:$actor,executedAt:$executedAt}}' \
  > "$STAGE3_RELEASE_MANIFEST_OUTPUT"

manifest_sha="$(hash_file "$STAGE3_RELEASE_MANIFEST_OUTPUT")"
printf '%s  %s\n' "$manifest_sha" "$(basename -- "$STAGE3_RELEASE_MANIFEST_OUTPUT")" > "${STAGE3_RELEASE_MANIFEST_OUTPUT}.sha256"
printf 'stage3.manifest.status=PASS\nstage3.manifest.sha256=%s\n' "$manifest_sha"
