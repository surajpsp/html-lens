package app.htmllens.ui.settings

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.htmllens.AppController
import app.htmllens.Dialog
import app.htmllens.core.model.ThemeMode
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.util.formatBytes
import app.htmllens.platform.EngineStatus
import app.htmllens.platform.PlatformKind
import app.htmllens.ui.LogoMark
import app.htmllens.ui.components.Btn
import app.htmllens.ui.components.BtnStyle
import app.htmllens.ui.components.Card
import app.htmllens.ui.components.Divider
import app.htmllens.ui.components.Label
import app.htmllens.ui.components.Segmented
import app.htmllens.ui.components.SettingRow
import app.htmllens.ui.components.Toggle
import app.htmllens.ui.theme.Lens
import app.htmllens.ui.viewer.modeColors
import app.htmllens.ui.viewer.modeIcons

const val APP_VERSION = "1.0.0"

@Composable
fun SettingsScreen(controller: AppController, wide: Boolean) {
    val c = Lens.colors
    val st = controller.settings
    val hPad = if (wide) 40.dp else 18.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = hPad, end = hPad, top = if (wide) 52.dp else 24.dp, bottom = 48.dp)) {
        item {
            Text("Settings", style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = c.text)
            Spacer(Modifier.height(28.dp))
        }
        item {
            Section("Appearance") {
                SettingRow("Theme", null) {
                    Segmented(listOf("System" to null, "Light" to null, "Dark" to null), st.theme.ordinal, { i -> controller.updateSettings { it.copy(theme = ThemeMode.entries[i]) } }, compact = true)
                }
            }
        }
        item {
            Section("Viewer") {
                val modes = SandboxMode.entries.mapIndexed { i, m -> m.label to modeIcons[i] }
                SettingRow("Default mode for files", "Used for files you open. Emails always start in Safe mode.") {
                    Segmented(modes, st.defaultFileMode.ordinal, { i -> controller.updateSettings { it.copy(defaultFileMode = SandboxMode.entries[i]) } }, colors = modeColors, compact = true)
                }
                Divider()
                SettingRow("Default mode for pasted code", "AI-generated pages often load libraries from the internet.") {
                    Segmented(modes, st.defaultPasteMode.ordinal, { i -> controller.updateSettings { it.copy(defaultPasteMode = SandboxMode.entries[i]) } }, colors = modeColors, compact = true)
                }
                Divider()
                SettingRow("Switch to Safe mode for dangerous files", "When the security scan finds phishing or malware patterns.") {
                    Toggle(st.autoSafeOnThreat, { v -> controller.updateSettings { it.copy(autoSafeOnThreat = v) } })
                }
            }
        }
        item {
            Section("Editor") {
                SettingRow("Font size", null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Btn("−", { controller.updateSettings { it.copy(editorFontSize = (it.editorFontSize - 1).coerceAtLeast(10)) } }, compact = true)
                        Text("${st.editorFontSize}px", style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.widthIn(min = 52.dp).padding(horizontal = 8.dp))
                        Btn("+", { controller.updateSettings { it.copy(editorFontSize = (it.editorFontSize + 1).coerceAtMost(24)) } }, compact = true)
                    }
                }
                Divider()
                SettingRow("Wrap long lines", null) { Toggle(st.editorWrap, { v -> controller.updateSettings { it.copy(editorWrap = v) } }) }
            }
        }
        item {
            Section("Library & storage") {
                SettingRow(
                    "Keep private copies",
                    if (controller.platform.kind == PlatformKind.WEB) "Files are stored in this browser so you can reopen them later. They never leave your device."
                    else "Store a copy so files still open if they are moved. Large folders are re-read from disk instead.",
                ) { Toggle(st.keepCopies, { v -> controller.updateSettings { it.copy(keepCopies = v) } }) }
                Divider()
                SettingRow("Storage used", "${formatBytes(controller.storageUsed)} · ${controller.library.size} files · ${controller.apps.size} apps") {
                    Btn("Clear library", { controller.clearLibrary() }, style = BtnStyle.DANGER, compact = true)
                }
            }
        }
        if (controller.platform.kind == PlatformKind.DESKTOP) item {
            val status by controller.platform.engineStatus.collectAsState()
            Section("Rendering engine") {
                SettingRow("Chromium (CEF)", when (val s = status) {
                    EngineStatus.Ready -> "Installed and running locally."
                    is EngineStatus.Preparing -> s.step
                    is EngineStatus.Failed -> s.message
                }) {
                    if (status is EngineStatus.Failed) Btn("Retry", { controller.platform.retryEngine() }, compact = true)
                }
                Divider()
                SettingRow(
                    "Make HTML Lens your default HTML viewer",
                    if (controller.platform.isMac) "In Finder, select an .html file → Get Info → Open with → HTML Lens → Change All."
                    else "Right-click an .html file → Open with → Choose another app → HTML Lens → Always.",
                ) {}
            }
        }
        item {
            Section("About") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    LogoMark(Modifier.size(44.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("HTML Lens $APP_VERSION", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text("Private by design: no accounts, no tracking, no uploads. Everything runs on your device.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    Btn("Website", { controller.platform.openUrl(controller.platform.siteUrl) }, icon = Icons.Rounded.Language, compact = true)
                    Spacer(Modifier.width(8.dp))
                    Btn("Keyboard shortcuts", { controller.dialog = Dialog.Shortcuts }, icon = Icons.Rounded.Keyboard, compact = true)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Label(title)
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth().widthIn(max = 860.dp), padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
        Column { content() }
    }
    Spacer(Modifier.height(22.dp))
}

