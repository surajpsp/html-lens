package app.htmllens.core.util

/** Charset detection and decoding that works the same on every platform. */
object Charsets {

    private val META_CHARSET = Regex("""<meta[^>]+charset\s*=\s*["']?\s*([A-Za-z0-9_:.\-]+)""", RegexOption.IGNORE_CASE)

    /** Returns a charset label for an HTML (or CSS) payload: BOM, then meta/@charset, then UTF-8 validity. */
    fun detectHtml(bytes: ByteArray, declared: String? = null): String {
        bom(bytes)?.let { return it }
        declared?.takeIf { it.isNotBlank() }?.let { return normalize(it) }
        val head = bytes.latin1(0, minOf(bytes.size, 4096))
        META_CHARSET.find(head)?.groupValues?.get(1)?.let { return normalize(it) }
        Regex("""^@charset\s+["']([^"']+)["']""").find(head)?.groupValues?.get(1)?.let { return normalize(it) }
        return if (isValidUtf8(bytes, minOf(bytes.size, 1 shl 20))) "utf-8" else "windows-1252"
    }

    fun bom(bytes: ByteArray): String? = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> "utf-8"
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> "utf-16be"
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> "utf-16le"
        else -> null
    }

    fun isUtf16(charset: String) = charset.lowercase().startsWith("utf-16")

    fun normalize(label: String): String {
        val l = label.trim().lowercase()
        return when (l) {
            "utf8", "unicode-1-1-utf-8" -> "utf-8"
            "latin1", "latin-1", "iso8859-1", "iso_8859-1", "us-ascii", "ascii", "iso-8859-1" -> "windows-1252"
            else -> l
        }
    }

    fun decode(bytes: ByteArray, charset: String? = null): String {
        val cs = normalize(charset ?: detectHtml(bytes))
        val start = when (bom(bytes)) {
            "utf-8" -> 3
            "utf-16be", "utf-16le" -> 2
            else -> 0
        }
        val body = if (start == 0) bytes else bytes.copyOfRange(start, bytes.size)
        return when (cs) {
            "utf-8" -> body.decodeToString()
            "windows-1252" -> platformDecode(body, cs) ?: decodeCp1252(body)
            else -> platformDecode(body, cs) ?: body.decodeToString()
        }
    }

    fun isValidUtf8(bytes: ByteArray, limit: Int = bytes.size): Boolean {
        var i = 0
        val end = minOf(limit, bytes.size)
        while (i < end) {
            val b = bytes[i].toInt() and 0xFF
            val n = when {
                b < 0x80 -> 0
                b in 0xC2..0xDF -> 1
                b in 0xE0..0xEF -> 2
                b in 0xF0..0xF4 -> 3
                else -> return false
            }
            if (i + n >= end && n > 0) return true // truncated at the sample boundary
            for (k in 1..n) if ((bytes[i + k].toInt() and 0xC0) != 0x80) return false
            i += n + 1
        }
        return true
    }

    private val CP1252_HIGH = intArrayOf(
        0x20AC, 0xFFFD, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021, 0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0xFFFD, 0x017D, 0xFFFD,
        0xFFFD, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014, 0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0xFFFD, 0x017E, 0x0178,
    )

    fun decodeCp1252(bytes: ByteArray): String {
        val chars = CharArray(bytes.size)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            chars[i] = if (b in 0x80..0x9F) CP1252_HIGH[b - 0x80].toChar() else b.toChar()
        }
        return chars.concatToString()
    }
}
