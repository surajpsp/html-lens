package app.htmllens.core.formats

import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.bundle.VFile
import app.htmllens.core.util.Charsets
import app.htmllens.core.util.MimeTypes
import app.htmllens.core.util.Paths
import app.htmllens.core.util.latin1

/** A file handed to the loader: a name/relative path plus its bytes. */
class InputFile(val path: String, val bytes: ByteArray)

/** Detects the input format and produces a renderable [Bundle]. */
object BundleLoader {
    const val MAX_UNCOMPRESSED = 2_000_000_000L
    private val SKIP = listOf("__MACOSX/", ".DS_Store", "Thumbs.db", "desktop.ini")

    val supportedExtensions = listOf("html", "htm", "xhtml", "shtml", "mht", "mhtml", "webarchive", "eml", "zip", "svg")

    fun load(name: String, inputs: List<InputFile>): Bundle {
        require(inputs.isNotEmpty()) { "Nothing to open" }
        if (inputs.size == 1 && !inputs[0].path.contains('/')) return loadSingle(inputs[0].path, inputs[0].bytes)
        return fromFolder(name, inputs)
    }

    fun loadSingle(fileName: String, bytes: ByteArray): Bundle {
        val name = Paths.basename(fileName)
        val ext = Paths.extension(name)
        val htmlExt = ext in setOf("html", "htm", "xhtml", "shtml")
        return when {
            ZipReader.isZip(bytes) -> fromZip(name, bytes)
            ext == "webarchive" || (Plist.isBinary(bytes) && WebArchive.looksLikeWebArchive(bytes)) -> WebArchive.parse(name, bytes)
            ext == "eml" -> Eml.parse(name, bytes)
            ext == "mht" || ext == "mhtml" -> Mhtml.parse(name, bytes)
            Mhtml.looksLikeMhtml(bytes) && !Eml.looksLikeEml(bytes) -> Mhtml.parse(name, bytes)
            !htmlExt && Eml.looksLikeEml(bytes) -> Eml.parse(name, bytes)
            ext == "svg" || looksLikeSvg(bytes) -> Bundle(name, BundleKind.SVG, listOf(VFile(safeName(name, "svg"), bytes, "image/svg+xml")))
            else -> {
                val path = safeName(name, "html")
                Bundle(name, BundleKind.HTML, listOf(VFile(path, bytes, "text/html; charset=" + Charsets.detectHtml(bytes))), path)
            }
        }
    }

    fun fromZip(name: String, bytes: ByteArray): Bundle {
        val zip = ZipReader(bytes)
        val warnings = ArrayList<String>()
        val total = zip.entries.sumOf { it.size }
        if (total > MAX_UNCOMPRESSED) throw FormatException("ZIP expands to ${total / 1_000_000} MB, which is over the safety limit")
        val files = ArrayList<VFile>()
        for (e in zip.entries) {
            if (e.isDirectory || SKIP.any { e.name.startsWith(it) || e.name.endsWith("/$it") || e.name == it }) continue
            if (e.isEncrypted) { warnings += "Skipped encrypted file ${e.name}"; continue }
            val data = try { zip.read(e) } catch (ex: FormatException) { warnings += ex.message ?: "Could not read ${e.name}"; continue }
            val path = Paths.normalize(e.name)
            if (path.isEmpty()) continue
            files += vfile(path, data)
        }
        if (files.isEmpty()) throw FormatException("The ZIP file is empty")
        // A ZIP that only wraps a single archive (e.g. page.mhtml) opens that archive instead.
        if (files.none { it.isHtml }) {
            files.singleOrNull()?.let { only -> return loadSingle(only.path, only.bytes) }
        }
        return Bundle(name, BundleKind.ZIP, files, null, emptyMap(), emptyList(), warnings)
    }

    fun fromFolder(name: String, inputs: List<InputFile>): Bundle {
        val files = inputs
            .filter { f -> SKIP.none { f.path.startsWith(it) || f.path.endsWith("/$it") || f.path == it } }
            .map { vfile(Paths.normalize(it.path), it.bytes) }
        if (files.isEmpty()) throw FormatException("The folder is empty")
        return Bundle(name, BundleKind.FOLDER, files)
    }

    private fun vfile(path: String, data: ByteArray): VFile {
        val mime = MimeTypes.forPath(path)
        val full = when {
            MimeTypes.isHtml(mime) || MimeTypes.isCss(mime) -> "$mime; charset=" + Charsets.detectHtml(data)
            mime.startsWith("text/") || MimeTypes.isJs(mime) -> "$mime; charset=" + (Charsets.bom(data) ?: if (Charsets.isValidUtf8(data)) "utf-8" else "windows-1252")
            else -> mime
        }
        return VFile(path, data, full)
    }

    private fun safeName(name: String, defaultExt: String): String {
        val n = Paths.sanitizeSegment(name, 120)
        return if (Paths.extension(n).isEmpty()) "$n.$defaultExt" else n
    }

    private fun looksLikeSvg(bytes: ByteArray): Boolean {
        val head = bytes.latin1(0, minOf(bytes.size, 1024)).trimStart().lowercase()
        return head.startsWith("<svg") || (head.startsWith("<?xml") && head.contains("<svg"))
    }
}
