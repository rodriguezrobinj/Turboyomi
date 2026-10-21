package mihon.desktop.app.platform

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WindowControlsTest {
    @Test
    fun `fullscreen toggles and goes back to the placement it came from`() {
        val controls = WindowControls(WindowState(placement = WindowPlacement.Maximized))
        controls.toggleFullscreen()
        controls.isFullscreen shouldBe true
        controls.toggleFullscreen()
        controls.isFullscreen shouldBe false
        controls.placementToSave shouldBe WindowPlacement.Maximized
    }

    @Test
    fun `closing in fullscreen saves the placement before it, never fullscreen`() {
        val controls = WindowControls(WindowState(placement = WindowPlacement.Floating))
        controls.toggleFullscreen()
        controls.placementToSave shouldBe WindowPlacement.Floating
    }

    @Test
    fun `closing in fullscreen saves the size and position from before it`() {
        val state = WindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(100.dp, 50.dp),
            size = DpSize(1280.dp, 800.dp),
        )
        val controls = WindowControls(state)
        controls.toggleFullscreen()
        // What the window reports once it covers the screen
        state.position = WindowPosition(0.dp, 0.dp)
        state.size = DpSize(1920.dp, 1080.dp)

        controls.positionToSave shouldBe WindowPosition(100.dp, 50.dp)
        controls.sizeToSave shouldBe DpSize(1280.dp, 800.dp)
    }
}
