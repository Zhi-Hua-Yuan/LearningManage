#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

for command in curl docker; do
    lm_require_command "$command"
done
lm_require_private_file "$LM_ENV_FILE"

lm_export_application_images
lm_compose ps
lm_wait_for_url http://127.0.0.1:18080/api/health '"code":0' 30

for path in /api/doc.html /api/v3/api-docs /api/swagger-ui/index.html; do
    status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
        --max-time 5 "http://127.0.0.1:18080${path}")"
    [[ "$status" == 404 ]] || lm_die "API documentation path was not blocked: $path returned $status"
done

lm_compose exec -T backend sh -c \
    "wget -q -O - http://127.0.0.1:9123/actuator/health/liveness | grep -q '\"status\":\"UP\"'"
lm_compose exec -T backend sh -c \
    "wget -q -O - http://127.0.0.1:9123/actuator/health/readiness | grep -q '\"status\":\"UP\"'"
lm_compose exec -T backend sh -c \
    "wget -q -O - http://127.0.0.1:9123/actuator/health/ai | grep -q '\"status\":\"UP\"'"

unhealthy="$(lm_compose ps --format json | grep -E 'unhealthy|exited|dead' || true)"
[[ -z "$unhealthy" ]] || lm_die "unhealthy core container detected"
docker stats --no-stream \
    "$(lm_compose ps -q mysql)" \
    "$(lm_compose ps -q redis)" \
    "$(lm_compose ps -q qdrant)" \
    "$(lm_compose ps -q backend)" \
    "$(lm_compose ps -q frontend)"

lm_log "production smoke checks passed"
