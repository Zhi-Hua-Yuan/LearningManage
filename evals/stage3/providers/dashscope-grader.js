'use strict';

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
    const baseUrl = (process.env.STAGE3_DASHSCOPE_BASE_URL || 'https://dashscope.aliyuncs.com/compatible-mode/v1').replace(/\/$/, '');
    const response = await fetch(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({ model, temperature: 0, messages: [{ role: 'user', content: prompt }] })
    });
    const body = await response.json();
    if (!response.ok) throw new Error(`Grader request failed with HTTP ${response.status}`);
    const output = body?.choices?.[0]?.message?.content;
    if (typeof output !== 'string' || !output.trim()) throw new Error('Grader returned no content');
    const inputPrice = Number(process.env.STAGE3_GRADER_INPUT_PRICE_CNY_PER_MILLION);
    const outputPrice = Number(process.env.STAGE3_GRADER_OUTPUT_PRICE_CNY_PER_MILLION);
    const priceVersion = process.env.STAGE3_GRADER_PRICE_VERSION;
    if (!Number.isFinite(inputPrice) || inputPrice < 0 || !Number.isFinite(outputPrice) || outputPrice < 0 || !priceVersion) {
      throw new Error('Stage 3 grader pricing and price version must be configured');
    }
    const estimatedCost = body.usage
      ? (Number(body.usage.prompt_tokens || 0) * inputPrice + Number(body.usage.completion_tokens || 0) * outputPrice) / 1_000_000
      : undefined;
    return {
      output,
      tokenUsage: body.usage ? {
        prompt: body.usage.prompt_tokens,
        completion: body.usage.completion_tokens,
        total: body.usage.total_tokens
      } : undefined,
      cost: estimatedCost,
      metadata: { model, priceVersion, providerRequestIdHash: body.id ? require('node:crypto').createHash('sha256').update(String(body.id)).digest('hex').toUpperCase() : null }
    };
  }
};
