'use strict';

module.exports = function todayOrderAssertion(output, context) {
  const envelope = JSON.parse(output);
  if (!envelope.success) return { pass: false, score: 0, reason: 'Today-order request failed' };
  const data = envelope.data || {};
  const items = data.items || data.tasks || [];
  const allowed = new Set(JSON.parse(context.vars.allowedResourceIds || '[]').map(Number));
  const seen = new Set();
  if (!Array.isArray(items)) return { pass: false, score: 0, reason: 'Order items are missing' };
  for (const item of items) {
    const id = Number(item.taskId);
    if (!allowed.has(id) || seen.has(id)) return { pass: false, score: 0, reason: 'Order contains extra or duplicate task IDs' };
    seen.add(id);
    for (const key of ['difficulty', 'cost', 'benefit']) {
      if (!Number.isInteger(item[key]) || item[key] < 1 || item[key] > 5) {
        return { pass: false, score: 0, reason: `${key} is outside 1-5` };
      }
    }
    if (!Number.isInteger(item.estimatedMinutes) || item.estimatedMinutes < 10 || item.estimatedMinutes > 240) {
      return { pass: false, score: 0, reason: 'estimatedMinutes is outside 10-240' };
    }
  }
  if (seen.size !== allowed.size) return { pass: false, score: 0, reason: 'Not every requested task ID was returned' };
  return { pass: true, score: 1, reason: 'Today-order IDs and ranges passed' };
};
