'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const ENDPOINTS = Object.freeze({
  'task-breakdown': '/ai/breakdown/preview',
  'weekly-polish': '/ai/polish/preview',
  'today-order': '/ai/today-order/recommend',
  'daily-rename': '/ai/daily-review/suggest-rename',
  'list-replan': '/ai/list/replan/preview'
});

const ACTOR_ACCOUNTS = Object.freeze({
  owner: 'stage3owner',
  member: 'stage3member',
  outsider: 'stage3outsider'
});

const tokenCache = new Map();
let mysqlPromise;

function parseJsonVar(value, fallback) {
  if (value == null) return fallback;
  if (typeof value === 'object') return value;
  return JSON.parse(value);
}

function env(name, fallback) {
  const value = process.env[name];
  return value == null || value === '' ? fallback : value;
}

function apiBaseUrl() {
  return env('STAGE3_API_BASE_URL', 'http://127.0.0.1:18133/api').replace(/\/$/, '');
}

async function login(actor) {
  if (tokenCache.has(actor)) return tokenCache.get(actor);
  const account = ACTOR_ACCOUNTS[actor];
  if (!account) throw new Error(`Unknown Stage 3 actor: ${actor}`);
  const password = env('STAGE3_EVAL_PASSWORD');
  if (!password) throw new Error('STAGE3_EVAL_PASSWORD is required');
  const response = await fetch(`${apiBaseUrl()}/user/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password })
  });
  const body = await response.json();
  if (!response.ok || body?.code !== 0 || !body?.data?.token) {
    throw new Error(`Stage 3 actor login failed for ${actor} with HTTP ${response.status}`);
  }
  tokenCache.set(actor, body.data.token);
  return body.data.token;
}

function validateDatabaseTarget(database, host, port) {
  if (!database || !database.endsWith('_eval')) throw new Error('Stage 3 metadata database must end with _eval');
  if (!['127.0.0.1', 'localhost'].includes(host)) throw new Error('Stage 3 metadata database must be loopback-only');
  if (Number(port) === 3306) throw new Error('Stage 3 metadata database must not use port 3306');
}

async function mysqlPool() {
  if (mysqlPromise) return mysqlPromise;
  const database = env('STAGE3_DB_NAME');
  if (!database) throw new Error('STAGE3_DB_NAME is required for trace and write-integrity verification');
  const host = env('STAGE3_DB_HOST', '127.0.0.1');
  const port = Number(env('STAGE3_DB_PORT', '13316'));
  validateDatabaseTarget(database, host, port);
  mysqlPromise = import('mysql2/promise').then(({ createPool }) => createPool({
    host,
    port,
    database,
    user: env('STAGE3_DB_USERNAME'),
    password: env('STAGE3_DB_PASSWORD'),
    connectionLimit: 2,
    enableKeepAlive: true
  }));
  return mysqlPromise;
}

function loadPromptManifest() {
  const configured = env('STAGE3_PROMPT_MANIFEST');
  if (!configured) return {};
  const manifestPath = path.resolve(configured);
  if (!fs.existsSync(manifestPath)) throw new Error(`Prompt manifest does not exist: ${manifestPath}`);
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  return Object.fromEntries((manifest.prompts || []).map((item) => [`${item.code}:${item.version}:${item.source}`, item]));
}

async function lookupMetadata(traceId) {
  const pool = await mysqlPool();
  const [rows] = await pool.execute(
    `SELECT id, scene, requested_model, model_name, finish_reason, provider_request_id, prompt_tokens,
            completion_tokens, total_tokens, estimated_cost, price_version,
            prompt_type, prompt_version, prompt_source, degraded, failure_type,
            fallback_used, cost_time_ms
       FROM ai_call_log
      WHERE trace_id = ?
      ORDER BY create_time DESC, id DESC
      LIMIT 1`,
    [traceId]
  );
  const row = rows[0];
  if (!row) throw new Error(`No ai_call_log record found for Stage 3 trace ${traceId}`);
  const manifest = loadPromptManifest();
  const key = `${row.prompt_type}:${row.prompt_version}:${String(row.prompt_source || '').toUpperCase()}`;
  return {
    callLogFound: true,
    callLogId: String(row.id),
    promptCode: row.prompt_type,
    promptVersion: row.prompt_version,
    promptSource: row.prompt_source,
    promptContentHash: manifest[key]?.sha256 || null,
    requestedModel: row.requested_model,
    actualModel: row.model_name,
    finishReason: row.finish_reason,
    providerRequestIdHash: row.provider_request_id == null ? null
      : crypto.createHash('sha256').update(String(row.provider_request_id)).digest('hex').toUpperCase(),
    degraded: row.degraded === 1,
    failureType: row.failure_type,
    fallbackUsed: row.fallback_used === 1,
    latencyMs: Number(row.cost_time_ms || 0),
    promptTokens: row.prompt_tokens == null ? null : Number(row.prompt_tokens),
    completionTokens: row.completion_tokens == null ? null : Number(row.completion_tokens),
    totalTokens: row.total_tokens == null ? null : Number(row.total_tokens),
    estimatedCost: row.estimated_cost == null ? null : Number(row.estimated_cost),
    priceVersion: row.price_version
  };
}

function canonicalDatabaseValue(value) {
  if (value instanceof Date) return value.toISOString();
  if (Buffer.isBuffer(value)) return value.toString('hex');
  if (typeof value === 'bigint') return value.toString();
  return value;
}

async function formalBusinessSnapshot() {
  const pool = await mysqlPool();
  const hash = crypto.createHash('sha256');
  let rowCount = 0;
  for (const table of ['project', 'milestone', 'task']) {
    const [rows] = await pool.query(`SELECT * FROM \`${table}\` ORDER BY id`);
    rowCount += rows.length;
    hash.update(`${table}\n`);
    hash.update(JSON.stringify(rows, (_key, value) => canonicalDatabaseValue(value)));
    hash.update('\n');
  }
  return { rowCount, sha256: hash.digest('hex').toUpperCase() };
}

async function aiDraftRowCount() {
  const pool = await mysqlPool();
  if (!pool) return null;
  const [rows] = await pool.query('SELECT COUNT(*) AS row_count FROM ai_draft');
  return Number(rows[0]?.row_count || 0);
}

module.exports = class LearningManageHttpProvider {
  constructor(options = {}) {
    this.providerId = options.id || 'learning-manage-http';
  }

  id() {
    return this.providerId;
  }

  async callApi(_prompt, context) {
    const vars = context?.vars || {};
    const caseId = String(vars.caseId || 'UNKNOWN');
    const scene = String(vars.scene || '');
    const endpoint = ENDPOINTS[scene];
    if (!endpoint) throw new Error(`Unsupported Stage 3 scene: ${scene}`);
    const actor = String(vars.actor || 'owner');
    const token = await login(actor);
    const requestPayload = parseJsonVar(vars.requestPayload, {});
    const traceId = `s3_${caseId.replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 28)}_${crypto.randomBytes(6).toString('hex')}`;
    const startedAt = Date.now();
    const formalBefore = await formalBusinessSnapshot();
    const draftRowsBefore = await aiDraftRowCount();
    const response = await fetch(`${apiBaseUrl()}${endpoint}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'X-Trace-Id': traceId
      },
      body: JSON.stringify(requestPayload)
    });
    const text = await response.text();
    let body;
    try { body = JSON.parse(text); } catch { body = { code: -1, message: 'Non-JSON application response', data: null }; }
    const persisted = await lookupMetadata(traceId);
    const formalAfter = await formalBusinessSnapshot();
    const draftRowsAfter = await aiDraftRowCount();
    const success = response.ok && body?.code === 0;
    const envelope = {
      caseId,
      scene,
      success,
      data: success ? body.data : null,
      error: success ? null : { httpStatus: response.status, code: body?.code ?? null, message: body?.message || 'Unknown error' },
      meta: {
        traceId,
        formalBusinessWrites: Number(formalBefore.sha256 !== formalAfter.sha256),
        formalBusinessRowsBefore: formalBefore.rowCount,
        formalBusinessRowsAfter: formalAfter.rowCount,
        aiDraftWrites: draftRowsBefore == null || draftRowsAfter == null ? null : draftRowsAfter - draftRowsBefore,
        latencyMs: persisted.latencyMs ?? Date.now() - startedAt,
        ...persisted
      }
    };
    return {
      output: JSON.stringify(envelope),
      tokenUsage: persisted.totalTokens == null ? undefined : {
        prompt: persisted.promptTokens,
        completion: persisted.completionTokens,
        total: persisted.totalTokens
      },
      cost: persisted.estimatedCost ?? undefined,
      metadata: envelope.meta
    };
  }
};
