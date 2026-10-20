package app.htmllens.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.htmllens.AppController
import app.htmllens.Dialog
import app.htmllens.Screen
import app.htmllens.core.serve.SandboxMode
import app.htmllens.desktop.engine.LocalServer
import app.htmllens.ui.HtmlLensApp
import app.htmllens.ui.viewer.updateModeFor
import app.htmllens.viewer.ViewerCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.swing.SwingUtilities

/** Paths handed to the app by the OS (file associations, second instance, drops onto the browser). */
object OpenRequests {
    val channel = Channel<List<String>>(Channel.UNLIMITED)
    fun send(paths: List<String>) { if (paths.isNotEmpty()) channel.trySend(paths) }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    // Render popups, menus and dialogs as separate windows so they appear above the native Chromium view.
    System.setProperty("compose.layers.type", "WINDOW")
    System.setProperty("apple.awt.application.name", "HTML Lens")
    System.setProperty("apple.laf.useScreenMenuBar", "true")

    val fileArgs = args.filter { !it.startsWith("-") }
    if (!SingleInstance.acquire(fileArgs) { paths -> OpenRequests.send(paths) }) return
    OpenRequests.send(fileArgs)

    if (Desktop.isDesktopSupported()) {
        runCatching {
            Desktop.getDesktop().setOpenFileHandler { e -> OpenRequests.send(e.files.map { it.absolutePath }) }
        }
    }

    LocalServer.start(AppDirs.data)

    application {
        val state = rememberWindowState(size = DpSize(1380.dp, 900.dp), position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center))
        Window(
            onCloseRequest = {
                exitApplication()
                javafx.application.Platform.exit()
            },
            title = "HTML Lens",
            state = state,
            icon = painterResource("icon.png"),
        ) {
            window.minimumSize = java.awt.Dimension(720, 520)
            val scope = rememberCoroutineScope()
            val platform = remember { DesktopPlatform { window } }
            val controller = remember { AppController(platform, scope) }
            LaunchedEffect(Unit) {
                controller.init()
                OpenRequests.channel.receiveAsFlow().collect { paths ->
                    controller.openPaths(paths)
                    SwingUtilities.invokeLater { window.toFront(); window.requestFocus() }
                }
            }
            DisposableEffect(Unit) { onDispose { } }
            AppMenu(controller)
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().dragAndDropTarget(
                    shouldStartDragAndDrop = { true },
                    target = remember {
                        object : DragAndDropTarget {
                            override fun onEntered(event: DragAndDropEvent) { controller.dragActive = true }
                            override fun onExited(event: DragAndDropEvent) { controller.dragActive = false }
                            override fun onEnded(event: DragAndDropEvent) { controller.dragActive = false }
                            override fun onDrop(event: DragAndDropEvent): Boolean {
                                controller.dragActive = false
                                val t = event.awtTransferable
                                if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                                @Suppress("UNCHECKED_CAST")
                                val files = t.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                                controller.openPaths(files.map { it.absolutePath })
                                return true
                            }
                        }
                    },
                ),
            ) {
                HtmlLensApp(controller)
            }
        }
    }
}

@Composable
private fun FrameWindowScope.AppMenu(controller: AppController) {
    val tab = controller.activeTab.takeIf { controller.screen == Screen.Viewer }
    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item("Open File…", shortcut = KeyShortcut(Key.O, meta = controller.platform.isMac, ctrl = !controller.platform.isMac), onClick = { controller.pickAndOpen() })
            Item("Open Folder…", shortcut = KeyShortcut(Key.O, meta = controller.platform.isMac, ctrl = !controller.platform.isMac, shift = true), onClick = { controller.pickFolderAndOpen() })
            Item("Paste HTML…", onClick = { controller.dialog = Dialog.Paste })
            Item("New in Editor", shortcut = KeyShortcut(Key.N, meta = controller.platform.isMac, ctrl = !controller.platform.isMac), onClick = { controller.newPlayground() })
            Separator()
            Item("Export as PDF…", enabled = tab != null, onClick = { tab?.let { controller.exportPdf(it) } })
            Item("Save as Single HTML File…", enabled = tab != null, onClick = { tab?.let { controller.exportPacked(it) } })
            Item("Close Tab", enabled = tab != null, shortcut = KeyShortcut(Key.W, meta = controller.platform.isMac, ctrl = !controller.platform.isMac), onClick = { tab?.let { controller.closeTab(it) } })
        }
        Menu("View", mnemonic = 'V') {
            Item("Safe Mode", enabled = tab != null, shortcut = KeyShortcut(Key.One, meta = controller.platform.isMac, ctrl = !controller.platform.isMac), onClick = { tab?.let { controller.updateModeFor(it, SandboxMode.SAFE) } })
            Item("Offline Mode", enabled = tab != null, shortcut = KeyShortcut(Key.Two, meta = controller.platform.isMac, ctrl = !controller.platform.isMac), onClick = { tab?.let { controller.updateModeFor(it, SandboxMode.OFFLINE) } })
            Item("Full Mode", enabled = tab != null, shortcut = KeyShortcut(Key.Three, meta = controller.platform.isMac, ctrl = !controller.platform.isMac), onClick = { tab?.let { controller.updateModeFor(it, SandboxMode.FULL) } })
            Separator()
            Item("Reload", enabled = tab != null, shortcut = KeyShortcut(Key.R, meta = controller.platform.isMac, ctrl = !controller.platform.isMac), onClick = { tab?.session?.reload() })
            Item("Find in Page", enabled = tab != null, shortcut = KeyShortcut(Key.F, meta = controller.platform.isMac, ctrl = !controller.platform.isMac), onClick = { tab?.findOpen = true })
            Item("View Source", enabled = tab != null, shortcut = KeyShortcut(Key.U, meta = controller.platform.isMac, ctrl = !controller.platform.isMac), onClick = { tab?.let { it.showSource = !it.showSource } })
            Item("Console", enabled = tab != null, onClick = { tab?.let { it.bottom = if (it.bottom == null) app.htmllens.BottomPanel.CONSOLE else null } })
            Item("Open in Browser", enabled = tab != null, onClick = { tab?.session?.send(ViewerCommand.DevTools) })
        }
        Menu("Go", mnemonic = 'G') {
            Item("Home", onClick = { controller.navigate(Screen.Home) })
            Item("Apps", onClick = { controller.navigate(Screen.Apps) })
            Item("Editor", onClick = { controller.navigate(Screen.Playground) })
            Item("Tools", onClick = { controller.navigate(Screen.Tools) })
            Item("Settings", shortcut = KeyShortcut(Key.Comma, meta = controller.platform.isMac, ctrl = !controller.platform.isMac), onClick = { controller.navigate(Screen.Settings) })
        }
        Menu("Help", mnemonic = 'H') {
            Item("Keyboard Shortcuts", onClick = { controller.dialog = Dialog.Shortcuts })
            Item("Website", onClick = { controller.platform.openUrl(controller.platform.siteUrl) })
            Item("Try the Phishing Demo", onClick = { controller.openSample("phishing") })
        }
    }
}

private typealias Composable = androidx.compose.runtime.Composable
