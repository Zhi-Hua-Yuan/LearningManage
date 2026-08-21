#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

ci_assert_ci_target
ci_assert_ci_flyway_identity
ci_assert_ci_app_identity
ci_require_command "curl"
ci_require_command "docker"
ci_require_command "grep"
ci_require_env "CI_COMPOSE_FILE"
ci_require_env "CI_COMPOSE_PROJECT_NAME"
ci_require_env "CI_HEALTH_URL"
ci_require_env "CI_EXPECTED_HISTORY_TOTAL"

compose=(docker compose --project-name "${CI_COMPOSE_PROJECT_NAME}" --file "${CI_COMPOSE_FILE}")
backend_container="$("${compose[@]} ps -q backend)"
[[ -n "$backend_container" ]] || ci_fail "docker_backend_container_missing"

container_user="$(docker inspect --format '{{.Config.User}}' "$backend_container")"
[[ -n "$container_user" ]] || ci_fail "docker_backend_user_missing"
[[ "$container_user" != "root" && "$container_user" != "0" ]] \
    || ci_fail "docker_backend_running_as_root"

deadline=$((SECONDS + 120))
health_body=""
while (( SECONDS < deadline )); do
    if health_body="$(curl --fail --silent --show-error --max-time 5 "${CI_HEALTH_URL}" 2>/dev/null)"; then
        if grep -Eq '"code"[[:space:]]*:[[:space:]]*0' <<<"$health_body"; then
            break
        fi
    fi
    sleep 2
done

[[ -n "$health_body" ]] || ci_fail "docker_health_timeout"
grep -Eq '"code"[[:space:]]*:[[:space:]]*0' <<<"$health_body" \
    || ci_fail "docker_health_response_invalid"

history_total="$(ci_mysql_migrator --database="${DB_NAME}" \
    --execute='SELECT COUNT(*) FROM flyway_schema_history;')"
ci_assert_equals "${CI_EXPECTED_HISTORY_TOTAL}" "$history_total" \
    "docker_flyway_history_changed"
ci_assert_application_ddl_denied

ci_emit "docker.runtime.verify.success" "true"
ci_emit "docker.runtime.container_user" "$container_user"
ci_emit "docker.runtime.history_rows" "$history_total"
