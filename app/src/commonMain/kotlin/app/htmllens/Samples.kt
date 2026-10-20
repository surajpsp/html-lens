package app.htmllens

/** Built-in sample files and editor templates. Everything here is self-contained and offline. */
object Samples {

    val welcome = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Welcome to HTML Lens</title>
<style>
  :root { --bg:#f6f1e7; --card:#fbf8f1; --line:#e2d9c6; --text:#1e1a15; --muted:#574f42; --brand:#a8432a; --on-brand:#fff; }
  @media (prefers-color-scheme: dark) { :root { --bg:#1b1915; --card:#23201b; --line:#36302a; --text:#eee7da; --muted:#b8ae9c; --brand:#e58a63; --on-brand:#1b1915; } }
  * { box-sizing: border-box; }
  body { margin:0; font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif; background:var(--bg); color:var(--text); }
  header { padding:56px 24px 32px; text-align:center; }
  h1 { font:500 clamp(30px,5vw,46px)/1.1 Georgia,'Times New Roman',serif; margin:0 0 12px; letter-spacing:-.015em; }
  p.lead { color:var(--muted); max-width:620px; margin:0 auto; }
  main { max-width:960px; margin:0 auto; padding:0 24px 64px; display:grid; gap:16px; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); }
  .card { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:22px; }
  .card h2 { font:500 21px/1.25 Georgia,'Times New Roman',serif; margin:0 0 6px; }
  .card p { margin:0; color:var(--muted); font-size:14.5px; }
  button { margin-top:14px; background:var(--brand); color:var(--on-brand); border:0; border-radius:6px; padding:10px 16px; font:inherit; font-weight:600; cursor:pointer; }
  #count { font-weight:800; }
  code { background:rgba(168,67,42,.12); padding:2px 6px; border-radius:6px; font-size:13px; }
</style>
</head>
<body>
<header>
  <h1>Welcome to HTML Lens</h1>
  <p class="lead">This sample page runs inside the viewer. Try the console, the sandbox modes, device frames, find-in-page and the security scan from the toolbar.</p>
</header>
<main>
  <section class="card">
    <h2>Interactive</h2>
    <p>Scripts run in <b>Offline</b> and <b>Full</b> mode. In <b>Safe</b> mode this button does nothing.</p>
    <button id="btn">Clicked <span id="count">0</span> times</button>
  </section>
  <section class="card">
    <h2>Console</h2>
    <p>Open the console panel to see messages logged by this page, then run <code>document.title</code>.</p>
    <button onclick="console.log('Hello from the page!', { time: new Date().toLocaleTimeString() }); console.warn('Warnings show up too');">Log something</button>
  </section>
  <section class="card">
    <h2>Network</h2>
    <p>In Offline mode this request is blocked and shows up in the network panel.</p>
    <button onclick="fetch('https://example.com/data.json').then(r => console.log('Loaded', r.status)).catch(e => console.error('Blocked:', e.message))">Fetch from the internet</button>
  </section>
  <section class="card">
    <h2>Storage</h2>
    <p>Saved apps keep their own private storage. Visits on this page: <b id="visits">–</b></p>
  </section>
</main>
<script>
  let n = 0;
  document.getElementById('btn').addEventListener('click', () => { document.getElementById('count').textContent = ++n; });
  try { const v = +(localStorage.getItem('visits') || 0) + 1; localStorage.setItem('visits', v); document.getElementById('visits').textContent = v; } catch (e) {}
  console.info('Welcome page loaded');
</script>
</body>
</html>
"""

    /** A harmless imitation of a credential-phishing attachment, used to demonstrate the scanner. */
    val phishingDemo = """<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>Microsoft 365 - Sign in to view shared document</title>
<style>
 body{font-family:Segoe UI,Arial,sans-serif;background:#f2f2f2;display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
 .box{background:#fff;width:360px;padding:36px;box-shadow:0 2px 6px rgba(0,0,0,.2)}
 h1{font-size:22px;font-weight:600;margin:0 0 16px} input{width:100%;padding:8px 0;border:0;border-bottom:1px solid #666;margin:10px 0;font-size:15px}
 button{background:#0067b8;color:#fff;border:0;padding:8px 32px;float:right;margin-top:16px}
 .note{font-size:12px;color:#c00;margin-top:60px}
</style>
</head>
<body>
<div class="box">
  <h1>Sign in</h1>
  <p>Your document "Invoice_2026.pdf" is protected. Verify your account to continue.</p>
  <form id="f" action="https://login-microsoftonline.example.net/auth.php" method="post">
    <input type="email" name="login" placeholder="Email, phone, or Skype">
    <input type="password" name="passwd" placeholder="Password">
    <button type="submit">Sign in</button>
  </form>
  <p class="note">DEMO FILE — created by HTML Lens to show how the security scan works. Nothing is sent anywhere.</p>
</div>
<script>
  // Typical phishing-kit behaviour (disabled in this demo): forward credentials to a Telegram bot.
  var endpoint = "https://api.telegram.org/bot0000000000:DEMO/sendMessage";
  var payload = atob("ZGVtbw==");
  document.getElementById('f').addEventListener('submit', function (e) { e.preventDefault(); alert('Demo only – nothing was sent.'); });
</script>
</body>
</html>
"""

    val emailDemo = """From: HTML Lens <hello@htmllens.app>
To: You <you@example.com>
Subject: Your weekly report is ready
Date: Mon, 12 Oct 2026 09:30:00 +0000
MIME-Version: 1.0
Content-Type: multipart/alternative; boundary="b1"

--b1
Content-Type: text/plain; charset=utf-8

Your weekly report is ready. Open it at https://htmllens.app

--b1
Content-Type: text/html; charset=utf-8

<!doctype html><html><head><meta name="viewport" content="width=device-width"><title>Weekly report</title>
<style>@media (prefers-color-scheme: dark){ .bg{background:#111 !important} .card{background:#1d1d27 !important;color:#eee !important} }</style></head>
<body class="bg" style="margin:0;background:#f4f4f8;font-family:Arial,sans-serif">
<table width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px">
<table class="card" width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;color:#222">
<tr><td style="padding:32px">
<h1 style="margin:0 0 12px;font-size:24px">Your weekly report</h1>
<p style="font-size:15px;line-height:1.6">You opened <b>42 files</b> this week and blocked <b>3 suspicious</b> attachments. Nice work staying safe!</p>
<p><a href="https://htmllens.app" style="display:inline-block;background:#a8432a;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none">Open dashboard</a></p>
</td></tr></table>
</td></tr></table>
</body></html>
--b1--
"""

    val templates: List<Pair<String, String>> = listOf(
        "Blank page" to """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>My page</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 40px; }
  </style>
</head>
<body>
  <h1>Hello, world!</h1>
  <p>Start typing — the preview updates as you go.</p>
</body>
</html>
""",
        "Landing page" to """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Product launch</title>
<style>
  *{box-sizing:border-box} body{margin:0;font:17px/1.6 system-ui,sans-serif;color:#1d1d2b}
  .hero{min-height:70vh;display:grid;place-items:center;text-align:center;padding:48px 24px;background:radial-gradient(circle at 20% 20%,#f3e0d6,transparent 60%),radial-gradient(circle at 80% 0%,#efe6d2,transparent 50%)}
  h1{font-size:clamp(32px,6vw,60px);line-height:1.05;margin:0 0 16px;letter-spacing:-.03em}
  .cta{display:inline-block;margin-top:20px;background:#a8432a;color:#fff;padding:14px 24px;border-radius:12px;text-decoration:none;font-weight:600}
  .grid{max-width:1000px;margin:0 auto;padding:56px 24px;display:grid;gap:20px;grid-template-columns:repeat(auto-fit,minmax(240px,1fr))}
  .grid div{padding:24px;border-radius:16px;background:#f6f1e7}
</style>
</head>
<body>
<section class="hero"><div>
  <h1>Ship something<br>people love.</h1>
  <p>A clean starting point for your next idea.</p>
  <a class="cta" href="#features">See features</a>
</div></section>
<section class="grid" id="features">
  <div><h3>Fast</h3><p>Loads instantly and works offline.</p></div>
  <div><h3>Private</h3><p>Your data never leaves your device.</p></div>
  <div><h3>Beautiful</h3><p>Looks great on every screen.</p></div>
</section>
</body>
</html>
""",
        "Tailwind (CDN)" to """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Tailwind demo</title>
<script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-slate-50 min-h-screen grid place-items-center p-6">
  <div class="max-w-md w-full bg-white rounded-2xl shadow-xl p-8">
    <h1 class="text-2xl font-bold text-slate-900">Tailwind is ready</h1>
    <p class="mt-2 text-slate-600">This template loads Tailwind from a CDN, so it needs <b>Full</b> mode (network on).</p>
    <button class="mt-6 w-full rounded-xl bg-stone-900 py-3 font-semibold text-stone-50 hover:bg-stone-700">Get started</button>
  </div>
</body>
</html>
""",
        "Canvas game" to """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Catch the dot</title>
<style>html,body{margin:0;height:100%;background:#0f0f17;color:#fff;font-family:system-ui}canvas{display:block;width:100%;height:100%}#s{position:fixed;top:12px;left:16px;font-weight:700}</style>
</head>
<body>
<div id="s">Score: 0 · Best: 0</div>
<canvas id="c"></canvas>
<script>
const c = document.getElementById('c'), x = c.getContext('2d'), s = document.getElementById('s');
let W, H, dot, score = 0, best = +(localStorage.getItem('best') || 0), t = 0;
function size() { W = c.width = innerWidth * devicePixelRatio; H = c.height = innerHeight * devicePixelRatio; }
function spawn() { dot = { x: Math.random() * (W - 80) + 40, y: Math.random() * (H - 80) + 40, r: 28 * devicePixelRatio, born: performance.now() }; }
function hud() { s.textContent = 'Score: ' + score + ' · Best: ' + best; }
addEventListener('resize', size); size(); spawn(); hud();
c.addEventListener('pointerdown', e => {
  const px = e.clientX * devicePixelRatio, py = e.clientY * devicePixelRatio;
  if (Math.hypot(px - dot.x, py - dot.y) < dot.r * 1.4) { score++; if (score > best) { best = score; localStorage.setItem('best', best); } spawn(); hud(); }
});
(function loop(now) {
  t = now; x.clearRect(0, 0, W, H);
  const life = 1 - Math.min(1, (now - dot.born) / 1600);
  if (life <= 0) { score = 0; hud(); spawn(); }
  x.beginPath(); x.arc(dot.x, dot.y, dot.r * (0.6 + life * 0.4), 0, Math.PI * 2);
  x.fillStyle = 'hsl(' + (260 - life * 120) + ',90%,65%)'; x.fill();
  requestAnimationFrame(loop);
})(0);
</script>
</body>
</html>
""",
        "Chart (Chart.js)" to """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Sales dashboard</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js"></script>
<style>body{font-family:system-ui;margin:24px;background:#f6f1e7}.card{background:#fff;border-radius:16px;padding:20px;box-shadow:0 4px 18px rgba(0,0,0,.06);max-width:820px}</style>
</head>
<body>
<div class="card"><h2>Monthly sales</h2><canvas id="chart" height="120"></canvas></div>
<script>
new Chart(document.getElementById('chart'), {
  type: 'bar',
  data: { labels: ['Jan','Feb','Mar','Apr','May','Jun'], datasets: [{ label: 'Revenue', data: [12, 19, 14, 22, 28, 31], backgroundColor: '#a8432a', borderRadius: 8 }] },
  options: { plugins: { legend: { display: false } } }
});
</script>
</body>
</html>
""",
        "To-do app" to """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>To-do</title>
<style>
body{font-family:system-ui;background:#f6f1e7;margin:0;display:grid;place-items:start center;min-height:100vh;padding:48px 16px}
.app{width:100%;max-width:440px;background:#fff;border-radius:18px;padding:22px;box-shadow:0 10px 30px rgba(0,0,0,.07)}
form{display:flex;gap:8px}input{flex:1;padding:12px;border:1px solid #ddd;border-radius:10px;font:inherit}
button{border:0;border-radius:10px;padding:0 16px;background:#a8432a;color:#fff;font-weight:600}
li{list-style:none;display:flex;align-items:center;gap:10px;padding:10px 4px;border-bottom:1px solid #f0f0f0}
li.done span{text-decoration:line-through;color:#999} ul{padding:0;margin:16px 0 0} li button{margin-left:auto;background:none;color:#c33}
</style>
</head>
<body>
<div class="app">
  <h2>My tasks</h2>
  <form id="f"><input id="i" placeholder="Add a task…" autocomplete="off"><button>Add</button></form>
  <ul id="l"></ul>
</div>
<script>
let items = JSON.parse(localStorage.getItem('todos') || '[]');
const l = document.getElementById('l');
function save() { localStorage.setItem('todos', JSON.stringify(items)); render(); }
function render() {
  l.innerHTML = '';
  items.forEach((t, k) => {
    const li = document.createElement('li'); li.className = t.done ? 'done' : '';
    li.innerHTML = '<input type="checkbox"' + (t.done ? ' checked' : '') + '><span></span><button>✕</button>';
    li.querySelector('span').textContent = t.text;
    li.querySelector('input').onchange = () => { t.done = !t.done; save(); };
    li.querySelector('button').onclick = () => { items.splice(k, 1); save(); };
    l.appendChild(li);
  });
}
document.getElementById('f').onsubmit = e => { e.preventDefault(); const v = document.getElementById('i').value.trim(); if (v) { items.push({ text: v, done: false }); document.getElementById('i').value = ''; save(); } };
render();
</script>
</body>
</html>
""",
    )
}
