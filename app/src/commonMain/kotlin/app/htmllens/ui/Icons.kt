package app.htmllens.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Rocket
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.htmllens.core.bundle.BundleKind

object KindStyle {
    fun icon(kind: BundleKind): ImageVector = when (kind) {
        BundleKind.HTML -> Icons.Rounded.Code
        BundleKind.PASTE -> Icons.Rounded.ContentPaste
        BundleKind.ZIP -> Icons.Rounded.FolderZip
        BundleKind.FOLDER -> Icons.Rounded.Folder
        BundleKind.MHTML -> Icons.Rounded.Archive
        BundleKind.WEBARCHIVE -> Icons.Rounded.Public
        BundleKind.EML -> Icons.Rounded.Email
        BundleKind.SVG -> Icons.Rounded.Image
        BundleKind.APP -> Icons.Rounded.Apps
    }

    fun color(kind: BundleKind): Color = when (kind) {
        BundleKind.HTML -> Color(0xFF6246EA)
        BundleKind.PASTE -> Color(0xFF8B5CF6)
        BundleKind.ZIP -> Color(0xFFF59E0B)
        BundleKind.FOLDER -> Color(0xFF0EA5E9)
        BundleKind.MHTML -> Color(0xFF10B981)
        BundleKind.WEBARCHIVE -> Color(0xFF14B8A6)
        BundleKind.EML -> Color(0xFFEC4899)
        BundleKind.SVG -> Color(0xFFF97316)
        BundleKind.APP -> Color(0xFF6246EA)
    }
}

/** Icons selectable for saved HTML apps (emoji are avoided so tiles render on every platform). */
object AppIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "widgets" to Icons.Rounded.Widgets, "rocket" to Icons.Rounded.Rocket, "spark" to Icons.Rounded.AutoAwesome,
        "game" to Icons.Rounded.Gamepad, "chart" to Icons.Rounded.BarChart, "dashboard" to Icons.Rounded.Dashboard,
        "calc" to Icons.Rounded.Calculate, "todo" to Icons.Rounded.Checklist, "timer" to Icons.Rounded.Timer,
        "calendar" to Icons.Rounded.CalendarMonth, "web" to Icons.Rounded.Web, "doc" to Icons.Rounded.Description,
        "book" to Icons.Rounded.Book, "school" to Icons.Rounded.School, "quiz" to Icons.Rounded.Quiz,
        "music" to Icons.Rounded.MusicNote, "palette" to Icons.Rounded.Palette, "shop" to Icons.Rounded.ShoppingCart,
        "globe" to Icons.Rounded.Language, "code" to Icons.Rounded.Code,
    )

    fun byKey(key: String): ImageVector = all.firstOrNull { it.first == key }?.second ?: Icons.Rounded.Widgets

    /** Guesses a fitting icon from an app's name or content. */
    fun guess(text: String): String {
        val t = text.lowercase()
        return when {
            listOf("game", "snake", "tetris", "pong", "canvas").any { it in t } -> "game"
            listOf("chart", "graph", "plot").any { it in t } -> "chart"
            listOf("dashboard", "kpi", "analytics").any { it in t } -> "dashboard"
            listOf("calculator", "converter", "calc").any { it in t } -> "calc"
            listOf("todo", "task", "checklist").any { it in t } -> "todo"
            listOf("timer", "clock", "pomodoro", "stopwatch").any { it in t } -> "timer"
            listOf("calendar", "schedule", "planner").any { it in t } -> "calendar"
            listOf("quiz", "flashcard", "trivia").any { it in t } -> "quiz"
            listOf("music", "piano", "drum", "synth").any { it in t } -> "music"
            listOf("color", "palette", "draw", "paint").any { it in t } -> "palette"
            listOf("shop", "cart", "store", "invoice").any { it in t } -> "shop"
            listOf("landing", "website", "portfolio").any { it in t } -> "web"
            else -> "spark"
        }
    }
}
