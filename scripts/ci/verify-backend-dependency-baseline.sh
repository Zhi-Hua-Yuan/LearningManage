#!/usr/bin/env bash
#
# 后端依赖基线锁定。
#
# 目的：把「本轮升级没有悄悄改变技术栈」从口头承诺变成 CI 可证伪的断言。
# 三件事：
#   1. 关键坐标必须落在预期版本上；
#   2. 已淘汰 / 未获批的坐标必须不存在（knife4j、springdoc 2.3.0、org.springframework.ai）；
#   3. 产出依赖树与校验和，作为发布候选制品的一部分。
#
# PR 1 阶段 org.springframework.ai 必须完全缺席——这是「PR 1 不引入 Spring AI」
# 的可证伪形式。PR 2 引入 Spring AI 时需要显式修改本脚本，不允许通过放宽断言绕过。
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

ci_require_command sha256sum
ci_require_command jq
ci_require_command awk

output_dir="${CI_DEPENDENCY_OUTPUT_DIR:-${project_root}/ci-artifacts}"
tree_file="${output_dir}/dependency-tree.txt"
tree_sha="${output_dir}/dependency-tree.sha256"
report_file="${output_dir}/dependency-baseline-report.json"
report_sha="${output_dir}/dependency-baseline-report.sha256"

mkdir -p "$output_dir"

cd "$project_root"
[[ -x "./mvnw" ]] || ci_fail "maven_wrapper_missing"
./mvnw -B -ntp dependency:tree -DoutputFile="$tree_file" >/dev/null
[[ -s "$tree_file" ]] || ci_fail "dependency_tree_not_produced"

# 从依赖树中取出坐标的版本号。依赖树行形如：
#   +- org.springframework:spring-core:jar:6.2.19:compile
# 也兼容带 [INFO] 前缀的形态（stdout 与 outputFile 两种来源）。
tree_version() {
    local ga="$1"
    awk -v ga="$ga" '
        {
            line = $0
            sub(/^\[INFO\][[:space:]]*/, "", line)
            gsub(/^[|+\\ -]+/, "", line)
            n = split(line, parts, ":")
            if (n >= 4 && parts[1] ":" parts[2] == ga) { print parts[4]; exit }
        }
    ' "$tree_file"
}

assert_exact_version() {
    local ga="$1" expected="$2" actual
    actual="$(tree_version "$ga")"
    [[ -n "$actual" ]] || ci_fail "dependency_missing:${ga}"
    if [[ "$actual" != "$expected" ]]; then
        printf 'dependency %s expected %s but resolved %s\n' "$ga" "$expected" "$actual" >&2
        ci_fail "dependency_version_mismatch:${ga}"
    fi
    printf '%s\t%s\n' "$ga" "$actual"
}

assert_version_prefix() {
    local ga="$1" prefix="$2" actual
    actual="$(tree_version "$ga")"
    [[ -n "$actual" ]] || ci_fail "dependency_missing:${ga}"
    if [[ "$actual" != "${prefix}"* ]]; then
        printf 'dependency %s expected prefix %s but resolved %s\n' "$ga" "$prefix" "$actual" >&2
        ci_fail "dependency_version_prefix_mismatch:${ga}"
    fi
    printf '%s\t%s\n' "$ga" "$actual"
}

assert_absent() {
    local pattern="$1" code="$2"
    if grep -qE "$pattern" "$tree_file"; then
        printf 'forbidden dependency matched /%s/\n' "$pattern" >&2
        grep -nE "$pattern" "$tree_file" | head -n 10 >&2
        ci_fail "$code"
    fi
}

resolved="$(mktemp)"
trap 'rm -f "$resolved"' EXIT

{
    assert_exact_version "org.springframework.boot:spring-boot" "3.5.16"
    assert_version_prefix "org.springframework:spring-core" "6.2."
    assert_version_prefix "org.springframework:spring-webmvc" "6.2."
    assert_version_prefix "com.fasterxml.jackson.core:jackson-core" "2.21."
    assert_version_prefix "com.fasterxml.jackson.core:jackson-databind" "2.21."
    assert_version_prefix "org.flywaydb:flyway-core" "11."
    assert_version_prefix "org.flywaydb:flyway-mysql" "11."
    assert_exact_version "com.baomidou:mybatis-plus-spring-boot3-starter" "3.5.17"
    assert_exact_version "com.baomidou:mybatis-plus-jsqlparser" "3.5.17"
    assert_exact_version "org.springdoc:springdoc-openapi-starter-webmvc-ui" "2.8.17"
} > "$resolved"

# 已淘汰坐标：knife4j 与 Spring Boot 3.5 硬冲突，且上游已停更（最后 release 2024-01）。
assert_absent 'com\.github\.xiaoymin|knife4j' "knife4j_dependency_present"
# 旧版 springdoc 会在 Spring Framework 6.2 下抛 NoSuchMethodError。
assert_absent 'springdoc-openapi[a-z-]*:jar:2\.3\.0' "legacy_springdoc_dependency_present"
# PR 1 的程序性约束：不引入任何 Spring AI 构件，包括 BOM。
assert_absent 'org\.springframework\.ai' "spring_ai_dependency_present"

printf '%s  %s\n' "$(sha256sum "$tree_file" | awk '{print toupper($1)}')" \
    "$(basename -- "$tree_file")" > "$tree_sha"

resolved_json="$(jq -Rn '[inputs | split("\t") | {coordinate: .[0], version: .[1]}]' < "$resolved")"

jq -n \
    --arg treeSha256 "$(sha256sum "$tree_file" | awk '{print toupper($1)}')" \
    --argjson resolved "$resolved_json" \
    '{
        schemaVersion: 1,
        status: "PASS",
        forbiddenDependencies: ["com.github.xiaoymin:knife4j-*", "org.springdoc:*:2.3.0", "org.springframework.ai:*"],
        dependencyTreeSha256: $treeSha256,
        resolved: $resolved
    }' > "$report_file"

printf '%s  %s\n' "$(sha256sum "$report_file" | awk '{print toupper($1)}')" \
    "$(basename -- "$report_file")" > "$report_sha"

ci_emit "dependency_baseline.gate" "PASS"
ci_emit "dependency_baseline.tree" "$(basename -- "$tree_file")"
ci_emit "dependency_baseline.tree_sha256" "$(sha256sum "$tree_file" | awk '{print toupper($1)}')"
