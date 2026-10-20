@file:OptIn(ExperimentalWasmJsInterop::class)

package app.htmllens.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebElementView
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.util.Paths
import app.htmllens.core.util.randomId
import app.htmllens.core.util.toUint8Array
import app.htmllens.web.hvNewArray
import app.htmllens.web.hvOnFrameMessage
import app.htmllens.web.hvPostToFrame
import app.htmllens.web.hvPushFile
import app.htmllens.web.hvRegisterFrame
import app.htmllens.web.hvSandboxOrigin
import app.htmllens.web.hvSandboxPut
import app.htmllens.web.hvUnregisterFrame
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLIFrameElement

actual val engineSurfacesAreHeavyweight: Boolean = false

/** Routes postMessage traffic from viewer/editor iframes to Kotlin handlers. */
private object Frames {
    private var next = 1
    private val handlers = HashMap<Int, (String) -> Unit>()

    init {
        hvOnFrameMessage { id, json -> handlers[id]?.invoke(json) }
    }

    fun register(handler: (String) -> Unit): Int = next++.also { handlers[it] = handler }
    fun unregister(id: Int) { handlers.remove(id) }
}

/** Publishes bundles to the sandbox origin (Cache Storage via host.html) once per revision. */
private object Sandbox {
    val origin: String = hvSandboxOrigin()
    private val published = HashMap<ViewSession, Pair<Int, String>>()

    suspend fun publish(session: ViewSession): String {
        published[session]?.let { (rev, sid) -> if (rev == session.revision) return sid }
        val sid = session.key.filter { it.isLetterOrDigit() }.take(16) + "r" + session.revision + randomId(8)
        val arr = hvNewArray()
        for (f in session.bundle.files.values) hvPushFile(arr, f.path, f.mime, f.bytes.toUint8Array())
        hvSandboxPut(sid, session.bundle.entry, arr).await<JsAny?>()
        published[session] = session.revision to sid
        return sid
    }

    fun url(session: ViewSession, sid: String): String {
        val cfg = session.serveConfig("web")
        val m = when (cfg.mode) { SandboxMode.SAFE -> "s"; SandboxMode.OFFLINE -> "o"; SandboxMode.FULL -> "f" }
        val ns = session.key.filter { it.isLetterOrDigit() }.take(24)
        val token = "$m-${if (cfg.darkSim) 1 else 0}-${if (cfg.imagesOff) 1 else 0}-${cfg.scrollY}-${cfg.nonce}-$ns"
        val clean = session.path.removePrefix("/")
        val frag = clean.substringAfter('#', "")
        return "$origin/s/$sid/$token/" + Paths.encodePath(clean.substringBefore('#')) + if (frag.isNotEmpty()) "#$frag" else ""
    }
}

private class WebFrame(private val session: ViewSession, private val primary: Boolean) {
    val container = (document.createElement("div") as HTMLDivElement).apply {
        style.width = "100%"; style.height = "100%"; style.overflowX = "hidden"; style.overflowY = "hidden"; style.background = "#ffffff"
    }
    val iframe = (document.createElement("iframe") as HTMLIFrameElement).apply {
        setAttribute("sandbox", "allow-scripts allow-same-origin allow-forms allow-popups allow-modals allow-downloads allow-popups-to-escape-sandbox")
        setAttribute("allow", "clipboard-write; fullscreen")
        setAttribute("referrerpolicy", "no-referrer")
        setAttribute("title", "Page preview")
        style.border = "0"; style.display = "block"; style.width = "100%"; style.height = "100%"; style.background = "#ffffff"
        style.setProperty("transform-origin", "0 0")
    }
    private val id = Frames.register { json -> if (primary) session.handleBridgeMessage(json) }

    init { container.appendChild(iframe) }

    suspend fun load() {
        session.sandboxOrigin = Sandbox.origin
        if (primary) session.onLoadStart()
        val sid = runCatching { Sandbox.publish(session) }.getOrElse {
            if (primary) session.addConsole(LogLevel.ERROR, "Could not reach the preview sandbox (${Sandbox.origin}): ${it.message}")
            return
        }
        hvRegisterFrame(iframe, id)
        iframe.src = Sandbox.url(session, sid)
        hvRegisterFrame(iframe, id)
    }

    fun setZoom(z: Float) {
        val pct = (100.0 / z).toString() + "%"
        iframe.style.width = if (z == 1f) "100%" else pct
        iframe.style.height = if (z == 1f) "100%" else pct
        iframe.style.setProperty("transform", if (z == 1f) "none" else "scale($z)")
    }

    fun command(c: ViewerCommand) {
        when (c) {
            is ViewerCommand.Zoom -> setZoom(c.factor)
            is ViewerCommand.Pdf -> send(ViewerCommand.Print.toBridgeJson())
            ViewerCommand.DevTools -> window.open(iframe.src, "_blank")
            else -> send(c.toBridgeJson())
        }
    }

    private fun send(json: String) = hvPostToFrame(iframe, json.replaceFirst("{", "{\"__hvcmd\":1,"), Sandbox.origin)

    fun dispose() {
        Frames.unregister(id)
        hvUnregisterFrame(iframe)
        iframe.src = "about:blank"
    }
}

@Composable
actual fun HtmlView(session: ViewSession, modifier: Modifier, primary: Boolean, zoom: Float) {
    val frame = remember(session, primary) { WebFrame(session, primary) }
    DisposableEffect(frame) { onDispose { frame.dispose() } }
    LaunchedEffect(frame, session.loadToken, session.revision) { frame.load() }
    LaunchedEffect(frame, zoom) { frame.setZoom(zoom) }
    if (primary) LaunchedEffect(frame) { session.commands.collect { frame.command(it) } }
    WebElementView(factory = { frame.container }, modifier = modifier)
}

// ---------------------------------------------------------------------------------------------
// Code editor (CodeMirror in a same-origin iframe)
// ---------------------------------------------------------------------------------------------

private class WebEditor(private val state: CodeEditorState) {
    var options: EditorOptions? = null
    private var ready = false
    val iframe = (document.createElement("iframe") as HTMLIFrameElement).apply {
        style.border = "0"; style.display = "block"; style.width = "100%"; style.height = "100%"
        setAttribute("title", "Code editor")
        src = "editor/index.html?t=web"
    }
    private val id = Frames.register { json ->
        if (json.contains("\"type\":\"ready\"")) { ready = true; options?.let { sendInit(it) } }
        state.handleMessage(json)
    }

    fun attach() = hvRegisterFrame(iframe, id)

    private fun sendInit(o: EditorOptions) = send(buildJsonObject {
        put("op", "init"); put("value", state.text); put("options", o.toJson())
    }.toString())

    fun setOptions(o: EditorOptions) {
        val first = options == null
        options = o
        if (!ready) return
        if (first) sendInit(o) else send(buildJsonObject { put("op", "options"); put("options", o.toJson()) }.toString())
    }

    fun command(c: EditorCommand) { if (ready) send(CodeEditorState.commandJson(c)) }

    private fun send(json: String) = hvPostToFrame(iframe, json.replaceFirst("{", "{\"__hvedcmd\":1,"), window.location.origin)

    fun dispose() { Frames.unregister(id); hvUnregisterFrame(iframe) }
}

@Composable
actual fun CodeEditor(state: CodeEditorState, options: EditorOptions, modifier: Modifier) {
    val editor = remember(state) { WebEditor(state) }
    DisposableEffect(editor) { onDispose { editor.dispose() } }
    LaunchedEffect(editor) { editor.attach() }
    LaunchedEffect(editor, options) { editor.attach(); editor.setOptions(options) }
    LaunchedEffect(editor) { state.commands.collect { editor.command(it) } }
    WebElementView(factory = { editor.iframe }, modifier = modifier)
}
