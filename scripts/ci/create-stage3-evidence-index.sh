#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
source "${script_dir}/lib/ci-common.sh"
ci_require_command jq
ci_require_command sha256sum

output_file="${STAGE3_EVIDENCE_INDEX_OUTPUT:?STAGE3_EVIDENCE_INDEX_OUTPUT is required}"
mkdir -p "$(dirname -- "$output_file")"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

for root in "$project_root/docs/stage3" "$project_root/evals/stage3"; do
  find "$root" -type f \
    -not -path '*/node_modules/*' \
    -not -path '*/.promptfoo/*' \
    -not -path '*/real-results/*' \
    -not -path '*/tests/generated.json' \
    -not -name 'output*.json' \
    -not -name 'summary.json' \
    -not -name 'report.md' \
    -not -path "$(realpath -m "$output_file")" -print
done | sort | while IFS= read -r file; do
  relative="${file#"$project_root/"}"
  size="$(wc -c < "$file" | tr -d '[:space:]')"
  sha="$(sha256sum "$file" | awk '{print toupper($1)}')"
  jq -cn --arg path "$relative" --arg sha256 "$sha" --argjson size "$size" \
    '{path:$path,size:$size,sha256:$sha256}'
done > "$tmp"

jq -s '{schemaVersion:1,stage:"stage3",roots:["docs/stage3","evals/stage3"],files:sort_by(.path)}' "$tmp" > "$output_file"
sha="$(sha256sum "$output_file" | awk '{print toupper($1)}')"
printf '%s  %s\n' "$sha" "$(basename -- "$output_file")" > "${output_file}.sha256"
printf 'stage3.evidence.files=%s\n' "$(jq '.files|length' "$output_file")"
printf 'stage3.evidence.sha256=%s\n' "$sha"
