#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/release-candidate-common.sh"
source "${script_dir}/lib/ci-common.sh"
manifest="${STAGE3_RELEASE_MANIFEST:-${1:-}}"
schema="${STAGE3_RELEASE_MANIFEST_SCHEMA:-${project_root}/docs/stage3/release/stage3-release-candidate-manifest.schema.json}"
python_bin="${STAGE3_PYTHON_BIN:-python3}"
[[ -s "$manifest" ]] || release_fail "stage3_manifest_missing"
[[ -s "$schema" ]] || release_fail "stage3_manifest_schema_missing"
"$python_bin" "${project_root}/scripts/ci/validate-json-schema.py" "$manifest" "$schema"
jq -e '.status == "PASS" and .stage == "stage3" and .flyway.head == "V3" and .flyway.v4Present == false and
  .evaluation.qualityCases == 170 and .evaluation.failureInjectionCases == 40 and .evaluation.promptCodes == 6 and
  .evaluation.regressionRounds == 3 and .evaluation.holdoutRounds == 3 and
  .regression.backendTests >= 710 and .regression.frontendTests >= 484 and .regression.status == "PASS"' "$manifest" >/dev/null \
  || release_fail "stage3_manifest_invariants_invalid"
(cd "$(dirname -- "$manifest")" && sha256sum --check "$(basename -- "${manifest}.sha256")")
printf 'stage3.manifest.validation=PASS\n'
