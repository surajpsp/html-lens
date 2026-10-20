package app.htmllens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import app.htmllens.core.analysis.EmailCheck
import app.htmllens.core.analysis.EmailLint
import app.htmllens.core.analysis.ExportDetector
import app.htmllens.core.analysis.ExportType
import app.htmllens.core.analysis.PageAnalyzer
import app.htmllens.core.analysis.PageInfo
import app.htmllens.core.analysis.ScanReport
import app.htmllens.core.analysis.ThreatScanner
import app.htmllens.core.analysis.Verdict
import app.htmllens.core.bundle.Bundle
import app.htmllens.core.bundle.BundleCodec
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.formats.BundleLoader
import app.htmllens.core.formats.InputFile
import app.htmllens.core.formats.ZipWriter
import app.htmllens.core.model.AppVersion
import app.htmllens.core.model.HtmlApp
import app.htmllens.core.model.LibraryItem
import app.htmllens.core.model.Settings
import app.htmllens.core.search.LibrarySearch
import app.htmllens.core.search.TextExtractor
import app.htmllens.core.serve.SandboxMode
import app.htmllens.core.tools.Packer
import app.htmllens.core.tools.ShareLink
import app.htmllens.core.util.Charsets
import app.htmllens.core.util.Paths
import app.htmllens.core.util.currentTimeMillis
import app.htmllens.core.util.formatBytes
import app.htmllens.core.util.randomId
import app.htmllens.platform.PickedFolder
import app.htmllens.platform.Platform
import app.htmllens.platform.PlatformKind
import app.htmllens.ui.AppIcons
import app.htmllens.viewer.CodeEditorState
import app.htmllens.viewer.ViewSession
import app.htmllens.viewer.ViewerCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

sealed interface Screen {
    data object Home : Screen
    data object Viewer : Screen
    data object Apps : Screen
    data object Playground : Screen
    data object Tools : Screen
    data class Tool(val id: ToolId) : Screen
    data object Settings : Screen
}

enum class ToolId(val title: String, val subtitle: String) {
    SAFETY("Safety check", "Scan an HTML attachment for phishing, smuggling and data theft — without opening it."),
    PACK("Pack into one file", "Inline CSS, scripts, images and fonts into a single self-contained .html file."),
    CONVERT("MHTML & Web Archive converter", "Turn .mhtml, .mht and .webarchive files into plain HTML or a ZIP of files."),
    FORMAT("Format & minify", "Beautify messy HTML or shrink it for production."),
    EMAIL("Email preview & test", "Check HTML emails for Gmail clipping, dark mode and Outlook problems."),
    SHARE("Share as link", "Turn HTML into a link that opens in HTML Lens. Nothing is uploaded."),
    DIFF("Compare HTML", "See exactly what changed between two versions."),
    TEXT("Extract text", "Get the readable text out of any HTML page."),
}

enum class Device(val label: String, val width: Int, val height: Int) {
    RESPONSIVE("Responsive", 0, 0),
    PHONE_SMALL("iPhone SE", 375, 667),
    PHONE("iPhone 15", 393, 852),
    ANDROID("Pixel 8", 412, 915),
    TABLET("iPad Air", 820, 1180),
    LAPTOP("Laptop", 1366, 768),
    DESKTOP("Desktop HD", 1920, 1080),
    MULTI("Phone + Tablet + Desktop", 0, 0),
    EMAIL_MOBILE("Email · mobile", 375, 740),
    EMAIL_DESKTOP("Email · desktop", 680, 900),
}

enum class LeftPanel { FILES, OUTLINE, SEARCH, ATTACHMENTS }
enum class RightPanel { SECURITY, INFO, EMAIL }
enum class BottomPanel { CONSOLE, NETWORK }

class ViewerTab(
    val id: String,
    val session: ViewSession,
    val libraryId: String?,
    val appId: String?,
    val sourcePath: String?,
) {
    var title by mutableStateOf(session.bundle.name)
    var scan by mutableStateOf<ScanReport?>(null)
    var scanning by mutableStateOf(false)
    var info by mutableStateOf<PageInfo?>(null)
    var export by mutableStateOf<ExportType?>(null)
    var emailChecks by mutableStateOf<List<EmailCheck>>(emptyList())
    var device by mutableStateOf(Device.RESPONSIVE)
    var landscape by mutableStateOf(false)
    var zoom by mutableStateOf(1f)
    var showSource by mutableStateOf(false)
    var left by mutableStateOf<LeftPanel?>(null)
    var right by mutableStateOf<RightPanel?>(null)
    var bottom by mutableStateOf<BottomPanel?>(null)
    var findOpen by mutableStateOf(false)
    var findText by mutableStateOf("")
    var liveReload by mutableStateOf(sourcePath != null)
    var bannerDismissed by mutableStateOf(false)
    var networkBannerDismissed by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchHits by mutableStateOf<List<app.htmllens.core.search.BundleSearch.Hit>?>(null)
    var searchProgress by mutableStateOf<Float?>(null)
    var split by mutableStateOf(0.55f)
    var sourceEditor: CodeEditorState? = null
    internal var watchJob: Job? = null
}

sealed interface Dialog {
    data class Confirm(val title: String, val text: String, val confirm: String, val danger: Boolean = false, val onConfirm: () -> Unit) : Dialog
    data class Input(val title: String, val label: String, val initial: String, val confirm: String, val onSubmit: (String) -> Unit) : Dialog
    data class ExternalLink(val url: String) : Dialog
    data object Paste : Dialog
    data class SaveApp(val html: String, val suggestedName: String, val existingId: String?) : Dialog
    data class Versions(val appId: String) : Dialog
    data class Message(val title: String, val text: String) : Dialog
    data object Shortcuts : Dialog
}

data class Toast(val text: String, val error: Boolean = false, val action: String? = null, val onAction: (() -> Unit)? = null)

data class QuickAction(val label: String, val icon: ImageVector, val run: () -> Unit)

/** Application state and actions shared by the desktop and web front ends. */
class AppController(val platform: Platform, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var settings by mutableStateOf(Settings())
        private set
    val library = mutableStateListOf<LibraryItem>()
    val apps = mutableStateListOf<HtmlApp>()
    val tabs = mutableStateListOf<ViewerTab>()
    var activeTab by mutableStateOf<ViewerTab?>(null)
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var busy by mutableStateOf<String?>(null)
    var dialog by mutableStateOf<Dialog?>(null)
    var loaded by mutableStateOf(false)
        private set
    var storageUsed by mutableStateOf(0L)
    var dragActive by mutableStateOf(false)
    /** Library search text, edited from the app header. */
    var libraryQuery by mutableStateOf("")
    /** Text to pre-fill the paste box with (set by the paste shortcut when the text isn't HTML). */
    var pendingPaste by mutableStateOf("")
    val toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 8)
    val playground = PlaygroundState()

    private val texts = HashMap<String, String>()
    private var saveLibraryJob: Job? = null

    // ---------------------------------------------------------------------------------------
    // Startup & persistence
    // ---------------------------------------------------------------------------------------

    suspend fun init() {
        runCatching { platform.store.get("settings.json")?.decodeToString()?.let { settings = json.decodeFromString(Settings.serializer(), it) } }
        runCatching {
            platform.store.get("library.json")?.decodeToString()?.let {
                library.addAll(json.decodeFromString(ListSerializer(LibraryItem.serializer()), it).sortedByDescending { i -> i.lastOpenedAt })
            }
        }
        runCatching {
            platform.store.get("apps.json")?.decodeToString()?.let {
                apps.addAll(json.decodeFromString(ListSerializer(HtmlApp.serializer()), it).sortedByDescending { a -> a.updatedAt })
            }
        }
        runCatching {
            platform.store.get("playground/draft.html")?.decodeToString()?.let { playground.restore(it, platform.store.get("playground/meta.json")?.decodeToString()) }
        }
        loaded = true
        refreshUsage()
    }

    fun refreshUsage() { scope.launch { storageUsed = runCatching { platform.store.usage() }.getOrDefault(0) } }

    private fun persistLibrary() {
        saveLibraryJob?.cancel()
        saveLibraryJob = scope.launch {
            delay(300)
            platform.store.put("library.json", json.encodeToString(ListSerializer(LibraryItem.serializer()), library.toList()).encodeToByteArray())
        }
    }

    private fun persistApps() {
        scope.launch { platform.store.put("apps.json", json.encodeToString(ListSerializer(HtmlApp.serializer()), apps.toList()).encodeToByteArray()) }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        settings = transform(settings)
        scope.launch { platform.store.put("settings.json", json.encodeToString(Settings.serializer(), settings).encodeToByteArray()) }
    }

    // ---------------------------------------------------------------------------------------
    // Navigation & feedback
    // ---------------------------------------------------------------------------------------

    fun navigate(target: Screen) { screen = target }

    fun toast(text: String, error: Boolean = false, action: String? = null, onAction: (() -> Unit)? = null) {
        toasts.tryEmit(Toast(text, error, action, onAction))
    }

    private suspend fun <T> withBusy(message: String, block: suspend () -> T): T? {
        busy = message
        delay(32) // let the overlay paint before heavy, single-threaded work (web)
        return try {
            block()
        } catch (e: Throwable) {
            toast(e.message ?: "Something went wrong", error = true)
            null
        } finally {
            busy = null
        }
    }

    // ---------------------------------------------------------------------------------------
    // Opening content
    // ---------------------------------------------------------------------------------------

    fun pickAndOpen() = scope.launch {
        val files = runCatching { platform.pickFiles(multiple = true) }.getOrElse { toast(it.message ?: "Could not open file", true); emptyList() }
        for (f in files) openInputs(f.name, listOf(InputFile(f.name, f.bytes)), f.path)
    }

    fun pickFolderAndOpen() = scope.launch {
        val folder = runCatching { platform.pickFolder() }.getOrElse { toast(it.message ?: "Could not open folder", true); null } ?: return@launch
        openFolder(folder)
    }

    fun openFolder(folder: PickedFolder) = scope.launch { openInputs(folder.name, folder.files, folder.path) }

    fun openPaths(paths: List<String>) = scope.launch {
        for (p in paths) {
            val picked = platform.reopen(p) ?: continue
            openInputs(picked.name, picked.files, picked.path)
        }
    }

    suspend fun openInputs(name: String, inputs: List<InputFile>, sourcePath: String?, forcedMode: SandboxMode? = null) {
        if (inputs.isEmpty()) { toast("That folder has no files", true); return }
        withBusy("Opening $name…") {
            val bundle = BundleLoader.load(name, inputs)
            openBundle(bundle, sourcePath, forcedMode, fileName = name)
        }
    }

    private suspend fun openBundle(bundle: Bundle, sourcePath: String?, forcedMode: SandboxMode?, fileName: String, keepItem: LibraryItem? = null) {
        val htmlBytes = bundle.htmlFiles.sumOf { it.size.toLong() }
        val quickScan = if (htmlBytes < 4_000_000) runCatching { ThreatScanner.scan(bundle) }.getOrNull() else null
        val export = runCatching { ExportDetector.detect(bundle) }.getOrNull()
        val existing = keepItem ?: library.firstOrNull { sourcePath != null && it.sourcePath == sourcePath }
            ?: library.firstOrNull { it.fileName == fileName && it.sizeBytes == bundle.totalSize && it.kind == bundle.kind }
        val defaultMode = when {
            bundle.kind == BundleKind.EML -> SandboxMode.SAFE
            bundle.kind == BundleKind.PASTE -> settings.defaultPasteMode
            else -> settings.defaultFileMode
        }
        val dangerous = quickScan?.verdict == Verdict.DANGEROUS
        val mode = forcedMode ?: when {
            dangerous && settings.autoSafeOnThreat -> SandboxMode.SAFE
            existing != null -> existing.mode
            else -> defaultMode
        }
        val now = currentTimeMillis()
        val title = PageAnalyzer.title(bundle.files[bundle.entry]?.let { Charsets.decode(it.bytes.copyOfRange(0, minOf(it.size, 65536))) } ?: "")
            ?.takeIf { bundle.kind != BundleKind.EML } ?: bundle.name
        val item = (existing ?: LibraryItem(
            id = randomId(), title = title, fileName = fileName, kind = bundle.kind, sizeBytes = bundle.totalSize,
            fileCount = bundle.files.size, createdAt = now, lastOpenedAt = now, openCount = 0,
        )).copy(
            title = if (existing != null && existing.title != existing.fileName) existing.title else title,
            sizeBytes = bundle.totalSize, fileCount = bundle.files.size, lastOpenedAt = now,
            openCount = (existing?.openCount ?: 0) + 1, sourcePath = sourcePath ?: existing?.sourcePath,
            mode = mode, verdict = quickScan?.verdict ?: existing?.verdict, exportLabel = export?.label,
        )
        library.removeAll { it.id == item.id }
        library.add(0, item)
        persistLibrary()

        val storeCopy = settings.keepCopies && (sourcePath == null || platform.kind == PlatformKind.WEB || bundle.totalSize < 25_000_000)
        if (storeCopy && bundle.totalSize < settings.maxStoredMb * 1_000_000L) {
            scope.launch { runCatching { platform.store.put("bundle/${item.id}", BundleCodec.encode(bundle)) }; refreshUsage() }
        }
        scope.launch {
            val text = runCatching { TextExtractor.extractBundle(bundle) }.getOrDefault("")
            texts[item.id] = text
            runCatching { platform.store.put("text/${item.id}", text.encodeToByteArray()) }
        }

        val session = ViewSession(item.id, bundle, mode, item.scrollY)
        val tab = ViewerTab(randomId(), session, item.id, null, item.sourcePath)
        tab.title = item.title
        tab.scan = quickScan
        tab.export = export
        configureTab(tab)
        if (quickScan == null) scanAsync(tab)
        if (dangerous) {
            tab.right = RightPanel.SECURITY
            toast("Warning: this file looks dangerous. It was opened in Safe mode.", error = true)
        }
        showTab(tab)
    }

    private fun configureTab(tab: ViewerTab) {
        val bundle = tab.session.bundle
        tab.info = runCatching { PageAnalyzer.analyze(bundle) }.getOrNull()
        if (bundle.kind == BundleKind.EML) {
            tab.left = if (bundle.attachments.isNotEmpty()) LeftPanel.ATTACHMENTS else null
        } else if (tab.export?.searchable == true && bundle.htmlFiles.size > 1) {
            tab.left = LeftPanel.SEARCH
        } else if (bundle.isMultiFile && bundle.kind in setOf(BundleKind.ZIP, BundleKind.FOLDER)) {
            tab.left = LeftPanel.FILES
        }
        val s = tab.session
        s.onOpenExternal = { url -> if (s.mode == SandboxMode.FULL && !url.startsWith("mailto:")) platform.openUrl(url) else dialog = Dialog.ExternalLink(url) }
        s.onScroll = { y -> tab.libraryId?.let { id -> updateItem(id) { it.copy(scrollY = y) } } }
        s.onSnap = { bytes, error ->
            if (bytes != null) scope.launch {
                platform.saveFile(Paths.stripExtension(Paths.sanitizeSegment(tab.title)) + ".png", "image/png", bytes)?.let { toast("Screenshot saved") }
            } else toast(error ?: "Screenshot failed", true)
        }
        s.onDownload = { name, bytes -> scope.launch { platform.saveFile(name, "application/octet-stream", bytes)?.let { toast("Saved $name") } } }
        s.onPdf = { path, ok -> if (ok) toast("PDF saved", action = "Show") { platform.reveal(path) } else toast("Could not create the PDF", true) }
        startWatching(tab)
    }

    private fun scanAsync(tab: ViewerTab) {
        tab.scanning = true
        scope.launch {
            delay(150)
            val report = runCatching { ThreatScanner.scan(tab.session.bundle) }.getOrNull()
            tab.scan = report
            tab.scanning = false
            tab.libraryId?.let { id -> updateItem(id) { it.copy(verdict = report?.verdict) } }
        }
    }

    fun rescan(tab: ViewerTab) = scanAsync(tab)

    private fun showTab(tab: ViewerTab) {
        tabs.add(tab)
        activeTab = tab
        screen = Screen.Viewer
    }

    fun activate(tab: ViewerTab) { activeTab = tab; screen = Screen.Viewer }

    fun closeTab(tab: ViewerTab) {
        tab.watchJob?.cancel()
        val idx = tabs.indexOf(tab)
        tabs.remove(tab)
        if (activeTab == tab) activeTab = tabs.getOrNull(idx.coerceAtMost(tabs.size - 1))
        if (tabs.isEmpty()) screen = Screen.Home
    }

    fun openLibraryItem(item: LibraryItem, mode: SandboxMode? = null) = scope.launch {
        tabs.firstOrNull { it.libraryId == item.id }?.let { existing ->
            if (mode != null) existing.session.changeMode(mode)
            activate(existing)
            return@launch
        }
        withBusy("Opening ${item.title}…") {
            val bundle = loadItemBundle(item) ?: run {
                dialog = Dialog.Confirm(
                    "File not found", "“${item.fileName}” is no longer available${item.sourcePath?.let { " at $it" } ?: ""}. Remove it from your library?",
                    "Remove", danger = true,
                ) { deleteItem(item) }
                return@withBusy
            }
            openBundle(bundle, item.sourcePath, mode, item.fileName, keepItem = item)
        }
    }

    private suspend fun loadItemBundle(item: LibraryItem): Bundle? {
        item.sourcePath?.let { path ->
            platform.reopen(path)?.let { picked -> return runCatching { BundleLoader.load(picked.name, picked.files) }.getOrNull() }
        }
        return platform.store.get("bundle/${item.id}")?.let { runCatching { BundleCodec.decode(it) }.getOrNull() }
    }

    fun openPasted(html: String, name: String? = null) = scope.launch {
        if (html.isBlank()) { toast("Nothing to open — the text is empty", true); return@launch }
        withBusy("Opening…") {
            val title = name ?: PageAnalyzer.title(html) ?: "Pasted HTML"
            val bundle = Bundle.single(title, html, BundleKind.PASTE)
            openBundle(bundle, null, null, fileName = "$title.html")
        }
    }

    fun openSample(which: String) = scope.launch {
        when (which) {
            "welcome" -> openInputs("welcome.html", listOf(InputFile("welcome.html", Samples.welcome.encodeToByteArray())), null)
            "phishing" -> openInputs("Invoice_2026_shared.html", listOf(InputFile("Invoice_2026_shared.html", Samples.phishingDemo.encodeToByteArray())), null)
            "email" -> openInputs("weekly-report.eml", listOf(InputFile("weekly-report.eml", Samples.emailDemo.encodeToByteArray())), null)
        }
    }

    fun pasteFromClipboard() = scope.launch {
        handlePastedText(platform.readClipboard())
    }

    /** Paste shortcut on Home: HTML opens straight away, anything else lands in the paste box. */
    fun handlePastedText(text: String?) {
        when {
            text.isNullOrBlank() -> dialog = Dialog.Paste
            looksLikeHtml(text) -> openPasted(text)
            else -> { pendingPaste = text; dialog = Dialog.Paste }
        }
    }

    /** True when a global paste should open content (Home screen, no dialog open). */
    val acceptsGlobalPaste: Boolean get() = screen == Screen.Home && dialog == null && busy == null

    fun looksLikeHtml(text: String): Boolean {
        val t = text.trimStart().take(2000).lowercase()
        return t.startsWith("<!doctype") || t.startsWith("<html") || Regex("<(div|body|head|style|script|svg|p|h1|table|section|main|span|a)[\\s>]").containsMatchIn(t)
    }

    /** Opens a share link fragment (#name=…&code=…) if present. */
    fun openShareFragment(fragment: String): Boolean {
        val parsed = ShareLink.parseFragment(fragment) ?: return false
        scope.launch {
            withBusy("Opening shared page…") {
                val name = parsed.first ?: PageAnalyzer.title(parsed.second) ?: "Shared page"
                val bundle = Bundle.single(name, parsed.second, BundleKind.PASTE)
                openBundle(bundle, null, SandboxMode.OFFLINE, "$name.html")
                toast("Opened a shared page in Offline mode. Switch to Full if it needs the internet.")
            }
        }
        return true
    }

    private fun startWatching(tab: ViewerTab) {
        val path = tab.sourcePath ?: return
        if (platform.kind != PlatformKind.DESKTOP) return
        tab.watchJob?.cancel()
        tab.watchJob = scope.launch {
            var last = platform.lastModified(path) ?: return@launch
            while (isActive) {
                delay(1200)
                if (!tab.liveReload) continue
                val now = platform.lastModified(path) ?: continue
                if (now != last) {
                    last = now
                    val picked = platform.reopen(path) ?: continue
                    val bundle = runCatching { BundleLoader.load(picked.name, picked.files) }.getOrNull() ?: continue
                    tab.session.replaceBundle(bundle, keepPath = true)
                    tab.info = runCatching { PageAnalyzer.analyze(bundle) }.getOrNull()
                    tab.sourceEditor?.let { ed -> sourceText(tab)?.let { ed.setText(it, keepCursor = true) } }
                    scanAsync(tab)
                }
            }
        }
    }

    fun sourceText(tab: ViewerTab): String? {
        val b = tab.session.bundle
        val f = b.resolve(tab.session.currentPath.substringBefore('#')) ?: b.files[b.entry] ?: return null
        if (!(f.isHtml || f.mime.startsWith("text/") || f.mime.contains("javascript") || f.mime.contains("svg") || f.mime.contains("json"))) return null
        return Charsets.decode(f.bytes, f.mime.substringAfter("charset=", "").ifEmpty { null })
    }

    // ---------------------------------------------------------------------------------------
    // Library management
    // ---------------------------------------------------------------------------------------

    fun updateItem(id: String, transform: (LibraryItem) -> LibraryItem) {
        val i = library.indexOfFirst { it.id == id }
        if (i < 0) return
        library[i] = transform(library[i])
        persistLibrary()
    }

    fun toggleFavorite(item: LibraryItem) = updateItem(item.id) { it.copy(favorite = !it.favorite) }

    fun renameItem(item: LibraryItem) {
        dialog = Dialog.Input("Rename", "Name", item.title, "Rename") { name ->
            if (name.isNotBlank()) {
                updateItem(item.id) { it.copy(title = name.trim()) }
                tabs.filter { it.libraryId == item.id }.forEach { it.title = name.trim() }
            }
        }
    }

    fun deleteItem(item: LibraryItem) {
        library.removeAll { it.id == item.id }
        texts.remove(item.id)
        persistLibrary()
        scope.launch {
            platform.store.delete("bundle/${item.id}")
            platform.store.delete("text/${item.id}")
            refreshUsage()
        }
    }

    fun clearLibrary() {
        dialog = Dialog.Confirm("Clear library?", "This removes every recent file and stored copy from HTML Lens. Your original files are not touched.", "Clear", danger = true) {
            val ids = library.map { it.id }
            library.clear()
            texts.clear()
            persistLibrary()
            scope.launch {
                platform.store.deletePrefix("bundle/")
                platform.store.deletePrefix("text/")
                ids.size
                refreshUsage()
            }
        }
    }

    suspend fun searchLibrary(query: String): List<LibrarySearch.Hit> {
        if (query.trim().length < 2) return emptyList()
        for (item in library) {
            if (item.id !in texts) texts[item.id] = platform.store.get("text/${item.id}")?.decodeToString() ?: ""
        }
        val docs = library.map { LibrarySearch.Doc(it.id, it.title + " " + it.fileName, texts[it.id] ?: "") }
        return LibrarySearch.search(query, docs)
    }

    // ---------------------------------------------------------------------------------------
    // Exports
    // ---------------------------------------------------------------------------------------

    private fun baseName(tab: ViewerTab) = Paths.stripExtension(Paths.sanitizeSegment(tab.title, 60))

    fun exportPacked(tab: ViewerTab) = scope.launch {
        val result = withBusy("Packing into one file…") { Packer.pack(tab.session.bundle) } ?: return@launch
        val saved = platform.saveFile(baseName(tab) + ".html", "text/html", result.html.encodeToByteArray()) ?: return@launch
        val note = if (result.missing.isNotEmpty()) " (${result.missing.size} missing reference${if (result.missing.size > 1) "s" else ""})" else ""
        toast("Saved ${formatBytes(result.sizeBytes.toLong())} single-file HTML$note", action = if (platform.kind == PlatformKind.DESKTOP) "Show" else null) { platform.reveal(saved) }
    }

    fun exportZip(tab: ViewerTab) = scope.launch {
        val bytes = withBusy("Creating ZIP…") { ZipWriter.write(tab.session.bundle.files.values.map { it.path to it.bytes }) } ?: return@launch
        platform.saveFile(baseName(tab) + ".zip", "application/zip", bytes)?.let { toast("ZIP saved") }
    }

    fun exportPdf(tab: ViewerTab) = scope.launch {
        val path = platform.chooseSavePath(baseName(tab) + ".pdf")
        if (path != null) tab.session.send(ViewerCommand.Pdf(path))
        else {
            toast("Choose “Save as PDF” in the print dialog.")
            tab.session.send(ViewerCommand.Print)
        }
    }

    fun screenshot(tab: ViewerTab) {
        toast("Capturing full-page screenshot…")
        tab.session.send(ViewerCommand.Snap)
    }

    fun downloadAttachment(name: String, mime: String, bytes: ByteArray) = scope.launch {
        platform.saveFile(name, mime, bytes)?.let { toast("Saved $name") }
    }

    fun copyShareLink(html: String, name: String?) = scope.launch {
        val url = ShareLink.buildUrl(platform.appUrl, html, name)
        when {
            url.length > ShareLink.HARD_MAX -> toast("This page is too large for a link (${formatBytes(url.length.toLong())}). Export it as a file instead.", true)
            else -> {
                platform.writeClipboard(url)
                toast(if (url.length > ShareLink.RECOMMENDED_MAX) "Link copied (${url.length / 1000}k characters — some chat apps may cut it off)" else "Share link copied — anyone with it can open this page")
            }
        }
    }

    fun shareTab(tab: ViewerTab) = scope.launch {
        val b = tab.session.bundle
        val html = if (b.isMultiFile) runCatching { Packer.pack(b).html }.getOrNull() else b.files[b.entry]?.let { Charsets.decode(it.bytes) }
        if (html == null) { toast("Could not prepare this page for sharing", true); return@launch }
        copyShareLink(html, tab.title)
    }

    // ---------------------------------------------------------------------------------------
    // HTML apps
    // ---------------------------------------------------------------------------------------

    fun requestSaveApp(html: String, suggestedName: String, existingId: String?) {
        dialog = Dialog.SaveApp(html, suggestedName, existingId)
    }

    fun saveNewApp(name: String, icon: String, color: Int, description: String, html: String, mode: SandboxMode): HtmlApp {
        val now = currentTimeMillis()
        val v = AppVersion(randomId(8), now, html.length, "Created")
        val app = HtmlApp(randomId(), name.ifBlank { "Untitled app" }, icon, color, now, now, listOf(v), v.id, mode = mode, description = description)
        apps.add(0, app)
        persistApps()
        scope.launch { platform.store.put("app/${app.id}/${v.id}", html.encodeToByteArray()); refreshUsage() }
        toast("Saved “${app.name}” to your apps", action = "Open") { runApp(app) }
        return app
    }

    fun addVersion(appId: String, html: String, note: String = "Edited"): HtmlApp? {
        val i = apps.indexOfFirst { it.id == appId }
        if (i < 0) return null
        val now = currentTimeMillis()
        val v = AppVersion(randomId(8), now, html.length, note)
        val updated = apps[i].copy(versions = (apps[i].versions + v).takeLast(50), currentVersionId = v.id, updatedAt = now)
        apps[i] = updated
        persistApps()
        scope.launch { platform.store.put("app/$appId/${v.id}", html.encodeToByteArray()) }
        return updated
    }

    fun updateApp(appId: String, transform: (HtmlApp) -> HtmlApp) {
        val i = apps.indexOfFirst { it.id == appId }
        if (i < 0) return
        apps[i] = transform(apps[i])
        persistApps()
    }

    suspend fun appHtml(app: HtmlApp, versionId: String = app.currentVersionId): String? =
        platform.store.get("app/${app.id}/$versionId")?.decodeToString()

    fun runApp(app: HtmlApp) = scope.launch {
        tabs.firstOrNull { it.appId == app.id }?.let { activate(it); it.session.reload(); return@launch }
        val html = appHtml(app) ?: run { toast("This app's content is missing", true); return@launch }
        val bundle = Bundle.single(app.name, html, BundleKind.APP)
        val session = ViewSession(app.id, bundle, app.mode)
        val tab = ViewerTab(randomId(), session, null, app.id, null)
        tab.title = app.name
        configureTab(tab)
        scanAsync(tab)
        updateApp(app.id) { it.copy(runCount = it.runCount + 1, lastRunAt = currentTimeMillis()) }
        showTab(tab)
    }

    fun editApp(app: HtmlApp) = scope.launch {
        val html = appHtml(app) ?: return@launch
        playground.load(html, app.name, app.id)
        screen = Screen.Playground
    }

    fun duplicateApp(app: HtmlApp) = scope.launch {
        val html = appHtml(app) ?: return@launch
        saveNewApp(app.name + " copy", app.icon, app.color, app.description, html, app.mode)
    }

    fun deleteApp(app: HtmlApp) {
        dialog = Dialog.Confirm("Delete “${app.name}”?", "The app and all ${app.versions.size} version(s) will be removed.", "Delete", danger = true) {
            apps.removeAll { it.id == app.id }
            tabs.filter { it.appId == app.id }.forEach { closeTab(it) }
            persistApps()
            scope.launch { platform.store.deletePrefix("app/${app.id}/"); refreshUsage() }
        }
    }

    fun exportApp(app: HtmlApp) = scope.launch {
        val html = appHtml(app) ?: return@launch
        platform.saveFile(Paths.sanitizeSegment(app.name) + ".html", "text/html", html.encodeToByteArray())?.let { toast("Exported ${app.name}") }
    }

    fun shareApp(app: HtmlApp) = scope.launch { appHtml(app)?.let { copyShareLink(it, app.name) } }

    fun restoreVersion(app: HtmlApp, version: AppVersion) {
        updateApp(app.id) { it.copy(currentVersionId = version.id, updatedAt = currentTimeMillis()) }
        tabs.filter { it.appId == app.id }.forEach { tab ->
            scope.launch { appHtml(app, version.id)?.let { tab.session.replaceBundle(Bundle.single(app.name, it, BundleKind.APP)) } }
        }
        toast("Restored version from ${relativeTime(version.createdAt)}")
    }

    fun saveTabAsApp(tab: ViewerTab) = scope.launch {
        val b = tab.session.bundle
        val html = if (b.isMultiFile) runCatching { Packer.pack(b).html }.getOrNull() else b.files[b.entry]?.let { Charsets.decode(it.bytes) }
        if (html == null) { toast("Could not read this page", true); return@launch }
        requestSaveApp(html, tab.title, tab.appId)
    }

    fun editTabSource(tab: ViewerTab) {
        val html = sourceText(tab) ?: return toast("This page can't be edited", true)
        playground.load(html, tab.title, tab.appId)
        screen = Screen.Playground
    }

    fun newPlayground(template: String? = null) {
        val html = template ?: Samples.templates.first().second
        playground.load(html, "Untitled", null)
        screen = Screen.Playground
    }

    fun savePlaygroundDraft() = scope.launch {
        platform.store.put("playground/draft.html", playground.editor.text.encodeToByteArray())
        platform.store.put("playground/meta.json", playground.metaJson().encodeToByteArray())
    }

    fun savePlayground() {
        val p = playground
        val html = p.editor.text
        val id = p.appId
        if (id != null && apps.any { it.id == id }) {
            addVersion(id, html)
            updateApp(id) { it.copy(name = p.name.ifBlank { it.name }) }
            p.dirty = false
            toast("Saved a new version of “${p.name}”")
        } else {
            requestSaveApp(html, p.name.takeIf { it != "Untitled" } ?: PageAnalyzer.title(html) ?: "My app", null)
        }
    }

    fun suggestIcon(name: String, html: String) = AppIcons.guess(name + " " + (PageAnalyzer.title(html) ?: ""))

    // ---------------------------------------------------------------------------------------
    // Tools helpers
    // ---------------------------------------------------------------------------------------

    suspend fun pickBundle(): Bundle? {
        val files = runCatching { platform.pickFiles(multiple = false) }.getOrElse { toast(it.message ?: "Could not read file", true); emptyList() }
        val f = files.firstOrNull() ?: return null
        return withBusy("Reading ${f.name}…") { BundleLoader.loadSingle(f.name, f.bytes) }
    }

    suspend fun pickFolderBundle(): Bundle? {
        val folder = platform.pickFolder() ?: return null
        return withBusy("Reading ${folder.name}…") { BundleLoader.load(folder.name, folder.files) }
    }

    fun emailChecks(html: String): List<EmailCheck> = runCatching { EmailLint.lint(html) }.getOrDefault(emptyList())

    fun openEmailPreview(html: String, name: String = "Email preview") = scope.launch {
        withBusy("Preparing preview…") {
            val bundle = Bundle.single(name, html, BundleKind.PASTE)
            val session = ViewSession(randomId(), bundle, SandboxMode.SAFE)
            val tab = ViewerTab(randomId(), session, null, null, null)
            tab.title = name
            tab.device = Device.EMAIL_DESKTOP
            tab.emailChecks = emailChecks(html)
            tab.right = RightPanel.EMAIL
            configureTab(tab)
            scanAsync(tab)
            showTab(tab)
        }
    }

    fun openBundleInViewer(bundle: Bundle, mode: SandboxMode? = null) = scope.launch {
        withBusy("Opening ${bundle.name}…") { openBundle(bundle, null, mode, bundle.name) }
    }

    fun saveText(name: String, mime: String, text: String) = scope.launch {
        platform.saveFile(name, mime, text.encodeToByteArray())?.let { toast("Saved $name") }
    }

    fun copy(text: String, message: String = "Copied to clipboard") {
        platform.writeClipboard(text)
        toast(message)
    }
}

fun relativeTime(epochMs: Long): String {
    val diff = (currentTimeMillis() - epochMs) / 1000
    return when {
        diff < 45 -> "just now"
        diff < 3600 -> "${diff / 60} min ago"
        diff < 86_400 -> "${diff / 3600} h ago"
        diff < 7 * 86_400 -> "${diff / 86_400} d ago"
        diff < 60 * 86_400 -> "${diff / (7 * 86_400)} wk ago"
        else -> "${diff / (30 * 86_400)} mo ago"
    }
}
