#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

[[ $# -eq 2 ]] || lm_die "usage: restore-drill.sh ENCRYPTED_BACKUP AGE_IDENTITY_FILE"
backup_file="$(realpath -e "$1")"
identity_file="$(realpath -e "$2")"
[[ "$backup_file" == *.sql.zst.age ]] || lm_die "unexpected backup filename"
lm_require_file "${backup_file}.sha256"
metadata_file="${backup_file}.metadata"
lm_require_file "$metadata_file"

for command in age docker jq openssl sha256sum zstd; do
    lm_require_command "$command"
done
lm_acquire_operation_lock
(cd "$(dirname -- "$backup_file")" && sha256sum --check "$(basename -- "$backup_file").sha256")
"${script_dir}/verify-release-bundle.sh"

manifest="${LM_RELEASE_DIR}/production-release-manifest.json"
mysql_image="$(jq -er '.externalImages.mysql' "$manifest")"
suffix="$(date -u +'%Y%m%d%H%M%S')-$$"
container="learning-manage-restore-${suffix}"
volume="learning-manage-restore-${suffix}"
root_password="$(openssl rand -hex 24)"

cleanup() {
    docker rm -f "$container" >/dev/null 2>&1 || true
    docker volume rm "$volume" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker volume create "$volume" >/dev/null
docker run -d \
    --name "$container" \
    --network none \
    --memory 640m \
    --pids-limit 160 \
    --env "MYSQL_ROOT_PASSWORD=$root_password" \
    --volume "${volume}:/var/lib/mysql" \
    "$mysql_image" >/dev/null

for _ in {1..60}; do
    if docker exec -e "MYSQL_PWD=$root_password" "$container" \
            mysqladmin -uroot --protocol=socket ping --silent >/dev/null 2>&1; then
        break
    fi
    sleep 2
done
docker exec -e "MYSQL_PWD=$root_password" "$container" \
    mysqladmin -uroot --protocol=socket ping --silent >/dev/null \
    || lm_die "isolated MySQL did not become ready"

age --decrypt --identity "$identity_file" "$backup_file" \
    | zstd --decompress --stdout \
    | docker exec -i -e "MYSQL_PWD=$root_password" "$container" mysql -uroot --protocol=socket

docker exec -e "MYSQL_PWD=$root_password" "$container" \
    mysqlcheck -uroot --protocol=socket learning_manage >/dev/null

summary="$(docker exec -e "MYSQL_PWD=$root_password" "$container" \
    mysql -N -B -uroot --protocol=socket --execute="
      SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema = 'learning_manage' AND table_type = 'BASE TABLE';
      SELECT COUNT(*) FROM learning_manage.flyway_schema_history WHERE success = 1;
      SELECT COUNT(*) FROM learning_manage.\`user\`;
      SELECT COUNT(*) FROM learning_manage.project;
      SELECT COUNT(*) FROM learning_manage.task;")"
[[ "$(sed -n '1p' <<<"$summary")" -gt 0 ]] || lm_die "restored database has no business tables"
[[ "$(sed -n '2p' <<<"$summary")" == 8 ]] || lm_die "restored Flyway history is incomplete"
expected_user_count="$(lm_env_value "$metadata_file" user_count)"
expected_project_count="$(lm_env_value "$metadata_file" project_count)"
expected_task_count="$(lm_env_value "$metadata_file" task_count)"
[[ "$(sed -n '3p' <<<"$summary")" == "$expected_user_count" ]] \
    || lm_die "restored user row count does not match backup metadata"
[[ "$(sed -n '4p' <<<"$summary")" == "$expected_project_count" ]] \
    || lm_die "restored project row count does not match backup metadata"
[[ "$(sed -n '5p' <<<"$summary")" == "$expected_task_count" ]] \
    || lm_die "restored task row count does not match backup metadata"

lm_log "isolated restore passed; user/project/task counts=$(sed -n '3,5p' <<<"$summary" | paste -sd/ -)"
