package mihon.desktop.app.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import org.junit.jupiter.api.Test
import tachiyomi.presentation.core.components.WheelTextPicker
import kotlin.math.absoluteValue

/** The update-interval picker (spec 3b §3 F) driven by a mouse wheel: the wheel's animated scroll ends in a snap too. */
@OptIn(ExperimentalTestApi::class)
class WheelPickerTest {
    @Test
    fun `a mouse wheel scroll comes to rest on a whole row, the selected one`() = runComposeUiTest {
        var selected = 0
        setContent {
            WheelTextPicker(
                items = (0..28).map { "$it" },
                modifier = Modifier.testTag("picker"),
                size = DpSize(120.dp, 120.dp),
                onSelectionChanged = { selected = it },
            )
        }

        onNodeWithTag("picker").performMouseInput {
            moveTo(center)
            scroll(9f)
        }
        // Long after any wheel animation and snap could still be running
        mainClock.advanceTimeBy(3_000)
        waitForIdle()

        selected shouldBeGreaterThan 0
        val picker = onNodeWithTag("picker").fetchSemanticsNode().boundsInRoot
        val row = onNodeWithText("$selected").fetchSemanticsNode().boundsInRoot
        (row.center.y - picker.center.y).absoluteValue shouldBeLessThan 1f
    }
}
