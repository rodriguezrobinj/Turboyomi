package eu.kanade.presentation.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import kotlin.math.abs

const val BRIGHTNESS_OVERLAY_TAG = "brightness overlay"
const val COLOR_OVERLAY_TAG = "color overlay"

/**
 * Below 0, [brightness] dims the pages with black; [color] goes over them with [colorBlendMode].
 * desktop: Mihon's, without Android's @IntRange and @ColorInt; brightness above 0 does nothing here (spec 8b).
 */
@Composable
fun ReaderContentOverlay(
    brightness: Int,
    color: Int?,
    colorBlendMode: BlendMode?,
    modifier: Modifier = Modifier,
) {
    if (brightness < 0) {
        val brightnessAlpha = remember(brightness) {
            abs(brightness) / 100f
        }

        Canvas(
            modifier = modifier
                .fillMaxSize()
                .testTag(BRIGHTNESS_OVERLAY_TAG)
                .graphicsLayer {
                    alpha = brightnessAlpha
                },
        ) {
            drawRect(Color.Black)
        }
    }

    if (color != null) {
        Canvas(
            modifier = modifier
                .fillMaxSize()
                .testTag(COLOR_OVERLAY_TAG),
        ) {
            drawRect(
                color = Color(color),
                blendMode = colorBlendMode ?: BlendMode.SrcOver,
            )
        }
    }
}
