#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

lm_require_command docker
lm_acquire_operation_lock
lm_require_private_file "$LM_ENV_FILE"
lm_require_private_file "$LM_MIGRATION_ENV_FILE"
"${script_dir}/verify-production-secrets.sh" --with-migration
"${script_dir}/verify-release-bundle.sh"
lm_export_application_images

db_name="$(lm_env_value "$LM_ENV_FILE" DB_NAME)"
app_user="$(lm_env_value "$LM_ENV_FILE" DB_USERNAME)"
app_password="$(lm_env_value "$LM_ENV_FILE" DB_PASSWORD)"
migrator_user="$(lm_env_value "$LM_MIGRATION_ENV_FILE" FLYWAY_DB_USERNAME)"
migrator_password="$(lm_env_value "$LM_MIGRATION_ENV_FILE" FLYWAY_DB_PASSWORD)"

[[ "$db_name" =~ ^[A-Za-z0-9_]+$ ]] || lm_die "DB_NAME contains unsupported characters"
[[ "$app_user" == learning_manage_app ]] || lm_die "unexpected application database username"
[[ "$migrator_user" == learning_manage_migrator ]] || lm_die "unexpected migrator database username"

for password in "$app_password" "$migrator_password"; do
    [[ "$password" =~ ^[A-Za-z0-9._~!@%+=:-]{32,128}$ ]] \
        || lm_die "database passwords must be 32-128 URL-safe characters without quotes or backslashes"
done

lm_compose up -d mysql

business_table_count="$(lm_compose exec -T mysql sh -eu -c '
    MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
    export MYSQL_PWD
    mysql -N -B -uroot --protocol=socket --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\"${MYSQL_DATABASE}\";"
')"
[[ "$business_table_count" == 0 ]] || lm_die "database is not empty; initial provisioning refused"

sql="$(printf '%s\n' \
    "CREATE USER IF NOT EXISTS '${migrator_user}'@'%' IDENTIFIED BY '${migrator_password}';" \
    "ALTER USER '${migrator_user}'@'%' IDENTIFIED BY '${migrator_password}';" \
    "REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${migrator_user}'@'%';" \
    "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, CREATE TEMPORARY TABLES, ALTER, DROP, INDEX, REFERENCES ON \`${db_name}\`.* TO '${migrator_user}'@'%';" \
    "CREATE USER IF NOT EXISTS '${app_user}'@'%' IDENTIFIED BY '${app_password}';" \
    "ALTER USER '${app_user}'@'%' IDENTIFIED BY '${app_password}';" \
    "REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${app_user}'@'%';" \
    "GRANT SELECT, INSERT, UPDATE, DELETE ON \`${db_name}\`.* TO '${app_user}'@'%';" \
    "FLUSH PRIVILEGES;")"

lm_compose exec -T mysql sh -eu -c '
    MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
    export MYSQL_PWD
    exec mysql -uroot --protocol=socket
' <<<"$sql"

grants="$(lm_compose exec -T mysql sh -eu -c '
    MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
    export MYSQL_PWD
    mysql -N -B -uroot --protocol=socket --execute="SHOW GRANTS FOR '\''learning_manage_app'\''@'\''%'\''; SHOW GRANTS FOR '\''learning_manage_migrator'\''@'\''%'\'';"
')"

grep -Fq "GRANT SELECT, INSERT, UPDATE, DELETE ON \`${db_name}\`.* TO \`learning_manage_app\`@\`%\`" <<<"$grants" \
    || lm_die "application DML grant verification failed"
grep -Fq "learning_manage_migrator" <<<"$grants" \
    || lm_die "migrator grant verification failed"
grep -Fq "CREATE TEMPORARY TABLES" <<<"$grants" \
    || lm_die "migrator temporary-table grant verification failed"

privilege_summary="$(lm_compose exec -T mysql sh -eu -c '
    MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
    export MYSQL_PWD
    mysql -N -B -uroot --protocol=socket --execute="
        SELECT
          (SELECT COUNT(*) FROM information_schema.user_privileges
             WHERE grantee IN ('\''learning_manage_app'\''@'\''%'\'', '\''learning_manage_migrator'\''@'\''%'\'')
               AND privilege_type <> '\''USAGE'\''),
          ((SELECT COUNT(*) FROM information_schema.user_privileges
              WHERE grantee IN ('\''learning_manage_app'\''@'\''%'\'', '\''learning_manage_migrator'\''@'\''%'\'')
                AND is_grantable = '\''YES'\'')
           + (SELECT COUNT(*) FROM information_schema.schema_privileges
              WHERE grantee IN ('\''learning_manage_app'\''@'\''%'\'', '\''learning_manage_migrator'\''@'\''%'\'')
                AND is_grantable = '\''YES'\''));"
')"
[[ "$privilege_summary" == $'0\t0' ]] || lm_die "global privilege or grant option detected"

lm_log "database accounts provisioned with separated privileges"
