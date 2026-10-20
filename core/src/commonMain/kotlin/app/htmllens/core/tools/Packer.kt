package app.htmllens.core.tools

import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.VFile
import app.htmllens.core.formats.UrlRewriter
import app.htmllens.core.util.B64
import app.htmllens.core.util.Charsets
import app.htmllens.core.util.MimeTypes
import app.htmllens.core.util.Paths
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.DataNode
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

/** Inlines every local stylesheet, script, image, font and frame into one self-contained HTML file. */
object Packer {
    class Result(val html: String, val inlined: Int, val missing: List<String>, val notes: List<String>) {
        val sizeBytes: Int get() = html.encodeToByteArray().size
    }

    private const val MAX_TOTAL = 150 * 1024 * 1024

    fun pack(bundle: Bundle, entry: String = bundle.entry): Result {
        val ctx = Ctx(bundle)
        val html = packHtml(ctx, entry, 0)
        return Result(html, ctx.inlined, ctx.missing.distinct(), ctx.notes.distinct())
    }

    private class Ctx(val bundle: Bundle) {
        var inlined = 0
        var total = 0L
        val missing = ArrayList<String>()
        val notes = ArrayList<String>()
    }

    private fun text(f: VFile): String = Charsets.decode(f.bytes, f.mime.substringAfter("charset=", "").ifEmpty { null })

    private fun isLocal(ref: String): Boolean {
        val r = ref.trim()
        return r.isNotEmpty() && !r.startsWith("#") && !r.startsWith("data:", true) && !r.startsWith("blob:", true) &&
            !r.startsWith("javascript:", true) && !r.startsWith("mailto:", true) && !r.startsWith("tel:", true) &&
            !r.contains("://") && !r.startsWith("//")
    }

    private fun lookup(ctx: Ctx, from: String, ref: String): VFile? {
        if (!isLocal(ref)) return null
        val f = ctx.bundle.resolve(Paths.resolve(from, ref))
        if (f == null) ctx.missing += ref
        return f
    }

    private fun dataUri(ctx: Ctx, f: VFile): String {
        ctx.inlined++
        ctx.total += f.size
        if (ctx.total > MAX_TOTAL) throw IllegalStateException("The packed file would be larger than 150 MB")
        val mime = f.mime.substringBefore(';').let { if (it == "application/octet-stream") MimeTypes.forPath(f.path) else it }
        return "data:$mime;base64," + B64.encode(f.bytes)
    }

    private fun packCss(ctx: Ctx, css: String, cssPath: String, depth: Int): String {
        val importRe = Regex("""@import\s+(?:url\(\s*)?["']?([^"')\s]+)["']?\s*\)?\s*([^;]*);""", RegexOption.IGNORE_CASE)
        val withImports = importRe.replace(css) { m ->
            val f = if (depth < 6) lookup(ctx, cssPath, m.groupValues[1]) else null
            if (f == null) m.value else {
                ctx.inlined++
                val inner = packCss(ctx, text(f), f.path, depth + 1)
                val media = m.groupValues[2].trim()
                if (media.isNotEmpty()) "@media $media {\n$inner\n}" else inner
            }
        }
        return UrlRewriter.rewriteCss(withImports) { ref -> lookup(ctx, cssPath, ref)?.let { dataUri(ctx, it) } }
    }

    private fun packHtml(ctx: Ctx, path: String, depth: Int): String {
        val file = ctx.bundle.files[path] ?: ctx.bundle.resolve(path) ?: return ""
        val doc: Document = Ksoup.parse(text(file))
        doc.outputSettings().prettyPrint(false)

        for (link in doc.select("link[href]")) {
            val rel = link.attr("rel").lowercase()
            val href = link.attr("href")
            when {
                rel.contains("stylesheet") -> {
                    val f = lookup(ctx, path, href) ?: continue
                    val style = doc.createElement("style")
                    link.attr("media").takeIf { it.isNotBlank() }?.let { style.attr("media", it) }
                    style.appendChild(DataNode(packCss(ctx, text(f), f.path, 0)))
                    link.replaceWith(style)
                    ctx.inlined++
                }
                rel.contains("icon") || rel.contains("apple-touch") -> lookup(ctx, path, href)?.let { link.attr("href", dataUri(ctx, it)) }
                rel.contains("preload") || rel.contains("prefetch") || rel == "manifest" -> if (isLocal(href)) link.remove()
            }
        }
        for (script in doc.select("script[src]")) {
            val f = lookup(ctx, path, script.attr("src")) ?: continue
            if (script.attr("type").equals("module", true) && Regex("""(^|\s)import\s|import\(""").containsMatchIn(text(f))) {
                ctx.notes += "ES module imports in ${f.path} were inlined as a data URL; relative imports inside it may not resolve."
                script.attr("src", dataUri(ctx, f))
                continue
            }
            script.removeAttr("src")
            script.empty()
            script.appendChild(DataNode(text(f).replace("</script", "<\\/script", ignoreCase = true)))
            ctx.inlined++
        }
        for (style in doc.select("style")) {
            val css = style.data()
            if (css.contains("url(", true) || css.contains("@import", true)) {
                style.empty(); style.appendChild(DataNode(packCss(ctx, css, path, 0)))
            }
        }
        for (el in doc.select("[style]")) {
            val v = el.attr("style")
            if (v.contains("url(", true)) el.attr("style", UrlRewriter.rewriteCss(v) { ref -> lookup(ctx, path, ref)?.let { dataUri(ctx, it) } })
        }
        val single = listOf(
            "img" to "src", "source" to "src", "video" to "src", "video" to "poster", "audio" to "src", "track" to "src",
            "input" to "src", "embed" to "src", "object" to "data", "image" to "href", "image" to "xlink:href", "use" to "href",
            "td" to "background", "table" to "background", "body" to "background",
        )
        for ((tag, attr) in single) {
            for (el in doc.select("$tag[$attr]")) {
                val v = el.attr(attr)
                if (tag == "use" && v.startsWith("#")) continue
                lookup(ctx, path, v.substringBefore('#'))?.let { f ->
                    val frag = v.substringAfter('#', "")
                    el.attr(attr, dataUri(ctx, f) + if (frag.isNotEmpty() && tag == "use") "#$frag" else "")
                }
            }
        }
        for (el in doc.select("img[srcset], source[srcset]")) {
            val v = el.attr("srcset")
            val rewritten = v.split(',').joinToString(", ") { c ->
                val t = c.trim(); val url = t.substringBefore(' '); val d = t.substring(url.length)
                (lookup(ctx, path, url)?.let { dataUri(ctx, it) } ?: url) + d
            }
            el.attr("srcset", rewritten)
        }
        if (depth < 3) {
            for (frame in doc.select("iframe[src], frame[src]")) {
                val f = lookup(ctx, path, frame.attr("src")) ?: continue
                if (!f.isHtml) { frame.attr("src", dataUri(ctx, f)); continue }
                frame.removeAttr("src")
                frame.attr("srcdoc", packHtml(ctx, f.path, depth + 1))
                ctx.inlined++
            }
        }
        val localPages = doc.select("a[href]").count { a ->
            val h = a.attr("href")
            isLocal(h) && MimeTypes.isHtmlPath(h.substringBefore('#').substringBefore('?')) && ctx.bundle.resolve(Paths.resolve(path, h)) != null
        }
        if (localPages > 0 && depth == 0) ctx.notes += "$localPages link(s) to other pages in the bundle were kept as links."

        val head: Element? = doc.head()
        if (head != null && doc.selectFirst("meta[charset]") == null) head.prependElement("meta").attr("charset", "utf-8")
        doc.select("meta[charset]").forEach { it.attr("charset", "utf-8") }
        return doc.outerHtml()
    }
}
