package app.htmllens.core.bundle

import app.htmllens.core.util.MimeTypes
import app.htmllens.core.util.Paths
import kotlinx.serialization.Serializable

@Serializable
enum class BundleKind(val label: String) {
    HTML("HTML"),
    PASTE("Pasted HTML"),
    ZIP("ZIP bundle"),
    FOLDER("Folder"),
    MHTML("MHTML archive"),
    WEBARCHIVE("Safari Web Archive"),
    EML("Email"),
    SVG("SVG image"),
    APP("HTML App"),
}

/** A file inside a virtual bundle. [mime] may carry a charset parameter. */
class VFile(val path: String, val bytes: ByteArray, val mime: String) {
    val size: Int get() = bytes.size
    val isHtml: Boolean get() = MimeTypes.isHtml(mime)
}

class Attachment(val name: String, val mime: String, val bytes: ByteArray)

/**
 * A set of files that render together, with one entry document.
 * Paths are normalized, slash-separated and relative (no leading slash).
 */
class Bundle(
    val name: String,
    val kind: BundleKind,
    files: List<VFile>,
    entry: String? = null,
    val meta: Map<String, String> = emptyMap(),
    val attachments: List<Attachment> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val files: Map<String, VFile> = LinkedHashMap<String, VFile>().also { m -> files.forEach { m[it.path] = it } }
    private val lowerIndex: Map<String, VFile> by lazy { this.files.values.associateBy { it.path.lowercase() } }

    val entry: String = entry?.takeIf { it in this.files } ?: EntryDetector.detect(this.files.keys)
        ?: this.files.keys.firstOrNull() ?: "index.html"

    val totalSize: Long get() = files.values.sumOf { it.size.toLong() }
    val htmlFiles: List<VFile> get() = files.values.filter { it.isHtml }
    val isMultiFile: Boolean get() = files.size > 1

    /** Resolves a request path (possibly percent-encoded, with query) to a file. */
    fun resolve(requestPath: String): VFile? {
        val decoded = Paths.normalize(Paths.percentDecode(requestPath.substringBefore('?').substringBefore('#')))
        if (decoded.isEmpty()) return files[entry]
        files[decoded]?.let { return it }
        lowerIndex[decoded.lowercase()]?.let { return it }
        for (index in listOf("index.html", "index.htm")) {
            val p = if (decoded.isEmpty()) index else "$decoded/$index"
            files[p]?.let { return it }
        }
        return null
    }

    fun withFile(file: VFile): Bundle = Bundle(name, kind, files.values.filter { it.path != file.path } + file, entry, meta, attachments, warnings)

    fun copy(name: String = this.name, kind: BundleKind = this.kind, entry: String = this.entry, meta: Map<String, String> = this.meta): Bundle =
        Bundle(name, kind, files.values.toList(), entry, meta, attachments, warnings)

    companion object {
        fun single(name: String, html: String, kind: BundleKind = BundleKind.PASTE): Bundle =
            Bundle(name, kind, listOf(VFile("index.html", html.encodeToByteArray(), "text/html; charset=utf-8")), "index.html")
    }
}

object EntryDetector {
    private val preferred = listOf(
        "index.html", "index.htm", "messages.html", "default.html", "default.htm", "main.html", "home.html",
        "start.html", "archive_browser.html", "start_here.html", "readme.html",
    )

    fun detect(paths: Collection<String>): String? {
        val html = paths.filter { MimeTypes.isHtmlPath(it) && !it.startsWith("__MACOSX/") }
        if (html.isEmpty()) return paths.firstOrNull { Paths.extension(it) == "svg" }
        val levels = html.groupBy { it.count { c -> c == '/' } }.entries.sortedBy { it.key }.map { it.value }
        val shallow = levels.first()
        preferredIn(shallow)?.let { return it }
        if (shallow.size == 1) return shallow.first()
        levels.getOrNull(1)?.let { deeper -> preferredIn(deeper)?.let { return it } }
        return shallow.sorted().first()
    }

    private fun preferredIn(group: List<String>): String? {
        for (name in preferred) group.firstOrNull { Paths.basename(it).lowercase() == name }?.let { return it }
        return null
    }
}
