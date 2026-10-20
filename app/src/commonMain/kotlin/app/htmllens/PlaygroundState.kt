package app.htmllens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.serve.SandboxMode
import app.htmllens.viewer.CodeEditorState
import app.htmllens.viewer.ViewSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class PlaygroundLayout { SPLIT, CODE, PREVIEW }

/** Editor + live preview state; survives navigation and is auto-saved as a draft. */
class PlaygroundState {
    @Serializable
    private data class Meta(val name: String, val appId: String? = null)

    var editor by mutableStateOf(CodeEditorState(Samples.templates.first().second))
        private set
    var name by mutableStateOf("Untitled")
    var appId by mutableStateOf<String?>(null)
    var autoRun by mutableStateOf(true)
    var dirty by mutableStateOf(false)
    var layout by mutableStateOf(PlaygroundLayout.SPLIT)
    var device by mutableStateOf(Device.RESPONSIVE)
    var showConsole by mutableStateOf(false)
    var split by mutableStateOf(0.5f)
    val session = ViewSession("playground", Bundle.single("Preview", editor.text, BundleKind.PASTE), SandboxMode.FULL)
    var lastRunText: String = editor.text
        private set

    fun load(html: String, title: String, id: String?) {
        editor = CodeEditorState(html)
        name = title
        appId = id
        dirty = false
        run()
    }

    fun run() {
        lastRunText = editor.text
        session.replaceBundle(Bundle.single(name, editor.text, BundleKind.PASTE), keepPath = true)
    }

    fun restore(html: String, metaJson: String?) {
        val meta = metaJson?.let { runCatching { Json.decodeFromString(Meta.serializer(), it) }.getOrNull() }
        load(html, meta?.name ?: "Untitled", meta?.appId)
    }

    fun metaJson(): String = Json.encodeToString(Meta.serializer(), Meta(name, appId))
}
