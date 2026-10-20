/*
 * HTML Lens sandbox service worker. Serves bundle files stored by host.js from Cache Storage at
 *   /s/<session>/<cfg>/<path>
 * adding the sandbox Content-Security-Policy and the bridge script. Mirrors core/serve/Serve.kt.
 */
importScripts('/config.js');

const CACHE = 'hv-vfs';
const ANCESTORS = (self.HV_CONFIG && self.HV_CONFIG.appOrigins || []).concat(['http://localhost:*', 'http://127.0.0.1:*']).join(' ');
const indexCache = new Map();

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/s/')) {
    event.respondWith(serve(url.pathname));
    return;
  }
  if (url.pathname.startsWith('/__hv/') || ['/host', '/host.html', '/host.js', '/sw.js', '/config.js', '/', '/index', '/index.html', '/favicon.ico'].includes(url.pathname)) return;
  // Root-absolute reference ("/css/site.css") from a bundle page: resolve against that page's session.
  // Navigations have no client yet (waiting on resultingClientId would deadlock), so only subresources qualify.
  if (event.request.mode === 'navigate' || !event.clientId) return;
  event.respondWith((async () => {
    const client = await self.clients.get(event.clientId);
    const ref = client ? new URL(client.url).pathname : '';
    if (ref.startsWith('/s/')) {
      const [sid, cfg] = ref.slice(3).split('/');
      return serve('/s/' + sid + '/' + cfg + url.pathname);
    }
    return fetch(event.request);
  })());
});

function normalize(path) {
  const out = [];
  for (const seg of path.split('/')) {
    if (!seg || seg === '.') continue;
    if (seg === '..') out.pop(); else out.push(seg);
  }
  return out.join('/');
}

function decodePath(p) {
  return p.split('/').map((s) => { try { return decodeURIComponent(s); } catch (e) { return s; } }).join('/');
}

async function loadIndex(cache, sid) {
  if (indexCache.has(sid)) return indexCache.get(sid);
  const r = await cache.match('/vfs/' + sid + '/__index');
  const idx = r ? await r.json() : null;
  if (idx) {
    idx.lower = new Map(idx.files.map((f) => [f.toLowerCase(), f]));
    indexCache.set(sid, idx);
  }
  return idx;
}

function parseCfg(token) {
  const [m, d, i, y, n, a] = (token || '').split('-');
  return {
    mode: m === 's' ? 'safe' : m === 'f' ? 'full' : 'offline',
    dark: d === '1', imagesOff: i === '1', scroll: Number(y) || 0, nonce: n || 'none', ns: a || '',
  };
}

function csp(cfg) {
  const img = cfg.imagesOff ? "'none'" : "'self' data: blob:";
  const fa = ' frame-ancestors ' + ANCESTORS;
  if (cfg.mode === 'safe') {
    return "default-src 'self' data: blob:; script-src 'nonce-" + cfg.nonce + "'; style-src 'self' 'unsafe-inline' data:; img-src " + img +
      "; font-src 'self' data:; media-src 'self' data: blob:; connect-src 'self'; frame-src 'self'; object-src 'none'; form-action 'none'; base-uri 'self';" + fa;
  }
  if (cfg.mode === 'offline') {
    return "default-src 'self' data: blob: 'unsafe-inline' 'unsafe-eval'; script-src 'self' data: blob: 'unsafe-inline' 'unsafe-eval'; style-src 'self' data: blob: 'unsafe-inline'; img-src " + img +
      "; font-src 'self' data: blob:; media-src 'self' data: blob:; connect-src 'self' data: blob:; frame-src 'self' data: blob:; worker-src 'self' data: blob:; object-src 'none'; form-action 'self';" + fa;
  }
  return (cfg.imagesOff ? "img-src 'none';" : '') + fa;
}

const INVERT = 'html{background:#fff;filter:invert(1) hue-rotate(180deg)}img,video,picture,canvas,iframe,[style*="background-image"]{filter:invert(1) hue-rotate(180deg)}';

function toLatin1(u8) {
  let s = '';
  for (let i = 0; i < u8.length; i += 32768) s += String.fromCharCode.apply(null, u8.subarray(i, i + 32768));
  return s;
}
function fromLatin1(s) {
  const u8 = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) u8[i] = s.charCodeAt(i) & 0xff;
  return u8;
}

function transformHtml(bytes, cfg) {
  if (bytes.length >= 2 && ((bytes[0] === 0xfe && bytes[1] === 0xff) || (bytes[0] === 0xff && bytes[1] === 0xfe))) return bytes; // UTF-16
  let s = toLatin1(bytes);
  if (cfg.mode === 'safe') s = s.replace(/(<meta[^>]+http-equiv\s*=\s*["']?)refresh/gi, '$1x-refresh-blocked');
  const q = 't=web&m=' + cfg.mode + '&n=' + cfg.nonce + (cfg.ns ? '&a=' + cfg.ns : '') + (cfg.scroll ? '&y=' + cfg.scroll : '') + '&net=1';
  let inject = '<script src="/__hv/bridge.js?' + q + '"' + (cfg.mode === 'safe' ? ' nonce="' + cfg.nonce + '"' : '') + '></script>';
  if (cfg.dark) {
    if (/prefers-color-scheme\s*:\s*dark/i.test(s)) {
      s = s.replace(/prefers-color-scheme\s*:\s*dark/gi, 'min-width:0px').replace(/prefers-color-scheme\s*:\s*light/gi, 'max-width:0px');
      inject += '<style>:root{color-scheme:dark}</style>';
    } else inject += '<style>' + INVERT + '</style>';
  }
  let at = 0, m;
  if ((m = /<head(\s[^>]*)?>/i.exec(s))) at = m.index + m[0].length;
  else if ((m = /<html(\s[^>]*)?>/i.exec(s))) at = m.index + m[0].length;
  else if ((m = /<!doctype[^>]*>/i.exec(s))) at = m.index + m[0].length;
  else if (s.startsWith('ï»¿')) at = 3;
  return fromLatin1(s.slice(0, at) + inject + s.slice(at));
}

function notFound(path) {
  const body = '<!doctype html><meta charset=utf-8><title>Not found</title><body style="font:15px system-ui;padding:32px;color:#444">' +
    '<h2>File not found in this bundle</h2><p><code>' + path.replace(/</g, '&lt;') + '</code></p>' +
    '<p>If this page is part of a folder, open the whole folder or ZIP so linked files are available.</p></body>';
  return new Response(body, { status: 404, headers: { 'Content-Type': 'text/html; charset=utf-8', 'Content-Security-Policy': 'frame-ancestors ' + ANCESTORS } });
}

async function serve(pathname) {
  const parts = pathname.slice(3).split('/');
  const sid = parts[0];
  const cfg = parseCfg(parts[1]);
  let path = normalize(decodePath(parts.slice(2).join('/')));
  const cache = await caches.open(CACHE);
  const idx = await loadIndex(cache, sid);
  if (!idx) return notFound(path);
  const has = (p) => idx.files.includes(p);
  if (!path) path = idx.entry;
  else if (!has(path)) {
    const ci = idx.lower.get(path.toLowerCase());
    if (ci) path = ci;
    else if (has(path + '/index.html')) path = path + '/index.html';
    else if (has(path + '/index.htm')) path = path + '/index.htm';
    else return notFound(path);
  }
  const res = await cache.match('/vfs/' + sid + '/' + encodeURIComponent(path));
  if (!res) return notFound(path);
  const mime = res.headers.get('Content-Type') || 'application/octet-stream';
  let body = new Uint8Array(await res.arrayBuffer());
  if (/^(text\/html|application\/xhtml)/i.test(mime)) body = transformHtml(body, cfg);
  else if (cfg.dark && /^text\/css/i.test(mime)) {
    body = fromLatin1(toLatin1(body).replace(/prefers-color-scheme\s*:\s*dark/gi, 'min-width:0px').replace(/prefers-color-scheme\s*:\s*light/gi, 'max-width:0px'));
  }
  const headers = { 'Content-Type': mime, 'Cache-Control': 'no-store', 'Referrer-Policy': 'same-origin', 'Content-Security-Policy': csp(cfg) };
  return new Response(body, { status: 200, headers });
}
