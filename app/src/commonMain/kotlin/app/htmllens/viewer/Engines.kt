package app.htmllens.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders a [ViewSession] with the platform's web engine: embedded Chromium (CEF) on desktop,
 * a sandboxed cross-origin iframe on the web. [zoom] scales the rendered page (CSS layout width is
 * the view width divided by [zoom]), which is how device frames show a phone layout scaled down.
 * Only the [primary] view of a session reports telemetry and receives commands.
 */
@Composable
expect fun HtmlView(
    session: ViewSession,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    zoom: Float = 1f,
)

/** Embedded CodeMirror 6 editor. */
@Composable
expect fun CodeEditor(
    state: CodeEditorState,
    options: EditorOptions,
    modifier: Modifier = Modifier,
)

/** True when engine surfaces are native windows that Compose cannot draw over (desktop CEF). */
expect val engineSurfacesAreHeavyweight: Boolean
