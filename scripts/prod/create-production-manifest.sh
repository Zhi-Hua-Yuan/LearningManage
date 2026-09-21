#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

[[ $# -eq 3 ]] || lm_die "usage: create-production-manifest.sh CANDIDATE_MANIFEST IMAGE_ENV OUTPUT"

candidate_manifest="$(realpath -e "$1")"
image_env="$(realpath -e "$2")"
output="$3"

lm_require_command jq
lm_require_command sha256sum

jq -e '.schemaVersion == 5 and .status == "PASS" and .flyway.emptyDatabase == "PASS"' \
    "$candidate_manifest" >/dev/null || lm_die "candidate manifest is not a passing schema-v4 manifest"

backend_sha="$(jq -er '.backend.sha' "$candidate_manifest")"
frontend_sha="$(jq -er '.frontend.sha' "$candidate_manifest")"
candidate_sha256="$(sha256sum "$candidate_manifest" | awk '{print toupper($1)}')"

[[ "$backend_sha" =~ ^[0-9a-f]{40}$ ]] || lm_die "invalid backend SHA in candidate manifest"
[[ "$frontend_sha" =~ ^[0-9a-f]{40}$ ]] || lm_die "invalid frontend SHA in candidate manifest"

declare -A images
for name in RUNTIME_IMAGE NGINX_IMAGE MYSQL_IMAGE REDIS_IMAGE QDRANT_IMAGE PROMETHEUS_IMAGE TEMPO_IMAGE GRAFANA_IMAGE; do
    images[$name]="$(lm_env_value "$image_env" "$name")"
    [[ "${images[$name]}" =~ @sha256:[0-9a-f]{64}$ ]] \
        || lm_die "$name is not pinned to a lowercase sha256 digest"
done

migrations_json='{}'
for version in {1..9}; do
    migration="$(find "${LM_RELEASE_DIR}/src/main/resources/db/migration" -maxdepth 1 -type f -name "V${version}__*.sql" -print)"
    [[ -n "$migration" && "$(wc -l <<<"$migration")" -eq 1 ]] \
        || lm_die "expected exactly one V${version} migration"
    migration_hash="$(sha256sum "$migration" | awk '{print toupper($1)}')"
    migrations_json="$(jq -c --arg key "V${version}" --arg value "$migration_hash" \
        '. + {($key): $value}' <<<"$migrations_json")"
done

mkdir -p "$(dirname -- "$output")"
jq -n \
    --arg backendSha "$backend_sha" \
    --arg frontendSha "$frontend_sha" \
    --arg candidateManifestSha256 "$candidate_sha256" \
    --arg runtimeImage "${images[RUNTIME_IMAGE]}" \
    --arg nginxImage "${images[NGINX_IMAGE]}" \
    --arg mysqlImage "${images[MYSQL_IMAGE]}" \
    --arg redisImage "${images[REDIS_IMAGE]}" \
    --arg qdrantImage "${images[QDRANT_IMAGE]}" \
    --arg prometheusImage "${images[PROMETHEUS_IMAGE]}" \
    --arg tempoImage "${images[TEMPO_IMAGE]}" \
    --arg grafanaImage "${images[GRAFANA_IMAGE]}" \
    --argjson migrations "$migrations_json" \
    '{
        schemaVersion: 1,
        status: "PASS",
        backendSha: $backendSha,
        frontendSha: $frontendSha,
        candidateManifestSha256: $candidateManifestSha256,
        applicationImages: {
            backend: ("learningmanage-backend:" + $backendSha),
            frontend: ("learningmanage-frontend:" + $frontendSha)
        },
        externalImages: {
            runtime: $runtimeImage,
            nginx: $nginxImage,
            mysql: $mysqlImage,
            redis: $redisImage,
            qdrant: $qdrantImage,
            prometheus: $prometheusImage,
            tempo: $tempoImage,
            grafana: $grafanaImage
        },
        migrations: $migrations
    }' > "$output"

(cd "$(dirname -- "$output")" \
    && sha256sum "$(basename -- "$output")" > "$(basename -- "$output").sha256")
lm_log "created production manifest: $output"
