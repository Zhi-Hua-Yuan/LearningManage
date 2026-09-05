#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/ci-common.sh"

wp8_report="${project_root}/docs/stage2/evidence/wp8/real-provider-validation.json"
verification="${project_root}/docs/stage2/evidence/wp7/local-verification.json"
if [[ -s "$wp8_report" && -s "${wp8_report}.sha256" ]]; then
  report="$wp8_report"
  sidecar="${report}.sha256"
  provider_sha="$(jq -er '.backendSha' "$report")"
  run_id="$(jq -er '.workflowRunId' "$report")"
else
  report="${project_root}/docs/stage2/evidence/wp7/real-provider-validation.json"
  sidecar="${report}.sha256"
  provider_sha="$(jq -er '.verification.realProvider.backendSha' "$verification")"
  run_id="$(jq -er '.verification.realProvider.workflowRunId' "$verification")"
fi
candidate_sha="${STAGE2_BACKEND_SHA:-$(git -C "$project_root" rev-parse HEAD)}"

[[ "$candidate_sha" =~ ^[0-9a-f]{40}$ ]] || ci_fail "stage2_candidate_sha_invalid"
[[ -s "$report" && -s "$sidecar" ]] || ci_fail "stage2_wp7_provider_evidence_missing"
report_relative="${report#"$project_root/"}"
expected_report_sha="$(awk 'NR == 1 {print toupper($1)}' "$sidecar")"
canonical_report_sha="$(git -C "$project_root" show "${candidate_sha}:${report_relative}" | sha256sum | awk '{print toupper($1)}')"
[[ "$canonical_report_sha" == "$expected_report_sha" ]] \
  || ci_fail "stage2_wp7_provider_checksum_invalid"

[[ "$provider_sha" =~ ^[0-9a-f]{40}$ ]] || ci_fail "stage2_wp7_provider_sha_invalid"
git -C "$project_root" merge-base --is-ancestor "$provider_sha" "$candidate_sha" \
  || ci_fail "stage2_wp7_provider_sha_not_ancestor"
bash "${script_dir}/verify-stage2-wp7-real-provider-report.sh" "$report" "$provider_sha" "$run_id" >/dev/null

changed="$(mktemp)"
unexpected="$(mktemp)"
trap 'rm -f "$changed" "$unexpected"' EXIT
git -C "$project_root" diff --name-only "${provider_sha}..${candidate_sha}" > "$changed"
while IFS= read -r path; do
  [[ -n "$path" ]] || continue
  case "$path" in
    docs/stage2/*|.github/workflows/stage2-release-gate.yml|scripts/ci/README.md|scripts/ci/create-stage2-*|scripts/ci/verify-stage2-* ) ;;
    *) printf '%s\n' "$path" >> "$unexpected" ;;
  esac
done < "$changed"

if [[ -s "$unexpected" ]]; then
  printf 'Files requiring a new real-provider run:\n' >&2
  cat "$unexpected" >&2
  ci_fail "stage2_wp7_provider_revalidation_required"
fi

printf 'stage2.provider.binding=PASS\n'
printf 'stage2.provider.backend_sha=%s\n' "$provider_sha"
printf 'stage2.provider.changed_files=%s\n' "$(wc -l < "$changed" | tr -d '[:space:]')"
