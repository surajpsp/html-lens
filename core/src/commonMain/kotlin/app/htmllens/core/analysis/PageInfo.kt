package app.htmllens.core.analysis

import app.htmllens.core.bundle.Bundle
import app.htmllens.core.util.Charsets
import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.Serializable

@Serializable
data class Heading(val level: Int, val text: String, val index: Int)

@Serializable
data class PageInfo(
    val title: String,
    val description: String?,
    val lang: String?,
    val charset: String,
    val headings: List<Heading>,
    val internalLinks: Int,
    val externalLinks: Int,
    val images: Int,
    val imagesWithoutAlt: Int,
    val scripts: Int,
    val stylesheets: Int,
    val words: Int,
    val readingMinutes: Int,
    val hasViewport: Boolean,
    val generator: String?,
    val canonical: String?,
    val sizeBytes: Int,
)

object PageAnalyzer {
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun analyze(bundle: Bundle, path: String = bundle.entry): PageInfo? {
        val file = bundle.files[path] ?: return null
        if (!file.isHtml || file.size > MAX_BYTES) return null
        val charset = file.mime.substringAfter("charset=", "").ifEmpty { Charsets.detectHtml(file.bytes) }
        val doc = Ksoup.parse(Charsets.decode(file.bytes, charset))
        val headings = doc.select("h1, h2, h3, h4, h5, h6").mapIndexed { i, h ->
            Heading(h.tagName().substring(1).toInt(), h.text().take(140), i)
        }.filter { it.text.isNotBlank() }
        val links = doc.select("a[href]").map { it.attr("href") }
        val external = links.count { it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("//") }
        val text = doc.body().text()
        val words = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
        val images = doc.select("img")
        return PageInfo(
            title = doc.title().ifBlank { bundle.name },
            description = doc.selectFirst("meta[name=description]")?.attr("content")?.takeIf { it.isNotBlank() },
            lang = doc.selectFirst("html")?.attr("lang")?.takeIf { it.isNotBlank() },
            charset = charset,
            headings = headings,
            internalLinks = links.size - external,
            externalLinks = external,
            images = images.size,
            imagesWithoutAlt = images.count { !it.hasAttr("alt") },
            scripts = doc.select("script").size,
            stylesheets = doc.select("link[rel~=(?i)stylesheet], style").size,
            words = words,
            readingMinutes = maxOf(1, words / 220),
            hasViewport = doc.selectFirst("meta[name=viewport]") != null,
            generator = doc.selectFirst("meta[name=generator]")?.attr("content")?.takeIf { it.isNotBlank() },
            canonical = doc.selectFirst("link[rel=canonical]")?.attr("href")?.takeIf { it.isNotBlank() },
            sizeBytes = file.size,
        )
    }

    fun title(html: String): String? = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.get(1)?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }?.take(120)
}
