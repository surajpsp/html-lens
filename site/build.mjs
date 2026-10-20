// Static site generator for the HTML Lens website (no dependencies).
//   node site/build.mjs            → site/dist (landing pages + /app web app)
// Env: SITE_URL, SANDBOX_URL, DOWNLOAD_BASE (e.g. https://github.com/<you>/html-lens/releases/latest/download)
import { cpSync, mkdirSync, rmSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tools, guides, homeFaq } from './src/content.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const out = join(here, 'dist');
const SITE = (process.env.SITE_URL || 'https://htmllens.silentdev.online').replace(/\/$/, '');
const SANDBOX = (process.env.SANDBOX_URL || 'https://htmllens-sandbox.silentdev.online').replace(/\/$/, '');
const DOWNLOAD_BASE = (process.env.DOWNLOAD_BASE || '').replace(/\/$/, '');
const TODAY = new Date().toISOString().slice(0, 10);
const VERSION = '1.0.0';

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const toolBySlug = Object.fromEntries(tools.map((t) => [t.slug, t]));

const LOGO = '<svg viewBox="0 0 512 512" aria-hidden="true"><rect x="20" y="20" width="472" height="472" rx="100" fill="currentColor"/><circle cx="226" cy="226" r="102" fill="none" stroke="var(--bg)" stroke-width="36"/><line x1="303" y1="303" x2="386" y2="386" stroke="#E07A52" stroke-width="44" stroke-linecap="round"/></svg>';

function layout({ path, title, description, body, jsonld = [], noindex = false, scripts = true }) {
  const url = SITE + path;
  const ld = jsonld.map((o) => `<script type="application/ld+json">${JSON.stringify(o)}</script>`).join('\n');
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<meta name="description" content="${esc(description)}">
<link rel="canonical" href="${url}">
${noindex ? '<meta name="robots" content="noindex">' : ''}
<meta name="theme-color" content="#f6f1e7" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#1b1915" media="(prefers-color-scheme: dark)">
<meta property="og:type" content="website">
<meta property="og:site_name" content="HTML Lens">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(description)}">
<meta property="og:url" content="${url}">
<meta property="og:image" content="${SITE}/assets/og-image.png">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<link rel="icon" href="/favicon.ico" sizes="48x48">
<link rel="apple-touch-icon" href="/assets/apple-touch-icon.png">
<link rel="preload" href="/assets/fonts/newsreader.ttf" as="font" type="font/ttf" crossorigin>
<link rel="stylesheet" href="/assets/site.css?v=${VERSION}">
${ld}
</head>
<body>
<a class="skip" href="#main">Skip to content</a>
<header class="site-header"><div class="wrap">
  <a class="brand" href="/">${LOGO}<span>HTML Lens</span></a>
  <nav class="site-nav" aria-label="Main">
    <a href="/html-viewer/"${path === '/html-viewer/' ? ' aria-current="page"' : ''}>HTML viewer</a>
    <a href="/html-attachment-checker/"${path === '/html-attachment-checker/' ? ' aria-current="page"' : ''}>Safety check</a>
    <a href="/tools/"${path === '/tools/' ? ' aria-current="page"' : ''}>All tools</a>
    <a href="/download/"${path === '/download/' ? ' aria-current="page"' : ''}>Download</a>
  </nav>
  <a class="btn btn-ink btn-small header-cta" href="/app/">Open the app</a>
</div></header>
<main id="main">
${body}
</main>
<footer class="site-footer"><div class="wrap footer-grid">
  <div>
    <a class="brand" href="/">${LOGO}<span>HTML Lens</span></a>
    <p class="faint" style="max-width:300px;margin-top:14px">Open any HTML file safely. Everything stays on your device — there are no servers that see your files.</p>
  </div>
  <div><h4>Open</h4><ul>
    <li><a href="/html-viewer/">HTML files</a></li><li><a href="/mhtml-viewer/">MHTML &amp; MHT</a></li>
    <li><a href="/eml-viewer/">EML email</a></li><li><a href="/webarchive-viewer/">Safari web archives</a></li>
    <li><a href="/zip-html-viewer/">ZIP &amp; folders</a></li></ul></div>
  <div><h4>Tools</h4><ul>
    <li><a href="/html-attachment-checker/">Attachment safety check</a></li><li><a href="/run-html-code/">Run HTML code</a></li>
    <li><a href="/mhtml-to-html/">MHTML to HTML</a></li><li><a href="/html-email-tester/">HTML email tester</a></li>
    <li><a href="/telegram-export-viewer/">Telegram export viewer</a></li></ul></div>
  <div><h4>More</h4><ul>
    <li><a href="/download/">Download for Mac &amp; Windows</a></li>
    ${guides.map((g) => `<li><a href="/guides/${g.slug}/">${esc(g.h1.replace(/^How to /, '').replace(/^./, (c) => c.toUpperCase()))}</a></li>`).join('')}
    <li><a href="/privacy/">Privacy</a></li></ul></div>
</div></footer>
${scripts ? `<script src="/assets/site.js?v=${VERSION}" defer></script>` : ''}
</body>
</html>
`;
}

function tray(accept, label = 'Drop it here') {
  return `<div class="tray" data-tray role="button" tabindex="0" aria-label="Choose a file to open">
  <div>
    <p class="title">${label}</p>
    <p class="muted" style="margin:6px 0 0">Or click to choose a file. It opens in your browser — nothing is uploaded.</p>
  </div>
  <ul><li>.html&nbsp; .htm</li><li>.mhtml&nbsp; .mht</li><li>.zip</li><li>.eml</li><li>.webarchive</li><li>.svg</li></ul>
  <p class="status" role="status"></p>
  <input type="file" accept="${accept}" multiple hidden>
</div>`;
}

const faqLd = (faq) => ({
  '@context': 'https://schema.org', '@type': 'FAQPage',
  mainEntity: faq.map(([q, a]) => ({ '@type': 'Question', name: q, acceptedAnswer: { '@type': 'Answer', text: a } })),
});
const crumbLd = (items) => ({
  '@context': 'https://schema.org', '@type': 'BreadcrumbList',
  itemListElement: items.map(([name, path], i) => ({ '@type': 'ListItem', position: i + 1, name, item: SITE + path })),
});
const appLd = {
  '@context': 'https://schema.org', '@type': 'SoftwareApplication', name: 'HTML Lens',
  applicationCategory: 'UtilitiesApplication', operatingSystem: 'Web, macOS, Windows',
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
  description: 'Open, inspect and safely preview HTML, MHTML, ZIP, EML and Safari Web Archive files on your own device.',
  url: SITE + '/', image: SITE + '/assets/og-image.png',
};

function faqHtml(faq) {
  return `<div class="faq">${faq.map(([q, a]) => `<details><summary>${esc(q)}</summary><p>${esc(a)}</p></details>`).join('')}</div>`;
}

function relatedHtml(slugs) {
  return `<div class="rule-list">${slugs.map((s) => toolBySlug[s]).filter(Boolean).map((t) =>
    `<a class="row" href="/${t.slug}/"><span><b>${esc(t.nav)}</b><span class="d">${esc(t.description.split('. ')[0])}.</span></span><span class="arrow" aria-hidden="true">→</span></a>`).join('')}</div>`;
}

const pages = [];
const page = (path, html) => pages.push({ path, html });

// ---------------------------------------------------------------- home
page('/', layout({
  path: '/',
  title: 'HTML Lens — Open Any HTML, MHTML, ZIP or EML File Safely',
  description: 'A free HTML viewer for the web, Mac and Windows. Open HTML, MHTML, ZIP, EML and Safari web archive files with images intact, check attachments for phishing, and run code from ChatGPT — all on your device.',
  jsonld: [appLd, { '@context': 'https://schema.org', '@type': 'WebSite', name: 'HTML Lens', url: SITE + '/' }, faqLd(homeFaq)],
  body: `
<section class="hero"><div class="wrap hero-grid">
  <div>
    <p class="kicker">No upload · No account · Works offline</p>
    <h1>Open any HTML file — and see exactly what it does.</h1>
    <p class="lead">Saved pages, email attachments, exports and reports open with their images and styles intact. Anything you don’t trust runs in a sealed sandbox first.</p>
    <div class="actions">
      <a class="btn btn-ink" href="/app/" data-open-file>Open a file</a>
      <a class="btn btn-line" href="/download/">Get the desktop app</a>
      <a href="/run-html-code/">or paste code</a>
    </div>
  </div>
  ${tray('.html,.htm,.xhtml,.mhtml,.mht,.zip,.eml,.webarchive,.svg')}
</div></section>

<section class="block"><div class="wrap">
  <h2>What people open with it</h2>
  <div class="rule-list">
    <a class="row" href="/html-attachment-checker/"><span><b>An attachment they don’t trust</b><span class="d">See if an .html file is a fake sign-in page before typing anything into it.</span></span><span class="arrow" aria-hidden="true">→</span></a>
    <a class="row" href="/mhtml-viewer/"><span><b>A saved web page</b><span class="d">MHTML from Chrome or Edge, and Safari web archives — on any device.</span></span><span class="arrow" aria-hidden="true">→</span></a>
    <a class="row" href="/run-html-code/"><span><b>Code an AI wrote for them</b><span class="d">Paste HTML from ChatGPT or Claude, watch it run, keep it as an app.</span></span><span class="arrow" aria-hidden="true">→</span></a>
    <a class="row" href="/zip-html-viewer/"><span><b>Exports and reports</b><span class="d">Telegram and Facebook exports, test reports and docs — straight from the ZIP.</span></span><span class="arrow" aria-hidden="true">→</span></a>
    <a class="row" href="/eml-viewer/"><span><b>An email saved as .eml</b><span class="d">Read it with images and attachments, without Outlook.</span></span><span class="arrow" aria-hidden="true">→</span></a>
    <a class="row" href="/html-email-tester/"><span><b>An email they’re about to send</b><span class="d">Gmail clipping, dark mode and Outlook problems, before it goes out.</span></span><span class="arrow" aria-hidden="true">→</span></a>
  </div>
</div></section>

<section class="block"><div class="wrap">
  <h2>Careful by default</h2>
  <p class="muted" style="max-width:640px">Most viewers simply open the file. HTML Lens looks first, then lets you choose how much freedom the page gets.</p>
  <div class="cols3" style="margin-top:24px">
    <div class="card"><h3>Safe</h3><p class="muted">No scripts, no internet. The page can be read but can’t do anything — the right choice for attachments.</p></div>
    <div class="card"><h3>Offline</h3><p class="muted">Scripts run so interactive pages work, but nothing can reach the internet or send your data anywhere.</p></div>
    <div class="card"><h3>Online</h3><p class="muted">Behaves like a normal browser, for pages you trust that load libraries or data from the web.</p></div>
  </div>
  <p class="faint" style="margin-top:18px">Every file also gets a safety check for fake sign-in pages, password theft, data sent to Telegram bots, HTML smuggling and ClickFix lures.</p>
</div></section>

<section class="block"><div class="wrap">
  <h2>Questions</h2>
  ${faqHtml(homeFaq)}
</div></section>

<div class="cta-band"><div class="wrap">
  <div><h2 style="margin:0">Have a file to open right now?</h2><p class="muted" style="margin:6px 0 0">It takes a second, and it never leaves your device.</p></div>
  <div class="actions"><a class="btn btn-ink" href="/app/">Open the web app</a><a class="btn btn-line" href="/download/">Download for Mac or Windows</a></div>
</div></div>`,
}));

// ---------------------------------------------------------------- tool pages
for (const t of tools) {
  const path = `/${t.slug}/`;
  const appLink = t.tool ? `/app/#tool=${t.tool}` : t.paste ? '/app/#editor' : '/app/';
  page(path, layout({
    path, title: t.title, description: t.description,
    jsonld: [
      { '@context': 'https://schema.org', '@type': 'WebApplication', name: `HTML Lens — ${t.nav}`, url: SITE + path, applicationCategory: 'UtilitiesApplication',
        operatingSystem: 'Any (web browser)', offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' }, description: t.description },
      faqLd(t.faq), crumbLd([['HTML Lens', '/'], [t.nav, path]]),
    ],
    body: `
<section class="hero"><div class="wrap hero-grid">
  <div>
    <p class="kicker">${esc(t.kicker)}</p>
    <h1>${esc(t.h1)}</h1>
    <p class="lead">${esc(t.intro)}</p>
    <div class="actions">
      <a class="btn btn-ink" href="${appLink}" data-open-file>Open a file</a>
      <a class="btn btn-line" href="${appLink}">Open the full app</a>
    </div>
  </div>
  ${t.paste ? `<form class="paste-box" data-paste>
    <label for="code" class="title" style="font:500 26px/1.2 var(--serif);display:block;margin-bottom:10px">Paste your code</label>
    <textarea id="code" placeholder="&lt;!doctype html&gt;…" spellcheck="false"></textarea>
    <div class="actions" style="margin-top:12px"><button class="btn btn-ink" type="submit">Run it</button><span class="status faint" role="status"></span></div>
  </form>` : tray(t.accept)}
</div></section>

<section class="block"><div class="wrap">
  <h2>How it works</h2>
  <ol class="steps">${t.steps.map(([h, d]) => `<li><h3>${esc(h)}</h3><p class="muted">${esc(d)}</p></li>`).join('')}</ol>
</div></section>

<section class="block"><div class="wrap">
  <h2>What you get</h2>
  <div class="rule-list">${t.features.map(([h, d]) => `<div><b>${esc(h)}</b><span class="d">${esc(d)}</span></div>`).join('')}</div>
</div></section>

<section class="block"><div class="wrap">
  <h2>Questions</h2>
  ${faqHtml(t.faq)}
</div></section>

<section class="block"><div class="wrap">
  <h2>Related</h2>
  ${relatedHtml(t.related)}
</div></section>`,
  }));
}

// ---------------------------------------------------------------- tools index
page('/tools/', layout({
  path: '/tools/',
  title: 'Free HTML Tools — Viewer, MHTML Converter, Safety Check & More | HTML Lens',
  description: 'Every HTML Lens tool in one place: HTML, MHTML, EML and web archive viewers, an attachment phishing check, an HTML email tester and a code runner. All private and free.',
  jsonld: [crumbLd([['HTML Lens', '/'], ['Tools', '/tools/']])],
  body: `<section class="hero"><div class="wrap">
  <p class="kicker">Tools</p>
  <h1>Small tools for everyday HTML jobs.</h1>
  <p class="lead">Each one runs in your browser. Files are never uploaded anywhere.</p>
  ${relatedHtml(tools.map((t) => t.slug))}
</div></section>`,
}));

// ---------------------------------------------------------------- guides
for (const g of guides) {
  const path = `/guides/${g.slug}/`;
  page(path, layout({
    path, title: g.title, description: g.description,
    jsonld: [
      { '@context': 'https://schema.org', '@type': 'Article', headline: g.h1, description: g.description, dateModified: TODAY,
        author: { '@type': 'Organization', name: 'HTML Lens' }, publisher: { '@type': 'Organization', name: 'HTML Lens' }, mainEntityOfPage: SITE + path },
      crumbLd([['HTML Lens', '/'], ['Guides', path], [g.h1, path]]),
    ],
    body: `<div class="wrap"><article class="article">
  <p class="crumbs"><a href="/">HTML Lens</a> · Guide</p>
  <h1 style="font-size:clamp(32px,4.6vw,48px)">${esc(g.h1)}</h1>
  ${g.body}
  <h2>Related</h2>
  ${relatedHtml(g.related)}
</article></div>`,
  }));
}

// ---------------------------------------------------------------- download
const dl = (file) => (DOWNLOAD_BASE ? `${DOWNLOAD_BASE}/${file}` : null);
const dlBtn = (file, label) => {
  const href = dl(file);
  return href ? `<a class="btn btn-ink" href="${href}">${label}</a>` : `<a class="btn btn-ink" aria-disabled="true">Coming soon</a>`;
};
page('/download/', layout({
  path: '/download/',
  title: 'Download HTML Lens for Mac and Windows — Free HTML Viewer',
  description: 'Download the free HTML Lens desktop app for macOS (Apple Silicon and Intel) and Windows. Open HTML, MHTML, EML and ZIP files from Finder and Explorer, with live reload and PDF export.',
  jsonld: [{ ...appLd, operatingSystem: 'macOS 11+, Windows 10+', softwareVersion: VERSION, downloadUrl: SITE + '/download/' }, crumbLd([['HTML Lens', '/'], ['Download', '/download/']])],
  body: `<section class="hero"><div class="wrap">
  <p class="kicker">Desktop app · Version ${VERSION}</p>
  <h1>HTML Lens for Mac and Windows.</h1>
  <p class="lead">Everything the web app does, plus double-click to open .html, .mhtml and .eml files, live reload while you edit a page, and native print to PDF.</p>
  ${DOWNLOAD_BASE ? '' : '<p class="faint">The desktop builds are being prepared. Until then, the web app does everything the desktop app will — and you can install it from your browser.</p>'}
  <div class="dl-grid">
    <div class="card"><h3>macOS — Apple Silicon</h3><p class="muted">M1, M2, M3, M4 and newer. macOS 11 or later.</p>${dlBtn('HTML-Lens-mac-arm64.dmg', 'Download .dmg')}</div>
    <div class="card"><h3>macOS — Intel</h3><p class="muted">Intel Macs, macOS 11 or later.</p>${dlBtn('HTML-Lens-mac-intel.dmg', 'Download .dmg')}</div>
    <div class="card"><h3>Windows</h3><p class="muted">Windows 10 and 11, 64-bit.</p>${dlBtn('HTML-Lens-windows.msi', 'Download .msi')}</div>
  </div>
  <section class="block" style="padding-top:48px">
    <h2>Prefer not to install anything?</h2>
    <p class="muted" style="max-width:640px">The <a href="/app/">web app</a> runs in Chrome, Edge, Safari and Firefox, works offline once loaded, and can be installed as an app from the browser menu.</p>
    <h2 style="margin-top:36px">Opening the app for the first time</h2>
    <p class="muted" style="max-width:640px">If macOS says the app can’t be checked, right-click HTML Lens in Applications and choose <b>Open</b>. On Windows, if SmartScreen appears, choose <b>More info › Run anyway</b>. Both happen only once.</p>
  </section>
</div></section>`,
}));

// ---------------------------------------------------------------- privacy + 404
page('/privacy/', layout({
  path: '/privacy/', title: 'Privacy — HTML Lens', description: 'HTML Lens has no accounts, no analytics and no servers that receive your files.',
  body: `<div class="wrap"><article class="article">
  <h1 style="font-size:clamp(32px,4.6vw,48px)">Privacy</h1>
  <p>The short version: the files you open never leave your device.</p>
  <h2>Files</h2>
  <p>HTML Lens reads and renders files inside your browser (web) or on your computer (desktop). There is no upload step and no server that receives file contents. Files you open in the web app are kept in your browser’s own storage so they appear in your recent list; clear them any time in Settings.</p>
  <h2>Share links</h2>
  <p>A share link contains the page itself, compressed after the <code>#</code> in the address. Browsers don’t send that part to any server, so we never see it — but anyone you give the link to can open the page.</p>
  <h2>Accounts, cookies and analytics</h2>
  <p>There are no accounts, no cookies and no analytics or tracking scripts.</p>
  <h2>Hosting</h2>
  <p>The website is served by Cloudflare, which may keep standard technical logs such as IP addresses for security. The desktop app makes no network requests unless a page you open in Online mode does.</p>
</article></div>`,
}));
page('/404.html', layout({
  path: '/404.html', title: 'Page not found — HTML Lens', description: 'This page does not exist.', noindex: true,
  body: `<section class="hero"><div class="wrap"><p class="kicker">404</p><h1>That page isn’t here.</h1>
  <p class="lead">It may have moved. Try the <a href="/">home page</a>, the <a href="/tools/">list of tools</a>, or open the <a href="/app/">web app</a>.</p></div></section>`,
}));

// ---------------------------------------------------------------- write
rmSync(out, { recursive: true, force: true });
for (const p of pages) {
  const file = p.path.endsWith('.html') ? join(out, p.path) : join(out, p.path, 'index.html');
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, p.html);
}
const indexable = pages.filter((p) => !p.path.endsWith('.html'));
writeFileSync(join(out, 'sitemap.xml'), `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${indexable.map((p) => `  <url><loc>${SITE}${p.path}</loc><lastmod>${TODAY}</lastmod>${p.path === '/' ? '<priority>1.0</priority>' : ''}</url>`).join('\n')}
</urlset>
`);
writeFileSync(join(out, 'robots.txt'), `User-agent: *\nAllow: /\n\nSitemap: ${SITE}/sitemap.xml\n`);

// Assets
const assets = join(out, 'assets');
mkdirSync(join(assets, 'fonts'), { recursive: true });
cpSync(join(here, 'src/site.css'), join(assets, 'site.css'));
cpSync(join(here, 'src/site.js'), join(assets, 'site.js'));
const fonts = join(root, 'app/src/commonMain/composeResources/font');
for (const f of ['newsreader.ttf', 'plex_sans.ttf', 'plex_mono.ttf']) cpSync(join(fonts, f), join(assets, 'fonts', f));
const brand = join(root, 'branding/generated');
cpSync(join(brand, 'og-image.png'), join(assets, 'og-image.png'));
cpSync(join(brand, 'icon-180.png'), join(assets, 'apple-touch-icon.png'));
cpSync(join(brand, 'favicon.ico'), join(out, 'favicon.ico'));
cpSync(join(root, 'branding/logo.svg'), join(out, 'favicon.svg'));

// Web app under /app
const dist = join(root, 'app/build/dist/wasmJs/productionExecutable');
if (existsSync(join(dist, 'index.html'))) {
  cpSync(dist, join(out, 'app'), { recursive: true, filter: (src) => !src.endsWith('.map') });
  writeFileSync(join(out, 'app/config.js'), `window.HTMLLENS_CONFIG = ${JSON.stringify({ site: SITE, sandbox: SANDBOX })};\n`);
} else {
  console.warn('! Web app not built — run ./gradlew :app:wasmJsBrowserDistribution first. /app will be missing.');
}

writeFileSync(join(out, '_headers'), `/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: camera=(), microphone=(), geolocation=()
/assets/fonts/*
  Cache-Control: public, max-age=31536000, immutable
/assets/*
  Cache-Control: public, max-age=86400
/app/*.wasm
  Cache-Control: public, max-age=31536000, immutable
/app/composeResources/*
  Cache-Control: public, max-age=604800
/app/htmllens.js
  Cache-Control: no-cache
/app/config.js
  Cache-Control: no-cache
/app/hv-web.js
  Cache-Control: no-cache
`);
console.log(`Built ${pages.length} pages → ${out}`);
