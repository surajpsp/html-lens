/*
 * HTML Lens bridge — injected as the first script of every page rendered in the viewer.
 * Reports console output, errors, network activity, scroll position and find results to the
 * host app, and executes host commands (find, scroll, print, screenshot, console eval).
 * Transport: CEF message router on desktop (t=cef), postMessage to the app frame on the web (t=web).
 */
(function () {
  'use strict';
  if (window.__hv) return;

  var script = document.currentScript;
  var params;
  try { params = new URL(script && script.src ? script.src : location.href, location.href).searchParams; } catch (e) { params = new URLSearchParams(''); }
  var T = params.get('t') || 'web';
  var MODE = params.get('m') || 'full';
  var NONCE = params.get('n') || '';
  var NS = params.get('a');
  var RESTORE_Y = +(params.get('y') || 0);
  var NET = params.get('net') === '1';
  var KEY = params.get('k') || '';
  var isRoot = T === 'web' ? window.parent === window.top : window === window.top;
  var target = T === 'web' ? window.top : null;

  var queue = [];
  var flushTimer = 0;

  function post(msg) {
    msg.__hv = 1;
    msg.root = isRoot;
    try {
      if (T === 'http') {
        var body = JSON.stringify(msg);
        if (navigator.sendBeacon && body.length < 60000) navigator.sendBeacon('/__hv/msg/' + KEY, new Blob([body], { type: 'text/plain' }));
        else fetch('/__hv/msg/' + KEY, { method: 'POST', body: body, keepalive: body.length < 60000, headers: { 'Content-Type': 'text/plain' } }).catch(function () {});
      } else if (T === 'cef') {
        if (window.cefQuery) window.cefQuery({ request: JSON.stringify(msg), persistent: false, onSuccess: function () {}, onFailure: function () {} });
      } else if (target && target !== window) {
        target.postMessage(msg, '*');
      }
    } catch (e) { /* host unavailable */ }
  }

  // Batch chatty messages (console/net) so heavy pages don't flood the host.
  function send(msg, immediate) {
    if (immediate) { post(msg); return; }
    queue.push(msg);
    if (!flushTimer) {
      flushTimer = setTimeout(function () {
        flushTimer = 0;
        var batch = queue.splice(0, 200);
        if (queue.length) send({ type: 'console', level: 'warn', args: ['[HTML Lens] ' + queue.length + ' more messages were dropped'] }, true), queue.length = 0;
        post({ type: 'batch', items: batch });
      }, 60);
    }
  }

  function describe(v, depth, seen) {
    depth = depth || 0; seen = seen || [];
    try {
      if (v === null) return 'null';
      if (v === undefined) return 'undefined';
      var t = typeof v;
      if (t === 'string') return depth ? JSON.stringify(v.length > 300 ? v.slice(0, 300) + '…' : v) : (v.length > 4000 ? v.slice(0, 4000) + '…' : v);
      if (t === 'number' || t === 'boolean' || t === 'bigint') return String(v);
      if (t === 'symbol') return v.toString();
      if (t === 'function') return 'ƒ ' + (v.name || 'anonymous') + '()';
      if (v instanceof Error) return (v.stack || (v.name + ': ' + v.message));
      if (typeof Element !== 'undefined' && v instanceof Element) {
        return '<' + v.tagName.toLowerCase() + (v.id ? '#' + v.id : '') + (typeof v.className === 'string' && v.className ? '.' + v.className.trim().split(/\s+/).join('.') : '') + '>';
      }
      if (seen.indexOf(v) >= 0) return '[Circular]';
      if (depth > 2) return Array.isArray(v) ? '[Array(' + v.length + ')]' : '{…}';
      seen.push(v);
      if (Array.isArray(v)) {
        var items = v.slice(0, 50).map(function (x) { return describe(x, depth + 1, seen); });
        return '[' + items.join(', ') + (v.length > 50 ? ', … ' + (v.length - 50) + ' more' : '') + ']';
      }
      var keys = Object.keys(v).slice(0, 30);
      var name = v.constructor && v.constructor.name && v.constructor.name !== 'Object' ? v.constructor.name + ' ' : '';
      return name + '{' + keys.map(function (k) { return k + ': ' + describe(v[k], depth + 1, seen); }).join(', ') + (Object.keys(v).length > 30 ? ', …' : '') + '}';
    } catch (e) {
      return '[unserializable]';
    }
  }

  // ---- Console capture -------------------------------------------------------
  ['log', 'info', 'warn', 'error', 'debug', 'trace', 'table', 'dir'].forEach(function (level) {
    var original = console[level];
    console[level] = function () {
      try {
        var args = Array.prototype.map.call(arguments, function (a) { return describe(a); });
        send({ type: 'console', level: level === 'table' || level === 'dir' || level === 'trace' ? 'log' : level, args: args });
      } catch (e) {}
      if (original) return original.apply(console, arguments);
    };
  });

  window.addEventListener('error', function (e) {
    var el = e.target;
    if (el && el !== window && el.tagName) {
      var url = el.currentSrc || el.src || el.href || '';
      if (url && url.indexOf('/__hv/') < 0) send({ type: 'net', url: url, kind: el.tagName.toLowerCase(), status: 0, failed: true });
      return;
    }
    send({ type: 'error', message: e.message || 'Script error', source: e.filename || '', line: e.lineno || 0, col: e.colno || 0, stack: e.error && e.error.stack ? String(e.error.stack) : '' });
  }, true);

  window.addEventListener('unhandledrejection', function (e) {
    var r = e.reason;
    send({ type: 'error', message: 'Unhandled promise rejection: ' + describe(r), source: '', line: 0, col: 0, stack: r && r.stack ? String(r.stack) : '' });
  });

  document.addEventListener('securitypolicyviolation', function (e) {
    var uri = e.blockedURI || '';
    if (uri === 'inline' || uri === 'eval') {
      send({ type: 'net', url: uri + ' script', kind: e.violatedDirective, status: 0, blocked: true });
    } else {
      send({ type: 'net', url: uri, kind: e.violatedDirective, status: 0, blocked: true });
    }
  });

  // ---- Network (resource timing) --------------------------------------------
  if (NET && typeof PerformanceObserver !== 'undefined') {
    try {
      new PerformanceObserver(function (list) {
        list.getEntries().forEach(function (en) {
          if (en.name.indexOf('/__hv/') >= 0) return;
          send({ type: 'net', url: en.name, kind: en.initiatorType, status: en.responseStatus || 0, duration: Math.round(en.duration), size: en.transferSize || en.encodedBodySize || 0 });
        });
      }).observe({ type: 'resource', buffered: true });
    } catch (e) {}
  }

  // ---- Storage isolation for the web sandbox ---------------------------------
  if (NS && T === 'web') {
    var prefix = 'hl:' + NS + ':';
    var wrap = function (real) {
      var own = function () {
        var out = [];
        for (var i = 0; i < real.length; i++) { var k = real.key(i); if (k && k.indexOf(prefix) === 0) out.push(k.slice(prefix.length)); }
        return out;
      };
      var api = {
        getItem: function (k) { return real.getItem(prefix + k); },
        setItem: function (k, v) { real.setItem(prefix + k, String(v)); },
        removeItem: function (k) { real.removeItem(prefix + k); },
        clear: function () { own().forEach(function (k) { real.removeItem(prefix + k); }); },
        key: function (i) { var k = own()[i]; return k === undefined ? null : k; }
      };
      Object.defineProperty(api, 'length', { get: function () { return own().length; } });
      return new Proxy(api, {
        get: function (t, p) { if (p in t) return t[p]; var v = real.getItem(prefix + String(p)); return v === null ? undefined : v; },
        set: function (t, p, v) { real.setItem(prefix + String(p), String(v)); return true; },
        deleteProperty: function (t, p) { real.removeItem(prefix + String(p)); return true; },
        has: function (t, p) { return p in t || real.getItem(prefix + String(p)) !== null; },
        ownKeys: function () { return own(); },
        getOwnPropertyDescriptor: function (t, p) {
          var v = real.getItem(prefix + String(p));
          return v === null ? undefined : { value: v, enumerable: true, configurable: true, writable: true };
        }
      });
    };
    try {
      var ls = window.localStorage, ss = window.sessionStorage;
      Object.defineProperty(window, 'localStorage', { value: wrap(ls), configurable: true });
      Object.defineProperty(window, 'sessionStorage', { value: wrap(ss), configurable: true });
    } catch (e) {}
    try {
      var open = IDBFactory.prototype.open, del = IDBFactory.prototype.deleteDatabase;
      IDBFactory.prototype.open = function (name, version) { return version === undefined ? open.call(this, prefix + name) : open.call(this, prefix + name, version); };
      IDBFactory.prototype.deleteDatabase = function (name) { return del.call(this, prefix + name); };
    } catch (e) {}
  }

  // ---- Links -----------------------------------------------------------------
  document.addEventListener('click', function (e) {
    if (e.defaultPrevented || e.button !== 0) return;
    var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
    if (!a) return;
    var href = a.getAttribute('href') || '';
    if (/^(javascript:|#)/i.test(href)) return;
    var url;
    try { url = new URL(a.href, location.href); } catch (err) { return; }
    if (url.protocol === 'mailto:' || url.protocol === 'tel:') { e.preventDefault(); post({ type: 'open', url: url.href, external: true }); return; }
    if (url.origin !== location.origin) {
      e.preventDefault();
      post({ type: 'open', url: url.href, external: true });
    } else if (a.target && a.target !== '_self' && a.target !== '_parent' && a.target !== '_top') {
      e.preventDefault();
      location.href = url.href;
    }
  }, true);

  // ---- Downloads (desktop WebView has no download manager) ----------------------
  if (T === 'http') {
    document.addEventListener('click', function (e) {
      var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
      if (!a || e.defaultPrevented) return;
      e.preventDefault();
      var name = a.getAttribute('download') || (a.href.split('/').pop() || 'download').split('?')[0];
      fetch(a.href).then(function (r) { return r.blob(); }).then(function (blob) {
        var reader = new FileReader();
        reader.onload = function () { post({ type: 'download', name: name, data: String(reader.result) }); };
        reader.readAsDataURL(blob);
      }).catch(function (err) { post({ type: 'batch', items: [{ type: 'console', level: 'error', args: ['Download failed: ' + err] }] }); });
    }, true);
  }

  // ---- File drops land in the app, not the page (web) ---------------------------
  if (T === 'web' && isRoot) {
    window.addEventListener('dragenter', function (e) {
      if (e.dataTransfer && Array.prototype.indexOf.call(e.dataTransfer.types || [], 'Files') >= 0) {
        try { window.top.postMessage({ __hv: 1, type: 'dragenter' }, '*'); } catch (err) {}
      }
    });
  }

  // ---- Page state --------------------------------------------------------------
  var lastScroll = 0;
  window.addEventListener('scroll', function () {
    var now = Date.now();
    if (now - lastScroll < 400) return;
    lastScroll = now;
    if (isRoot) post({ type: 'scroll', y: Math.round(window.scrollY) });
  }, { passive: true });

  function pageInfo() {
    return { type: 'page', path: decodeURIComponent(location.pathname.replace(/^\//, '')), hash: location.hash, title: document.title || '', height: document.documentElement.scrollHeight };
  }

  function flash(el) {
    if (!el) return;
    el.scrollIntoView({ block: 'center' });
    var prev = el.style.outline, prevBg = el.style.backgroundColor;
    el.style.outline = '3px solid #7c5cff';
    el.style.backgroundColor = 'rgba(124,92,255,.12)';
    setTimeout(function () { el.style.outline = prev; el.style.backgroundColor = prevBg; }, 1800);
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!isRoot) return;
    post(pageInfo());
    if (location.hash.length > 1) {
      var id = decodeURIComponent(location.hash.slice(1));
      setTimeout(function () { flash(document.getElementById(id)); }, 50);
    } else if (RESTORE_Y > 0) {
      window.scrollTo(0, RESTORE_Y);
      setTimeout(function () { if (window.scrollY < RESTORE_Y - 50) window.scrollTo(0, RESTORE_Y); }, 400);
    }
    try {
      var titleEl = document.querySelector('title');
      if (titleEl) new MutationObserver(function () { post({ type: 'title', title: document.title }); }).observe(titleEl, { childList: true, characterData: true, subtree: true });
    } catch (e) {}
  });
  window.addEventListener('load', function () { if (isRoot) post({ type: 'loaded', height: document.documentElement.scrollHeight }); });
  window.addEventListener('hashchange', function () { if (isRoot) post(pageInfo()); });

  // ---- Find in page (CSS Custom Highlight API with window.find fallback) ------
  var find = { q: '', ranges: [], idx: -1 };
  var styleAdded = false;
  function addFindStyle() {
    if (styleAdded) return;
    styleAdded = true;
    var st = document.createElement('style');
    if (NONCE) st.nonce = NONCE;
    st.textContent = '::highlight(hv-find){background-color:#ffe066;color:inherit}::highlight(hv-cur){background-color:#ff9632;color:#000}';
    (document.head || document.documentElement).appendChild(st);
  }
  function collect(q) {
    var out = [];
    var lower = q.toLowerCase();
    if (!document.body) return out;
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n) {
        var p = n.parentElement;
        if (!p || /^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE)$/.test(p.tagName)) return NodeFilter.FILTER_REJECT;
        return NodeFilter.FILTER_ACCEPT;
      }
    });
    var node;
    while ((node = walker.nextNode()) && out.length < 5000) {
      var text = node.nodeValue.toLowerCase();
      var i = text.indexOf(lower);
      while (i >= 0 && out.length < 5000) {
        var r = document.createRange();
        r.setStart(node, i); r.setEnd(node, i + q.length);
        out.push(r);
        i = text.indexOf(lower, i + q.length);
      }
    }
    return out;
  }
  function doFind(q, dir) {
    if (!q) { clearFind(); post({ type: 'find', count: 0, current: 0 }); return; }
    var hasHighlights = typeof CSS !== 'undefined' && CSS.highlights && typeof Highlight !== 'undefined';
    if (!hasHighlights) {
      var found = window.find ? window.find(q, false, dir < 0, true) : false;
      post({ type: 'find', count: found ? -1 : 0, current: found ? -1 : 0 });
      return;
    }
    addFindStyle();
    if (q !== find.q) {
      find = { q: q, ranges: collect(q), idx: -1 };
      CSS.highlights.set('hv-find', new Highlight(...find.ranges));
    }
    var n = find.ranges.length;
    if (!n) { CSS.highlights.delete('hv-cur'); post({ type: 'find', count: 0, current: 0 }); return; }
    find.idx = ((find.idx + (dir || 1)) % n + n) % n;
    var cur = find.ranges[find.idx];
    CSS.highlights.set('hv-cur', new Highlight(cur));
    var el = cur.startContainer.parentElement;
    if (el) {
      var rect = cur.getBoundingClientRect();
      if (rect.top < 60 || rect.bottom > window.innerHeight - 60) el.scrollIntoView({ block: 'center' });
    }
    post({ type: 'find', count: n, current: find.idx + 1 });
  }
  function clearFind() {
    find = { q: '', ranges: [], idx: -1 };
    try { CSS.highlights.delete('hv-find'); CSS.highlights.delete('hv-cur'); } catch (e) {}
  }

  // ---- Screenshot ------------------------------------------------------------
  function snap() {
    function run() {
      var doc = document.documentElement;
      var w = Math.max(doc.scrollWidth, document.body ? document.body.scrollWidth : 0);
      var h = Math.min(Math.max(doc.scrollHeight, document.body ? document.body.scrollHeight : 0), 16000);
      window.modernScreenshot.domToPng(doc, { width: w, height: h, scale: Math.min(2, window.devicePixelRatio || 1), backgroundColor: getComputedStyle(document.body || doc).backgroundColor || '#ffffff' })
        .then(function (data) { post({ type: 'snap', data: data }); })
        .catch(function (err) { post({ type: 'snap', error: String(err && err.message || err) }); });
    }
    if (window.modernScreenshot) return run();
    var s = document.createElement('script');
    s.src = '/__hv/snap.js';
    if (NONCE) s.nonce = NONCE;
    s.onload = run;
    s.onerror = function () { post({ type: 'snap', error: 'Screenshot helper could not load' }); };
    (document.head || document.documentElement).appendChild(s);
  }

  // ---- Commands from the host --------------------------------------------------
  function cmd(c) {
    if (!c || !c.op) return;
    switch (c.op) {
      case 'find': doFind(c.text || '', c.dir || 1); break;
      case 'findClear': clearFind(); break;
      case 'scrollTo': window.scrollTo(0, c.y || 0); break;
      case 'heading': {
        var hs = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
        flash(hs[c.index]);
        break;
      }
      case 'anchor': flash(document.getElementById(c.id)); break;
      case 'print': window.print(); break;
      case 'snap': snap(); break;
      case 'eval': {
        try {
          var result = (0, eval)(c.code);
          post({ type: 'batch', items: [{ type: 'console', level: 'result', args: [describe(result)] }] });
        } catch (err) {
          post({ type: 'batch', items: [{ type: 'console', level: 'error', args: [err && err.stack ? String(err.stack) : String(err)] }] });
        }
        break;
      }
      case 'info': post(pageInfo()); break;
      case 'back': history.back(); break;
      case 'forward': history.forward(); break;
    }
  }

  window.__hv = { cmd: cmd, version: 1, mode: MODE };
  if (T === 'web') {
    window.addEventListener('message', function (e) {
      if (e.source !== window.parent) return;
      var d = e.data;
      if (d && d.__hvcmd) cmd(d);
    });
  }
})();
