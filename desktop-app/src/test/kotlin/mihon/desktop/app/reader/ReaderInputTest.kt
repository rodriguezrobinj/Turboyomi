package mihon.desktop.app.reader

import androidx.compose.ui.input.key.Key
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderInputTest {
    private fun key(key: Key, mode: ReadingMode, shift: Boolean = false) =
        readerActionFor(key, shift = shift, ctrl = false, alt = false, mode = mode)

    @Test
    fun `arrows follow the reading direction`() {
        key(Key.DirectionRight, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.NextPage
        key(Key.DirectionLeft, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.PreviousPage
        key(Key.DirectionRight, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.PreviousPage
        key(Key.DirectionLeft, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.NextPage
    }

    @Test
    fun `up and down scroll in webtoon and turn pages in paged modes`() {
        key(Key.DirectionDown, ReadingMode.WEBTOON) shouldBe ReaderAction.ScrollDown
        key(Key.DirectionUp, ReadingMode.CONTINUOUS_VERTICAL) shouldBe ReaderAction.ScrollUp
        key(Key.DirectionDown, ReadingMode.VERTICAL) shouldBe ReaderAction.NextPage
        key(Key.DirectionUp, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.PreviousPage
    }

    @Test
    fun `space, page keys, home, end, chapters and fullscreen`() {
        key(Key.Spacebar, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.NextPage
        key(Key.Spacebar, ReadingMode.RIGHT_TO_LEFT, shift = true) shouldBe ReaderAction.PreviousPage
        key(Key.PageDown, ReadingMode.WEBTOON) shouldBe ReaderAction.NextPage
        key(Key.PageUp, ReadingMode.WEBTOON) shouldBe ReaderAction.PreviousPage
        key(Key.MoveHome, ReadingMode.VERTICAL) shouldBe ReaderAction.FirstPage
        key(Key.MoveEnd, ReadingMode.VERTICAL) shouldBe ReaderAction.LastPage
        key(Key.N, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.NextChapter
        key(Key.P, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.PreviousChapter
    }

    @Test
    fun `window shortcuts stay with the window`() {
        readerActionFor(
            Key.DirectionLeft,
            shift = false,
            ctrl = false,
            alt = true,
            mode = ReadingMode.LEFT_TO_RIGHT,
        ) shouldBe
            null
        readerActionFor(Key.F, shift = false, ctrl = true, alt = false, mode = ReadingMode.LEFT_TO_RIGHT) shouldBe null
        key(Key.Escape, ReadingMode.LEFT_TO_RIGHT) shouldBe null
    }

    @Test
    fun `ctrl with plus, minus and the arrows changes the webtoon's zoom and scroll step`() {
        fun ctrl(key: Key, mode: ReadingMode = ReadingMode.WEBTOON, shift: Boolean = false) =
            readerActionFor(key, shift = shift, ctrl = true, alt = false, mode = mode)
        ctrl(Key.Plus) shouldBe ReaderAction.ZoomIn
        ctrl(Key.Equals) shouldBe ReaderAction.ZoomIn
        ctrl(Key.NumPadAdd) shouldBe ReaderAction.ZoomIn
        ctrl(Key.Minus) shouldBe ReaderAction.ZoomOut
        ctrl(Key.NumPadSubtract) shouldBe ReaderAction.ZoomOut
        ctrl(Key.DirectionUp, ReadingMode.CONTINUOUS_VERTICAL) shouldBe ReaderAction.ScrollStepUp
        ctrl(Key.DirectionDown) shouldBe ReaderAction.ScrollStepDown
        // Webtoon has no zoom to reset, and the scroll step and speed are webtoon's only
        ctrl(Key.Zero) shouldBe null
        ctrl(Key.DirectionUp, ReadingMode.VERTICAL) shouldBe null
        ctrl(Key.DirectionUp, ReadingMode.VERTICAL, shift = true) shouldBe null
        // AltGr is Ctrl+Alt on Windows
        readerActionFor(Key.Plus, shift = false, ctrl = true, alt = true, mode = ReadingMode.WEBTOON) shouldBe null
    }

    @Test
    fun `ctrl with plus, minus and zero zooms the page in paged modes`() {
        fun ctrl(key: Key) = readerActionFor(key, shift = false, ctrl = true, alt = false, ReadingMode.LEFT_TO_RIGHT)
        ctrl(Key.Plus) shouldBe ReaderAction.ZoomIn
        ctrl(Key.Equals) shouldBe ReaderAction.ZoomIn
        ctrl(Key.NumPadAdd) shouldBe ReaderAction.ZoomIn
        ctrl(Key.Minus) shouldBe ReaderAction.ZoomOut
        ctrl(Key.NumPadSubtract) shouldBe ReaderAction.ZoomOut
        ctrl(Key.Zero) shouldBe ReaderAction.ZoomReset
        ctrl(Key.NumPad0) shouldBe ReaderAction.ZoomReset
    }

    @Test
    fun `S and ctrl+shift with the arrows run the webtoon's automatic scrolling`() {
        key(Key.S, ReadingMode.WEBTOON) shouldBe ReaderAction.ToggleAutoScroll
        key(Key.S, ReadingMode.CONTINUOUS_VERTICAL) shouldBe ReaderAction.ToggleAutoScroll
        key(Key.S, ReadingMode.LEFT_TO_RIGHT) shouldBe null
        key(Key.S, ReadingMode.WEBTOON, shift = true) shouldBe null
        readerActionFor(Key.DirectionUp, shift = true, ctrl = true, alt = false, ReadingMode.WEBTOON) shouldBe
            ReaderAction.AutoScrollFaster
        readerActionFor(Key.DirectionDown, shift = true, ctrl = true, alt = false, ReadingMode.WEBTOON) shouldBe
            ReaderAction.AutoScrollSlower
    }

    @Test
    fun `the side thirds turn pages in the reading direction and the middle toggles the controls`() {
        tapActionFor(50f, 900f, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.PreviousPage
        tapActionFor(850f, 900f, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.NextPage
        tapActionFor(50f, 900f, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.NextPage
        tapActionFor(850f, 900f, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.PreviousPage
        tapActionFor(450f, 900f, ReadingMode.WEBTOON) shouldBe ReaderAction.ToggleMenu
    }
}
