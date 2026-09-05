import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const files = ['development.jsonl', 'regression.jsonl', 'holdout.jsonl', 'failure-injection.jsonl'];
const combined = crypto.createHash('sha256');
const datasets = [];
for (const file of files) {
  const content = fs.readFileSync(path.join(root, 'datasets', file));
  const cases = content.toString('utf8').split(/\r?\n/).filter(Boolean).map(JSON.parse);
  combined.update(content);
  datasets.push({
    file,
    split: cases[0]?.split,
    cases: cases.length,
    sha256: crypto.createHash('sha256').update(content).digest('hex').toUpperCase(),
    labelVersions: [...new Set(cases.map((item) => item.labelVersion))].sort()
  });
}
const manifest = {
  schemaVersion: 1,
  datasetVersion: '1.0.0',
  syntheticOrAnonymizedOnly: true,
  qualityCases: 170,
  failureInjectionCases: 40,
  combinedSha256: combined.digest('hex').toUpperCase(),
  datasets
};
fs.writeFileSync(path.join(root, 'dataset-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({ status: 'PASS', combinedSha256: manifest.combinedSha256 }));
