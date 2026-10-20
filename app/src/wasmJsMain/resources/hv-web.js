/* Browser helpers for the HTML Lens web app (called from Kotlin/Wasm). Everything stays on this device. */
(function () {
  'use strict';
  const g = window;

  // ---- Config -------------------------------------------------------------------
  g.hvSandboxOrigin = function () {
    const cfg = g.HTMLLENS_CONFIG || {};
    if (cfg.sandbox) return String(cfg.sandbox).replace(/\/$/, '');
    const h = location.hostname;
    if (h === 'localhost' || h === '127.0.0.1') return location.protocol + '//127.0.0.1:' + (Number(location.port || 80) + 1);
    return location.protocol + '//sandbox.' + h.replace(/^www\./, '');
  };
  g.hvSiteUrl = function () { return (g.HTMLLENS_CONFIG && g.HTMLLENS_CONFIG.site) || location.origin; };
  g.hvAppUrl = function () { return location.origin + location.pathname.replace(/[^/]*$/, ''); };
  g.hvIsMac = function () { return /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent); };
  g.hvHash = function () { return location.hash || ''; };
  g.hvClearHash = function () { history.replaceState(null, '', location.pathname + location.search); };
  g.hvOnHashChange = function (cb) { window.addEventListener('hashchange', () => { if (location.hash.length > 1) cb(); }); };

  // ---- IndexedDB blob store -------------------------------------------------------
  let dbp = null;
  function db() {
    if (!dbp) dbp = new Promise((res, rej) => {
      const r = indexedDB.open('htmllens', 1);
      r.onupgradeneeded = () => r.result.createObjectStore('blobs');
      r.onsuccess = () => res(r.result);
      r.onerror = () => rej(r.error);
    });
    return dbp;
  }
  function tx(mode, fn) {
    return db().then((d) => new Promise((res, rej) => {
      const t = d.transaction('blobs', mode);
      const s = t.objectStore('blobs');
      let out;
      Promise.resolve(fn(s, (v) => { out = v; })).catch(rej);
      t.oncomplete = () => res(out === undefined ? null : out);
      t.onerror = () => rej(t.error);
    }));
  }
  g.hvIdbGet = (key) => tx('readonly', (s, set) => { const r = s.get(key); r.onsuccess = () => set(r.result ? new Uint8Array(r.result) : null); });
  g.hvIdbPut = (key, u8) => tx('readwrite', (s) => { s.put(u8.slice().buffer, key); });
  g.hvIdbDelete = (key) => tx('readwrite', (s) => { s.delete(key); });
  g.hvIdbDeletePrefix = (prefix) => tx('readwrite', (s) => { s.delete(IDBKeyRange.bound(prefix, prefix + '￿')); });
  g.hvUsage = () => (navigator.storage && navigator.storage.estimate ? navigator.storage.estimate().then((e) => e.usage || 0) : Promise.resolve(0));
  try { navigator.storage && navigator.storage.persist && navigator.storage.persist(); } catch (e) {}

  // ---- Files ------------------------------------------------------------------------
  function readFile(file, path) {
    return file.arrayBuffer().then((buf) => ({ name: file.name, path: path || file.webkitRelativePath || file.name, data: new Uint8Array(buf) }));
  }
  g.hvReadFiles = (files) => Promise.all(Array.from(files).map((f) => readFile(f)));
  g.hvPickFiles = function (multiple, directory) {
    return new Promise((resolve) => {
      const input = document.createElement('input');
      input.type = 'file';
      input.multiple = !!multiple;
      if (directory) { input.webkitdirectory = true; input.setAttribute('webkitdirectory', ''); }
      else input.accept = '.html,.htm,.xhtml,.mht,.mhtml,.zip,.eml,.webarchive,.svg,text/html,application/zip,message/rfc822';
      input.style.display = 'none';
      document.body.appendChild(input);
      let done = false;
      const finish = (v) => { if (!done) { done = true; input.remove(); resolve(v); } };
      input.addEventListener('change', () => g.hvReadFiles(input.files || []).then(finish));
      input.addEventListener('cancel', () => finish([]));
      input.click();
    });
  };
  g.hvDownload = function (name, mime, u8) {
    const url = URL.createObjectURL(new Blob([u8], { type: mime || 'application/octet-stream' }));
    const a = document.createElement('a');
    a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  };
  g.hvClipboardRead = () => (navigator.clipboard && navigator.clipboard.readText ? navigator.clipboard.readText().catch(() => null) : Promise.resolve(null));
  g.hvClipboardWrite = (t) => { try { navigator.clipboard.writeText(t); } catch (e) {} };
  g.hvOpen = (url) => { window.open(url, '_blank', 'noopener'); };

  // Drag & drop of files and whole folders anywhere on the page.
  function walkEntry(entry, prefix, out) {
    return new Promise((resolve) => {
      if (entry.isFile) {
        entry.file((f) => readFile(f, prefix + f.name).then((r) => { out.push(r); resolve(); }), () => resolve());
      } else if (entry.isDirectory) {
        const reader = entry.createReader();
        const all = [];
        const read = () => reader.readEntries((batch) => {
          if (!batch.length) Promise.all(all.map((e) => walkEntry(e, prefix + entry.name + '/', out))).then(() => resolve());
          else { all.push(...batch); read(); }
        }, () => resolve());
        read();
      } else resolve();
    });
  }
  let dropEl = null, dropHandler = null;
  function overlay() {
    if (dropEl) return dropEl;
    dropEl = document.createElement('div');
    dropEl.id = 'hv-drop';
    dropEl.innerHTML = '<div><svg width="52" height="52" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 15V3m0 0 4 4m-4-4-4 4"/><path d="M4 15v3a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3v-3"/></svg>' +
      '<b>Drop to open</b><span>HTML, MHTML, ZIP, EML, Web Archive, SVG or a whole folder</span></div>';
    dropEl.style.cssText = 'position:fixed;inset:0;z-index:2147483647;display:none;align-items:center;justify-content:center;background:rgba(16,16,24,.55);padding:24px;box-sizing:border-box;font:500 15px system-ui,-apple-system,Segoe UI,sans-serif';
    const inner = dropEl.firstChild;
    inner.style.cssText = 'width:100%;height:100%;border:2px dashed #8b7bff;border-radius:24px;background:rgba(21,21,30,.92);color:#ececf4;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;pointer-events:none';
    inner.querySelector('svg').style.color = '#8b7bff';
    inner.querySelector('b').style.fontSize = '22px';
    inner.querySelector('span').style.color = '#a2a4ba';
    dropEl.addEventListener('dragover', (e) => e.preventDefault());
    dropEl.addEventListener('dragleave', (e) => { if (e.target === dropEl) hide(); });
    dropEl.addEventListener('drop', (e) => {
      e.preventDefault(); hide();
      const items = Array.from(e.dataTransfer.items || []);
      const entries = items.map((i) => (i.webkitGetAsEntry ? i.webkitGetAsEntry() : null)).filter(Boolean);
      const out = [];
      const p = entries.length ? Promise.all(entries.map((en) => walkEntry(en, '', out))).then(() => out) : g.hvReadFiles(e.dataTransfer.files);
      p.then((files) => { const dir = entries.length === 1 && entries[0].isDirectory ? entries[0].name : ''; if (files.length && dropHandler) dropHandler(files, dir); });
    });
    document.body.appendChild(dropEl);
    return dropEl;
  }
  function hide() { if (dropEl) dropEl.style.display = 'none'; }
  g.hvShowDrop = () => { overlay().style.display = 'flex'; };
  g.hvInstallDrop = function (onDrop) {
    dropHandler = onDrop;
    const hasFiles = (e) => e.dataTransfer && Array.prototype.indexOf.call(e.dataTransfer.types || [], 'Files') >= 0;
    window.addEventListener('dragenter', (e) => { if (hasFiles(e)) { e.preventDefault(); g.hvShowDrop(); } });
    window.addEventListener('dragover', (e) => { if (hasFiles(e)) e.preventDefault(); });
    window.addEventListener('drop', (e) => { if (hasFiles(e)) e.preventDefault(); });
    window.addEventListener('keydown', (e) => { if (e.key === 'Escape') hide(); });
    if ('launchQueue' in window) {
      window.launchQueue.setConsumer((params) => {
        if (!params.files || !params.files.length) return;
        Promise.all(params.files.map((h) => h.getFile())).then((fs) => g.hvReadFiles(fs)).then((files) => onDrop(files, ''));
      });
    }
  };
  // Landing pages hand dropped files to the app through IndexedDB (key "handoff").
  g.hvTakeHandoff = function () {
    return tx('readwrite', (s, set) => {
      const r = s.get('handoff');
      r.onsuccess = () => { set(r.result || null); s.delete('handoff'); };
    }).then((v) => (v && v.files ? v.files.map((f) => ({ name: f.name, path: f.path || f.name, data: new Uint8Array(f.data) })) : []));
  };
  g.hvInstallPaste = function (onText, onFiles) {
    document.addEventListener('paste', (e) => {
      const t = e.target;
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) {
        // Compose keeps a hidden textarea focused even when no field is active; only skip real, visible fields.
        const r = t.getBoundingClientRect ? t.getBoundingClientRect() : { width: 1 };
        if (r.width > 2 && r.height > 2) return;
      }
      const cd = e.clipboardData;
      if (!cd) return;
      if (cd.files && cd.files.length) { e.preventDefault(); g.hvReadFiles(cd.files).then((fs) => onFiles(fs)); return; }
      const text = cd.getData('text/plain') || cd.getData('text/html');
      if (text) { if (onText(text)) e.preventDefault(); }
    });
  };
  g.hvFilesLength = (a) => a.length;
  g.hvFileAt = (a, i) => a[i];

  // ---- Frames: viewer and editor iframes, message routing ---------------------------
  const frames = new Map(); // id -> iframe (windows are looked up at message time: re-parenting replaces them)
  let listener = null;
  g.hvRegisterFrame = (iframe, id) => { frames.set(id, iframe); };
  g.hvUnregisterFrame = (iframe) => { for (const [id, f] of frames) if (f === iframe) frames.delete(id); };
  function frameIdFor(win) {
    for (const [id, f] of frames) { try { if (f.contentWindow === win) return id; } catch (e) {} }
    return undefined;
  }
  g.hvOnFrameMessage = function (cb) {
    if (listener) window.removeEventListener('message', listener);
    listener = (e) => {
      const d = e.data;
      if (!d || typeof d !== 'object' || !(d.__hv || d.__hved)) return;
      let w = e.source, id;
      for (let i = 0; w && i < 12; i++) {
        id = frameIdFor(w);
        if (id !== undefined) break;
        if (w === w.parent) break;
        try { w = w.parent; } catch (err) { break; }
      }
      if (id === undefined) return;
      try { cb(id, JSON.stringify(d)); } catch (err) {}
    };
    window.addEventListener('message', listener);
  };
  g.hvPostToFrame = (iframe, json, origin) => { try { iframe.contentWindow.postMessage(JSON.parse(json), origin); } catch (e) {} };

  // ---- Sandbox host: hidden iframe on the sandbox origin that stores bundle files ----
  let hostFrame = null, hostReady = null, hostListener = null, reqSeq = 0;
  const pending = new Map();
  g.hvSandboxInit = function () {
    if (hostReady) return hostReady;
    const origin = g.hvSandboxOrigin();
    hostReady = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Sandbox did not respond: ' + origin)), 20000);
      hostListener = (e) => {
        if (e.origin !== origin || !e.data || !e.data.hvhost) return;
        if (e.data.hvhost === 'ready') { clearTimeout(timer); resolve(); }
        else if (e.data.reqId && pending.has(e.data.reqId)) { pending.get(e.data.reqId)(e.data); pending.delete(e.data.reqId); }
      };
      window.addEventListener('message', hostListener);
      hostFrame = document.createElement('iframe');
      hostFrame.src = origin + '/host';
      hostFrame.title = 'sandbox';
      hostFrame.style.cssText = 'position:absolute;width:0;height:0;border:0;visibility:hidden';
      document.body.appendChild(hostFrame);
    }).catch((err) => {
      // Let the next preview retry from scratch instead of failing until the app is reloaded.
      window.removeEventListener('message', hostListener);
      if (hostFrame) hostFrame.remove();
      hostFrame = null; hostReady = null; hostListener = null;
      throw err;
    });
    return hostReady;
  };
  g.hvNewArray = () => [];
  g.hvPushFile = (arr, path, mime, u8) => { arr.push({ path, mime, data: u8 }); };
  g.hvSandboxPut = function (id, entry, files) {
    return g.hvSandboxInit().then(() => new Promise((resolve, reject) => {
      const reqId = ++reqSeq;
      const timer = setTimeout(() => { pending.delete(reqId); reject(new Error('Sandbox did not store the files in time')); }, 60000);
      pending.set(reqId, (d) => { clearTimeout(timer); if (d.error) reject(new Error(d.error)); else resolve(); });
      hostFrame.contentWindow.postMessage({ hvhost: 'put', reqId, id, entry, files }, g.hvSandboxOrigin());
    }));
  };

  // ---- Boot splash ---------------------------------------------------------------
  g.hvHideSplash = () => { const s = document.getElementById('splash'); if (s) { s.style.opacity = '0'; setTimeout(() => s.remove(), 300); } };
})();
