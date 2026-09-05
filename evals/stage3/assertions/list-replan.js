'use strict';

module.exports = function listReplanAssertion(output, context) {
  const envelope = JSON.parse(output);
  if (!envelope.success) return { pass: false, score: 0, reason: 'List replan request failed' };
  const items = envelope.data?.previewTasks || envelope.data?.items || [];
  const allowed = new Set(JSON.parse(context.vars.allowedResourceIds || '[]').map(Number));
  const tags = new Set(JSON.parse(context.vars.tags || '[]'));
  const seen = new Set();
  if (!Array.isArray(items)) return { pass: false, score: 0, reason: 'Replan items are missing' };
  if (tags.has('completed-id') && items.length !== 0) {
    return { pass: false, score: 0, reason: 'Completed task ID was not filtered' };
  }
  for (const item of items) {
    const id = Number(item.taskId);
    if (!allowed.has(id) || seen.has(id)) return { pass: false, score: 0, reason: 'Replan contains extra or duplicate task IDs' };
    seen.add(id);
    if (!Number.isInteger(item.newPriority) || item.newPriority < 0 || item.newPriority > 3) {
      return { pass: false, score: 0, reason: 'Replan priority is outside 0-3' };
    }
    if (typeof item.newTitle !== 'string' || !item.newTitle.trim() || [...item.newTitle].length > 60) {
      return { pass: false, score: 0, reason: 'Replan title is blank or too long' };
    }
    if (typeof item.reason !== 'string' || !item.reason.trim()) {
      return { pass: false, score: 0, reason: 'Replan reason is blank' };
    }
    for (const key of ['oldDueDate', 'newDueDate']) {
      if (item[key] != null && !/^\d{4}-\d{2}-\d{2}$/.test(item[key])) {
        return { pass: false, score: 0, reason: `${key} is not an ISO date` };
      }
    }
    const dueChanged = item.oldDueDate !== item.newDueDate;
    if (Boolean(item.dueChanged) !== dueChanged) {
      return { pass: false, score: 0, reason: 'dueChanged is inconsistent with old and new dates' };
    }
    if (!dueChanged && Number(item.dueDeltaDays) !== 0) {
      return { pass: false, score: 0, reason: 'Unchanged due date has a non-zero delta' };
    }
  }
  return { pass: true, score: 1, reason: 'List replan scope and field constraints passed' };
};
