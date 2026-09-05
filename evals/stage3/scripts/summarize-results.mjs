import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const [inputName = 'output.json', summaryName = 'summary.json', reportName = 'report.md'] = process.argv.slice(2);
const inputPath = path.resolve(inputName);
const document = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
const rows = document?.results?.results || document?.results || document?.table?.body || [];
if (!Array.isArray(rows)) throw new Error('Unsupported Promptfoo result format: result rows were not found');

function parseEnvelope(row) {
  const output = row?.response?.output ?? row?.response?.raw ?? row?.output;
  if (typeof output !== 'string') return null;
  try { return JSON.parse(output); } catch { return null; }
}

function percentile(values, value) {
  const usable = values.filter(Number.isFinite);
  if (!usable.length) return null;
  const sorted = [...usable].sort((a, b) => a - b);
  return sorted[Math.ceil((value / 100) * sorted.length) - 1];
}

function parseJson(value, fallback) {
  if (value == null || value === '') return fallback;
  if (typeof value !== 'string') return value;
  try { return JSON.parse(value); } catch { return fallback; }
}

function average(values) {
  const usable = values.filter(Number.isFinite);
  return usable.length ? usable.reduce((sum, value) => sum + value, 0) / usable.length : null;
}

function scoreBySuffix(namedScores, suffix) {
  const entry = Object.entries(namedScores || {}).find(([name]) => name.endsWith(suffix));
  return entry ? Number(entry[1]) : null;
}

const normalized = rows.map((row, index) => {
  const envelope = parseEnvelope(row);
  const vars = row?.vars || row?.testCase?.vars || row?.test?.vars || {};
  const metadata = row?.metadata || row?.testCase?.metadata || row?.test?.metadata || {};
  const grading = row?.gradingResult || row?.grading || {};
  const namedScores = row?.namedScores || grading?.namedScores || {};
  const expected = parseJson(vars.expectedInvariants, {});
  const providerContractScore = Number(namedScores.provider_contract);
  const commonScore = Number(namedScores.common_invariants);
  const businessScore = scoreBySuffix(namedScores, '_business_contract');
  const semanticScore = scoreBySuffix(namedScores, '_semantic_quality');
  const deterministicScores = [providerContractScore, commonScore, businessScore].filter(Number.isFinite);
  const deterministicPass = deterministicScores.length > 0 && deterministicScores.every((score) => score === 1);
  return {
    caseId: envelope?.caseId || metadata.caseId || vars.caseId || `UNKNOWN-${index + 1}`,
    scene: envelope?.scene || metadata.scene || vars.scene || 'unknown',
    split: metadata.split || vars.split || 'unknown',
    passed: Boolean(row?.success ?? grading?.pass ?? false),
    score: Number(row?.score ?? grading?.score ?? 0),
    namedScores,
    deterministicPass,
    providerContractScore: Number.isFinite(providerContractScore) ? providerContractScore : null,
    commonScore: Number.isFinite(commonScore) ? commonScore : null,
    businessScore: Number.isFinite(businessScore) ? businessScore : null,
    semanticScore: Number.isFinite(semanticScore) ? semanticScore : null,
    expected,
    envelope,
    latencyMs: Number(envelope?.meta?.latencyMs ?? row?.latencyMs ?? 0),
    promptTokens: envelope?.meta?.promptTokens ?? null,
    completionTokens: envelope?.meta?.completionTokens ?? null,
    totalTokens: envelope?.meta?.totalTokens ?? null,
    estimatedCost: row?.cost != null && Number.isFinite(Number(row.cost)) ? Number(row.cost) : (envelope?.meta?.estimatedCost ?? null),
    reason: grading?.reason || row?.error || null
  };
});

const scenes = {};
for (const item of normalized) {
  const scene = scenes[item.scene] ||= {
    total: 0, passed: 0, failed: 0, deterministicPassed: 0,
    latenciesMs: [], totalTokens: [], estimatedCosts: [], semanticScores: []
  };
  scene.total += 1;
  scene[item.passed ? 'passed' : 'failed'] += 1;
  if (item.deterministicPass) scene.deterministicPassed += 1;
  if (Number.isFinite(item.latencyMs) && item.latencyMs >= 0) scene.latenciesMs.push(item.latencyMs);
  if (item.totalTokens != null) scene.totalTokens.push(Number(item.totalTokens));
  if (item.estimatedCost != null) scene.estimatedCosts.push(Number(item.estimatedCost));
  if (item.semanticScore != null) scene.semanticScores.push(item.semanticScore);
}

for (const scene of Object.values(scenes)) {
  scene.passRate = scene.total ? scene.passed / scene.total : 0;
  scene.deterministicPassRate = scene.total ? scene.deterministicPassed / scene.total : 0;
  scene.p50LatencyMs = percentile(scene.latenciesMs, 50);
  scene.p95LatencyMs = percentile(scene.latenciesMs, 95);
  scene.averageTotalTokens = scene.totalTokens.length ? scene.totalTokens.reduce((a, b) => a + b, 0) / scene.totalTokens.length : null;
  scene.averageEstimatedCost = scene.estimatedCosts.length ? scene.estimatedCosts.reduce((a, b) => a + b, 0) / scene.estimatedCosts.length : null;
  scene.averageSemanticScore = average(scene.semanticScores);
  delete scene.latenciesMs;
  delete scene.totalTokens;
  delete scene.estimatedCosts;
  delete scene.semanticScores;
}

const failed = normalized.filter((item) => !item.passed);
const deterministicFailed = normalized.filter((item) => !item.deterministicPass);
const usageKnown = normalized.filter((item) => item.totalTokens != null);
const traceKnown = normalized.filter((item) => typeof item.envelope?.meta?.traceId === 'string' && item.envelope.meta.traceId.length > 0);
const providerRequestHashes = normalized.map((item) => item.envelope?.meta?.providerRequestIdHash).filter((value) => typeof value === 'string');
const degradationExpected = normalized.filter((item) => item.expected.expectDegraded === true);
const degradationSucceeded = degradationExpected.filter((item) => item.envelope?.meta?.degraded === true);
const providerScores = normalized.map((item) => item.providerContractScore).filter(Number.isFinite);
const businessScores = normalized.map((item) => item.businessScore).filter(Number.isFinite);
const semanticScores = normalized.map((item) => item.semanticScore).filter(Number.isFinite);
const semanticSceneAverages = Object.values(scenes).map((scene) => scene.averageSemanticScore).filter(Number.isFinite);
const formalBusinessWrites = normalized.reduce((sum, item) => sum + Number(item.envelope?.meta?.formalBusinessWrites || 0), 0);
const uniqueMeta = (key) => [...new Set(normalized.map((item) => item.envelope?.meta?.[key]).filter((value) => value != null))].sort();
const datasetHash = crypto.createHash('sha256');
for (const file of ['development.jsonl', 'regression.jsonl', 'holdout.jsonl', 'failure-injection.jsonl']) {
  datasetHash.update(fs.readFileSync(path.resolve('datasets', file)));
}
const summary = {
  schemaVersion: 1,
  generatedAt: new Date().toISOString(),
  status: deterministicFailed.length ? 'FAIL' : 'PASS',
  run: {
    backendSha: process.env.STAGE3_BACKEND_SHA || process.env.GITHUB_SHA || null,
    datasetVersion: process.env.STAGE3_DATASET_VERSION || '1.0.0',
    datasetSha256: datasetHash.digest('hex').toUpperCase(),
    model: process.env.STAGE3_SUT_MODEL || 'qwen-plus',
    graderModel: process.env.STAGE3_ENABLE_MODEL_GRADING === 'true' ? (process.env.STAGE3_GRADER_MODEL || 'qwen-max') : null,
    roundCount: Number(process.env.STAGE3_ROUND_COUNT || 1),
    requestedModels: uniqueMeta('requestedModel'),
    actualModels: uniqueMeta('actualModel'),
    priceVersions: [...new Set([...uniqueMeta('priceVersion'), process.env.STAGE3_GRADER_PRICE_VERSION].filter(Boolean))].sort(),
    promptBindings: [...new Set(normalized.map((item) => {
      const meta = item.envelope?.meta;
      return meta?.promptCode ? `${meta.promptCode}:${meta.promptVersion}:${meta.promptSource}:${meta.promptContentHash || 'UNKNOWN'}` : null;
    }).filter(Boolean))].sort(),
    providerRequestIdHashDigest: providerRequestHashes.length
      ? crypto.createHash('sha256').update(providerRequestHashes.sort().join('\n')).digest('hex').toUpperCase()
      : null
  },
  counts: {
    total: normalized.length,
    passed: normalized.length - failed.length,
    failed: failed.length,
    deterministicPassed: normalized.length - deterministicFailed.length,
    deterministicFailed: deterministicFailed.length
  },
  metrics: {
    passRate: normalized.length ? (normalized.length - failed.length) / normalized.length : 0,
    deterministicPassRate: normalized.length ? (normalized.length - deterministicFailed.length) / normalized.length : 0,
    structureParseRate: average(providerScores),
    businessValidationRate: average(businessScores),
    degradationSuccessRate: degradationExpected.length ? degradationSucceeded.length / degradationExpected.length : null,
    traceCoverageRate: normalized.length ? traceKnown.length / normalized.length : 0,
    semanticScore: average(semanticScores),
    minimumSemanticDimensionScore: semanticSceneAverages.length ? Math.min(...semanticSceneAverages) : null,
    formalBusinessWrites,
    p50LatencyMs: percentile(normalized.map((item) => item.latencyMs), 50),
    p95LatencyMs: percentile(normalized.map((item) => item.latencyMs), 95),
    usageRecordRate: normalized.length ? usageKnown.length / normalized.length : 0,
    providerRequestIdHashCoverageRate: normalized.length ? providerRequestHashes.length / normalized.length : 0,
    totalEstimatedCost: normalized.reduce((sum, item) => sum + Number(item.estimatedCost || 0), 0)
  },
  scenes,
  failures: failed.map(({ caseId, scene, split, reason, deterministicPass }) => ({ caseId, scene, split, deterministicPass, reason }))
};

fs.writeFileSync(path.resolve(summaryName), `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
const lines = [
  '# LearningManage Stage 3 evaluation report', '',
  `- Status: **${summary.status}**`,
  `- Cases: ${summary.counts.passed}/${summary.counts.total} passed`,
  `- Deterministic contracts: ${summary.counts.deterministicPassed}/${summary.counts.total} passed`,
  `- Structure / business validation: ${formatRate(summary.metrics.structureParseRate)} / ${formatRate(summary.metrics.businessValidationRate)}`,
  `- Trace / Usage coverage: ${formatRate(summary.metrics.traceCoverageRate)} / ${formatRate(summary.metrics.usageRecordRate)}`,
  `- Semantic average / minimum scene average: ${formatScore(summary.metrics.semanticScore)} / ${formatScore(summary.metrics.minimumSemanticDimensionScore)}`,
  `- Dataset: ${summary.run.datasetVersion} (${summary.run.datasetSha256})`,
  `- Model: ${summary.run.model}`,
  `- P50/P95 latency: ${summary.metrics.p50LatencyMs ?? 'N/A'} / ${summary.metrics.p95LatencyMs ?? 'N/A'} ms`,
  `- Estimated cost: ${summary.metrics.totalEstimatedCost}`, '',
  '## Scene results', '',
  '| Scene | Passed | Total | Pass rate | P95 latency |',
  '|---|---:|---:|---:|---:|',
  ...Object.entries(scenes).map(([name, value]) => `| ${name} | ${value.passed} | ${value.total} | ${(value.passRate * 100).toFixed(2)}% | ${value.p95LatencyMs ?? 'N/A'} ms |`), '',
  '## Failed cases', '',
  ...(failed.length ? summary.failures.map((item) => `- ${item.caseId} (${item.scene}): ${item.reason || 'assertion failed'}`) : ['No failed cases.']), ''
];
fs.writeFileSync(path.resolve(reportName), `${lines.join('\n')}\n`, 'utf8');
console.log(JSON.stringify({ status: summary.status, summary: path.resolve(summaryName), report: path.resolve(reportName) }));

function formatRate(value) {
  return value == null ? 'N/A' : `${(value * 100).toFixed(2)}%`;
}

function formatScore(value) {
  return value == null ? 'N/A' : value.toFixed(3);
}
