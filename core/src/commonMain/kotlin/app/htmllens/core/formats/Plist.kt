package app.htmllens.core.formats

import app.htmllens.core.util.B64
import app.htmllens.core.util.beUInt
import app.htmllens.core.util.startsWith
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.parser.Parser

/** Apple property lists: binary (bplist00) and XML. Values map to Map/List/String/ByteArray/Long/Double/Boolean. */
object Plist {
    private val BPLIST = "bplist00".encodeToByteArray()

    fun isBinary(bytes: ByteArray) = bytes.startsWith(BPLIST)

    fun parse(bytes: ByteArray): Any? = if (isBinary(bytes)) BinaryPlist(bytes).root() else parseXml(bytes.decodeToString())

    private fun parseXml(xml: String): Any? {
        val doc = Ksoup.parse(xml, Parser.xmlParser(), "")
        val plist = doc.selectFirst("plist") ?: return null
        val first = plist.children().firstOrNull() ?: return null
        return xmlValue(first)
    }

    private fun xmlValue(e: Element): Any? = when (e.tagName()) {
        "dict" -> {
            val map = LinkedHashMap<String, Any?>()
            val kids = e.children()
            var i = 0
            while (i + 1 < kids.size) {
                if (kids[i].tagName() == "key") map[kids[i].text()] = xmlValue(kids[i + 1])
                i += 2
            }
            map
        }
        "array" -> e.children().map { xmlValue(it) }
        "string" -> e.wholeText()
        "data" -> B64.decode(e.wholeText())
        "integer" -> e.text().trim().toLongOrNull()
        "real" -> e.text().trim().toDoubleOrNull()
        "true" -> true
        "false" -> false
        "date" -> e.text()
        else -> null
    }

    private class BinaryPlist(private val b: ByteArray) {
        private val trailer = b.size - 32
        private val offsetSize = b[trailer + 6].toInt() and 0xFF
        private val refSize = b[trailer + 7].toInt() and 0xFF
        private val numObjects = b.beUInt(trailer + 8, 8).toInt()
        private val top = b.beUInt(trailer + 16, 8).toInt()
        private val tableOffset = b.beUInt(trailer + 24, 8).toInt()

        init {
            if (b.size < 40 || offsetSize !in 1..8 || refSize !in 1..8 || numObjects < 0 || tableOffset !in 8 until b.size) {
                throw FormatException("Corrupt binary plist")
            }
        }

        fun root(): Any? = obj(top, 0)

        private fun offsetOf(i: Int): Int {
            if (i !in 0 until numObjects) throw FormatException("Bad plist object reference")
            return b.beUInt(tableOffset + i * offsetSize, offsetSize).toInt()
        }

        private fun lengthAt(off: Int, info: Int): Pair<Int, Int> {
            if (info != 0xF) return info to off + 1
            val marker = b[off + 1].toInt() and 0xFF
            val size = 1 shl (marker and 0xF)
            return b.beUInt(off + 2, size).toInt() to off + 2 + size
        }

        private fun obj(i: Int, depth: Int): Any? {
            if (depth > 64) throw FormatException("Plist nesting too deep")
            val off = offsetOf(i)
            val marker = b[off].toInt() and 0xFF
            val type = marker ushr 4
            val info = marker and 0xF
            return when (type) {
                0x0 -> when (info) { 0x8 -> false; 0x9 -> true; else -> null }
                0x1 -> {
                    val size = 1 shl info
                    val v = b.beUInt(off + 1, minOf(size, 8))
                    v
                }
                0x2 -> {
                    val size = 1 shl info
                    if (size == 4) Float.fromBits(b.beUInt(off + 1, 4).toInt()).toDouble() else Double.fromBits(b.beUInt(off + 1, 8))
                }
                0x3 -> Double.fromBits(b.beUInt(off + 1, 8))
                0x4 -> { val (len, start) = lengthAt(off, info); b.copyOfRange(start, start + len) }
                0x5 -> { val (len, start) = lengthAt(off, info); CharArray(len) { (b[start + it].toInt() and 0xFF).toChar() }.concatToString() }
                0x6 -> {
                    val (len, start) = lengthAt(off, info)
                    CharArray(len) { k -> (((b[start + 2 * k].toInt() and 0xFF) shl 8) or (b[start + 2 * k + 1].toInt() and 0xFF)).toChar() }.concatToString()
                }
                0x8 -> b.beUInt(off + 1, info + 1)
                0xA, 0xC -> {
                    val (len, start) = lengthAt(off, info)
                    List(len) { k -> obj(b.beUInt(start + k * refSize, refSize).toInt(), depth + 1) }
                }
                0xD -> {
                    val (len, start) = lengthAt(off, info)
                    val map = LinkedHashMap<String, Any?>()
                    for (k in 0 until len) {
                        val key = obj(b.beUInt(start + k * refSize, refSize).toInt(), depth + 1)?.toString() ?: continue
                        map[key] = obj(b.beUInt(start + (len + k) * refSize, refSize).toInt(), depth + 1)
                    }
                    map
                }
                else -> null
            }
        }
    }
}
