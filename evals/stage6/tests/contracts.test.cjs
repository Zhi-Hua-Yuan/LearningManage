'use strict';

const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '..');

test('generated matrix freezes 60 quality and 40 security/failure cases', () => {
  const cases = JSON.parse(fs.readFileSync(path.join(root, 'tests', 'generated.json'), 'utf8'));
  assert.equal(cases.length, 100);
  assert.equal(cases.filter((value) => value.vars.scene === 'PROJECT_RISK').length, 70);
  assert.equal(cases.filter((value) => value.vars.scene === 'TEAM_WORKLOAD').length, 30);
  assert.equal(cases.filter((value) => value.vars.caseKind === 'security').length, 20);
  assert.equal(cases.filter((value) => value.vars.caseKind === 'failure').length, 20);
  assert.equal(new Set(cases.map((value) => value.vars.caseId)).size, 100);
});

test('provider only submits analysis and polls status', () => {
  const source = fs.readFileSync(path.join(root, 'providers', 'learning-manage-agent-http.js'), 'utf8');
  assert.match(source, /\/ai\/agent\/project-risk/);
  assert.match(source, /\/ai\/agent\/team-workload/);
  assert.match(source, /\/ai\/agent\/run\//);
  assert.doesNotMatch(source, /\/confirm/);
  assert.doesNotMatch(source, /\/delete/);
});
