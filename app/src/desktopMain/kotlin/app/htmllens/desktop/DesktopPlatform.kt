package app.htmllens.desktop

import app.htmllens.core.formats.InputFile
import app.htmllens.platform.BlobStore
import app.htmllens.platform.EngineStatus
import app.htmllens.platform.PickedFile
import app.htmllens.platform.PickedFolder
import app.htmllens.platform.Platform
import app.htmllens.platform.PlatformKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

object AppDirs {
    val isMac = System.getProperty("os.name").lowercase().contains("mac")
    val isWindows = System.getProperty("os.name").lowercase().contains("win")

    val data: File by lazy {
        val home = System.getProperty("user.home")
        val dir = when {
            isMac -> File(home, "Library/Application Support/HTML Lens")
            isWindows -> File(System.getenv("LOCALAPPDATA") ?: System.getenv("APPDATA") ?: home, "HTML Lens")
            else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "html-lens")
        }
        dir.mkdirs()
        dir
    }
}

class FileBlobStore(private val root: File) : BlobStore {
    init { root.mkdirs() }

    private fun fileFor(key: String): File {
        val safe = key.split('/').joinToString(File.separator) { seg -> seg.replace(Regex("[^A-Za-z0-9._-]"), "_") }
        return File(root, safe)
    }

    override suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        fileFor(key).takeIf { it.isFile }?.readBytes()
    }

    override suspend fun put(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val f = fileFor(key)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        Unit
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) { fileFor(key).delete(); Unit }

    override suspend fun deletePrefix(prefix: String) = withContext(Dispatchers.IO) {
        val f = fileFor(prefix.trimEnd('/'))
        if (f.isDirectory) f.deleteRecursively() else f.delete()
        Unit
    }

    override suspend fun usage(): Long = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

class DesktopPlatform(private val window: () -> Frame?) : Platform {
    override val kind = PlatformKind.DESKTOP
    override val store: BlobStore = FileBlobStore(File(AppDirs.data, "library"))
    override val siteUrl: String = System.getProperty("htmllens.site") ?: "https://htmllens.silentdev.online"
    override val supportsFolderPick = true
    override val isMac = AppDirs.isMac
    override val engineStatus: StateFlow<EngineStatus> = MutableStateFlow(EngineStatus.Ready)

    private var lastDir: String? = null

    override suspend fun pickFiles(multiple: Boolean): List<PickedFile> {
        val files = onEdt {
            val dialog = FileDialog(window(), "Open HTML file", FileDialog.LOAD)
            dialog.isMultipleMode = multiple
            lastDir?.let { dialog.directory = it }
            dialog.isVisible = true
            dialog.files?.toList().orEmpty().also { if (it.isNotEmpty()) lastDir = dialog.directory }
        }
        return withContext(Dispatchers.IO) { files.map { PickedFile(it.name, it.readBytes(), it.absolutePath) } }
    }

    override suspend fun pickFolder(): PickedFolder? {
        val dir = onEdt {
            if (isMac) {
                System.setProperty("apple.awt.fileDialogForDirectories", "true")
                try {
                    val dialog = FileDialog(window(), "Open folder", FileDialog.LOAD)
                    lastDir?.let { dialog.directory = it }
                    dialog.isVisible = true
                    dialog.file?.let { File(dialog.directory, it) }
                } finally {
                    System.setProperty("apple.awt.fileDialogForDirectories", "false")
                }
            } else {
                val chooser = JFileChooser(lastDir)
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = "Open folder"
                if (chooser.showOpenDialog(window()) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            }
        } ?: return null
        lastDir = dir.parent
        return readFolder(dir)
    }

    suspend fun readFolder(dir: File): PickedFolder = withContext(Dispatchers.IO) {
        val base = dir.parentFile
        val files = dir.walkTopDown()
            .onEnter { !it.name.startsWith(".") || it == dir }
            .filter { it.isFile && !it.name.startsWith(".") }
            .take(20_000)
            .map { InputFile(it.relativeTo(base).invariantSeparatorsPath, it.readBytes()) }
            .toList()
        PickedFolder(dir.name, files, dir.absolutePath)
    }

    override suspend fun reopen(path: String): PickedFolder? = withContext(Dispatchers.IO) {
        val f = File(path)
        when {
            f.isDirectory -> readFolder(f)
            f.isFile -> PickedFolder(f.name, listOf(InputFile(f.name, f.readBytes())), f.absolutePath)
            else -> null
        }
    }

    override suspend fun saveFile(name: String, mime: String, bytes: ByteArray): String? {
        val target = onEdt {
            val dialog = FileDialog(window(), "Save", FileDialog.SAVE)
            dialog.file = name
            lastDir?.let { dialog.directory = it }
            dialog.isVisible = true
            dialog.file?.let { File(dialog.directory, it) }
        } ?: return null
        withContext(Dispatchers.IO) { target.writeBytes(bytes) }
        lastDir = target.parent
        return target.absolutePath
    }

    override suspend fun lastModified(path: String): Long? = withContext(Dispatchers.IO) {
        val f = File(path)
        when {
            f.isFile -> f.lastModified()
            f.isDirectory -> f.walkTopDown().take(5000).filter { it.isFile }.maxOfOrNull { it.lastModified() }
            else -> null
        }
    }

    /** Asks for a destination path without writing anything (used for PDF export). */
    suspend fun chooseSavePathDialog(name: String): String? = onEdt {
        val dialog = FileDialog(window(), "Save", FileDialog.SAVE)
        dialog.file = name
        lastDir?.let { dialog.directory = it }
        dialog.isVisible = true
        dialog.file?.let { File(dialog.directory, it).absolutePath }
    }

    override suspend fun readClipboard(): String? = onEdt {
        runCatching {
            val cb = Toolkit.getDefaultToolkit().systemClipboard
            if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) cb.getData(DataFlavor.stringFlavor) as? String else null
        }.getOrNull()
    }

    override fun writeClipboard(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    override fun openUrl(url: String) {
        runCatching {
            if (url.startsWith("mailto:")) Desktop.getDesktop().mail(URI(url)) else Desktop.getDesktop().browse(URI(url))
        }
    }

    override fun reveal(path: String) {
        val f = File(path)
        runCatching {
            when {
                isMac -> ProcessBuilder("open", "-R", f.absolutePath).start()
                AppDirs.isWindows -> ProcessBuilder("explorer.exe", "/select,", f.absolutePath).start()
                else -> Desktop.getDesktop().open(if (f.isDirectory) f else f.parentFile)
            }
        }
    }

    private suspend fun <T> onEdt(block: () -> T): T = withContext(Dispatchers.Main) {
        if (SwingUtilities.isEventDispatchThread()) block() else {
            var result: Result<T>? = null
            SwingUtilities.invokeAndWait { result = runCatching(block) }
            result!!.getOrThrow()
        }
    }
}
