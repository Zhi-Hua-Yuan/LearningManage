'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');

test('generated evaluation set has frozen regression and holdout counts', () => {
  const cases = JSON.parse(fs.readFileSync('tests/generated.json', 'utf8'));
  assert.equal(cases.length, 50);
  assert.equal(cases.filter((item) => item.vars.split === 'regression').length, 30);
  assert.equal(cases.filter((item) => item.vars.split === 'holdout').length, 20);
  assert.equal(new Set(cases.map((item) => item.vars.caseId)).size, 50);
  assert.equal(new Set(cases.map((item) => item.vars.expectedMarker)).size, 50);
  for (const item of cases) {
    assert.match(item.vars.question, /EVIDENCE-\d{3}/);
    assert.equal(item.vars.datasetVersion, '1.0.0');
    assert.equal(item.vars.labelVersion, '1');
  }
});
