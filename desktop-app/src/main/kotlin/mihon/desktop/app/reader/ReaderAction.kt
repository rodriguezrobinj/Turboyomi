package mihon.desktop.app.reader

import androidx.compose.ui.input.key.Key
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode

enum class ReaderAction {
    NextPage,
    PreviousPage,
    ScrollDown,
    ScrollUp,
    FirstPage,
    LastPage,
    NextChapter,
    PreviousChapter,
    ToggleMenu,

    // Webtoon: less or more side padding. Paged: the page's zoom, and back to 1×
    ZoomIn,
    ZoomOut,
    ZoomReset,

    // Webtoon only: a longer or shorter step for ↑ and ↓, and scrolling by itself at a speed of its own
    ScrollStepUp,
    ScrollStepDown,
    ToggleAutoScroll,
    AutoScrollFaster,
    AutoScrollSlower,
}

/**
 * The reader's keys (spec 2a §4, "Entrada"). ← and → follow the reading direction; ↑ and ↓ scroll in webtoon and
 * turn pages otherwise. Ctrl with +, - and 0 zooms (webtoon: the side padding); in webtoon Ctrl with ↑, ↓ changes
 * the scroll step, Ctrl+Shift with them the automatic scrolling's speed, and S starts or stops it (spec 8a). Other
 * keys with Ctrl or Alt are the window's (Alt+←, Ctrl+F); Esc is Compose's back key.
 */
fun readerActionFor(key: Key, shift: Boolean, ctrl: Boolean, alt: Boolean, mode: ReadingMode): ReaderAction? {
    val rightToLeft = mode == ReadingMode.RIGHT_TO_LEFT
    val webtoon = mode.type == ReadingMode.ViewerType.Webtoon
    if (ctrl && !alt) {
        return when (key) {
            // Equals: + is Shift+= on a US keyboard
            Key.Plus, Key.Equals, Key.NumPadAdd -> ReaderAction.ZoomIn
            Key.Minus, Key.NumPadSubtract -> ReaderAction.ZoomOut
            Key.Zero, Key.NumPad0 -> if (webtoon) null else ReaderAction.ZoomReset
            Key.DirectionUp -> when {
                !webtoon -> null
                shift -> ReaderAction.AutoScrollFaster
                else -> ReaderAction.ScrollStepUp
            }
            Key.DirectionDown -> when {
                !webtoon -> null
                shift -> ReaderAction.AutoScrollSlower
                else -> ReaderAction.ScrollStepDown
            }
            else -> null
        }
    }
    if (ctrl || alt) return null
    return when (key) {
        Key.DirectionRight -> if (rightToLeft) ReaderAction.PreviousPage else ReaderAction.NextPage
        Key.DirectionLeft -> if (rightToLeft) ReaderAction.NextPage else ReaderAction.PreviousPage
        Key.DirectionDown -> if (webtoon) ReaderAction.ScrollDown else ReaderAction.NextPage
        Key.DirectionUp -> if (webtoon) ReaderAction.ScrollUp else ReaderAction.PreviousPage
        Key.Spacebar -> if (shift) ReaderAction.PreviousPage else ReaderAction.NextPage
        Key.PageDown -> ReaderAction.NextPage
        Key.PageUp -> ReaderAction.PreviousPage
        Key.MoveHome -> ReaderAction.FirstPage
        Key.MoveEnd -> ReaderAction.LastPage
        Key.N -> ReaderAction.NextChapter
        Key.P -> ReaderAction.PreviousChapter
        Key.S -> if (webtoon && !shift) ReaderAction.ToggleAutoScroll else null
        else -> null
    }
}

/** A click at [x] of [width]: the side thirds turn pages in the reading direction, the middle one shows the controls. */
fun tapActionFor(x: Float, width: Float, mode: ReadingMode): ReaderAction {
    val rightToLeft = mode == ReadingMode.RIGHT_TO_LEFT
    return when {
        x < width / 3 -> if (rightToLeft) ReaderAction.NextPage else ReaderAction.PreviousPage
        x > width * 2 / 3 -> if (rightToLeft) ReaderAction.PreviousPage else ReaderAction.NextPage
        else -> ReaderAction.ToggleMenu
    }
}
