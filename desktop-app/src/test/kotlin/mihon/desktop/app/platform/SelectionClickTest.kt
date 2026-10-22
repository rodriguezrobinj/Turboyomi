package mihon.desktop.app.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SelectionClickTest {
    @Test
    fun `outside selection mode a click opens, even with shift`() {
        selectionClick(shift = false, selecting = false) shouldBe SelectionClick.Open
        selectionClick(shift = true, selecting = false) shouldBe SelectionClick.Open
    }

    @Test
    fun `in selection mode a click toggles and shift+click selects the range`() {
        selectionClick(shift = false, selecting = true) shouldBe SelectionClick.Toggle
        selectionClick(shift = true, selecting = true) shouldBe SelectionClick.Range
    }
}
