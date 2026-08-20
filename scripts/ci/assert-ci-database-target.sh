#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

ci_assert_ci_target
ci_assert_ci_flyway_identity
ci_emit "gate.target.valid" "true"
ci_emit "gate.database" "${DB_NAME}"
ci_emit "gate.host" "${DB_HOST}"
ci_emit "gate.port" "${DB_PORT}"
