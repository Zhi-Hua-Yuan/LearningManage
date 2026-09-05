#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/ci-common.sh"

ci_require_command jq
ci_require_command sort
ci_require_command comm
ci_require_command sha256sum

baseline_file="${STAGE2_BASELINE_CONTRACT:-${project_root}/docs/stage1/release/stage0-frontend-operation-baseline.json}"
current_file="${STAGE2_CURRENT_CONTRACT:?STAGE2_CURRENT_CONTRACT is required}"
runtime_file="${STAGE2_RUNTIME_OPENAPI:?STAGE2_RUNTIME_OPENAPI is required}"
output_dir="${STAGE2_API_OUTPUT_DIR:-${project_root}/.codex-tmp/stage2-api-contract}"

[[ -s "$baseline_file" ]] || ci_fail "stage2_baseline_contract_missing"
[[ -s "$current_file" ]] || ci_fail "stage2_current_contract_missing"
[[ -s "$runtime_file" ]] || ci_fail "stage2_runtime_openapi_missing"

validate_contract() {
  local file="$1"
  jq -e '
    .schemaVersion == 1 and .basePath == "/api" and
    (.operations | type == "array" and length > 0) and
    all(.operations[]; (.method | IN("DELETE","GET","PATCH","POST","PUT")) and
      (.path | type == "string" and startswith("/") and (test("[?#]") | not)))
  ' "$file" >/dev/null || ci_fail "invalid_frontend_contract"
}

validate_contract "$baseline_file"
validate_contract "$current_file"
jq -e '(.openapi | startswith("3.")) and (.paths | type == "object" and length > 0)' "$runtime_file" >/dev/null \
  || ci_fail "invalid_runtime_openapi"

mkdir -p "$output_dir"
baseline_ops="$(mktemp)"
current_ops="$(mktemp)"
runtime_ops="$(mktemp)"
legacy_missing_current="$(mktemp)"
legacy_missing_runtime="$(mktemp)"
current_missing_runtime="$(mktemp)"
trap 'rm -f "$baseline_ops" "$current_ops" "$runtime_ops" "$legacy_missing_current" "$legacy_missing_runtime" "$current_missing_runtime"' EXIT

jq -r '.operations[] | [(.method|ascii_upcase),(.path|gsub("\\{[^{}]+\\}";"{}"))] | @tsv' "$baseline_file" | sort -u > "$baseline_ops"
jq -r '.operations[] | [(.method|ascii_upcase),(.path|gsub("\\{[^{}]+\\}";"{}"))] | @tsv' "$current_file" | sort -u > "$current_ops"
jq -r '(.paths // {}) | to_entries[] | .key as $path | .value | to_entries[] |
  select(.key|IN("get","post","put","patch","delete")) |
  [(.key|ascii_upcase),($path|gsub("\\{[^{}]+\\}";"{}"))] | @tsv' "$runtime_file" | sort -u > "$runtime_ops"

comm -23 "$baseline_ops" "$current_ops" > "$legacy_missing_current"
comm -23 "$baseline_ops" "$runtime_ops" > "$legacy_missing_runtime"
comm -23 "$current_ops" "$runtime_ops" > "$current_missing_runtime"

legacy_count="$(wc -l < "$baseline_ops" | tr -d '[:space:]')"
current_count="$(wc -l < "$current_ops" | tr -d '[:space:]')"
runtime_count="$(wc -l < "$runtime_ops" | tr -d '[:space:]')"
legacy_missing_current_count="$(wc -l < "$legacy_missing_current" | tr -d '[:space:]')"
legacy_missing_runtime_count="$(wc -l < "$legacy_missing_runtime" | tr -d '[:space:]')"
current_missing_runtime_count="$(wc -l < "$current_missing_runtime" | tr -d '[:space:]')"

status=PASS
[[ "$legacy_count" == 37 ]] || status=FAIL
[[ "$current_count" == 44 ]] || status=FAIL
[[ "$runtime_count" -ge 65 ]] || status=FAIL
[[ "$legacy_missing_current_count" == 0 ]] || status=FAIL
[[ "$legacy_missing_runtime_count" == 0 ]] || status=FAIL
[[ "$current_missing_runtime_count" == 0 ]] || status=FAIL

report="${output_dir}/api-compatibility-report.json"
jq -n \
  --arg status "$status" \
  --arg baselineSha256 "$(sha256sum "$baseline_file" | awk '{print toupper($1)}')" \
  --arg currentSha256 "$(sha256sum "$current_file" | awk '{print toupper($1)}')" \
  --arg runtimeSha256 "$(sha256sum "$runtime_file" | awk '{print toupper($1)}')" \
  --argjson legacyOperationCount "$legacy_count" \
  --argjson frontendOperationCount "$current_count" \
  --argjson runtimeOperationCount "$runtime_count" \
  --argjson legacyMissingFromCurrentCount "$legacy_missing_current_count" \
  --argjson legacyMissingFromRuntimeCount "$legacy_missing_runtime_count" \
  --argjson missingOperationCount "$current_missing_runtime_count" \
  '{schemaVersion:1,stage:"stage2",status:$status,baselineSha256:$baselineSha256,
    frontendContractSha256:$currentSha256,runtimeOpenapiSha256:$runtimeSha256,
    legacyOperationCount:$legacyOperationCount,frontendOperationCount:$frontendOperationCount,
    runtimeOperationCount:$runtimeOperationCount,matchedOperationCount:($frontendOperationCount-$missingOperationCount),
    legacyMissingFromCurrentCount:$legacyMissingFromCurrentCount,
    legacyMissingFromRuntimeCount:$legacyMissingFromRuntimeCount,missingOperationCount:$missingOperationCount}' > "$report"
printf '%s  %s\n' "$(sha256sum "$report" | awk '{print toupper($1)}')" "$(basename "$report")" > "${report}.sha256"

printf 'stage2.api.status=%s\n' "$status"
printf 'stage2.api.legacy=%s\n' "$legacy_count"
printf 'stage2.api.frontend=%s\n' "$current_count"
printf 'stage2.api.runtime=%s\n' "$runtime_count"
printf 'stage2.api.missing=%s\n' "$current_missing_runtime_count"
[[ "$status" == PASS ]] || ci_fail "stage2_api_compatibility_failed"
