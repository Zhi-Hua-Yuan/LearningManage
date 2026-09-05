import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';

const summaryPath = path.resolve(process.argv[2] || 'summary.json');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const summary = JSON.parse(fs.readFileSync(summaryPath, 'utf8'));
const schema = JSON.parse(fs.readFileSync(path.join(root, 'schemas', 'eval-summary.schema.json'), 'utf8'));
const ajv = new Ajv2020({ allErrors: true, strict: false, formats: false });
const validate = ajv.compile(schema);
if (!validate(summary)) throw new Error(`Summary contract failed: ${ajv.errorsText(validate.errors)}`);

const failures = [];
if (summary.status !== 'PASS') failures.push(`${summary.counts.deterministicFailed ?? summary.counts.failed} deterministic case(s) failed`);
if (summary.counts.total === 0) failures.push('evaluation contains no cases');
if ((summary.metrics.structureParseRate ?? 0) < Number(process.env.STAGE3_MIN_ROUND_PARSE_RATE || 0.90)) failures.push('single-round structure parse rate is below 90%');
if (summary.metrics.businessValidationRate != null && summary.metrics.businessValidationRate !== 1) failures.push('business validation rate is below 100%');
if ((summary.metrics.deterministicPassRate ?? 0) !== 1) failures.push('deterministic safety or business contracts failed');
if ((summary.metrics.formalBusinessWrites ?? 0) !== 0) failures.push('evaluation produced formal business writes');
if ((summary.metrics.traceCoverageRate ?? 0) !== 1) failures.push('trace coverage rate is below 100%');
if (summary.metrics.degradationSuccessRate != null && summary.metrics.degradationSuccessRate !== 1) failures.push('rule degradation success rate is below 100%');
if (summary.metrics.semanticScore != null && summary.metrics.semanticScore < Number(process.env.STAGE3_MIN_SEMANTIC_SCORE || 0.80)) failures.push('average semantic score is below 0.80');
if (summary.metrics.minimumSemanticDimensionScore != null && summary.metrics.minimumSemanticDimensionScore < Number(process.env.STAGE3_MIN_SEMANTIC_DIMENSION_SCORE || 0.70)) failures.push('a semantic scene average is below 0.70');
if ((summary.metrics.p95LatencyMs ?? 0) > Number(process.env.STAGE3_MAX_P95_MS || 15000)) failures.push('P95 latency exceeds limit');
if ((summary.metrics.totalEstimatedCost ?? 0) > Number(process.env.STAGE3_MAX_TOTAL_COST_CNY || 10)) failures.push('estimated total cost exceeds limit');
if (process.env.STAGE3_REQUIRE_USAGE === 'true' && summary.metrics.usageRecordRate !== 1) failures.push('usage record rate is below 100%');
if (process.env.STAGE3_REQUIRE_PROVIDER_REQUEST_ID === 'true' && summary.metrics.providerRequestIdHashCoverageRate !== 1) failures.push('provider request ID hash coverage is below 100%');

const baselinePath = process.env.STAGE3_BASELINE_SUMMARY;
if (baselinePath) {
  const baseline = JSON.parse(fs.readFileSync(path.resolve(baselinePath), 'utf8'));
  const ratio = (candidate, frozen) => frozen > 0 ? candidate / frozen : 1;
  if (ratio(summary.metrics.p95LatencyMs, baseline.metrics.p95LatencyMs) > 1.5) failures.push('P95 latency regressed by more than 50%');
  for (const [sceneName, candidate] of Object.entries(summary.scenes)) {
    const frozen = baseline.scenes?.[sceneName];
    if (!frozen) continue;
    if (ratio(candidate.averageTotalTokens, frozen.averageTotalTokens) > 1.25) failures.push(`${sceneName} average tokens regressed by more than 25%`);
    if (ratio(candidate.averageEstimatedCost, frozen.averageEstimatedCost) > 1.25) failures.push(`${sceneName} average cost regressed by more than 25%`);
  }
}

if (failures.length) {
  console.error(JSON.stringify({ status: 'FAIL', failures }, null, 2));
  process.exit(1);
}
console.log(JSON.stringify({ status: 'PASS', cases: summary.counts.total, p95LatencyMs: summary.metrics.p95LatencyMs }));
