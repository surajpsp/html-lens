package app.htmllens.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.htmllens.desktop.engine.CodeEditorFx
import app.htmllens.desktop.engine.HtmlViewFx

@Composable
actual fun HtmlView(session: ViewSession, modifier: Modifier, primary: Boolean, zoom: Float) =
    HtmlViewFx(session, modifier, primary, zoom)

@Composable
actual fun CodeEditor(state: CodeEditorState, options: EditorOptions, modifier: Modifier) =
    CodeEditorFx(state, options, modifier)

actual val engineSurfacesAreHeavyweight: Boolean = true
