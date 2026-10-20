package app.htmllens.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.htmllens.AppController
import app.htmllens.Screen
import app.htmllens.Toast
import app.htmllens.core.model.ThemeMode
import app.htmllens.ui.components.IconBtn
import app.htmllens.ui.components.ProgressOverlay
import app.htmllens.ui.home.HomeScreen
import app.htmllens.ui.apps.AppsScreen
import app.htmllens.ui.editor.PlaygroundScreen
import app.htmllens.ui.settings.SettingsScreen
import app.htmllens.ui.theme.HtmlLensTheme
import app.htmllens.ui.theme.Lens
import app.htmllens.ui.tools.ToolScreen
import app.htmllens.ui.tools.ToolsScreen
import app.htmllens.ui.viewer.ViewerScreen
import kotlinx.coroutines.delay

@Composable
fun HtmlLensApp(controller: AppController) {
    val dark = when (controller.settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    HtmlLensTheme(dark) {
        val c = Lens.colors
        var toast by remember { mutableStateOf<Toast?>(null) }
        LaunchedEffect(Unit) {
            controller.toasts.collect { t ->
                toast = t
            }
        }
        LaunchedEffect(toast) {
            if (toast != null) {
                delay(if (toast?.action != null) 6000 else 3500)
                toast = null
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().background(c.bg).onKeyEvent { e ->
                // Bubbles up only when no text field handled it, so pasting into search still works.
                val mod = e.isCtrlPressed || e.isMetaPressed
                if (e.type == KeyEventType.KeyDown && mod && e.key == Key.V && controller.acceptsGlobalPaste &&
                    controller.platform.kind == app.htmllens.platform.PlatformKind.DESKTOP
                ) { controller.pasteFromClipboard(); true } else false
            }.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val mod = e.isCtrlPressed || e.isMetaPressed
                if (!mod) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.O -> { if (e.isShiftPressed) controller.pickFolderAndOpen() else controller.pickAndOpen(); true }
                    Key.N -> { controller.newPlayground(); true }
                    Key.Comma -> { controller.navigate(Screen.Settings); true }
                    Key.W -> { controller.activeTab?.let { controller.closeTab(it) }; controller.activeTab != null }
                    else -> false
                }
            },
        ) {
            val wide = maxWidth >= 760.dp
            Column(Modifier.fillMaxSize()) {
                AppHeader(controller, dark, wide)
                Box(Modifier.weight(1f).fillMaxWidth()) { ScreenContent(controller, wide) }
                if (!wide) BottomNav(controller)
            }

            controller.busy?.let { message ->
                Popup(alignment = Alignment.Center, properties = PopupProperties(focusable = false)) {
                    Box(Modifier.size(maxWidth, maxHeight)) { ProgressOverlay(message) }
                }
            }
            if (controller.dragActive) DropOverlay(maxWidth, maxHeight)
            toast?.let { t -> ToastPopup(t) { toast = null } }
            Dialogs(controller)
        }
    }
}

@Composable
private fun ScreenContent(controller: AppController, wide: Boolean) {
    when (val s = controller.screen) {
        Screen.Home -> HomeScreen(controller, wide)
        Screen.Viewer -> controller.activeTab?.let { ViewerScreen(controller, it, wide) } ?: HomeScreen(controller, wide)
        Screen.Apps -> AppsScreen(controller, wide)
        Screen.Playground -> PlaygroundScreen(controller, wide)
        Screen.Tools -> ToolsScreen(controller, wide)
        is Screen.Tool -> ToolScreen(controller, s.id, wide)
        Screen.Settings -> SettingsScreen(controller, wide)
    }
}

private data class NavItem(val screen: Screen, val label: String)

private fun navItems(controller: AppController) = buildList {
    add(NavItem(Screen.Home, "Library"))
    if (controller.tabs.isNotEmpty()) add(NavItem(Screen.Viewer, "Open (${controller.tabs.size})"))
    add(NavItem(Screen.Apps, "Apps"))
    add(NavItem(Screen.Playground, "Editor"))
    add(NavItem(Screen.Tools, "Tools"))
}

private fun isSelected(controller: AppController, screen: Screen) =
    controller.screen == screen || (screen == Screen.Tools && controller.screen is Screen.Tool)

private fun go(controller: AppController, screen: Screen) {
    if (screen == Screen.Viewer && controller.activeTab == null) controller.activeTab = controller.tabs.lastOrNull()
    controller.navigate(screen)
}

@Composable
private fun AppHeader(controller: AppController, dark: Boolean, wide: Boolean) {
    val c = Lens.colors
    Row(
        Modifier.fillMaxWidth().background(c.bg).padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).clickable { controller.navigate(Screen.Home) }.padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoMark(Modifier.size(26.dp), dark)
            Spacer(Modifier.width(10.dp))
            Text("HTML Lens", style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.text)
        }
        if (wide) {
            Spacer(Modifier.width(28.dp))
            for (item in navItems(controller)) {
                HeaderLink(item.label, isSelected(controller, item.screen)) { go(controller, item.screen) }
                Spacer(Modifier.width(4.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        if (wide) {
            app.htmllens.ui.components.SearchField(
                controller.libraryQuery,
                { controller.libraryQuery = it; if (controller.screen != Screen.Home) controller.navigate(Screen.Home) },
                "Search your files",
                Modifier.width(260.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        IconBtn(
            if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            if (dark) "Switch to light" else "Switch to dark",
            onClick = { controller.updateSettings { it.copy(theme = if (dark) ThemeMode.LIGHT else ThemeMode.DARK) } },
        )
        IconBtn(Icons.Rounded.Settings, "Settings", { controller.navigate(Screen.Settings) }, active = controller.screen == Screen.Settings)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
}

@Composable
private fun HeaderLink(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Lens.colors
    Column(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal),
            color = if (selected) c.text else c.textMuted,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(18.dp).height(2.dp).background(if (selected) c.primary else Color.Transparent))
    }
}

@Composable
private fun BottomNav(controller: AppController) {
    val c = Lens.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    Row(Modifier.fillMaxWidth().background(c.surface)) {
        for (item in navItems(controller).map { if (it.screen == Screen.Viewer) it.copy(label = "Open") else it }) {
            val selected = isSelected(controller, item.screen)
            Column(
                Modifier.weight(1f).clickable { go(controller, item.screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) c.primary else Color.Transparent))
                Text(
                    item.label, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal),
                    color = if (selected) c.text else c.textMuted,
                )
            }
        }
    }
}

@Composable
fun LogoMark(modifier: Modifier = Modifier, dark: Boolean = Lens.colors.isDark) {
    androidx.compose.foundation.Image(if (dark) LensLogoDark else LensLogo, "HTML Lens", modifier)
}

@Composable
private fun ToastPopup(toast: Toast, onDismiss: () -> Unit) {
    val c = Lens.colors
    Popup(alignment = Alignment.BottomCenter, offset = IntOffset(0, -28), properties = PopupProperties(focusable = false)) {
        AnimatedVisibility(true, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            Row(
                Modifier.widthIn(max = 560.dp).shadow(10.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                    .background(if (c.isDark) c.surface3 else Color(0xFF1E1A15)).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (toast.error) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle, null,
                    tint = if (toast.error) Color(0xFFF08A74) else Color(0xFF9CC99A), modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(toast.text, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (toast.action != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        toast.action, color = Color(0xFFF2A585), style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { toast.onAction?.invoke(); onDismiss() }.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                Icon(
                    Icons.Rounded.Close, "Dismiss", tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 4.dp).size(28.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun DropOverlay(w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    val c = Lens.colors
    Popup(alignment = Alignment.Center, properties = PopupProperties(focusable = false)) {
        Box(Modifier.size(w, h).background(c.scrim).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).border(2.dp, c.primary, RoundedCornerShape(24.dp)).background(c.surface.copy(alpha = 0.92f)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.UploadFile, null, tint = c.primary, modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(12.dp))
                Text("Drop to open", style = MaterialTheme.typography.headlineSmall, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text("HTML, MHTML, ZIP, EML, Web Archive, SVG or a whole folder", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
        }
    }
}
