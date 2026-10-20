package app.htmllens.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun buildLogo(square: Color, ring: Color, handle: Color): ImageVector =
    ImageVector.Builder("LensLogo", 64.dp, 64.dp, 512f, 512f).apply {
        path(fill = SolidColor(square)) {
            moveTo(120f, 20f); lineTo(392f, 20f)
            curveTo(447f, 20f, 492f, 65f, 492f, 120f); lineTo(492f, 392f)
            curveTo(492f, 447f, 447f, 492f, 392f, 492f); lineTo(120f, 492f)
            curveTo(65f, 492f, 20f, 447f, 20f, 392f); lineTo(20f, 120f)
            curveTo(20f, 65f, 65f, 20f, 120f, 20f); close()
        }
        path(stroke = SolidColor(ring), strokeLineWidth = 36f) {
            moveTo(328f, 226f)
            arcTo(102f, 102f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 124f, y1 = 226f)
            arcTo(102f, 102f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 328f, y1 = 226f)
            close()
        }
        path(stroke = SolidColor(handle), strokeLineWidth = 44f, strokeLineCap = StrokeCap.Round) {
            moveTo(303f, 303f); lineTo(386f, 386f)
        }
    }.build()

/** The HTML Lens mark (branding/logo.svg). */
val LensLogo: ImageVector by lazy { buildLogo(Color(0xFF1E1A15), Color(0xFFF6F1E7), Color(0xFFE07A52)) }
val LensLogoDark: ImageVector by lazy { buildLogo(Color(0xFFEEE7DA), Color(0xFF1B1915), Color(0xFFC2552D)) }
