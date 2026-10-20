package app.htmllens.core.formats

import app.htmllens.core.util.Charsets
import app.htmllens.core.util.u16
import app.htmllens.core.util.u32
import app.htmllens.core.util.u64

class ZipEntry(val name: String, val method: Int, val flags: Int, val compressedSize: Long, val size: Long, val localOffset: Long) {
    val isDirectory get() = name.endsWith("/")
    val isEncrypted get() = flags and 1 != 0
}

/** Reads ZIP archives (stored + deflate, ZIP64 aware). */
class ZipReader(private val data: ByteArray) {

    val entries: List<ZipEntry> = readCentralDirectory()

    private fun readCentralDirectory(): List<ZipEntry> {
        val eocd = findEocd() ?: throw FormatException("Not a ZIP file (end of central directory not found)")
        var count = data.u16(eocd + 10).toLong()
        var cdOffset = data.u32(eocd + 16)
        if (count == 0xFFFFL || cdOffset == 0xFFFFFFFFL) {
            val loc = eocd - 20
            if (loc >= 0 && data.u32(loc) == 0x07064b50L) {
                val z64 = data.u64(loc + 8).toInt()
                if (z64 >= 0 && z64 + 56 <= data.size && data.u32(z64) == 0x06064b50L) {
                    count = data.u64(z64 + 32)
                    cdOffset = data.u64(z64 + 48)
                }
            }
        }
        val list = ArrayList<ZipEntry>(count.toInt().coerceAtMost(100_000))
        var p = cdOffset.toInt()
        for (i in 0 until count) {
            if (p + 46 > data.size || data.u32(p) != 0x02014b50L) break
            val flags = data.u16(p + 8)
            val method = data.u16(p + 10)
            var csize = data.u32(p + 20)
            var usize = data.u32(p + 24)
            val nameLen = data.u16(p + 28)
            val extraLen = data.u16(p + 30)
            val commentLen = data.u16(p + 32)
            var offset = data.u32(p + 42)
            val nameBytes = data.copyOfRange(p + 46, p + 46 + nameLen)
            val name = if (flags and 0x800 != 0 || Charsets.isValidUtf8(nameBytes)) nameBytes.decodeToString() else cp437(nameBytes)
            // ZIP64 extended information extra field
            var e = p + 46 + nameLen
            val extraEnd = e + extraLen
            while (e + 4 <= extraEnd) {
                val id = data.u16(e)
                val sz = data.u16(e + 2)
                if (id == 0x0001) {
                    var q = e + 4
                    if (usize == 0xFFFFFFFFL) { usize = data.u64(q); q += 8 }
                    if (csize == 0xFFFFFFFFL) { csize = data.u64(q); q += 8 }
                    if (offset == 0xFFFFFFFFL) { offset = data.u64(q) }
                }
                e += 4 + sz
            }
            list += ZipEntry(name.replace('\\', '/'), method, flags, csize, usize, offset)
            p += 46 + nameLen + extraLen + commentLen
        }
        return list
    }

    private fun findEocd(): Int? {
        val min = maxOf(0, data.size - 22 - 65535)
        var i = data.size - 22
        while (i >= min) {
            if (data[i] == 0x50.toByte() && data[i + 1] == 0x4b.toByte() && data[i + 2] == 0x05.toByte() && data[i + 3] == 0x06.toByte()) return i
            i--
        }
        return null
    }

    fun read(entry: ZipEntry): ByteArray {
        if (entry.isEncrypted) throw FormatException("Encrypted entry: ${entry.name}")
        val lo = entry.localOffset.toInt()
        if (lo < 0 || lo + 30 > data.size || data.u32(lo) != 0x04034b50L) throw FormatException("Corrupt local header: ${entry.name}")
        val start = lo + 30 + data.u16(lo + 26) + data.u16(lo + 28)
        val end = (start + entry.compressedSize).toInt()
        if (end > data.size || end < start) throw FormatException("Truncated entry: ${entry.name}")
        return when (entry.method) {
            0 -> data.copyOfRange(start, end)
            8 -> Inflater.inflate(data, start, end, entry.size.toInt().coerceIn(0, 512 * 1024 * 1024))
            else -> throw FormatException("Unsupported compression method ${entry.method}: ${entry.name}")
        }
    }

    companion object {
        fun isZip(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte()) && (bytes[3] == 0x04.toByte() || bytes[3] == 0x06.toByte())

        private const val CP437_HIGH = "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ "

        fun cp437(bytes: ByteArray): String = buildString {
            for (b in bytes) {
                val c = b.toInt() and 0xFF
                append(if (c < 128) c.toChar() else CP437_HIGH[c - 128])
            }
        }
    }
}

/** Minimal ZIP writer (stored entries) used for exporting bundles. */
object ZipWriter {
    fun write(files: List<Pair<String, ByteArray>>): ByteArray {
        val sink = app.htmllens.core.util.ByteSink(files.sumOf { it.second.size } + files.size * 100 + 64)
        val central = app.htmllens.core.util.ByteSink(files.size * 80 + 64)
        for ((name, bytes) in files) {
            val nameBytes = name.encodeToByteArray()
            val crc = Crc32.compute(bytes)
            val offset = sink.size
            le32(sink, 0x04034b50); le16(sink, 20); le16(sink, 0x800); le16(sink, 0); le16(sink, 0); le16(sink, 0x21)
            le32(sink, crc); le32(sink, bytes.size); le32(sink, bytes.size); le16(sink, nameBytes.size); le16(sink, 0)
            sink.write(nameBytes); sink.write(bytes)
            le32(central, 0x02014b50); le16(central, 20); le16(central, 20); le16(central, 0x800); le16(central, 0)
            le16(central, 0); le16(central, 0x21); le32(central, crc); le32(central, bytes.size); le32(central, bytes.size)
            le16(central, nameBytes.size); le16(central, 0); le16(central, 0); le16(central, 0); le16(central, 0)
            le32(central, 0); le32(central, offset); central.write(nameBytes)
        }
        val cdOffset = sink.size
        val cd = central.toByteArray()
        sink.write(cd)
        le32(sink, 0x06054b50); le16(sink, 0); le16(sink, 0); le16(sink, files.size); le16(sink, files.size)
        le32(sink, cd.size); le32(sink, cdOffset); le16(sink, 0)
        return sink.toByteArray()
    }

    private fun le16(s: app.htmllens.core.util.ByteSink, v: Int) { s.write(v and 0xFF); s.write((v shr 8) and 0xFF) }
    private fun le32(s: app.htmllens.core.util.ByteSink, v: Int) { le16(s, v and 0xFFFF); le16(s, (v ushr 16) and 0xFFFF) }
}

object Crc32 {
    private val TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun compute(bytes: ByteArray): Int {
        var c = -1
        for (b in bytes) c = TABLE[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }
}
