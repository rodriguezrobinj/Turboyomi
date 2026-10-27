package mihon.desktop.app.reader

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/** Grayscale and inverted colors for the pages (Mihon's ReaderActivity.getCombinedPaint); null when neither. */
fun readerColorMatrix(grayscale: Boolean, inverted: Boolean): ColorMatrix? {
    if (!grayscale && !inverted) return null
    val gray = ColorMatrix().apply { if (grayscale) setToSaturation(0f) }
    if (!inverted) return gray
    // The inversion after the grayscale, as Mihon's postConcat
    return ColorMatrix(InvertMatrix.copyOf()).apply { timesAssign(gray) }
}

private val InvertMatrix = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

/** The filter the reader puts on its pages; the background, transitions and controls stay as they are. */
val LocalPageColorFilter: ProvidableCompositionLocal<ColorFilter?> = staticCompositionLocalOf { null }
