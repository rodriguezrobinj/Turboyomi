package tachiyomi.presentation.core.util

import androidx.compose.ui.input.key.Key
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScrollKeysTest {
    private fun key(key: Key, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
        scrollKeyFor(key, ctrl, alt, shift)

    @Test
    fun `page, home, end and line keys`() {
        key(Key.PageUp) shouldBe ScrollKey.PageUp
        key(Key.PageDown) shouldBe ScrollKey.PageDown
        key(Key.MoveHome) shouldBe ScrollKey.Home
        key(Key.MoveEnd) shouldBe ScrollKey.End
        key(Key.DirectionUp) shouldBe ScrollKey.LineUp
        key(Key.DirectionDown) shouldBe ScrollKey.LineDown
    }

    @Test
    fun `space pages down and shift+space pages up`() {
        key(Key.Spacebar) shouldBe ScrollKey.PageDown
        key(Key.Spacebar, shift = true) shouldBe ScrollKey.PageUp
    }

    @Test
    fun `ctrl and alt combinations belong to other shortcuts`() {
        key(Key.PageDown, ctrl = true) shouldBe null
        key(Key.DirectionDown, alt = true) shouldBe null
        key(Key.MoveEnd, ctrl = true) shouldBe null
        key(Key.Spacebar, alt = true) shouldBe null
    }

    @Test
    fun `shift on other keys stays with text selection, left and right are not scroll keys`() {
        key(Key.MoveEnd, shift = true) shouldBe null
        key(Key.DirectionDown, shift = true) shouldBe null
        key(Key.DirectionLeft) shouldBe null
        key(Key.DirectionRight) shouldBe null
        key(Key.A) shouldBe null
    }
}
