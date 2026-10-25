package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ScrollerBarTest {
    @Test
    fun `the thumb rests half visible and is never hidden while the list can scroll`() {
        thumbAlpha(allowed = true, active = false) shouldBe 0.5f
        thumbAlpha(allowed = true, active = true) shouldBe 1f
        thumbAlpha(allowed = false, active = true) shouldBe 0f
    }

    private fun ComposeUiTest.setList(state: LazyListState): () -> Float {
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            FastScrollLazyColumn(Modifier.size(200.dp, 400.dp), state = state) {
                items(200) { Box(Modifier.fillMaxWidth().height(50.dp)) { Text("fila $it") } }
            }
        }
        return { (state.firstVisibleItemIndex * 50f * density + state.firstVisibleItemScrollOffset) / density }
    }

    @Test
    fun `dragging the thumb scrolls the list`() = runComposeUiTest {
        val state = LazyListState()
        setList(state)
        waitForIdle()

        // A frame between the steps: the scroll follows the thumb only while the drag is on
        onNodeWithTag(THUMB_TAG).performMouseInput {
            moveTo(center)
            press()
        }
        onNodeWithTag(THUMB_TAG).performMouseInput { moveBy(Offset(0f, 20f)) }
        waitForIdle()
        onNodeWithTag(THUMB_TAG).performMouseInput { moveBy(Offset(0f, 130f)) }
        waitForIdle()
        onNodeWithTag(THUMB_TAG).performMouseInput { release() }
        waitForIdle()

        (state.firstVisibleItemIndex > 20) shouldBe true
    }

    @Test
    fun `a click on the track below the thumb scrolls one page`() = runComposeUiTest {
        val state = LazyListState()
        val scrolledDp = setList(state)
        waitForIdle()

        onNodeWithTag(TRACK_TAG).performMouseInput { click(Offset(width / 2f, height - 10f)) }
        waitForIdle()

        (scrolledDp() in 300f..400f) shouldBe true
    }

    @Test
    fun `a click on the thumb itself does not page`() = runComposeUiTest {
        val state = LazyListState()
        setList(state)
        waitForIdle()

        onNodeWithTag(THUMB_TAG).performMouseInput { click() }
        waitForIdle()

        state.firstVisibleItemIndex shouldBe 0
        state.firstVisibleItemScrollOffset shouldBe 0
    }

    @Test
    fun `the scroller keeps its own gutter, the rows never reach the thumb`() = runComposeUiTest {
        setContent {
            FastScrollLazyColumn(Modifier.size(200.dp, 400.dp)) {
                items(200) { Box(Modifier.fillMaxWidth().height(50.dp).testTag("row$it")) }
            }
        }
        waitForIdle()

        val row = onNodeWithTag("row0").getBoundsInRoot()
        val thumb = onNodeWithTag(THUMB_TAG).getBoundsInRoot()
        (row.right < thumb.left) shouldBe true
        // Centered in the gutter, not stuck to the window edge
        (200.dp - thumb.right) shouldBe (thumb.left - row.right)
    }

    @Test
    fun `a grid's thumb starts below its top content padding, not under the app bar`() = runComposeUiTest {
        setContent {
            FastScrollLazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.size(300.dp, 400.dp),
                contentPadding = PaddingValues(top = 64.dp),
            ) {
                items(300) { Box(Modifier.height(50.dp)) }
            }
        }
        waitForIdle()

        onNodeWithTag(THUMB_TAG).getBoundsInRoot().top shouldBe 64.dp
    }

    @Test
    fun `a padded grid's thumb sits centered between the last card and the edge`() = runComposeUiTest {
        setContent {
            FastScrollLazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.size(300.dp, 400.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(300) { Box(Modifier.height(50.dp).testTag("card$it")) }
            }
        }
        waitForIdle()

        val last = onNodeWithTag("card2").getBoundsInRoot()
        val thumb = onNodeWithTag(THUMB_TAG).getBoundsInRoot()
        (thumb.left - last.right) shouldBe (300.dp - thumb.right)
    }

    @Test
    fun `the thumb is as long as the visible share of the list`() = runComposeUiTest {
        var rows by mutableIntStateOf(20)
        setContent {
            FastScrollLazyColumn(Modifier.size(200.dp, 400.dp)) {
                items(rows) { Box(Modifier.fillMaxWidth().height(50.dp)) }
            }
        }
        waitForIdle()
        // 8 of 20 rows fit
        onNodeWithTag(THUMB_TAG).getBoundsInRoot().height shouldBe 160.dp

        rows = 1000
        waitForIdle()
        onNodeWithTag(THUMB_TAG).getBoundsInRoot().height shouldBe 48.dp
    }

    @Test
    fun `a grid's thumb is as long as the visible share of its rows`() = runComposeUiTest {
        setContent {
            FastScrollLazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.size(300.dp, 400.dp)) {
                // 20 rows of 50 dp, 8 fit
                items(60) { Box(Modifier.height(50.dp)) }
            }
        }
        waitForIdle()

        onNodeWithTag(THUMB_TAG).getBoundsInRoot().height shouldBe 160.dp
    }

    @Test
    fun `a scrollbar column works inside an alert dialog`() = runComposeUiTest {
        setContent {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                text = { ScrollbarLazyColumn { items(50) { Text("opcion $it") } } },
            )
        }
        waitForIdle()

        onNodeWithText("opcion 0").assertExists()
    }
}
