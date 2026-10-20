package app.htmllens.core.analysis

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.Serializable

@Serializable
enum class CheckLevel { PASS, INFO, WARN, FAIL }

@Serializable
data class EmailCheck(val level: CheckLevel, val title: String, val detail: String)

/** Email-client compatibility checks for HTML email templates (Gmail, Outlook, Apple Mail). */
object EmailLint {
    const val GMAIL_CLIP_BYTES = 102 * 1024

    fun lint(html: String): List<EmailCheck> {
        val out = ArrayList<EmailCheck>()
        val size = html.encodeToByteArray().size
        out += if (size > GMAIL_CLIP_BYTES) {
            EmailCheck(CheckLevel.FAIL, "Gmail will clip this email", "The HTML is ${size / 1024} KB. Gmail hides everything after 102 KB behind a \"View entire message\" link.")
        } else {
            EmailCheck(CheckLevel.PASS, "Size is under Gmail's 102 KB limit", "${size / 1024} KB of HTML.")
        }
        val doc = Ksoup.parse(html)
        val css = (doc.select("style").joinToString("\n") { it.data() } + "\n" + doc.select("[style]").joinToString("\n") { it.attr("style") }).lowercase()

        if (doc.select("script").isNotEmpty()) out += EmailCheck(CheckLevel.FAIL, "Contains <script>", "Every major email client removes JavaScript.")
        if (doc.select("form").isNotEmpty()) out += EmailCheck(CheckLevel.WARN, "Contains a form", "Forms are unsupported or disabled in Gmail, Outlook and most webmail.")
        if (doc.select("link[rel~=(?i)stylesheet]").isNotEmpty()) out += EmailCheck(CheckLevel.FAIL, "Uses an external stylesheet", "Linked CSS files are stripped by Gmail and Outlook. Inline your CSS.")
        if (doc.select("svg").isNotEmpty()) out += EmailCheck(CheckLevel.WARN, "Uses inline SVG", "Gmail and Outlook do not render SVG. Use PNG images instead.")
        if (doc.select("video, audio, iframe, embed, object").isNotEmpty()) out += EmailCheck(CheckLevel.WARN, "Embeds media or frames", "Video, audio and iframes are blocked by most clients.")
        if (Regex("display\\s*:\\s*(flex|grid)").containsMatchIn(css)) out += EmailCheck(CheckLevel.WARN, "Uses flexbox or grid", "Outlook for Windows ignores flex and grid layouts. Use tables for structure.")
        if (Regex("position\\s*:\\s*(absolute|fixed|sticky)").containsMatchIn(css)) out += EmailCheck(CheckLevel.WARN, "Uses CSS positioning", "position is stripped by Gmail and Outlook.")
        if (css.contains("background-image") || doc.select("[background]").isNotEmpty()) out += EmailCheck(CheckLevel.INFO, "Uses background images", "Outlook for Windows needs VML fallbacks for background images.")
        if (css.contains("@font-face") || doc.select("link[href*=fonts.googleapis]").isNotEmpty()) out += EmailCheck(CheckLevel.INFO, "Uses web fonts", "Web fonts only work in Apple Mail and a few clients. Provide a safe fallback stack.")
        if (css.contains("prefers-color-scheme")) out += EmailCheck(CheckLevel.PASS, "Has dark mode styles", "prefers-color-scheme rules are present (Apple Mail, Outlook.com).")
        else out += EmailCheck(CheckLevel.INFO, "No dark mode styles", "Clients like Gmail and Outlook may auto-invert your colors. Preview with Dark mode on.")

        val images = doc.select("img")
        val noAlt = images.count { !it.hasAttr("alt") }
        if (noAlt > 0) out += EmailCheck(CheckLevel.WARN, "$noAlt image(s) without alt text", "Many clients block images by default; alt text is what readers see.")
        val relative = images.count { val s = it.attr("src"); s.isNotEmpty() && !s.startsWith("http") && !s.startsWith("cid:") && !s.startsWith("data:") }
        if (relative > 0) out += EmailCheck(CheckLevel.FAIL, "$relative image(s) use relative paths", "Images in sent emails need absolute https:// URLs.")
        val dataImages = images.count { it.attr("src").startsWith("data:") }
        if (dataImages > 0) out += EmailCheck(CheckLevel.WARN, "$dataImages image(s) embedded as data URIs", "Gmail and Outlook block data: URI images.")
        val noWidth = images.count { !it.hasAttr("width") }
        if (noWidth > 0) out += EmailCheck(CheckLevel.INFO, "$noWidth image(s) without a width attribute", "Outlook may render images at their full size without an explicit width.")
        val wide = doc.select("table[width]").mapNotNull { it.attr("width").toIntOrNull() }.maxOrNull()
        if (wide != null && wide > 640) out += EmailCheck(CheckLevel.WARN, "Table is ${wide}px wide", "Most emails are designed at 600-640px so they fit preview panes.")
        if (doc.selectFirst("meta[name=viewport]") == null) out += EmailCheck(CheckLevel.INFO, "No viewport meta tag", "Add <meta name=\"viewport\" content=\"width=device-width\"> for mobile clients.")
        if (doc.title().isBlank()) out += EmailCheck(CheckLevel.INFO, "No <title>", "Some clients show the title in previews and web versions.")
        val emptyLinks = doc.select("a").count { val h = it.attr("href").trim(); h.isEmpty() || h == "#" }
        if (emptyLinks > 0) out += EmailCheck(CheckLevel.WARN, "$emptyLinks link(s) with empty or # targets", "Check that every button and link has a destination.")
        val linkCount = doc.select("a[href]").size
        out += EmailCheck(CheckLevel.INFO, "${images.size} images, $linkCount links", "Spam filters flag emails that are mostly images with little text.")
        return out.sortedBy { it.level.ordinal * -1 }
    }
}
