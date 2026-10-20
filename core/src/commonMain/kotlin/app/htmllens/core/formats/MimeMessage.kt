package app.htmllens.core.formats

import app.htmllens.core.util.B64
import app.htmllens.core.util.ByteSink
import app.htmllens.core.util.Charsets
import app.htmllens.core.util.indexOf
import app.htmllens.core.util.latin1

/** A parsed MIME entity (RFC 2045/2046) with its transfer-decoded body. */
class MimePart(
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
    val children: List<MimePart>,
) {
    fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

    val contentType: String get() = header("Content-Type")?.substringBefore(';')?.trim()?.lowercase() ?: "text/plain"
    val isMultipart: Boolean get() = contentType.startsWith("multipart/")
    val charset: String? get() = param("Content-Type", "charset")
    val contentId: String? get() = header("Content-ID")?.trim()?.removePrefix("<")?.removeSuffix(">")?.trim()
    val contentLocation: String? get() = header("Content-Location")?.trim()?.replace(Regex("\\s+"), "")
    val disposition: String get() = header("Content-Disposition")?.substringBefore(';')?.trim()?.lowercase() ?: ""
    val filename: String? get() = param("Content-Disposition", "filename") ?: param("Content-Type", "name")

    fun param(headerName: String, paramName: String): String? {
        val h = header(headerName) ?: return null
        return MimeParser.parseParams(h)[paramName.lowercase()]
    }

    fun text(): String = Charsets.decode(body, charset ?: if (contentType == "text/html") null else "utf-8")

    fun walk(): Sequence<MimePart> = sequence {
        yield(this@MimePart)
        for (c in children) yieldAll(c.walk())
    }
}

object MimeParser {

    fun parse(bytes: ByteArray, start: Int = 0, end: Int = bytes.size, depth: Int = 0): MimePart {
        val sep = findHeaderEnd(bytes, start, end)
        val headerEnd = sep?.first ?: end
        val bodyStart = sep?.second ?: end
        val headers = parseHeaders(bytes.latin1(start, headerEnd))
        val ct = headers.firstOrNull { it.first.equals("Content-Type", true) }?.second ?: ""
        val type = ct.substringBefore(';').trim().lowercase()
        if (type.startsWith("multipart/") && depth < 20) {
            val boundary = parseParams(ct)["boundary"]
            if (!boundary.isNullOrEmpty()) {
                val children = splitMultipart(bytes, bodyStart, end, boundary).map { (s, e) -> parse(bytes, s, e, depth + 1) }
                return MimePart(headers, ByteArray(0), children)
            }
        }
        if (type == "message/rfc822" && depth < 20) {
            val inner = parse(bytes, bodyStart, end, depth + 1)
            return MimePart(headers, bytes.copyOfRange(bodyStart, end), listOf(inner))
        }
        val encoding = headers.firstOrNull { it.first.equals("Content-Transfer-Encoding", true) }?.second?.trim()?.lowercase()
        val body = when (encoding) {
            "base64" -> B64.decodeBytes(bytes, bodyStart, end)
            "quoted-printable" -> decodeQuotedPrintable(bytes, bodyStart, end)
            else -> bytes.copyOfRange(bodyStart, end)
        }
        return MimePart(headers, body, emptyList())
    }

    /** Returns (end of headers, start of body). */
    private fun findHeaderEnd(bytes: ByteArray, start: Int, end: Int): Pair<Int, Int>? {
        var i = start
        // An entity that starts with a blank line has no headers.
        if (i < end && (bytes[i] == '\n'.code.toByte())) return i to i + 1
        if (i + 1 < end && bytes[i] == '\r'.code.toByte() && bytes[i + 1] == '\n'.code.toByte()) return i to i + 2
        while (i < end) {
            if (bytes[i] == '\n'.code.toByte()) {
                if (i + 1 < end && bytes[i + 1] == '\n'.code.toByte()) return i to i + 2
                if (i + 2 < end && bytes[i + 1] == '\r'.code.toByte() && bytes[i + 2] == '\n'.code.toByte()) return i to i + 3
            }
            i++
        }
        return null
    }

    fun parseHeaders(raw: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var name: String? = null
        val value = StringBuilder()
        for (line in raw.split('\n')) {
            val l = line.trimEnd('\r')
            if (l.isEmpty()) continue
            if ((l[0] == ' ' || l[0] == '\t') && name != null) {
                value.append(' ').append(l.trim())
            } else {
                if (name != null) out += name to value.toString().trim()
                val colon = l.indexOf(':')
                if (colon <= 0) { name = null; value.clear(); continue }
                name = l.substring(0, colon).trim()
                value.clear().append(l.substring(colon + 1))
            }
        }
        if (name != null) out += name to value.toString().trim()
        return out
    }

    fun parseParams(header: String): Map<String, String> {
        val out = HashMap<String, String>()
        val ext = HashMap<String, String>()
        var i = header.indexOf(';')
        if (i < 0) return out
        val s = header
        while (i in s.indices) {
            i++
            while (i < s.length && s[i].isWhitespace()) i++
            val eq = s.indexOf('=', i)
            if (eq < 0) break
            val key = s.substring(i, eq).trim().lowercase()
            var j = eq + 1
            while (j < s.length && s[j].isWhitespace()) j++
            val v: String
            if (j < s.length && s[j] == '"') {
                val sb = StringBuilder()
                j++
                while (j < s.length && s[j] != '"') {
                    if (s[j] == '\\' && j + 1 < s.length) j++
                    sb.append(s[j]); j++
                }
                v = sb.toString()
                j++
                i = s.indexOf(';', j).let { if (it < 0) s.length else it }
            } else {
                val semi = s.indexOf(';', j).let { if (it < 0) s.length else it }
                v = s.substring(j, semi).trim()
                i = semi
            }
            if (key.endsWith("*") || key.contains("*")) ext[key] = v else out[key] = v
        }
        // RFC 2231: name*=charset''value, name*0*=..., name*1*=...
        val grouped = ext.keys.groupBy { it.substringBefore('*') }
        for ((base, keys) in grouped) {
            val ordered = keys.sortedBy { it.substringAfter('*').trimEnd('*').toIntOrNull() ?: 0 }
            var charset = "utf-8"
            val sink = ByteSink()
            for ((idx, k) in ordered.withIndex()) {
                var v = ext[k]!!
                val encoded = k.endsWith("*")
                if (idx == 0 && encoded && v.count { it == '\'' } >= 2) {
                    charset = v.substringBefore('\'').ifEmpty { "utf-8" }
                    v = v.substringAfter('\'').substringAfter('\'')
                }
                if (encoded) {
                    var p = 0
                    while (p < v.length) {
                        if (v[p] == '%' && p + 2 < v.length) {
                            val hv = v.substring(p + 1, p + 3).toIntOrNull(16)
                            if (hv != null) { sink.write(hv); p += 3; continue }
                        }
                        sink.write(v[p].code and 0xFF); p++
                    }
                } else sink.write(v)
            }
            out[base] = Charsets.decode(sink.toByteArray(), charset)
        }
        return out
    }

    private fun splitMultipart(bytes: ByteArray, start: Int, end: Int, boundary: String): List<Pair<Int, Int>> {
        val delim = ("--$boundary").encodeToByteArray()
        val parts = ArrayList<Pair<Int, Int>>()
        var i = start
        var partStart = -1
        while (i < end) {
            val idx = bytes.indexOf(delim, i, end)
            if (idx < 0) break
            val atLineStart = idx == start || bytes[idx - 1] == '\n'.code.toByte()
            if (!atLineStart) { i = idx + 1; continue }
            if (partStart >= 0) {
                var pe = idx
                if (pe > partStart && bytes[pe - 1] == '\n'.code.toByte()) pe--
                if (pe > partStart && bytes[pe - 1] == '\r'.code.toByte()) pe--
                parts += partStart to pe
            }
            var j = idx + delim.size
            val closing = j + 1 < end && bytes[j] == '-'.code.toByte() && bytes[j + 1] == '-'.code.toByte()
            if (closing) return parts
            while (j < end && bytes[j] != '\n'.code.toByte()) j++
            partStart = j + 1
            i = partStart
        }
        if (partStart in 0 until end) parts += partStart to end
        return parts
    }

    fun decodeQuotedPrintable(bytes: ByteArray, start: Int, end: Int): ByteArray {
        val sink = ByteSink(end - start)
        var i = start
        while (i < end) {
            val b = bytes[i].toInt() and 0xFF
            if (b == '='.code) {
                if (i + 1 < end && bytes[i + 1] == '\n'.code.toByte()) { i += 2; continue }
                if (i + 2 < end && bytes[i + 1] == '\r'.code.toByte() && bytes[i + 2] == '\n'.code.toByte()) { i += 3; continue }
                if (i + 2 < end) {
                    val h = hex(bytes[i + 1].toInt()); val l = hex(bytes[i + 2].toInt())
                    if (h >= 0 && l >= 0) { sink.write(h * 16 + l); i += 3; continue }
                }
                // A stray '=' followed by trailing whitespace before a line break is a soft break too.
                var k = i + 1
                while (k < end && (bytes[k] == ' '.code.toByte() || bytes[k] == '\t'.code.toByte())) k++
                if (k < end && (bytes[k] == '\r'.code.toByte() || bytes[k] == '\n'.code.toByte())) {
                    i = if (bytes[k] == '\r'.code.toByte() && k + 1 < end) k + 2 else k + 1
                    continue
                }
            }
            sink.write(b); i++
        }
        return sink.toByteArray()
    }

    private fun hex(c: Int): Int = when (c) {
        in '0'.code..'9'.code -> c - '0'.code
        in 'a'.code..'f'.code -> c - 'a'.code + 10
        in 'A'.code..'F'.code -> c - 'A'.code + 10
        else -> -1
    }

    private val ENCODED_WORD = Regex("""=\?([^?]+)\?([bBqQ])\?([^?]*)\?=""")

    /** Decodes RFC 2047 encoded words in display headers such as Subject and From. */
    fun decodeWords(value: String): String {
        if (!value.contains("=?")) return value
        // Whitespace between adjacent encoded words is not significant.
        val collapsed = value.replace(Regex("""\?=\s+=\?"""), "?==?")
        return ENCODED_WORD.replace(collapsed) { m ->
            val charset = m.groupValues[1].substringBefore('*')
            val data = m.groupValues[3]
            val bytes = if (m.groupValues[2].equals("b", true)) B64.decode(data) else {
                val q = data.replace('_', ' ').encodeToByteArray()
                decodeQuotedPrintable(q, 0, q.size)
            }
            Charsets.decode(bytes, charset)
        }
    }
}
