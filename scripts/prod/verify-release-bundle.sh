#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

for command in jq sha256sum; do
    lm_require_command "$command"
done

lm_require_file "${LM_RELEASE_DIR}/SHA256SUMS"
lm_require_file "${LM_RELEASE_DIR}/release-candidate-manifest.json"
lm_require_file "${LM_RELEASE_DIR}/release-candidate-manifest.json.sha256"
lm_require_file "${LM_RELEASE_DIR}/production-release-manifest.json"

(cd "$LM_RELEASE_DIR" && sha256sum --check SHA256SUMS)
(cd "$LM_RELEASE_DIR" && sha256sum --check release-candidate-manifest.json.sha256)
(cd "$LM_RELEASE_DIR" && sha256sum --check production-release-manifest.json.sha256)

production_manifest="${LM_RELEASE_DIR}/production-release-manifest.json"
candidate_manifest="${LM_RELEASE_DIR}/release-candidate-manifest.json"
jq -e '
    .schemaVersion == 1 and .status == "PASS" and
    (.backendSha | test("^[0-9a-f]{40}$")) and
    (.frontendSha | test("^[0-9a-f]{40}$")) and
    (.candidateManifestSha256 | test("^[0-9A-F]{64}$")) and
    ((.externalImages | keys) == ["grafana", "mysql", "nginx", "prometheus", "qdrant", "redis", "runtime", "tempo"]) and
    (all(.externalImages[]; test("@sha256:[0-9a-f]{64}$"))) and
    ((.migrations | keys) == ["V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9"]) and
    (all(.migrations[]; test("^[0-9A-F]{64}$")))
' "$production_manifest" >/dev/null \
    || lm_die "production manifest did not pass schema identity checks"
jq -e '.schemaVersion == 5 and .status == "PASS"' "$candidate_manifest" >/dev/null \
    || lm_die "candidate manifest did not pass schema identity checks"

candidate_hash="$(sha256sum "$candidate_manifest" | awk '{print toupper($1)}')"
[[ "$candidate_hash" == "$(jq -er '.candidateManifestSha256' "$production_manifest")" ]] \
    || lm_die "candidate manifest hash does not match production manifest"

backend_sha="$(jq -er '.backendSha' "$production_manifest")"
frontend_sha="$(jq -er '.frontendSha' "$production_manifest")"
[[ "$(basename -- "$LM_RELEASE_DIR")" == "${backend_sha}-${frontend_sha}" ]] \
    || lm_die "release directory name does not match the manifest SHAs"
[[ "$backend_sha" == "$(jq -er '.backend.sha' "$candidate_manifest")" ]] \
    || lm_die "backend SHA mismatch"
[[ "$frontend_sha" == "$(jq -er '.frontend.sha' "$candidate_manifest")" ]] \
    || lm_die "frontend SHA mismatch"

jar_hash="$(sha256sum "${LM_RELEASE_DIR}/backend/app.jar" | awk '{print toupper($1)}')"
(cd "${LM_RELEASE_DIR}/backend" && sha256sum --check app.jar.sha256)
[[ "$jar_hash" == "$(jq -er '.backend.jarSha256' "$candidate_manifest")" ]] \
    || lm_die "backend JAR hash does not match the candidate manifest"
(cd "${LM_RELEASE_DIR}/frontend" && sha256sum --check dist.sha256)
dist_manifest_hash="$(sha256sum "${LM_RELEASE_DIR}/frontend/dist.sha256" | awk '{print toupper($1)}')"
[[ "$dist_manifest_hash" == "$(jq -er '.frontend.distManifestSha256' "$candidate_manifest")" ]] \
    || lm_die "frontend dist manifest hash does not match the candidate manifest"

for side in backend frontend; do
    scan_file="${LM_RELEASE_DIR}/evidence/artifact-scans/${side}-artifact-scan.txt"
    lm_require_file "$scan_file"
    scan_hash="$(sha256sum "$scan_file" | awk '{print toupper($1)}')"
    manifest_field="backendReportSha256"
    [[ "$side" == frontend ]] && manifest_field="frontendReportSha256"
    [[ "$scan_hash" == "$(jq -er ".artifactScanning.${manifest_field}" "$candidate_manifest")" ]] \
        || lm_die "$side artifact scan hash does not match the candidate manifest"
done

for expression in \
    '.interfaceContract.frontendContractSha256' \
    '.interfaceContract.runtimeDocumentSha256' \
    '.interfaceContract.comparisonReportSha256' \
    '.fullStackRuntime.evidenceSha256'; do
    expected_hash="$(jq -er "$expression" "$candidate_manifest")"
    matched=false
    while IFS= read -r -d '' evidence_file; do
        actual_hash="$(sha256sum "$evidence_file" | awk '{print toupper($1)}')"
        if [[ "$actual_hash" == "$expected_hash" ]]; then
            matched=true
            break
        fi
    done < <(find "${LM_RELEASE_DIR}/evidence" -type f -print0)
    [[ "$matched" == true ]] || lm_die "release evidence is missing hash $expected_hash"
done

for version in {1..9}; do
    migration="$(find "${LM_RELEASE_DIR}/migrations" -maxdepth 1 -type f -name "V${version}__*.sql" -print)"
    [[ -n "$migration" && "$(wc -l <<<"$migration")" -eq 1 ]] \
        || lm_die "expected exactly one bundled V${version} migration"
    actual="$(sha256sum "$migration" | awk '{print toupper($1)}')"
    expected="$(jq -er ".migrations.V${version}" "$production_manifest")"
    [[ "$actual" == "$expected" ]] || lm_die "V${version} migration checksum mismatch"
done

lm_log "release bundle verification passed"
