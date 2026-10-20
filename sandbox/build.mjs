// Writes deploy-time config for the sandbox origin.
//   APP_ORIGINS="https://htmllens.app,https://www.htmllens.app" node build.mjs
import { writeFileSync } from 'node:fs';
const origins = (process.env.APP_ORIGINS || 'https://htmllens.app,https://www.htmllens.app').split(',').map((s) => s.trim()).filter(Boolean);
writeFileSync(new URL('./public/config.js', import.meta.url), `self.HV_CONFIG = ${JSON.stringify({ appOrigins: origins })};\n`);
writeFileSync(new URL('./public/_headers', import.meta.url), `/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: same-origin
/host.html
  Content-Security-Policy: frame-ancestors ${origins.join(' ')}
/sw.js
  Cache-Control: no-cache
/config.js
  Cache-Control: no-cache
`);
console.log('sandbox configured for', origins.join(', '));
