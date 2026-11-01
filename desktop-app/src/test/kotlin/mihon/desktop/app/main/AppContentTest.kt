package mihon.desktop.app.main

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import eu.kanade.domain.ui.model.ThemeMode
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppContentTest {
    private lateinit var graph: TestAppGraph

    @BeforeAll
    fun start() {
        graph = startTestApp()
        graph.uiPreferences.themeMode.set(ThemeMode.DARK)
    }

    @Test
    fun `the window background follows the theme`() = runComposeUiTest {
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        // Bottom-right corner: away from the rail and the empty-screen text
        val pixels = onRoot().captureToImage().toPixelMap()
        val corner = pixels[pixels.width - 5, pixels.height - 5]
        corner.alpha shouldBe 1f // painted, not the bare window
        corner.luminance() shouldBeLessThan 0.2f
    }

    @Test
    fun `a message with a button goes away by itself, and the next one shows`() = runComposeUiTest {
        setContent { AppContent(graph, crash = null, onRestart = {}) }
        // The snackbar collects the messages once composed; from there the clock moves only by hand
        waitForIdle()
        mainClock.autoAdvance = false

        AppMessages.show("Aviso con botón", "Más información") {}
        AppMessages.show("El siguiente")
        mainClock.advanceTimeBy(1_000)
        onAllNodes(hasText("Aviso con botón")).fetchSemanticsNodes().isEmpty() shouldBe false

        // A long snackbar is 10 s, then the next one's 4 s start
        mainClock.advanceTimeBy(11_000)
        onAllNodes(hasText("Aviso con botón")).fetchSemanticsNodes().isEmpty() shouldBe true
        onAllNodes(hasText("El siguiente")).fetchSemanticsNodes().isEmpty() shouldBe false
    }
}
