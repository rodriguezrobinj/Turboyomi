package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
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
}
