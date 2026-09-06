import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const localConfig = path.join(root, '.promptfoo');
fs.mkdirSync(localConfig, { recursive: true });
const cli = path.resolve(root, '..', 'stage3', 'node_modules', 'promptfoo', 'dist', 'src', 'main.js');
if (!fs.existsSync(cli)) throw new Error('Stage 3 promptfoo dependency is missing; run npm ci in evals/stage3 first');
const result = spawnSync(process.execPath, [cli, ...process.argv.slice(2)], {
  cwd: root,
  stdio: 'inherit',
  env: { ...process.env, PROMPTFOO_CONFIG_DIR: localConfig,
    PROMPTFOO_LOG_DIR: path.join(localConfig, 'logs'), PROMPTFOO_DISABLE_TELEMETRY: 'true',
    PROMPTFOO_DISABLE_UPDATE: 'true' },
});
if (result.error) throw result.error;
process.exit(result.status ?? 1);
