package app.htmllens.ui.editor

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import app.htmllens.AppController
import app.htmllens.Device
import app.htmllens.Dialog
import app.htmllens.PlaygroundLayout
import app.htmllens.Samples
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.tools.Formatter
import app.htmllens.core.util.formatBytes
import app.htmllens.ui.components.Btn
import app.htmllens.ui.components.BtnStyle
import app.htmllens.ui.components.IconBtn
import app.htmllens.ui.components.Segmented
import app.htmllens.ui.components.Toggle
import app.htmllens.ui.theme.Lens
import app.htmllens.ui.viewer.DevToolsPanel
import app.htmllens.ui.viewer.Splitter
import app.htmllens.ui.viewer.modeColors
import app.htmllens.ui.viewer.modeIcons
import app.htmllens.viewer.CodeEditor
import app.htmllens.viewer.EditorOptions
import app.htmllens.viewer.HtmlView
import kotlinx.coroutines.delay

@Composable
fun PlaygroundScreen(controller: AppController, wide: Boolean) {
    val c = Lens.colors
    val p = controller.playground
    val editor = p.editor
    val s = p.session
    val consoleTab = remember { app.htmllens.ViewerTab("pg", s, null, p.appId, null).also { it.bottom = app.htmllens.BottomPanel.CONSOLE } }

    LaunchedEffect(editor) {
        editor.onRun = { p.run() }
        editor.onSave = { controller.savePlayground() }
    }
    LaunchedEffect(editor.editVersion) {
        if (editor.editVersion == 0) return@LaunchedEffect
        p.dirty = true
        delay(1200)
        controller.savePlaygroundDraft()
    }
    LaunchedEffect(editor.editVersion, p.autoRun) {
        if (!p.autoRun || editor.editVersion == 0) return@LaunchedEffect
        delay(550)
        if (editor.text != p.lastRunText) p.run()
    }
    if (!wide && p.layout == PlaygroundLayout.SPLIT) p.layout = PlaygroundLayout.CODE

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // Toolbar
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                p.name, { p.name = it; p.dirty = true }, singleLine = true,
                textStyle = MaterialTheme.typography.titleSmall.copy(color = c.text), cursorBrush = SolidColor(c.primary),
                modifier = Modifier.widthIn(min = 80.dp, max = 260.dp).clip(RoundedCornerShape(8.dp)).background(c.surface2).padding(horizontal = 10.dp, vertical = 7.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (p.appId != null) Text(if (p.dirty) "Unsaved changes" else "Saved", style = MaterialTheme.typography.labelSmall, color = if (p.dirty) c.warning else c.success)
            Spacer(Modifier.weight(1f))
            if (wide) {
                Segmented(listOf("Code" to null, "Split" to null, "Preview" to null),
                    when (p.layout) { PlaygroundLayout.CODE -> 0; PlaygroundLayout.SPLIT -> 1; PlaygroundLayout.PREVIEW -> 2 },
                    { p.layout = listOf(PlaygroundLayout.CODE, PlaygroundLayout.SPLIT, PlaygroundLayout.PREVIEW)[it] }, compact = true)
            } else {
                Segmented(listOf("Code" to null, "Preview" to null), if (p.layout == PlaygroundLayout.PREVIEW) 1 else 0,
                    { p.layout = if (it == 1) PlaygroundLayout.PREVIEW else PlaygroundLayout.CODE }, compact = true)
            }
            Spacer(Modifier.width(8.dp))
            TemplatesMenu(controller)
            if (wide) {
                Btn("Format", { editor.setText(Formatter.beautify(editor.text), keepCursor = true) }, icon = Icons.Rounded.AutoFixHigh, compact = true, style = BtnStyle.GHOST)
            }
            Btn("Run", { p.run() }, icon = Icons.Rounded.PlayArrow, compact = true, style = BtnStyle.SOFT)
            Spacer(Modifier.width(6.dp))
            Btn(if (p.appId != null) "Save" else "Save as app", { controller.savePlayground() }, icon = Icons.Rounded.Save, compact = true, style = BtnStyle.PRIMARY)
            PlaygroundMore(controller)
        }
        HorizontalDivider(color = c.border)

        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (p.layout != PlaygroundLayout.PREVIEW) {
                Box(Modifier.weight(if (p.layout == PlaygroundLayout.SPLIT) p.split else 1f).fillMaxHeight()) {
                    CodeEditor(editor, EditorOptions(c.isDark, controller.settings.editorFontSize, readOnly = false, wrap = controller.settings.editorWrap, lang = "html"), Modifier.fillMaxSize())
                }
            }
            if (p.layout == PlaygroundLayout.SPLIT) Splitter { dx, total -> p.split = (p.split + dx / total).coerceIn(0.2f, 0.8f) }
            if (p.layout != PlaygroundLayout.CODE) {
                Column(Modifier.weight(if (p.layout == PlaygroundLayout.SPLIT) 1f - p.split else 1f).fillMaxHeight()) {
                    PreviewToolbar(controller)
                    Box(Modifier.weight(1f).fillMaxWidth().background(c.surface)) {
                        if (p.device == Device.RESPONSIVE) HtmlView(s, Modifier.fillMaxSize())
                        else BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                            val scale = minOf(maxWidth.value / p.device.width, maxHeight.value / p.device.height, 1f)
                            Box(Modifier.size((p.device.width * scale).dp, (p.device.height * scale).dp).clip(RoundedCornerShape(10.dp)).background(Color.White)) {
                                HtmlView(s, Modifier.fillMaxSize(), zoom = scale)
                            }
                        }
                    }
                    if (p.showConsole) {
                        HorizontalDivider(color = c.border)
                        Box(Modifier.fillMaxWidth().height(200.dp).background(c.surface)) { DevToolsPanel(controller, consoleTab) }
                    }
                }
            }
        }
        // Status line
        HorizontalDivider(color = c.border)
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ln ${editor.cursorLine}, Col ${editor.cursorCol}  ·  ${editor.lineCount} lines  ·  ${formatBytes(editor.text.length.toLong())}",
                style = MaterialTheme.typography.labelSmall, color = c.textFaint)
            Spacer(Modifier.weight(1f))
            Text("Auto-run", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            Spacer(Modifier.width(6.dp))
            Toggle(p.autoRun, { p.autoRun = it })
            Spacer(Modifier.width(12.dp))
            Text(if (controller.platform.isMac) "⌘↵ run · ⌘S save · ⌘E emmet" else "Ctrl+↵ run · Ctrl+S save · Ctrl+E emmet", style = MaterialTheme.typography.labelSmall, color = c.textFaint)
        }
    }
}

@Composable
private fun PreviewToolbar(controller: AppController) {
    val c = Lens.colors
    val p = controller.playground
    val s = p.session
    var deviceMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Segmented(
            listOf("Offline" to modeIcons[1], "Full" to modeIcons[2]),
            if (s.mode == SandboxMode.FULL) 1 else 0,
            { s.changeMode(if (it == 1) SandboxMode.FULL else SandboxMode.OFFLINE) },
            colors = listOf(modeColors[1], modeColors[2]), compact = true,
        )
        Spacer(Modifier.weight(1f))
        Box {
            Btn(if (p.device == Device.RESPONSIVE) "Responsive" else p.device.label, { deviceMenu = true }, icon = Icons.Rounded.Devices, compact = true, style = BtnStyle.GHOST, trailing = Icons.Rounded.ExpandMore)
            DropdownMenu(deviceMenu, { deviceMenu = false }, containerColor = c.surface) {
                Device.entries.filter { it != Device.MULTI && it != Device.EMAIL_DESKTOP && it != Device.EMAIL_MOBILE }.forEach { d ->
                    DropdownMenuItem({ Text(d.label + if (d.width > 0) "  ${d.width}×${d.height}" else "") }, { deviceMenu = false; p.device = d })
                }
            }
        }
        IconBtn(Icons.Rounded.Terminal, "Console", { p.showConsole = !p.showConsole }, active = p.showConsole,
            badge = if (s.errorCount > 0) s.errorCount.toString() else null)
        IconBtn(Icons.Rounded.OpenInFull, "Open in viewer", { controller.openPasted(p.editor.text, p.name) })
    }
    HorizontalDivider(color = c.border)
}

@Composable
private fun TemplatesMenu(controller: AppController) {
    var open by remember { mutableStateOf(false) }
    val c = Lens.colors
    Box {
        Btn("Templates", { open = true }, icon = Icons.Rounded.Description, compact = true, style = BtnStyle.GHOST, trailing = Icons.Rounded.ExpandMore)
        DropdownMenu(open, { open = false }, containerColor = c.surface) {
            Samples.templates.forEach { (name, html) ->
                DropdownMenuItem({ Text(name) }, {
                    open = false
                    val p = controller.playground
                    if (p.dirty && p.editor.text.isNotBlank()) {
                        controller.dialog = Dialog.Confirm("Replace current code?", "Your unsaved changes in the editor will be replaced by the “$name” template.", "Replace") { p.load(html, name, null) }
                    } else p.load(html, name, null)
                })
            }
        }
    }
}

@Composable
private fun PlaygroundMore(controller: AppController) {
    var open by remember { mutableStateOf(false) }
    val c = Lens.colors
    val p = controller.playground
    Box {
        IconBtn(Icons.Rounded.MoreVert, "More", { open = true })
        DropdownMenu(open, { open = false }, containerColor = c.surface) {
            DropdownMenuItem({ Text("New blank page") }, { open = false; controller.newPlayground() })
            DropdownMenuItem({ Text("Format code") }, { open = false; p.editor.setText(Formatter.beautify(p.editor.text), keepCursor = true) }, leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null) })
            DropdownMenuItem({ Text("Minify code") }, { open = false; p.editor.setText(Formatter.minify(p.editor.text)) }, leadingIcon = { Icon(Icons.Rounded.Compress, null) })
            HorizontalDivider(color = c.border)
            DropdownMenuItem({ Text("Copy share link") }, { open = false; controller.copyShareLink(p.editor.text, p.name) }, leadingIcon = { Icon(Icons.Rounded.Link, null) })
            DropdownMenuItem({ Text("Download .html") }, { open = false; controller.saveText(app.htmllens.core.util.Paths.sanitizeSegment(p.name) + ".html", "text/html", p.editor.text) }, leadingIcon = { Icon(Icons.Rounded.FileDownload, null) })
            if (p.appId != null) {
                DropdownMenuItem({ Text("Version history") }, { open = false; controller.dialog = Dialog.Versions(p.appId!!) }, leadingIcon = { Icon(Icons.Rounded.History, null) })
                DropdownMenuItem({ Text("Save as a new app") }, { open = false; controller.requestSaveApp(p.editor.text, p.name + " copy", null) }, leadingIcon = { Icon(Icons.Rounded.Save, null) })
            }
        }
    }
}
