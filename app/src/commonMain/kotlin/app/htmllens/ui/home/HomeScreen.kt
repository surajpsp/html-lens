package app.htmllens.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.htmllens.AppController
import app.htmllens.Dialog
import app.htmllens.Screen
import app.htmllens.ToolId
import app.htmllens.core.analysis.Verdict
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.model.LibraryItem
import app.htmllens.core.search.LibrarySearch
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.util.formatBytes
import app.htmllens.platform.PlatformKind
import app.htmllens.relativeTime
import app.htmllens.ui.components.Btn
import app.htmllens.ui.components.BtnStyle
import app.htmllens.ui.components.IconBtn
import app.htmllens.ui.components.Pill
import app.htmllens.ui.components.SearchField
import app.htmllens.ui.theme.Lens
import kotlinx.coroutines.delay

private enum class Filter(val label: String) { ALL("All"), STARRED("Starred"), FLAGGED("Flagged"), EMAIL("Emails"), ARCHIVES("Archives & folders"), CODE("Pasted code") }

private fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
        cornerRadius = CornerRadius(radius.toPx()),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(controller: AppController, wide: Boolean) {
    val c = Lens.colors
    var results by remember { mutableStateOf<List<LibrarySearch.Hit>?>(null) }
    var filter by remember { mutableStateOf(Filter.ALL) }
    val query = controller.libraryQuery
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = null; return@LaunchedEffect }
        delay(200)
        results = controller.searchLibrary(query)
    }
    val items = controller.library.filter { item ->
        when (filter) {
            Filter.ALL -> true
            Filter.STARRED -> item.favorite
            Filter.FLAGGED -> item.verdict == Verdict.SUSPICIOUS || item.verdict == Verdict.DANGEROUS
            Filter.EMAIL -> item.kind == BundleKind.EML
            Filter.ARCHIVES -> item.kind in setOf(BundleKind.ZIP, BundleKind.FOLDER, BundleKind.MHTML, BundleKind.WEBARCHIVE)
            Filter.CODE -> item.kind == BundleKind.PASTE
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val hPad = if (wide) 40.dp else 18.dp
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = hPad, end = hPad, top = if (wide) 52.dp else 24.dp, bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!wide) item {
                SearchField(query, { controller.libraryQuery = it }, "Search your files", Modifier.widthIn(max = 1120.dp).fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }

            if (results != null) {
                item {
                    Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth()) {
                        Text("Results for “${query.trim()}”", style = MaterialTheme.typography.headlineSmall, color = c.text)
                        Spacer(Modifier.height(4.dp))
                        Text("Titles and the text inside every page you've opened.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                        Spacer(Modifier.height(14.dp))
                        Rule()
                    }
                }
                val hits = results!!
                if (hits.isEmpty()) item {
                    Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(vertical = 28.dp)) {
                        Icon(Icons.Rounded.Search, null, tint = c.textFaint)
                        Spacer(Modifier.height(8.dp))
                        Text("Nothing you've opened contains that.", style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
                    }
                }
                items(hits, key = { "hit-" + it.id }) { hit ->
                    controller.library.firstOrNull { it.id == hit.id }?.let { LibraryRow(controller, it, wide, snippet = hit.snippet) }
                }
                return@LazyColumn
            }

            item { Hero(controller, wide) }
            item { Tasks(controller, wide) }

            item {
                Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(top = 60.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Recently opened", style = MaterialTheme.typography.headlineSmall, color = c.text)
                        Spacer(Modifier.width(14.dp))
                        if (controller.library.isNotEmpty()) {
                            Text("${controller.library.size} file${if (controller.library.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = c.textFaint, modifier = Modifier.padding(bottom = 3.dp))
                        }
                    }
                    if (controller.library.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Filter.entries.forEach { f ->
                                val count = when (f) {
                                    Filter.ALL -> null
                                    else -> null
                                }
                                Text(
                                    f.label + (count?.let { " $it" } ?: ""),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (filter == f) FontWeight.SemiBold else FontWeight.Normal),
                                    color = if (filter == f) c.text else c.textMuted,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { filter = f }.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (wide && controller.library.isNotEmpty()) TableHeader()
                    else Rule()
                }
            }

            if (controller.library.isEmpty()) {
                item { EmptyLibrary(controller) }
            } else {
                if (items.isEmpty()) item {
                    Text("No files match this filter.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                        modifier = Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(vertical = 18.dp))
                }
                items(items, key = { it.id }) { LibraryRow(controller, it, wide) }
                item {
                    Row(Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Try a sample: ", style = MaterialTheme.typography.bodyMedium, color = c.textFaint)
                        SampleLinks(controller)
                    }
                }
            }
            item {
                Text(
                    "Everything you open stays on this device. HTML Lens has no servers that see your files.",
                    style = MaterialTheme.typography.bodySmall, color = c.textFaint,
                    modifier = Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(top = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun Rule() = Box(Modifier.fillMaxWidth().height(1.dp).background(Lens.colors.border))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(controller: AppController, wide: Boolean) {
    val c = Lens.colors
    val web = controller.platform.kind == PlatformKind.WEB
    FlowRow(
        Modifier.widthIn(max = 1120.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        maxItemsInEachRow = if (wide) 2 else 1,
    ) {
        Column(Modifier.weight(1.35f).widthIn(min = 280.dp)) {
            Text(
                "NO UPLOAD · NO ACCOUNT · WORKS OFFLINE",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = Lens.mono, letterSpacing = 1.1.sp),
                color = c.accent,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (wide) "Open any HTML file — and see exactly what it does." else "Open any HTML file, safely.",
                style = if (wide) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displaySmall,
                color = c.text,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Saved pages, email attachments, exports and reports open with their images and styles intact. " +
                    "Anything you don't trust runs in a sealed sandbox first.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (wide) 17.sp else 15.5.sp, lineHeight = if (wide) 27.sp else 24.sp),
                color = c.textMuted, modifier = Modifier.widthIn(max = 540.dp),
            )
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Btn("Open a file", { controller.pickAndOpen() }, style = BtnStyle.PRIMARY)
                Spacer(Modifier.width(10.dp))
                Btn("Open a folder", { controller.pickFolderAndOpen() })
                Spacer(Modifier.width(14.dp))
                TextLink("or paste code") { controller.dialog = Dialog.Paste }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Tip: copied some HTML? Press ${if (controller.platform.isMac) "⌘V" else "Ctrl+V"} anywhere on this screen to open it.",
                style = MaterialTheme.typography.bodySmall, color = c.textFaint,
            )
        }
        if (wide) {
            Column(
                Modifier.weight(1f).widthIn(min = 260.dp).heightIn(min = 250.dp).dashedBorder(c.borderStrong, 10.dp)
                    .clip(RoundedCornerShape(10.dp)).clickable { controller.pickAndOpen() }.background(c.surface.copy(alpha = 0.6f)).padding(26.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Drop it here", style = MaterialTheme.typography.headlineSmall, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (web) "A file, a ZIP, or a whole folder with its images and CSS. Anywhere on this page works."
                        else "A file, a ZIP, or a whole folder with its images and CSS. Anywhere in this window works.",
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                    )
                }
                Spacer(Modifier.height(22.dp))
                val formats = listOf(".html  .htm", ".mhtml  .mht", ".zip  folders", ".eml", ".webarchive", ".svg")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    formats.chunked(2).forEach { row ->
                        Row {
                            row.forEach { f ->
                                Text(f, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Lens.mono, fontSize = 13.sp), color = c.textMuted, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit) {
    val c = Lens.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text, style = MaterialTheme.typography.labelLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
        color = if (hovered) c.primaryHover else c.primary,
        modifier = Modifier.hoverable(interaction).clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(vertical = 6.dp),
    )
}

private data class Task(val title: String, val text: String, val run: () -> Unit)

@Composable
private fun Tasks(controller: AppController, wide: Boolean) {
    val c = Lens.colors
    val tasks = listOf(
        Task("Check if an attachment is safe", "Spots fake sign-in pages and data theft before you open it.") { controller.navigate(Screen.Tool(ToolId.SAFETY)) },
        Task("Run code from ChatGPT or Claude", "Paste it, see it work, keep it as an app.") { controller.dialog = Dialog.Paste },
        Task("Turn MHTML or a web archive into HTML", "One clean file that opens anywhere.") { controller.navigate(Screen.Tool(ToolId.CONVERT)) },
        Task("Test an HTML email", "Gmail clipping, dark mode and Outlook problems.") { controller.navigate(Screen.Tool(ToolId.EMAIL)) },
    )
    Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(top = if (wide) 64.dp else 40.dp)) {
        Text("Or start with what you need", style = MaterialTheme.typography.headlineSmall, color = c.text)
        Spacer(Modifier.height(14.dp))
        Rule()
        tasks.chunked(if (wide) 2 else 1).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                row.forEach { t -> TaskRow(t, Modifier.weight(1f)) }
                if (row.size == 1 && wide) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TaskRow(t: Task, modifier: Modifier) {
    val c = Lens.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().hoverable(interaction).clickable(interactionSource = interaction, indication = null, onClick = t.run).padding(vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, color = if (hovered) c.primary else c.text)
                Spacer(Modifier.height(2.dp))
                Text(t.text, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            Text("→", style = MaterialTheme.typography.titleMedium, color = c.primary, modifier = Modifier.padding(start = 12.dp, end = if (hovered) 0.dp else 4.dp))
        }
        Rule()
    }
}

@Composable
private fun EmptyLibrary(controller: AppController) {
    val c = Lens.colors
    Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(vertical = 24.dp)) {
        Text("Nothing here yet. Files you open will be listed here so you can come back to them.", style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Not sure where to start? Open a sample: ", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            SampleLinks(controller)
        }
    }
}

@Composable
private fun SampleLinks(controller: AppController) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        TextLink("an interactive page") { controller.openSample("welcome") }
        TextLink("a phishing attachment") { controller.openSample("phishing") }
        TextLink("an email") { controller.openSample("email") }
    }
}

@Composable
private fun TableHeader() {
    val c = Lens.colors
    Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth()) {
        Rule()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            val st = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp)
            Text("NAME", style = st, color = c.textFaint, modifier = Modifier.weight(5f))
            Text("TYPE", style = st, color = c.textFaint, modifier = Modifier.weight(2f))
            Text("SIZE", style = st, color = c.textFaint, modifier = Modifier.weight(1f))
            Text("OPENED", style = st, color = c.textFaint, modifier = Modifier.weight(1.6f))
            Spacer(Modifier.width(76.dp))
        }
        Rule()
    }
}

private fun kindText(item: LibraryItem) = buildString {
    append(when (item.kind) {
        BundleKind.ZIP -> "ZIP"
        BundleKind.FOLDER -> "Folder"
        BundleKind.MHTML -> "MHTML"
        BundleKind.WEBARCHIVE -> "Web archive"
        BundleKind.EML -> "Email"
        BundleKind.PASTE -> "Pasted code"
        BundleKind.SVG -> "SVG"
        BundleKind.APP -> "App"
        BundleKind.HTML -> "HTML"
    })
    if (item.fileCount > 1) append(" · ${item.fileCount} files")
}

@Composable
fun LibraryRow(controller: AppController, item: LibraryItem, wide: Boolean, snippet: String? = null) {
    val c = Lens.colors
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().hoverable(interaction).clickable(interactionSource = interaction, indication = null) { controller.openLibraryItem(item) }
                .background(if (hovered) c.surface.copy(alpha = 0.7f) else Color.Transparent).padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.text)) { append(item.title) }
                            if (item.fileName != item.title && wide) withStyle(SpanStyle(color = c.textFaint)) { append("  " + item.fileName) }
                        },
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    when (item.verdict) {
                        Verdict.DANGEROUS -> { Spacer(Modifier.width(8.dp)); Pill("Phishing risk", c.danger, background = c.dangerSoft) }
                        Verdict.SUSPICIOUS -> { Spacer(Modifier.width(8.dp)); Pill("Suspicious", c.warning, background = c.warningSoft) }
                        else -> Unit
                    }
                    item.exportLabel?.let { Spacer(Modifier.width(8.dp)); Pill(it, c.info, background = c.infoSoft) }
                }
                if (!wide) {
                    Spacer(Modifier.height(2.dp))
                    Text("${kindText(item)} · ${relativeTime(item.lastOpenedAt)}", style = MaterialTheme.typography.bodySmall, color = c.textFaint)
                }
                if (snippet != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(snippet, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (wide) {
                Text(kindText(item), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.weight(2f), maxLines = 1)
                Text(formatBytes(item.sizeBytes), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.weight(1f), maxLines = 1)
                Text(relativeTime(item.lastOpenedAt), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.weight(1.6f), maxLines = 1)
            }
            IconBtn(
                if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (item.favorite) "Remove star" else "Star",
                { controller.toggleFavorite(item) }, tint = if (item.favorite) c.accent else c.textFaint, size = 34.dp,
            )
            Box {
                IconBtn(Icons.Rounded.MoreHoriz, "More actions", { menu = true }, size = 34.dp)
                DropdownMenu(menu, { menu = false }, containerColor = c.surface) {
                    DropdownMenuItem({ Text("Open in Safe mode") }, { menu = false; controller.openLibraryItem(item, SandboxMode.SAFE) }, leadingIcon = { Icon(Icons.Rounded.Shield, null) })
                    DropdownMenuItem({ Text("Rename") }, { menu = false; controller.renameItem(item) }, leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) })
                    if (item.sourcePath != null && controller.platform.kind == PlatformKind.DESKTOP) {
                        DropdownMenuItem({ Text(if (controller.platform.isMac) "Show in Finder" else "Show in folder") }, { menu = false; controller.platform.reveal(item.sourcePath!!) }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) })
                    }
                    DropdownMenuItem({ Text("Remove from list", color = c.danger) }, { menu = false; controller.deleteItem(item) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.danger) })
                }
            }
        }
        Rule()
    }
}
