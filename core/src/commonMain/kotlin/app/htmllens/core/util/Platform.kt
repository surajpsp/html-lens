package app.htmllens.core.util

/** Wall-clock time in epoch milliseconds. */
expect fun currentTimeMillis(): Long

/**
 * Decodes [bytes] using the named [charset]. Returns null when the platform
 * does not know the charset so callers can fall back to UTF-8 / Windows-1252.
 */
expect fun platformDecode(bytes: ByteArray, charset: String): String?

/** Raw DEFLATE compression (no zlib header). Used for share links. */
expect suspend fun deflateRaw(data: ByteArray): ByteArray
