package app.htmllens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.htmllens.AppController
import app.htmllens.Dialog
import app.htmllens.core.model.AppVersion
import app.htmllens.core.model.HtmlApp
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.tools.LineDiff
import app.htmllens.core.util.formatBytes
import app.htmllens.relativeTime
import app.htmllens.ui.components.Btn
import app.htmllens.ui.components.BtnStyle
import app.htmllens.ui.components.Label
import app.htmllens.ui.components.Segmented
import app.htmllens.ui.components.TextInput
import app.htmllens.ui.theme.AppTileColors
import app.htmllens.ui.theme.Lens
import kotlinx.coroutines.launch

@Composable
fun Dialogs(controller: AppController) {
    val d = controller.dialog ?: return
    val close = { controller.dialog = null }
    when (d) {
        is Dialog.Confirm -> SimpleDialog(d.title, close, confirm = {
            Btn(d.confirm, { close(); d.onConfirm() }, style = if (d.danger) BtnStyle.DANGER else BtnStyle.PRIMARY)
        }) { Text(d.text, style = MaterialTheme.typography.bodyMedium, color = Lens.colors.textMuted) }

        is Dialog.Message -> SimpleDialog(d.title, close, confirm = { Btn("OK", close, style = BtnStyle.PRIMARY) }, dismissLabel = null) {
            Text(d.text, style = MaterialTheme.typography.bodyMedium, color = Lens.colors.textMuted)
        }

        is Dialog.Input -> {
            var value by remember(d) { mutableStateOf(d.initial) }
            SimpleDialog(d.title, close, confirm = { Btn(d.confirm, { close(); d.onSubmit(value) }, style = BtnStyle.PRIMARY) }) {
                Label(d.label)
                Spacer(Modifier.height(6.dp))
                TextInput(value, { value = it }, Modifier.fillMaxWidth(), onSubmit = { close(); d.onSubmit(value) })
            }
        }

        is Dialog.ExternalLink -> SimpleDialog("Open external link?", close, confirm = {
            Btn("Open in browser", { close(); controller.platform.openUrl(d.url) }, icon = Icons.Rounded.OpenInNew, style = BtnStyle.PRIMARY)
        }, extra = { Btn("Copy link", { close(); controller.copy(d.url, "Link copied") }) }) {
            Text("This page wants to open a website outside the sandbox. Make sure you trust it.", style = MaterialTheme.typography.bodyMedium, color = Lens.colors.textMuted)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Lens.colors.surface2).padding(10.dp)) {
                Text(d.url, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono), color = Lens.colors.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }

        Dialog.Paste -> PasteDialog(controller, close)
        is Dialog.SaveApp -> SaveAppDialog(controller, d, close)
        is Dialog.Versions -> controller.apps.firstOrNull { it.id == d.appId }?.let { VersionsDialog(controller, it, close) } ?: close()
        Dialog.Shortcuts -> SimpleDialog("Keyboard shortcuts", close, confirm = { Btn("Done", close, style = BtnStyle.PRIMARY) }, dismissLabel = null) {
            val mod = if (controller.platform.isMac) "⌘" else "Ctrl"
            val rows = listOf(
                "$mod V" to "Open copied HTML (Home)", "$mod O" to "Open file", "$mod ⇧ O" to "Open folder", "$mod N" to "New in editor", "$mod W" to "Close tab",
                "$mod ," to "Settings", "$mod F" to "Find in page (viewer)", "$mod S" to "Save app (editor)", "$mod ↵" to "Run (editor)",
                "$mod E" to "Expand Emmet abbreviation (editor)",
            )
            Column {
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(v, style = MaterialTheme.typography.bodyMedium, color = Lens.colors.text, modifier = Modifier.weight(1f))
                        Text(k, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Lens.mono), color = Lens.colors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit,
    dismissLabel: String? = "Cancel",
    extra: (@Composable () -> Unit)? = null,
    wide: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = Lens.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = if (wide) Modifier.widthIn(min = 560.dp, max = 760.dp) else Modifier.widthIn(max = 520.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = c.text) },
        text = { Column { content() } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dismissLabel != null) Btn(dismissLabel, onDismiss, style = BtnStyle.GHOST)
                extra?.invoke()
                confirm()
            }
        },
    )
}

@Composable
private fun PasteDialog(controller: AppController, close: () -> Unit) {
    var text by remember { mutableStateOf(controller.pendingPaste.also { controller.pendingPaste = "" }) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (text.isNotEmpty()) return@LaunchedEffect
        controller.platform.readClipboard()?.takeIf { controller.looksLikeHtml(it) }?.let { text = it }
    }
    SimpleDialog("Paste HTML", close, wide = true, confirm = {
        Btn("Open", { close(); controller.openPasted(text) }, style = BtnStyle.PRIMARY, enabled = text.isNotBlank())
    }, extra = {
        Btn("Save as app", { close(); controller.requestSaveApp(text, controller.suggestName(text), null) }, enabled = text.isNotBlank())
    }) {
        Text(
            "Paste a page or snippet — for example HTML generated by ChatGPT, Claude or Gemini. It renders privately on this device.",
            style = MaterialTheme.typography.bodyMedium, color = Lens.colors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        TextInput(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 360.dp), placeholder = "<!doctype html>…", singleLine = false, mono = true, minHeight = 240.dp)
        Spacer(Modifier.height(8.dp))
        Btn("Paste from clipboard", { scope.launch { controller.platform.readClipboard()?.let { text = it } } }, icon = Icons.Rounded.ContentPaste, compact = true, style = BtnStyle.GHOST)
    }
}

fun AppController.suggestName(html: String) = app.htmllens.core.analysis.PageAnalyzer.title(html) ?: "My app"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveAppDialog(controller: AppController, d: Dialog.SaveApp, close: () -> Unit) {
    val c = Lens.colors
    var name by remember { mutableStateOf(d.suggestedName) }
    var description by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(controller.suggestIcon(d.suggestedName, d.html)) }
    var color by remember { mutableIntStateOf((d.suggestedName.hashCode() and 0x7fffffff) % AppTileColors.size) }
    var mode by remember { mutableStateOf(if (d.html.contains("https://") || d.html.contains("http://")) SandboxMode.FULL else SandboxMode.OFFLINE) }
    SimpleDialog("Save as HTML app", close, wide = true, confirm = {
        Btn("Save app", {
            close()
            val app = controller.saveNewApp(name, icon, color, description, d.html, mode)
            if (controller.playground.editor.text == d.html) { controller.playground.appId = app.id; controller.playground.name = app.name; controller.playground.dirty = false }
        }, style = BtnStyle.PRIMARY, enabled = name.isNotBlank())
    }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(AppTileColors[color]), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.byKey(icon), null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Label("Name")
                    Spacer(Modifier.height(4.dp))
                    TextInput(name, { name = it }, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(14.dp))
            Label("Description (optional)")
            Spacer(Modifier.height(4.dp))
            TextInput(description, { description = it }, Modifier.fillMaxWidth(), placeholder = "What does it do?")
            Spacer(Modifier.height(14.dp))
            Label("Icon")
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIcons.all.forEach { (key, vec) ->
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (key == icon) c.primarySoft else c.surface2)
                            .border(1.dp, if (key == icon) c.primary else c.border, RoundedCornerShape(10.dp)).clickable { icon = key },
                        contentAlignment = Alignment.Center,
                    ) { Icon(vec, key, tint = if (key == icon) c.primary else c.textMuted, modifier = Modifier.size(19.dp)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Label("Color")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTileColors.forEachIndexed { i, col ->
                    Box(Modifier.size(26.dp).clip(CircleShape).background(col).border(if (i == color) 3.dp else 0.dp, c.text.copy(alpha = 0.6f), CircleShape).clickable { color = i })
                }
            }
            Spacer(Modifier.height(14.dp))
            Label("Network")
            Spacer(Modifier.height(6.dp))
            Segmented(
                listOf("Offline" to null, "Full (internet)" to null),
                if (mode == SandboxMode.FULL) 1 else 0,
                { mode = if (it == 1) SandboxMode.FULL else SandboxMode.OFFLINE },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (mode == SandboxMode.FULL) "Choose Full when the app loads libraries or data from the internet (CDNs, APIs)." else "Offline apps can never send data anywhere.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted,
            )
        }
    }
}

@Composable
private fun VersionsDialog(controller: AppController, app: HtmlApp, close: () -> Unit) {
    val c = Lens.colors
    val scope = rememberCoroutineScope()
    var diffWith by remember { mutableStateOf<AppVersion?>(null) }
    var diff by remember { mutableStateOf<List<LineDiff.Line>?>(null) }
    LaunchedEffect(diffWith) {
        val v = diffWith ?: return@LaunchedEffect
        val old = controller.appHtml(app, v.id) ?: ""
        val cur = controller.appHtml(app) ?: ""
        diff = LineDiff.diff(old, cur)
    }
    SimpleDialog("Versions of “${app.name}”", close, wide = true, confirm = { Btn("Done", close, style = BtnStyle.PRIMARY) }, dismissLabel = null) {
        if (diff != null && diffWith != null) {
            val stats = LineDiff.stats(diff!!)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Changes since ${relativeTime(diffWith!!.createdAt)}", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
                Text("+${stats.added}  −${stats.removed}", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Spacer(Modifier.width(10.dp))
                Btn("Back", { diffWith = null; diff = null }, compact = true)
            }
            Spacer(Modifier.height(8.dp))
            DiffView(diff!!, Modifier.fillMaxWidth().heightIn(max = 420.dp))
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(app.versions.reversed()) { v ->
                    val current = v.id == app.currentVersionId
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(relativeTime(v.createdAt) + if (current) "  ·  current" else "", style = MaterialTheme.typography.titleSmall, color = if (current) c.primary else c.text)
                            Text("${v.note} · ${formatBytes(v.size.toLong())}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                        if (!current) {
                            Btn("Compare", { diffWith = v }, icon = Icons.Rounded.Difference, compact = true, style = BtnStyle.GHOST)
                            Spacer(Modifier.width(6.dp))
                            Btn("Restore", { scope.launch { controller.restoreVersion(app, v) } }, icon = Icons.Rounded.Restore, compact = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiffView(lines: List<LineDiff.Line>, modifier: Modifier = Modifier) {
    val c = Lens.colors
    LazyColumn(modifier.clip(RoundedCornerShape(10.dp)).background(c.code).border(1.dp, c.border, RoundedCornerShape(10.dp))) {
        items(lines) { l ->
            val (bg, fg, sign) = when (l.op) {
                LineDiff.Op.INSERT -> Triple(c.successSoft, c.success, "+")
                LineDiff.Op.DELETE -> Triple(c.dangerSoft, c.danger, "−")
                LineDiff.Op.EQUAL -> Triple(Color.Transparent, c.textMuted, " ")
            }
            Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 1.dp)) {
                Text((l.oldNo?.toString() ?: "").padStart(4), style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono, fontSize = 11.sp), color = c.textFaint)
                Text((l.newNo?.toString() ?: "").padStart(5), style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono, fontSize = 11.sp), color = c.textFaint)
                Text(" $sign ", style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono, fontWeight = FontWeight.Bold), color = fg)
                Text(l.text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Lens.mono, fontSize = 12.sp), color = if (l.op == LineDiff.Op.EQUAL) c.text else fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
