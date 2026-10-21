package mihon.desktop.app.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

/** Mihon's imageScaleType values the desktop reader supports (spec deviation 6); the others read as fit screen. */
enum class ImageScale(val prefValue: Int, val contentScale: ContentScale, val label: StringResource) {
    FIT_SCREEN(1, ContentScale.Fit, MR.strings.scale_type_fit_screen),
    FIT_WIDTH(3, ContentScale.FillWidth, MR.strings.scale_type_fit_width),
    FIT_HEIGHT(4, ContentScale.FillHeight, MR.strings.scale_type_fit_height),
    ORIGINAL(5, ContentScale.None, MR.strings.scale_type_original_size),
    ;

    companion object {
        fun fromPreference(value: Int): ImageScale = entries.find { it.prefValue == value } ?: FIT_SCREEN
    }
}

/** Mihon's readerTheme values: the colour behind the pages. */
enum class ReaderBackground(val prefValue: Int, val label: StringResource) {
    WHITE(0, MR.strings.white_background),
    BLACK(1, MR.strings.black_background),
    GRAY(2, MR.strings.gray_background),
    AUTOMATIC(3, MR.strings.automatic_background),
    ;

    @Composable
    fun color(): Color = when (this) {
        WHITE -> Color.White
        BLACK -> Color.Black
        GRAY -> Color(0xFF202125)
        AUTOMATIC -> MaterialTheme.colorScheme.background
    }

    companion object {
        fun fromPreference(value: Int): ReaderBackground = entries.find { it.prefValue == value } ?: BLACK
    }
}
