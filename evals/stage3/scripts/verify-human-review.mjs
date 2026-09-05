import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const file = path.resolve(process.argv[2] || 'human-review-sample.json');
const outputFile = path.resolve(process.argv[3] || 'human-review-result.json');
const sample = JSON.parse(fs.readFileSync(file, 'utf8'));
if (!Array.isArray(sample.items) || sample.items.length !== sample.sampleSize || sample.sampleSize < Math.ceil(sample.population * 0.2)) {
  throw new Error('Human review sample size contract failed');
}
const tolerance = Number(sample.agreementTolerance ?? 0.2);
let agreements = 0;
for (const item of sample.items) {
  if (!Number.isFinite(item.humanScore) || item.humanScore < 0 || item.humanScore > 1) throw new Error(`Missing human score: ${item.caseId}`);
  if (!item.reviewer || !item.reviewedAt) throw new Error(`Missing reviewer evidence: ${item.caseId}`);
  const agrees = Math.abs(Number(item.modelScore) - Number(item.humanScore)) <= tolerance;
  if (agrees) agreements += 1;
}
const agreementRate = agreements / sample.items.length;
if (agreementRate < 0.8) throw new Error(`Human/grader agreement ${agreementRate} is below 0.8`);
const sampleSha256 = crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex').toUpperCase();
const result = {
  schemaVersion: 1,
  status: 'PASS',
  population: sample.population,
  reviewed: sample.items.length,
  agreementTolerance: tolerance,
  agreementRate,
  sampleSha256,
  caseIds: sample.items.map((item) => item.caseId),
  reviewerHashes: [...new Set(sample.items.map((item) => crypto.createHash('sha256').update(String(item.reviewer)).digest('hex').toUpperCase()))]
};
fs.writeFileSync(outputFile, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({ status: 'PASS', reviewed: sample.items.length, agreementRate, output: outputFile }));
