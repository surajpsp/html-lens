// Hands files dropped on a landing page to the web app through IndexedDB (same origin), then opens it.
(function () {
  'use strict';
  function db() {
    return new Promise(function (res, rej) {
      var r = indexedDB.open('htmllens', 1);
      r.onupgradeneeded = function () { r.result.createObjectStore('blobs'); };
      r.onsuccess = function () { res(r.result); };
      r.onerror = function () { rej(r.error); };
    });
  }
  function handoff(entries, status) {
    if (!entries.length) return;
    if (status) status.textContent = 'Opening ' + (entries.length === 1 ? entries[0].name : entries.length + ' files') + '…';
    Promise.all(entries.map(function (e) {
      return e.data ? Promise.resolve(e) : e.file.arrayBuffer().then(function (buf) { return { name: e.file.name, path: e.file.webkitRelativePath || e.file.name, data: buf }; });
    })).then(function (files) {
      return db().then(function (d) {
        return new Promise(function (res, rej) {
          var t = d.transaction('blobs', 'readwrite');
          t.objectStore('blobs').put({ files: files }, 'handoff');
          t.oncomplete = res; t.onerror = function () { rej(t.error); };
        });
      });
    }).then(function () { location.href = '/app/#open=handoff'; })
      .catch(function (err) { if (status) status.textContent = 'Could not open the file: ' + err; });
  }
  function fromFiles(list) { return Array.prototype.map.call(list, function (f) { return { file: f, name: f.name }; }); }

  document.querySelectorAll('[data-tray]').forEach(function (tray) {
    var input = tray.querySelector('input[type=file]');
    var status = tray.querySelector('.status');
    tray.addEventListener('click', function (e) { if (e.target.tagName !== 'INPUT' && e.target.tagName !== 'A') input.click(); });
    tray.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); input.click(); } });
    input.addEventListener('change', function () { handoff(fromFiles(input.files || []), status); });
    tray.addEventListener('dragover', function (e) { e.preventDefault(); tray.classList.add('over'); });
    tray.addEventListener('dragleave', function () { tray.classList.remove('over'); });
    tray.addEventListener('drop', function (e) { e.preventDefault(); tray.classList.remove('over'); handoff(fromFiles(e.dataTransfer.files || []), status); });
  });
  document.querySelectorAll('[data-open-file]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      var tray = document.querySelector('[data-tray] input[type=file]');
      if (tray) { e.preventDefault(); tray.click(); }
    });
  });
  // Drops anywhere on the page work too.
  window.addEventListener('dragover', function (e) { if (document.querySelector('[data-tray]')) e.preventDefault(); });
  window.addEventListener('drop', function (e) {
    var tray = document.querySelector('[data-tray]');
    if (!tray || tray.contains(e.target)) return;
    e.preventDefault();
    handoff(fromFiles(e.dataTransfer.files || []), tray.querySelector('.status'));
  });
  var paste = document.querySelector('[data-paste]');
  if (paste) {
    paste.addEventListener('submit', function (e) {
      e.preventDefault();
      var code = paste.querySelector('textarea').value;
      if (!code.trim()) return;
      var data = new TextEncoder().encode(code).buffer;
      handoff([{ name: 'pasted.html', path: 'pasted.html', data: data }], paste.querySelector('.status'));
    });
  }
})();
