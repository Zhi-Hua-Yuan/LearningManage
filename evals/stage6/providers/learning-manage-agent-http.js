'use strict';

const fs = require('node:fs');
const path = require('node:path');

let tokenPromise;

function env(name, fallback) {
  const value = process.env[name];
  return value == null || value === '' ? fallback : value;
}

function apiBaseUrl() {
  return env('STAGE6_API_BASE_URL', 'http://127.0.0.1:18133/api').replace(/\/$/, '');
}

function fixture() {
  const file = path.resolve(env('STAGE6_AGENT_FIXTURE', '.runtime-fixture.json'));
  return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : {};
}

function targetId(vars) {
  const runtime = fixture();
  if (vars.scene === 'TEAM_WORKLOAD') return String(runtime.teamId || env('STAGE6_TEAM_ID', '')).trim();
  if (vars.caseKind === 'security') return String(runtime.securityProjectId || env('STAGE6_SECURITY_PROJECT_ID', '')).trim();
  if (vars.caseKind === 'failure') {
    return String(runtime.failureProjectIds?.[vars.fault] || env('STAGE6_FAILURE_PROJECT_ID', '')).trim();
  }
  return String(runtime.projectId || env('STAGE6_PROJECT_ID', '')).trim();
}

async function login() {
  if (tokenPromise) return tokenPromise;
  tokenPromise = (async () => {
    const response = await fetch(`${apiBaseUrl()}/user/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        account: env('STAGE6_EVAL_ACCOUNT'),
        password: env('STAGE6_EVAL_PASSWORD'),
      }),
    });
    const body = await response.json();
    if (!response.ok || body?.code !== 0 || !body?.data?.token) {
      throw new Error(`Stage 6 login failed with HTTP ${response.status}`);
    }
    return body.data.token;
  })();
  return tokenPromise;
}

async function jsonRequest(path, token, options = {}) {
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...(options.headers || {}) },
  });
  return { response, body: await response.json() };
}

async function waitForRun(runId, token) {
  const deadline = Date.now() + Number(env('STAGE6_AGENT_TIMEOUT_MS', '70000'));
  while (Date.now() < deadline) {
    const { body } = await jsonRequest(`/ai/agent/run/${encodeURIComponent(runId)}`, token);
    if (body?.code !== 0) return { success: false, errorCode: body?.code, data: body?.data };
    if (['SUCCEEDED', 'PARTIAL', 'FAILED', 'TIMED_OUT', 'CANCELED'].includes(body.data?.status)) {
      return { success: true, errorCode: 0, data: body.data };
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  return { success: false, errorCode: 'EVAL_TIMEOUT', data: null };
}

module.exports = class LearningManageAgentProvider {
  id() { return 'learning-manage-stage6-agent'; }

  async callApi(_prompt, context) {
    const vars = context?.vars || {};
    const token = await login();
    const project = vars.scene === 'PROJECT_RISK';
    const resourceId = targetId(vars);
    if (!/^\d+$/.test(resourceId) || resourceId === '0') {
      throw new Error(`Missing valid Stage 6 fixture target for ${vars.caseId}`);
    }
    const path = project ? '/ai/agent/project-risk' : '/ai/agent/team-workload';
    const request = project
      ? { projectId: resourceId, clientRequestId: `eval-${vars.caseId}` }
      : { teamId: resourceId, clientRequestId: `eval-${vars.caseId}` };
    const submitted = await jsonRequest(path, token, { method: 'POST', body: JSON.stringify(request) });
    if (!submitted.response.ok || submitted.body?.code !== 0 || !submitted.body?.data?.runId) {
      return { output: JSON.stringify({ caseId: vars.caseId, scene: vars.scene,
        success: false, errorCode: submitted.body?.code ?? submitted.response.status, data: null }) };
    }
    const terminal = await waitForRun(submitted.body.data.runId, token);
    return { output: JSON.stringify({ caseId: vars.caseId, scene: vars.scene,
      caseKind: vars.caseKind || 'quality', fault: vars.fault || null,
      expectedDraft: vars.expectedDraft !== false, ...terminal }) };
  }
};
