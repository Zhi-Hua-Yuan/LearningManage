import fs from 'node:fs';

const [legacyFile, springAiFile] = process.argv.slice(2);
if (!legacyFile || !springAiFile) {
  console.error('usage: node scripts/compare-adapters.mjs <legacy-output.json> <spring-ai-output.json>');
  process.exit(2);
}

const volatileKeys = new Set([
  'traceId', 'callLogId', 'providerRequestIdHash', 'latencyMs',
  'draftId', 'expireAt', 'createdAt', 'updatedAt', 'generatedAt', 'operationId'
]);

function canonical(value, key = '') {
  if (volatileKeys.has(key)) return '<volatile>';
  if (Array.isArray(value)) return value.map((item) => canonical(item));
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map((name) => [name, canonical(value[name], name)]));
  }
  return value;
}

function readRecords(file) {
  const document = JSON.parse(fs.readFileSync(file, 'utf8'));
  const results = document?.results?.results;
  if (!Array.isArray(results) || results.length === 0) {
    throw new Error(`${file}: promptfoo results are empty`);
  }
  const records = results.map((result) => {
    const vars = result.vars ?? {};
    const response = result.response ?? {};
    const metadata = response.metadata ?? {};
    if (metadata.failureType == null
      && (typeof metadata.providerRequestIdHash !== 'string' || metadata.providerRequestIdHash.length === 0)) {
      throw new Error(`${file}: ${vars.caseId ?? 'unknown-case'} has no audited provider request ID`);
    }
    let envelope;
    try {
      envelope = JSON.parse(response.output ?? '{}');
    } catch (error) {
      throw new Error(`${file}: ${vars.caseId ?? 'unknown-case'} output is not JSON: ${error.message}`);
    }
    return {
      caseId: vars.caseId,
      scene: vars.scene,
      promptCode: metadata.promptCode,
      promptVersion: metadata.promptVersion,
      promptContentHash: metadata.promptContentHash,
      requestedModel: metadata.requestedModel,
      actualModel: metadata.actualModel,
      finishReason: metadata.finishReason,
      usage: {
        promptTokens: metadata.promptTokens,
        completionTokens: metadata.completionTokens,
        totalTokens: metadata.totalTokens
      },
      toolCalls: metadata.toolCalls ?? envelope.toolCalls ?? envelope.data?.toolCalls ?? [],
      content: canonical(envelope),
      degraded: metadata.degraded,
      failureType: metadata.failureType,
      fallbackUsed: metadata.fallbackUsed
    };
  });
  records.sort((left, right) => String(left.caseId).localeCompare(String(right.caseId)));
  const promptCodes = new Set(records.map((record) => record.promptCode));
  const scenes = new Set(records.map((record) => record.scene));
  if (promptCodes.size !== 6 || scenes.size !== 5) {
    throw new Error(`${file}: expected six prompt codes/five scenes, got ${promptCodes.size}/${scenes.size}`);
  }
  return records;
}

const legacy = readRecords(legacyFile);
const springAi = readRecords(springAiFile);
const left = JSON.stringify(legacy);
const right = JSON.stringify(springAi);
if (left !== right) {
  console.error('Legacy and Spring AI normalized outputs differ');
  const firstDifference = [...left].findIndex((character, index) => character !== right[index]);
  console.error(`first difference at character ${firstDifference}`);
  process.exit(1);
}

console.log(`adapter parity PASS: ${legacy.length} cases, 5 scenes, 6 prompt codes`);
