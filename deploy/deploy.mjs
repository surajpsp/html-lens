// Builds the website (site/build.mjs, which includes the web app at /app) and deploys it plus the sandbox to Cloudflare.
//   1. ./gradlew :app:wasmJsBrowserDistribution
//   2. npx wrangler login            (once)
//   3. node deploy/deploy.mjs
// Override domains with APP_DOMAIN / SANDBOX_DOMAIN env vars.
import { cpSync, mkdirSync, rmSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const APP = process.env.APP_DOMAIN || 'htmllens.silentdev.online';
const SANDBOX = process.env.SANDBOX_DOMAIN || 'htmllens-sandbox.silentdev.online';
const dist = join(root, 'app/build/dist/wasmJs/productionExecutable');
if (!existsSync(join(dist, 'index.html'))) throw new Error('Build the web app first: ./gradlew :app:wasmJsBrowserDistribution');

// --- site + app (/app) ---
execSync('node site/build.mjs', { cwd: root, stdio: 'inherit', env: { ...process.env, SITE_URL: `https://${APP}`, SANDBOX_URL: `https://${SANDBOX}` } });
const appOut = join(here, 'app/public');
rmSync(appOut, { recursive: true, force: true });
cpSync(join(root, 'site/dist'), appOut, { recursive: true });

// --- sandbox ---
const sbOut = join(here, 'sandbox/public');
rmSync(sbOut, { recursive: true, force: true });
mkdirSync(sbOut, { recursive: true });
cpSync(join(root, 'sandbox/public'), sbOut, { recursive: true });
const origins = [`https://${APP}`, 'https://htmllens.pages.dev'];
writeFileSync(join(sbOut, 'config.js'), `self.HV_CONFIG = ${JSON.stringify({ appOrigins: origins })};\n`);
writeFileSync(join(sbOut, '_headers'), `/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: same-origin
  X-Robots-Tag: noindex
/host.html
  Content-Security-Policy: frame-ancestors ${origins.join(' ')}
/sw.js
  Cache-Control: no-cache
/config.js
  Cache-Control: no-cache
`);

const run = (cwd) => execSync('npx --yes wrangler@4 deploy', { cwd, stdio: 'inherit' });
run(join(here, 'sandbox'));
run(join(here, 'app'));
console.log(`\nDeployed:\n  App      https://${APP}\n  Sandbox  https://${SANDBOX}`);
