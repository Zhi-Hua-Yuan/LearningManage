#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

lm_acquire_operation_lock
lm_require_private_file "$LM_ENV_FILE"
lm_require_private_file "$LM_OBSERVABILITY_ENV_FILE"
lm_export_application_images
lm_compose_observability stop prometheus tempo grafana
lm_compose up -d --force-recreate backend
lm_compose exec -T backend sh -c \
    "wget -q -O - http://127.0.0.1:9123/actuator/health/readiness | grep -q '\"status\":\"UP\"'"

lm_log "observability stopped and backend recreated with tracing disabled"
