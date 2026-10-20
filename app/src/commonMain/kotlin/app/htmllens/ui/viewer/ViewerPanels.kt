package app.htmllens.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Css
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Javascript
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.htmllens.AppController
import app.htmllens.BottomPanel
import app.htmllens.LeftPanel
import app.htmllens.RightPanel
import app.htmllens.ViewerTab
import app.htmllens.core.analysis.CheckLevel
import app.htmllens.core.analysis.Severity
import app.htmllens.core.analysis.Verdict
import app.htmllens.core.search.BundleSearch
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.util.MimeTypes
import app.htmllens.core.util.Paths
import app.htmllens.core.util.formatBytes
import app.htmllens.ui.components.Btn
import app.htmllens.ui.components.BtnStyle
import app.htmllens.ui.components.Chip
import app.htmllens.ui.components.IconBtn
import app.htmllens.ui.components.KeyValue
import app.htmllens.ui.components.Label
import app.htmllens.ui.components.Pill
import app.htmllens.ui.components.SearchField
import app.htmllens.ui.components.Segmented
import app.htmllens.ui.components.TextInput
import app.htmllens.ui.components.Toggle
import app.htmllens.ui.theme.Lens
import app.htmllens.viewer.LogLevel
import app.htmllens.viewer.ViewerCommand
import kotlinx.coroutines.launch

@Composable
private fun PanelHeader(title: String, onClose: () -> Unit, actions: @Composable () -> Unit = {}) {
    val c = Lens.colors
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
        actions()
        IconBtn(Icons.Rounded.Close, "Close panel", onClose, size = 28.dp)
    }
    HorizontalDivider(color = c.border)
}

// -------------------------------------------------------------------------------------------
// Left panels
// -------------------------------------------------------------------------------------------

@Composable
fun LeftPanelView(controller: AppController, tab: ViewerTab) {
    when (tab.left) {
        LeftPanel.FILES, LeftPanel.SEARCH -> FilesAndSearch(controller, tab)
        LeftPanel.OUTLINE -> Outline(tab)
        LeftPanel.ATTACHMENTS -> Attachments(controller, tab)
        null -> Unit
    }
}

@Composable
private fun FilesAndSearch(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    Column(Modifier.fillMaxSize()) {
        PanelHeader(if (tab.left == LeftPanel.SEARCH) "Search all pages" else "Files", { tab.left = null })
        Segmented(listOf("Files" to null, "Search" to null), if (tab.left == LeftPanel.SEARCH) 1 else 0,
            { tab.left = if (it == 1) LeftPanel.SEARCH else LeftPanel.FILES }, Modifier.padding(10.dp), compact = true)
        if (tab.left == LeftPanel.SEARCH) BundleSearchView(tab) else FileTree(tab)
    }
}

private fun fileIcon(path: String, mime: String): ImageVector = when {
    MimeTypes.isHtml(mime) -> Icons.Rounded.Html
    MimeTypes.isCss(mime) -> Icons.Rounded.Css
    MimeTypes.isJs(mime) -> Icons.Rounded.Javascript
    MimeTypes.isImage(mime) -> Icons.Rounded.Image
    mime.startsWith("text/") -> Icons.Rounded.Description
    else -> Icons.Rounded.InsertDriveFile
}

@Composable
private fun FileTree(tab: ViewerTab) {
    val c = Lens.colors
    val s = tab.session
    var filter by remember { mutableStateOf("") }
    val files = remember(s.bundle, filter) {
        s.bundle.files.values.filter { filter.isBlank() || it.path.contains(filter, ignoreCase = true) }
            .sortedWith(compareBy({ Paths.dirname(it.path) }, { !it.isHtml }, { it.path }))
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(filter, { filter = it }, "Filter ${s.bundle.files.size} files", Modifier.fillMaxWidth().padding(horizontal = 10.dp))
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            var lastDir: String? = null
            files.forEach { f ->
                val dir = Paths.dirname(f.path)
                if (dir != lastDir) {
                    lastDir = dir
                    item(key = "dir:$dir") {
                        Text(if (dir.isEmpty()) "/" else "$dir/", style = MaterialTheme.typography.labelSmall.copy(fontFamily = Lens.mono), color = c.textFaint,
                            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item(key = f.path) {
                    val current = s.currentPath.substringBefore('#') == f.path
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (current) c.primarySoft else Color.Transparent)
                            .clickable { s.navigate(f.path) }.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(fileIcon(f.path, f.mime), null, tint = if (f.isHtml) c.primary else c.textFaint, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Paths.basename(f.path), style = MaterialTheme.typography.bodySmall, color = if (current) c.primary else c.text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(formatBytes(f.size.toLong()), style = MaterialTheme.typography.labelSmall, color = c.textFaint)
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleSearchView(tab: ViewerTab) {
    val c = Lens.colors
    val s = tab.session
    val scope = rememberCoroutineScope()
    fun run() {
        val q = tab.searchQuery
        if (q.trim().length < 2) return
        scope.launch {
            tab.searchProgress = 0f
            tab.searchHits = BundleSearch.search(s.bundle, q) { i, n -> tab.searchProgress = if (n == 0) 1f else i / n.toFloat() }
            tab.searchProgress = null
        }
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(tab.searchQuery, { tab.searchQuery = it }, "Search ${s.bundle.htmlFiles.size} pages…", Modifier.fillMaxWidth().padding(horizontal = 10.dp), onSubmit = { run() })
        Spacer(Modifier.height(6.dp))
        Btn("Search", { run() }, Modifier.padding(horizontal = 10.dp).fillMaxWidth(), compact = true, style = BtnStyle.SOFT, enabled = tab.searchQuery.trim().length >= 2)
        tab.searchProgress?.let {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { it }, color = c.primary, trackColor = c.surface3, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp))
        }
        val hits = tab.searchHits
        if (hits != null) {
            Text(
                if (hits.isEmpty()) "No matches" else "${hits.size}${if (hits.size >= 300) "+" else ""} matches in ${hits.map { it.file }.distinct().size} page(s)",
                style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(hits ?: emptyList()) { hit ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        val same = s.currentPath.substringBefore('#') == hit.file
                        if (same && hit.anchor != null) s.send(ViewerCommand.Anchor(hit.anchor!!))
                        else s.navigate(hit.file + (hit.anchor?.let { "#$it" } ?: ""))
                        if (tab.findText != tab.searchQuery) { tab.findText = tab.searchQuery }
                    }.padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    Text(hit.snippet, style = MaterialTheme.typography.bodySmall, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(Paths.basename(hit.file) + (hit.anchor?.let { "  #$it" } ?: ""), style = MaterialTheme.typography.labelSmall.copy(fontFamily = Lens.mono), color = c.textFaint, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Outline(tab: ViewerTab) {
    val c = Lens.colors
    val headings = tab.info?.headings ?: emptyList()
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Outline", { tab.left = null })
        if (headings.isEmpty()) {
            Text("This page has no headings.", style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(14.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(headings) { h ->
                Row(
                    Modifier.fillMaxWidth().clickable { tab.session.send(ViewerCommand.Heading(h.index)) }
                        .padding(start = (10 + (h.level - 1) * 14).dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("H${h.level}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = Lens.mono), color = c.textFaint, modifier = Modifier.width(26.dp))
                    Text(h.text, style = if (h.level <= 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                        color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Attachments(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Attachments", { tab.left = null })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            items(tab.session.bundle.attachments) { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { controller.downloadAttachment(a.name, a.mime, a.bytes) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AttachFile, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.bodySmall, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${a.mime} · ${formatBytes(a.bytes.size.toLong())}", style = MaterialTheme.typography.labelSmall, color = c.textFaint)
                    }
                    Icon(Icons.Rounded.Download, "Save", tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            "Attachments are saved as-is. Executables, scripts and Office files from unknown senders can be dangerous.",
            style = MaterialTheme.typography.bodySmall, color = c.textFaint, modifier = Modifier.padding(14.dp),
        )
    }
}

// -------------------------------------------------------------------------------------------
// Right panels
// -------------------------------------------------------------------------------------------

@Composable
fun RightPanelView(controller: AppController, tab: ViewerTab) {
    when (tab.right) {
        RightPanel.SECURITY -> SecurityPanel(controller, tab)
        RightPanel.INFO -> InfoPanel(tab)
        RightPanel.EMAIL -> EmailPanel(controller, tab)
        null -> Unit
    }
}

@Composable
private fun SecurityPanel(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    val scan = tab.scan
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Safety", { tab.right = null }) {
            IconBtn(Icons.Rounded.Refresh, "Scan again", { controller.rescan(tab) }, size = 28.dp)
        }
        if (scan == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) }
            return@Column
        }
        val color = when (scan.verdict) {
            Verdict.CLEAN -> c.success
            Verdict.CAUTION -> c.info
            Verdict.SUSPICIOUS -> c.warning
            Verdict.DANGEROUS -> c.danger
        }
        val headline = when (scan.verdict) {
            Verdict.CLEAN -> "Nothing suspicious found"
            Verdict.CAUTION -> "A few things to know"
            Verdict.SUSPICIOUS -> "Be careful with this file"
            Verdict.DANGEROUS -> "Don't trust this file"
        }
        val serious = scan.findings.count { it.severity == Severity.HIGH }
        val minor = scan.findings.size - serious
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
            item {
                Label("Safety report")
                Spacer(Modifier.height(6.dp))
                Text(headline, style = MaterialTheme.typography.headlineSmall, color = color)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildList {
                        if (serious > 0) add("$serious serious warning${if (serious > 1) "s" else ""}")
                        if (minor > 0) add("$minor minor")
                        if (isEmpty()) add("No warnings")
                    }.joinToString(", ") + ". Checked on this device.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
                if ((scan.verdict == Verdict.DANGEROUS || scan.verdict == Verdict.SUSPICIOUS) && tab.session.mode != SandboxMode.SAFE) {
                    Spacer(Modifier.height(12.dp))
                    Btn("Switch to Safe sandbox", { controller.updateModeFor(tab, SandboxMode.SAFE) }, compact = true, style = BtnStyle.PRIMARY)
                }
                if (scan.verdict == Verdict.DANGEROUS || scan.verdict == Verdict.SUSPICIOUS) {
                    Spacer(Modifier.height(10.dp))
                    Text("Don't type passwords, card numbers or personal details into this page.", style = MaterialTheme.typography.bodySmall, color = c.text)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = c.border)
            }
            items(scan.findings) { f ->
                val dot = when (f.severity) { Severity.HIGH -> c.danger; Severity.MEDIUM -> c.warning; Severity.LOW -> c.info; Severity.INFO -> c.textFaint }
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(7.dp).clip(RoundedCornerShape(50)).background(dot))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.title, style = MaterialTheme.typography.titleSmall, color = if (f.severity == Severity.INFO || f.severity == Severity.LOW) c.textMuted else c.text)
                            Spacer(Modifier.height(2.dp))
                            Text(f.detail, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                            f.evidence?.let {
                                Spacer(Modifier.height(5.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono, fontSize = 11.5.sp), color = c.textFaint, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            if (f.file != null && tab.session.bundle.files.size > 1) {
                                Text(f.file!!, style = MaterialTheme.typography.labelSmall.copy(fontFamily = Lens.mono), color = c.textFaint)
                            }
                        }
                    }
                }
                HorizontalDivider(color = c.border)
            }
            if (scan.externalDomains.isNotEmpty()) item {
                Spacer(Modifier.height(6.dp))
                Label("Contacts these domains")
                Spacer(Modifier.height(6.dp))
                Column {
                    scan.externalDomains.take(40).forEach { d ->
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Public, null, tint = c.textFaint, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(d, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono), color = c.text)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("This check looks for known tricks. It runs entirely on this device, and a clean result is not a guarantee.", style = MaterialTheme.typography.bodySmall, color = c.textFaint)
            }
        }
    }
}

@Composable
private fun InfoPanel(tab: ViewerTab) {
    val c = Lens.colors
    val info = tab.info
    val b = tab.session.bundle
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Page info", { tab.right = null })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp)) {
            item {
                Label("Document")
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Column {
                        KeyValue("Title", info?.title ?: b.name)
                        info?.description?.let { KeyValue("Description", it) }
                        info?.lang?.let { KeyValue("Language", it) }
                        info?.let { KeyValue("Encoding", it.charset) }
                        KeyValue("Type", b.kind.label)
                        KeyValue("Entry file", b.entry, mono = true)
                        KeyValue("Total size", formatBytes(b.totalSize))
                        if (b.files.size > 1) KeyValue("Files", "${b.files.size} (${b.htmlFiles.size} pages)")
                        info?.generator?.let { KeyValue("Generator", it) }
                        info?.canonical?.let { KeyValue("Canonical", it, mono = true) }
                        b.meta.forEach { (k, v) -> KeyValue(k, v) }
                        tab.export?.let { KeyValue("Recognised as", it.label) }
                    }
                }
            }
            if (info != null) item {
                Spacer(Modifier.height(14.dp))
                Label("Content")
                Spacer(Modifier.height(4.dp))
                KeyValue("Words", "${info.words} (~${info.readingMinutes} min read)")
                KeyValue("Headings", info.headings.size.toString())
                KeyValue("Links", "${info.internalLinks} internal · ${info.externalLinks} external")
                KeyValue("Images", "${info.images}" + if (info.imagesWithoutAlt > 0) " (${info.imagesWithoutAlt} without alt text)" else "")
                KeyValue("Scripts", info.scripts.toString())
                KeyValue("Stylesheets", info.stylesheets.toString())
                KeyValue("Mobile-friendly", if (info.hasViewport) "Yes (viewport meta)" else "No viewport meta tag")
            }
            if (b.warnings.isNotEmpty()) item {
                Spacer(Modifier.height(14.dp))
                Label("Warnings")
                b.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.warning, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

@Composable
private fun EmailPanel(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    val s = tab.session
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Email checks", { tab.right = null })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(12.dp)) {
                    Label("Preview as")
                    Spacer(Modifier.height(6.dp))
                    Segmented(
                        listOf("Desktop" to null, "Mobile" to null, "Full" to null),
                        when (tab.device) { app.htmllens.Device.EMAIL_DESKTOP -> 0; app.htmllens.Device.EMAIL_MOBILE -> 1; else -> 2 },
                        { tab.device = listOf(app.htmllens.Device.EMAIL_DESKTOP, app.htmllens.Device.EMAIL_MOBILE, app.htmllens.Device.RESPONSIVE)[it] },
                        compact = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark mode (client inversion)", style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.weight(1f))
                        Toggle(s.darkSim, { s.darkSim = it; s.reload() })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Images blocked (default in Outlook)", style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.weight(1f))
                        Toggle(s.imagesOff, { s.imagesOff = it; s.reload() })
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            items(tab.emailChecks) { ch ->
                val (color, icon) = when (ch.level) {
                    CheckLevel.PASS -> c.success to Icons.Rounded.CheckCircle
                    CheckLevel.INFO -> c.info to Icons.Rounded.Info
                    CheckLevel.WARN -> c.warning to Icons.Rounded.Warning
                    CheckLevel.FAIL -> c.danger to Icons.Rounded.Error
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(ch.title, style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(ch.detail, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Console & network
// -------------------------------------------------------------------------------------------

@Composable
fun DevToolsPanel(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    val s = tab.session
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Console", tab.bottom == BottomPanel.CONSOLE, { tab.bottom = BottomPanel.CONSOLE }, count = s.console.size)
            Spacer(Modifier.width(6.dp))
            Chip("Network", tab.bottom == BottomPanel.NETWORK, { tab.bottom = BottomPanel.NETWORK }, count = s.network.size)
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Rounded.DeleteSweep, "Clear", { s.clearLogs() }, size = 28.dp)
            IconBtn(Icons.Rounded.Close, "Close", { tab.bottom = null }, size = 28.dp)
        }
        HorizontalDivider(color = c.border)
        if (tab.bottom == BottomPanel.NETWORK) NetworkList(tab) else ConsoleList(controller, tab)
    }
}

@Composable
private fun ConsoleList(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    val s = tab.session
    var filter by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    val entries = s.console.filter {
        when (filter) {
            1 -> it.level == LogLevel.ERROR
            2 -> it.level == LogLevel.WARN
            3 -> it.level == LogLevel.LOG || it.level == LogLevel.INFO || it.level == LogLevel.DEBUG || it.level == LogLevel.RESULT || it.level == LogLevel.INPUT
            else -> true
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) listState.scrollToItem(entries.size - 1) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("All", "Errors ${s.errorCount}", "Warnings ${s.warnCount}", "Logs").forEachIndexed { i, l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = if (filter == i) c.primary else c.textMuted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (filter == i) c.primarySoft else Color.Transparent).clickable { filter = i }.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                if (entries.isEmpty()) item {
                    Text(
                        if (s.mode == SandboxMode.SAFE) "Scripts are disabled in Safe mode, so the page can't log anything." else "No console messages yet.",
                        style = MaterialTheme.typography.bodySmall, color = c.textFaint, modifier = Modifier.padding(12.dp),
                    )
                }
                itemsIndexed(entries) { _, e ->
                    val (color, bg) = when (e.level) {
                        LogLevel.ERROR -> c.danger to c.dangerSoft.copy(alpha = 0.5f)
                        LogLevel.WARN -> c.warning to c.warningSoft.copy(alpha = 0.5f)
                        LogLevel.RESULT -> c.primary to Color.Transparent
                        LogLevel.INPUT -> c.textMuted to Color.Transparent
                        else -> c.text to Color.Transparent
                    }
                    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 10.dp, vertical = 3.dp)) {
                        Text(
                            when (e.level) { LogLevel.RESULT -> "← "; LogLevel.INPUT -> "› "; else -> "" },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono, fontSize = 12.sp), color = c.textFaint,
                        )
                        Text(e.text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono, fontSize = 12.sp), color = color, modifier = Modifier.weight(1f))
                        if (e.count > 1) Pill(e.count.toString(), c.textMuted)
                        if (e.source.isNotEmpty()) Text("  ${e.source}:${e.line}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = Lens.mono), color = c.textFaint)
                    }
                    HorizontalDivider(color = c.border.copy(alpha = 0.5f))
                }
            }
        }
        HorizontalDivider(color = c.border)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("›", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Lens.mono, fontWeight = FontWeight.Bold), color = c.primary)
            Spacer(Modifier.width(6.dp))
            TextInput(
                input, { input = it }, Modifier.weight(1f), placeholder = if (s.mode == SandboxMode.SAFE) "JavaScript is disabled in Safe mode" else "Run JavaScript in the page (Enter)",
                mono = true, minHeight = 32.dp,
                onSubmit = {
                    if (input.isNotBlank() && s.mode != SandboxMode.SAFE) {
                        s.addConsole(LogLevel.INPUT, input)
                        s.send(ViewerCommand.Eval(input))
                        input = ""
                    }
                },
            )
        }
    }
}

@Composable
private fun NetworkList(tab: ViewerTab) {
    val c = Lens.colors
    val s = tab.session
    var filter by remember { mutableStateOf(0) }
    val entries = s.network.filter { when (filter) { 1 -> it.blocked || it.failed; 2 -> it.external; else -> true } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("All", "Blocked / failed", "External").forEachIndexed { i, l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = if (filter == i) c.primary else c.textMuted,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (filter == i) c.primarySoft else Color.Transparent).clickable { filter = i }.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.weight(1f))
            val hosts = s.network.filter { it.external }.mapNotNull { app.htmllens.core.analysis.ThreatScanner.hostOf(it.url) }.distinct().size
            Text("${s.network.size} requests · ${s.blockedCount} blocked · $hosts external host(s)", style = MaterialTheme.typography.labelSmall, color = c.textFaint)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (entries.isEmpty()) item { Text("No requests recorded.", style = MaterialTheme.typography.bodySmall, color = c.textFaint, modifier = Modifier.padding(12.dp)) }
            items(entries) { n ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color) = when {
                        n.blocked -> Icons.Rounded.Block to c.danger
                        n.failed -> Icons.Rounded.Error to c.warning
                        n.external -> Icons.Rounded.Public to c.info
                        else -> Icons.AutoMirrored.Rounded.KeyboardArrowRight to c.textFaint
                    }
                    Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (n.blocked) "blocked" else if (n.failed) "failed" else if (n.status > 0) n.status.toString() else "ok",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = Lens.mono), color = color, modifier = Modifier.width(56.dp),
                    )
                    Text(displayUrl(n.url), style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono, fontSize = 11.5.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(n.kind, style = MaterialTheme.typography.labelSmall, color = c.textFaint, modifier = Modifier.width(90.dp).padding(start = 8.dp), maxLines = 1)
                    Text(if (n.size > 0) formatBytes(n.size) else "", style = MaterialTheme.typography.labelSmall, color = c.textFaint, modifier = Modifier.width(64.dp))
                    Text(if (n.duration > 0) "${n.duration} ms" else "", style = MaterialTheme.typography.labelSmall, color = c.textFaint, modifier = Modifier.width(56.dp))
                }
                HorizontalDivider(color = c.border.copy(alpha = 0.5f))
            }
        }
    }
}

private fun displayUrl(url: String): String {
    val idx = url.indexOf(".htmllens.local/")
    if (idx > 0) return Paths.percentDecode(url.substring(idx + ".htmllens.local".length))
    val s = url.indexOf("/s/")
    if (s > 0 && url.indexOf('/', s + 3) > 0) {
        val rest = url.substring(s + 3).split('/', limit = 3)
        if (rest.size == 3) return "/" + Paths.percentDecode(rest[2])
    }
    return url
}
