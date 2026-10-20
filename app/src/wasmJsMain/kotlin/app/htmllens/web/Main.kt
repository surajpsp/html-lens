@file:OptIn(ExperimentalWasmJsInterop::class)

package app.htmllens.web

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeViewport
import app.htmllens.AppController
import app.htmllens.Dialog
import app.htmllens.Screen
import app.htmllens.ToolId
import app.htmllens.core.formats.InputFile
import app.htmllens.core.util.toByteArray
import app.htmllens.ui.HtmlLensApp
import app.htmllens.viewer.ViewSession
import kotlinx.browser.document
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

fun main() {
    val platform = WebPlatform()
    ComposeViewport(document.body!!) {
        val scope = rememberCoroutineScope()
        val controller = remember { AppController(platform, scope) }
        LaunchedEffect(Unit) {
            hvHideSplash()
            ViewSession.onDragEnter = { hvShowDrop() }
            hvInstallDrop { files, dir ->
                val list = files.toList()
                scope.launch {
                    val inputs = list.map { InputFile(it.path, it.data.toByteArray()) }
                    if (dir.isNotEmpty() || inputs.size > 1 && inputs.any { '/' in it.path }) controller.openInputs(dir.ifEmpty { "Folder" }, inputs, null)
                    else inputs.forEach { controller.openInputs(it.path, listOf(it), null) }
                }
            }
            hvInstallPaste(
                { text -> if (controller.acceptsGlobalPaste) { controller.handlePastedText(text); true } else false },
                { files ->
                    if (controller.acceptsGlobalPaste) scope.launch {
                        files.toList().forEach { f -> controller.openInputs(f.name, listOf(InputFile(f.name, f.data.toByteArray())), null) }
                    }
                },
            )
            controller.init()
            handleHash(controller)
            hvOnHashChange { scope.launch { handleHash(controller) } }
        }
        HtmlLensApp(controller)
    }
}

/** Deep links: #code=… (share link), #tool=pack, #editor, #paste, #sample=phishing, #open=handoff. */
private suspend fun handleHash(controller: AppController) {
    val hash = hvHash().removePrefix("#")
    if (hash.isEmpty()) return
    val params = hash.split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.isNotEmpty() }?.let { it[0] to it.getOrElse(1) { "" } } }.toMap()
    when {
        "code" in params -> controller.openShareFragment(hash)
        params["open"] == "handoff" -> {
            val files = hvTakeHandoff().await<JsArray<JsFile>>().toList()
            if (files.size == 1) controller.openInputs(files[0].name, listOf(InputFile(files[0].name, files[0].data.toByteArray())), null)
            else if (files.isNotEmpty()) controller.openInputs(files[0].path.substringBefore('/'), files.map { InputFile(it.path, it.data.toByteArray()) }, null)
        }
        "tool" in params -> ToolId.entries.firstOrNull { it.name.equals(params["tool"], true) }?.let { controller.navigate(Screen.Tool(it)) }
        "editor" in params -> controller.newPlayground()
        "apps" in params -> controller.navigate(Screen.Apps)
        "paste" in params -> controller.dialog = Dialog.Paste
        "sample" in params -> controller.openSample(params["sample"] ?: "welcome")
    }
    hvClearHash()
}
