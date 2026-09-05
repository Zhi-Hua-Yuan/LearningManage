#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib/ci-common.sh"
ci_require_command jq

report="${1:-${AI_REAL_PROVIDER_REPORT:-target/real-provider-validation.json}}"
expected_backend_sha="${2:-${WP7_EXPECTED_BACKEND_SHA:-}}"
expected_workflow_run_id="${3:-${WP7_EXPECTED_WORKFLOW_RUN_ID:-}}"
[[ -s "$report" ]] || ci_fail "wp7_real_provider_report_missing"
[[ "$expected_backend_sha" =~ ^[0-9a-f]{40}$ ]] \
  || ci_fail "wp7_expected_backend_sha_invalid"
[[ "$expected_workflow_run_id" =~ ^[1-9][0-9]*$ ]] \
  || ci_fail "wp7_expected_workflow_run_id_invalid"

jq -e --arg expectedBackendSha "$expected_backend_sha" \
  --arg expectedWorkflowRunId "$expected_workflow_run_id" '
  .schemaVersion == 1 and
  .backendSha == $expectedBackendSha and
  .workflowRunId == $expectedWorkflowRunId and
  (.model | type == "string" and length > 0) and
  .roundCount == 3 and
  .scenarioCount == 9 and
  .scenarioStatus == "PASS" and
  (.finishReason | contains("stop")) and
  (.inputTokens | type == "number" and . >= 0) and
  (.outputTokens | type == "number" and . >= 0) and
  (.totalTokens | type == "number" and . > 0) and
  (.estimatedCost | type == "number" and . >= 0) and
  (.priceVersion | type == "string" and length > 0) and
  (.currency | test("^[A-Z]{3}$")) and
  (.providerRequestIdHash | test("^[0-9a-f]{64}$")) and
  (.latencyMs | type == "number" and . >= 0) and
  (.executedAt |
    if test("\\.[0-9]+Z$") then sub("\\.[0-9]+Z$"; "Z") else . end |
    fromdateiso8601 | type == "number") and
  (.rounds | length == 3) and
  ([.rounds[].scenarioStatus] | all(. == "PASS")) and
  ([.rounds[].scenarios[]] | length == 9) and
  ([.rounds[].scenarios[].status] | all(. == "PASS")) and
  ([.rounds[].scenarios[] | select(.scenario == "text")] | length == 3) and
  ([.rounds[].scenarios[] | select(.scenario == "text") | .finishReason] | all(. == "stop")) and
  ([.rounds[].scenarios[] | select(.scenario == "forced-tool-call")] | length == 3) and
  ([.rounds[].scenarios[] | select(.scenario == "forced-tool-call") | .finishReason] |
    all(. == "tool_calls" or . == "stop")) and
  ([.rounds[].scenarios[] | select(.scenario == "tool-result-round-trip")] | length == 3) and
  ([.rounds[].scenarios[] | select(.scenario == "tool-result-round-trip") | .finishReason] |
    all(. == "stop")) and
  ([.rounds[].scenarios[].providerRequestIdHash] | all(test("^[0-9a-f]{64}$")))
' "$report" >/dev/null || ci_fail "wp7_real_provider_report_invalid"

if grep -Eqi '(authorization|cookie|api[_-]?key|bearer[[:space:]]|eyJ[A-Za-z0-9_-]{20,}\.|sk-[A-Za-z0-9]{20,}|完整 Prompt|完整响应)' "$report"; then
  ci_fail "wp7_real_provider_report_contains_sensitive_material"
fi

ci_emit "wp7.realProvider.rounds" "$(jq -r '.roundCount' "$report")"
ci_emit "wp7.realProvider.scenarios" "$(jq -r '.scenarioCount' "$report")"
ci_emit "wp7.realProvider.status" "PASS"
