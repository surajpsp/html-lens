package app.htmllens.core.formats

import app.htmllens.core.bundle.Attachment
import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.bundle.VFile
import app.htmllens.core.util.Charsets
import app.htmllens.core.util.MimeTypes
import app.htmllens.core.util.Paths
import app.htmllens.core.util.Url

/** One resource captured inside a web archive (MHTML, .webarchive, e-mail). */
class WebResource(
    val url: String?,
    val contentId: String?,
    val mime: String,
    val charset: String?,
    val data: ByteArray,
    val isMain: Boolean = false,
)

/**
 * Turns a set of archived resources (addressed by absolute URL or cid:) into a bundle of
 * relative paths, rewriting HTML/CSS references so the page renders fully offline.
 */
object ArchiveBuilder {

    fun build(
        name: String,
        kind: BundleKind,
        resources: List<WebResource>,
        meta: Map<String, String> = emptyMap(),
        attachments: List<Attachment> = emptyList(),
        warnings: List<String> = emptyList(),
    ): Bundle {
        val main = resources.firstOrNull { it.isMain } ?: resources.firstOrNull { MimeTypes.isHtml(it.mime) }
            ?: resources.firstOrNull() ?: throw FormatException("Archive contains no resources")

        val used = HashSet<String>()
        val pathOf = HashMap<WebResource, String>()
        val byUrl = HashMap<String, String>()
        val byCid = HashMap<String, String>()

        pathOf[main] = "index.html".also { used += it }
        for (r in resources) {
            if (r === main) continue
            pathOf[r] = unique(derivePath(r), used)
        }
        for (r in resources) {
            val p = pathOf.getValue(r)
            r.url?.let { u -> urlKeys(u).forEach { k -> if (k !in byUrl) byUrl[k] = p } }
            r.contentId?.let { byCid[it.lowercase()] = p }
        }

        val files = resources.map { r ->
            val path = pathOf.getValue(r)
            val isHtml = MimeTypes.isHtml(r.mime) || (r === main)
            val isCss = MimeTypes.isCss(r.mime)
            if (isHtml || isCss || r.mime == "image/svg+xml") {
                val text = Charsets.decode(r.data, r.charset)
                val base = r.url?.let { Url.parse(it) } ?: main.url?.let { Url.parse(it) }
                val htmlBase = if (isHtml) findBaseHref(text)?.let { Url.resolve(base, it) } ?: base else base
                val mapper: (String) -> String? = { ref -> mapRef(ref, htmlBase, path, byUrl, byCid) }
                var rewritten = if (isCss) UrlRewriter.rewriteCss(text, mapper) else UrlRewriter.rewriteHtml(text, mapper)
                if (isHtml) rewritten = UrlRewriter.forceUtf8Meta(UrlRewriter.disableBaseTags(rewritten))
                val mime = if (isHtml) "text/html; charset=utf-8" else if (isCss) "text/css; charset=utf-8" else r.mime
                VFile(path, rewritten.encodeToByteArray(), mime)
            } else {
                VFile(path, r.data, r.mime)
            }
        }
        return Bundle(name, kind, files, "index.html", meta, attachments, warnings)
    }

    private fun mapRef(ref: String, base: Url?, fromPath: String, byUrl: Map<String, String>, byCid: Map<String, String>): String? {
        val r = ref.trim()
        if (r.isEmpty() || r.startsWith("#") || r.startsWith("data:", true) || r.startsWith("javascript:", true) ||
            r.startsWith("mailto:", true) || r.startsWith("about:", true) || r.startsWith("blob:", true)) return null
        if (r.startsWith("cid:", true)) {
            val id = Paths.percentDecode(r.substring(4)).removePrefix("<").removeSuffix(">").lowercase()
            return byCid[id]?.let { Paths.relative(fromPath, it) }
        }
        val abs = Url.resolve(base, r) ?: return null
        val fragment = abs.fragment
        val target = urlKeys(abs.withoutFragment().toString()).firstNotNullOfOrNull { byUrl[it] } ?: return null
        val rel = if (target == fromPath) "" else Paths.relative(fromPath, target)
        return if (fragment != null) "$rel#$fragment" else rel.ifEmpty { "#" }
    }

    /** Lookup keys for a URL, tolerant of scheme, trailing slash and fragment differences. */
    private fun urlKeys(url: String): List<String> {
        val u = url.substringBefore('#').trim()
        val keys = linkedSetOf(u)
        if (u.endsWith("/")) keys += u.dropLast(1) else keys += "$u/"
        if (u.startsWith("http://")) keys += "https://" + u.removePrefix("http://")
        if (u.startsWith("https://")) keys += "http://" + u.removePrefix("https://")
        keys += Paths.percentDecode(u)
        return keys.toList()
    }

    private val BASE_HREF = Regex("""<base\s[^>]*href\s*=\s*["']?([^"'\s>]+)""", RegexOption.IGNORE_CASE)
    private fun findBaseHref(html: String): String? = BASE_HREF.find(html)?.groupValues?.get(1)

    private fun derivePath(r: WebResource): String {
        val ext = MimeTypes.extensionFor(r.mime)
        val url = r.url?.let { Url.parse(it) }
        val raw = when {
            url != null && url.scheme in setOf("http", "https") -> {
                val segs = url.path.split('/').filter { it.isNotEmpty() }.map { Paths.sanitizeSegment(Paths.percentDecode(it)) }
                val host = Paths.sanitizeSegment(url.host.ifEmpty { "site" })
                var p = (listOf("res", host) + segs).joinToString("/")
                if (segs.isEmpty() || url.path.endsWith("/")) p += "/index"
                p
            }
            r.contentId != null -> "res/cid/" + Paths.sanitizeSegment(r.contentId)
            url != null -> "res/" + Paths.sanitizeSegment(url.scheme) + "/" + Paths.sanitizeSegment(url.path)
            else -> "res/file"
        }
        val hasExt = Paths.extension(raw).isNotEmpty()
        return when {
            ext != null && !hasExt -> "$raw.$ext"
            ext != null && MimeTypes.forPath(raw) != r.mime.substringBefore(';') && (MimeTypes.isHtml(r.mime) || MimeTypes.isCss(r.mime)) -> "$raw.$ext"
            else -> raw
        }
    }

    private fun unique(path: String, used: MutableSet<String>): String {
        if (used.add(path)) return path
        val dir = Paths.dirname(path)
        val base = Paths.stripExtension(Paths.basename(path))
        val ext = Paths.extension(path).let { if (it.isEmpty()) "" else ".$it" }
        var i = 2
        while (true) {
            val candidate = (if (dir.isEmpty()) "" else "$dir/") + "$base-$i$ext"
            if (used.add(candidate)) return candidate
            i++
        }
    }
}
