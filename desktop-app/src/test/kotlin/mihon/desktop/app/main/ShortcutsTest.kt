package mihon.desktop.app.main

import androidx.compose.ui.input.key.Key
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShortcutsTest {
    @Test
    fun `desktop shortcuts from the spec`() {
        // Esc is Compose's own back key: ConnectWindowBack routes it, dialogs first
        shortcutFor(Key.Escape, ctrl = false, alt = false, shift = false) shouldBe null
        shortcutFor(Key.DirectionLeft, ctrl = false, alt = true, shift = false) shouldBe Shortcut.Back
        shortcutFor(Key.F, ctrl = true, alt = false, shift = false) shouldBe Shortcut.GlobalSearch
        shortcutFor(Key.F5, ctrl = false, alt = false, shift = false) shouldBe Shortcut.Refresh
        shortcutFor(Key.F11, ctrl = false, alt = false, shift = false) shouldBe Shortcut.Fullscreen
        shortcutFor(Key.Comma, ctrl = true, alt = false, shift = false) shouldBe Shortcut.Settings
        shortcutFor(Key.F1, ctrl = false, alt = false, shift = false) shouldBe Shortcut.Help
    }

    @Test
    fun `plain keys stay with the focused field`() {
        shortcutFor(Key.F, ctrl = false, alt = false, shift = false) shouldBe null
        shortcutFor(Key.DirectionLeft, ctrl = false, alt = false, shift = false) shouldBe null
        shortcutFor(Key.Escape, ctrl = true, alt = false, shift = false) shouldBe null
    }

    @Test
    fun `back waits for the focused component, the others go first`() {
        // A dialog or a search field closes itself on Esc; only an unhandled Esc goes back
        Shortcut.Back.beforeFocusedComponent shouldBe false
        Shortcut.GlobalSearch.beforeFocusedComponent shouldBe true
        Shortcut.Refresh.beforeFocusedComponent shouldBe true
        Shortcut.Settings.beforeFocusedComponent shouldBe true
        Shortcut.Help.beforeFocusedComponent shouldBe true
    }

    @Test
    fun `library shortcuts`() {
        shortcutFor(Key.A, ctrl = true, alt = false, shift = false) shouldBe Shortcut.SelectAll
        shortcutFor(Key.Tab, ctrl = true, alt = false, shift = false) shouldBe Shortcut.NextCategory
        shortcutFor(Key.Tab, ctrl = true, alt = false, shift = true) shouldBe Shortcut.PreviousCategory
        shortcutFor(Key.A, ctrl = false, alt = false, shift = false) shouldBe null
        // A search field's own Ctrl+A wins: select all only goes to the screen when nothing handled it
        Shortcut.SelectAll.beforeFocusedComponent shouldBe false
    }
}
