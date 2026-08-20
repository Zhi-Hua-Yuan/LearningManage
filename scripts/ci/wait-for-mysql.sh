#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

ci_assert_ci_target
ci_require_command "mysql"
ci_require_command "mysqladmin"
ci_require_env "CI_MYSQL_ADMIN_USERNAME"
ci_require_env "CI_MYSQL_ADMIN_PASSWORD"

timeout_seconds="${CI_MYSQL_WAIT_TIMEOUT_SECONDS:-60}"
[[ "$timeout_seconds" =~ ^[1-9][0-9]{0,2}$ ]] || ci_fail "mysql_wait_timeout_invalid"

deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
    if MYSQL_PWD="${CI_MYSQL_ADMIN_PASSWORD}" mysqladmin \
        --protocol=TCP \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --user="${CI_MYSQL_ADMIN_USERNAME}" \
        ping --silent >/dev/null 2>&1; then
        version="$(ci_mysql_admin --execute='SELECT VERSION();')"
        ci_emit "mysql.ready" "true"
        ci_emit "mysql.version" "$version"
        exit 0
    fi
    sleep 2
done

ci_fail "mysql_wait_timeout"
