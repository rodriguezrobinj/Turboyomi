package mihon.desktop.app.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class WindowTitleBarTest {
    private val done = mutableListOf<String>()
    private var geometry: TitleBarGeometry? = null

    private fun ComposeUiTest.show() {
        val graph = startTestApp()
        setContent {
            var maximized by remember { mutableStateOf(false) }
            AppProviders(graph) {
                WindowTitleBar(
                    title = "Turboyomi",
                    icon = ColorPainter(Color.Red),
                    active = true,
                    maximized = maximized,
                    onMinimize = { done += "minimize" },
                    onToggleMaximize = {
                        done += "maximize"
                        maximized = !maximized
                    },
                    onClose = { done += "close" },
                    onGeometry = { geometry = it },
                )
            }
        }
    }

    @Test
    fun `each button does its action and maximize turns into restore`() = runComposeUiTest {
        show()
        onNodeWithText("Turboyomi").assertExists()
        onNodeWithContentDescription(str(MR.strings.desktop_window_minimize)).performClick()
        onNodeWithContentDescription(str(MR.strings.desktop_window_maximize)).performClick()
        onNodeWithContentDescription(str(MR.strings.desktop_window_restore)).performClick()
        onNodeWithContentDescription(str(MR.strings.action_close)).performClick()
        done shouldBe listOf("minimize", "maximize", "maximize", "close")
    }

    @Test
    fun `the geometry is the bar's height and where its buttons start`() = runComposeUiTest {
        show()
        waitForIdle()
        val minimize = onNodeWithContentDescription(str(MR.strings.desktop_window_minimize)).fetchSemanticsNode()
        geometry shouldBe TitleBarGeometry(
            height = with(density) { 32.dp.roundToPx() },
            buttonsStart = minimize.boundsInWindow.left.toInt(),
        )
    }
}
