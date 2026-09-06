import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildCases } from './case-factory.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const tests = buildCases().map((item) => ({
  description: `${item.caseId} ${item.split}`,
  vars: {
    caseId: item.caseId,
    sourceAlias: item.alias,
    question: item.question,
    expectedTitle: item.title,
    expectedMarker: item.expectedMarker,
    split: item.split,
    datasetVersion: '1.0.0',
    labelVersion: '1',
  },
  metadata: {
    caseId: item.caseId,
    split: item.split,
  },
}))
fs.mkdirSync(path.join(root, 'tests'), { recursive: true })
fs.writeFileSync(path.join(root, 'tests', 'generated.json'), `${JSON.stringify(tests, null, 2)}\n`)
console.log(`Stage 5 RAG tests generated: ${tests.length}`)
