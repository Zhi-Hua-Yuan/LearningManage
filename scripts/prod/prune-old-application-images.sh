#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

[[ $# -eq 2 && "$1" == --previous-release ]] \
    || lm_die "usage: prune-old-application-images.sh --previous-release BACKEND_SHA-FRONTEND_SHA"
[[ "$2" =~ ^[0-9a-f]{40}-[0-9a-f]{40}$ ]] || lm_die "invalid previous release name"

for command in docker jq realpath; do
    lm_require_command "$command"
done
lm_acquire_operation_lock

current_dir="$(realpath -e "$LM_CURRENT_LINK")"
previous_dir="$(realpath -e "${LM_RELEASE_ROOT}/$2")"
root_real="$(realpath -e "$LM_RELEASE_ROOT")"
[[ "$current_dir" == "$root_real"/* && "$previous_dir" == "$root_real"/* ]] \
    || lm_die "release path escaped the fixed release root"

current_backend="$(jq -er '.applicationImages.backend' "${current_dir}/production-release-manifest.json")"
current_frontend="$(jq -er '.applicationImages.frontend' "${current_dir}/production-release-manifest.json")"
previous_backend="$(jq -er '.applicationImages.backend' "${previous_dir}/production-release-manifest.json")"
previous_frontend="$(jq -er '.applicationImages.frontend' "${previous_dir}/production-release-manifest.json")"

while IFS= read -r image; do
    [[ "$image" =~ ^learningmanage-(backend|frontend):[0-9a-f]{40}$ ]] || continue
    case "$image" in
        "$current_backend"|"$current_frontend"|"$previous_backend"|"$previous_frontend") continue ;;
    esac
    docker image rm "$image"
done < <(docker images --format '{{.Repository}}:{{.Tag}}')

lm_log "only current and previous application image tags are retained"
