package app.htmllens.core.formats

/**
 * Rewrites URL references inside HTML and CSS text without re-serializing the document,
 * so the original markup is preserved byte-for-byte apart from the rewritten values.
 */
object UrlRewriter {

    private val ATTR = Regex(
        """(\s)(src|href|poster|data|background|action|longdesc|lowsrc|data-src|data-original|xlink:href|imagesrcset|srcset)(\s*=\s*)("([^"]*)"|'([^']*)'|([^\s"'>]+))""",
        RegexOption.IGNORE_CASE,
    )
    private val CSS_URL = Regex("""url\(\s*("([^"]*)"|'([^']*)'|([^)\s]*))\s*\)""", RegexOption.IGNORE_CASE)
    private val CSS_IMPORT = Regex("""@import\s+("([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
    private val BASE_TAG = Regex("""<base(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val META_CHARSET = Regex("""(<meta[^>]+charset\s*=\s*["']?)([A-Za-z0-9_:.\-]+)""", RegexOption.IGNORE_CASE)

    /** [map] receives a decoded reference and returns the replacement, or null to keep it. */
    fun rewriteHtml(html: String, map: (String) -> String?): String {
        var out = ATTR.replace(html) { m ->
            val attr = m.groupValues[2].lowercase()
            val raw = m.groupValues[5].ifEmpty { m.groupValues[6] }.ifEmpty { m.groupValues[7] }
            val quote = when {
                m.groupValues[4].startsWith("\"") -> "\""
                m.groupValues[4].startsWith("'") -> "'"
                else -> "\""
            }
            val replaced = if (attr.endsWith("srcset")) rewriteSrcset(raw, map) else map(decodeEntities(raw))?.let { escapeAttr(it) }
            if (replaced == null) m.value else m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + quote + replaced + quote
        }
        out = rewriteCss(out, map)
        return out
    }

    fun rewriteCss(css: String, map: (String) -> String?): String {
        var out = CSS_URL.replace(css) { m ->
            val raw = m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { m.groupValues[4] }
            val replaced = map(decodeEntities(raw).trim())
            if (replaced == null) m.value else "url(\"${replaced.replace("\"", "%22")}\")"
        }
        out = CSS_IMPORT.replace(out) { m ->
            val raw = m.groupValues[2].ifEmpty { m.groupValues[3] }
            val replaced = map(raw.trim())
            if (replaced == null) m.value else "@import \"$replaced\""
        }
        return out
    }

    private fun rewriteSrcset(value: String, map: (String) -> String?): String? {
        var changed = false
        val parts = value.split(',').map { candidate ->
            val trimmed = candidate.trim()
            if (trimmed.isEmpty()) return@map candidate
            val url = trimmed.substringBefore(' ')
            val descriptor = trimmed.substring(url.length)
            val r = map(decodeEntities(url))
            if (r != null) { changed = true; escapeAttr(r) + descriptor } else trimmed
        }
        return if (changed) parts.joinToString(", ") else null
    }

    /** Neutralizes <base> tags so rewritten relative paths resolve against the virtual origin. */
    fun disableBaseTags(html: String): String = BASE_TAG.replace(html) { "<!-- base removed by HTML Lens -->" }

    fun forceUtf8Meta(html: String): String = META_CHARSET.replace(html) { it.groupValues[1] + "utf-8" }

    fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&#38;", "&")
    }

    private fun escapeAttr(s: String): String = s.replace("&", "&amp;").replace("\"", "&quot;")
}
