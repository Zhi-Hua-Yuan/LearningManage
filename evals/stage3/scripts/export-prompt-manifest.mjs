import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = path.resolve(root, '..', '..');
const sourcePath = path.join(repositoryRoot, 'src', 'main', 'java', 'com', 'spt', 'learningmanage', 'prompt', 'DefaultAiPromptTemplateProvider.java');
if (!fs.existsSync(sourcePath)) throw new Error(`Built-in prompt provider is missing: ${sourcePath}`);

const source = fs.readFileSync(sourcePath, 'utf8');
const promptCodes = {
  TASK_BREAKDOWN_DEFAULT: 'task-breakdown.default',
  TASK_BREAKDOWN_DETAILED: 'task-breakdown.detailed',
  WEEKLY_POLISH_DEFAULT: 'weekly-polish.default',
  TODAY_ORDER_DEFAULT: 'today-order.default',
  DAILY_REVIEW_RENAME_DEFAULT: 'daily-review-rename.default',
  LIST_REPLAN_PREVIEW: 'list-replan.preview'
};

function findInvocation(enumName) {
  const marker = `register(AiPromptCodeEnum.${enumName}`;
  const start = source.indexOf(marker);
  if (start < 0) throw new Error(`Prompt registration not found for ${enumName}`);
  let inString = false;
  let escaped = false;
  let depth = 0;
  for (let index = start; index < source.length; index += 1) {
    const char = source[index];
    if (inString) {
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === '"') inString = false;
      continue;
    }
    if (char === '"') inString = true;
    else if (char === '(') depth += 1;
    else if (char === ')') {
      depth -= 1;
      if (depth === 0) return source.slice(start, index + 1);
    }
  }
  throw new Error(`Unterminated prompt registration for ${enumName}`);
}

function decodeJavaString(value) {
  return value
    .replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(Number.parseInt(hex, 16)))
    .replace(/\\n/g, '\n')
    .replace(/\\r/g, '\r')
    .replace(/\\t/g, '\t')
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, '\\');
}

function promptContent(invocation) {
  const literals = [];
  const expression = /"((?:\\.|[^"\\])*)"/g;
  let match;
  while ((match = expression.exec(invocation)) !== null) literals.push(decodeJavaString(match[1]));
  if (literals.length < 1) throw new Error('Prompt registration contains no system prompt literal');
  return literals.join('');
}

const builtInPrompts = Object.entries(promptCodes).map(([enumName, code]) => {
  const content = promptContent(findInvocation(enumName));
  return {
    code,
    version: 1,
    source: 'BUILTIN',
    sha256: crypto.createHash('sha256').update(content, 'utf8').digest('hex').toUpperCase(),
    contentLength: [...content].length
  };
});

const candidateConfigPath = path.join(root, 'prompts', 'candidate-prompts.json');
const candidateConfig = JSON.parse(fs.readFileSync(candidateConfigPath, 'utf8'));
const candidatePrompts = new Map((candidateConfig.prompts || []).map((candidate) => {
  const promptRoot = path.resolve(root, 'prompts');
  const contentPath = path.resolve(promptRoot, candidate.file);
  if (!contentPath.startsWith(`${promptRoot}${path.sep}`) || !fs.existsSync(contentPath)) {
    throw new Error(`Candidate prompt file is missing: ${candidate.file}`);
  }
  const content = fs.readFileSync(contentPath, 'utf8').trim();
  return [candidate.code, {
    code: candidate.code,
    version: candidate.version,
    source: 'DATABASE',
    sha256: crypto.createHash('sha256').update(content, 'utf8').digest('hex').toUpperCase(),
    contentLength: [...content].length
  }];
}));
const prompts = builtInPrompts.map((prompt) => candidatePrompts.get(prompt.code) || prompt);

const manifest = {
  schemaVersion: 1,
  generatedAt: process.env.STAGE3_MANIFEST_GENERATED_AT || null,
  backendSha: process.env.STAGE3_MANIFEST_BACKEND_SHA || null,
  prompts
};
const outputPath = path.join(root, 'prompt-manifest.json');
fs.writeFileSync(outputPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({ status: 'PASS', output: outputPath, count: prompts.length }));
