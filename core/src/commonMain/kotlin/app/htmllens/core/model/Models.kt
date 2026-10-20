package app.htmllens.core.model

import app.htmllens.core.analysis.Verdict
import app.htmllens.core.bundle.BundleKind
import app.htmllens.core.serve.SandboxMode
import kotlinx.serialization.Serializable

@Serializable
data class LibraryItem(
    val id: String,
    val title: String,
    val fileName: String,
    val kind: BundleKind,
    val sizeBytes: Long,
    val fileCount: Int,
    val createdAt: Long,
    val lastOpenedAt: Long,
    val openCount: Int = 1,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    /** Desktop: original location on disk, re-read on open so edits show up. */
    val sourcePath: String? = null,
    val stored: Boolean = true,
    val mode: SandboxMode = SandboxMode.OFFLINE,
    val scrollY: Int = 0,
    val verdict: Verdict? = null,
    val exportLabel: String? = null,
)

@Serializable
data class AppVersion(
    val id: String,
    val createdAt: Long,
    val size: Int,
    val note: String = "",
)

@Serializable
data class HtmlApp(
    val id: String,
    val name: String,
    val icon: String,
    val color: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val versions: List<AppVersion>,
    val currentVersionId: String,
    val runCount: Int = 0,
    val lastRunAt: Long = 0,
    val pinned: Boolean = false,
    val mode: SandboxMode = SandboxMode.FULL,
    val description: String = "",
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultFileMode: SandboxMode = SandboxMode.OFFLINE,
    val defaultPasteMode: SandboxMode = SandboxMode.FULL,
    val autoSafeOnThreat: Boolean = true,
    val keepCopies: Boolean = true,
    val editorFontSize: Int = 14,
    val editorAutoRun: Boolean = true,
    val editorWrap: Boolean = true,
    val showWelcome: Boolean = true,
    val clipboardSuggest: Boolean = true,
    val maxStoredMb: Int = 300,
)
