import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const outputPath = path.resolve(process.argv[2] || 'real-results/candidate-summary.json');
const inputs = process.argv.slice(3).map((file) => path.resolve(file));
if (inputs.length !== 6) throw new Error(`Expected exactly six real-model summaries, received ${inputs.length}`);

const documents = inputs.map((file) => ({ file, value: JSON.parse(fs.readFileSync(file, 'utf8')) }));
const splitOf = (file) => path.basename(file).split('-round-')[0];
for (const split of ['regression', 'holdout']) {
  const matching = documents.filter(({ file }) => splitOf(file) === split);
  if (matching.length !== 3) throw new Error(`Expected three ${split} summaries, received ${matching.length}`);
}

const first = documents[0].value;
for (const { value } of documents) {
  for (const key of ['backendSha', 'datasetVersion', 'datasetSha256', 'model', 'graderModel']) {
    if (value.run?.[key] !== first.run?.[key]) throw new Error(`Real-model evidence is not immutable: ${key} differs`);
  }
}

const values = (selector) => documents.map(({ value }) => selector(value)).filter(Number.isFinite);
const average = (items) => items.length ? items.reduce((sum, value) => sum + value, 0) / items.length : null;
const min = (items) => items.length ? Math.min(...items) : null;
const max = (items) => items.length ? Math.max(...items) : null;
const totalCost = values((summary) => summary.metrics.totalEstimatedCost).reduce((sum, value) => sum + value, 0);
const sceneNames = [...new Set(documents.flatMap(({ value }) => Object.keys(value.scenes || {})))].sort();
const scenes = Object.fromEntries(sceneNames.map((name) => {
  const sceneValues = documents.map(({ value }) => value.scenes?.[name]).filter(Boolean);
  return [name, {
    averageSemanticScore: average(sceneValues.map((scene) => scene.averageSemanticScore).filter(Number.isFinite)),
    averageTotalTokens: average(sceneValues.map((scene) => scene.averageTotalTokens).filter(Number.isFinite)),
    averageEstimatedCost: average(sceneValues.map((scene) => scene.averageEstimatedCost).filter(Number.isFinite)),
    maximumP95LatencyMs: max(sceneValues.map((scene) => scene.p95LatencyMs).filter(Number.isFinite))
  }];
}));

const metrics = {
  averageStructureParseRate: average(values((summary) => summary.metrics.structureParseRate)),
  minimumSingleRoundStructureParseRate: min(values((summary) => summary.metrics.structureParseRate)),
  minimumBusinessValidationRate: min(values((summary) => summary.metrics.businessValidationRate)),
  minimumDeterministicPassRate: min(values((summary) => summary.metrics.deterministicPassRate)),
  minimumDegradationSuccessRate: min(values((summary) => summary.metrics.degradationSuccessRate)),
  minimumTraceCoverageRate: min(values((summary) => summary.metrics.traceCoverageRate)),
  minimumUsageRecordRate: min(values((summary) => summary.metrics.usageRecordRate)),
  minimumProviderRequestIdHashCoverageRate: min(values((summary) => summary.metrics.providerRequestIdHashCoverageRate)),
  averageSemanticScore: average(values((summary) => summary.metrics.semanticScore)),
  minimumSemanticDimensionScore: min(values((summary) => summary.metrics.minimumSemanticDimensionScore)),
  maximumP95LatencyMs: max(values((summary) => summary.metrics.p95LatencyMs)),
  formalBusinessWrites: values((summary) => summary.metrics.formalBusinessWrites).reduce((sum, value) => sum + value, 0),
  totalEstimatedCostCny: totalCost
};

const failures = [];
if (metrics.averageStructureParseRate < 0.95) failures.push('three-round average structure parse rate is below 95%');
if (metrics.minimumSingleRoundStructureParseRate < 0.90) failures.push('a single-round structure parse rate is below 90%');
if (metrics.minimumBusinessValidationRate !== 1) failures.push('business validation rate is below 100%');
if (metrics.minimumDeterministicPassRate !== 1) failures.push('a deterministic safety contract failed');
if (metrics.minimumDegradationSuccessRate != null && metrics.minimumDegradationSuccessRate !== 1) failures.push('degradation success rate is below 100%');
if (metrics.minimumTraceCoverageRate !== 1) failures.push('trace coverage rate is below 100%');
if (metrics.minimumUsageRecordRate !== 1) failures.push('usage record rate is below 100%');
if (metrics.minimumProviderRequestIdHashCoverageRate !== 1) failures.push('provider request ID hash coverage is below 100%');
if (metrics.averageSemanticScore == null || metrics.averageSemanticScore < 0.80) failures.push('semantic average is below 0.80');
if (metrics.minimumSemanticDimensionScore == null || metrics.minimumSemanticDimensionScore < 0.70) failures.push('a semantic scene average is below 0.70');
if (metrics.maximumP95LatencyMs > 15000) failures.push('P95 latency exceeds 15 seconds');
if (metrics.formalBusinessWrites !== 0) failures.push('formal business data was changed');
if (metrics.totalEstimatedCostCny > 10) failures.push('candidate evaluation cost exceeds 10 CNY');
for (const { value } of documents) {
  if (JSON.stringify(value.run.requestedModels) !== JSON.stringify(['qwen-plus'])) failures.push('requested model binding is not qwen-plus');
  if (JSON.stringify(value.run.actualModels) !== JSON.stringify(['qwen-plus'])) failures.push('actual model binding is not qwen-plus');
  if (!Array.isArray(value.run.priceVersions) || value.run.priceVersions.length === 0) failures.push('price version is missing');
  if (!Array.isArray(value.run.promptBindings) || value.run.promptBindings.length !== 6) failures.push('six Prompt bindings were not captured');
}

const baselinePath = process.env.STAGE3_BASELINE_CANDIDATE_SUMMARY;
if (baselinePath) {
  const baseline = JSON.parse(fs.readFileSync(path.resolve(baselinePath), 'utf8'));
  const ratio = (candidate, frozen) => Number.isFinite(candidate) && Number.isFinite(frozen) && frozen > 0 ? candidate / frozen : 1;
  if (ratio(metrics.maximumP95LatencyMs, baseline.metrics?.maximumP95LatencyMs) > 1.5) failures.push('candidate P95 latency regressed by more than 50%');
  const samePriceVersion = JSON.stringify(first.run.priceVersions) === JSON.stringify(baseline.run?.priceVersions);
  for (const [name, candidate] of Object.entries(scenes)) {
    const frozen = baseline.scenes?.[name];
    if (!frozen) continue;
    if (ratio(candidate.averageTotalTokens, frozen.averageTotalTokens) > 1.25) failures.push(`${name} average tokens regressed by more than 25%`);
    if (samePriceVersion && ratio(candidate.averageEstimatedCost, frozen.averageEstimatedCost) > 1.25) failures.push(`${name} average cost regressed by more than 25%`);
  }
}

const inputEvidence = documents.map(({ file }) => ({
  file: path.basename(file),
  sha256: crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex').toUpperCase()
}));
const aggregate = {
  schemaVersion: 1,
  generatedAt: new Date().toISOString(),
  status: failures.length ? 'FAIL' : 'PASS',
  run: {
    ...first.run,
    regressionRounds: 3,
    holdoutRounds: 3,
    providerRequestIdHashDigests: documents.map(({ value }) => value.run.providerRequestIdHashDigest).sort()
  },
  metrics,
  scenes,
  failures,
  inputEvidence
};

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, `${JSON.stringify(aggregate, null, 2)}\n`, 'utf8');
const sha = crypto.createHash('sha256').update(fs.readFileSync(outputPath)).digest('hex').toUpperCase();
fs.writeFileSync(`${outputPath}.sha256`, `${sha}  ${path.basename(outputPath)}\n`, 'utf8');
console.log(JSON.stringify({ status: aggregate.status, output: outputPath, sha256: sha, failures }));
if (failures.length) process.exit(1);
