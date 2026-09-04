#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/ci-common.sh"
ci_require_command jq

contract="${STAGE2_ACCEPTANCE_CONTRACT:-${project_root}/docs/stage2/acceptance/stage2-acceptance-contract.json}"
schema="${STAGE2_ACCEPTANCE_SCHEMA:-${project_root}/docs/stage2/acceptance/stage2-acceptance-contract.schema.json}"
mode="${STAGE2_ACCEPTANCE_MODE:-frozen}"
schema_validator="${STAGE2_SCHEMA_VALIDATOR:-${project_root}/scripts/ci/validate-json-schema.py}"

[[ -s "$contract" ]] || ci_fail "stage2_acceptance_contract_missing"
[[ -s "$schema" ]] || ci_fail "stage2_acceptance_schema_missing"
[[ -s "$schema_validator" ]] || ci_fail "stage2_schema_validator_missing"

python_bin="${PYTHON_BIN:-python3}"
if ! command -v "$python_bin" >/dev/null 2>&1; then
  python_bin="python"
fi
command -v "$python_bin" >/dev/null 2>&1 || ci_fail "python_validator_unavailable"
"$python_bin" "$schema_validator" "$contract" "$schema" >/dev/null \
  || ci_fail "stage2_acceptance_schema_invalid"

case "$mode" in
  frozen)
    jq -e '
      .schemaVersion == 1 and .stage == "stage2" and .status == "FROZEN" and
      .baseline.stage1Tag == "stage1-v1.0.0" and
      .baseline.backendSha == "505715860cce7f04a52c00a4e4258ac8ed838b8d" and
      .baseline.frontendSha == "2ef907f292fbbacecf8a68f7d24c4701a555aa8a" and
      ([.gates[] | select(.id | IN("S2-A-001","S2-A-002","S2-A-003","S2-A-004")) | .status] | all(. == "PASS")) and
      ([.gates[] | select(.id | IN("S2-A-005","S2-A-006","S2-A-007","S2-A-008","S2-A-009","S2-A-010","S2-A-011","S2-A-012")) | .status] | all(. == "PENDING")) and
      ([.risks[].status] | all(. == "OPEN")) and
      .policy.v1Modified == false and .policy.v2Modified == false and
      .policy.v3MigrationExecuted == false and .policy.ragOrAgentImplemented == false and
      .summary.fail == 0
    ' "$contract" >/dev/null || ci_fail "stage2_frozen_contract_invalid"
    ;;
  sealed)
    jq -e '
      .schemaVersion == 1 and .stage == "stage2" and .status == "PASS" and
      ([.gates[].status] | all(. == "PASS")) and
      .policy.v1Modified == false and .policy.v2Modified == false and
      .policy.ragOrAgentImplemented == false and .summary.fail == 0 and .summary.openRisk == 0
    ' "$contract" >/dev/null || ci_fail "stage2_sealed_contract_invalid"
    ;;
  *) ci_fail "invalid_stage2_acceptance_mode" ;;
esac

jq -e '([.gates[].id] | length == 12) and ([.gates[].id] | unique | length == 12)' "$contract" >/dev/null \
  || ci_fail "stage2_acceptance_gate_set_invalid"
jq -e '([.risks[].id] | unique | length == (.risks | length))' "$contract" >/dev/null \
  || ci_fail "stage2_acceptance_risk_set_invalid"
jq -e '([.gates[].evidence[]] | all(test("^(docs/|[A-Za-z0-9 _-]+)")))' "$contract" >/dev/null \
  || ci_fail "stage2_acceptance_evidence_invalid"
jq -e '
  ([.gates[] | select(.status == "PASS")] | length) == .summary.pass and
  ([.gates[] | select(.status == "PENDING")] | length) == .summary.pending and
  ([.gates[] | select(.status == "FAIL")] | length) == .summary.fail
' "$contract" >/dev/null || ci_fail "stage2_acceptance_summary_invalid"

while IFS= read -r evidence_path; do
  [[ -z "$evidence_path" ]] && continue
  [[ -s "$project_root/$evidence_path" ]] || ci_fail "stage2_evidence_missing:${evidence_path}"
done < <(jq -r '.gates[].evidence[] | select(startswith("docs/"))' "$contract")

printf 'stage2.acceptance.mode=%s\n' "$mode"
printf 'stage2.acceptance.status=PASS\n'
