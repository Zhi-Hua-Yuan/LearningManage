#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib/ci-common.sh"
ci_require_command jq

report="${1:-${AI_REAL_PROVIDER_REPORT:-target/real-provider-validation.json}}"
[[ -s "$report" ]] || ci_fail "wp7_real_provider_report_missing"

jq -e '
  .schemaVersion == 1 and
  (.backendSha | type == "string" and length > 0) and
  (.workflowRunId | type == "string" and length > 0) and
  (.model | type == "string" and length > 0) and
  .roundCount == 3 and
  .scenarioCount == 9 and
  .scenarioStatus == "PASS" and
  (.finishReason | contains("stop") and contains("tool_calls")) and
  (.inputTokens | type == "number" and . >= 0) and
  (.outputTokens | type == "number" and . >= 0) and
  (.totalTokens | type == "number" and . > 0) and
  (.estimatedCost | type == "number" and . >= 0) and
  (.priceVersion | type == "string" and length > 0) and
  (.currency | test("^[A-Z]{3}$")) and
  (.providerRequestIdHash | test("^[0-9a-f]{64}$")) and
  (.latencyMs | type == "number" and . >= 0) and
  (.executedAt | fromdateiso8601 | type == "number") and
  (.rounds | length == 3) and
  ([.rounds[].scenarioStatus] | all(. == "PASS")) and
  ([.rounds[].scenarios[]] | length == 9) and
  ([.rounds[].scenarios[].status] | all(. == "PASS")) and
  ([.rounds[].scenarios[].providerRequestIdHash] | all(test("^[0-9a-f]{64}$")))
' "$report" >/dev/null || ci_fail "wp7_real_provider_report_invalid"

if grep -Eqi '(authorization|cookie|api[_-]?key|bearer[[:space:]]|eyJ[A-Za-z0-9_-]{20,}\.|sk-[A-Za-z0-9]{20,}|完整 Prompt|完整响应)' "$report"; then
  ci_fail "wp7_real_provider_report_contains_sensitive_material"
fi

ci_emit "wp7.realProvider.rounds" "$(jq -r '.roundCount' "$report")"
ci_emit "wp7.realProvider.scenarios" "$(jq -r '.scenarioCount' "$report")"
ci_emit "wp7.realProvider.status" "PASS"
