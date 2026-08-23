#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
acceptance_script="${project_root}/scripts/ci/verify-stage0-acceptance.sh"
acceptance_file="${project_root}/docs/stage0/acceptance/stage0-acceptance.json"
schema_file="${project_root}/docs/stage0/acceptance/stage0-acceptance.schema.json"

[[ -x "$acceptance_script" || -f "$acceptance_script" ]]
[[ -f "$acceptance_file" ]]
[[ -f "$schema_file" ]]

bash -n "$acceptance_script"
jq -e '.schemaVersion == 1 and .stage == "stage0" and .status == "PROVISIONAL"' "$acceptance_file" >/dev/null
jq -e '.required[0] == "$schema" and .properties.status.enum == ["PROVISIONAL", "PASS"]' "$schema_file" >/dev/null
bash "$acceptance_script" >/dev/null

printf 'stage0.acceptance.selftest=PASS\n'
