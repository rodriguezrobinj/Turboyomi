package mihon.desktop.app.platform

import com.sun.jna.platform.win32.WinBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScreenKeeperTest {
    @Test
    fun `keeping the screen on asks Windows for the display, and letting go clears it`() {
        ScreenKeeper.keepOn(true)
        val whileReading = ScreenKeeper.keepOn(false)
        (whileReading and WinBase.ES_DISPLAY_REQUIRED != 0) shouldBe true
        val after = ScreenKeeper.keepOn(false)
        (after and WinBase.ES_DISPLAY_REQUIRED) shouldBe 0
    }
}
