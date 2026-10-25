package tachiyomi.presentation.core.util

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.ScrollbarLazyColumn

private const val FOCUS_TAG = "focus"

/** The window's key handler in miniature: the key reaches [handleScrollKey] only when the focused node ignores it. */
@Composable
private fun Host(focusTarget: @Composable (FocusRequester) -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val requester = remember { FocusRequester() }
    Box(Modifier.onKeyEvent { handleScrollKey(it, scope) }) {
        Column {
            focusTarget(requester)
            content()
        }
    }
    LaunchedEffect(Unit) { requester.requestFocus() }
}

private val Anchor: @Composable (FocusRequester) -> Unit = { requester ->
    Box(Modifier.testTag(FOCUS_TAG).size(1.dp).focusRequester(requester).focusable())
}

/** 200 rows of 50 dp in a 200 x 400 dp viewport, so a page is 360 dp. */
@Composable
private fun TestList(state: LazyListState, tag: String = "list", count: Int = 200) {
    LazyColumn(Modifier.size(200.dp, 400.dp).testTag(tag).keyboardScrollable(state), state) {
        items(count) { Box(Modifier.fillMaxWidth().height(50.dp)) { Text("fila $it") } }
    }
}

private fun LazyListState.scrolledDp(density: Float) =
    (firstVisibleItemIndex * 50f * density + firstVisibleItemScrollOffset) / density

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.press(keys: List<Key>) =
    onNodeWithTag(FOCUS_TAG).performKeyInput { keys.forEach { pressKey(it) } }

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.press(key: Key) = press(listOf(key))

private fun near(actual: Float, expected: Float) = (actual - expected) in -1f..1f

@OptIn(ExperimentalTestApi::class)
class KeyboardScrollTest {
    @AfterEach
    fun reset() = KeyboardScrollRegistry.clear()

    @Test
    fun `page down scrolls 90 percent of the viewport, end and home jump`() = runComposeUiTest {
        val state = LazyListState()
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            Host(Anchor) { TestList(state) }
        }

        press(Key.PageDown)
        waitForIdle()
        near(state.scrolledDp(density), 360f) shouldBe true

        press(Key.MoveEnd)
        waitForIdle()
        state.canScrollForward shouldBe false

        press(Key.MoveHome)
        waitForIdle()
        state.firstVisibleItemIndex shouldBe 0
        state.firstVisibleItemScrollOffset shouldBe 0
    }

    @Test
    fun `space pages down, shift+space pages up, down arrow moves one line`() = runComposeUiTest {
        val state = LazyListState()
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            Host(Anchor) { TestList(state) }
        }

        press(Key.Spacebar)
        waitForIdle()
        near(state.scrolledDp(density), 360f) shouldBe true

        press(Key.DirectionDown)
        waitForIdle()
        near(state.scrolledDp(density), 408f) shouldBe true

        onNodeWithTag(FOCUS_TAG).performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Spacebar)
            keyUp(Key.ShiftLeft)
        }
        waitForIdle()
        near(state.scrolledDp(density), 48f) shouldBe true
    }

    @Test
    fun `a focused text field keeps home, end and the arrows`() = runComposeUiTest {
        val state = LazyListState()
        setContent {
            Host(
                focusTarget = { requester ->
                    BasicTextField(
                        value = "hola mundo",
                        onValueChange = {},
                        singleLine = true,
                        modifier = Modifier.testTag(FOCUS_TAG).focusRequester(requester),
                    )
                },
            ) { TestList(state) }
        }

        press(listOf(Key.MoveEnd, Key.MoveHome))
        waitForIdle()

        state.firstVisibleItemIndex shouldBe 0
        state.firstVisibleItemScrollOffset shouldBe 0
    }

    @Test
    fun `the list under the pointer is the active one, otherwise the last composed`() = runComposeUiTest {
        val a = LazyListState()
        val b = LazyListState()
        setContent {
            Host(Anchor) {
                Row {
                    TestList(a, tag = "a")
                    TestList(b, tag = "b")
                }
            }
        }

        press(Key.PageDown)
        waitForIdle()
        a.firstVisibleItemIndex shouldBe 0
        (b.firstVisibleItemIndex > 0) shouldBe true
        val bAfterFirst = b.firstVisibleItemIndex

        onNodeWithTag("a").performMouseInput { moveTo(center) }
        press(Key.PageDown)
        waitForIdle()
        (a.firstVisibleItemIndex > 0) shouldBe true
        b.firstVisibleItemIndex shouldBe bAfterFirst
    }

    @Test
    fun `a list that leaves the composition stops receiving keys`() = runComposeUiTest {
        val state = LazyListState()
        var shown by mutableStateOf(true)
        lateinit var scope: CoroutineScope
        setContent {
            // Scrolling needs the composition's frame clock
            scope = rememberCoroutineScope()
            if (shown) TestList(state)
        }
        waitForIdle()
        KeyboardScrollRegistry.dispatch(ScrollKey.PageDown, scope) shouldBe true
        waitForIdle()

        shown = false
        waitForIdle()

        KeyboardScrollRegistry.dispatch(ScrollKey.PageDown, scope) shouldBe false
    }

    @Test
    fun `a list that fits, or an empty one, does not consume keys`() = runComposeUiTest {
        val short = LazyListState()
        val empty = LazyListState()
        var showEmpty by mutableStateOf(false)
        setContent {
            if (showEmpty) TestList(empty, count = 0) else TestList(short, count = 3)
        }
        waitForIdle()
        val scope = CoroutineScope(Job())

        KeyboardScrollRegistry.dispatch(ScrollKey.PageDown, scope) shouldBe false

        showEmpty = true
        waitForIdle()
        KeyboardScrollRegistry.dispatch(ScrollKey.End, scope) shouldBe false
        empty.firstVisibleItemIndex shouldBe 0
    }

    @Test
    fun `fast repeated page downs end with the list at rest`() = runComposeUiTest {
        val state = LazyListState()
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            Host(Anchor) { TestList(state) }
        }

        press(listOf(Key.PageDown, Key.PageDown, Key.PageDown))
        waitForIdle()

        state.isScrollInProgress shouldBe false
        (state.scrolledDp(density) >= 359f) shouldBe true
    }

    @Test
    fun `grids and plain scroll states take the keys too`() = runComposeUiTest {
        val grid = LazyGridState()
        val plain = ScrollState(0)
        var showGrid by mutableStateOf(true)
        setContent {
            Host(Anchor) {
                if (showGrid) {
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.size(300.dp, 400.dp).keyboardScrollable(grid), grid) {
                        items(300) { Box(Modifier.height(50.dp)) { Text("$it") } }
                    }
                } else {
                    Column(Modifier.size(200.dp, 400.dp).keyboardScrollable(plain).verticalScroll(plain)) {
                        repeat(100) { Box(Modifier.fillMaxWidth().height(50.dp)) }
                    }
                }
            }
        }

        press(Key.MoveEnd)
        waitForIdle()
        grid.canScrollForward shouldBe false

        showGrid = false
        waitForIdle()
        press(Key.MoveEnd)
        waitForIdle()
        plain.value shouldBe plain.maxValue
    }

    @Test
    fun `the shared list components register themselves`() = runComposeUiTest {
        val column = LazyListState()
        val scrollbarColumn = LazyListState()
        val grid = LazyGridState()
        var which by mutableStateOf(0)
        setContent {
            Host(Anchor) {
                when (which) {
                    0 -> FastScrollLazyColumn(Modifier.size(200.dp, 400.dp), state = column) {
                        items(200) { Box(Modifier.fillMaxWidth().height(50.dp)) { Text("$it") } }
                    }
                    1 -> ScrollbarLazyColumn(Modifier.size(200.dp, 400.dp), state = scrollbarColumn) {
                        items(200) { Box(Modifier.fillMaxWidth().height(50.dp)) { Text("$it") } }
                    }
                    else -> FastScrollLazyVerticalGrid(
                        GridCells.Fixed(3),
                        Modifier.size(300.dp, 400.dp),
                        state = grid,
                    ) {
                        items(300) { Box(Modifier.height(50.dp)) { Text("$it") } }
                    }
                }
            }
        }

        press(Key.PageDown)
        waitForIdle()
        (column.firstVisibleItemIndex > 0) shouldBe true

        which = 1
        waitForIdle()
        press(Key.PageDown)
        waitForIdle()
        (scrollbarColumn.firstVisibleItemIndex > 0) shouldBe true

        which = 2
        waitForIdle()
        press(Key.PageDown)
        waitForIdle()
        (grid.firstVisibleItemIndex > 0) shouldBe true
    }
}
