#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

for command in age date docker find sha256sum s3cmd sort zstd; do
    lm_require_command "$command"
done
lm_acquire_operation_lock
lm_require_private_file "$LM_ENV_FILE"
lm_require_private_file "$LM_BACKUP_ENV_FILE"
"${script_dir}/verify-production-secrets.sh"
lm_export_application_images

recipient="$(lm_env_value "$LM_BACKUP_ENV_FILE" BACKUP_AGE_RECIPIENT)"
oss_root="$(lm_env_value "$LM_BACKUP_ENV_FILE" OSS_BUCKET_URI)"
s3cfg="$(lm_env_value "$LM_BACKUP_ENV_FILE" S3CMD_CONFIG)"
backup_dir="$(lm_env_value "$LM_BACKUP_ENV_FILE" BACKUP_DIRECTORY)"

[[ "$recipient" == age1* ]] || lm_die "invalid age recipient"
[[ "$oss_root" =~ ^s3://[A-Za-z0-9._-]+/[A-Za-z0-9._/-]+$ && "$oss_root" != *..* ]] \
    || lm_die "OSS_BUCKET_URI must be a scoped bucket prefix"
[[ "$(realpath -m "$backup_dir")" == /var/backups/learning-manage ]] \
    || lm_die "BACKUP_DIRECTORY must remain /var/backups/learning-manage"
lm_require_private_file "$s3cfg"

umask 077
mkdir -p "$backup_dir"
timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
archive="${backup_dir}/learning-manage-${timestamp}.sql.zst.age"
archive_complete=false

cleanup_partial_archive() {
    if [[ "$archive_complete" == false && "$archive" == /var/backups/learning-manage/learning-manage-*.sql.zst.age ]]; then
        rm -f -- "$archive" "${archive}.sha256" "${archive}.metadata"
    fi
}
trap cleanup_partial_archive EXIT

query_key_counts() {
    lm_compose exec -T mysql sh -eu -c '
        MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
        export MYSQL_PWD
        mysql -N -B -uroot --protocol=socket "${MYSQL_DATABASE}" --execute="
          SELECT CONCAT(
            (SELECT COUNT(*) FROM \`user\`), CHAR(9),
            (SELECT COUNT(*) FROM project), CHAR(9),
            (SELECT COUNT(*) FROM task));"
    '
}

counts_before="$(query_key_counts)"
[[ "$counts_before" =~ ^[0-9]+$'\t'[0-9]+$'\t'[0-9]+$ ]] \
    || lm_die "unable to capture source key-table counts"

lm_compose exec -T mysql sh -eu -c '
    MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
    export MYSQL_PWD
    exec mysqldump -uroot --protocol=socket \
      --single-transaction --quick --routines --triggers --events \
      --set-gtid-purged=OFF --databases "${MYSQL_DATABASE}"
' | zstd -T1 -10 --stdout | age --encrypt --recipient "$recipient" --output "$archive"

[[ -s "$archive" ]] || lm_die "encrypted backup is empty"
counts_after="$(query_key_counts)"
if [[ "$counts_before" != "$counts_after" ]]; then
    rm -f -- "$archive"
    lm_die "key-table counts changed during backup; retry required"
fi
IFS=$'\t' read -r user_count project_count task_count <<<"$counts_before"
archive_sha256="$(sha256sum "$archive" | awk '{print $1}')"
archive_size="$(stat -c '%s' "$archive")"
metadata="${archive}.metadata"

daily_prefix="${oss_root%/}/daily"
printf 'created_at_utc=%s\nbytes=%s\nsha256=%s\nobject=%s/%s\nuser_count=%s\nproject_count=%s\ntask_count=%s\n' \
    "$timestamp" "$archive_size" "$archive_sha256" "$daily_prefix" "$(basename -- "$archive")" \
    "$user_count" "$project_count" "$task_count" \
    > "$metadata"
(cd "$backup_dir" && sha256sum "$(basename -- "$archive")" "$(basename -- "$metadata")" \
    > "$(basename -- "$archive").sha256")
archive_complete=true
s3cmd --config "$s3cfg" put "$archive" "${archive}.sha256" "$metadata" "${daily_prefix}/"
s3cmd --config "$s3cfg" info "${daily_prefix}/$(basename -- "$archive")" >/dev/null
s3cmd --config "$s3cfg" info "${daily_prefix}/$(basename -- "$archive").sha256" >/dev/null
s3cmd --config "$s3cfg" info "${daily_prefix}/$(basename -- "$archive").metadata" >/dev/null

prune_remote_backups() {
    local prefix="$1" keep="$2"
    mapfile -t objects < <(s3cmd --config "$s3cfg" ls "${prefix}/" \
        | awk '$4 ~ /learning-manage-[0-9]{8}T[0-9]{6}Z[.]sql[.]zst[.]age$/ { print $4 }' \
        | sort)
    while ((${#objects[@]} > keep)); do
        victim="${objects[0]}"
        [[ "$victim" == "${prefix}/"* && "$victim" == *.sql.zst.age ]] \
            || lm_die "refusing to prune unexpected OSS object: $victim"
        s3cmd --config "$s3cfg" del "$victim" "${victim}.sha256" "${victim}.metadata"
        objects=("${objects[@]:1}")
    done
}

prune_remote_backups "$daily_prefix" 7

if [[ "$(TZ=Asia/Shanghai date +%u)" == 7 ]]; then
    weekly_prefix="${oss_root%/}/weekly"
    s3cmd --config "$s3cfg" cp \
        "${daily_prefix}/$(basename -- "$archive")" \
        "${daily_prefix}/$(basename -- "$archive").sha256" \
        "${daily_prefix}/$(basename -- "$archive").metadata" \
        "${weekly_prefix}/"
    s3cmd --config "$s3cfg" info "${weekly_prefix}/$(basename -- "$archive")" >/dev/null
    prune_remote_backups "$weekly_prefix" 4
fi

find "$backup_dir" -maxdepth 1 -type f \
    \( -name 'learning-manage-*.sql.zst.age' -o -name 'learning-manage-*.sql.zst.age.sha256' \
       -o -name 'learning-manage-*.sql.zst.age.metadata' \) \
    -mtime +7 -delete

lm_log "encrypted backup uploaded and verified: $(basename -- "$archive")"
