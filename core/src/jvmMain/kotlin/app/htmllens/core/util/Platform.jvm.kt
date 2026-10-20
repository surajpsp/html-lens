package app.htmllens.core.util

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.Deflater

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun platformDecode(bytes: ByteArray, charset: String): String? =
    runCatching { String(bytes, Charset.forName(charset.trim())) }.getOrNull()

actual suspend fun deflateRaw(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    try {
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(maxOf(64, data.size / 3))
        val buf = ByteArray(16 * 1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    } finally {
        deflater.end()
    }
}
