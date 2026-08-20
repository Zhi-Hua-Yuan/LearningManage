#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
    printf '%s\n' 'flyway.error=usage: info|validate|baseline|migrate' >&2
    exit 1
fi

action="$1"
case "$action" in
    info|validate|baseline|migrate) ;;
    *)
        printf '%s\n' 'flyway.error=usage: info|validate|baseline|migrate' >&2
        exit 1
        ;;
esac

for name in DB_HOST DB_PORT DB_NAME FLYWAY_DB_USERNAME FLYWAY_DB_PASSWORD; do
    if [[ -z "${!name:-}" ]]; then
        printf 'flyway.error=%s is required; credentials must be supplied through the protected environment\n' "$name" >&2
        exit 1
    fi
done

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/.." && pwd)"
maven_wrapper="${project_root}/mvnw"

[[ -f "$maven_wrapper" ]] || {
    printf '%s\n' 'flyway.error=Maven Wrapper not found' >&2
    exit 1
}

cd "$project_root"
"$maven_wrapper" -q -DskipTests spring-boot:run \
    -Dspring-boot.run.main-class=com.spt.learningmanage.flyway.FlywayAdmin \
    "-Dspring-boot.run.arguments=${action}"
