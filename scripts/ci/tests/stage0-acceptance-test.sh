#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../../.." && pwd)"
acceptance_script="${project_root}/scripts/ci/verify-stage0-acceptance.sh"
acceptance_file="${project_root}/docs/stage0/acceptance/stage0-acceptance.json"
schema_file="${project_root}/docs/stage0/acceptance/stage0-acceptance.schema.json"
schema_validator="${project_root}/scripts/ci/validate-json-schema.py"

[[ -x "$acceptance_script" || -f "$acceptance_script" ]]
[[ -f "$acceptance_file" ]]
[[ -f "$schema_file" ]]
[[ -f "$schema_validator" ]]

bash -n "$acceptance_script"
python3 "$schema_validator" "$acceptance_file" "$schema_file" >/dev/null
jq -e '.schemaVersion == 1 and .stage == "stage0" and .status == "PROVISIONAL"' "$acceptance_file" >/dev/null
jq -e '.required[0] == "$schema" and .properties.status.enum == ["PROVISIONAL", "PASS"]' "$schema_file" >/dev/null
bash "$acceptance_script" >/dev/null

temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT
jq '.unexpectedProperty = true' "$acceptance_file" > "$temporary_dir/invalid.json"
if python3 "$schema_validator" "$temporary_dir/invalid.json" "$schema_file" >/dev/null 2>&1; then
    printf 'stage0.acceptance.error=schema_invalid_document_accepted\n' >&2
    exit 1
fi

printf 'stage0.acceptance.selftest=PASS\n'
