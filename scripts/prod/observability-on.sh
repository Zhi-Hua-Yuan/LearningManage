#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

lm_acquire_operation_lock
lm_require_private_file "$LM_ENV_FILE"
lm_require_private_file "$LM_OBSERVABILITY_ENV_FILE"
grafana_password_file="$(lm_env_value "$LM_OBSERVABILITY_ENV_FILE" GRAFANA_ADMIN_PASSWORD_FILE)"
lm_require_private_file "$grafana_password_file"
"${script_dir}/verify-production-secrets.sh" --with-observability
lm_export_application_images
lm_compose_observability up -d tempo
lm_compose_observability up -d --force-recreate backend
lm_compose_observability up -d prometheus grafana
lm_compose_observability exec -T backend sh -c \
    "wget -q -O - http://127.0.0.1:9123/actuator/prometheus | grep -q learning_"

lm_log "observability enabled; access Grafana only through an SSH tunnel to 127.0.0.1:13000"
