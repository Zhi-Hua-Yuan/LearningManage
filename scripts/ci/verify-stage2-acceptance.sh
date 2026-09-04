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
      (.gates[] | select(.id == "S2-A-009") | .status) as $wp5_status |
      (.risks[] | select(.id == "S2-R-004") | .status) as $wp5_risk_status |
      .schemaVersion == 1 and .stage == "stage2" and .status == "FROZEN" and
      .baseline.stage1Tag == "stage1-v1.0.0" and
      .baseline.backendSha == "505715860cce7f04a52c00a4e4258ac8ed838b8d" and
      .baseline.frontendSha == "2ef907f292fbbacecf8a68f7d24c4701a555aa8a" and
      ([.gates[] | select(.id | IN("S2-A-001","S2-A-002","S2-A-003","S2-A-004")) | .status] | all(. == "PASS")) and
      ($wp5_status == "PENDING" or $wp5_status == "PASS") and
      ([.gates[] | select(.id | IN("S2-A-010","S2-A-011","S2-A-012")) | .status] | all(. == "PENDING")) and
      ([.gates[] | select(.id == "S2-A-008") | .status] | all(. == "PENDING" or . == "PASS")) and
      ([.gates[] | select(.id == "S2-A-005") | .status] | all(. == "PENDING" or . == "PASS")) and
      ([.gates[] | select(.id == "S2-A-006") | .status] | all(. == "PENDING" or . == "PASS")) and
      ([.gates[] | select(.id == "S2-A-007") | .status] | all(. == "PENDING" or . == "PASS")) and
      (.risks[] | select(.id == "S2-R-001") | .status) == "CLOSED" and
      (($wp5_status == "PENDING" and $wp5_risk_status == "OPEN") or
       ($wp5_status == "PASS" and $wp5_risk_status == "CLOSED")) and
      .policy.publishedMigrationHead == "3" and
      .policy.v1Modified == false and .policy.v2Modified == false and .policy.v3Modified == false and
      .policy.ragOrAgentImplemented == false and
      .summary.fail == 0
    ' "$contract" >/dev/null || ci_fail "stage2_frozen_contract_invalid"
    ;;
  sealed)
    jq -e '
      .schemaVersion == 1 and .stage == "stage2" and .status == "PASS" and
      ([.gates[].status] | all(. == "PASS")) and
      .policy.publishedMigrationHead == "3" and
      .policy.v1Modified == false and .policy.v2Modified == false and .policy.v3Modified == false and
      .policy.ragOrAgentImplemented == false and .summary.fail == 0 and .summary.openRisk == 0
    ' "$contract" >/dev/null || ci_fail "stage2_sealed_contract_invalid"
    ;;
  *) ci_fail "invalid_stage2_acceptance_mode" ;;
esac

jq -e '([.gates[].id] | length == 12) and ([.gates[].id] | unique | length == 12)' "$contract" >/dev/null \
  || ci_fail "stage2_acceptance_gate_set_invalid"
jq -e '((.risks | map(.id) | unique | length) == (.risks | length))' "$contract" >/dev/null \
  || ci_fail "stage2_acceptance_risk_set_invalid"
jq -e '([.gates[].evidence[]] | all(test("^(docs/|[A-Za-z0-9 _-]+)")))' "$contract" >/dev/null \
  || ci_fail "stage2_acceptance_evidence_invalid"
jq -e '
  ([.gates[] | select(.status == "PASS")] | length) == .summary.pass and
  ([.gates[] | select(.status == "PENDING")] | length) == .summary.pending and
  ([.gates[] | select(.status == "FAIL")] | length) == .summary.fail
' "$contract" >/dev/null || ci_fail "stage2_acceptance_summary_invalid"
jq -e '([.risks[] | select(.status == "OPEN")] | length) == .summary.openRisk' "$contract" >/dev/null \
  || ci_fail "stage2_acceptance_open_risk_summary_invalid"

while IFS= read -r evidence_path; do
  [[ -z "$evidence_path" ]] && continue
  [[ -s "$project_root/$evidence_path" ]] || ci_fail "stage2_evidence_missing:${evidence_path}"
done < <(jq -r '.gates[].evidence[] | select(startswith("docs/"))' "$contract")

wp2_gate_status="$(jq -r '.gates[] | select(.id == "S2-A-006") | .status' "$contract")"
if [[ "$wp2_gate_status" == "PASS" ]]; then
  wp2_verification="${project_root}/docs/stage2/evidence/wp2/local-verification.json"
  [[ -s "$wp2_verification" ]] || ci_fail "stage2_wp2_verification_missing"
  jq -e '.verification.fullMySqlCiStatus == "PASS" and .gate.status == "PASS"' "$wp2_verification" >/dev/null \
    || ci_fail "stage2_wp2_pass_without_full_ci"
fi

wp4_gate_status="$(jq -r '.gates[] | select(.id == "S2-A-008") | .status' "$contract")"
if [[ "$wp4_gate_status" == "PASS" ]]; then
  wp4_verification="${project_root}/docs/stage2/evidence/wp4/local-verification.json"
  [[ -s "$wp4_verification" ]] || ci_fail "stage2_wp4_verification_missing"
  jq -e '
    .verification.facade.status == "PASS" and
    .verification.sceneServices.status == "PASS" and
    .verification.architectureGate.status == "PASS" and
    .verification.fullRegression.fullVerify.status == "PASS" and
    .verification.frontend.productionBuild == "PASS" and
    .verification.candidateCi.status == "PASS" and
    .verification.dockerStub.status == "PASS" and
    .verification.runtimeApiComparison.status == "PASS" and
    .gate.status == "PASS"
  ' "$wp4_verification" >/dev/null || ci_fail "stage2_wp4_pass_without_candidate_ci"
fi

wp5_gate_status="$(jq -r '.gates[] | select(.id == "S2-A-009") | .status' "$contract")"
if [[ "$wp5_gate_status" == "PASS" ]]; then
  wp5_verification="${project_root}/docs/stage2/evidence/wp5/local-verification.json"
  [[ -s "$wp5_verification" ]] || ci_fail "stage2_wp5_verification_missing"
  jq -e '
    .verification.confirmationKernel.status == "PASS" and
    .verification.handlerRegistry.status == "PASS" and
    .verification.writePath.status == "PASS" and
    .verification.concurrentMySql.status == "PASS" and
    .verification.publishedMigrations.status == "PASS" and
    .verification.candidateCi.status == "PASS" and
    .verification.dockerStub.status == "PASS" and
    .verification.runtimeApiComparison.status == "PASS" and
    .gate.status == "PASS"
  ' "$wp5_verification" >/dev/null || ci_fail "stage2_wp5_pass_without_candidate_ci"
fi

wp3_gate_status="$(jq -r '.gates[] | select(.id == "S2-A-007") | .status' "$contract")"
if [[ "$wp3_gate_status" == "PASS" ]]; then
  wp3_verification="${project_root}/docs/stage2/evidence/wp3/local-verification.json"
  [[ -s "$wp3_verification" ]] || ci_fail "stage2_wp3_verification_missing"
  jq -e '
    .verification.coreTests.status == "PASS" and
    .verification.architectureGate.status == "PASS" and
    .verification.sourceBoundaryGate.status == "PASS" and
    .verification.wp3MySqlTests.status == "PASS" and
    .verification.fullMySqlCiStatus == "PASS" and
    .gate.status == "PASS"
  ' "$wp3_verification" >/dev/null || ci_fail "stage2_wp3_pass_without_full_ci"
fi

printf 'stage2.acceptance.mode=%s\n' "$mode"
printf 'stage2.acceptance.status=PASS\n'
