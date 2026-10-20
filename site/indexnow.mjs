// Submits every page in the sitemap to IndexNow (Bing, Yandex, Seznam, Naver…). Run after deploying.
//   node site/indexnow.mjs
import { readFileSync } from 'node:fs';
const build = readFileSync(new URL('./build.mjs', import.meta.url), 'utf8');
const key = build.match(/INDEXNOW_KEY = '([0-9a-f]+)'/)[1];
const site = (process.env.SITE_URL || 'https://htmllens.silentdev.online').replace(/\/$/, '');
const xml = await (await fetch(`${site}/sitemap.xml`)).text();
const urlList = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
const res = await fetch('https://api.indexnow.org/indexnow', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json; charset=utf-8' },
  body: JSON.stringify({ host: new URL(site).host, key, keyLocation: `${site}/${key}.txt`, urlList }),
});
console.log(`IndexNow: ${res.status} ${res.statusText} — submitted ${urlList.length} URLs`);
