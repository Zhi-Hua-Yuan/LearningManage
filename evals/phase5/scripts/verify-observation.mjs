import fs from 'node:fs';
import path from 'node:path';

const file = path.resolve(process.argv[2] || 'observation.json');
const document = JSON.parse(fs.readFileSync(file, 'utf8'));
const days = Array.isArray(document.days) ? document.days : [];
const failures = [];
const fail = (message) => failures.push(message);

if (days.length !== 7) fail(`expected exactly 7 observation days, got ${days.length}`);
for (const [index, day] of days.entries()) {
  const label = day.date || `day-${index + 1}`;
  if (day.smokePass !== true) fail(`${label}: protected smoke did not pass`);
  if (Number(day.safetyIncidents || 0) !== 0) fail(`${label}: safety incident count is non-zero`);
  if (Number(day.formalBusinessWrites || 0) !== 0) fail(`${label}: formal business writes are non-zero`);
  if (Number(day.permissionLeaks || 0) !== 0) fail(`${label}: permission leaks are non-zero`);
  if (Number(day.successfulCalls || 0) > 0 && Number(day.missingTelemetry || 0) !== 0) {
    fail(`${label}: successful calls have missing telemetry`);
  }
  const failureRate = Number(day.failureRate);
  const baselineFailureRate = Number(day.baselineFailureRate);
  if (Number.isFinite(failureRate) && Number.isFinite(baselineFailureRate)
      && failureRate - baselineFailureRate > 0.05) {
    fail(`${label}: failure rate regressed by more than 5 percentage points`);
  }
  const p95 = Number(day.p95LatencyMs);
  const baselineP95 = Number(day.baselineP95LatencyMs);
  if (Number.isFinite(p95) && Number.isFinite(baselineP95) && baselineP95 > 0 && p95 > baselineP95 * 1.2) {
    fail(`${label}: P95 latency exceeded 1.2x Legacy baseline`);
  }
  const cost = Number(day.averageCost);
  const baselineCost = Number(day.baselineAverageCost);
  if (Number.isFinite(cost) && Number.isFinite(baselineCost) && baselineCost > 0 && cost > baselineCost * 1.2) {
    fail(`${label}: average cost exceeded 1.2x Legacy baseline`);
  }
}

if (failures.length) {
  console.error(JSON.stringify({ status: 'FAIL', failures }, null, 2));
  process.exit(1);
}
console.log(JSON.stringify({ status: 'PASS', observationDays: days.length }, null, 2));
