package app.htmllens.core.util

/** Helpers for the virtual, slash-separated file paths used inside bundles. */
object Paths {

    fun normalize(path: String): String {
        val parts = ArrayList<String>()
        for (seg in path.replace('\\', '/').split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    fun dirname(path: String): String {
        val i = path.lastIndexOf('/')
        return if (i < 0) "" else path.substring(0, i)
    }

    fun basename(path: String): String = path.substringAfterLast('/')

    fun extension(path: String): String {
        val name = basename(path.substringBefore('?').substringBefore('#'))
        val i = name.lastIndexOf('.')
        return if (i <= 0) "" else name.substring(i + 1).lowercase()
    }

    fun stripExtension(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i <= 0) name else name.substring(0, i)
    }

    /** Resolves [ref] (a relative URL path) against the directory of [fromFile]. */
    fun resolve(fromFile: String, ref: String): String {
        val clean = ref.substringBefore('#').substringBefore('?')
        return if (clean.startsWith("/")) normalize(clean) else normalize(dirname(fromFile) + "/" + clean)
    }

    /** Builds a relative reference from [fromFile] to [toFile] (both bundle paths). */
    fun relative(fromFile: String, toFile: String): String {
        val from = dirname(fromFile).split('/').filter { it.isNotEmpty() }
        val to = toFile.split('/').filter { it.isNotEmpty() }
        var common = 0
        while (common < from.size && common < to.size - 1 && from[common] == to[common]) common++
        val sb = StringBuilder()
        repeat(from.size - common) { sb.append("../") }
        sb.append(to.drop(common).joinToString("/") { encodeSegment(it) })
        return sb.toString()
    }

    fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val sink = ByteSink(s.length)
        var i = 0
        val bytes = s.encodeToByteArray()
        while (i < bytes.size) {
            val b = bytes[i]
            if (b == '%'.code.toByte() && i + 2 < bytes.size) {
                val h = hex(bytes[i + 1].toInt()) ; val l = hex(bytes[i + 2].toInt())
                if (h >= 0 && l >= 0) { sink.write(h * 16 + l); i += 3; continue }
            }
            sink.write(b.toInt()); i++
        }
        return sink.toByteArray().decodeToString()
    }

    private fun hex(c: Int): Int = when (c) {
        in '0'.code..'9'.code -> c - '0'.code
        in 'a'.code..'f'.code -> c - 'a'.code + 10
        in 'A'.code..'F'.code -> c - 'A'.code + 10
        else -> -1
    }

    private const val SAFE = "-_.~!$&'()*+,;=:@"

    fun encodeSegment(seg: String): String {
        val sb = StringBuilder()
        for (b in seg.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            if (c.toChar().isLetterOrDigit() && c < 128 || SAFE.indexOf(c.toChar()) >= 0) sb.append(c.toChar())
            else {
                sb.append('%'); sb.append("0123456789ABCDEF"[c shr 4]); sb.append("0123456789ABCDEF"[c and 15])
            }
        }
        return sb.toString()
    }

    fun encodePath(path: String): String = path.split('/').joinToString("/") { encodeSegment(it) }

    /** Makes a string safe to use as a single file name segment. */
    fun sanitizeSegment(s: String, maxLen: Int = 80): String {
        val cleaned = s.map { c -> if (c.isLetterOrDigit() || c == '.' || c == '-' || c == '_') c else '_' }
            .joinToString("").trim('.', '_')
        return cleaned.take(maxLen).ifEmpty { "file" }
    }
}
