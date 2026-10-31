package mihon.desktop.app.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TitleBarHitTest {
    private val bar = TitleBarGeometry(height = 32, buttonsStart = 862)

    private fun hit(x: Int, y: Int, maximized: Boolean = false) =
        titleBarHit(x, y, width = 1000, bar = bar, border = 8, maximized = maximized)

    @Test
    fun `the top edge resizes, diagonally at its ends`() {
        hit(500, 0) shouldBe HTTOP
        hit(500, 7) shouldBe HTTOP
        hit(3, 2) shouldBe HTTOPLEFT
        hit(995, 2) shouldBe HTTOPRIGHT
        // Over the buttons too, as on Windows' own bar
        hit(950, 3) shouldBe HTTOP
    }

    @Test
    fun `maximized there's no edge to resize from`() {
        hit(500, 0, maximized = true) shouldBe HTCAPTION
        hit(950, 0, maximized = true) shouldBe HTCLIENT
    }

    @Test
    fun `the bar moves the window and leaves its buttons and the content to the app`() {
        hit(500, 8) shouldBe HTCAPTION
        hit(861, 31) shouldBe HTCAPTION
        hit(862, 20) shouldBe HTCLIENT
        hit(500, 32) shouldBe HTCLIENT
    }

    @Test
    fun `before the bar is laid out only the top edge is Windows'`() {
        titleBarHit(500, 20, width = 1000, bar = null, border = 8, maximized = false) shouldBe HTCLIENT
    }
}
