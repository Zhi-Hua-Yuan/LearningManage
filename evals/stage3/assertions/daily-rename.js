'use strict';

module.exports = function dailyRenameAssertion(output, context) {
  const envelope = JSON.parse(output);
  if (!envelope.success) return { pass: false, score: 0, reason: 'Daily rename request failed' };
  const items = envelope.data?.items || envelope.data?.suggestions || [];
  const allowed = new Set(JSON.parse(context.vars.allowedResourceIds || '[]').map(Number));
  const tags = new Set(JSON.parse(context.vars.tags || '[]'));
  const payload = JSON.parse(context.vars.requestPayload || '{}');
  const maxEdits = payload.maxEdits ?? allowed.size;
  const seen = new Set();
  if (!Array.isArray(items) || items.length > maxEdits) return { pass: false, score: 0, reason: 'Rename count exceeds maxEdits' };
  if (tags.has('unauthorized-id') && items.length !== 0) {
    return { pass: false, score: 0, reason: 'Unauthorized task ID was not filtered' };
  }
  for (const item of items) {
    const id = Number(item.taskId);
    if (!allowed.has(id) || seen.has(id)) return { pass: false, score: 0, reason: 'Rename contains extra or duplicate task IDs' };
    seen.add(id);
    if (typeof item.newTitle !== 'string' || !item.newTitle.trim() || [...item.newTitle].length > 60) {
      return { pass: false, score: 0, reason: 'Renamed title is blank or too long' };
    }
  }
  return { pass: true, score: 1, reason: 'Daily rename scope and title constraints passed' };
};
