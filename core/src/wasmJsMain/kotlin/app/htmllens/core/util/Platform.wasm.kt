@file:OptIn(ExperimentalWasmJsInterop::class)

package app.htmllens.core.util

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

private fun jsNow(): Double = js("Date.now()")

actual fun currentTimeMillis(): Long = jsNow().toLong()

private fun jsDecode(bytes: Uint8Array, charset: String): String? =
    js("{ try { return new TextDecoder(charset, { fatal: false }).decode(bytes); } catch (e) { return null; } }")

actual fun platformDecode(bytes: ByteArray, charset: String): String? =
    jsDecode(bytes.toUint8Array(), charset.trim())

private fun jsDeflateRaw(bytes: Uint8Array): Promise<ArrayBuffer> =
    js("new Response(new Blob([bytes]).stream().pipeThrough(new CompressionStream('deflate-raw'))).arrayBuffer()")

actual suspend fun deflateRaw(data: ByteArray): ByteArray =
    jsDeflateRaw(data.toUint8Array()).await<ArrayBuffer>().toByteArray()

// ---- Fast-ish byte array bridging between Kotlin/Wasm and JS typed arrays ----

private fun newInt8Array(size: Int): Int8Array = js("new Int8Array(size)")
private fun setInt32(view: Int8Array, offset: Int, value: Int): Unit =
    js("{ const d = new DataView(view.buffer, view.byteOffset, view.byteLength); d.setInt32(offset, value, true); }")
private fun getInt32(view: Int8Array, offset: Int): Int =
    js("new DataView(view.buffer, view.byteOffset, view.byteLength).getInt32(offset, true)")
private fun setByte(view: Int8Array, offset: Int, value: Byte): Unit = js("{ view[offset] = value; }")
private fun getByte(view: Int8Array, offset: Int): Byte = js("view[offset]")
private fun int8Of(buffer: ArrayBuffer): Int8Array = js("new Int8Array(buffer)")
private fun int8OfU8(u8: Uint8Array): Int8Array = js("new Int8Array(u8.buffer, u8.byteOffset, u8.byteLength)")
private fun u8OfInt8(i8: Int8Array): Uint8Array = js("new Uint8Array(i8.buffer, i8.byteOffset, i8.byteLength)")

fun ByteArray.toInt8Array(): Int8Array {
    val out = newInt8Array(size)
    var i = 0
    val words = size - (size % 4)
    while (i < words) {
        val v = (this[i].toInt() and 0xFF) or
            ((this[i + 1].toInt() and 0xFF) shl 8) or
            ((this[i + 2].toInt() and 0xFF) shl 16) or
            ((this[i + 3].toInt() and 0xFF) shl 24)
        setInt32(out, i, v)
        i += 4
    }
    while (i < size) {
        setByte(out, i, this[i]); i++
    }
    return out
}

fun ByteArray.toUint8Array(): Uint8Array = u8OfInt8(toInt8Array())

fun Int8Array.toByteArray(): ByteArray {
    val size = length
    val out = ByteArray(size)
    var i = 0
    val words = size - (size % 4)
    while (i < words) {
        val v = getInt32(this, i)
        out[i] = v.toByte()
        out[i + 1] = (v shr 8).toByte()
        out[i + 2] = (v shr 16).toByte()
        out[i + 3] = (v shr 24).toByte()
        i += 4
    }
    while (i < size) {
        out[i] = getByte(this, i); i++
    }
    return out
}

fun ArrayBuffer.toByteArray(): ByteArray = int8Of(this).toByteArray()
fun Uint8Array.toByteArray(): ByteArray = int8OfU8(this).toByteArray()
