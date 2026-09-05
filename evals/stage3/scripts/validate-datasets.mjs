import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const files = ['development.jsonl', 'regression.jsonl', 'holdout.jsonl', 'failure-injection.jsonl'];
const schema = JSON.parse(fs.readFileSync(path.join(root, 'schemas', 'eval-case.schema.json'), 'utf8'));
const ajv = new Ajv2020({ allErrors: true, strict: false });
const validate = ajv.compile(schema);
const cases = [];
const hashes = {};

for (const file of files) {
  const filePath = path.join(root, 'datasets', file);
  if (!fs.existsSync(filePath)) throw new Error(`Dataset is missing: ${file}`);
  const content = fs.readFileSync(filePath, 'utf8');
  hashes[file] = crypto.createHash('sha256').update(content).digest('hex').toUpperCase();
  const lines = content.split(/\r?\n/).filter(Boolean);
  for (const [index, line] of lines.entries()) {
    let item;
    try { item = JSON.parse(line); } catch (error) { throw new Error(`${file}:${index + 1} is invalid JSON: ${error.message}`); }
    if (!validate(item)) throw new Error(`${file}:${index + 1} violates schema: ${ajv.errorsText(validate.errors)}`);
    cases.push(item);
  }
}

const ids = new Set();
for (const item of cases) {
  if (ids.has(item.caseId)) throw new Error(`Duplicate caseId: ${item.caseId}`);
  ids.add(item.caseId);
  if (item.split === 'failure-injection' && !item.tags.includes('failure-injection')) {
    throw new Error(`Failure case lacks failure-injection tag: ${item.caseId}`);
  }
}

const expected = { development: 102, regression: 34, holdout: 34, 'failure-injection': 40 };
const actual = Object.fromEntries(Object.keys(expected).map((split) => [split, cases.filter((item) => item.split === split).length]));
for (const [split, count] of Object.entries(expected)) {
  if (actual[split] !== count) throw new Error(`Expected ${count} ${split} cases, found ${actual[split]}`);
}

const sceneExpected = { 'task-breakdown': 60, 'weekly-polish': 30, 'today-order': 30, 'daily-rename': 25, 'list-replan': 25 };
for (const [scene, count] of Object.entries(sceneExpected)) {
  const found = cases.filter((item) => item.scene === scene && item.split !== 'failure-injection').length;
  if (found !== count) throw new Error(`Expected ${count} quality cases for ${scene}, found ${found}`);
}

console.log(JSON.stringify({ status: 'PASS', total: cases.length, quality: 170, failures: 40, splits: actual, hashes }, null, 2));
