'use strict';

const fs = require('node:fs');
const path = require('node:path');

let tokenPromise;

function env(name, fallback) {
  const value = process.env[name];
  return value == null || value === '' ? fallback : value;
}

function apiBaseUrl() {
  return env('STAGE5_API_BASE_URL', 'http://127.0.0.1:18133/api').replace(/\/$/, '');
}

function fixture() {
  const file = path.resolve(env('STAGE5_RAG_FIXTURE', '.runtime-fixture.json'));
  if (!fs.existsSync(file)) throw new Error(`Stage 5 fixture does not exist: ${file}`);
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

async function login() {
  if (tokenPromise) return tokenPromise;
  tokenPromise = (async () => {
    const response = await fetch(`${apiBaseUrl()}/user/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        account: env('STAGE5_EVAL_ACCOUNT'),
        password: env('STAGE5_EVAL_PASSWORD'),
      }),
    });
    const body = await response.json();
    if (!response.ok || body?.code !== 0 || !body?.data?.token) {
      throw new Error(`Stage 5 login failed with HTTP ${response.status}`);
    }
    return body.data.token;
  })();
  return tokenPromise;
}

module.exports = class LearningManageRagProvider {
  id() {
    return 'learning-manage-stage5-rag';
  }

  async callApi(_prompt, context) {
    const vars = context?.vars || {};
    const runtime = fixture();
    const expectedSourceId = runtime.sources?.[String(vars.sourceAlias)];
    if (!expectedSourceId) throw new Error(`Missing source alias ${vars.sourceAlias}`);
    const token = await login();
    const response = await fetch(`${apiBaseUrl()}/ai/rag/ask`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'X-Trace-Id': `s5_${String(vars.caseId).replace(/[^A-Za-z0-9_-]/g, '_')}`,
      },
      body: JSON.stringify({ question: vars.question, projectId: runtime.projectId }),
    });
    const body = await response.json();
    const envelope = {
      caseId: vars.caseId,
      success: response.ok && body?.code === 0,
      errorCode: body?.code ?? null,
      data: body?.data ?? null,
      expectedSourceId: String(expectedSourceId),
      expectedTitle: vars.expectedTitle,
      split: vars.split,
    };
    return { output: JSON.stringify(envelope) };
  }
};
