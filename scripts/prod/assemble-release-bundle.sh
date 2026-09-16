#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

[[ $# -eq 6 ]] || lm_die "usage: assemble-release-bundle.sh BACKEND_ARTIFACT_DIR FRONTEND_ARTIFACT_DIR CANDIDATE_MANIFEST EVIDENCE_DIR PRODUCTION_MANIFEST OUTPUT_ROOT"

backend_artifacts="$(realpath -e "$1")"
frontend_artifacts="$(realpath -e "$2")"
candidate_manifest="$(realpath -e "$3")"
[[ "$(basename -- "$candidate_manifest")" == release-candidate-manifest.json ]] \
    || lm_die "candidate manifest must retain its Gate filename"
candidate_sidecar="$(realpath -e "${candidate_manifest}.sha256")"
evidence_dir="$(realpath -e "$4")"
production_manifest="$(realpath -e "$5")"
[[ "$(basename -- "$production_manifest")" == production-release-manifest.json ]] \
    || lm_die "production manifest must retain its Gate filename"
production_sidecar="$(realpath -e "${production_manifest}.sha256")"
output_root="$(realpath -m "$6")"

for command in git jq sha256sum find sort tar xargs; do
    lm_require_command "$command"
done

backend_sha="$(jq -er '.backend.sha' "$candidate_manifest")"
frontend_sha="$(jq -er '.frontend.sha' "$candidate_manifest")"
[[ "$(git -C "$LM_RELEASE_DIR" rev-parse HEAD)" == "$backend_sha" ]] \
    || lm_die "checked-out backend commit does not match candidate manifest"
git -C "$LM_RELEASE_DIR" diff --quiet -- || lm_die "tracked backend worktree changes are not allowed"
git -C "$LM_RELEASE_DIR" diff --cached --quiet -- || lm_die "staged backend changes are not allowed"
production_status="$(git -C "$LM_RELEASE_DIR" status --porcelain --untracked-files=all -- \
    deploy scripts/prod src/main/resources/db/migration)"
[[ -z "$production_status" ]] || lm_die "production asset paths contain uncommitted files"

bundle="${output_root}/${backend_sha}-${frontend_sha}"
[[ ! -e "$bundle" ]] || lm_die "bundle target already exists: $bundle"

(cd "$backend_artifacts" && sha256sum --check backend.jar.sha256)
(cd "$frontend_artifacts" && sha256sum --check dist.sha256)
(cd "$(dirname -- "$candidate_manifest")" && sha256sum --check "$(basename -- "$candidate_sidecar")")
(cd "$(dirname -- "$production_manifest")" && sha256sum --check "$(basename -- "$production_sidecar")")

candidate_hash="$(sha256sum "$candidate_manifest" | awk '{print toupper($1)}')"
[[ "$candidate_hash" == "$(jq -er '.candidateManifestSha256' "$production_manifest")" ]] \
    || lm_die "production manifest is not bound to the supplied candidate manifest"
[[ "$backend_sha" == "$(jq -er '.backendSha' "$production_manifest")" ]] \
    || lm_die "production manifest backend SHA mismatch"
[[ "$frontend_sha" == "$(jq -er '.frontendSha' "$production_manifest")" ]] \
    || lm_die "production manifest frontend SHA mismatch"

backend_scan="${backend_artifacts}/backend-artifact-scan.txt"
frontend_scan="${frontend_artifacts}/frontend-artifact-scan.txt"
lm_require_file "$backend_scan"
lm_require_file "$frontend_scan"
[[ "$(sha256sum "$backend_scan" | awk '{print toupper($1)}')" \
    == "$(jq -er '.artifactScanning.backendReportSha256' "$candidate_manifest")" ]] \
    || lm_die "backend artifact scan hash mismatch"
[[ "$(sha256sum "$frontend_scan" | awk '{print toupper($1)}')" \
    == "$(jq -er '.artifactScanning.frontendReportSha256' "$candidate_manifest")" ]] \
    || lm_die "frontend artifact scan hash mismatch"

mkdir -p "$bundle/backend" "$bundle/frontend" "$bundle/evidence" "$bundle/migrations"
cp "$backend_artifacts/LearningManage-0.0.1-SNAPSHOT.jar" "$bundle/backend/app.jar"
(cd "$bundle/backend" && sha256sum app.jar > app.jar.sha256)
cp -a "$frontend_artifacts/dist" "$bundle/frontend/"
cp "$frontend_artifacts/dist.sha256" "$bundle/frontend/dist.sha256"
cp "$candidate_manifest" "$bundle/release-candidate-manifest.json"
cp "$candidate_sidecar" "$bundle/release-candidate-manifest.json.sha256"
cp "$production_manifest" "$bundle/production-release-manifest.json"
cp "$production_sidecar" "$bundle/production-release-manifest.json.sha256"
mkdir -p "$bundle/evidence/artifact-scans"
cp "$backend_scan" "$bundle/evidence/artifact-scans/"
cp "$frontend_scan" "$bundle/evidence/artifact-scans/"
cp -a "$evidence_dir"/. "$bundle/evidence/"
find "$bundle/evidence" -type f -print -quit | grep -q . || lm_die "release evidence directory is empty"

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
    done < <(find "$bundle/evidence" -type f -print0)
    [[ "$matched" == true ]] || lm_die "release evidence is missing hash $expected_hash"
done

release_assets=(
    deploy/Dockerfile.backend.prod
    deploy/Dockerfile.backend.prod.dockerignore
    deploy/Dockerfile.frontend.prod
    deploy/Dockerfile.frontend.prod.dockerignore
    deploy/BUGFIX_RELEASE_GUIDE.md
    deploy/README.prod.md
    deploy/apt
    deploy/backup.env.example
    deploy/docker-compose.prod.yml
    deploy/learning.env.example
    deploy/migration.env.example
    deploy/mysql/my.cnf
    deploy/nginx.host.conf
    deploy/nginx.host.http.conf
    deploy/nginx.prod.conf
    deploy/observability.env.example
    deploy/observability/alerts.yml
    deploy/observability/grafana
    deploy/observability/prometheus.yml
    deploy/observability/tempo.prod.yml
    deploy/production-release-manifest.schema.json
    deploy/redis/redis-entrypoint-prod.sh
    deploy/release-images.env.example
    deploy/sshd
    deploy/sudoers
    deploy/systemd
    scripts/prod
)
git -C "$LM_RELEASE_DIR" archive --format=tar "$backend_sha" -- "${release_assets[@]}" \
    | tar -x -C "$bundle"

for version in {1..8}; do
    migration_path="$(git -C "$LM_RELEASE_DIR" ls-tree -r --name-only "$backend_sha" -- \
        src/main/resources/db/migration \
        | grep -E "/V${version}__[^/]+[.]sql$")"
    [[ -n "$migration_path" && "$(wc -l <<<"$migration_path")" -eq 1 ]] \
        || lm_die "expected exactly one committed V${version} migration"
    git -C "$LM_RELEASE_DIR" show "${backend_sha}:${migration_path}" \
        > "$bundle/migrations/$(basename -- "$migration_path")"
done

(
    cd "$bundle"
    find . -type f ! -name SHA256SUMS -print0 \
        | sort -z \
        | xargs -0 sha256sum > SHA256SUMS
    sha256sum --check SHA256SUMS >/dev/null
)

bash "$bundle/scripts/prod/verify-release-bundle.sh"

lm_log "assembled verified release bundle: $bundle"
