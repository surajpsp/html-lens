package app.htmllens.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

sealed class EditorCommand {
    data class SetText(val text: String, val keepCursor: Boolean) : EditorCommand()
    data class Reveal(val line: Int) : EditorCommand()
    data class Insert(val text: String) : EditorCommand()
    data object Focus : EditorCommand()
}

data class EditorOptions(
    val dark: Boolean,
    val fontSize: Int,
    val readOnly: Boolean,
    val wrap: Boolean,
    val lang: String,
) {
    fun toJson() = buildJsonObject {
        put("theme", if (dark) "dark" else "light")
        put("fontSize", fontSize)
        put("readOnly", readOnly)
        put("wrap", wrap)
        put("lang", lang)
    }
}

/** State for an embedded CodeMirror editor; the platform engine syncs it both ways. */
class CodeEditorState(initial: String, val readOnly: Boolean = false, val lang: String = "html") {
    var text by mutableStateOf(initial)
        private set
    var ready by mutableStateOf(false)
    var cursorLine by mutableIntStateOf(1)
    var cursorCol by mutableIntStateOf(1)
    var lineCount by mutableIntStateOf(1)
    /** Bumped on every edit coming from the editor itself. */
    var editVersion by mutableIntStateOf(0)

    val commands = MutableSharedFlow<EditorCommand>(extraBufferCapacity = 32)
    var onSave: () -> Unit = {}
    var onRun: () -> Unit = {}

    fun setText(value: String, keepCursor: Boolean = false) {
        text = value
        commands.tryEmit(EditorCommand.SetText(value, keepCursor))
    }

    fun reveal(line: Int) { commands.tryEmit(EditorCommand.Reveal(line)) }
    fun insert(snippet: String) { commands.tryEmit(EditorCommand.Insert(snippet)) }

    private val json = Json { ignoreUnknownKeys = true }

    fun handleMessage(raw: String) {
        val o = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when (o.s("type")) {
            "ready" -> ready = true
            "change" -> { text = o.s("value"); editVersion++ }
            "save" -> onSave()
            "run" -> onRun()
            "cursor" -> { cursorLine = o.i("line"); cursorCol = o.i("col"); lineCount = o.i("lines") }
        }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull ?: ""
    private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.intOrNull ?: 0

    companion object {
        fun commandJson(cmd: EditorCommand): String = buildJsonObject {
            when (cmd) {
                is EditorCommand.SetText -> { put("op", "set"); put("value", cmd.text); put("keepCursor", cmd.keepCursor) }
                is EditorCommand.Reveal -> { put("op", "reveal"); put("line", cmd.line) }
                is EditorCommand.Insert -> { put("op", "insert"); put("text", cmd.text) }
                EditorCommand.Focus -> put("op", "focus")
            }
        }.toString()
    }
}
