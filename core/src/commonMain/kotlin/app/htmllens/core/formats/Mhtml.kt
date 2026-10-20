package app.htmllens.core.formats

import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.util.latin1

/** MHTML / MHT archives as produced by Chrome, Edge, Opera and Internet Explorer. */
object Mhtml {

    fun looksLikeMhtml(bytes: ByteArray): Boolean {
        val head = bytes.latin1(0, minOf(bytes.size, 8192)).lowercase()
        return head.contains("multipart/related") && (head.contains("mime-version") || head.contains("snapshot-content-location"))
    }

    fun parse(name: String, bytes: ByteArray): Bundle {
        val root = MimeParser.parse(bytes)
        val leaves = root.walk().filter { !it.isMultipart }.toList()
        if (leaves.isEmpty()) throw FormatException("MHTML file has no parts")
        val startCid = root.param("Content-Type", "start")?.removePrefix("<")?.removeSuffix(">")
        val main = leaves.firstOrNull { startCid != null && it.contentId == startCid }
            ?: leaves.firstOrNull { it.contentType == "text/html" }
            ?: leaves.first()
        val resources = leaves.map { p ->
            WebResource(
                url = p.contentLocation,
                contentId = p.contentId,
                mime = p.contentType,
                charset = p.charset,
                data = p.body,
                isMain = p === main,
            )
        }
        val meta = buildMap {
            (root.header("Snapshot-Content-Location") ?: main.contentLocation)?.let { put("Original URL", it) }
            root.header("Subject")?.let { put("Title", MimeParser.decodeWords(it)) }
            root.header("Date")?.let { put("Saved", it) }
        }
        return ArchiveBuilder.build(name, BundleKind.MHTML, resources, meta)
    }
}
