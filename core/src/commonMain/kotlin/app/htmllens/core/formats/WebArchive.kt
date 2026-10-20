package app.htmllens.core.formats

import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.util.indexOf
import app.htmllens.core.util.latin1

/** Safari .webarchive files (binary or XML property lists). */
object WebArchive {
    private val MARKER = "WebMainResource".encodeToByteArray()

    fun looksLikeWebArchive(bytes: ByteArray): Boolean =
        (Plist.isBinary(bytes) || bytes.latin1(0, minOf(bytes.size, 512)).contains("<plist")) && bytes.indexOf(MARKER) >= 0

    fun parse(name: String, bytes: ByteArray): Bundle {
        val root = Plist.parse(bytes) as? Map<*, *> ?: throw FormatException("Not a Safari web archive")
        val resources = ArrayList<WebResource>()
        collect(root, resources, top = true)
        if (resources.isEmpty()) throw FormatException("Web archive has no main resource")
        val mainUrl = resources.firstOrNull { it.isMain }?.url
        val meta = buildMap { mainUrl?.let { put("Original URL", it) } }
        return ArchiveBuilder.build(name, BundleKind.WEBARCHIVE, resources, meta)
    }

    private fun collect(archive: Map<*, *>, out: MutableList<WebResource>, top: Boolean) {
        (archive["WebMainResource"] as? Map<*, *>)?.let { out += resource(it, top) }
        (archive["WebSubresources"] as? List<*>)?.forEach { r -> (r as? Map<*, *>)?.let { out += resource(it, false) } }
        (archive["WebSubframeArchives"] as? List<*>)?.forEach { a -> (a as? Map<*, *>)?.let { collect(it, out, false) } }
    }

    private fun resource(m: Map<*, *>, main: Boolean) = WebResource(
        url = m["WebResourceURL"] as? String,
        contentId = null,
        mime = (m["WebResourceMIMEType"] as? String)?.lowercase() ?: "application/octet-stream",
        charset = m["WebResourceTextEncodingName"] as? String,
        data = m["WebResourceData"] as? ByteArray ?: ByteArray(0),
        isMain = main,
    )
}
