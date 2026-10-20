package app.htmllens.core.tools

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Comment
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/** HTML beautifier/minifier built on the shared parser. */
object Formatter {
    private val DOC_MARKERS = Regex("""<!doctype|<html|<head|<body""", RegexOption.IGNORE_CASE)

    fun isFullDocument(html: String) = DOC_MARKERS.containsMatchIn(html.take(4096))

    fun beautify(html: String, indent: Int = 2): String {
        val full = isFullDocument(html)
        val doc = if (full) Ksoup.parse(html) else Ksoup.parseBodyFragment(html)
        doc.outputSettings().prettyPrint(true).indentAmount(indent).outline(false)
        for (style in doc.select("style")) {
            val formatted = formatCss(style.data(), indent)
            style.empty(); style.appendChild(com.fleeksoft.ksoup.nodes.DataNode("\n$formatted\n"))
        }
        return (if (full) doc.outerHtml() else doc.body().html()).trim() + "\n"
    }

    fun minify(html: String): String {
        val full = isFullDocument(html)
        val doc = if (full) Ksoup.parse(html) else Ksoup.parseBodyFragment(html)
        doc.outputSettings().prettyPrint(false)
        minifyNode(doc)
        for (style in doc.select("style")) {
            val m = minifyCss(style.data())
            style.empty(); style.appendChild(com.fleeksoft.ksoup.nodes.DataNode(m))
        }
        for (el in doc.select("[style]")) el.attr("style", minifyCss(el.attr("style")))
        return if (full) doc.outerHtml() else doc.body().html()
    }

    private val PRESERVE = setOf("pre", "textarea", "script", "style", "code")

    private fun minifyNode(node: Node) {
        val kids = node.childNodes().toList()
        for (child in kids) {
            when (child) {
                is Comment -> if (!child.getData().trimStart().startsWith("[if") && !child.getData().trimStart().startsWith("<![")) child.remove()
                is TextNode -> {
                    val parent = child.parent()
                    if (parent is Element && parent.tagName().lowercase() in PRESERVE) continue
                    val collapsed = child.getWholeText().replace(Regex("\\s+"), " ")
                    if (collapsed == " " && parent is Element && parent.tagName().lowercase() in setOf("html", "head", "table", "tbody", "thead", "tr", "ul", "ol", "select")) {
                        child.remove()
                    } else child.text(collapsed)
                }
                is Element -> if (child.tagName().lowercase() !in PRESERVE) minifyNode(child)
                is Document -> minifyNode(child)
                else -> Unit
            }
        }
    }

    fun minifyCss(css: String): String = css
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*([{}:;,>])\\s*"), "$1")
        .replace(";}", "}")
        .trim()

    fun formatCss(css: String, indent: Int = 2): String {
        val src = minifyCss(css)
        if (src.isEmpty()) return ""
        val pad = " ".repeat(indent)
        val sb = StringBuilder()
        var level = 0
        var i = 0
        var inString: Char? = null
        fun newline() { sb.append('\n'); repeat(level) { sb.append(pad) } }
        while (i < src.length) {
            val c = src[i]
            if (inString != null) {
                sb.append(c); if (c == inString && src[i - 1] != '\\') inString = null; i++; continue
            }
            when (c) {
                '"', '\'' -> { inString = c; sb.append(c) }
                '{' -> { sb.append(" {"); level++; newline() }
                '}' -> {
                    while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
                    level = (level - 1).coerceAtLeast(0)
                    if (sb.isNotEmpty() && sb.last() != '\n') { sb.setLength(sb.trimEnd().length) ; newline() }
                    else { sb.setLength(sb.trimEnd().length); newline() }
                    sb.append('}')
                    if (i + 1 < src.length && src[i + 1] != '}') { sb.append('\n'); newline() }
                }
                ';' -> { sb.append(';'); if (i + 1 < src.length && src[i + 1] != '}') newline() }
                ':' -> { sb.append(':'); if (level > 0 && src.getOrNull(i + 1)?.isWhitespace() == false && !src.substring(i).startsWith("://")) sb.append(' ') }
                ',' -> { sb.append(if (level == 0) ",\n" else ", ") }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString().lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
