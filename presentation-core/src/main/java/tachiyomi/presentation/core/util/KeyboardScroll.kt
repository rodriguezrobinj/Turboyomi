package tachiyomi.presentation.core.util

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A page is this much of the visible height; the scroller's track clicks use it too. */
internal const val PAGE_FRACTION = 0.9f

private val LineHeight = 48.dp

enum class ScrollKey { PageUp, PageDown, Home, End, LineUp, LineDown }

/** The keys that scroll the active list (spec 7, section 3 B). Ctrl and Alt belong to other shortcuts. */
fun scrollKeyFor(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean): ScrollKey? = when {
    ctrl || alt -> null
    key == Key.Spacebar -> if (shift) ScrollKey.PageUp else ScrollKey.PageDown
    // Shift + anything else is a text selection
    shift -> null
    key == Key.PageUp -> ScrollKey.PageUp
    key == Key.PageDown -> ScrollKey.PageDown
    key == Key.MoveHome -> ScrollKey.Home
    key == Key.MoveEnd -> ScrollKey.End
    key == Key.DirectionUp -> ScrollKey.LineUp
    key == Key.DirectionDown -> ScrollKey.LineDown
    else -> null
}

class KeyboardScrollTarget internal constructor(
    private val state: ScrollableState,
    private val linePx: Float,
    private val toStart: suspend () -> Unit,
    private val toEnd: suspend () -> Unit,
) {
    internal var viewportPx = 0f

    /** False for a list that fits or is empty: its keys go on to whoever else wants them. */
    val canScroll: Boolean get() = state.canScrollForward || state.canScrollBackward

    suspend fun perform(key: ScrollKey) {
        when (key) {
            ScrollKey.PageUp -> state.animateScrollBy(-PAGE_FRACTION * viewportPx)
            ScrollKey.PageDown -> state.animateScrollBy(PAGE_FRACTION * viewportPx)
            ScrollKey.LineUp -> state.animateScrollBy(-linePx)
            ScrollKey.LineDown -> state.animateScrollBy(linePx)
            ScrollKey.Home -> toStart()
            ScrollKey.End -> toEnd()
        }
    }
}

/**
 * The scrollable lists on screen. Only the main thread touches it: composition, pointer events and key events.
 * The active one is the last the pointer entered, and until then the last composed (the top screen's).
 */
object KeyboardScrollRegistry {
    private val targets = mutableListOf<KeyboardScrollTarget>()
    private var hovered: KeyboardScrollTarget? = null

    internal fun register(target: KeyboardScrollTarget) {
        targets += target
    }

    internal fun unregister(target: KeyboardScrollTarget) {
        targets -= target
        if (hovered === target) hovered = null
    }

    internal fun hover(target: KeyboardScrollTarget) {
        hovered = target
    }

    internal fun clear() {
        targets.clear()
        hovered = null
    }

    /** True when a list took the key, so the key event stops there. */
    fun dispatch(key: ScrollKey, scope: CoroutineScope): Boolean {
        val target = (hovered ?: targets.lastOrNull())?.takeIf { it.canScroll } ?: return false
        scope.launch { target.perform(key) }
        return true
    }
}

/** For the window's key handler, after the focused component had its turn. */
fun handleScrollKey(event: KeyEvent, scope: CoroutineScope): Boolean =
    event.type == KeyEventType.KeyDown &&
        scrollKeyFor(event.key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
            ?.let { KeyboardScrollRegistry.dispatch(it, scope) } == true

@Composable
fun Modifier.keyboardScrollable(state: LazyListState): Modifier {
    val linePx = with(LocalDensity.current) { LineHeight.toPx() }
    val target = remember(state) {
        KeyboardScrollTarget(
            state,
            linePx,
            toStart = { state.scrollToItem(0) },
            toEnd = { state.scrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) },
        )
    }
    return attach(target)
}

@Composable
fun Modifier.keyboardScrollable(state: LazyGridState): Modifier {
    val linePx = with(LocalDensity.current) { LineHeight.toPx() }
    val target = remember(state) {
        KeyboardScrollTarget(
            state,
            linePx,
            toStart = { state.scrollToItem(0) },
            toEnd = { state.scrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) },
        )
    }
    return attach(target)
}

@Composable
fun Modifier.keyboardScrollable(state: ScrollState): Modifier {
    val linePx = with(LocalDensity.current) { LineHeight.toPx() }
    val target = remember(state) {
        KeyboardScrollTarget(
            state,
            linePx,
            toStart = { state.scrollTo(0) },
            toEnd = { state.scrollTo(state.maxValue) },
        )
    }
    return attach(target)
}

@Composable
private fun Modifier.attach(target: KeyboardScrollTarget): Modifier {
    DisposableEffect(target) {
        KeyboardScrollRegistry.register(target)
        onDispose { KeyboardScrollRegistry.unregister(target) }
    }
    return this
        .onSizeChanged { target.viewportPx = it.height.toFloat() }
        .onPointerEvent(PointerEventType.Enter) { KeyboardScrollRegistry.hover(target) }
}
