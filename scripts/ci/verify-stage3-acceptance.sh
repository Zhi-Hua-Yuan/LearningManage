#!/usr/bin/env bash

set -Eeuo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$project_root"

required=(
  evals/stage3/package.json
  evals/stage3/package-lock.json
  evals/stage3/promptfooconfig.yaml
  evals/stage3/dataset-manifest.json
  evals/stage3/prompt-manifest.json
  evals/stage3/fixtures/semantic-context.json
  docs/stage3/requirements/stage3-acceptance-contract.json
  docs/stage3/requirements/published-migrations.sha256
  docs/stage3/release/stage3-release-candidate-manifest.schema.json
  deploy/docker-compose.stage3-eval.yml
  .github/workflows/stage3-eval.yml
  .github/workflows/stage3-real-eval.yml
  scripts/ci/create-stage3-evidence-index.sh
  scripts/ci/create-stage3-release-manifest.sh
  scripts/ci/provision-stage3-eval-reader.sh
  scripts/ci/seed-stage3-eval.sh
  scripts/ci/verify-stage3-release-manifest.sh
)
for file in "${required[@]}"; do
  [[ -f "$file" ]] || { printf 'missing Stage 3 contract file: %s\n' "$file" >&2; exit 1; }
done

[[ "$(find src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' | wc -l | tr -d ' ')" == 3 ]]
[[ ! -e src/main/resources/db/migration/V4__stage3.sql ]]
sha256sum --check docs/stage3/requirements/published-migrations.sha256

node - <<'NODE'
const fs = require('node:fs');
const crypto = require('node:crypto');
const pkg = JSON.parse(fs.readFileSync('evals/stage3/package.json'));
const lock = JSON.parse(fs.readFileSync('evals/stage3/package-lock.json'));
if (pkg.engines.node !== '22.13.1' || pkg.engines.npm !== '10.9.2') throw new Error('Stage 3 Node/npm contract drift');
for (const name of ['promptfoo', 'ajv', 'mysql2']) {
  const requested = pkg.devDependencies[name];
  if (!requested || /^[~^]/.test(requested)) throw new Error(`${name} is not exact-pinned`);
  const locked = lock.packages[`node_modules/${name}`]?.version;
  if (locked !== requested) throw new Error(`${name} lock mismatch: ${requested} != ${locked}`);
}
const datasets = JSON.parse(fs.readFileSync('evals/stage3/dataset-manifest.json'));
if (datasets.qualityCases !== 170 || datasets.failureInjectionCases !== 40 || datasets.syntheticOrAnonymizedOnly !== true) throw new Error('dataset manifest contract failed');
const datasetFiles = ['development.jsonl', 'regression.jsonl', 'holdout.jsonl', 'failure-injection.jsonl'];
const combined = crypto.createHash('sha256');
const cases = [];
for (const file of datasetFiles) {
  const content = fs.readFileSync(`evals/stage3/datasets/${file}`);
  combined.update(content);
  cases.push(...content.toString('utf8').split(/\r?\n/).filter(Boolean).map(JSON.parse));
}
if (combined.digest('hex').toUpperCase() !== datasets.combinedSha256) throw new Error('dataset content hash drift');
const contexts = JSON.parse(fs.readFileSync('evals/stage3/fixtures/semantic-context.json'));
const knownTaskIds = new Set(Object.values(contexts).flatMap((fixture) => fixture.tasks.map((task) => task.taskId)));
for (const item of cases.filter((candidate) => candidate.split !== 'failure-injection' && candidate.scene !== 'task-breakdown')) {
  if (item.allowedResourceIds.some((taskId) => !knownTaskIds.has(taskId))) throw new Error(`semantic fixture fact missing for ${item.caseId}`);
}
const prompts = JSON.parse(fs.readFileSync('evals/stage3/prompt-manifest.json')).prompts;
if (prompts.length !== 6 || new Set(prompts.map((item) => item.code)).size !== 6) throw new Error('prompt manifest contract failed');
if (prompts.some((item) => !/^[A-F0-9]{64}$/.test(item.sha256))) throw new Error('prompt hash contract failed');
NODE

git check-ignore -q evals/stage3/output.json
git check-ignore -q evals/stage3/node_modules/example
! find src/main/resources/db/migration -maxdepth 1 -type f -name 'V4*' | grep -q .

printf 'stage3.acceptance=PASS\n'
