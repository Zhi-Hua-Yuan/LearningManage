import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runtimeFile = path.join(root, '.runtime-fixture.json');
const baseUrl = (process.env.STAGE6_API_BASE_URL || 'http://127.0.0.1:18133/api').replace(/\/$/, '');
const account = process.env.STAGE6_EVAL_ACCOUNT;
const password = process.env.STAGE6_EVAL_PASSWORD;
if (!account || !password) throw new Error('STAGE6_EVAL_ACCOUNT and STAGE6_EVAL_PASSWORD are required');

async function request(endpoint, options = {}) {
  const response = await fetch(`${baseUrl}${endpoint}`, options);
  const body = await response.json();
  if (!response.ok || body?.code !== 0) throw new Error(`Stage 6 fixture request failed: ${endpoint}; code=${body?.code}`);
  return body.data;
}

async function login() {
  try {
    return (await request('/user/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ account, password }),
    })).token;
  } catch (error) {
    if (process.env.STAGE6_EVAL_ALLOW_REGISTER !== 'true') throw error;
    await request('/user/register', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ account, username: 'Stage6 Agent Eval', password, confirmPassword: password }),
    });
    return (await request('/user/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ account, password }),
    })).token;
  }
}

const token = await login();
const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
const suffix = new Date().toISOString();
const me = await request('/user/me', { headers });

async function createProject(name, teamId = null) {
  const endpoint = teamId ? '/project/team/create' : '/project/add';
  const data = await request(endpoint, {
    method: 'POST', headers,
    body: JSON.stringify({ teamId: teamId || undefined, name: `${name} ${suffix}`,
      goal: 'Deterministic controlled Agent evaluation fixture', startDate: '2026-09-01', endDate: '2026-12-31' }),
  });
  return String(typeof data === 'object' ? data.id || data.projectId : data);
}

async function createTask(projectId, title, dueDate, assigneeUserId = undefined) {
  await request('/task/add', {
    method: 'POST', headers,
    body: JSON.stringify({ projectId, title, description: 'Stage 6 deterministic evaluation fixture',
      assigneeUserId, priority: 3, dueDate }),
  });
}

const projectId = await createProject('Stage6 quality project');
await createTask(projectId, '处理项目逾期风险', '2026-09-01', String(me.id));
await createTask(projectId, '完成近期交付', '2026-09-15', String(me.id));

const team = await request('/team/create', {
  method: 'POST', headers,
  body: JSON.stringify({ name: `Stage6 eval team ${suffix}`, description: 'Team workload evaluation' }),
});
const teamId = String(team.teamId);
const teamProjectId = await createProject('Stage6 team project', teamId);
await createTask(teamProjectId, '团队逾期任务', '2026-09-01', String(me.id));
await createTask(teamProjectId, '团队近期任务', '2026-09-15', String(me.id));

const failureProjectIds = {};
for (const fault of ['unregistered-tool', 'invalid-arguments', 'duplicate-tool', 'invalid-json', 'timeout']) {
  const faultProjectId = await createProject(`Stage6 ${fault}`);
  await createTask(faultProjectId, `[[STAGE6_AGENT:${fault}]]`, '2026-09-01', String(me.id));
  failureProjectIds[fault] = faultProjectId;
}

fs.writeFileSync(runtimeFile, `${JSON.stringify({
  datasetVersion: '1.0.0', projectId, teamId,
  securityProjectId: failureProjectIds['unregistered-tool'], failureProjectIds,
  createdAt: new Date().toISOString(),
}, null, 2)}\n`);
console.log(`Stage 6 fixture ready: project=${projectId}, team=${teamId}, faultProjects=5`);

