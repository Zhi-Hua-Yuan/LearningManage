import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const matrix = JSON.parse(fs.readFileSync(path.join(root, 'matrix.json'), 'utf8'));
const stage3 = JSON.parse(fs.readFileSync(path.resolve(root, matrix.datasetSources.core), 'utf8'));
const stage5 = JSON.parse(fs.readFileSync(path.resolve(root, matrix.datasetSources.rag), 'utf8'));
const stage6 = JSON.parse(fs.readFileSync(path.resolve(root, matrix.datasetSources.agent), 'utf8'));
const ragNegative = JSON.parse(fs.readFileSync(path.resolve(root, matrix.datasetSources.ragNegative), 'utf8'));
const agentWriteTool = JSON.parse(fs.readFileSync(path.resolve(root, matrix.datasetSources.agentWriteTool), 'utf8'));

const failures = [];
const expect = (condition, message) => { if (!condition) failures.push(message); };
expect(matrix.schemaVersion === 1, 'matrix schemaVersion must be 1');
expect(JSON.stringify(matrix.adapters) === JSON.stringify(['legacy', 'spring-ai']), 'adapter order must be legacy,spring-ai');
expect(matrix.defaultAdapter === 'spring-ai', 'default adapter must be spring-ai');
expect(stage3.qualityCases === matrix.core.qualityCases, 'core quality case count drift');
expect(stage3.failureInjectionCases === matrix.core.failureInjectionCases, 'core failure case count drift');
expect(stage5.length === matrix.rag.positiveCases, 'RAG positive case count drift');
expect(stage5.filter((item) => item.vars?.split === 'regression').length === matrix.rag.regressionCases, 'RAG regression split drift');
expect(stage5.filter((item) => item.vars?.split === 'holdout').length === matrix.rag.holdoutCases, 'RAG holdout split drift');
expect(stage6.length === matrix.agent.existingCases, 'Agent historical case count drift');
expect(ragNegative.length === matrix.rag.negativeCases, 'RAG negative case count drift');
expect(ragNegative.filter((item) => item.kind === 'no-answer').length === 4, 'RAG no-answer coverage drift');
expect(ragNegative.filter((item) => item.kind === 'permission-isolation').length === 4, 'RAG permission coverage drift');
expect(ragNegative.filter((item) => item.kind === 'stale-version').length === 4, 'RAG stale-version coverage drift');
expect(agentWriteTool.length === matrix.agent.writeToolInjectionCases, 'Agent write Tool case count drift');
expect(agentWriteTool.every((item) => item.expectedTerminal === 'PARTIAL'), 'Agent write Tool cases must degrade to PARTIAL');
expect(matrix.rag.positiveCases >= 30, 'RAG labelled set must contain at least 30 cases');
expect(matrix.rag.recallAt5Minimum >= 0.8, 'RAG Recall@5 threshold may not be lowered');
expect(matrix.agent.maximumToolCalls === 4, 'Agent Tool call maximum drift');
for (const [name, enabled] of Object.entries(matrix.featureFlags)) {
  expect(typeof enabled === 'boolean', `feature flag ${name} must be boolean`);
}

const hashes = {};
for (const relative of ['matrix.json', '../stage3/dataset-manifest.json', '../stage5/tests/generated.json', '../stage6/tests/generated.json', 'rag-negative-cases.json', 'agent-write-tool-cases.json']) {
  const file = path.resolve(root, relative);
  hashes[relative] = crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex').toUpperCase();
}
if (failures.length) {
  console.error(JSON.stringify({ status: 'FAIL', failures }, null, 2));
  process.exit(1);
}
console.log(JSON.stringify({ status: 'PASS', hashes, matrix }, null, 2));
