#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

lm_require_command docker
lm_acquire_operation_lock
"${script_dir}/verify-release-bundle.sh"

manifest="${LM_RELEASE_DIR}/production-release-manifest.json"
lm_export_application_images "$manifest"

verify_external_image() {
    local image_ref="$1"
    [[ "$image_ref" =~ @sha256:[0-9a-f]{64}$ ]] || lm_die "image is not digest-pinned: $image_ref"
    docker pull "$image_ref"
    docker image inspect "$image_ref" --format '{{json .RepoDigests}}' \
        | grep -Fq "${image_ref#*@}" || lm_die "RepoDigests verification failed for $image_ref"
}

for key in runtime nginx mysql redis qdrant prometheus tempo grafana; do
    verify_external_image "$(jq -er ".externalImages.${key}" "$manifest")"
done

runtime_image="$(jq -er '.externalImages.runtime' "$manifest")"
nginx_image="$(jq -er '.externalImages.nginx' "$manifest")"
backend_sha="$(jq -er '.backendSha' "$manifest")"
frontend_sha="$(jq -er '.frontendSha' "$manifest")"

docker build \
    --build-arg "RUNTIME_IMAGE=${runtime_image}" \
    --build-arg "BACKEND_SHA=${backend_sha}" \
    --file "${LM_RELEASE_DIR}/deploy/Dockerfile.backend.prod" \
    --tag "$BACKEND_IMAGE" \
    "$LM_RELEASE_DIR"

docker build \
    --build-arg "NGINX_IMAGE=${nginx_image}" \
    --build-arg "FRONTEND_SHA=${frontend_sha}" \
    --file "${LM_RELEASE_DIR}/deploy/Dockerfile.frontend.prod" \
    --tag "$FRONTEND_IMAGE" \
    "$LM_RELEASE_DIR"

docker image inspect "$BACKEND_IMAGE" >/dev/null
docker image inspect "$FRONTEND_IMAGE" >/dev/null
[[ "$(docker image inspect "$BACKEND_IMAGE" --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}')" == "$backend_sha" ]] \
    || lm_die "backend image revision label mismatch"
[[ "$(docker image inspect "$FRONTEND_IMAGE" --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}')" == "$frontend_sha" ]] \
    || lm_die "frontend image revision label mismatch"
lm_log "application images built and verified"
