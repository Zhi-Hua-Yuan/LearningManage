import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildCases } from './case-factory.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const runtimeFile = path.join(root, '.runtime-fixture.json')
const baseUrl = (process.env.STAGE5_API_BASE_URL || 'http://127.0.0.1:18133/api').replace(/\/$/, '')
const account = process.env.STAGE5_EVAL_ACCOUNT
const password = process.env.STAGE5_EVAL_PASSWORD
if (!account || !password) throw new Error('STAGE5_EVAL_ACCOUNT and STAGE5_EVAL_PASSWORD are required')

async function jsonRequest(endpoint, options = {}) {
  const response = await fetch(`${baseUrl}${endpoint}`, options)
  const body = await response.json()
  return { response, body }
}

async function login() {
  let attempt = await jsonRequest('/user/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password }),
  })
  if (attempt.body?.code !== 0 && process.env.STAGE5_EVAL_ALLOW_REGISTER === 'true') {
    await jsonRequest('/user/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ account, username: 'Stage5 RAG Eval', password, confirmPassword: password }),
    })
    attempt = await jsonRequest('/user/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ account, password }),
    })
  }
  if (!attempt.response.ok || attempt.body?.code !== 0 || !attempt.body?.data?.token) {
    throw new Error(`Stage 5 fixture login failed with HTTP ${attempt.response.status}`)
  }
  return attempt.body.data.token
}

const token = await login()
const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
const projectResponse = await jsonRequest('/project/add', {
  method: 'POST', headers,
  body: JSON.stringify({
    name: `Stage5 RAG Eval ${new Date().toISOString()}`,
    goal: 'Deterministic permission-aware RAG retrieval evaluation fixture',
  }),
})
if (projectResponse.body?.code !== 0) throw new Error('Unable to create Stage 5 evaluation project')
const rawProject = projectResponse.body.data
const projectId = String(typeof rawProject === 'object' ? rawProject.id || rawProject.projectId : rawProject)
if (!/^\d+$/.test(projectId)) throw new Error('Stage 5 project API returned an invalid ID')

const sources = {}
for (const item of buildCases()) {
  const taskResponse = await jsonRequest('/task/add', {
    method: 'POST', headers,
    body: JSON.stringify({
      projectId,
      title: item.title,
      description: item.description,
      priority: 2,
    }),
  })
  if (taskResponse.body?.code !== 0) throw new Error(`Unable to create fixture task ${item.caseId}`)
  sources[item.alias] = String(taskResponse.body.data)
}

fs.writeFileSync(runtimeFile, `${JSON.stringify({
  datasetVersion: '1.0.0',
  projectId,
  sources,
  createdAt: new Date().toISOString(),
}, null, 2)}\n`)

const probe = buildCases()[0]
const expectedSourceId = sources[probe.alias]
let ready = false
let lastProbeBody = null
for (let attempt = 0; attempt < 60; attempt += 1) {
  const response = await jsonRequest('/ai/rag/ask', {
    method: 'POST', headers,
    body: JSON.stringify({ projectId, question: probe.question }),
  })
  lastProbeBody = response.body
  if (response.body?.code === 0 && response.body?.data?.sources?.some(
    (source) => String(source.sourceId) === expectedSourceId,
  )) {
    ready = true
    break
  }
  await new Promise((resolve) => setTimeout(resolve, 1000))
}
if (!ready) {
  const qdrantBaseUrl = (process.env.QDRANT_BASE_URL || '').replace(/\/$/, '')
  const qdrantAlias = process.env.QDRANT_ALIAS || 'learning_knowledge_current'
  let qdrantDocument = 'unavailable'
  if (qdrantBaseUrl) {
    try {
      const diagnostic = await fetch(`${qdrantBaseUrl}/collections/${qdrantAlias}/points/scroll`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          filter: { must: [{ key: 'documentKey', match: {
            value: `TASK:${expectedSourceId}:PRIVATE:${projectId}`,
          } }] },
          limit: 2,
          with_payload: true,
          with_vector: false,
        }),
      })
      qdrantDocument = await diagnostic.text()
    } catch (error) {
      qdrantDocument = `diagnostic-error:${error?.name || 'Error'}`
    }
  }
  throw new Error(`Stage 5 knowledge index did not become queryable within 60 seconds; lastProbe=${JSON.stringify(lastProbeBody)}; qdrantDocument=${qdrantDocument}`)
}
console.log(`Stage 5 fixture ready: project=${projectId}, sources=${Object.keys(sources).length}`)
