package mihon.desktop.app.main

import androidx.compose.ui.input.key.Key
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShortcutsTest {
    @Test
    fun `desktop shortcuts from the spec`() {
        shortcutFor(Key.Escape, ctrl = false, alt = false, shift = false) shouldBe Shortcut.Back
        shortcutFor(Key.DirectionLeft, ctrl = false, alt = true, shift = false) shouldBe Shortcut.Back
        shortcutFor(Key.F, ctrl = true, alt = false, shift = false) shouldBe Shortcut.GlobalSearch
        shortcutFor(Key.F5, ctrl = false, alt = false, shift = false) shouldBe Shortcut.Refresh
        shortcutFor(Key.Comma, ctrl = true, alt = false, shift = false) shouldBe Shortcut.Settings
    }

    @Test
    fun `plain keys stay with the focused field`() {
        shortcutFor(Key.F, ctrl = false, alt = false, shift = false) shouldBe null
        shortcutFor(Key.DirectionLeft, ctrl = false, alt = false, shift = false) shouldBe null
        shortcutFor(Key.Escape, ctrl = true, alt = false, shift = false) shouldBe null
    }
}
