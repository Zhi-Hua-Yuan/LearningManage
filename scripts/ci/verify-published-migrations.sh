#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
manifest="${project_root}/src/test/resources/flyway/published-migrations.sha256"
migration_dir="${project_root}/src/main/resources/db/migration"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

ci_require_command "git"
ci_require_command "awk"
ci_require_command "sha256sum"
[[ -f "$manifest" ]] || ci_fail "published_migration_manifest_missing"
[[ -d "$migration_dir" ]] || ci_fail "migration_directory_missing"

cd "$project_root"

base_commit=""
if [[ -n "${BASE_REF:-}" ]]; then
    if ! base_commit="$(git rev-parse --verify "${BASE_REF}^{commit}" 2>/dev/null)"; then
        ci_fail "base_ref_not_resolvable"
    fi
fi

declare -A manifest_paths=()
declare -A versions=()
max_immutable_version=0
manifest_entries=0

while read -r checksum path extra; do
    [[ -n "${checksum:-}" ]] || continue
    [[ "$checksum" == \#* ]] && continue
    [[ -z "${extra:-}" ]] || ci_fail "published_manifest_entry_invalid"
    [[ "$checksum" =~ ^[A-F0-9]{64}$ ]] || ci_fail "published_manifest_checksum_invalid"
    [[ "$path" =~ ^src/main/resources/db/migration/V([0-9]+)__[A-Za-z0-9_]+\.sql$ ]] \
        || ci_fail "published_manifest_path_invalid"
    [[ -z "${manifest_paths[$path]:-}" ]] || ci_fail "published_manifest_path_duplicate"

    version="${BASH_REMATCH[1]}"
    [[ -z "${versions[$version]:-}" ]] || ci_fail "published_migration_version_duplicate"
    manifest_paths["$path"]="$checksum"
    versions["$version"]="$path"
    manifest_entries=$((manifest_entries + 1))

    [[ -f "$path" ]] || ci_fail "published_migration_missing"
    actual_checksum="$(sha256sum "$path" | awk '{print toupper($1)}')"
    ci_assert_equals "$checksum" "$actual_checksum" "published_migration_checksum_mismatch"

    immutable=false
    if git cat-file -e "HEAD:${path}" 2>/dev/null; then
        immutable=true
        git diff --quiet -- "$path" || ci_fail "published_migration_worktree_modified"
        git diff --cached --quiet -- "$path" || ci_fail "published_migration_index_modified"
    fi
    if [[ -n "$base_commit" ]] && git cat-file -e "${base_commit}:${path}" 2>/dev/null; then
        immutable=true
        git diff --quiet "${base_commit}...HEAD" -- "$path" \
            || ci_fail "published_migration_changed_from_base"
    fi
    if [[ "$immutable" == "true" ]] && (( version > max_immutable_version )); then
        max_immutable_version=$version
    fi
done <"$manifest"

(( manifest_entries > 0 )) || ci_fail "published_manifest_empty"

shopt -s nullglob
migration_files=("${migration_dir}"/*.sql)
(( ${#migration_files[@]} > 0 )) || ci_fail "migration_directory_empty"

for migration_file in "${migration_files[@]}"; do
    relative_path="${migration_file#${project_root}/}"
    [[ -n "${manifest_paths[$relative_path]:-}" ]] || ci_fail "migration_not_listed_in_manifest"
    file_name="$(basename "$migration_file")"
    [[ "$file_name" =~ ^V([0-9]+)__[A-Za-z0-9_]+\.sql$ ]] \
        || ci_fail "migration_filename_invalid"
    version="${BASH_REMATCH[1]}"

    if ! git cat-file -e "HEAD:${relative_path}" 2>/dev/null; then
        if [[ -z "$base_commit" ]] || ! git cat-file -e "${base_commit}:${relative_path}" 2>/dev/null; then
            (( version > max_immutable_version )) || ci_fail "new_migration_version_not_greater"
        fi
    fi
done

ci_emit "published.verify.success" "true"
ci_emit "published.entries" "$manifest_entries"
ci_emit "published.max_immutable_version" "$max_immutable_version"
if [[ -n "$base_commit" ]]; then
    ci_emit "published.base_commit" "$base_commit"
fi
