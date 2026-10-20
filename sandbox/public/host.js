/* Receives bundle files from the HTML Lens app (allowed origins only) and stores them for sw.js. */
(function () {
  const CACHE = 'hv-vfs';
  const allowed = (self.HV_CONFIG && self.HV_CONFIG.appOrigins) || [];
  const isAllowed = (o) => allowed.includes(o) || /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(o);

  function remember(id) {
    let list = [];
    try { list = JSON.parse(localStorage.getItem('hv-sessions') || '[]'); } catch (e) {}
    list = list.filter((x) => x !== id);
    list.push(id);
    const drop = list.splice(0, Math.max(0, list.length - 40));
    try { localStorage.setItem('hv-sessions', JSON.stringify(list)); } catch (e) {}
    return drop;
  }

  async function put(msg) {
    const cache = await caches.open(CACHE);
    const files = msg.files || [];
    await Promise.all(files.map((f) => cache.put(
      new Request('/vfs/' + msg.id + '/' + encodeURIComponent(f.path)),
      new Response(f.data, { headers: { 'Content-Type': f.mime || 'application/octet-stream' } }),
    )));
    await cache.put(new Request('/vfs/' + msg.id + '/__index'),
      new Response(JSON.stringify({ entry: msg.entry, files: files.map((f) => f.path) }), { headers: { 'Content-Type': 'application/json' } }));
    for (const old of remember(msg.id)) {
      const keys = await cache.keys();
      await Promise.all(keys.filter((k) => new URL(k.url).pathname.startsWith('/vfs/' + old + '/')).map((k) => cache.delete(k)));
    }
  }

  window.addEventListener('message', (e) => {
    if (!isAllowed(e.origin) || !e.data) return;
    if (e.data.hvhost !== 'put') return;
    put(e.data).then(
      () => e.source.postMessage({ hvhost: 'put-done', reqId: e.data.reqId }, e.origin),
      (err) => e.source.postMessage({ hvhost: 'put-done', reqId: e.data.reqId, error: String(err) }, e.origin),
    );
  });

  navigator.serviceWorker.register('/sw.js', { scope: '/' })
    .then(() => navigator.serviceWorker.ready)
    .then(() => { if (window.parent !== window) window.parent.postMessage({ hvhost: 'ready' }, '*'); })
    .catch((err) => console.error('HTML Lens sandbox: service worker failed', err));
})();
