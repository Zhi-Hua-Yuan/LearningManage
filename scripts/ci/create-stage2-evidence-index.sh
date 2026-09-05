#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/ci-common.sh"
ci_require_command jq
ci_require_command sha256sum
ci_require_command sort

output_file="${STAGE2_EVIDENCE_INDEX_OUTPUT:?STAGE2_EVIDENCE_INDEX_OUTPUT is required}"
exclude_file="$(realpath -m "$output_file")"
mkdir -p "$(dirname -- "$output_file")"

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

find "$project_root/docs/stage2" -type f \
  -not -path '*/evidence/wp8/*' \
  -not -path '*/release/stage2-source-evidence-index.json' \
  -not -path '*/release/stage2-release-candidate-manifest.json' \
  -not -path '*/release/stage2-release-candidate-manifest.json.sha256' \
  -not -path "$exclude_file" -print | sort | while IFS= read -r file; do
    relative="${file#"$project_root/"}"
    size="$(wc -c < "$file" | tr -d '[:space:]')"
    sha="$(sha256sum "$file" | awk '{print toupper($1)}')"
    jq -cn --arg path "$relative" --arg sha256 "$sha" --argjson size "$size" \
      '{path:$path,size:$size,sha256:$sha256}'
  done > "$tmp"

jq -s '{schemaVersion:1,stage:"stage2",root:"docs/stage2",excludes:["docs/stage2/evidence/wp8"],files:sort_by(.path)}' "$tmp" > "$output_file"
sha="$(sha256sum "$output_file" | awk '{print toupper($1)}')"
printf '%s  %s\n' "$sha" "$(basename -- "$output_file")" > "${output_file}.sha256"
printf 'stage2.evidence.files=%s\n' "$(jq '.files|length' "$output_file")"
printf 'stage2.evidence.sha256=%s\n' "$sha"
