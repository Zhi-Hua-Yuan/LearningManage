'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const test = require('node:test');

const root = path.resolve(__dirname, '..');

test('all generated case IDs are globally unique and stable-looking', () => {
  const ids = [];
  for (const file of ['development.jsonl', 'regression.jsonl', 'holdout.jsonl', 'failure-injection.jsonl']) {
    for (const line of fs.readFileSync(path.join(root, 'datasets', file), 'utf8').split(/\r?\n/).filter(Boolean)) ids.push(JSON.parse(line).caseId);
  }
  assert.equal(ids.length, 210);
  assert.equal(new Set(ids).size, 210);
  assert.ok(ids.every((id) => /^(TB|WP|TO|DR|LR)-(DEV|REG|HOLD)-\d{3}$|^FI-\d{3}$/.test(id)));
});

test('provider requires one of the fixed synthetic actors', async () => {
  const providerPath = path.join(root, 'providers', 'learning-manage-http.js');
  process.env.STAGE3_EVAL_PASSWORD = 'synthetic-test-only';
  delete require.cache[require.resolve(providerPath)];
  const Provider = require(providerPath);
  const provider = new Provider();
  await assert.rejects(
    provider.callApi('', { vars: { caseId: 'TEST', scene: 'task-breakdown', actor: 'unknown', requestPayload: '{}' } }),
    /Unknown Stage 3 actor/
  );
  delete process.env.STAGE3_EVAL_PASSWORD;
});

test('promptfoo config disables result sharing', () => {
  const config = fs.readFileSync(path.join(root, 'promptfooconfig.yaml'), 'utf8');
  assert.match(config, /sharing:\s*false/);
  assert.match(config, /learning-manage-http\.js/);
});

test('real evaluation supplies required production runtime ports', () => {
  const workflow = fs.readFileSync(path.resolve(root, '..', '..', '.github', 'workflows', 'stage3-real-eval.yml'), 'utf8');
  assert.match(workflow, /^\s*SERVER_PORT:\s*['"]?18133['"]?\s*$/m);
  assert.match(workflow, /^\s*STAGE3_API_BASE_URL:\s*http:\/\/127\.0\.0\.1:18133\/api\s*$/m);
  assert.match(workflow, /^\s*REDIS_HOST:\s*127\.0\.0\.1\s*$/m);
  assert.match(workflow, /^\s*REDIS_PORT:\s*['"]?6379['"]?\s*$/m);
});

test('locked evaluation dependencies are cross-platform and use the restricted native install', () => {
  const lock = JSON.parse(fs.readFileSync(path.join(root, 'package-lock.json'), 'utf8'));
  const missingVersions = Object.entries(lock.packages || {})
    .filter(([packagePath, metadata]) => packagePath && !metadata.version)
    .map(([packagePath]) => packagePath);
  assert.deepEqual(missingVersions, []);
  const nonOfficialResolvedUrls = Object.values(lock.packages || {})
    .map((metadata) => metadata.resolved)
    .filter(Boolean)
    .filter((resolved) => new URL(resolved).hostname !== 'registry.npmjs.org');
  assert.deepEqual(nonOfficialResolvedUrls, []);

  const packageJson = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
  assert.equal(packageJson.scripts['install:ci'], 'npm ci --ignore-scripts && npm rebuild better-sqlite3');
  for (const workflow of ['stage3-eval.yml', 'stage3-real-eval.yml']) {
    const source = fs.readFileSync(path.resolve(root, '..', '..', '.github', 'workflows', workflow), 'utf8');
    assert.match(source, /npm run install:ci/);
    assert.doesNotMatch(source, /npm ci --ignore-scripts/);
  }
});

test('credential scan distinguishes task names from standalone secret-shaped values', () => {
  const credentialPattern = /(^|[^A-Za-z0-9_])(?:Bearer\s+[A-Za-z0-9._-]{20,}|sk-[A-Za-z0-9_-]{16,})/;
  assert.equal(credentialPattern.test('task-breakdown_business_contract'), false);
  assert.equal(credentialPattern.test('{"token":"sk-ABCDEFGHIJKLMNOPQRST"}'), true);
  assert.equal(credentialPattern.test('{"authorization":"Bearer abcdefghijklmnopqrstuvwxyz"}'), true);

  for (const workflow of ['stage3-eval.yml', 'stage3-real-eval.yml']) {
    const source = fs.readFileSync(path.resolve(root, '..', '..', '.github', 'workflows', workflow), 'utf8');
    assert.match(source, /grep -R -q/);
    assert.match(source, /\(\^\|\[\^A-Za-z0-9_\]\)sk-/);
  }
});

test('release manifest schema compiles', () => {
  const Ajv2020 = require('ajv/dist/2020');
  const schema = JSON.parse(fs.readFileSync(path.resolve(root, '..', '..', 'docs', 'stage3', 'release', 'stage3-release-candidate-manifest.schema.json'), 'utf8'));
  assert.equal(typeof new Ajv2020({ strict: false, formats: false }).compile(schema), 'function');
});

test('deterministic assertions reject resource-scope violations', () => {
  const assertion = require(path.join(root, 'assertions', 'today-order.js'));
  const output = JSON.stringify({
    success: true,
    data: { items: [{ taskId: 999999, difficulty: 3, cost: 3, benefit: 3, estimatedMinutes: 30 }] }
  });
  const result = assertion(output, { vars: { allowedResourceIds: '[930001]' } });
  assert.equal(result.pass, false);
  assert.match(result.reason, /extra or duplicate/);
});

test('normalized business faults remain safe without being mislabeled as degradation', () => {
  const renameAssertion = require(path.join(root, 'assertions', 'daily-rename.js'));
  const rename = renameAssertion(JSON.stringify({ success: true, data: { items: [] } }), {
    vars: { allowedResourceIds: '[930201]', tags: '["unauthorized-id"]', requestPayload: '{"maxEdits":1}' }
  });
  assert.equal(rename.pass, true);

  const replanAssertion = require(path.join(root, 'assertions', 'list-replan.js'));
  const normalizedDate = replanAssertion(JSON.stringify({ success: true, data: { previewTasks: [{
    taskId: 933006, newPriority: 2, newTitle: '保留原计划', reason: '非法日期已恢复',
    oldDueDate: '2026-10-01', newDueDate: '2026-10-01', dueChanged: false, dueDeltaDays: 0
  }] } }), { vars: { allowedResourceIds: '[933006]', tags: '["invalid-date"]' } });
  assert.equal(normalizedDate.pass, true);

  const inconsistentDate = replanAssertion(JSON.stringify({ success: true, data: { previewTasks: [{
    taskId: 933006, newPriority: 2, newTitle: '错误计划', reason: '日期字段矛盾',
    oldDueDate: '2026-10-01', newDueDate: 'not-a-date', dueChanged: true, dueDeltaDays: 0
  }] } }), { vars: { allowedResourceIds: '[933006]', tags: '[]' } });
  assert.equal(inconsistentDate.pass, false);
  assert.match(inconsistentDate.reason, /ISO date/);

  const faultCases = fs.readFileSync(path.join(root, 'datasets', 'failure-injection.jsonl'), 'utf8')
    .split(/\r?\n/).filter(Boolean).map(JSON.parse);
  for (const fault of ['unauthorized-id', 'overlong-title', 'completed-id', 'invalid-date']) {
    const item = faultCases.find((candidate) => candidate.tags.includes(fault));
    assert.equal(item.expectedInvariants.expectNoDegradation, true);
    assert.equal(item.labelVersion, 2);
  }
});

test('semantic grading and human review receive complete synthetic facts', () => {
  const contexts = JSON.parse(fs.readFileSync(path.join(root, 'fixtures', 'semantic-context.json'), 'utf8'));
  const knownTaskIds = new Set(Object.values(contexts).flatMap((fixture) => fixture.tasks.map((task) => task.taskId)));
  const qualityCases = ['development.jsonl', 'regression.jsonl', 'holdout.jsonl']
    .flatMap((file) => fs.readFileSync(path.join(root, 'datasets', file), 'utf8').split(/\r?\n/).filter(Boolean).map(JSON.parse));
  for (const item of qualityCases.filter((candidate) => candidate.scene !== 'task-breakdown')) {
    for (const taskId of item.allowedResourceIds) assert.equal(knownTaskIds.has(taskId), true, `${item.caseId} fixture fact is missing`);
  }

  const generatedPath = path.join(root, 'tests', 'generated.json');
  const generatedExisted = fs.existsSync(generatedPath);
  const originalGenerated = generatedExisted ? fs.readFileSync(generatedPath) : null;
  try {
    execFileSync(process.execPath, [path.join(root, 'scripts', 'build-tests.mjs')], {
      cwd: root,
      env: { ...process.env, STAGE3_EVAL_SPLIT: 'regression', STAGE3_ENABLE_MODEL_GRADING: 'true' }
    });
    const generated = JSON.parse(fs.readFileSync(generatedPath, 'utf8'));
    const orderCase = generated.find((item) => item.vars.scene === 'today-order');
    const facts = JSON.parse(orderCase.vars.semanticFacts);
    assert.equal(facts.fixture.tasks.length, JSON.parse(orderCase.vars.requestPayload).taskIds.length);
    const semanticAssertion = orderCase.assert.find((item) => item.type === 'llm-rubric');
    assert.match(semanticAssertion.value, /Complete synthetic input facts/);
    assert.match(semanticAssertion.value, /lowest dimension/);
    assert.equal(semanticAssertion.threshold, 0);
  } finally {
    if (generatedExisted) fs.writeFileSync(generatedPath, originalGenerated);
    else fs.rmSync(generatedPath, { force: true });
  }
});

test('common assertion preserves unknown usage and blocks formal writes', () => {
  const assertion = require(path.join(root, 'assertions', 'common.js'));
  const base = {
    caseId: 'FI-TEST', scene: 'today-order', success: true, data: {},
    meta: { traceId: 'stage3_test_trace', callLogFound: true, callLogId: '1', degraded: true, promptTokens: null, completionTokens: null, totalTokens: null, estimatedCost: null, formalBusinessWrites: 0, aiDraftWrites: 0 }
  };
  const context = { vars: { caseId: 'FI-TEST', scene: 'today-order', tags: '["missing-usage"]', expectedInvariants: '{"expectSuccess":true,"expectDegraded":true}' } };
  assert.equal(assertion(JSON.stringify(base), context).pass, true);
  base.meta.formalBusinessWrites = 1;
  assert.equal(assertion(JSON.stringify(base), context).pass, false);
  base.scene = 'task-breakdown';
  base.success = false;
  base.meta.formalBusinessWrites = 0;
  base.meta.aiDraftWrites = 1;
  const failedContext = { vars: { caseId: 'FI-TEST', scene: 'task-breakdown', tags: '[]', expectedInvariants: '{"expectSuccess":false}' } };
  assert.equal(assertion(JSON.stringify(base), failedContext).pass, false);
});

test('provider fingerprints formal business content instead of comparing row counts only', () => {
  const providerSource = fs.readFileSync(path.join(root, 'providers', 'learning-manage-http.js'), 'utf8');
  assert.match(providerSource, /formalBusinessSnapshot/);
  assert.match(providerSource, /SELECT \* FROM/);
  assert.match(providerSource, /formalBefore\.sha256 !== formalAfter\.sha256/);
  assert.doesNotMatch(providerSource, /formalRowsAfter\s*-\s*formalRowsBefore/);
});

test('summary separates deterministic gates from semantic scores', () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'stage3-summary-'));
  try {
    const input = path.join(temp, 'input.json');
    const summary = path.join(temp, 'summary.json');
    const report = path.join(temp, 'report.md');
    fs.writeFileSync(input, JSON.stringify({ results: { results: [{
      success: true,
      vars: { caseId: 'TB-REG-001', scene: 'task-breakdown', split: 'regression', expectedInvariants: '{}' },
      namedScores: { provider_contract: 1, common_invariants: 1, 'task-breakdown_business_contract': 1, 'task-breakdown_semantic_quality': 0.85 },
      response: { output: JSON.stringify({ caseId: 'TB-REG-001', scene: 'task-breakdown', success: true, data: {}, meta: { traceId: 'trace_12345678', latencyMs: 100, totalTokens: 10, estimatedCost: 0.01, formalBusinessWrites: 0 } }) }
    }] } }));
    execFileSync(process.execPath, [path.join(root, 'scripts', 'summarize-results.mjs'), input, summary, report], { cwd: root });
    const value = JSON.parse(fs.readFileSync(summary, 'utf8'));
    assert.equal(value.status, 'PASS');
    assert.equal(value.metrics.deterministicPassRate, 1);
    assert.equal(value.metrics.semanticScore, 0.85);
    assert.equal(value.metrics.traceCoverageRate, 1);
  } finally {
    fs.rmSync(temp, { recursive: true, force: true });
  }
});

test('real-result aggregation requires and binds three regression plus three holdout rounds', () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'stage3-aggregate-'));
  try {
    const run = {
      backendSha: 'a'.repeat(40), datasetVersion: '1.0.0', datasetSha256: 'B'.repeat(64), model: 'qwen-plus', graderModel: 'qwen-max', roundCount: 1,
      requestedModels: ['qwen-plus'], actualModels: ['qwen-plus'], priceVersions: ['2026-09'],
      promptBindings: Array.from({ length: 6 }, (_, index) => `prompt-${index}:1:BUILTIN:${'A'.repeat(64)}`),
      providerRequestIdHashDigest: 'C'.repeat(64)
    };
    const base = {
      schemaVersion: 1, generatedAt: new Date(0).toISOString(), status: 'PASS', run,
      counts: { total: 34, passed: 34, failed: 0, deterministicPassed: 34, deterministicFailed: 0 },
      metrics: { structureParseRate: 1, businessValidationRate: 1, deterministicPassRate: 1, degradationSuccessRate: null, traceCoverageRate: 1, usageRecordRate: 1, providerRequestIdHashCoverageRate: 1, semanticScore: 0.85, minimumSemanticDimensionScore: 0.8, p95LatencyMs: 1000, formalBusinessWrites: 0, totalEstimatedCost: 0.1 },
      scenes: {
        'task-breakdown': { averageSemanticScore: 0.85, averageTotalTokens: 100, averageEstimatedCost: 0.01, p95LatencyMs: 1000 }
      },
      failures: []
    };
    const files = [];
    for (const split of ['regression', 'holdout']) {
      for (let round = 1; round <= 3; round += 1) {
        const file = path.join(temp, `${split}-round-${round}-summary.json`);
        fs.writeFileSync(file, JSON.stringify(base));
        files.push(file);
      }
    }
    const output = path.join(temp, 'candidate-summary.json');
    execFileSync(process.execPath, [path.join(root, 'scripts', 'aggregate-real-results.mjs'), output, ...files], { cwd: root });
    const aggregate = JSON.parse(fs.readFileSync(output, 'utf8'));
    assert.equal(aggregate.status, 'PASS');
    assert.equal(aggregate.run.regressionRounds, 3);
    assert.ok(Math.abs(aggregate.metrics.totalEstimatedCostCny - 0.6) < 1e-9);
    assert.equal(aggregate.metrics.minimumSemanticDimensionScore, 0.85);
    assert.equal(aggregate.inputEvidence.length, 6);
  } finally {
    fs.rmSync(temp, { recursive: true, force: true });
  }
});

test('real evaluation preserves failed round evidence and defers semantic gating to aggregation', () => {
  const workflow = fs.readFileSync(path.resolve(root, '..', '..', '.github', 'workflows', 'stage3-real-eval.yml'), 'utf8');
  assert.match(workflow, /eval_status=\$\?/);
  assert.match(workflow, /\[\[ -s output\.json \]\] && mv output\.json/);
  assert.match(workflow, /STAGE3_MIN_SEMANTIC_SCORE=0 STAGE3_MIN_SEMANTIC_DIMENSION_SCORE=0 npm run gate/);
  assert.match(workflow, /eval_status != 0 \|\| report_status != 0 \|\| gate_status != 0/);
});
