package app.htmllens.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.htmllens.AppController
import app.htmllens.Screen
import app.htmllens.ToolId
import app.htmllens.core.analysis.CheckLevel
import app.htmllens.core.analysis.EmailCheck
import app.htmllens.core.analysis.ScanReport
import app.htmllens.core.analysis.Severity
import app.htmllens.core.analysis.ThreatScanner
import app.htmllens.core.analysis.Verdict
import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.formats.ZipWriter
import app.htmllens.core.search.TextExtractor
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.tools.Formatter
import app.htmllens.core.tools.LineDiff
import app.htmllens.core.tools.Packer
import app.htmllens.core.tools.ShareLink
import app.htmllens.core.util.Charsets
import app.htmllens.core.util.Paths
import app.htmllens.core.util.formatBytes
import app.htmllens.ui.DiffView
import app.htmllens.ui.components.Btn
import app.htmllens.ui.components.BtnStyle
import app.htmllens.ui.components.Card
import app.htmllens.ui.components.IconBtn
import app.htmllens.ui.components.IconTile
import app.htmllens.ui.components.Label
import app.htmllens.ui.components.Pill
import app.htmllens.ui.theme.Lens
import app.htmllens.viewer.CodeEditor
import app.htmllens.viewer.CodeEditorState
import app.htmllens.viewer.EditorOptions
import kotlinx.coroutines.launch

fun toolIcon(id: ToolId): ImageVector = when (id) {
    ToolId.SAFETY -> Icons.Rounded.Shield
    ToolId.PACK -> Icons.Rounded.Inventory2
    ToolId.CONVERT -> Icons.Rounded.Transform
    ToolId.FORMAT -> Icons.Rounded.AutoFixHigh
    ToolId.EMAIL -> Icons.Rounded.Email
    ToolId.SHARE -> Icons.Rounded.Link
    ToolId.DIFF -> Icons.Rounded.Difference
    ToolId.TEXT -> Icons.Rounded.Article
}

fun toolColor(id: ToolId): Color = when (id) {
    ToolId.SAFETY -> Color(0xFF10B981)
    ToolId.PACK -> Color(0xFFF59E0B)
    ToolId.CONVERT -> Color(0xFF14B8A6)
    ToolId.FORMAT -> Color(0xFF6246EA)
    ToolId.EMAIL -> Color(0xFFEC4899)
    ToolId.SHARE -> Color(0xFF0EA5E9)
    ToolId.DIFF -> Color(0xFF8B5CF6)
    ToolId.TEXT -> Color(0xFF64748B)
}

@Composable
fun ToolsScreen(controller: AppController, wide: Boolean) {
    val c = Lens.colors
    val hPad = if (wide) 40.dp else 18.dp
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = hPad, vertical = if (wide) 52.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth()) {
            Label("Tools")
            Spacer(Modifier.height(10.dp))
            Text("Small tools for everyday HTML jobs", style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = c.text)
            Spacer(Modifier.height(10.dp))
            Text("Each one runs on this device. Files are never uploaded anywhere.", style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
            Spacer(Modifier.height(32.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            ToolId.entries.chunked(if (wide) 2 else 1).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    row.forEach { id -> ToolRow(id, Modifier.weight(1f)) { controller.navigate(Screen.Tool(id)) } }
                    if (row.size == 1 && wide) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolRow(id: ToolId, modifier: Modifier, onClick: () -> Unit) {
    val c = Lens.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(toolIcon(id), null, tint = c.textFaint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(id.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(id.subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            Text("→", style = MaterialTheme.typography.titleMedium, color = c.primary, modifier = Modifier.padding(start = 12.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
fun ToolScreen(controller: AppController, id: ToolId, wide: Boolean) {
    val c = Lens.colors
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "All tools", { controller.navigate(Screen.Tools) })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(id.title, style = MaterialTheme.typography.titleLarge, color = c.text)
                Text(id.subtitle, style = MaterialTheme.typography.bodySmall, color = c.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = c.border)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (id) {
                ToolId.SAFETY -> SafetyTool(controller)
                ToolId.PACK -> PackTool(controller)
                ToolId.CONVERT -> ConvertTool(controller)
                ToolId.FORMAT -> FormatTool(controller, wide)
                ToolId.EMAIL -> EmailTool(controller, wide)
                ToolId.SHARE -> ShareTool(controller)
                ToolId.DIFF -> DiffTool(controller, wide)
                ToolId.TEXT -> TextTool(controller)
            }
        }
    }
}

@Composable
private fun ToolPage(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Column(Modifier.widthIn(max = 900.dp)) { content() }
    }
}

@Composable
private fun PickRow(controller: AppController, folder: Boolean = true, onBundle: (Bundle) -> Unit) {
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn("Choose file", { scope.launch { controller.pickBundle()?.let(onBundle) } }, icon = Icons.Rounded.FolderOpen, style = BtnStyle.PRIMARY)
        if (folder && controller.platform.supportsFolderPick) Btn("Choose folder", { scope.launch { controller.pickFolderBundle()?.let(onBundle) } }, icon = Icons.Rounded.Folder)
    }
}

// --- Safety --------------------------------------------------------------------------------

@Composable
private fun SafetyTool(controller: AppController) {
    val c = Lens.colors
    var bundle by remember { mutableStateOf<Bundle?>(null) }
    var report by remember { mutableStateOf<ScanReport?>(null) }
    ToolPage {
        Text("Got an .html or .htm attachment you don't trust? Check it here first. The file is analysed without being opened or run, and never leaves your device.",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Spacer(Modifier.height(16.dp))
        PickRow(controller) { b -> bundle = b; report = ThreatScanner.scan(b) }
        val r = report
        val b = bundle
        if (r != null && b != null) {
            Spacer(Modifier.height(20.dp))
            ScanReportView(r, b.name)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("View in Safe mode", { controller.openBundleInViewer(b, SandboxMode.SAFE) }, icon = Icons.Rounded.Visibility)
                if (r.verdict != Verdict.DANGEROUS) Btn("Open normally", { controller.openBundleInViewer(b) })
            }
        }
        Spacer(Modifier.height(24.dp))
        Label("What we look for")
        Spacer(Modifier.height(8.dp))
        listOf(
            "Fake sign-in pages imitating Microsoft 365, Google, banks, DocuSign and more",
            "Forms that send passwords or card numbers to other websites",
            "Stolen data forwarded to Telegram bots, Discord webhooks or capture services",
            "HTML smuggling — files assembled in the page and downloaded automatically",
            "ClickFix lures that tell you to paste commands into Run or a terminal",
            "Obfuscated code, automatic redirects, tracking pixels and anti-inspection tricks",
        ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

@Composable
fun ScanReportView(r: ScanReport, name: String) {
    val c = Lens.colors
    val (color, soft, icon) = when (r.verdict) {
        Verdict.CLEAN -> Triple(c.success, c.successSoft, Icons.Rounded.GppGood)
        Verdict.CAUTION -> Triple(c.info, c.infoSoft, Icons.Rounded.GppGood)
        Verdict.SUSPICIOUS -> Triple(c.warning, c.warningSoft, Icons.Rounded.GppMaybe)
        Verdict.DANGEROUS -> Triple(c.danger, c.dangerSoft, Icons.Rounded.GppBad)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(soft).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.verdict.label, style = MaterialTheme.typography.headlineSmall, color = c.text)
                Text("$name · risk score ${r.score}/100 · ${r.scannedFiles} file(s)", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { r.score / 100f }, color = color, trackColor = c.surface, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
    }
    Spacer(Modifier.height(12.dp))
    if (r.findings.isEmpty()) Text("No warning signs were found.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    r.findings.forEach { f ->
        val fc = when (f.severity) { Severity.HIGH -> c.danger; Severity.MEDIUM -> c.warning; Severity.LOW -> c.info; Severity.INFO -> c.textFaint }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Pill(f.severity.name.lowercase().replaceFirstChar { it.uppercase() }, fc)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(f.title, style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(f.detail, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                f.evidence?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono, fontSize = 11.sp), color = c.textFaint, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    if (r.externalDomains.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Domains: " + r.externalDomains.take(15).joinToString(", "), style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono), color = c.textMuted)
    }
}

// --- Pack ---------------------------------------------------------------------------------

@Composable
private fun PackTool(controller: AppController) {
    val c = Lens.colors
    var bundle by remember { mutableStateOf<Bundle?>(null) }
    var result by remember { mutableStateOf<Packer.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    ToolPage {
        Text("Turn a web page with separate CSS, JavaScript, images and fonts — a folder, ZIP, MHTML or web archive — into one .html file that works anywhere, offline.",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Spacer(Modifier.height(16.dp))
        PickRow(controller) { b ->
            bundle = b
            result = runCatching { Packer.pack(b) }.onFailure { error = it.message }.getOrNull()
        }
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = c.danger, style = MaterialTheme.typography.bodyMedium) }
        val r = result
        val b = bundle
        if (r != null && b != null) {
            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth()) {
                Column {
                    Text("Ready: ${Paths.stripExtension(b.name)}.html", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    Text("${formatBytes(b.totalSize)} in ${b.files.size} file(s) → ${formatBytes(r.sizeBytes.toLong())} in 1 file · ${r.inlined} resources inlined",
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    if (r.missing.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("${r.missing.size} referenced file(s) were not found: " + r.missing.take(6).joinToString(), style = MaterialTheme.typography.bodySmall, color = c.warning)
                    }
                    r.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textFaint) }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn("Download .html", { controller.saveText(Paths.stripExtension(Paths.sanitizeSegment(b.name)) + ".html", "text/html", r.html) }, icon = Icons.Rounded.FileDownload, style = BtnStyle.PRIMARY)
                        Btn("Open in viewer", { controller.openPasted(r.html, b.name) }, icon = Icons.Rounded.Visibility)
                        Btn("Copy share link", { controller.copyShareLink(r.html, b.name) }, icon = Icons.Rounded.Link)
                    }
                }
            }
        }
    }
}

// --- Convert --------------------------------------------------------------------------------

@Composable
private fun ConvertTool(controller: AppController) {
    val c = Lens.colors
    var bundle by remember { mutableStateOf<Bundle?>(null) }
    ToolPage {
        Text("Open .mhtml / .mht files from Chrome, Edge or Internet Explorer and Safari .webarchive files on any device, then convert them to plain HTML.",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Spacer(Modifier.height(16.dp))
        PickRow(controller, folder = false) { bundle = it }
        bundle?.let { b ->
            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth()) {
                Column {
                    Text(b.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text("${b.kind.label} · ${b.files.size} resources · ${formatBytes(b.totalSize)}", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    b.meta["Original URL"]?.let { Text("Saved from $it", style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono), color = c.textFaint) }
                    if (b.kind !in setOf(BundleKind.MHTML, BundleKind.WEBARCHIVE)) {
                        Spacer(Modifier.height(8.dp))
                        Text("This isn't an MHTML or web archive, but you can still convert it.", style = MaterialTheme.typography.bodySmall, color = c.warning)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn("Single .html file", {
                            runCatching { Packer.pack(b) }.onSuccess { controller.saveText(Paths.stripExtension(Paths.sanitizeSegment(b.name)) + ".html", "text/html", it.html) }
                                .onFailure { controller.toast(it.message ?: "Conversion failed", true) }
                        }, icon = Icons.Rounded.FileDownload, style = BtnStyle.PRIMARY)
                        Btn("ZIP of files", {
                            val zip = ZipWriter.write(b.files.values.map { it.path to it.bytes })
                            controller.downloadAttachment(Paths.stripExtension(Paths.sanitizeSegment(b.name)) + ".zip", "application/zip", zip)
                        }, icon = Icons.Rounded.FolderZip)
                        Btn("View", { controller.openBundleInViewer(b) }, icon = Icons.Rounded.Visibility)
                    }
                }
            }
        }
    }
}

// --- Format ----------------------------------------------------------------------------------

@Composable
private fun FormatTool(controller: AppController, wide: Boolean) {
    val c = Lens.colors
    val input = remember { CodeEditorState("<div><h1>Paste messy HTML here</h1><p>Then press Beautify or Minify.</p><ul><li>One</li><li>Two</li></ul></div>") }
    var output by remember { mutableStateOf("") }
    val out = remember { CodeEditorState("", readOnly = true) }
    val options = EditorOptions(c.isDark, controller.settings.editorFontSize, false, true, "html")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("Beautify", { output = Formatter.beautify(input.text); out.setText(output) }, icon = Icons.Rounded.AutoFixHigh, style = BtnStyle.PRIMARY, compact = true)
            Btn("Minify", { output = Formatter.minify(input.text); out.setText(output) }, icon = Icons.Rounded.Compress, compact = true)
            Btn("Use as input", { input.setText(output) }, icon = Icons.Rounded.SwapHoriz, compact = true, style = BtnStyle.GHOST, enabled = output.isNotEmpty())
            Spacer(Modifier.weight(1f))
            if (output.isNotEmpty()) {
                val before = input.text.encodeToByteArray().size
                val after = output.encodeToByteArray().size
                Text("${formatBytes(before.toLong())} → ${formatBytes(after.toLong())}", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Btn("Copy", { controller.copy(output) }, icon = Icons.Rounded.ContentCopy, compact = true)
                Btn("Download", { controller.saveText("formatted.html", "text/html", output) }, icon = Icons.Rounded.FileDownload, compact = true)
            }
        }
        HorizontalDivider(color = c.border)
        if (wide) Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight()) { CodeEditor(input, options, Modifier.fillMaxSize()) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
            Box(Modifier.weight(1f).fillMaxHeight()) { CodeEditor(out, options.copy(readOnly = true), Modifier.fillMaxSize()) }
        } else Column(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxWidth()) { CodeEditor(input, options, Modifier.fillMaxSize()) }
            HorizontalDivider(color = c.border)
            Box(Modifier.weight(1f).fillMaxWidth()) { CodeEditor(out, options.copy(readOnly = true), Modifier.fillMaxSize()) }
        }
    }
}

// --- Email ------------------------------------------------------------------------------------

@Composable
private fun EmailTool(controller: AppController, wide: Boolean) {
    val c = Lens.colors
    val scope = rememberCoroutineScope()
    val input = remember { CodeEditorState(app.htmllens.Samples.emailDemo.substringAfter("Content-Type: text/html; charset=utf-8\n\n").substringBefore("\n--b1--")) }
    var checks by remember { mutableStateOf<List<EmailCheck>>(emptyList()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("Run checks", { checks = controller.emailChecks(input.text) }, icon = Icons.Rounded.CheckCircle, style = BtnStyle.PRIMARY, compact = true)
            Btn("Preview", { controller.openEmailPreview(input.text) }, icon = Icons.Rounded.Visibility, compact = true)
            Btn("Open .html / .eml", {
                scope.launch {
                    controller.pickBundle()?.let { b ->
                        val f = b.files[b.entry] ?: return@let
                        input.setText(Charsets.decode(f.bytes, f.mime.substringAfter("charset=", "").ifEmpty { null }))
                        checks = controller.emailChecks(input.text)
                    }
                }
            }, icon = Icons.Rounded.FolderOpen, compact = true, style = BtnStyle.GHOST)
        }
        HorizontalDivider(color = c.border)
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                CodeEditor(input, EditorOptions(c.isDark, controller.settings.editorFontSize, false, true, "html"), Modifier.fillMaxSize())
            }
            if (wide) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
                Column(Modifier.width(380.dp).fillMaxHeight().background(c.surface).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (checks.isEmpty()) Text("Paste your email HTML on the left and press Run checks. Then use Preview to see it at desktop and mobile widths, in dark mode and with images blocked.",
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    EmailChecksList(checks)
                }
            }
        }
        if (!wide && checks.isNotEmpty()) Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp)) { EmailChecksList(checks) }
    }
}

@Composable
private fun EmailChecksList(checks: List<EmailCheck>) {
    val c = Lens.colors
    checks.forEach { ch ->
        val (color, icon) = when (ch.level) {
            CheckLevel.PASS -> c.success to Icons.Rounded.CheckCircle
            CheckLevel.INFO -> c.info to Icons.Rounded.Info
            CheckLevel.WARN -> c.warning to Icons.Rounded.Warning
            CheckLevel.FAIL -> c.danger to Icons.Rounded.Error
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(ch.title, style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(ch.detail, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
    }
}

// --- Share ------------------------------------------------------------------------------------

@Composable
private fun ShareTool(controller: AppController) {
    val c = Lens.colors
    val scope = rememberCoroutineScope()
    val input = remember { CodeEditorState("<!doctype html>\n<html>\n<body>\n  <h1>Hello from HTML Lens</h1>\n  <p>Anyone with the link can open this page.</p>\n</body>\n</html>\n") }
    var link by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("The page is compressed into the link itself — after the #, which browsers never send to any server. Nothing is uploaded or stored anywhere.",
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn("Create link", {
                    scope.launch { link = ShareLink.buildUrl(controller.platform.appUrl, input.text, app.htmllens.core.analysis.PageAnalyzer.title(input.text)) }
                }, icon = Icons.Rounded.Link, style = BtnStyle.PRIMARY, compact = true)
                link?.let { l ->
                    Btn("Copy", { controller.copy(l, "Link copied") }, icon = Icons.Rounded.ContentCopy, compact = true)
                    Btn("Open", { controller.platform.openUrl(l) }, compact = true, style = BtnStyle.GHOST)
                    Text("${l.length} characters" + if (l.length > ShareLink.RECOMMENDED_MAX) " — long links may be cut off by some apps" else "",
                        style = MaterialTheme.typography.labelMedium, color = if (l.length > ShareLink.RECOMMENDED_MAX) c.warning else c.textMuted)
                }
            }
            link?.let {
                Spacer(Modifier.height(10.dp))
                SelectionContainer {
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono), color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface2).padding(10.dp))
                }
            }
        }
        HorizontalDivider(color = c.border)
        Box(Modifier.weight(1f)) { CodeEditor(input, EditorOptions(c.isDark, controller.settings.editorFontSize, false, true, "html"), Modifier.fillMaxSize()) }
    }
}

// --- Diff -------------------------------------------------------------------------------------

@Composable
private fun DiffTool(controller: AppController, wide: Boolean) {
    val c = Lens.colors
    val left = remember { CodeEditorState("<h1>Version one</h1>\n<p>Original text.</p>\n") }
    val right = remember { CodeEditorState("<h1>Version two</h1>\n<p>Original text.</p>\n<p>New paragraph.</p>\n") }
    var diff by remember { mutableStateOf<List<LineDiff.Line>?>(null) }
    val options = EditorOptions(c.isDark, controller.settings.editorFontSize, false, true, "html")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("Compare", { diff = LineDiff.diff(left.text, right.text) }, icon = Icons.Rounded.Difference, style = BtnStyle.PRIMARY, compact = true)
            Btn("Beautify both first", { left.setText(Formatter.beautify(left.text)); right.setText(Formatter.beautify(right.text)) }, compact = true, style = BtnStyle.GHOST)
            diff?.let { d ->
                val st = LineDiff.stats(d)
                Text("+${st.added} added · −${st.removed} removed", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Btn("Edit again", { diff = null }, compact = true, style = BtnStyle.GHOST)
            }
        }
        HorizontalDivider(color = c.border)
        val d = diff
        if (d != null) {
            DiffView(d, Modifier.fillMaxSize().padding(12.dp))
        } else Row(Modifier.weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Original", style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.padding(8.dp))
                CodeEditor(left, options, Modifier.fillMaxSize())
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Changed", style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.padding(8.dp))
                CodeEditor(right, options, Modifier.fillMaxSize())
            }
        }
    }
}

// --- Text -------------------------------------------------------------------------------------

@Composable
private fun TextTool(controller: AppController) {
    val c = Lens.colors
    var text by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("page") }
    ToolPage {
        Text("Pull the readable text out of an HTML page, saved website or email — without scripts, styles or markup.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Spacer(Modifier.height(16.dp))
        PickRow(controller) { b -> name = Paths.stripExtension(b.name); text = TextExtractor.extractBundle(b, maxChars = 5_000_000) }
        text?.let { t ->
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${t.split(Regex("\\s+")).count { it.isNotBlank() }} words", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Spacer(Modifier.weight(1f))
                Btn("Copy", { controller.copy(t) }, icon = Icons.Rounded.ContentCopy, compact = true)
                Btn("Download .txt", { controller.saveText("$name.txt", "text/plain", t) }, icon = Icons.Rounded.FileDownload, compact = true, style = BtnStyle.PRIMARY)
            }
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Text(t.take(200_000), style = MaterialTheme.typography.bodyMedium, color = c.text,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(16.dp))
            }
        }
    }
}
