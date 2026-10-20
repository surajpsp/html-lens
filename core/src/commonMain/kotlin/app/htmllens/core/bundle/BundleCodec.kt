package app.htmllens.core.bundle

import app.htmllens.core.formats.FormatException
import app.htmllens.core.util.ByteSink
import app.htmllens.core.util.beUInt
import app.htmllens.core.util.startsWith
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Compact single-blob storage format for bundles: "HLB1" + header length + JSON header + payload. */
object BundleCodec {
    private val MAGIC = "HLB1".encodeToByteArray()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Entry(val p: String, val m: String, val o: Long, val l: Int)

    @Serializable
    private data class Header(
        val name: String,
        val kind: BundleKind,
        val entry: String,
        val meta: Map<String, String> = emptyMap(),
        val warnings: List<String> = emptyList(),
        val files: List<Entry>,
        val attachments: List<Entry> = emptyList(),
    )

    fun encode(bundle: Bundle): ByteArray {
        var offset = 0L
        val files = bundle.files.values.map { f -> Entry(f.path, f.mime, offset, f.size).also { offset += f.size } }
        val atts = bundle.attachments.map { a -> Entry(a.name, a.mime, offset, a.bytes.size).also { offset += a.bytes.size } }
        val header = json.encodeToString(Header.serializer(), Header(bundle.name, bundle.kind, bundle.entry, bundle.meta, bundle.warnings, files, atts)).encodeToByteArray()
        val sink = ByteSink(8 + header.size + offset.toInt())
        sink.write(MAGIC)
        sink.write((header.size ushr 24) and 0xFF); sink.write((header.size ushr 16) and 0xFF)
        sink.write((header.size ushr 8) and 0xFF); sink.write(header.size and 0xFF)
        sink.write(header)
        bundle.files.values.forEach { sink.write(it.bytes) }
        bundle.attachments.forEach { sink.write(it.bytes) }
        return sink.toByteArray()
    }

    fun decode(bytes: ByteArray): Bundle {
        if (!bytes.startsWith(MAGIC)) throw FormatException("Not a stored bundle")
        val len = bytes.beUInt(4, 4).toInt()
        val header = json.decodeFromString(Header.serializer(), bytes.decodeToString(8, 8 + len))
        val base = 8 + len
        fun slice(e: Entry) = bytes.copyOfRange(base + e.o.toInt(), base + e.o.toInt() + e.l)
        val files = header.files.map { VFile(it.p, slice(it), it.m) }
        val atts = header.attachments.map { Attachment(it.p, it.m, slice(it)) }
        return Bundle(header.name, header.kind, files, header.entry, header.meta, atts, header.warnings)
    }
}
