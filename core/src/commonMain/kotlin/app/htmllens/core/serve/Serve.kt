package app.htmllens.core.serve

import app.htmllens.core.bundle.VFile
import app.htmllens.core.util.Charsets
import app.htmllens.core.util.MimeTypes
import app.htmllens.core.util.latin1
import app.htmllens.core.util.latin1Bytes
import kotlinx.serialization.Serializable

@Serializable
enum class SandboxMode(val label: String, val description: String) {
    SAFE("Safe", "No scripts, no internet. Best for attachments you don't trust."),
    OFFLINE("Offline", "Scripts run, but nothing can reach the internet."),
    FULL("Online", "Behaves like a normal browser, including internet access."),
}

/** Per-view options that shape how bundle files are served to the rendering engine. */
data class ServeConfig(
    val mode: SandboxMode,
    val nonce: String,
    val transport: String,
    val darkSim: Boolean = false,
    val imagesOff: Boolean = false,
    val appId: String? = null,
    val scrollY: Int = 0,
    val captureNetwork: Boolean = true,
    /** Message channel id for the "http" transport (desktop local server). */
    val channel: String? = null,
)

/**
 * Builds HTTP responses for virtual bundle files: CSP per sandbox mode, the injected bridge
 * script, meta-refresh neutralisation and dark-mode simulation. The web sandbox service worker
 * (sandbox/sw.js) mirrors this logic in JavaScript; keep them in sync.
 */
object Serve {
    const val BRIDGE_PATH = "__hv/bridge.js"
    const val SNAP_PATH = "__hv/snap.js"

    fun csp(cfg: ServeConfig): String? {
        val img = if (cfg.imagesOff) "'none'" else "'self' data: blob:"
        return when (cfg.mode) {
            SandboxMode.SAFE -> "default-src 'self' data: blob:; script-src 'nonce-${cfg.nonce}'; style-src 'self' 'unsafe-inline' data:; " +
                "img-src $img; font-src 'self' data:; media-src 'self' data: blob:; connect-src 'self'; frame-src 'self'; " +
                "object-src 'none'; form-action 'none'; base-uri 'self'"
            SandboxMode.OFFLINE -> "default-src 'self' data: blob: 'unsafe-inline' 'unsafe-eval'; " +
                "script-src 'self' data: blob: 'unsafe-inline' 'unsafe-eval'; style-src 'self' data: blob: 'unsafe-inline'; " +
                "img-src $img; font-src 'self' data: blob:; media-src 'self' data: blob:; connect-src 'self' data: blob:; " +
                "frame-src 'self' data: blob:; worker-src 'self' data: blob:; object-src 'none'; form-action 'self'"
            SandboxMode.FULL -> if (cfg.imagesOff) "img-src 'none'" else null
        }
    }

    fun bridgeQuery(cfg: ServeConfig): String = buildString {
        append("t=").append(cfg.transport)
        append("&m=").append(cfg.mode.name.lowercase())
        append("&n=").append(cfg.nonce)
        if (cfg.appId != null) append("&a=").append(cfg.appId)
        if (cfg.scrollY > 0) append("&y=").append(cfg.scrollY)
        if (cfg.captureNetwork) append("&net=1")
        if (cfg.channel != null) append("&k=").append(cfg.channel)
    }

    fun bridgeTag(cfg: ServeConfig): String {
        val nonce = if (cfg.mode == SandboxMode.SAFE) " nonce=\"${cfg.nonce}\"" else ""
        return "<script src=\"/$BRIDGE_PATH?${bridgeQuery(cfg)}\"$nonce></script>"
    }

    fun headers(file: VFile, cfg: ServeConfig): Map<String, String> = buildMap {
        put("Content-Type", file.mime)
        put("Cache-Control", "no-store")
        // same-origin lets the desktop server resolve root-absolute URLs ("/css/x.css") via the Referer.
        put("Referrer-Policy", "same-origin")
        csp(cfg)?.let { put("Content-Security-Policy", it) }
    }

    fun body(file: VFile, cfg: ServeConfig): ByteArray = when {
        file.isHtml -> transformHtml(file.bytes, file.mime, cfg)
        MimeTypes.isCss(file.mime) && cfg.darkSim -> darkCss(file.bytes)
        else -> file.bytes
    }

    private val META_REFRESH = Regex("""(<meta[^>]+http-equiv\s*=\s*["']?)refresh""", RegexOption.IGNORE_CASE)
    private val HEAD = Regex("""<head(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val HTML = Regex("""<html(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val DOCTYPE = Regex("""<!doctype[^>]*>""", RegexOption.IGNORE_CASE)
    private val DARK = Regex("""prefers-color-scheme\s*:\s*dark""", RegexOption.IGNORE_CASE)
    private val LIGHT = Regex("""prefers-color-scheme\s*:\s*light""", RegexOption.IGNORE_CASE)
    const val INVERT_CSS = "html{background:#fff;filter:invert(1) hue-rotate(180deg)}" +
        "img,video,picture,canvas,iframe,[style*=\"background-image\"]{filter:invert(1) hue-rotate(180deg)}"

    fun transformHtml(bytes: ByteArray, mime: String, cfg: ServeConfig): ByteArray {
        val charset = mime.substringAfter("charset=", "").ifEmpty { Charsets.detectHtml(bytes) }
        if (Charsets.isUtf16(charset)) return bytes
        var s = bytes.latin1()
        if (cfg.mode == SandboxMode.SAFE) s = META_REFRESH.replace(s) { it.groupValues[1] + "x-refresh-blocked" }
        var inject = bridgeTag(cfg)
        if (cfg.darkSim) {
            if (DARK.containsMatchIn(s)) {
                s = DARK.replace(s, "min-width:0px")
                s = LIGHT.replace(s, "max-width:0px")
                inject += "<style>:root{color-scheme:dark}</style>"
            } else {
                inject += "<style>$INVERT_CSS</style>"
            }
        }
        val at = injectionPoint(s)
        return (s.substring(0, at) + inject + s.substring(at)).latin1Bytes()
    }

    fun injectionPoint(s: String): Int {
        HEAD.find(s)?.let { return it.range.last + 1 }
        HTML.find(s)?.let { return it.range.last + 1 }
        DOCTYPE.find(s)?.let { return it.range.last + 1 }
        return if (s.startsWith("ï»¿")) 3 else 0
    }

    fun darkCss(bytes: ByteArray): ByteArray {
        var s = bytes.latin1()
        s = DARK.replace(s, "min-width:0px")
        s = LIGHT.replace(s, "max-width:0px")
        return s.latin1Bytes()
    }

    fun notFoundPage(path: String): ByteArray =
        ("<!doctype html><meta charset=utf-8><title>Not found</title><body style=\"font:15px system-ui;padding:32px;color:#444\">" +
            "<h2>File not found in this bundle</h2><p><code>${path.replace("<", "&lt;")}</code></p>" +
            "<p>If this page is part of a folder, open the whole folder or ZIP so linked files are available.</p></body>").encodeToByteArray()
}
