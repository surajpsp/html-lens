package app.htmllens.ui.apps

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.htmllens.AppController
import app.htmllens.Dialog
import app.htmllens.core.model.HtmlApp
import app.htmllens.core.serve.SandboxMode
import app.htmllens.relativeTime
import app.htmllens.ui.AppIcons
import app.htmllens.ui.components.Btn
import app.htmllens.ui.components.BtnStyle
import app.htmllens.ui.components.Card
import app.htmllens.ui.components.EmptyState
import app.htmllens.ui.components.IconBtn
import app.htmllens.ui.components.Pill
import app.htmllens.ui.theme.AppTileColors
import app.htmllens.ui.theme.Lens

@Composable
fun AppsScreen(controller: AppController, wide: Boolean) {
    val c = Lens.colors
    val apps = controller.apps.sortedWith(compareByDescending<HtmlApp> { it.pinned }.thenByDescending { maxOf(it.updatedAt, it.lastRunAt) })
    val hPad = if (wide) 40.dp else 18.dp
    LazyVerticalGrid(
        GridCells.Adaptive(if (wide) 260.dp else 160.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = if (wide) 52.dp else 24.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your apps", style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, color = c.text)
                        Spacer(Modifier.height(8.dp))
                        Text("Little tools, games and pages you've kept — including ones written by ChatGPT or Claude. They run offline, with their own storage and version history.", style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
                    }
                    if (wide) {
                        Btn("Paste & save", { controller.dialog = Dialog.Paste }, icon = Icons.Rounded.ContentPaste)
                        Spacer(Modifier.width(8.dp))
                        Btn("New app", { controller.newPlayground() }, icon = Icons.Rounded.Add, style = BtnStyle.PRIMARY)
                    }
                }
                if (!wide) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("New app", { controller.newPlayground() }, icon = Icons.Rounded.Add, style = BtnStyle.PRIMARY)
                        Btn("Paste & save", { controller.dialog = Dialog.Paste }, icon = Icons.Rounded.ContentPaste)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (apps.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    Icons.Rounded.Widgets, "No apps yet",
                    "Asked ChatGPT, Claude or Gemini for a calculator, a game or a dashboard? Paste the code and keep it here. It gets its own storage, a version history and a link you can share.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Paste HTML", { controller.dialog = Dialog.Paste }, icon = Icons.Rounded.ContentPaste, style = BtnStyle.PRIMARY)
                        Btn("Start from a template", { controller.newPlayground(app.htmllens.Samples.templates.first { it.first == "To-do app" }.second) })
                    }
                }
            }
        }
        items(apps, key = { it.id }) { app -> AppCard(controller, app) }
    }
}

@Composable
private fun AppCard(controller: AppController, app: HtmlApp) {
    val c = Lens.colors
    var menu by remember { mutableStateOf(false) }
    val color = AppTileColors[app.color.coerceIn(0, AppTileColors.lastIndex)]
    Card(Modifier.fillMaxWidth(), onClick = { controller.runApp(app) }, padding = PaddingValues(0.dp)) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(88.dp).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.byKey(app.icon), null, tint = Color.White, modifier = Modifier.size(40.dp))
                if (app.pinned) Icon(Icons.Rounded.PushPin, "Pinned", tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(16.dp))
                Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Box {
                        IconBtn(Icons.Rounded.MoreVert, "More", { menu = true }, tint = Color.White)
                        DropdownMenu(menu, { menu = false }, containerColor = c.surface) {
                            @Composable
                            fun item(t: String, i: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, a: () -> Unit) =
                                DropdownMenuItem({ Text(t, color = if (danger) c.danger else c.text) }, { menu = false; a() }, leadingIcon = { Icon(i, null, tint = if (danger) c.danger else c.textMuted) })
                            item("Run", Icons.Rounded.PlayArrow) { controller.runApp(app) }
                            item("Edit", Icons.Rounded.Edit) { controller.editApp(app) }
                            item("Versions (${app.versions.size})", Icons.Rounded.History) { controller.dialog = Dialog.Versions(app.id) }
                            item("Rename", Icons.Rounded.DriveFileRenameOutline) {
                                controller.dialog = Dialog.Input("Rename app", "Name", app.name, "Rename") { n -> if (n.isNotBlank()) controller.updateApp(app.id) { it.copy(name = n.trim()) } }
                            }
                            item(if (app.pinned) "Unpin" else "Pin to top", Icons.Rounded.PushPin) { controller.updateApp(app.id) { it.copy(pinned = !it.pinned) } }
                            item("Duplicate", Icons.Rounded.ContentCopy) { controller.duplicateApp(app) }
                            item("Copy share link", Icons.Rounded.Link) { controller.shareApp(app) }
                            item("Export .html", Icons.Rounded.FileDownload) { controller.exportApp(app) }
                            HorizontalDivider(color = c.border)
                            item("Delete", Icons.Rounded.Delete, danger = true) { controller.deleteApp(app) }
                        }
                    }
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(app.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(app.description, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Updated ${relativeTime(app.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = c.textFaint, modifier = Modifier.weight(1f))
                    if (app.mode == SandboxMode.OFFLINE) Pill("Offline", c.info) else Pill("Online", c.warning)
                }
            }
        }
    }
}
