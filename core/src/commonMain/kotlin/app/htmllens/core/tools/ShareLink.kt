package app.htmllens.core.tools

import app.htmllens.core.formats.Inflater
import app.htmllens.core.util.B64
import app.htmllens.core.util.deflateRaw

/**
 * Serverless sharing: the HTML is compressed into the URL fragment (#...), which browsers never
 * send to the server. Anyone with the link can open it in the web app; nothing is stored anywhere.
 */
object ShareLink {
    const val KEY_CODE = "code"
    const val KEY_NAME = "name"
    /** Practical limit: most chat apps and browsers handle links up to this length reliably. */
    const val RECOMMENDED_MAX = 32_000
    const val HARD_MAX = 1_500_000

    suspend fun encodePayload(html: String): String = B64.encode(deflateRaw(html.encodeToByteArray()), urlSafe = true)

    fun decodePayload(payload: String): String = Inflater.inflate(B64.decode(payload)).decodeToString()

    suspend fun buildUrl(appUrl: String, html: String, name: String?): String {
        val sb = StringBuilder(appUrl.trimEnd('#')).append('#')
        if (!name.isNullOrBlank()) sb.append(KEY_NAME).append('=').append(encodeComponent(name.take(80))).append('&')
        sb.append(KEY_CODE).append('=').append(encodePayload(html))
        return sb.toString()
    }

    /** Parses "#name=...&code=..." (leading # optional). Returns null when no code is present. */
    fun parseFragment(fragment: String): Pair<String?, String>? {
        val params = fragment.removePrefix("#").split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val code = params[KEY_CODE] ?: return null
        val html = runCatching { decodePayload(code) }.getOrNull() ?: return null
        return params[KEY_NAME]?.let { decodeComponent(it) } to html
    }

    private fun encodeComponent(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            if (c < 128 && (c.toChar().isLetterOrDigit() || c.toChar() in "-_.~")) append(c.toChar())
            else { append('%'); append("0123456789ABCDEF"[c shr 4]); append("0123456789ABCDEF"[c and 15]) }
        }
    }

    private fun decodeComponent(s: String): String = app.htmllens.core.util.Paths.percentDecode(s.replace('+', ' '))
}
