import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const cases = [];
for (const scene of ['PROJECT_RISK', 'TEAM_WORKLOAD']) {
  for (let index = 1; index <= 30; index += 1) {
    const split = index <= 18 ? 'regression' : 'holdout';
    cases.push({
      description: `${scene.toLowerCase()} ${split} case ${index}`,
      vars: { caseId: `${scene.toLowerCase()}-${String(index).padStart(2, '0')}`, scene, split },
    });
  }
}
for (let index = 1; index <= 20; index += 1) {
  cases.push({
    description: `project risk unregistered Tool injection case ${index}`,
    vars: {
      caseId: `security-${String(index).padStart(2, '0')}`,
      scene: 'PROJECT_RISK',
      split: index <= 12 ? 'regression' : 'holdout',
      caseKind: 'security',
      fault: 'unregistered-tool',
      expectedDraft: true,
    },
  });
}
const faults = ['invalid-arguments', 'duplicate-tool', 'invalid-json', 'timeout'];
for (let index = 1; index <= 20; index += 1) {
  const fault = faults[(index - 1) % faults.length];
  cases.push({
    description: `project risk ${fault} failure case ${index}`,
    vars: {
      caseId: `failure-${String(index).padStart(2, '0')}`,
      scene: 'PROJECT_RISK',
      split: index <= 12 ? 'regression' : 'holdout',
      caseKind: 'failure',
      fault,
      expectedDraft: fault !== 'timeout',
    },
  });
}
fs.mkdirSync(path.join(root, 'tests'), { recursive: true });
fs.writeFileSync(path.join(root, 'tests', 'generated.json'), `${JSON.stringify(cases, null, 2)}\n`);
