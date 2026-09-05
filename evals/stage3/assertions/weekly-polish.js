'use strict';

module.exports = function weeklyPolishAssertion(output) {
  const envelope = JSON.parse(output);
  if (!envelope.success) return { pass: false, score: 0, reason: 'Weekly polish request failed' };
  const review = envelope.data?.polishedText ?? envelope.data?.review ?? envelope.data;
  if (typeof review !== 'string') return { pass: false, score: 0, reason: 'Weekly review text is missing' };
  const length = [...review.trim()].length;
  if (length < 100 || length > 220) {
    return { pass: false, score: 0, reason: `Weekly review length ${length} is outside 100-220` };
  }
  return { pass: true, score: 1, reason: 'Weekly review deterministic constraints passed' };
};
