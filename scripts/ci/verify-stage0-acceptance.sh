#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
acceptance_file="${STAGE0_ACCEPTANCE_FILE:-${project_root}/docs/stage0/acceptance/stage0-acceptance.json}"
schema_file="${STAGE0_ACCEPTANCE_SCHEMA:-${project_root}/docs/stage0/acceptance/stage0-acceptance.schema.json}"
schema_validator="${STAGE0_SCHEMA_VALIDATOR:-${script_dir}/validate-json-schema.py}"
python_bin="${STAGE0_PYTHON_BIN:-python3}"

fail() {
    printf 'stage0.acceptance.error=%s\n' "$1" >&2
    exit 1
}

command -v jq >/dev/null 2>&1 || fail jq_required
command -v "$python_bin" >/dev/null 2>&1 || fail python3_required
[[ -f "$acceptance_file" ]] || fail acceptance_file_missing
[[ -f "$schema_file" ]] || fail schema_file_missing
[[ -f "$schema_validator" ]] || fail schema_validator_missing

jq -e . "$acceptance_file" >/dev/null || fail acceptance_json_invalid
jq -e . "$schema_file" >/dev/null || fail acceptance_schema_invalid
"$python_bin" "$schema_validator" "$acceptance_file" "$schema_file" >/dev/null || fail schema_validation_failed

stage="$(jq -er '.stage' "$acceptance_file")"
schema_version="$(jq -er '.schemaVersion' "$acceptance_file")"
status="$(jq -er '.status' "$acceptance_file")"
[[ "$stage" == 'stage0' ]] || fail stage_mismatch
[[ "$schema_version" == '1' ]] || fail schema_version_mismatch
[[ "$status" == 'PROVISIONAL' ]] || fail provisional_contract_required

jq -e '
    (.repositories.backend.branch == "develop") and
    (.repositories.frontend.branch == "develop") and
    (.repositories.backend.sha | test("^[0-9a-f]{40}$")) and
    (.repositories.frontend.sha | test("^[0-9a-f]{40}$")) and
    (.sourceMatrix | startswith("docs/")) and
    (.policy.requiredFailureCount == 0) and
    (.policy.unclassifiedCount == 0) and
    (.policy.mainDatabaseTouchedByD3 == false) and
    (.policy.publishedFlywayV1ModifiedByD3 == false) and
    (.finalSeal.status == "PENDING")
' "$acceptance_file" >/dev/null || fail contract_policy_invalid

gate_count="$(jq -er '.gates | length' "$acceptance_file")"
[[ "$gate_count" == '21' ]] || fail required_gate_count

jq -e '
    ([.gates[].status] | all(. == "PASS" or . == "ACCEPTED_RISK" or . == "DEFERRED" or . == "NOT_APPLICABLE")) and
    ([.gates[].id] | length == (unique | length)) and
    ([.closingGates[].status] | all(. == "PENDING")) and
    ([.closingGates[].id] | length == (unique | length)) and
    (.summary.fail == 0) and
    (.summary.pending == 2)
' "$acceptance_file" >/dev/null || fail gate_status_invalid

for status_pair in \
    'PASS pass' \
    'ACCEPTED_RISK acceptedRisk' \
    'DEFERRED deferred' \
    'NOT_APPLICABLE notApplicable' \
    'FAIL fail'; do
    read -r gate_status summary_key <<< "$status_pair"
    actual_count="$(jq -er --arg status "$gate_status" '[.gates[] | select(.status == $status)] | length' "$acceptance_file")"
    declared_count="$(jq -er --arg key "$summary_key" '.summary[$key]' "$acceptance_file")"
    [[ "$actual_count" == "$declared_count" ]] || fail "summary_mismatch:${summary_key}"
done

pending_count="$(jq -er '[.closingGates[] | select(.status == "PENDING")] | length' "$acceptance_file")"
declared_pending="$(jq -er '.summary.pending' "$acceptance_file")"
[[ "$pending_count" == "$declared_pending" ]] || fail summary_mismatch:pending

for evidence_path in $(jq -er '.gates[].evidence[]' "$acceptance_file"); do
    [[ -f "${project_root}/${evidence_path}" ]] || fail "evidence_missing:${evidence_path}"
done

source_matrix="$(jq -er '.sourceMatrix' "$acceptance_file")"
[[ -f "${project_root}/${source_matrix}" ]] || fail source_matrix_missing

if grep -REnI --include='*.json' --include='*.md' \
    -e 'AKIA[0-9A-Z]{16}' \
    -e '-----BEGIN [A-Z ]*PRIVATE KEY-----' \
    -e 'Bearer[[:space:]]+[A-Za-z0-9._-]{20,}' \
    "${project_root}/docs/stage0/acceptance" >/dev/null; then
    fail sensitive_value_detected
fi

printf 'stage0.acceptance.status=%s\n' "$status"
printf 'stage0.acceptance.gates=%s\n' "$gate_count"
printf 'stage0.acceptance.pending_closing_gates=%s\n' "$(jq -er '[.closingGates[] | select(.status == "PENDING")] | length' "$acceptance_file")"
printf 'stage0.acceptance.risks=%s\n' "$(jq -er '.risks | length' "$acceptance_file")"
printf 'stage0.acceptance.main_database_touched=false\n'
printf 'stage0.acceptance.published_v1_modified=false\n'
