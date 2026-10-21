package mihon.desktop.app.platform

import androidx.compose.ui.window.WindowPlacement
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
}
