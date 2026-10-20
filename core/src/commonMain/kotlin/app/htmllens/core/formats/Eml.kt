package app.htmllens.core.formats

import app.htmllens.core.bundle.Attachment
import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.util.latin1

/** E-mail messages (.eml, RFC 5322) rendered with inline images and attachment list. */
object Eml {

    private val HEADER_HINTS = listOf("from:", "to:", "subject:", "date:", "message-id:", "received:", "mime-version:", "return-path:")

    fun looksLikeEml(bytes: ByteArray): Boolean {
        val head = bytes.latin1(0, minOf(bytes.size, 4096)).lowercase()
        val lines = head.lineSequence().take(60).toList()
        val hits = HEADER_HINTS.count { h -> lines.any { it.startsWith(h) } }
        return hits >= 2 && !head.contains("snapshot-content-location") && !head.contains("<saved by")
    }

    const val META_FROM = "From"
    const val META_TO = "To"
    const val META_CC = "Cc"
    const val META_SUBJECT = "Subject"
    const val META_DATE = "Date"

    fun parse(name: String, bytes: ByteArray): Bundle {
        var root = MimeParser.parse(bytes)
        if (root.contentType == "message/rfc822" && root.children.isNotEmpty()) root = root.children.first()

        val meta = linkedMapOf<String, String>()
        for (h in listOf(META_SUBJECT, META_FROM, META_TO, META_CC, META_DATE, "Reply-To")) {
            root.header(h)?.let { meta[h] = MimeParser.decodeWords(it) }
        }

        val body = chooseBody(root)
        val htmlPart = body.html
        val html = htmlPart?.text() ?: textToHtml(body.text?.text() ?: "", meta[META_SUBJECT])
        val bodyParts = setOfNotNull(htmlPart, body.text)

        val leaves = root.walk().filter { !it.isMultipart && it.contentType != "message/rfc822" }.toList()
        val inline = leaves.filter { it !in bodyParts && (it.contentId != null || (it.contentLocation != null && it in body.related)) }
        val referenced = inline.filter { p -> p.contentId?.let { html.contains(it, ignoreCase = true) } ?: true }.toSet()
        val attachments = leaves.filter { p ->
            p !in bodyParts && p !in referenced &&
                (p.disposition == "attachment" || p.filename != null || !p.contentType.startsWith("text/"))
        }.mapIndexed { i, p -> Attachment(p.filename ?: "attachment-${i + 1}", p.contentType, p.body) }

        val resources = buildList {
            add(WebResource(null, null, "text/html", "utf-8", html.encodeToByteArray(), isMain = true))
            inline.forEach { p -> add(WebResource(p.contentLocation, p.contentId, p.contentType, p.charset, p.body)) }
        }
        return ArchiveBuilder.build(meta[META_SUBJECT]?.takeIf { it.isNotBlank() } ?: name, BundleKind.EML, resources, meta, attachments)
    }

    private class Body(val html: MimePart?, val text: MimePart?, val related: List<MimePart>)

    private fun chooseBody(part: MimePart): Body {
        if (!part.isMultipart) {
            return when {
                part.contentType == "text/html" && part.disposition != "attachment" -> Body(part, null, emptyList())
                part.contentType.startsWith("text/") && part.disposition != "attachment" -> Body(null, part, emptyList())
                else -> Body(null, null, emptyList())
            }
        }
        return when (part.contentType) {
            "multipart/alternative" -> {
                val options = part.children.map { chooseBody(it) }
                val html = options.lastOrNull { it.html != null }
                val text = options.firstOrNull { it.text != null }?.text
                Body(html?.html, text, html?.related ?: emptyList())
            }
            "multipart/related" -> {
                val startCid = part.param("Content-Type", "start")?.removePrefix("<")?.removeSuffix(">")
                val rootPart = part.children.firstOrNull { startCid != null && it.contentId == startCid } ?: part.children.firstOrNull()
                val inner = rootPart?.let { chooseBody(it) } ?: Body(null, null, emptyList())
                Body(inner.html, inner.text, part.children.filter { it !== rootPart } + inner.related)
            }
            else -> {
                for (c in part.children) {
                    if (c.disposition == "attachment") continue
                    val b = chooseBody(c)
                    if (b.html != null || b.text != null) return b
                }
                Body(null, null, emptyList())
            }
        }
    }

    private val URL_RE = Regex("""\bhttps?://[^\s<>"')]+""")

    fun textToHtml(text: String, title: String?): String {
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val linked = URL_RE.replace(escaped) { "<a href=\"${it.value}\">${it.value}</a>" }
        val t = (title ?: "Message").replace("<", "&lt;")
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>$t</title>
<style>body{margin:24px;font:15px/1.6 -apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#1f2328}pre{white-space:pre-wrap;word-wrap:break-word;font:inherit}a{color:#4f46e5}</style>
</head><body><pre>$linked</pre></body></html>"""
    }
}
