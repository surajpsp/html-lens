package app.htmllens.core.analysis

import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.VFile
import app.htmllens.core.util.Charsets
import app.htmllens.core.util.MimeTypes
import app.htmllens.core.util.Url
import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.Serializable

@Serializable
enum class Severity(val weight: Int) { INFO(0), LOW(5), MEDIUM(15), HIGH(40) }

@Serializable
enum class Verdict(val label: String) {
    CLEAN("No threats found"),
    CAUTION("Minor concerns"),
    SUSPICIOUS("Suspicious"),
    DANGEROUS("Likely dangerous"),
}

@Serializable
data class Finding(
    val severity: Severity,
    val title: String,
    val detail: String,
    val file: String? = null,
    val evidence: String? = null,
)

@Serializable
data class ScanReport(
    val verdict: Verdict,
    val score: Int,
    val findings: List<Finding>,
    val externalDomains: List<String>,
    val scripts: Int,
    val forms: Int,
    val scannedFiles: Int,
)

/**
 * Heuristic scanner for phishing kits, HTML smuggling, ClickFix lures and data exfiltration.
 * Runs fully offline; no content is ever uploaded.
 */
object ThreatScanner {
    private const val MAX_FILES = 60
    private const val MAX_BYTES_PER_FILE = 6 * 1024 * 1024

    private val BRANDS = listOf(
        "microsoft", "office 365", "office365", "outlook", "onedrive", "sharepoint", "excel online", "adobe", "docusign",
        "dropbox", "gmail", "google account", "apple id", "icloud", "paypal", "netflix", "amazon", "dhl", "fedex", "ups",
        "wetransfer", "linkedin", "facebook", "instagram", "whatsapp", "coinbase", "binance", "metamask", "chase",
        "wells fargo", "bank of america", "hsbc", "citibank", "barclays", "zoom", "teams", "webmail", "roundcube", "cpanel",
    )
    private val EXFIL = listOf(
        Regex("""api\.telegram\.org/bot""", RegexOption.IGNORE_CASE) to "a Telegram bot",
        Regex("""discord(app)?\.com/api/webhooks""", RegexOption.IGNORE_CASE) to "a Discord webhook",
        Regex("""hooks\.slack\.com/services""", RegexOption.IGNORE_CASE) to "a Slack webhook",
        Regex("""(webhook\.site|pipedream\.net|requestbin|ngrok(-free)?\.(io|app)|trycloudflare\.com)""", RegexOption.IGNORE_CASE) to "a request-capture service",
        Regex("""(formspree\.io|getform\.io|formsubmit\.co|submit-form\.com|formcarry\.com|basin\.io)""", RegexOption.IGNORE_CASE) to "a form-forwarding service",
    )
    private val META_REFRESH = Regex("""<meta[^>]+http-equiv\s*=\s*["']?refresh["']?[^>]*content\s*=\s*["']?\s*\d*\s*;?\s*url\s*=\s*([^"'>\s]+)""", RegexOption.IGNORE_CASE)
    private val LOCATION_ASSIGN = Regex("""(window\.|document\.|top\.|self\.)?location(\.href)?\s*=(?!=)|location\.(replace|assign)\s*\(""")
    private val OBFUSCATION = listOf(
        Regex("""\beval\s*\(""") to "eval()",
        Regex("""\batob\s*\(""") to "atob()",
        Regex("""\bunescape\s*\(""") to "unescape()",
        Regex("""String\.fromCharCode\s*\(""") to "String.fromCharCode()",
        Regex("""new\s+Function\s*\(""") to "new Function()",
        Regex("""document\.write\s*\(""") to "document.write()",
    )
    private val LONG_B64 = Regex("""[A-Za-z0-9+/]{4000,}={0,2}""")
    private val HEX_ESCAPES = Regex("""(\\x[0-9a-fA-F]{2}){30,}""")
    private val SMUGGLING = Regex("""new\s+Blob\s*\(""")
    private val DOWNLOAD_TRIGGER = Regex("""(\.download\s*=|msSaveOrOpenBlob|msSaveBlob|createObjectURL)""")
    private val DANGEROUS_DOWNLOAD = Regex("""download\s*=\s*["'][^"']+\.(exe|scr|js|jse|vbs|vbe|hta|msi|iso|img|lnk|bat|cmd|ps1|zip|rar|7z|jar|dll|xll|one)["']""", RegexOption.IGNORE_CASE)
    private val CLIPBOARD = Regex("""(navigator\.clipboard\.writeText|execCommand\s*\(\s*["']copy["'])""")
    private val SHELL_WORDS = Regex("""(powershell|pwsh|mshta|cmd(\.exe)?\s*/c|curl\s+-|iwr\s|Invoke-WebRequest|bitsadmin|certutil|rundll32|regsvr32|osascript|bash\s+-c)""", RegexOption.IGNORE_CASE)
    private val RUN_DIALOG = Regex("""(win(dows)?\s*(\+|key\s*\+)\s*r\b|press\s+windows\s*\+\s*r|open\s+the\s+run\s+dialog|ctrl\s*\+\s*v.*enter)""", RegexOption.IGNORE_CASE)
    private val ANTI_DEBUG = Regex("""(event\.keyCode\s*==+\s*123|['"]F12['"]|contextmenu['"][^)]*\)\s*\{[^}]*preventDefault|\bdebugger\s*;)""")
    private val HASH_EMAIL = Regex("""location\.hash[\s\S]{0,200}(atob|@|email)""", RegexOption.IGNORE_CASE)
    private val MS_LOGIN_ASSETS = Regex("""(aadcdn\.msauth\.net|aadcdn\.msftauth\.net|logincdn\.msftauth\.net|login\.microsoftonline\.com)""", RegexOption.IGNORE_CASE)

    fun scan(bundle: Bundle): ScanReport {
        val findings = ArrayList<Finding>()
        val domains = LinkedHashSet<String>()
        var scripts = 0
        var forms = 0
        val candidates = bundle.files.values
            .filter { MimeTypes.isHtml(it.mime) || MimeTypes.isJs(it.mime) || it.mime == "image/svg+xml" }
            .sortedByDescending { it.path == bundle.entry }
            .take(MAX_FILES)
        val originalHost = bundle.meta["Original URL"]?.let { Url.parse(it)?.host }

        for (file in candidates) {
            if (file.size > MAX_BYTES_PER_FILE) continue
            val text = Charsets.decode(file.bytes, file.mime.substringAfter("charset=", "").ifEmpty { null })
            if (MimeTypes.isJs(file.mime)) {
                scanScript(text, file, findings)
                continue
            }
            val r = scanHtml(text, file, originalHost, domains, findings)
            scripts += r.first
            forms += r.second
        }

        val deduped = findings.distinctBy { it.severity to it.title }.sortedByDescending { it.severity.ordinal }
        val score = deduped.sumOf { it.severity.weight }.coerceAtMost(100)
        val hasHigh = deduped.any { it.severity == Severity.HIGH }
        val verdict = when {
            hasHigh && score >= 60 -> Verdict.DANGEROUS
            hasHigh || score >= 25 -> Verdict.SUSPICIOUS
            score >= 5 -> Verdict.CAUTION
            else -> Verdict.CLEAN
        }
        return ScanReport(verdict, score, deduped, domains.toList().sorted(), scripts, forms, candidates.size)
    }

    private fun scanHtml(
        text: String,
        file: VFile,
        originalHost: String?,
        domains: MutableSet<String>,
        out: MutableList<Finding>,
    ): Pair<Int, Int> {
        val doc = Ksoup.parse(text)
        val lowerText = doc.text().lowercase()
        val title = doc.title().lowercase()

        // External resources
        for (el in doc.select("script[src], link[href], img[src], iframe[src], form[action], embed[src], object[data], video[src], audio[src], source[src]")) {
            val raw = el.attr("src").ifEmpty { el.attr("href") }.ifEmpty { el.attr("action") }.ifEmpty { el.attr("data") }
            hostOf(raw)?.let { if (it != originalHost) domains += it }
        }
        val externalScripts = doc.select("script[src]").mapNotNull { hostOf(it.attr("src")) }.filter { it != originalHost }.distinct()
        if (externalScripts.isNotEmpty()) {
            out += Finding(Severity.INFO, "Loads scripts from the internet", "Scripts are loaded from: ${externalScripts.take(6).joinToString()}.", file.path)
        }

        // Forms and credential capture
        val formEls = doc.select("form")
        val passwordFields = doc.select("input[type=password]")
        val cardFields = doc.select("input").filter { i ->
            val key = (i.attr("name") + " " + i.attr("id") + " " + i.attr("autocomplete") + " " + i.attr("placeholder")).lowercase()
            listOf("cc-number", "cardnumber", "card_number", "card number", "cvv", "cvc", "cc-csc", "cc-exp", "expiry").any { key.contains(it) }
        }
        for (form in formEls) {
            val actionHost = hostOf(form.attr("action"))
            val hasPassword = form.select("input[type=password]").isNotEmpty()
            if (actionHost != null && actionHost != originalHost) {
                out += if (hasPassword) {
                    Finding(Severity.HIGH, "Sends a password to another website", "A login form submits what you type to $actionHost.", file.path, form.attr("action").take(160))
                } else {
                    Finding(Severity.LOW, "Form sends data to another website", "A form submits to $actionHost.", file.path, form.attr("action").take(160))
                }
            }
        }
        if (passwordFields.isNotEmpty()) {
            out += Finding(Severity.MEDIUM, "Asks for a password", "Pages opened from a file or attachment rarely need your password. Never sign in from a downloaded file.", file.path)
            val brand = BRANDS.firstOrNull { title.contains(it) || lowerText.take(5000).contains(it) }
            if (brand != null) {
                out += Finding(Severity.HIGH, "Looks like a ${brand.replaceFirstChar { it.uppercase() }} sign-in page", "A local file imitating a sign-in page is a classic phishing technique.", file.path)
            }
        }
        if (cardFields.isNotEmpty()) {
            out += Finding(Severity.HIGH, "Asks for payment card details", "Card number or security-code fields were found in a local file.", file.path)
        }
        if (MS_LOGIN_ASSETS.containsMatchIn(text)) {
            out += Finding(Severity.MEDIUM, "Uses Microsoft login page assets", "Microsoft sign-in images or scripts are referenced, which phishing kits often copy.", file.path)
        }

        // Redirects
        META_REFRESH.find(text)?.let { m ->
            val host = hostOf(m.groupValues[1])
            out += Finding(
                if (host != null) Severity.MEDIUM else Severity.LOW,
                "Redirects automatically",
                if (host != null) "The page sends you to $host as soon as it opens." else "The page reloads or redirects automatically.",
                file.path, m.value.take(160),
            )
        }

        // Inline scripts
        val scriptEls = doc.select("script")
        val inline = scriptEls.filter { it.attr("src").isEmpty() }.joinToString("\n") { it.data() }
        val handlers = doc.select("[onload], [onerror], [onclick], [onmouseover], [onfocus]").joinToString("\n") {
            it.attr("onload") + it.attr("onerror") + it.attr("onclick") + it.attr("onmouseover") + it.attr("onfocus")
        }
        scanScript(inline + "\n" + handlers, file, out)

        // Downloads / smuggling via markup
        DANGEROUS_DOWNLOAD.find(text)?.let {
            out += Finding(Severity.HIGH, "Offers a risky file download", "The page offers to download an executable or archive file.", file.path, it.value.take(160))
        }
        for (a in doc.select("a[href^=data:]")) {
            val href = a.attr("href").lowercase()
            if (href.startsWith("data:application/") || a.hasAttr("download")) {
                out += Finding(Severity.HIGH, "Contains an embedded file download", "A link carries an entire file inside the page (HTML smuggling).", file.path, a.attr("download").ifEmpty { href.take(60) })
            }
        }

        // Tracking pixels, iframes, javascript: links
        val pixels = doc.select("img").filter { img ->
            (img.attr("width") == "1" || img.attr("height") == "1" || img.attr("style").replace(" ", "").contains("width:1px")) && hostOf(img.attr("src")) != null
        }
        if (pixels.isNotEmpty()) {
            out += Finding(Severity.LOW, "Contains tracking pixels", "Invisible images report to ${pixels.mapNotNull { hostOf(it.attr("src")) }.distinct().take(3).joinToString()} when the page is opened online.", file.path)
        }
        val frames = doc.select("iframe[src]").mapNotNull { hostOf(it.attr("src")) }.distinct()
        if (frames.isNotEmpty()) {
            out += Finding(Severity.LOW, "Embeds other websites", "Frames load content from ${frames.take(4).joinToString()}.", file.path)
        }
        if (doc.select("a[href^=javascript:]").size > 3) {
            out += Finding(Severity.INFO, "Uses javascript: links", "Several links run code instead of navigating.", file.path)
        }
        if (RUN_DIALOG.containsMatchIn(lowerText)) {
            out += Finding(Severity.HIGH, "Tells you to run a command (ClickFix lure)", "The page instructs you to open the Run dialog or a terminal and paste something. Never do this.", file.path)
        }
        return scriptEls.size to formEls.size
    }

    private fun scanScript(code: String, file: VFile, out: MutableList<Finding>) {
        if (code.isBlank()) return
        for ((re, label) in EXFIL) {
            re.find(code)?.let {
                val sev = if (label.contains("Telegram") || label.contains("Discord") || label.contains("capture")) Severity.HIGH else Severity.MEDIUM
                out += Finding(sev, "Sends data to $label", "Phishing kits commonly forward stolen data to $label.", file.path, it.value)
            }
        }
        val used = OBFUSCATION.filter { (re, _) -> re.containsMatchIn(code) }.map { it.second }
        val decodesAndRuns = (code.contains("atob(") || code.contains("unescape(") || code.contains("fromCharCode")) &&
            (code.contains("eval(") || code.contains("document.write(") || code.contains("new Function"))
        if (decodesAndRuns) {
            out += Finding(Severity.HIGH, "Hides code and runs it", "Encoded code is decoded and executed at runtime (${used.joinToString()}).", file.path)
        } else if (used.size >= 3) {
            out += Finding(Severity.MEDIUM, "Uses obfuscation techniques", "Found ${used.joinToString()}.", file.path)
        }
        if (LONG_B64.containsMatchIn(code) || HEX_ESCAPES.containsMatchIn(code)) {
            out += Finding(Severity.MEDIUM, "Contains a large encoded payload", "A long block of encoded data is embedded in a script.", file.path)
        }
        if (SMUGGLING.containsMatchIn(code) && DOWNLOAD_TRIGGER.containsMatchIn(code)) {
            out += Finding(Severity.HIGH, "Builds and downloads a file (HTML smuggling)", "Script assembles a file in the browser and triggers a download, bypassing mail filters.", file.path)
        }
        if (CLIPBOARD.containsMatchIn(code) && SHELL_WORDS.containsMatchIn(code)) {
            out += Finding(Severity.HIGH, "Copies a command to your clipboard", "The page puts a shell command on your clipboard (ClickFix attack).", file.path, SHELL_WORDS.find(code)?.value)
        }
        LOCATION_ASSIGN.find(code)?.let {
            out += Finding(Severity.LOW, "Can redirect you elsewhere", "Script changes the page location.", file.path, snippet(code, it.range.first))
        }
        if (ANTI_DEBUG.containsMatchIn(code)) {
            out += Finding(Severity.MEDIUM, "Tries to block inspection", "The page disables right-click or developer tools, which legitimate pages rarely do.", file.path)
        }
        if (HASH_EMAIL.containsMatchIn(code)) {
            out += Finding(Severity.MEDIUM, "Reads your e-mail address from the link", "Personalised phishing pages pre-fill the victim's address from the URL.", file.path)
        }
        if (code.contains("document.cookie")) {
            out += Finding(Severity.INFO, "Reads cookies", "Script accesses document.cookie.", file.path)
        }
    }

    private fun snippet(code: String, at: Int): String {
        val s = (at - 30).coerceAtLeast(0)
        return code.substring(s, (at + 90).coerceAtMost(code.length)).replace(Regex("\\s+"), " ").trim()
    }

    fun hostOf(raw: String): String? {
        val r = raw.trim()
        if (!r.startsWith("http://", true) && !r.startsWith("https://", true) && !r.startsWith("//")) return null
        val u = Url.parse(if (r.startsWith("//")) "https:$r" else r) ?: return null
        return u.host.takeIf { it.isNotEmpty() }
    }
}
