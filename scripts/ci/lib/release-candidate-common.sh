#!/usr/bin/env bash

set -Eeuo pipefail

release_fail() {
    local code="$1"
    printf 'release.status=FAIL\n' >&2
    printf 'release.error=%s\n' "$code" >&2
    exit 1
}

release_require_env() {
    local name="$1"
    [[ -n "${!name:-}" ]] || release_fail "missing_environment_${name}"
}

release_validate_sha() {
    local value="$1"
    [[ "$value" =~ ^[0-9a-f]{40}$ ]] || release_fail "invalid_commit_sha"
}

release_validate_sha256() {
    local value="$1"
    [[ "$value" =~ ^[0-9A-Fa-f]{64}$ ]] || release_fail "invalid_sha256"
}

release_validate_candidate_id() {
    local value="$1"
    [[ ${#value} -ge 1 && ${#value} -le 64 ]] || release_fail "invalid_candidate_id_length"
    [[ "$value" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || release_fail "invalid_candidate_id"
}

release_validate_reason() {
    local value="$1"
    [[ ${#value} -ge 1 && ${#value} -le 200 ]] || release_fail "invalid_reason_length"
    [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] || release_fail "invalid_reason_control_character"
    [[ "$value" != *$'\t'* ]] || release_fail "invalid_reason_control_character"
}

release_emit_output() {
    local key="$1"
    local value="$2"
    printf 'release.%s=%s\n' "$key" "$value"
    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
        printf '%s=%s\n' "$key" "$value" >> "$GITHUB_OUTPUT"
    fi
}

release_file_sha256() {
    local path="$1"
    [[ -f "$path" ]] || release_fail "required_file_missing"
    sha256sum "$path" | awk '{ print toupper($1) }'
}

release_assert_checkout() {
    local checkout_dir="$1"
    local expected_sha="$2"
    local expected_repository="$3"

    [[ -d "$checkout_dir/.git" ]] || release_fail "checkout_missing"
    release_validate_sha "$expected_sha"

    local actual_sha
    actual_sha="$(git -C "$checkout_dir" rev-parse HEAD)"
    [[ "$actual_sha" == "$expected_sha" ]] || release_fail "checkout_sha_mismatch"

    local remote_url
    remote_url="$(git -C "$checkout_dir" remote get-url origin)"
    case "$remote_url" in
        "https://github.com/${expected_repository}"|"https://github.com/${expected_repository}.git"|"git@github.com:${expected_repository}.git") ;;
        *) release_fail "repository_identity_mismatch" ;;
    esac

    git -C "$checkout_dir" fetch --quiet --no-tags origin \
        "+refs/heads/develop:refs/remotes/origin/develop"

    local develop_sha
    develop_sha="$(git -C "$checkout_dir" rev-parse refs/remotes/origin/develop)"
    [[ "$develop_sha" == "$expected_sha" ]] || release_fail "candidate_not_current_develop_tip"
    printf '%s\n' "$develop_sha"
}

release_remote_develop_sha() {
    local repository="$1"
    local result
    result="$(git ls-remote --exit-code "https://github.com/${repository}.git" refs/heads/develop)" \
        || release_fail "remote_develop_lookup_failed"
    awk 'NR == 1 { print $1 }' <<< "$result"
}
