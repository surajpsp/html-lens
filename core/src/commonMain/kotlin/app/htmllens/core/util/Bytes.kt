package app.htmllens.core.util

import kotlin.random.Random

/** Byte-preserving decoding: every byte maps to the char with the same code (0..255). */
fun ByteArray.latin1(start: Int = 0, end: Int = size): String {
    val chars = CharArray(end - start)
    for (i in start until end) chars[i - start] = (this[i].toInt() and 0xFF).toChar()
    return chars.concatToString()
}

/** Inverse of [latin1]; chars above 0xFF are replaced by '?'. */
fun String.latin1Bytes(): ByteArray = ByteArray(length) { i ->
    val c = this[i].code
    (if (c <= 0xFF) c else '?'.code).toByte()
}

fun ByteArray.indexOf(pattern: ByteArray, from: Int = 0, to: Int = size): Int {
    if (pattern.isEmpty()) return from
    val first = pattern[0]
    val last = to - pattern.size
    var i = from
    outer@ while (i <= last) {
        if (this[i] != first) { i++; continue }
        for (j in 1 until pattern.size) if (this[i + j] != pattern[j]) { i++; continue@outer }
        return i
    }
    return -1
}

/** Case-insensitive ASCII search. [pattern] must be lowercase ASCII. */
fun ByteArray.indexOfIgnoreCase(pattern: String, from: Int = 0, to: Int = size): Int {
    if (pattern.isEmpty()) return from
    val p = pattern.encodeToByteArray()
    val last = to - p.size
    var i = from
    outer@ while (i <= last) {
        for (j in p.indices) {
            var b = this[i + j].toInt() and 0xFF
            if (b in 'A'.code..'Z'.code) b += 32
            if (b != (p[j].toInt() and 0xFF)) { i++; continue@outer }
        }
        return i
    }
    return -1
}

fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean {
    if (offset + prefix.size > size) return false
    for (i in prefix.indices) if (this[offset + i] != prefix[i]) return false
    return true
}

fun ByteArray.u16(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
fun ByteArray.u32(at: Int): Long = (u16(at).toLong()) or (u16(at + 2).toLong() shl 16)
fun ByteArray.u64(at: Int): Long = u32(at) or (u32(at + 4) shl 32)

/** Big-endian unsigned read of [len] bytes (len <= 8). */
fun ByteArray.beUInt(at: Int, len: Int): Long {
    var v = 0L
    for (i in 0 until len) v = (v shl 8) or (this[at + i].toLong() and 0xFF)
    return v
}

fun ByteArray.concat(other: ByteArray): ByteArray {
    val out = ByteArray(size + other.size)
    copyInto(out)
    other.copyInto(out, size)
    return out
}

/** Growable byte buffer for building outputs in common code. */
class ByteSink(initial: Int = 1024) {
    private var buf = ByteArray(maxOf(16, initial))
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra <= buf.size) return
        var n = buf.size * 2
        while (n < size + extra) n *= 2
        buf = buf.copyOf(n)
    }

    fun write(b: Int) { ensure(1); buf[size++] = b.toByte() }
    fun write(bytes: ByteArray, off: Int = 0, len: Int = bytes.size - off) {
        ensure(len); bytes.copyInto(buf, size, off, off + len); size += len
    }
    fun write(s: String) = write(s.encodeToByteArray())
    fun toByteArray(): ByteArray = buf.copyOf(size)
}

private val HEX = "0123456789abcdef"

fun randomId(length: Int = 12): String {
    val sb = StringBuilder(length)
    repeat(length) { sb.append(HEX[Random.nextInt(16)]) }
    return sb.toString()
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${oneDecimal(bytes / 1024.0)} KB"
    bytes < 1024L * 1024 * 1024 -> "${oneDecimal(bytes / (1024.0 * 1024))} MB"
    else -> "${oneDecimal(bytes / (1024.0 * 1024 * 1024))} GB"
}

private fun oneDecimal(v: Double): String {
    val r = kotlin.math.round(v * 10) / 10
    return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString()
}
