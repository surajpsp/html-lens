package app.htmllens.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.htmllens.core.bundle.Bundle
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.serve.ServeConfig
import app.htmllens.core.util.B64
import app.htmllens.core.util.currentTimeMillis
import app.htmllens.core.util.randomId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class LogLevel { LOG, INFO, WARN, ERROR, DEBUG, RESULT, INPUT }

class ConsoleEntry(
    val level: LogLevel,
    val text: String,
    val source: String = "",
    val line: Int = 0,
    val time: Long = currentTimeMillis(),
) {
    var count by mutableIntStateOf(1)
}

class NetEntry(
    val url: String,
    val kind: String,
    val status: Int,
    val blocked: Boolean,
    val failed: Boolean,
    val duration: Int,
    val size: Long,
    val external: Boolean,
)

sealed class ViewerCommand {
    data class Find(val text: String, val dir: Int = 1) : ViewerCommand()
    data object FindClear : ViewerCommand()
    data class ScrollTo(val y: Int) : ViewerCommand()
    data class Heading(val index: Int) : ViewerCommand()
    data class Anchor(val id: String) : ViewerCommand()
    data object Print : ViewerCommand()
    data object Snap : ViewerCommand()
    data class Eval(val code: String) : ViewerCommand()
    /** Desktop only: render to PDF at the given path. */
    data class Pdf(val path: String) : ViewerCommand()
    data class Zoom(val factor: Float) : ViewerCommand()
    /** Desktop only: open Chromium DevTools for this page. */
    data object DevTools : ViewerCommand()
    data object Back : ViewerCommand()
    data object Forward : ViewerCommand()

    fun toBridgeJson(): String = buildJsonObject {
        when (val c = this@ViewerCommand) {
            is Find -> { put("op", "find"); put("text", c.text); put("dir", c.dir) }
            FindClear -> put("op", "findClear")
            is ScrollTo -> { put("op", "scrollTo"); put("y", c.y) }
            is Heading -> { put("op", "heading"); put("index", c.index) }
            is Anchor -> { put("op", "anchor"); put("id", c.id) }
            Print -> put("op", "print")
            Snap -> put("op", "snap")
            is Eval -> { put("op", "eval"); put("code", c.code) }
            Back -> put("op", "back")
            Forward -> put("op", "forward")
            is Pdf, is Zoom, DevTools -> put("op", "noop")
        }
    }.toString()
}

/**
 * Everything the UI knows about one rendered bundle: what to show, how to sandbox it,
 * and the live telemetry (console, network, find) reported back by the injected bridge.
 */
class ViewSession(
    /** Storage namespace + stable origin key (library item id, app id, or a random id). */
    val key: String,
    bundle: Bundle,
    mode: SandboxMode,
    scrollY: Int = 0,
) {
    var bundle by mutableStateOf(bundle)
        private set
    /** Bumped whenever [bundle] content changes, so engines re-publish files. */
    var revision by mutableIntStateOf(0)
        private set
    var mode by mutableStateOf(mode)
    var darkSim by mutableStateOf(false)
    var imagesOff by mutableStateOf(false)
    /** Path (with optional #fragment) the engine should display. */
    var path by mutableStateOf(bundle.entry)
        private set
    var loadToken by mutableIntStateOf(0)
        private set

    val nonce: String = randomId(20)
    private var restoreScroll = scrollY

    var currentPath by mutableStateOf(bundle.entry)
    var title by mutableStateOf("")
    var loading by mutableStateOf(true)
    var scrollY by mutableIntStateOf(scrollY)
    var pageHeight by mutableIntStateOf(0)
    var findCount by mutableIntStateOf(0)
    var findCurrent by mutableIntStateOf(0)
    val console = mutableStateListOf<ConsoleEntry>()
    val network = mutableStateListOf<NetEntry>()
    var errorCount by mutableIntStateOf(0)
    var warnCount by mutableIntStateOf(0)
    var blockedCount by mutableIntStateOf(0)

    val commands = MutableSharedFlow<ViewerCommand>(extraBufferCapacity = 64)

    var onSnap: ((ByteArray?, String?) -> Unit)? = null
    var onOpenExternal: ((String) -> Unit)? = null
    var onScroll: ((Int) -> Unit)? = null
    var onPdf: ((String, Boolean) -> Unit)? = null
    var onDownload: ((String, ByteArray) -> Unit)? = null

    fun send(cmd: ViewerCommand) {
        commands.tryEmit(cmd)
    }

    fun navigate(target: String) {
        path = target.removePrefix("/")
        loadToken++
    }

    fun reload() {
        restoreScroll = scrollY
        loadToken++
    }

    fun changeMode(newMode: SandboxMode) {
        if (newMode == mode) return
        mode = newMode
        reload()
    }

    fun replaceBundle(newBundle: Bundle, keepPath: Boolean = false) {
        bundle = newBundle
        revision++
        if (!keepPath || newBundle.resolve(path.substringBefore('#')) == null) path = newBundle.entry
        loadToken++
    }

    fun clearLogs() {
        console.clear(); network.clear()
        errorCount = 0; warnCount = 0; blockedCount = 0
    }

    /** Called by engines right before a (re)load. */
    fun onLoadStart() {
        loading = true
        findCount = 0; findCurrent = 0
        clearLogs()
    }

    fun serveConfig(transport: String): ServeConfig {
        val y = restoreScroll
        restoreScroll = 0
        return ServeConfig(mode, nonce, transport, darkSim, imagesOff, key, y, captureNetwork = true)
    }

    fun addConsole(level: LogLevel, text: String, source: String = "", line: Int = 0) {
        val last = console.lastOrNull()
        if (last != null && last.level == level && last.text == text && last.source == source && last.line == line) {
            last.count++
        } else {
            if (console.size >= 2000) console.removeAt(0)
            console.add(ConsoleEntry(level, text, source, line))
        }
        if (level == LogLevel.ERROR) errorCount++
        if (level == LogLevel.WARN) warnCount++
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Parses a message posted by bridge.js. Safe to call from any thread that owns the UI. */
    fun handleBridgeMessage(raw: String) {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        handle(obj)
    }

    private fun handle(o: JsonObject) {
        when (o.str("type")) {
            "batch" -> (o["items"] as? JsonArray)?.forEach { runCatching { handle(it.jsonObject) } }
            "console" -> {
                val level = when (o.str("level")) {
                    "warn" -> LogLevel.WARN; "error" -> LogLevel.ERROR; "info" -> LogLevel.INFO
                    "debug" -> LogLevel.DEBUG; "result" -> LogLevel.RESULT; else -> LogLevel.LOG
                }
                val args = (o["args"] as? JsonArray)?.joinToString(" ") { it.jsonPrimitive.contentOrNull ?: it.toString() } ?: ""
                addConsole(level, args)
            }
            "error" -> {
                val stack = o.str("stack")
                val msg = o.str("message") + if (stack.isNotBlank() && !stack.contains(o.str("message"))) "\n$stack" else ""
                addConsole(LogLevel.ERROR, msg, o.str("source").substringAfterLast('/'), o.int("line"))
            }
            "net" -> {
                val url = o.str("url")
                if (url.isBlank()) return
                val blocked = o.bool("blocked")
                val failed = o.bool("failed")
                val external = url.startsWith("http") && !url.contains("htmllens.local") && !isSandboxUrl(url)
                if (network.size >= 3000) network.removeAt(0)
                network.add(NetEntry(url, o.str("kind"), o.int("status"), blocked, failed, o.int("duration"), o.long("size"), external))
                if (blocked) blockedCount++
            }
            "page" -> {
                currentPath = o.str("path").substringAfter("/s/").let { p -> stripSandboxPrefix(p) }
                title = o.str("title")
                pageHeight = o.int("height")
            }
            "title" -> title = o.str("title")
            "loaded" -> { loading = false; pageHeight = o.int("height") }
            "scroll" -> { scrollY = o.int("y"); onScroll?.invoke(scrollY) }
            "find" -> { findCount = o.int("count"); findCurrent = o.int("current") }
            "snap" -> {
                val data = o.str("data")
                if (data.startsWith("data:")) onSnap?.invoke(B64.decode(data.substringAfter(",")), null)
                else onSnap?.invoke(null, o.str("error").ifBlank { "Screenshot failed" })
            }
            "open" -> onOpenExternal?.invoke(o.str("url"))
            "download" -> {
                val data = o.str("data")
                if (data.startsWith("data:")) onDownload?.invoke(o.str("name").ifBlank { "download" }, B64.decode(data.substringAfter(",")))
            }
            "dragenter" -> onDragEnter?.invoke()
        }
    }

    companion object {
        /** Set by the web front end so drags that start over a page show the app's drop overlay. */
        var onDragEnter: (() -> Unit)? = null
    }

    /** Web sandbox paths look like s/<session>/<cfg>/<path>; desktop paths are already plain. */
    var sandboxOrigin: String? = null
    private fun isSandboxUrl(url: String) = sandboxOrigin?.let { url.startsWith(it) } ?: false
    private fun stripSandboxPrefix(p: String): String {
        val parts = p.split('/')
        return if (sandboxOrigin != null && parts.size > 2 && parts[0] == "s") parts.drop(3).joinToString("/") else p
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull ?: ""
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull ?: (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0
    private fun JsonObject.long(k: String) = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
}

@Suppress("unused")
private fun JsonArray.strings() = map { it.jsonPrimitive.content }

@Suppress("unused")
private fun JsonObject.arr(k: String) = this[k]?.jsonArray
