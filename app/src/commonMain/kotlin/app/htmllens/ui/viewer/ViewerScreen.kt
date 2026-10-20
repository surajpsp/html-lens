package app.htmllens.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.htmllens.AppController
import app.htmllens.BottomPanel
import app.htmllens.Device
import app.htmllens.LeftPanel
import app.htmllens.RightPanel
import app.htmllens.ViewerTab
import app.htmllens.core.analysis.Verdict
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.formats.Eml
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.util.formatBytes
import app.htmllens.platform.PlatformKind
import app.htmllens.ui.KindStyle
import app.htmllens.ui.components.Btn
import app.htmllens.ui.components.BtnStyle
import app.htmllens.ui.components.IconBtn
import app.htmllens.ui.components.IconTile
import app.htmllens.ui.components.Pill
import app.htmllens.ui.components.Segmented
import app.htmllens.ui.components.SearchField
import app.htmllens.ui.theme.Lens
import app.htmllens.viewer.CodeEditor
import app.htmllens.viewer.CodeEditorState
import app.htmllens.viewer.EditorOptions
import app.htmllens.viewer.HtmlView
import app.htmllens.viewer.ViewerCommand
import kotlinx.coroutines.delay

val modeColors @Composable get() = listOf(Lens.colors.success, Lens.colors.info, Lens.colors.primary)
val modeIcons = listOf(Icons.Rounded.Shield, Icons.Rounded.CloudOff, Icons.Rounded.Public)

@Composable
fun ViewerScreen(controller: AppController, tab: ViewerTab, wide: Boolean) {
    val c = Lens.colors
    val s = tab.session
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(controller, tab, wide)
        if (controller.tabs.size > 1) TabStrip(controller)
        Banners(controller, tab)
        if (tab.findOpen) FindBar(tab)
        if (s.bundle.kind == BundleKind.EML) EmailHeader(controller, tab)
        val panelOpenNarrow = !wide && (tab.left != null || tab.right != null || tab.bottom != null)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (wide && tab.left != null) {
                Box(Modifier.width(280.dp).fillMaxHeight().background(c.surface)) { LeftPanelView(controller, tab) }
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (panelOpenNarrow) {
                    NarrowPanel(controller, tab)
                } else {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.weight(if (tab.showSource && wide) tab.split else 1f).fillMaxHeight()) { Preview(controller, tab) }
                        if (tab.showSource && wide) {
                            Splitter { dx, total -> tab.split = (tab.split + dx / total).coerceIn(0.2f, 0.8f) }
                            Box(Modifier.weight(1f - tab.split).fillMaxHeight()) { SourcePane(controller, tab) }
                        }
                    }
                    if (wide && tab.bottom != null) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                        Box(Modifier.fillMaxWidth().height(240.dp).background(c.surface)) { DevToolsPanel(controller, tab) }
                    }
                }
            }
            if (wide && tab.right != null) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
                Box(Modifier.width(340.dp).fillMaxHeight().background(c.surface)) { RightPanelView(controller, tab) }
            }
        }
        StatusBar(controller, tab, wide)
    }
}

@Composable
private fun NarrowPanel(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    Column(Modifier.fillMaxSize().background(c.surface)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Back to page", style = MaterialTheme.typography.labelLarge, color = c.primary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                tab.left = null; tab.right = null; tab.bottom = null
            }.padding(8.dp))
        }
        HorizontalDivider(color = c.border)
        Box(Modifier.weight(1f)) {
            when {
                tab.left != null -> LeftPanelView(controller, tab)
                tab.right != null -> RightPanelView(controller, tab)
                tab.bottom != null -> DevToolsPanel(controller, tab)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Top bar
// -------------------------------------------------------------------------------------------

@Composable
private fun TopBar(controller: AppController, tab: ViewerTab, wide: Boolean) {
    val c = Lens.colors
    val s = tab.session
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back to library", { controller.navigate(app.htmllens.Screen.Home) })
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(tab.title, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                add(if (tab.appId != null) "Saved app" else kindLabel(s.bundle.kind))
                add(formatBytes(s.bundle.totalSize))
                if (s.bundle.files.size > 1) add("${s.bundle.files.size} files")
                if (s.currentPath.isNotEmpty() && s.bundle.files.size > 1) add(s.currentPath)
            }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        if (wide) {
            Text("Sandbox", style = MaterialTheme.typography.labelMedium, color = c.textFaint)
            Spacer(Modifier.width(8.dp))
            Segmented(
                SandboxMode.entries.map { it.label to null },
                s.mode.ordinal,
                { i -> controller.updateModeFor(tab, SandboxMode.entries[i]) },
                colors = modeColors, compact = true,
            )
            Spacer(Modifier.width(10.dp))
            DeviceMenu(tab)
            IconBtn(Icons.Rounded.Refresh, "Reload", { s.reload() })
            VerticalSep()
            ToolText("Find", tab.findOpen) { tab.findOpen = !tab.findOpen; if (!tab.findOpen) s.send(ViewerCommand.FindClear) }
            ToolText("Source", tab.showSource) { tab.showSource = !tab.showSource }
            ToolText(if (s.errorCount > 0) "Console · ${s.errorCount}" else "Console", tab.bottom != null, if (s.errorCount > 0) c.danger else null) {
                tab.bottom = if (tab.bottom != null) null else BottomPanel.CONSOLE
            }
            val verdict = tab.scan?.verdict
            ToolText(
                "Safety", tab.right == RightPanel.SECURITY,
                when (verdict) { Verdict.DANGEROUS -> c.danger; Verdict.SUSPICIOUS -> c.warning; else -> null },
            ) { tab.right = if (tab.right == RightPanel.SECURITY) null else RightPanel.SECURITY }
            if (s.bundle.files.size > 1 || tab.export != null) {
                ToolText("Files", tab.left == LeftPanel.FILES || tab.left == LeftPanel.SEARCH) {
                    tab.left = if (tab.left == LeftPanel.FILES || tab.left == LeftPanel.SEARCH) null else if (tab.export?.searchable == true) LeftPanel.SEARCH else LeftPanel.FILES
                }
            }
            if (s.bundle.attachments.isNotEmpty()) {
                ToolText("Attachments · ${s.bundle.attachments.size}", tab.left == LeftPanel.ATTACHMENTS) {
                    tab.left = if (tab.left == LeftPanel.ATTACHMENTS) null else LeftPanel.ATTACHMENTS
                }
            }
            Spacer(Modifier.width(6.dp))
            ExportMenu(controller, tab)
        } else {
            ModeMenuButton(controller, tab)
        }
        MoreMenu(controller, tab, wide)
        IconBtn(Icons.Rounded.Close, "Close this file", { controller.closeTab(tab) })
    }
    HorizontalDivider(color = c.border)
}

private fun kindLabel(kind: BundleKind) = when (kind) {
    BundleKind.HTML -> "HTML file"
    BundleKind.PASTE -> "Pasted code"
    BundleKind.ZIP -> "ZIP"
    BundleKind.FOLDER -> "Folder"
    BundleKind.MHTML -> "Saved web page (MHTML)"
    BundleKind.WEBARCHIVE -> "Safari web archive"
    BundleKind.EML -> "Email"
    BundleKind.SVG -> "SVG image"
    BundleKind.APP -> "Saved app"
}

@Composable
private fun ToolText(label: String, active: Boolean, color: Color? = null, onClick: () -> Unit) {
    val c = Lens.colors
    Text(
        label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal),
        color = color ?: if (active) c.text else c.textMuted,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (active) c.surface3 else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun ExportMenu(controller: AppController, tab: ViewerTab) {
    var open by remember { mutableStateOf(false) }
    val c = Lens.colors
    val s = tab.session
    val desktop = controller.platform.kind == PlatformKind.DESKTOP
    Box {
        Btn("Export", { open = true }, compact = true, trailing = Icons.Rounded.ExpandMore)
        DropdownMenu(open, { open = false }, containerColor = c.surface) {
            @Composable
            fun item(text: String, icon: ImageVector, action: () -> Unit) =
                DropdownMenuItem({ Text(text) }, { open = false; action() }, leadingIcon = { Icon(icon, null, tint = c.textMuted) })
            item(if (desktop) "PDF…" else "Print or save as PDF", Icons.Rounded.PictureAsPdf) { controller.exportPdf(tab) }
            item("Full-page image (PNG)", Icons.Rounded.Image) { controller.screenshot(tab) }
            item("One self-contained HTML file", Icons.Rounded.Inventory2) { controller.exportPacked(tab) }
            if (s.bundle.files.size > 1) item("ZIP of all files", Icons.Rounded.FolderZip) { controller.exportZip(tab) }
            HorizontalDivider(color = c.border)
            item("Copy a share link", Icons.Rounded.Link) { controller.shareTab(tab) }
            item(if (tab.appId != null) "Save a new version" else "Keep as an app", Icons.Rounded.Save) { controller.saveTabAsApp(tab) }
        }
    }
}

@Composable
private fun VerticalSep() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(20.dp).background(Lens.colors.border))
}

fun AppController.updateModeFor(tab: ViewerTab, mode: SandboxMode) {
    tab.session.changeMode(mode)
    tab.networkBannerDismissed = false
    tab.libraryId?.let { id -> updateItem(id) { it.copy(mode = mode) } }
    tab.appId?.let { id -> updateApp(id) { it.copy(mode = mode) } }
}

@Composable
private fun ModeMenuButton(controller: AppController, tab: ViewerTab) {
    var open by remember { mutableStateOf(false) }
    val s = tab.session
    Box {
        Btn(s.mode.label, { open = true }, icon = modeIcons[s.mode.ordinal], compact = true, trailing = Icons.Rounded.ExpandMore)
        DropdownMenu(open, { open = false }, containerColor = Lens.colors.surface) {
            SandboxMode.entries.forEachIndexed { i, m ->
                DropdownMenuItem(
                    { Column { Text(m.label, style = MaterialTheme.typography.titleSmall); Text(m.description, style = MaterialTheme.typography.bodySmall, color = Lens.colors.textMuted) } },
                    { open = false; controller.updateModeFor(tab, m) },
                    leadingIcon = { Icon(modeIcons[i], null, tint = modeColors[i]) },
                )
            }
        }
    }
}

@Composable
private fun DeviceMenu(tab: ViewerTab) {
    var open by remember { mutableStateOf(false) }
    val c = Lens.colors
    Box {
        Btn(if (tab.device == Device.RESPONSIVE) "Responsive" else tab.device.label, { open = true }, icon = Icons.Rounded.Devices, compact = true, style = BtnStyle.GHOST, trailing = Icons.Rounded.ExpandMore)
        DropdownMenu(open, { open = false }, containerColor = c.surface) {
            Device.entries.forEach { d ->
                DropdownMenuItem(
                    { Text(if (d.width > 0) "${d.label}   ${d.width}×${d.height}" else d.label, color = if (d == tab.device) c.primary else c.text) },
                    { open = false; tab.device = d },
                )
            }
            HorizontalDivider(color = c.border)
            DropdownMenuItem({ Text(if (tab.landscape) "Portrait" else "Landscape") }, { open = false; tab.landscape = !tab.landscape }, leadingIcon = { Icon(Icons.Rounded.ScreenRotation, null) })
        }
    }
}

@Composable
private fun MoreMenu(controller: AppController, tab: ViewerTab, wide: Boolean) {
    var open by remember { mutableStateOf(false) }
    val c = Lens.colors
    val s = tab.session
    val desktop = controller.platform.kind == PlatformKind.DESKTOP
    Box {
        IconBtn(Icons.Rounded.MoreVert, "More", { open = true })
        DropdownMenu(open, { open = false }, containerColor = c.surface) {
            @Composable
            fun item(text: String, icon: ImageVector, action: () -> Unit) =
                DropdownMenuItem({ Text(text) }, { open = false; action() }, leadingIcon = { Icon(icon, null, tint = c.textMuted) })
            if (!wide) {
                item("Find in page", Icons.Rounded.Search) { tab.findOpen = true }
                item("View source", Icons.Rounded.Code) { tab.right = null; tab.left = null; tab.bottom = null; controller.editTabSource(tab) }
                item("Console & network", Icons.Rounded.Terminal) { tab.bottom = BottomPanel.CONSOLE }
                item("Security scan", Icons.Rounded.Shield) { tab.right = RightPanel.SECURITY }
                item("Page info", Icons.Rounded.Info) { tab.right = RightPanel.INFO }
                if (s.bundle.files.size > 1) item("Files & search", Icons.Rounded.FolderOpen) { tab.left = if (tab.export?.searchable == true) LeftPanel.SEARCH else LeftPanel.FILES }
                if (s.bundle.attachments.isNotEmpty()) item("Attachments", Icons.Rounded.AttachFile) { tab.left = LeftPanel.ATTACHMENTS }
                HorizontalDivider(color = c.border)
            }
            if (!wide) {
                item(if (desktop) "Export PDF…" else "Print or save as PDF", Icons.Rounded.PictureAsPdf) { controller.exportPdf(tab) }
                item("Full-page image (PNG)", Icons.Rounded.Image) { controller.screenshot(tab) }
                item("One self-contained HTML file", Icons.Rounded.Inventory2) { controller.exportPacked(tab) }
                if (s.bundle.files.size > 1) item("ZIP of all files", Icons.Rounded.FolderZip) { controller.exportZip(tab) }
                item("Copy a share link", Icons.Rounded.Link) { controller.shareTab(tab) }
                item(if (tab.appId != null) "Save a new version" else "Keep as an app", Icons.Rounded.Save) { controller.saveTabAsApp(tab) }
                HorizontalDivider(color = c.border)
            }
            item("Outline (headings)", Icons.AutoMirrored.Rounded.ListAlt) { tab.left = if (tab.left == LeftPanel.OUTLINE) null else LeftPanel.OUTLINE }
            item("Page details", Icons.Rounded.Info) { tab.right = if (tab.right == RightPanel.INFO) null else RightPanel.INFO }
            if (tab.emailChecks.isNotEmpty() || s.bundle.kind == BundleKind.EML) item("Email checks", Icons.Rounded.Email) {
                if (tab.emailChecks.isEmpty()) controller.sourceText(tab)?.let { tab.emailChecks = controller.emailChecks(it) }
                tab.right = RightPanel.EMAIL
            }
            item("Back", Icons.AutoMirrored.Rounded.ArrowBack) { s.send(ViewerCommand.Back) }
            item("Forward", Icons.AutoMirrored.Rounded.ArrowForward) { s.send(ViewerCommand.Forward) }
            item("Edit the code", Icons.Rounded.Edit) { controller.editTabSource(tab) }
            item("Copy page source", Icons.Rounded.ContentCopy) { controller.sourceText(tab)?.let { controller.copy(it, "Source copied") } }
            HorizontalDivider(color = c.border)
            DropdownMenuItem(
                { Text(if (s.darkSim) "Dark mode simulation: on" else "Simulate dark mode") },
                { open = false; s.darkSim = !s.darkSim; s.reload() },
                leadingIcon = { Icon(Icons.Rounded.DarkMode, null, tint = if (s.darkSim) c.primary else c.textMuted) },
            )
            DropdownMenuItem(
                { Text(if (s.imagesOff) "Images blocked" else "Block images") },
                { open = false; s.imagesOff = !s.imagesOff; s.reload() },
                leadingIcon = { Icon(Icons.Rounded.HideImage, null, tint = if (s.imagesOff) c.primary else c.textMuted) },
            )
            if (desktop) {
                item("Open in your browser (with its DevTools)", Icons.Rounded.BugReport) { s.send(ViewerCommand.DevTools) }
                if (tab.sourcePath != null) {
                    DropdownMenuItem(
                        { Text(if (tab.liveReload) "Live reload: on" else "Live reload: off") },
                        { open = false; tab.liveReload = !tab.liveReload },
                        leadingIcon = { Icon(Icons.Rounded.Sync, null, tint = if (tab.liveReload) c.primary else c.textMuted) },
                    )
                    item(if (controller.platform.isMac) "Show in Finder" else "Show in folder", Icons.Rounded.FolderOpen) { controller.platform.reveal(tab.sourcePath) }
                }
            }
        }
    }
}

@Composable
private fun TabStrip(controller: AppController) {
    val c = Lens.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        controller.tabs.forEach { t ->
            val active = t == controller.activeTab
            Row(
                Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(9.dp)).background(if (active) c.primarySoft else c.surface2)
                    .clickable { controller.activate(t) }.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (t.appId != null) Icons.Rounded.Apps else KindStyle.icon(t.session.bundle.kind), null, tint = if (active) c.primary else c.textFaint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(t.title, style = MaterialTheme.typography.labelMedium, color = if (active) c.text else c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.Close, "Close", tint = c.textFaint, modifier = Modifier.padding(start = 4.dp).size(20.dp).clip(RoundedCornerShape(6.dp)).clickable { controller.closeTab(t) }.padding(3.dp))
            }
        }
    }
    HorizontalDivider(color = c.border)
}

// -------------------------------------------------------------------------------------------
// Banners, find bar, email header
// -------------------------------------------------------------------------------------------

@Composable
private fun Banners(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    val s = tab.session
    val scan = tab.scan
    when {
        scan != null && !tab.bannerDismissed && (scan.verdict == Verdict.DANGEROUS || scan.verdict == Verdict.SUSPICIOUS) -> {
            val danger = scan.verdict == Verdict.DANGEROUS
            Banner(
                if (danger) c.danger else c.warning, if (danger) c.dangerSoft else c.warningSoft, Icons.Rounded.Warning,
                (if (danger) "This file looks dangerous: " else "Be careful: ") + (scan.findings.firstOrNull()?.title ?: "suspicious content found") +
                    if (s.mode == SandboxMode.SAFE) ". Scripts and network are blocked." else ".",
                actions = {
                    Btn("Details", { tab.right = RightPanel.SECURITY }, compact = true, style = BtnStyle.GHOST)
                    if (s.mode != SandboxMode.SAFE) Btn("Switch to Safe", { controller.updateModeFor(tab, SandboxMode.SAFE) }, compact = true)
                },
                onDismiss = { tab.bannerDismissed = true },
            )
        }
        s.blockedCount > 0 && s.mode != SandboxMode.FULL && !tab.networkBannerDismissed -> {
            Banner(
                c.info, c.infoSoft, Icons.Rounded.CloudOff,
                "${s.blockedCount} request${if (s.blockedCount == 1) "" else "s"} to the internet ${if (s.blockedCount == 1) "was" else "were"} blocked. The page may look incomplete.",
                actions = {
                    Btn("Show", { tab.bottom = BottomPanel.NETWORK }, compact = true, style = BtnStyle.GHOST)
                    if (scan?.verdict != Verdict.DANGEROUS) Btn("Allow network", { controller.updateModeFor(tab, SandboxMode.FULL) }, compact = true)
                },
                onDismiss = { tab.networkBannerDismissed = true },
            )
        }
        tab.export != null && tab.export!!.searchable && s.bundle.htmlFiles.size > 1 && tab.left != LeftPanel.SEARCH && !tab.bannerDismissed -> {
            Banner(c.primary, c.primarySoft, Icons.Rounded.ManageSearch, "${tab.export!!.label} detected — ${tab.export!!.hint}",
                actions = { Btn("Search", { tab.left = LeftPanel.SEARCH }, compact = true) }, onDismiss = { tab.bannerDismissed = true })
        }
        s.bundle.warnings.isNotEmpty() && !tab.bannerDismissed -> {
            Banner(c.warning, c.warningSoft, Icons.Rounded.Warning, s.bundle.warnings.first() + if (s.bundle.warnings.size > 1) " (+${s.bundle.warnings.size - 1} more)" else "",
                onDismiss = { tab.bannerDismissed = true })
        }
    }
}

@Composable
private fun Banner(
    color: Color,
    background: Color,
    icon: ImageVector,
    text: String,
    actions: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(background).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Lens.colors.text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions?.invoke() }
        IconBtn(Icons.Rounded.Close, "Dismiss", onDismiss, size = 28.dp)
    }
}

@Composable
private fun FindBar(tab: ViewerTab) {
    val c = Lens.colors
    val s = tab.session
    LaunchedEffect(tab.findText) {
        delay(180)
        if (tab.findText.isNotEmpty()) s.send(ViewerCommand.Find(tab.findText, 1)) else s.send(ViewerCommand.FindClear)
    }
    Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SearchField(tab.findText, { tab.findText = it }, "Find in page", Modifier.widthIn(max = 360.dp).weight(1f, fill = false), onSubmit = { s.send(ViewerCommand.Find(tab.findText, 1)) })
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                tab.findText.isEmpty() -> ""
                s.findCount < 0 -> "found"
                s.findCount == 0 -> "No matches"
                else -> "${s.findCurrent} of ${s.findCount}"
            },
            style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.widthIn(min = 70.dp),
        )
        IconBtn(Icons.Rounded.ExpandLess, "Previous", { s.send(ViewerCommand.Find(tab.findText, -1)) }, enabled = tab.findText.isNotEmpty())
        IconBtn(Icons.Rounded.ExpandMore, "Next", { s.send(ViewerCommand.Find(tab.findText, 1)) }, enabled = tab.findText.isNotEmpty())
        Spacer(Modifier.weight(1f))
        IconBtn(Icons.Rounded.Close, "Close", { tab.findOpen = false; tab.findText = ""; s.send(ViewerCommand.FindClear) })
    }
    HorizontalDivider(color = c.border)
}

@Composable
private fun EmailHeader(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    val meta = tab.session.bundle.meta
    Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(meta[Eml.META_SUBJECT]?.ifBlank { null } ?: "(no subject)", style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        listOf(Eml.META_FROM, Eml.META_TO, Eml.META_CC, Eml.META_DATE).forEach { k ->
            meta[k]?.takeIf { it.isNotBlank() }?.let { v ->
                Row(Modifier.padding(vertical = 1.dp)) {
                    Text(k, style = MaterialTheme.typography.bodySmall, color = c.textFaint, modifier = Modifier.width(48.dp))
                    Text(v, style = MaterialTheme.typography.bodySmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val atts = tab.session.bundle.attachments
        if (atts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                atts.forEach { a ->
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(c.surface2).border(1.dp, c.border, RoundedCornerShape(8.dp))
                            .clickable { controller.downloadAttachment(a.name, a.mime, a.bytes) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.AttachFile, null, tint = c.textMuted, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(a.name, style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Text(formatBytes(a.bytes.size.toLong()), style = MaterialTheme.typography.labelSmall, color = c.textFaint)
                    }
                }
            }
        }
    }
    HorizontalDivider(color = c.border)
}

// -------------------------------------------------------------------------------------------
// Preview area
// -------------------------------------------------------------------------------------------

@Composable
private fun Preview(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    val s = tab.session
    val device = tab.device
    Box(Modifier.fillMaxSize().background(if (device == Device.RESPONSIVE) c.surface else c.bg)) {
        when {
            device == Device.RESPONSIVE -> HtmlView(s, Modifier.fillMaxSize(), primary = true, zoom = tab.zoom)
            device == Device.MULTI -> BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                val frames = listOf(Device.PHONE, Device.TABLET, Device.DESKTOP)
                val gap = 16.dp
                val totalW = frames.sumOf { it.width }
                val maxH = frames.maxOf { it.height }
                val availW = maxWidth - gap * (frames.size - 1)
                val scale = minOf(availW.value / totalW, maxHeight.value / maxH, 1f)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    frames.forEachIndexed { i, d ->
                        DeviceFrame(d.label, (d.width * scale).dp, (d.height * scale).dp) {
                            HtmlView(s, Modifier.fillMaxSize(), primary = i == 0, zoom = scale)
                        }
                    }
                }
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                val w = if (tab.landscape) device.height else device.width
                val h = if (tab.landscape) device.width else device.height
                val scale = minOf(maxWidth.value / w, (maxHeight.value - 24) / h, 1f)
                DeviceFrame("${device.label} · ${w}×${h}${if (scale < 1f) " · ${(scale * 100).toInt()}%" else ""}", (w * scale).dp, (h * scale).dp) {
                    HtmlView(s, Modifier.fillMaxSize(), primary = true, zoom = scale)
                }
            }
        }
    }
}

@Composable
private fun DeviceFrame(label: String, width: Dp, height: Dp, content: @Composable () -> Unit) {
    val c = Lens.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textFaint)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.size(width + 4.dp, height + 4.dp).clip(RoundedCornerShape(12.dp)).background(c.surface3)
                .border(1.dp, c.borderStrong, RoundedCornerShape(12.dp)).padding(2.dp),
        ) {
            Box(Modifier.size(width, height).clip(RoundedCornerShape(10.dp)).background(Color.White)) { content() }
        }
    }
}

@Composable
private fun SourcePane(controller: AppController, tab: ViewerTab) {
    val c = Lens.colors
    val path = tab.session.currentPath
    val revision = tab.session.revision
    val editor = remember(tab) { CodeEditorState(controller.sourceText(tab) ?: "", readOnly = true).also { tab.sourceEditor = it } }
    LaunchedEffect(path, revision) { controller.sourceText(tab)?.let { if (it != editor.text) editor.setText(it) } }
    Column(Modifier.fillMaxSize().background(if (c.isDark) Color(0xFF14141C) else Color.White)) {
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Code, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(path.substringBefore('#').ifEmpty { tab.session.bundle.entry }, style = MaterialTheme.typography.labelMedium.copy(fontFamily = Lens.mono), color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Btn("Edit", { controller.editTabSource(tab) }, icon = Icons.Rounded.Edit, compact = true, style = BtnStyle.GHOST)
            IconBtn(Icons.Rounded.Close, "Close source", { tab.showSource = false }, size = 28.dp)
        }
        HorizontalDivider(color = c.border)
        CodeEditor(editor, EditorOptions(c.isDark, controller.settings.editorFontSize - 1, readOnly = true, wrap = controller.settings.editorWrap, lang = "html"), Modifier.fillMaxSize())
    }
}

@Composable
fun Splitter(vertical: Boolean = true, onDrag: (Float, Float) -> Unit) {
    val c = Lens.colors
    val density = LocalDensity.current
    BoxWithConstraints {
        val total = with(density) { (if (vertical) 1200.dp else 800.dp).toPx() }
        Box(
            (if (vertical) Modifier.width(6.dp).fillMaxHeight() else Modifier.height(6.dp).fillMaxWidth())
                .background(c.bg)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    if (vertical) detectHorizontalDragGestures { change, amount -> change.consume(); onDrag(amount, total) }
                    else detectVerticalDragGestures { change, amount -> change.consume(); onDrag(amount, total) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(if (vertical) Modifier.width(2.dp).height(28.dp).clip(RoundedCornerShape(1.dp)).background(c.borderStrong) else Modifier.height(2.dp).width(28.dp).clip(RoundedCornerShape(1.dp)).background(c.borderStrong))
        }
    }
}

// -------------------------------------------------------------------------------------------
// Status bar
// -------------------------------------------------------------------------------------------

@Composable
private fun StatusBar(controller: AppController, tab: ViewerTab, wide: Boolean) {
    val c = Lens.colors
    val s = tab.session
    HorizontalDivider(color = c.border)
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Pill(s.mode.label, modeColors[s.mode.ordinal], icon = modeIcons[s.mode.ordinal])
        if (s.loading) Text("Loading…", style = MaterialTheme.typography.labelSmall, color = c.textFaint)
        if (s.darkSim) Pill("Dark sim", c.primary, icon = Icons.Rounded.DarkMode)
        if (s.imagesOff) Pill("Images off", c.primary, icon = Icons.Rounded.HideImage)
        if (tab.liveReload && tab.sourcePath != null) Pill("Live reload", c.success, icon = Icons.Rounded.Bolt)
        Spacer(Modifier.weight(1f))
        if (wide && tab.device == Device.RESPONSIVE) {
            IconBtn(Icons.Rounded.ZoomOut, "Zoom out", { tab.zoom = (tab.zoom - 0.1f).coerceAtLeast(0.3f) }, size = 24.dp)
            Text("${(tab.zoom * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = c.textMuted, modifier = Modifier.clickable { tab.zoom = 1f })
            IconBtn(Icons.Rounded.ZoomIn, "Zoom in", { tab.zoom = (tab.zoom + 0.1f).coerceAtMost(3f) }, size = 24.dp)
        }
        StatusCount(Icons.Rounded.Warning, "${s.errorCount}", if (s.errorCount > 0) c.danger else c.textFaint) { tab.bottom = BottomPanel.CONSOLE }
        StatusCount(Icons.Rounded.Public, "${s.network.size}${if (s.blockedCount > 0) " · ${s.blockedCount} blocked" else ""}", c.textFaint) { tab.bottom = BottomPanel.NETWORK }
        tab.scan?.let { scan ->
            val color = when (scan.verdict) { Verdict.CLEAN -> c.success; Verdict.CAUTION -> c.textMuted; Verdict.SUSPICIOUS -> c.warning; Verdict.DANGEROUS -> c.danger }
            StatusCount(Icons.Rounded.Shield, scan.verdict.label, color) { tab.right = RightPanel.SECURITY }
        } ?: if (tab.scanning) Text("Scanning…", style = MaterialTheme.typography.labelSmall, color = c.textFaint) else Unit
    }
}

@Composable
private fun StatusCount(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
