#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/release-candidate-common.sh"
source "${script_dir}/lib/ci-common.sh"
ci_require_command jq
python_bin="${STAGE2_PYTHON_BIN:-python3}"
ci_require_command "$python_bin"

manifest="${STAGE2_MANIFEST:-${1:-}}"
schema="${STAGE2_MANIFEST_SCHEMA:-${project_root}/docs/stage2/release/stage2-release-candidate-manifest.schema.json}"
[[ -s "$manifest" ]] || release_fail "stage2_manifest_missing"
[[ -s "$schema" ]] || release_fail "stage2_manifest_schema_missing"

"$python_bin" "${project_root}/scripts/ci/validate-json-schema.py" "$manifest" "$schema"
jq -e '
  .schemaVersion == 1 and .stage == "stage2" and
  (.status == "CANDIDATE_PASS" or .status == "PASS") and
  .flyway.historyTotal == 3 and .flyway.v4Present == false and
  .apiContract.legacyOperationCount == 37 and .apiContract.frontendOperationCount == 44 and
  .apiContract.runtimeOperationCount >= 65 and .apiContract.matchedOperationCount == 44 and .apiContract.missingOperationCount == 0 and
  .regression.backendTests >= 710 and .regression.frontendTests >= 484 and .regression.status == "PASS" and
  .realProvider.status == "BOUND" and .realProvider.model == "qwen-plus" and .realProvider.rounds == 3 and .realProvider.scenarios == 9 and
  ((.status == "CANDIDATE_PASS" and .riskClosure.s2R008 == "ELIGIBLE" and .riskClosure.openBlockingRiskCount == 1) or
   (.status == "PASS" and .riskClosure.s2R008 == "CLOSED" and .riskClosure.openBlockingRiskCount == 0))
' "$manifest" >/dev/null || release_fail "stage2_manifest_invariants_invalid"

expected_gate_ids=(
  S2-A-001 S2-A-002 S2-A-003 S2-A-004 S2-A-005 S2-A-006
  S2-A-007 S2-A-008 S2-A-009 S2-A-010 S2-A-011
)
if [[ "$(jq -r '.status' "$manifest")" == "PASS" ]]; then
  expected_gate_ids+=(S2-A-012)
fi
actual_gate_ids="$(jq -r '.stage2Acceptance.gatesPassed[]' "$manifest" | sort -u)"
[[ "$(printf '%s\n' "$actual_gate_ids" | sed '/^$/d' | wc -l | tr -d '[:space:]')" == "${#expected_gate_ids[@]}" ]] \
  || release_fail "stage2_manifest_gate_count_invalid"
for gate_id in "${expected_gate_ids[@]}"; do
  grep -Fx -- "$gate_id" <<< "$actual_gate_ids" >/dev/null \
    || release_fail "stage2_manifest_gate_missing_${gate_id}"
done

sidecar="${manifest}.sha256"
[[ -s "$sidecar" ]] || release_fail "stage2_manifest_checksum_missing"
(cd "$(dirname "$manifest")" && sha256sum --check "$(basename "$sidecar")")
printf 'stage2.manifest.validation=PASS\n'
