#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib/ci-common.sh"
ci_assert_ci_target
ci_require_command mysql
ci_require_env STAGE3_DB_READER_USERNAME
ci_require_env STAGE3_DB_READER_PASSWORD
[[ "$DB_NAME" == *_eval ]] || ci_fail "stage3_reader_database_suffix_invalid"
[[ "$STAGE3_DB_READER_USERNAME" == "learning_manage_stage3_reader" ]] || ci_fail "stage3_reader_username_invalid"
ci_validate_password_for_sql "$STAGE3_DB_READER_PASSWORD"

ci_mysql_admin <<SQL
CREATE USER IF NOT EXISTS '${STAGE3_DB_READER_USERNAME}'@'%' IDENTIFIED BY '${STAGE3_DB_READER_PASSWORD}';
GRANT SELECT ON \`${DB_NAME}\`.* TO '${STAGE3_DB_READER_USERNAME}'@'%';
SQL

MYSQL_PWD="$STAGE3_DB_READER_PASSWORD" mysql --protocol=TCP --host="$DB_HOST" --port="$DB_PORT" \
  --user="$STAGE3_DB_READER_USERNAME" --database="$DB_NAME" --batch --skip-column-names --execute='SELECT 1;' >/dev/null \
  || ci_fail "stage3_reader_connection_failed"
printf 'stage3.reader.status=PASS\n'
