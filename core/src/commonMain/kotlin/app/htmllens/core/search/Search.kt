package app.htmllens.core.search

import app.htmllens.core.bundle.Bundle
import app.htmllens.core.util.Charsets
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import kotlinx.coroutines.yield

object TextExtractor {
    /** Visible text of an HTML document, without scripts/styles. */
    fun extract(html: String, maxChars: Int = 300_000): String {
        val doc = Ksoup.parse(html)
        doc.select("script, style, noscript, template, svg").remove()
        val title = doc.title()
        val body = doc.body().text()
        return (title + "\n" + body).take(maxChars)
    }

    fun extractBundle(bundle: Bundle, maxChars: Int = 300_000, maxFiles: Int = 40): String {
        val sb = StringBuilder()
        for (f in bundle.htmlFiles.sortedByDescending { it.path == bundle.entry }.take(maxFiles)) {
            if (sb.length >= maxChars || f.size > 10 * 1024 * 1024) break
            sb.append(extract(Charsets.decode(f.bytes, f.mime.substringAfter("charset=", "").ifEmpty { null }), maxChars - sb.length)).append('\n')
        }
        return sb.toString()
    }
}

/** Ranks documents for a query; used for the library's full-text search. */
object LibrarySearch {
    data class Doc(val id: String, val title: String, val text: String)
    data class Hit(val id: String, val score: Double, val snippet: String)

    fun search(query: String, docs: List<Doc>, limit: Int = 50): List<Hit> {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        if (terms.isEmpty()) return emptyList()
        val hits = ArrayList<Hit>()
        for (d in docs) {
            val title = d.title.lowercase()
            val text = d.text.lowercase()
            var score = 0.0
            var firstPos = -1
            var allFound = true
            for (t in terms) {
                var count = 0
                var idx = text.indexOf(t)
                if (idx >= 0 && (firstPos < 0 || idx < firstPos)) firstPos = idx
                while (idx >= 0 && count < 50) { count++; idx = text.indexOf(t, idx + t.length) }
                val inTitle = title.contains(t)
                if (count == 0 && !inTitle) allFound = false
                score += kotlin.math.ln(1.0 + count) + if (inTitle) 3.0 else 0.0
            }
            if (!allFound || score <= 0) continue
            hits += Hit(d.id, score, snippet(d.text, firstPos))
        }
        return hits.sortedByDescending { it.score }.take(limit)
    }

    fun snippet(text: String, pos: Int, radius: Int = 80): String {
        if (pos < 0) return text.take(radius * 2).replace(Regex("\\s+"), " ").trim()
        val s = (pos - radius).coerceAtLeast(0)
        val e = (pos + radius).coerceAtMost(text.length)
        return (if (s > 0) "…" else "") + text.substring(s, e).replace(Regex("\\s+"), " ").trim() + if (e < text.length) "…" else ""
    }
}

/** Searches the text of every HTML page inside a bundle, returning anchors to jump to. */
object BundleSearch {
    data class Hit(val file: String, val anchor: String?, val snippet: String, val ordinal: Int)

    suspend fun search(bundle: Bundle, query: String, limit: Int = 300, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Hit> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val hits = ArrayList<Hit>()
        val pages = bundle.htmlFiles.sortedWith(compareBy({ naturalKey(it.path).first }, { naturalKey(it.path).second }))
        for ((i, f) in pages.withIndex()) {
            onProgress(i, pages.size)
            yield()
            val text = Charsets.decode(f.bytes, f.mime.substringAfter("charset=", "").ifEmpty { null })
            if (!text.lowercase().contains(q)) continue
            val doc = Ksoup.parse(text)
            var ordinal = 0
            for (el in doc.select("body *")) {
                val own = el.ownText()
                val idx = own.lowercase().indexOf(q)
                if (idx < 0) continue
                hits += Hit(f.path, anchorFor(el), LibrarySearch.snippet(own, idx, 70), ordinal++)
                if (hits.size >= limit) return hits
            }
        }
        onProgress(pages.size, pages.size)
        return hits
    }

    private fun anchorFor(el: Element): String? {
        var cur: Element? = el
        var depth = 0
        while (cur != null && depth < 12) {
            val id = cur.id()
            if (id.isNotBlank()) return id
            cur = cur.parent()
            depth++
        }
        return null
    }

    /** Sorts messages.html, messages2.html, messages10.html naturally. */
    private fun naturalKey(path: String): Pair<String, Int> {
        val m = Regex("^(.*?)(\\d+)?\\.html?$").find(path.lowercase())
        return (m?.groupValues?.get(1) ?: path) to (m?.groupValues?.get(2)?.toIntOrNull() ?: 0)
    }
}
