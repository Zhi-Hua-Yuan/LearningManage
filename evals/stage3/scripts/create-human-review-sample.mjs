import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const inputPaths = process.argv.slice(2);
if (!inputPaths.length) throw new Error('Pass one or more Promptfoo result JSON files');
const candidates = new Map();
for (const inputPath of inputPaths) {
  const document = JSON.parse(fs.readFileSync(path.resolve(inputPath), 'utf8'));
  const rows = document?.results?.results || document?.results || document?.table?.body || [];
  for (const row of rows) {
    const vars = row?.vars || row?.testCase?.vars || row?.test?.vars || {};
    const metadata = row?.metadata || row?.testCase?.metadata || row?.test?.metadata || {};
    const caseId = metadata.caseId || vars.caseId;
    if (!caseId || candidates.has(caseId)) continue;
    const namedScores = row?.namedScores || row?.gradingResult?.namedScores || {};
    const semanticEntry = Object.entries(namedScores).find(([name]) => name.endsWith('_semantic_quality'));
    if (!semanticEntry || !Number.isFinite(Number(semanticEntry[1]))) continue;
    let envelope = null;
    try { envelope = JSON.parse(row?.response?.output || 'null'); } catch { /* deterministic gates own malformed output */ }
    const parseVar = (value, fallback) => {
      if (value == null) return fallback;
      if (typeof value !== 'string') return value;
      try { return JSON.parse(value); } catch { return fallback; }
    };
    candidates.set(caseId, {
      caseId,
      scene: metadata.scene || vars.scene,
      split: metadata.split || vars.split,
      inputFacts: parseVar(vars.semanticFacts, {}),
      rubricDimensions: parseVar(vars.semanticRubric, []),
      outputData: envelope?.data ?? null,
      modelScore: Number(semanticEntry[1]),
      humanScore: null,
      agreement: null,
      reviewer: null,
      reviewedAt: null,
      notes: null
    });
  }
}
const ordered = [...candidates.values()].sort((left, right) => {
  const leftHash = crypto.createHash('sha256').update(left.caseId).digest('hex');
  const rightHash = crypto.createHash('sha256').update(right.caseId).digest('hex');
  return leftHash.localeCompare(rightHash);
});
const size = Math.ceil(ordered.length * 0.2);
const sample = {
  schemaVersion: 1,
  samplingAlgorithm: 'lowest-sha256-case-id',
  population: ordered.length,
  sampleSize: size,
  agreementTolerance: 0.2,
  status: 'PENDING_HUMAN_REVIEW',
  items: ordered.slice(0, size)
};
fs.writeFileSync('human-review-sample.json', `${JSON.stringify(sample, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({ status: sample.status, population: sample.population, sampleSize: sample.sampleSize }));
