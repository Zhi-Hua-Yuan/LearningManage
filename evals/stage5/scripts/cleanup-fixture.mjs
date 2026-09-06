import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const runtimeFile = path.join(root, '.runtime-fixture.json')
if (!fs.existsSync(runtimeFile)) process.exit(0)
const runtime = JSON.parse(fs.readFileSync(runtimeFile, 'utf8'))
const baseUrl = (process.env.STAGE5_API_BASE_URL || 'http://127.0.0.1:18133/api').replace(/\/$/, '')
const account = process.env.STAGE5_EVAL_ACCOUNT
const password = process.env.STAGE5_EVAL_PASSWORD
if (!account || !password) throw new Error('STAGE5_EVAL_ACCOUNT and STAGE5_EVAL_PASSWORD are required')

const login = await fetch(`${baseUrl}/user/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ account, password }),
})
const loginBody = await login.json()
if (!login.ok || loginBody?.code !== 0 || !loginBody?.data?.token) {
  throw new Error('Unable to authenticate Stage 5 fixture cleanup')
}
const response = await fetch(`${baseUrl}/project/delete/${encodeURIComponent(runtime.projectId)}`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${loginBody.data.token}` },
})
const body = await response.json()
if (!response.ok || body?.code !== 0) throw new Error('Unable to delete Stage 5 fixture project')
fs.rmSync(runtimeFile)
console.log(`Stage 5 fixture removed: project=${runtime.projectId}`)
