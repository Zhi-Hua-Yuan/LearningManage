#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/ci-common.sh"
ci_require_command jq

contract="${STAGE1_ACCEPTANCE_CONTRACT:-${project_root}/docs/stage1/acceptance/stage1-acceptance-contract.json}"
mode="${STAGE1_ACCEPTANCE_MODE:-pre-release}"
[[ -s "$contract" ]] || ci_fail "stage1_acceptance_contract_missing"

case "$mode" in
  pre-release)
    jq -e '
      .schemaVersion == 1 and .stage == "stage1" and .status == "FROZEN" and
      ([.gates[] | select(.id | IN("S1-A-001","S1-A-002","S1-A-003","S1-A-004","S1-A-005","S1-A-006","S1-A-007","S1-A-008")) | .status] | all(. == "PASS")) and
      ([.gates[] | select(.id | IN("S1-A-009","S1-A-010","S1-A-011","S1-A-012")) | .status] | all(. == "PENDING"))
    ' "$contract" >/dev/null || ci_fail "stage1_pre_release_contract_invalid"
    ;;
  sealed)
    jq -e '
      .schemaVersion == 1 and .stage == "stage1" and .status == "PASS" and
      ([.gates[].status] | all(. == "PASS"))
    ' "$contract" >/dev/null || ci_fail "stage1_sealed_contract_invalid"
    ;;
  *) ci_fail "invalid_stage1_acceptance_mode" ;;
esac

jq -e '(([.gates[].id] | length == 12) and ([.gates[].id] | unique | length == 12))' "$contract" >/dev/null \
  || ci_fail "stage1_acceptance_gate_set_invalid"
printf 'stage1.acceptance.mode=%s\n' "$mode"
printf 'stage1.acceptance.status=PASS\n'
