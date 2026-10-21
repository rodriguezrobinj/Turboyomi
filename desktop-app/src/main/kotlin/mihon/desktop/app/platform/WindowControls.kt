package mihon.desktop.app.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

/** The window's real fullscreen (spec 2a §4: F11), for the reader. */
class WindowControls(private val state: WindowState) {
    private var placementBefore = state.placement

    val isFullscreen: Boolean
        get() = state.placement == WindowPlacement.Fullscreen

    fun toggleFullscreen() {
        if (isFullscreen) {
            state.placement = placementBefore
        } else {
            placementBefore = state.placement
            state.placement = WindowPlacement.Fullscreen
        }
    }

    fun exitFullscreen() {
        if (isFullscreen) toggleFullscreen()
    }

    /** What to remember when the window closes: never fullscreen, which only the reader turns on. */
    val placementToSave: WindowPlacement
        get() = if (isFullscreen) placementBefore else state.placement
}

/** Null outside a window (previews and tests that don't need fullscreen). */
val LocalWindowControls: ProvidableCompositionLocal<WindowControls?> = staticCompositionLocalOf { null }
