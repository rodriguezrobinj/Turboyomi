package mihon.desktop.app.startup

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.Rectangle

class WindowStateStoreTest {
    private val primary = Rectangle(0, 0, 1920, 1080)
    private val secondary = Rectangle(1920, 0, 2560, 1440)

    @Test
    fun `a window that fits on a screen keeps its place`() {
        val saved = SavedWindow(x = 2000, y = 100, width = 1400, height = 900, maximized = true)
        placeOnScreens(saved, listOf(primary, secondary)) shouldBe saved
    }

    @Test
    fun `a window on a screen that was unplugged moves to the primary one`() {
        val saved = SavedWindow(x = 2000, y = 100, width = 1400, height = 900, maximized = false)
        placeOnScreens(saved, listOf(primary)) shouldBe SavedWindow(320, 140, 1280, 800, false)
    }

    @Test
    fun `nothing saved opens centered at the default size`() {
        placeOnScreens(null, listOf(primary)) shouldBe SavedWindow(320, 140, 1280, 800, false)
    }

    @Test
    fun `a window smaller than the minimum grows to it`() {
        val saved = SavedWindow(x = 10, y = 10, width = 300, height = 200, maximized = false)
        placeOnScreens(saved, listOf(primary)) shouldBe SavedWindow(10, 10, 800, 600, false)
    }

    @Test
    fun `the default size shrinks to fit a small screen`() {
        val laptop = Rectangle(0, 0, 1366, 768)
        placeOnScreens(null, listOf(laptop)) shouldBe SavedWindow(43, 0, 1280, 768, false)
    }

    @Test
    fun `a saved window taller than its screen's work area shrinks to it`() {
        val workArea = Rectangle(0, 0, 1280, 728)
        val saved = SavedWindow(x = 0, y = 0, width = 1280, height = 768, maximized = false)
        placeOnScreens(saved, listOf(workArea)) shouldBe SavedWindow(0, 0, 1280, 728, false)
    }
}
