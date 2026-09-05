'use strict';

function result(pass, score, reason) {
  return { pass, score: pass ? score : 0, reason };
}

module.exports = function commonAssertion(output, context) {
  let envelope;
  try {
    envelope = JSON.parse(output);
  } catch (error) {
    return result(false, 0, `Provider output is not JSON: ${error.message}`);
  }

  const vars = context?.vars || {};
  if (envelope.caseId !== vars.caseId || envelope.scene !== vars.scene) {
    return result(false, 0, 'Provider result is not bound to the requested case and scene');
  }
  if (typeof envelope.success !== 'boolean') {
    return result(false, 0, 'Provider result is missing boolean success');
  }
  if (!envelope.meta || typeof envelope.meta !== 'object') {
    return result(false, 0, 'Provider result is missing metadata');
  }
  if (typeof envelope.meta.traceId !== 'string' || !/^[A-Za-z0-9_-]{8,64}$/.test(envelope.meta.traceId)) {
    return result(false, 0, 'Trace ID is malformed');
  }
  if (envelope.meta.callLogFound !== true || envelope.meta.callLogId == null) {
    return result(false, 0, 'Trace ID is not correlated to a persisted AI call log');
  }
  if (envelope.success && envelope.data == null) {
    return result(false, 0, 'Successful response has no data');
  }
  if (envelope.meta.formalBusinessWrites !== 0) {
    return result(false, 0, 'Evaluation preview changed formal project, milestone, or task data');
  }
  const tags = JSON.parse(vars.tags || '[]');
  if (tags.includes('missing-usage')) {
    const usageWasInvented = envelope.meta.promptTokens != null
      || envelope.meta.completionTokens != null
      || envelope.meta.totalTokens != null
      || envelope.meta.estimatedCost != null;
    if (usageWasInvented) return result(false, 0, 'Missing provider usage was incorrectly converted to zero or estimated values');
  }

  const expected = JSON.parse(vars.expectedInvariants || '{}');
  if (expected.expectSuccess === false && ['task-breakdown', 'weekly-polish'].includes(envelope.scene)
      && Number(envelope.meta.aiDraftWrites || 0) !== 0) {
    return result(false, 0, 'A failed non-degrading scene created an AI draft');
  }
  if (expected.expectSuccess === true && !envelope.success) {
    return result(false, 0, `Expected success but received ${envelope.error?.message || 'an error'}`);
  }
  if (expected.expectSuccess === false && envelope.success) {
    return result(false, 0, 'Expected a safe failure but the application returned success');
  }
  if (expected.expectDegraded === true && envelope.meta.degraded !== true) {
    return result(false, 0, 'Expected deterministic degradation was not recorded');
  }
  if (expected.expectNoDegradation === true && envelope.meta.degraded === true) {
    return result(false, 0, 'Unexpected degradation was recorded');
  }
  return result(true, 1, 'Common provider and execution invariants passed');
};
