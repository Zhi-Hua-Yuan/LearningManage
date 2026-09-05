import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outputPath = path.resolve(process.argv[2] || 'candidate-prompts.sql');
const promptRoot = path.join(root, 'prompts');
const manifest = JSON.parse(fs.readFileSync(path.join(promptRoot, 'candidate-prompts.json'), 'utf8'));

function hex(value) {
  return `CONVERT(0x${Buffer.from(String(value), 'utf8').toString('hex')} USING utf8mb4)`;
}

function validatePrompt(prompt) {
  if (!/^\d{18}$/.test(String(prompt.id))) throw new Error(`Invalid candidate prompt ID: ${prompt.id}`);
  if (!/^[a-z0-9.-]{3,64}$/.test(String(prompt.code))) throw new Error(`Invalid candidate prompt code: ${prompt.code}`);
  if (!/^[a-z0-9-]{3,64}$/.test(String(prompt.scene))) throw new Error(`Invalid candidate prompt scene: ${prompt.scene}`);
  if (!Number.isInteger(prompt.version) || prompt.version < 2) throw new Error(`Invalid candidate prompt version: ${prompt.version}`);
  const resolved = path.resolve(promptRoot, prompt.file);
  if (!resolved.startsWith(`${promptRoot}${path.sep}`) || !fs.existsSync(resolved)) throw new Error(`Candidate prompt file is missing: ${prompt.file}`);
  const content = fs.readFileSync(resolved, 'utf8').trim();
  if (!content) throw new Error(`Candidate prompt is blank: ${prompt.file}`);
  return { ...prompt, content };
}

const prompts = (manifest.prompts || []).map(validatePrompt);
if (!prompts.length || new Set(prompts.map((prompt) => prompt.code)).size !== prompts.length) {
  throw new Error('Candidate prompt codes must be present and unique');
}

const statements = ['START TRANSACTION;'];
for (const prompt of prompts) {
  statements.push(`UPDATE prompt_template SET enabled=0 WHERE template_code=${hex(prompt.code)} AND enabled=1;`);
  statements.push([
    'INSERT INTO prompt_template',
    '(id, template_code, scene, template_name, template_content, version, enabled, remark, is_delete)',
    `VALUES (${prompt.id}, ${hex(prompt.code)}, ${hex(prompt.scene)}, ${hex(prompt.name)}, ${hex(prompt.content)}, ${prompt.version}, 1, ${hex(prompt.remark)}, 0)`,
    'ON DUPLICATE KEY UPDATE template_name=VALUES(template_name), template_content=VALUES(template_content),',
    'enabled=1, remark=VALUES(remark), is_delete=0;'
  ].join(' '));
}
statements.push('COMMIT;', '');

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, statements.join('\n'), 'utf8');
console.log(JSON.stringify({ status: 'PASS', output: outputPath, prompts: prompts.length }));
