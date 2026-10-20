package app.htmllens.desktop.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import app.htmllens.core.util.Paths
import app.htmllens.core.util.randomId
import app.htmllens.desktop.AppDirs
import app.htmllens.desktop.OpenRequests
import app.htmllens.viewer.CodeEditorState
import app.htmllens.viewer.EditorCommand
import app.htmllens.viewer.EditorOptions
import app.htmllens.viewer.ViewSession
import app.htmllens.viewer.ViewerCommand
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.print.PrinterJob
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.TextInputDialog
import javafx.scene.input.TransferMode
import javafx.scene.paint.Color
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import javax.swing.SwingUtilities

/** JavaFX toolkit helpers. The toolkit starts (embedded in AWT) when the first JFXPanel is created. */
object Fx {
    @Volatile private var configured = false

    fun panel(): JFXPanel {
        val p = JFXPanel()
        if (!configured) {
            configured = true
            Platform.setImplicitExit(false)
        }
        return p
    }

    fun run(block: () -> Unit) {
        if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
    }

    fun configure(engine: WebEngine) {
        engine.userDataDirectory = File(AppDirs.data, "webdata").apply { mkdirs() }
        engine.isJavaScriptEnabled = true
        engine.setOnAlert { e -> Alert(Alert.AlertType.INFORMATION, e.data).apply { headerText = null; title = "Message from the page" }.showAndWait() }
        engine.setConfirmHandler { msg ->
            Alert(Alert.AlertType.CONFIRMATION, msg).apply { headerText = null; title = "Confirm" }.showAndWait().filter { it == ButtonType.OK }.isPresent
        }
        engine.setPromptHandler { pd ->
            TextInputDialog(pd.defaultValue).apply { headerText = pd.message; title = "Input" }.showAndWait().orElse(null)
        }
    }
}

@Composable
fun HtmlViewFx(session: ViewSession, modifier: Modifier, primary: Boolean, zoom: Float) {
    val view = remember(session, primary) { FxPageView(session, primary) }
    DisposableEffect(view) { onDispose { view.dispose() } }
    LaunchedEffect(view, session.loadToken, session.revision) { view.load() }
    LaunchedEffect(view, zoom) { view.setZoom(zoom) }
    if (primary) {
        LaunchedEffect(view) { session.commands.collect { view.command(it) } }
    }
    SwingPanel(factory = { view.panel }, modifier = modifier)
}

/** One WebKit view rendering a bundle served by [LocalServer]. */
class FxPageView(private val session: ViewSession, private val primary: Boolean) {
    private val token = randomId(32)
    val panel: JFXPanel = Fx.panel()
    private var webView: WebView? = null
    private var engine: WebEngine? = null
    private var pending: String? = null
    @Volatile private var lastUrl: String = "about:blank"
    private var zoom = 1.0
    private val channel = LocalServer.Channel(session) { msg ->
        if (primary) SwingUtilities.invokeLater { session.handleBridgeMessage(msg) }
    }

    init {
        LocalServer.register(token, channel)
        Fx.run {
            val wv = WebView()
            wv.pageFill = Color.WHITE
            wv.isContextMenuEnabled = true
            val e = wv.engine
            Fx.configure(e)
            e.setCreatePopupHandler { null }
            e.locationProperty().addListener { _, _, loc ->
                if (loc != null && loc.startsWith("http") && !loc.startsWith(LocalServer.origin)) {
                    SwingUtilities.invokeLater { session.onOpenExternal?.invoke(loc) }
                    Platform.runLater { e.load(lastUrl) }
                }
            }
            e.loadWorker.stateProperty().addListener { _, _, st ->
                if (primary) SwingUtilities.invokeLater { session.loading = st == Worker.State.RUNNING || st == Worker.State.SCHEDULED }
            }
            wv.setOnDragOver { ev -> if (ev.dragboard.hasFiles()) ev.acceptTransferModes(*TransferMode.COPY_OR_MOVE); ev.consume() }
            wv.setOnDragDropped { ev ->
                val files = ev.dragboard.files.orEmpty().map { it.absolutePath }
                if (files.isNotEmpty()) OpenRequests.send(files)
                ev.isDropCompleted = files.isNotEmpty()
                ev.consume()
            }
            wv.zoom = zoom
            panel.scene = Scene(wv)
            webView = wv
            engine = e
            pending?.let { e.load(it); pending = null }
        }
    }

    fun url(path: String): String {
        val clean = path.removePrefix("/")
        val p = clean.substringBefore('#')
        val frag = clean.substringAfter('#', "")
        return "${LocalServer.origin}/s/$token/" + Paths.encodePath(p) + if (frag.isNotEmpty()) "#$frag" else ""
    }

    /** URL that opens the current page in the system browser. */
    fun currentUrl(): String = lastUrl

    fun load() {
        if (primary) session.onLoadStart()
        channel.config = session.serveConfig("http").copy(channel = token)
        val u = url(session.path)
        lastUrl = u
        currentPageUrls[session] = u
        Fx.run { engine?.load(u) ?: run { pending = u } }
    }

    fun setZoom(z: Float) {
        zoom = z.toDouble()
        Fx.run { webView?.zoom = zoom }
    }

    fun command(c: ViewerCommand) {
        when (c) {
            is ViewerCommand.Zoom -> setZoom(c.factor)
            ViewerCommand.Print, is ViewerCommand.Pdf -> Fx.run {
                val e = engine ?: return@run
                val job = PrinterJob.createPrinterJob() ?: return@run
                if (job.showPrintDialog(webView?.scene?.window)) {
                    e.print(job)
                    job.endJob()
                }
            }
            ViewerCommand.Back -> Fx.run { runCatching { engine?.history?.go(-1) } }
            ViewerCommand.Forward -> Fx.run { runCatching { engine?.history?.go(1) } }
            ViewerCommand.DevTools -> SwingUtilities.invokeLater { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(lastUrl)) } }
            else -> Fx.run { runCatching { engine?.executeScript("window.__hv&&window.__hv.cmd(${c.toBridgeJson()})") } }
        }
    }

    fun dispose() {
        LocalServer.unregister(token)
        currentPageUrls.remove(session)
        Fx.run {
            engine?.load(null)
            panel.scene = null
            webView = null
            engine = null
        }
    }

    companion object {
        /** Last loaded URL per session, used for "Open in browser". */
        val currentPageUrls = java.util.concurrent.ConcurrentHashMap<ViewSession, String>()
    }
}

// ---------------------------------------------------------------------------------------------
// Code editor
// ---------------------------------------------------------------------------------------------

@Composable
fun CodeEditorFx(state: CodeEditorState, options: EditorOptions, modifier: Modifier) {
    val view = remember(state) { FxEditorView(state) }
    DisposableEffect(view) { onDispose { view.dispose() } }
    LaunchedEffect(view, options) { view.setOptions(options) }
    LaunchedEffect(view) { state.commands.collect { view.command(it) } }
    SwingPanel(factory = { view.panel }, modifier = modifier)
}

class FxEditorView(private val state: CodeEditorState) {
    private val token = randomId(32)
    val panel: JFXPanel = Fx.panel()
    private var engine: WebEngine? = null
    private var options: EditorOptions? = null
    private var ready = false

    init {
        LocalServer.registerEditor(token) { msg ->
            SwingUtilities.invokeLater {
                if (msg.contains("\"type\":\"ready\"")) onReady()
                state.handleMessage(msg)
            }
        }
        Fx.run {
            val wv = WebView()
            wv.pageFill = Color.web("#14141c")
            wv.isContextMenuEnabled = false
            val e = wv.engine
            Fx.configure(e)
            panel.scene = Scene(wv)
            engine = e
            e.load("${LocalServer.origin}/editor/index.html?t=http&k=$token")
        }
    }

    private fun onReady() {
        ready = true
        options?.let { sendInit(it) }
    }

    private fun sendInit(o: EditorOptions) {
        val content = randomId(24)
        LocalServer.putContent(content, state.text.encodeToByteArray())
        exec(buildJsonObject {
            put("op", "load")
            put("url", "/__hv/content/$content")
            put("options", o.toJson())
        }.toString())
    }

    fun setOptions(o: EditorOptions) {
        val first = options == null
        options = o
        if (!ready) return
        if (first) sendInit(o) else exec(buildJsonObject { put("op", "options"); put("options", o.toJson()) }.toString())
    }

    fun command(c: EditorCommand) {
        if (!ready) return
        if (c is EditorCommand.SetText && c.text.length > 32_000) {
            val content = randomId(24)
            LocalServer.putContent(content, c.text.encodeToByteArray())
            exec(buildJsonObject { put("op", "load"); put("url", "/__hv/content/$content"); put("keepCursor", c.keepCursor) }.toString())
        } else {
            exec(CodeEditorState.commandJson(c))
        }
    }

    private fun exec(json: String) {
        Fx.run { runCatching { engine?.executeScript("window.__hvEditor&&window.__hvEditor.cmd($json)") } }
    }

    fun dispose() {
        LocalServer.unregisterEditor(token)
        Fx.run { engine?.load(null); panel.scene = null; engine = null }
    }
}
