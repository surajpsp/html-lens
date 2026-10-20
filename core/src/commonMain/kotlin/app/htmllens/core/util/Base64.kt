package app.htmllens.core.util

/** Small, dependency-free Base64 codec (standard + URL-safe), tolerant of whitespace when decoding. */
object B64 {
    private const val STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val DECODE = IntArray(256) { -1 }.also { t ->
        for (i in STD.indices) t[STD[i].code] = i
        t['-'.code] = 62
        t['_'.code] = 63
    }

    fun encode(data: ByteArray, urlSafe: Boolean = false, pad: Boolean = !urlSafe): String {
        val alphabet = if (urlSafe) URL else STD
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < data.size) {
            val n = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
            sb.append(alphabet[n shr 18 and 63]).append(alphabet[n shr 12 and 63])
                .append(alphabet[n shr 6 and 63]).append(alphabet[n and 63])
            i += 3
        }
        val rem = data.size - i
        if (rem == 1) {
            val n = (data[i].toInt() and 0xFF) shl 16
            sb.append(alphabet[n shr 18 and 63]).append(alphabet[n shr 12 and 63])
            if (pad) sb.append("==")
        } else if (rem == 2) {
            val n = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8)
            sb.append(alphabet[n shr 18 and 63]).append(alphabet[n shr 12 and 63]).append(alphabet[n shr 6 and 63])
            if (pad) sb.append('=')
        }
        return sb.toString()
    }

    fun decode(text: CharSequence): ByteArray = decode(text, 0, text.length)

    fun decode(text: CharSequence, start: Int, end: Int): ByteArray {
        val sink = ByteSink((end - start) * 3 / 4 + 4)
        var acc = 0
        var bits = 0
        for (i in start until end) {
            val c = text[i].code
            if (c >= 256) continue
            val v = DECODE[c]
            if (v < 0) {
                if (c == '='.code) break
                continue
            }
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                sink.write((acc shr bits) and 0xFF)
            }
        }
        return sink.toByteArray()
    }

    fun decodeBytes(data: ByteArray, start: Int = 0, end: Int = data.size): ByteArray {
        val sink = ByteSink((end - start) * 3 / 4 + 4)
        var acc = 0
        var bits = 0
        for (i in start until end) {
            val c = data[i].toInt() and 0xFF
            val v = DECODE[c]
            if (v < 0) {
                if (c == '='.code) break
                continue
            }
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                sink.write((acc shr bits) and 0xFF)
            }
        }
        return sink.toByteArray()
    }
}
