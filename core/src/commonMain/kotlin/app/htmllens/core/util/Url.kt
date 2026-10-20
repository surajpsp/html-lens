package app.htmllens.core.util

/** Minimal RFC 3986 URL parsing and resolution, enough for archive rewriting. */
data class Url(
    val scheme: String,
    val authority: String?,
    val path: String,
    val query: String?,
    val fragment: String?,
) {
    val host: String get() = authority?.substringAfterLast('@')?.substringBefore(':')?.lowercase() ?: ""

    override fun toString(): String = buildString {
        append(scheme).append(':')
        if (authority != null) append("//").append(authority)
        append(path)
        if (query != null) append('?').append(query)
        if (fragment != null) append('#').append(fragment)
    }

    fun withoutFragment(): Url = copy(fragment = null)

    companion object {
        private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")

        fun parse(raw: String): Url? {
            val s = raw.trim()
            val m = SCHEME.find(s) ?: return null
            val scheme = m.groupValues[1].lowercase()
            var rest = s.substring(m.range.last + 1)
            var fragment: String? = null
            rest.indexOf('#').takeIf { it >= 0 }?.let { fragment = rest.substring(it + 1); rest = rest.substring(0, it) }
            var query: String? = null
            rest.indexOf('?').takeIf { it >= 0 }?.let { query = rest.substring(it + 1); rest = rest.substring(0, it) }
            var authority: String? = null
            if (rest.startsWith("//")) {
                val end = rest.indexOf('/', 2).let { if (it < 0) rest.length else it }
                authority = rest.substring(2, end)
                rest = rest.substring(end)
                if (rest.isEmpty()) rest = "/"
            }
            return Url(scheme, authority, rest, query, fragment)
        }

        /** Resolves [ref] against [base]. Returns null for unparseable input. */
        fun resolve(base: Url?, ref: String): Url? {
            val r = ref.trim()
            parse(r)?.let { return it.copy(path = removeDots(it.path)) }
            if (base == null) return null
            if (r.startsWith("//")) return parse(base.scheme + ":" + r)
            var rest = r
            var fragment: String? = null
            rest.indexOf('#').takeIf { it >= 0 }?.let { fragment = rest.substring(it + 1); rest = rest.substring(0, it) }
            var query: String? = null
            rest.indexOf('?').takeIf { it >= 0 }?.let { query = rest.substring(it + 1); rest = rest.substring(0, it) }
            if (rest.isEmpty()) {
                return base.copy(query = query ?: base.query, fragment = fragment)
            }
            val path = if (rest.startsWith("/")) rest else {
                val dir = base.path.substringBeforeLast('/', "")
                (if (base.authority != null && base.path.isEmpty()) "/" else "$dir/") + rest
            }
            return base.copy(path = removeDots(path), query = query, fragment = fragment)
        }

        fun removeDots(path: String): String {
            if (!path.contains("./") && !path.endsWith("/.") && !path.endsWith("/..")) return path
            val out = ArrayList<String>()
            val segs = path.split('/')
            for ((i, seg) in segs.withIndex()) {
                when (seg) {
                    "." -> if (i == segs.lastIndex) out.add("")
                    ".." -> { if (out.size > 1) out.removeAt(out.size - 1); if (i == segs.lastIndex) out.add("") }
                    else -> out.add(seg)
                }
            }
            return out.joinToString("/")
        }
    }
}
