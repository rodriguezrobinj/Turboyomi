package mihon.desktop.app.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.Frame

/** The whole app's fullscreen (F11, the rail's button and the reader's). */
class WindowControls(private val state: WindowState) {
    /** The app's window, once it shows; without one (tests) fullscreen is only this flag. */
    var window: Frame? = null

    private var saved: BorderlessFullscreen.Saved? = null
    private var placementBefore = state.placement
    private var positionBefore = state.position
    private var sizeBefore = state.size

    var isFullscreen: Boolean by mutableStateOf(false)
        private set

    fun toggleFullscreen() {
        if (isFullscreen) {
            window?.let { w -> saved?.let { BorderlessFullscreen.exit(w, it) } }
            saved = null
        } else {
            placementBefore = state.placement
            positionBefore = state.position
            sizeBefore = state.size
            saved = window?.let(BorderlessFullscreen::enter)
        }
        isFullscreen = !isFullscreen
    }

    /** What to remember when the window closes: never fullscreen, the window opens normal next time. */
    val placementToSave: WindowPlacement
        get() = if (isFullscreen) placementBefore else state.placement

    /** Likewise the window's position and size: in fullscreen they're the screen's. */
    val positionToSave: WindowPosition
        get() = if (isFullscreen) positionBefore else state.position

    val sizeToSave: DpSize
        get() = if (isFullscreen) sizeBefore else state.size
}

/** Null outside a window (previews and tests that don't need fullscreen). */
val LocalWindowControls: ProvidableCompositionLocal<WindowControls?> = staticCompositionLocalOf { null }
