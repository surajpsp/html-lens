@file:OptIn(ExperimentalWasmJsInterop::class)

package app.htmllens.web

import app.htmllens.core.formats.InputFile
import app.htmllens.core.util.toByteArray
import app.htmllens.core.util.toUint8Array
import app.htmllens.platform.BlobStore
import app.htmllens.platform.EngineStatus
import app.htmllens.platform.PickedFile
import app.htmllens.platform.PickedFolder
import app.htmllens.platform.Platform
import app.htmllens.platform.PlatformKind
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class IdbBlobStore : BlobStore {
    override suspend fun get(key: String): ByteArray? = hvIdbGet(key).await<Uint8Array?>()?.toByteArray()
    override suspend fun put(key: String, bytes: ByteArray) { hvIdbPut(key, bytes.toUint8Array()).await<JsAny?>() }
    override suspend fun delete(key: String) { hvIdbDelete(key).await<JsAny?>() }
    override suspend fun deletePrefix(prefix: String) { hvIdbDeletePrefix(prefix).await<JsAny?>() }
    override suspend fun usage(): Long = hvUsage().await<JsNumber>().toDouble().toLong()
}

private typealias Uint8Array = org.khronos.webgl.Uint8Array

fun JsArray<JsFile>.toList(): List<JsFile> = List(length) { get(it)!! }

class WebPlatform : Platform {
    override val kind = PlatformKind.WEB
    override val store: BlobStore = IdbBlobStore()
    override val siteUrl: String = hvSiteUrl()
    override val appUrl: String = hvAppUrl()
    override val supportsFolderPick = true
    override val isMac: Boolean = hvIsMac()
    override val engineStatus: StateFlow<EngineStatus> = MutableStateFlow(EngineStatus.Ready)

    override suspend fun pickFiles(multiple: Boolean): List<PickedFile> =
        hvPickFiles(multiple, false).await<JsArray<JsFile>>().toList().map { PickedFile(it.name, it.data.toByteArray()) }

    override suspend fun pickFolder(): PickedFolder? {
        val files = hvPickFiles(true, true).await<JsArray<JsFile>>().toList()
        if (files.isEmpty()) return null
        val name = files.first().path.substringBefore('/')
        return PickedFolder(name, files.map { InputFile(it.path, it.data.toByteArray()) })
    }

    override suspend fun saveFile(name: String, mime: String, bytes: ByteArray): String? {
        hvDownload(name, mime, bytes.toUint8Array())
        return name
    }

    override suspend fun readClipboard(): String? = hvClipboardRead().await<JsString?>()?.toString()
    override fun writeClipboard(text: String) = hvClipboardWrite(text)
    override fun openUrl(url: String) = hvOpen(url)
}
