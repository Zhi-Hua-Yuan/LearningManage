'use strict';

function result(pass, reason) {
  return { pass, score: pass ? 1 : 0, reason };
}

module.exports = function ragContract(output, context) {
  let envelope;
  try {
    envelope = JSON.parse(output);
  } catch (error) {
    return result(false, `Provider output is not JSON: ${error.message}`);
  }
  if (envelope.caseId !== context?.vars?.caseId || !envelope.success) {
    return result(false, `RAG API failed or returned another case: ${envelope.errorCode}`);
  }
  const data = envelope.data;
  if (!data || data.status !== 'ACTIVE' || data.insufficientEvidence !== false) {
    return result(false, 'Expected one active evidence-backed answer');
  }
  if (typeof data.answer !== 'string' || !Array.isArray(data.sources)) {
    return result(false, 'RAG response shape is invalid');
  }
  if (data.sources.length < 1 || data.sources.length > 8) {
    return result(false, 'RAG source count is outside the frozen contract');
  }
  const firstFive = data.sources.slice(0, 5);
  if (!firstFive.some((source) => String(source.sourceId) === envelope.expectedSourceId)) {
    return result(false, 'Expected source was not present in cited Hit@5');
  }
  const citationIds = data.sources.map((source) => source.citationId);
  if (new Set(citationIds).size !== citationIds.length) {
    return result(false, 'Citation IDs are duplicated');
  }
  for (const citationId of citationIds) {
    if (!new RegExp(`\\[${citationId}\\]`).test(data.answer)) {
      return result(false, `Answer is missing marker [${citationId}]`);
    }
  }
  if (!data.sources.some((source) => source.title === envelope.expectedTitle)) {
    return result(false, 'Expected source title does not match the live citation');
  }
  return result(true, 'Expected source and citation contract passed');
};
