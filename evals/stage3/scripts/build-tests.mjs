import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const semanticContexts = JSON.parse(fs.readFileSync(path.join(root, 'fixtures', 'semantic-context.json'), 'utf8'));
const requestedSplits = (process.env.STAGE3_EVAL_SPLIT || 'regression').split(',').map((value) => value.trim()).filter(Boolean);
if (process.env.STAGE3_INCLUDE_FAILURES === 'true' && !requestedSplits.includes('failure-injection')) requestedSplits.push('failure-injection');
const allowedSplits = new Set(['development', 'regression', 'holdout', 'failure-injection']);
for (const split of requestedSplits) if (!allowedSplits.has(split)) throw new Error(`Unknown Stage 3 split: ${split}`);

const assertionByScene = {
  'task-breakdown': 'task-breakdown.js',
  'weekly-polish': 'weekly-polish.js',
  'today-order': 'today-order.js',
  'daily-rename': 'daily-rename.js',
  'list-replan': 'list-replan.js'
};

function semanticFacts(item) {
  if (item.scene === 'task-breakdown') return { request: item.requestPayload };
  const fixture = semanticContexts[item.fixtureKey];
  if (!fixture) return { request: item.requestPayload, fixtureKey: item.fixtureKey };
  const selectedIds = Array.isArray(item.requestPayload.taskIds)
    ? new Set(item.requestPayload.taskIds.map(Number))
    : null;
  return {
    request: item.requestPayload,
    actor: item.actor,
    fixture: {
      asOfDate: fixture.asOfDate,
      project: fixture.project,
      tasks: selectedIds ? fixture.tasks.filter((task) => selectedIds.has(Number(task.taskId))) : fixture.tasks
    }
  };
}

const tests = [];
for (const split of requestedSplits) {
  const file = path.join(root, 'datasets', `${split}.jsonl`);
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/).filter(Boolean)) {
    const item = JSON.parse(line);
    const assertions = [];
    if (item.expectedInvariants.expectSuccess) {
      assertions.push({ type: 'javascript', value: `file://assertions/${assertionByScene[item.scene]}`, metric: `${item.scene}_business_contract` });
    }
    if (process.env.STAGE3_ENABLE_MODEL_GRADING === 'true' && item.split !== 'failure-injection') {
      const facts = semanticFacts(item);
      assertions.push({
        type: 'llm-rubric',
        provider: 'file://providers/dashscope-grader.js',
        // Collect every valid semantic score. Candidate quality is gated after
        // the immutable three regression plus three holdout rounds are aggregated.
        threshold: 0,
        metric: `${item.scene}_semantic_quality`,
        value: [
          'Evaluate only the data field of the provider JSON output.',
          'Score every rubric dimension from 0 to 1, explain each dimension, and use the lowest dimension as the final score.',
          `Complete synthetic input facts: ${JSON.stringify(facts)}`,
          ...item.semanticRubric.map((rubric, index) => `Dimension ${index + 1}: ${rubric}`)
        ].join('\n')
      });
    }
    tests.push({
      description: `${item.caseId} ${item.scene}`,
      vars: {
        caseId: item.caseId,
        scene: item.scene,
        split: item.split,
        tags: JSON.stringify(item.tags),
        actor: item.actor,
        fixtureKey: item.fixtureKey,
        requestPayload: JSON.stringify(item.requestPayload),
        expectedInvariants: JSON.stringify(item.expectedInvariants),
        allowedResourceIds: JSON.stringify(item.allowedResourceIds),
        semanticRubric: JSON.stringify(item.semanticRubric),
        semanticFacts: JSON.stringify(semanticFacts(item)),
        datasetVersion: item.datasetVersion,
        labelVersion: String(item.labelVersion)
      },
      metadata: { caseId: item.caseId, scene: item.scene, split: item.split, tags: item.tags },
      assert: assertions
    });
  }
}

const outputDir = path.join(root, 'tests');
fs.mkdirSync(outputDir, { recursive: true });
fs.writeFileSync(path.join(outputDir, 'generated.json'), `${JSON.stringify(tests, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({ status: 'PASS', splits: requestedSplits, tests: tests.length }));
