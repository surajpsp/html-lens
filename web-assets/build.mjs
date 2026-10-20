// Builds browser assets into dist/ and copies them to every consumer:
//   - app desktop resources (served by the CEF resource handler)
//   - sandbox site (served by the web sandbox origin)
//   - main site (editor iframe used by the web app)
import { build } from 'esbuild';
import { cpSync, mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const dist = join(root, 'dist');
rmSync(dist, { recursive: true, force: true });
mkdirSync(join(dist, '__hv'), { recursive: true });
mkdirSync(join(dist, 'editor'), { recursive: true });

const common = { bundle: true, minify: true, format: 'iife', target: ['chrome100', 'safari15', 'firefox100'], legalComments: 'none' };

await build({ ...common, entryPoints: [join(root, 'src/bridge.js')], outfile: join(dist, '__hv/bridge.js') });
await build({ ...common, entryPoints: [join(root, 'src/snap.js')], outfile: join(dist, '__hv/snap.js') });
await build({ ...common, entryPoints: [join(root, 'src/editor.js')], outfile: join(dist, 'editor/editor.js') });
cpSync(join(root, 'src/editor.html'), join(dist, 'editor/index.html'));

const copies = [
  ['__hv', '../app/src/desktopMain/resources/web/__hv'],
  ['editor', '../app/src/desktopMain/resources/web/editor'],
  ['__hv', '../sandbox/public/__hv'],
  ['editor', '../site/public/app/editor'],
  ['editor', '../app/src/wasmJsMain/resources/editor'],
];
for (const [from, to] of copies) {
  const target = join(root, to);
  rmSync(target, { recursive: true, force: true });
  mkdirSync(target, { recursive: true });
  cpSync(join(dist, from), target, { recursive: true });
}
console.log('web-assets built and copied to app, sandbox and site');
