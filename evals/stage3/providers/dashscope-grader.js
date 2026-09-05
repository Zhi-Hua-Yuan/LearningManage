'use strict';

const crypto = require('node:crypto');

const RETRYABLE_STATUS = new Set([429, 500, 502, 503, 504]);

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function requestWithRetry(url, options) {
  const maxAttempts = Number(process.env.STAGE3_GRADER_MAX_ATTEMPTS || 3);
  const timeoutMs = Number(process.env.STAGE3_GRADER_TIMEOUT_MS || 60000);
  if (!Number.isInteger(maxAttempts) || maxAttempts < 1 || maxAttempts > 5) {
    throw new Error('STAGE3_GRADER_MAX_ATTEMPTS must be 1-5');
  }
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1000 || timeoutMs > 120000) {
    throw new Error('STAGE3_GRADER_TIMEOUT_MS must be 1000-120000');
  }
  let lastError;
  let unmeteredRetryAttempts = 0;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      const response = await fetch(url, { ...options, signal: AbortSignal.timeout(timeoutMs) });
      if (response.ok || !RETRYABLE_STATUS.has(response.status) || attempt === maxAttempts) {
        return { response, unmeteredRetryAttempts };
      }
      lastError = new Error(`Grader request failed with retryable HTTP ${response.status}`);
      unmeteredRetryAttempts += 1;
      try {
        await response.body?.cancel();
      } catch {
        // Releasing the failed response is best effort; the bounded retry still proceeds.
      }
    } catch (error) {
      lastError = error;
      if (attempt === maxAttempts) throw error;
      unmeteredRetryAttempts += 1;
    }
    await sleep(250 * (2 ** (attempt - 1)));
  }
  throw lastError || new Error('Grader request failed');
}

module.exports = class DashscopeGraderProvider {
  id() {
    return `dashscope-grader:${process.env.STAGE3_GRADER_MODEL || 'qwen-max'}`;
  }

  async callApi(prompt) {
    const apiKey = process.env.ALIYUN_API_KEY;
    if (!apiKey) throw new Error('ALIYUN_API_KEY is required for semantic grading');
    const model = process.env.STAGE3_GRADER_MODEL || 'qwen-max';
    const sutModel = process.env.STAGE3_SUT_MODEL || 'qwen-plus';
    if (model === sutModel) throw new Error('The grader model must differ from the system-under-test model');
    const inputPrice = Number(process.env.STAGE3_GRADER_INPUT_PRICE_CNY_PER_MILLION);
    const outputPrice = Number(process.env.STAGE3_GRADER_OUTPUT_PRICE_CNY_PER_MILLION);
    const priceVersion = process.env.STAGE3_GRADER_PRICE_VERSION;
    const maxCompletionTokens = Number(process.env.STAGE3_GRADER_MAX_COMPLETION_TOKENS || 1024);
    if (!Number.isFinite(inputPrice) || inputPrice < 0 || !Number.isFinite(outputPrice) || outputPrice < 0 || !priceVersion) {
      throw new Error('Stage 3 grader pricing and price version must be configured');
    }
    if (!Number.isInteger(maxCompletionTokens) || maxCompletionTokens < 64 || maxCompletionTokens > 4096) {
      throw new Error('STAGE3_GRADER_MAX_COMPLETION_TOKENS must be 64-4096');
    }
    const baseUrl = (process.env.STAGE3_DASHSCOPE_BASE_URL || 'https://dashscope.aliyuncs.com/compatible-mode/v1').replace(/\/$/, '');
    const { response, unmeteredRetryAttempts } = await requestWithRetry(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({ model, temperature: 0, max_tokens: maxCompletionTokens, messages: [{ role: 'user', content: prompt }] })
    });
    const body = await response.json();
    if (!response.ok) throw new Error(`Grader request failed with HTTP ${response.status}`);
    const output = body?.choices?.[0]?.message?.content;
    if (typeof output !== 'string' || !output.trim()) throw new Error('Grader returned no content');
    const usageEstimatedCost = body.usage
      ? (Number(body.usage.prompt_tokens || 0) * inputPrice + Number(body.usage.completion_tokens || 0) * outputPrice) / 1_000_000
      : undefined;
    // A timed-out/retryable attempt may still be billable even when no Usage reaches the client.
    // UTF-8 byte length plus protocol headroom is a conservative ceiling for input tokens;
    // max_tokens provides a hard output ceiling for every attempt.
    const maximumAttemptCost = ((Buffer.byteLength(prompt, 'utf8') + 256) * inputPrice
      + maxCompletionTokens * outputPrice) / 1_000_000;
    const unmeteredAttempts = unmeteredRetryAttempts + (body.usage ? 0 : 1);
    const retryCostReserve = unmeteredAttempts * maximumAttemptCost;
    const accountedCost = (usageEstimatedCost || 0) + retryCostReserve;
    return {
      output,
      tokenUsage: body.usage ? {
        prompt: body.usage.prompt_tokens,
        completion: body.usage.completion_tokens,
        total: body.usage.total_tokens
      } : undefined,
      cost: accountedCost,
      metadata: {
        model,
        priceVersion,
        providerRequestIdHash: body.id ? crypto.createHash('sha256').update(String(body.id)).digest('hex').toUpperCase() : null,
        usageEstimatedCost: usageEstimatedCost ?? null,
        unmeteredRetryAttempts: unmeteredAttempts,
        retryCostReserve,
        maximumAttemptCost
      }
    };
  }
};
