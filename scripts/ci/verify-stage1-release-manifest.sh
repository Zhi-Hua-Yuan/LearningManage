#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/release-candidate-common.sh"
source "${script_dir}/lib/ci-common.sh"
ci_require_command jq
python_bin="${STAGE1_PYTHON_BIN:-python3}"
ci_require_command "$python_bin"

manifest="${STAGE1_MANIFEST:-${1:-}}"
schema="${STAGE1_MANIFEST_SCHEMA:-${project_root}/docs/stage1/release/stage1-release-candidate-manifest.schema.json}"
[[ -s "$manifest" ]] || release_fail "stage1_manifest_missing"
[[ -s "$schema" ]] || release_fail "stage1_manifest_schema_missing"

"$python_bin" "${project_root}/scripts/ci/validate-json-schema.py" "$manifest" "$schema"

jq -e '
  (.schemaVersion == 1 and .stage == "stage1") and
  (.status == "CANDIDATE_PASS" or .status == "PASS") and
  (.apiContract.legacyOperationCount == 37 and .apiContract.frontendOperationCount == 44 and
   .apiContract.legacyMissingFromCurrentCount == 0 and .apiContract.legacyMissingFromRuntimeCount == 0 and
   .apiContract.missingOperationCount == 0 and .apiContract.matchedOperationCount == 44) and
  (.regression.status == "PASS" and .regression.personalFlow == "PASS" and
   .regression.stage1Flow == "PASS" and .regression.aiBreakdownFlow == "PASS") and
  ((.status == "CANDIDATE_PASS" and .riskClosure.s1R010 == "ELIGIBLE" and .riskClosure.openBlockingRiskCount == 1) or
   (.status == "PASS" and .riskClosure.s1R010 == "CLOSED" and .riskClosure.openBlockingRiskCount == 0))
' "$manifest" >/dev/null || release_fail "stage1_manifest_invariants_invalid"

sidecar="${manifest}.sha256"
[[ -s "$sidecar" ]] || release_fail "stage1_manifest_checksum_missing"
(cd "$(dirname -- "$manifest")" && sha256sum --check "$(basename -- "$sidecar")")
printf 'stage1.manifest.validation=PASS\n'
