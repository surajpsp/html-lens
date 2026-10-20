package app.htmllens.platform

import app.htmllens.core.formats.InputFile
import kotlinx.coroutines.flow.StateFlow

enum class PlatformKind { DESKTOP, WEB }

class PickedFile(val name: String, val bytes: ByteArray, val path: String? = null)

class PickedFolder(val name: String, val files: List<InputFile>, val path: String? = null)

/** Key/value blob storage: files on desktop, IndexedDB on the web. */
interface BlobStore {
    suspend fun get(key: String): ByteArray?
    suspend fun put(key: String, bytes: ByteArray)
    suspend fun delete(key: String)
    suspend fun deletePrefix(prefix: String)
    suspend fun usage(): Long
}

sealed interface EngineStatus {
    data object Ready : EngineStatus
    data class Preparing(val step: String, val progress: Float?) : EngineStatus
    data class Failed(val message: String) : EngineStatus
}

interface Platform {
    val kind: PlatformKind
    val store: BlobStore
    /** Public website, used for share links and "get the app" links. */
    val siteUrl: String
    /** Web app URL that opens share links. */
    val appUrl: String get() = "$siteUrl/app/"
    val supportsFolderPick: Boolean
    val isMac: Boolean
    val engineStatus: StateFlow<EngineStatus>

    suspend fun pickFiles(multiple: Boolean = false): List<PickedFile>
    suspend fun pickFolder(): PickedFolder?
    /** Saves or downloads a file. Returns a display location, or null when cancelled. */
    suspend fun saveFile(name: String, mime: String, bytes: ByteArray): String?
    suspend fun readClipboard(): String?
    fun writeClipboard(text: String)
    fun openUrl(url: String)
    /** Desktop: re-reads a file or folder that was opened from disk. */
    suspend fun reopen(path: String): PickedFolder? = null
    fun reveal(path: String) {}
    fun retryEngine() {}
    /** Desktop: lets the user choose where a PDF is written (rendered natively). Web returns null → print dialog. */
    suspend fun chooseSavePath(name: String): String? = null
    /** Desktop: newest modification time of a file or folder, for live reload. */
    suspend fun lastModified(path: String): Long? = null
}
