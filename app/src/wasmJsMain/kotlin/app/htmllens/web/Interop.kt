@file:OptIn(ExperimentalWasmJsInterop::class)

package app.htmllens.web

import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLIFrameElement
import kotlin.js.Promise

/** Bindings to app/src/wasmJsMain/resources/hv-web.js. */
external interface JsFile : JsAny {
    val name: String
    val path: String
    val data: Uint8Array
}

external fun hvSandboxOrigin(): String
external fun hvSiteUrl(): String
external fun hvAppUrl(): String
external fun hvIsMac(): Boolean
external fun hvHash(): String
external fun hvClearHash()
external fun hvOnHashChange(cb: () -> Unit)
external fun hvHideSplash()

external fun hvIdbGet(key: String): Promise<Uint8Array?>
external fun hvIdbPut(key: String, data: Uint8Array): Promise<JsAny?>
external fun hvIdbDelete(key: String): Promise<JsAny?>
external fun hvIdbDeletePrefix(prefix: String): Promise<JsAny?>
external fun hvUsage(): Promise<JsNumber>

external fun hvPickFiles(multiple: Boolean, directory: Boolean): Promise<JsArray<JsFile>>
external fun hvTakeHandoff(): Promise<JsArray<JsFile>>
external fun hvDownload(name: String, mime: String, data: Uint8Array)
external fun hvClipboardRead(): Promise<JsString?>
external fun hvClipboardWrite(text: String)
external fun hvOpen(url: String)
external fun hvInstallDrop(onDrop: (JsArray<JsFile>, String) -> Unit)
external fun hvShowDrop()
external fun hvInstallPaste(onText: (String) -> Boolean, onFiles: (JsArray<JsFile>) -> Unit)

external fun hvRegisterFrame(iframe: HTMLIFrameElement, id: Int)
external fun hvUnregisterFrame(iframe: HTMLIFrameElement)
external fun hvOnFrameMessage(cb: (Int, String) -> Unit)
external fun hvPostToFrame(iframe: HTMLIFrameElement, json: String, origin: String)

external fun hvSandboxInit(): Promise<JsAny?>
external fun hvNewArray(): JsAny
external fun hvPushFile(arr: JsAny, path: String, mime: String, data: Uint8Array)
external fun hvSandboxPut(id: String, entry: String, files: JsAny): Promise<JsAny?>
