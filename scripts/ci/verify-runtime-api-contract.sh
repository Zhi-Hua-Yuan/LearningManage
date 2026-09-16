#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

ci_assert_ci_target
ci_assert_ci_flyway_identity
ci_assert_ci_app_identity
ci_require_command "curl"
ci_require_command "jq"
ci_require_command "sha256sum"
ci_require_command "sort"
ci_require_command "comm"
ci_require_command "uniq"
ci_require_command "mktemp"
ci_require_env "CI_FRONTEND_ARTIFACT_DIR"
ci_require_env "CI_API_CONTRACT_OUTPUT_DIR"
ci_require_env "CI_RUNTIME_OPENAPI_URL"
ci_require_env "CI_OPENAPI_BASELINE_FILE"
ci_require_env "CI_OASDIFF_BIN"
ci_require_env "CI_OASDIFF_VERSION"

frontend_dir="${CI_FRONTEND_ARTIFACT_DIR}"
output_dir="${CI_API_CONTRACT_OUTPUT_DIR}"
frontend_contract="${frontend_dir}/frontend-api-contract.json"
frontend_contract_sha="${frontend_dir}/frontend-api-contract.sha256"
frontend_contract_schema="${frontend_dir}/frontend-api-contract.schema.json"
runtime_openapi="${output_dir}/runtime-openapi.json"
runtime_openapi_sha="${output_dir}/runtime-openapi.sha256"
comparison_report="${output_dir}/api-contract-report.json"
comparison_report_sha="${output_dir}/api-contract-report.sha256"
breaking_report="${output_dir}/openapi-breaking-change-report.json"
breaking_report_sha="${output_dir}/openapi-breaking-change-report.sha256"
baseline_openapi="${CI_OPENAPI_BASELINE_FILE}"
baseline_openapi_sha="${baseline_openapi}.sha256"

[[ -f "$frontend_contract" ]] || ci_fail "frontend_contract_missing"
[[ -f "$frontend_contract_sha" ]] || ci_fail "frontend_contract_checksum_missing"
[[ -f "$frontend_contract_schema" ]] || ci_fail "frontend_contract_schema_missing"
[[ -f "$baseline_openapi" ]] || ci_fail "openapi_baseline_missing"
[[ -f "$baseline_openapi_sha" ]] || ci_fail "openapi_baseline_checksum_missing"
[[ -x "$CI_OASDIFF_BIN" ]] || ci_fail "oasdiff_binary_missing"

(cd "$frontend_dir" && sha256sum --check "$(basename -- "$frontend_contract_sha")") \
    || ci_fail "frontend_contract_checksum_invalid"
(cd "$(dirname -- "$baseline_openapi")" \
    && sha256sum --check "$(basename -- "$baseline_openapi_sha")") \
    || ci_fail "openapi_baseline_checksum_invalid"
"$CI_OASDIFF_BIN" --version | grep -Eq \
    "(^|[^0-9])${CI_OASDIFF_VERSION//./\\.}([^0-9]|$)" \
    || ci_fail "oasdiff_version_mismatch"

jq -e '
    .schemaVersion == 1 and
    .basePath == "/api" and
    (.operations | type == "array" and length > 0) and
    all(.operations[];
        (.method | IN("DELETE", "GET", "PATCH", "POST", "PUT")) and
        (.path | type == "string" and startswith("/") and
            (startswith("/api") | not) and (test("[?#]") | not)))
' "$frontend_contract" >/dev/null || ci_fail "frontend_contract_invalid"

mkdir -p "$output_dir"
runtime_tmp="$(mktemp "${output_dir}/runtime-openapi.XXXXXX")"
runtime_compare_tmp="$(mktemp "${output_dir}/runtime-openapi-compare.XXXXXX")"
front_ops_tmp="$(mktemp "${output_dir}/frontend-operations.XXXXXX")"
runtime_ops_tmp="$(mktemp "${output_dir}/runtime-operations.XXXXXX")"
front_sorted_tmp="$(mktemp "${output_dir}/frontend-operations-sorted.XXXXXX")"
runtime_sorted_tmp="$(mktemp "${output_dir}/runtime-operations-sorted.XXXXXX")"
missing_tmp="$(mktemp "${output_dir}/missing-operations.XXXXXX")"
trap 'rm -f "$runtime_tmp" "$runtime_compare_tmp" "$front_ops_tmp" "$runtime_ops_tmp" "$front_sorted_tmp" "$runtime_sorted_tmp" "$missing_tmp"' EXIT

curl --fail --silent --show-error --max-time 20 \
    "$CI_RUNTIME_OPENAPI_URL" > "$runtime_tmp" || ci_fail "runtime_openapi_download_failed"
mv -- "$runtime_tmp" "$runtime_openapi"

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.paths | type == "object" and length > 0)
' "$runtime_openapi" >/dev/null || ci_fail "runtime_openapi_invalid"

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.paths | type == "object" and length > 0)
' "$baseline_openapi" >/dev/null || ci_fail "openapi_baseline_invalid"

# SpringDoc derives the server URL from the request host/port. Preserve the raw
# runtime document as evidence, but canonicalize only that volatile field for
# the compatibility comparison.
jq --slurpfile baseline "$baseline_openapi" \
    '.servers = ($baseline[0].servers // [])' \
    "$runtime_openapi" > "$runtime_compare_tmp"

if ! "$CI_OASDIFF_BIN" breaking "$baseline_openapi" "$runtime_compare_tmp" \
    --allow-external-refs=false --fail-on ERR --format json > "$breaking_report"; then
    printf '%s  %s\n' "$(sha256sum "$breaking_report" | awk '{print toupper($1)}')" \
        "$(basename -- "$breaking_report")" > "$breaking_report_sha"
    ci_fail "runtime_openapi_breaking_change_detected"
fi
printf '%s  %s\n' "$(sha256sum "$breaking_report" | awk '{print toupper($1)}')" \
    "$(basename -- "$breaking_report")" > "$breaking_report_sha"

jq -r '
    .operations[] |
    [(.method | ascii_upcase), (.path | gsub("\\{[^{}]+\\}"; "{}"))] |
    @tsv
' "$frontend_contract" > "$front_ops_tmp"

jq -r '
    (.paths // {}) |
    to_entries[] |
    .key as $path |
    .value |
    to_entries[] |
    select(.key | IN("get", "post", "put", "patch", "delete")) |
    [(.key | ascii_upcase), ($path | gsub("\\{[^{}]+\\}"; "{}"))] |
    @tsv
' "$runtime_openapi" > "$runtime_ops_tmp"

sort "$front_ops_tmp" > "$front_sorted_tmp"
sort -u "$runtime_ops_tmp" > "$runtime_sorted_tmp"

duplicate_operations="$(sort "$front_ops_tmp" | uniq -d)"
[[ -z "$duplicate_operations" ]] || ci_fail "frontend_contract_duplicate_operation"

frontend_operation_count="$(wc -l < "$front_sorted_tmp" | tr -d '[:space:]')"
runtime_operation_count="$(wc -l < "$runtime_sorted_tmp" | tr -d '[:space:]')"
comm -23 "$front_sorted_tmp" "$runtime_sorted_tmp" > "$missing_tmp"
missing_operation_count="$(wc -l < "$missing_tmp" | tr -d '[:space:]')"
matched_operation_count=$((frontend_operation_count - missing_operation_count))

frontend_contract_sha256="$(sha256sum "$frontend_contract" | awk '{print toupper($1)}')"
runtime_document_sha256="$(sha256sum "$runtime_openapi" | awk '{print toupper($1)}')"
runtime_openapi_version="$(jq -er '.openapi' "$runtime_openapi")"
baseline_document_sha256="$(sha256sum "$baseline_openapi" | awk '{print toupper($1)}')"
breaking_report_sha256="$(sha256sum "$breaking_report" | awk '{print toupper($1)}')"
missing_operations_json="$(jq -Rn '[inputs | split("\t") | {method: .[0], path: .[1]}]' < "$missing_tmp")"

if [[ "$missing_operation_count" == "0" ]]; then
    gate_status="PASS"
else
    gate_status="FAIL"
fi

jq -n \
    --arg status "$gate_status" \
    --arg runtimeVersion "$runtime_openapi_version" \
    --arg frontendContractSha256 "$frontend_contract_sha256" \
    --arg runtimeDocumentSha256 "$runtime_document_sha256" \
    --arg baselineDocumentSha256 "$baseline_document_sha256" \
    --arg breakingReportSha256 "$breaking_report_sha256" \
    --arg oasdiffVersion "$CI_OASDIFF_VERSION" \
    --argjson frontendOperationCount "$frontend_operation_count" \
    --argjson runtimeOperationCount "$runtime_operation_count" \
    --argjson matchedOperationCount "$matched_operation_count" \
    --argjson missingOperationCount "$missing_operation_count" \
    --argjson missingOperations "$missing_operations_json" \
    '{
        schemaVersion: 1,
        status: $status,
        runtimeOpenapiVersion: $runtimeVersion,
        frontendOperationCount: $frontendOperationCount,
        runtimeOperationCount: $runtimeOperationCount,
        matchedOperationCount: $matchedOperationCount,
        missingOperationCount: $missingOperationCount,
        missingOperations: $missingOperations,
        frontendContractSha256: $frontendContractSha256,
        runtimeDocumentSha256: $runtimeDocumentSha256,
        breakingChangeGate: "PASS",
        baselineDocumentSha256: $baselineDocumentSha256,
        breakingReportSha256: $breakingReportSha256,
        oasdiffVersion: $oasdiffVersion
    }' > "$comparison_report"

printf '%s  %s\n' "$(sha256sum "$runtime_openapi" | awk '{print toupper($1)}')" \
    "$(basename -- "$runtime_openapi")" > "$runtime_openapi_sha"
printf '%s  %s\n' "$(sha256sum "$comparison_report" | awk '{print toupper($1)}')" \
    "$(basename -- "$comparison_report")" > "$comparison_report_sha"

emit_output() {
    local key="$1"
    local value="$2"
    printf '%s=%s\n' "$key" "$value"
    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
        printf '%s=%s\n' "$key" "$value" >> "$GITHUB_OUTPUT"
    fi
}

emit_output "frontend_contract_sha256" "$frontend_contract_sha256"
emit_output "runtime_document_sha256" "$runtime_document_sha256"
emit_output "comparison_report_sha256" "$(sha256sum "$comparison_report" | awk '{print toupper($1)}')"
emit_output "frontend_operation_count" "$frontend_operation_count"
emit_output "runtime_operation_count" "$runtime_operation_count"
emit_output "matched_operation_count" "$matched_operation_count"
emit_output "missing_operation_count" "$missing_operation_count"
emit_output "runtime_openapi_version" "$runtime_openapi_version"
emit_output "baseline_document_sha256" "$baseline_document_sha256"
emit_output "breaking_report_sha256" "$breaking_report_sha256"
emit_output "oasdiff_version" "$CI_OASDIFF_VERSION"

[[ "$missing_operation_count" == "0" ]] || ci_fail "frontend_operation_missing_from_runtime_openapi"
ci_emit "api_contract.gate" "PASS"
ci_emit "api_contract.frontend_operations" "$frontend_operation_count"
ci_emit "api_contract.runtime_operations" "$runtime_operation_count"
ci_emit "api_contract.matched_operations" "$matched_operation_count"
ci_emit "api_contract.breaking_change_gate" "PASS"
