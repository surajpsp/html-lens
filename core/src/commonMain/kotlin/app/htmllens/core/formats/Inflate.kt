package app.htmllens.core.formats

class FormatException(message: String) : Exception(message)

/**
 * Pure Kotlin raw DEFLATE decoder (RFC 1951) with table-driven Huffman decoding.
 * Shared by every platform so ZIP support behaves identically on desktop and web.
 */
class Inflater(private val input: ByteArray, start: Int = 0, private val end: Int = input.size, sizeHint: Int = 0) {
    private var pos = start
    private var bitBuf = 0
    private var bitCnt = 0
    private var out = ByteArray(if (sizeHint > 0) sizeHint else maxOf(1024, (end - start) * 3))
    private var outLen = 0

    fun inflate(): ByteArray {
        do {
            val last = bits(1) == 1
            when (bits(2)) {
                0 -> stored()
                1 -> codes(FIXED_LIT, FIXED_DIST)
                2 -> dynamic()
                else -> throw FormatException("Invalid DEFLATE block type")
            }
        } while (!last)
        if (pos - (bitCnt shr 3) > end) throw FormatException("Truncated DEFLATE stream")
        return if (outLen == out.size) out else out.copyOf(outLen)
    }

    private fun fill(need: Int) {
        while (bitCnt < need) {
            val b = if (pos < end) input[pos].toInt() and 0xFF else 0
            pos++
            if (pos > end + 4) throw FormatException("Truncated DEFLATE stream")
            bitBuf = bitBuf or (b shl bitCnt)
            bitCnt += 8
        }
    }

    private fun bits(need: Int): Int {
        if (need == 0) return 0
        fill(need)
        val v = bitBuf and ((1 shl need) - 1)
        bitBuf = bitBuf ushr need
        bitCnt -= need
        return v
    }

    private fun ensure(extra: Int) {
        if (outLen + extra <= out.size) return
        var n = out.size * 2
        while (n < outLen + extra) n *= 2
        out = out.copyOf(n)
    }

    private fun stored() {
        bitBuf = 0; bitCnt = 0
        if (pos + 4 > end) throw FormatException("Truncated stored block")
        val len = (input[pos].toInt() and 0xFF) or ((input[pos + 1].toInt() and 0xFF) shl 8)
        val nlen = (input[pos + 2].toInt() and 0xFF) or ((input[pos + 3].toInt() and 0xFF) shl 8)
        if (len != (nlen.inv() and 0xFFFF)) throw FormatException("Corrupt stored block")
        pos += 4
        if (pos + len > end) throw FormatException("Truncated stored block")
        ensure(len)
        input.copyInto(out, outLen, pos, pos + len)
        outLen += len
        pos += len
    }

    private fun decode(h: Huffman): Int {
        if (h.maxLen == 0) throw FormatException("Empty Huffman code used")
        fill(h.maxLen)
        val e = h.table[bitBuf and h.mask]
        val len = e and 15
        if (len == 0) throw FormatException("Invalid Huffman code")
        bitBuf = bitBuf ushr len
        bitCnt -= len
        return e ushr 4
    }

    private fun codes(lit: Huffman, dist: Huffman) {
        while (true) {
            val sym = decode(lit)
            if (sym < 256) {
                ensure(1)
                out[outLen++] = sym.toByte()
            } else if (sym == 256) {
                return
            } else {
                val li = sym - 257
                if (li >= 29) throw FormatException("Invalid length symbol")
                val len = LBASE[li] + bits(LEXT[li])
                val di = decode(dist)
                if (di >= 30) throw FormatException("Invalid distance symbol")
                val d = DBASE[di] + bits(DEXT[di])
                if (d > outLen) throw FormatException("Distance too far back")
                ensure(len)
                var from = outLen - d
                if (d >= len) {
                    out.copyInto(out, outLen, from, from + len)
                    outLen += len
                } else {
                    repeat(len) { out[outLen++] = out[from++] }
                }
            }
        }
    }

    private fun dynamic() {
        val nlen = bits(5) + 257
        val ndist = bits(5) + 1
        val ncode = bits(4) + 4
        if (nlen > 286 || ndist > 30) throw FormatException("Bad dynamic block counts")
        val lengths = IntArray(320)
        for (i in 0 until ncode) lengths[ORDER[i]] = bits(3)
        val lencode = Huffman(lengths, 0, 19)
        var index = 0
        while (index < nlen + ndist) {
            var sym = decode(lencode)
            if (sym < 16) {
                lengths[index++] = sym
            } else {
                var len = 0
                when (sym) {
                    16 -> {
                        if (index == 0) throw FormatException("Repeat with no previous length")
                        len = lengths[index - 1]
                        sym = 3 + bits(2)
                    }
                    17 -> sym = 3 + bits(3)
                    else -> sym = 11 + bits(7)
                }
                if (index + sym > nlen + ndist) throw FormatException("Too many lengths")
                repeat(sym) { lengths[index++] = len }
            }
        }
        if (lengths[256] == 0) throw FormatException("Missing end-of-block code")
        val lit = Huffman(lengths, 0, nlen)
        val dist = Huffman(lengths, nlen, ndist)
        codes(lit, dist)
    }

    class Huffman(lengths: IntArray, off: Int, n: Int) {
        val maxLen: Int
        val mask: Int
        val table: IntArray

        init {
            val count = IntArray(16)
            for (i in 0 until n) count[lengths[off + i]]++
            var ml = 0
            for (l in 15 downTo 1) if (count[l] != 0) { ml = l; break }
            maxLen = ml
            mask = (1 shl ml) - 1
            table = IntArray(1 shl ml)
            if (ml > 0) {
                val next = IntArray(16)
                var code = 0
                count[0] = 0
                for (bitsLen in 1..15) {
                    code = (code + count[bitsLen - 1]) shl 1
                    next[bitsLen] = code
                }
                for (sym in 0 until n) {
                    val len = lengths[off + sym]
                    if (len == 0) continue
                    val c = next[len]++
                    var rev = 0
                    for (b in 0 until len) rev = rev or (((c shr b) and 1) shl (len - 1 - b))
                    val entry = (sym shl 4) or len
                    var i = rev
                    val step = 1 shl len
                    while (i < table.size) { table[i] = entry; i += step }
                }
            }
        }
    }

    companion object {
        private val ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
        private val LBASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
        private val LEXT = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
        private val DBASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
        private val DEXT = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)

        private val FIXED_LIT = Huffman(IntArray(288) { i -> if (i < 144) 8 else if (i < 256) 9 else if (i < 280) 7 else 8 }, 0, 288)
        private val FIXED_DIST = Huffman(IntArray(30) { 5 }, 0, 30)

        fun inflate(data: ByteArray, start: Int = 0, end: Int = data.size, sizeHint: Int = 0): ByteArray =
            Inflater(data, start, end, sizeHint).inflate()
    }
}
