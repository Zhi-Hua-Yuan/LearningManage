#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/ci-common.sh"

ci_require_command jq
ci_require_command sort
ci_require_command comm
ci_require_command sha256sum

baseline_file="${STAGE1_BASELINE_CONTRACT:-${project_root}/docs/stage1/release/stage0-frontend-operation-baseline.json}"
current_file="${STAGE1_CURRENT_CONTRACT:?STAGE1_CURRENT_CONTRACT is required}"
runtime_file="${STAGE1_RUNTIME_OPENAPI:?STAGE1_RUNTIME_OPENAPI is required}"
output_dir="${STAGE1_API_OUTPUT_DIR:-${project_root}/.codex-tmp/stage1-api-contract}"

[[ -s "$baseline_file" ]] || ci_fail "stage1_baseline_contract_missing"
[[ -s "$current_file" ]] || ci_fail "stage1_current_contract_missing"
[[ -s "$runtime_file" ]] || ci_fail "stage1_runtime_openapi_missing"

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
legacy_missing="$(mktemp)"
current_missing="$(mktemp)"
trap 'rm -f "$baseline_ops" "$current_ops" "$runtime_ops" "$legacy_missing" "$current_missing"' EXIT

jq -r '.operations[] | [(.method|ascii_upcase),(.path|gsub("\\{[^{}]+\\}";"{}"))] | @tsv' "$baseline_file" | sort -u > "$baseline_ops"
jq -r '.operations[] | [(.method|ascii_upcase),(.path|gsub("\\{[^{}]+\\}";"{}"))] | @tsv' "$current_file" | sort -u > "$current_ops"
jq -r '(.paths // {}) | to_entries[] | .key as $path | .value | to_entries[] |
  select(.key|IN("get","post","put","patch","delete")) |
  [(.key|ascii_upcase),($path|gsub("\\{[^{}]+\\}";"{}"))] | @tsv' "$runtime_file" | sort -u > "$runtime_ops"

comm -23 "$baseline_ops" "$current_ops" > "$legacy_missing"
comm -23 "$baseline_ops" "$runtime_ops" > "${output_dir}/legacy-missing-from-runtime.tsv"
comm -23 "$current_ops" "$runtime_ops" > "$current_missing"

legacy_count="$(wc -l < "$baseline_ops" | tr -d '[:space:]')"
current_count="$(wc -l < "$current_ops" | tr -d '[:space:]')"
runtime_count="$(wc -l < "$runtime_ops" | tr -d '[:space:]')"
legacy_missing_current_count="$(wc -l < "$legacy_missing" | tr -d '[:space:]')"
legacy_missing_runtime_count="$(wc -l < "${output_dir}/legacy-missing-from-runtime.tsv" | tr -d '[:space:]')"
current_missing_runtime_count="$(wc -l < "$current_missing" | tr -d '[:space:]')"

baseline_sha="$(sha256sum "$baseline_file" | awk '{print toupper($1)}')"
current_sha="$(sha256sum "$current_file" | awk '{print toupper($1)}')"
runtime_sha="$(sha256sum "$runtime_file" | awk '{print toupper($1)}')"

status="PASS"
[[ "$legacy_count" == "37" ]] || status="FAIL"
[[ "$current_count" == "44" ]] || status="FAIL"
[[ "$legacy_missing_current_count" == "0" ]] || status="FAIL"
[[ "$legacy_missing_runtime_count" == "0" ]] || status="FAIL"
[[ "$current_missing_runtime_count" == "0" ]] || status="FAIL"

jq -n \
  --arg status "$status" \
  --arg baselineSha256 "$baseline_sha" \
  --arg currentSha256 "$current_sha" \
  --arg runtimeSha256 "$runtime_sha" \
  --argjson legacyOperationCount "$legacy_count" \
  --argjson currentOperationCount "$current_count" \
  --argjson runtimeOperationCount "$runtime_count" \
  --argjson legacyMissingFromCurrentCount "$legacy_missing_current_count" \
  --argjson legacyMissingFromRuntimeCount "$legacy_missing_runtime_count" \
  --argjson currentMissingFromRuntimeCount "$current_missing_runtime_count" \
  '{schemaVersion:1,status:$status,baselineSha256:$baselineSha256,currentContractSha256:$currentSha256,runtimeOpenapiSha256:$runtimeSha256,
    legacyOperationCount:$legacyOperationCount,currentOperationCount:$currentOperationCount,runtimeOperationCount:$runtimeOperationCount,
    legacyMissingFromCurrentCount:$legacyMissingFromCurrentCount,legacyMissingFromRuntimeCount:$legacyMissingFromRuntimeCount,
    currentMissingFromRuntimeCount:$currentMissingFromRuntimeCount}' \
  > "${output_dir}/stage1-api-compatibility-report.json"
printf '%s  %s\n' "$(sha256sum "${output_dir}/stage1-api-compatibility-report.json" | awk '{print toupper($1)}')" stage1-api-compatibility-report.json \
  > "${output_dir}/stage1-api-compatibility-report.sha256"

printf 'stage1.api.status=%s\n' "$status"
printf 'stage1.api.legacy=%s/%s\n' "$legacy_count" "$legacy_count"
printf 'stage1.api.current=%s\n' "$current_count"
printf 'stage1.api.legacy_missing_current=%s\n' "$legacy_missing_current_count"
printf 'stage1.api.legacy_missing_runtime=%s\n' "$legacy_missing_runtime_count"
printf 'stage1.api.current_missing_runtime=%s\n' "$current_missing_runtime_count"

[[ "$status" == "PASS" ]] || ci_fail "stage1_api_compatibility_failed"
