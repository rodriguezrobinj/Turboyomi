package mihon.desktop.app.main

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.model.ThemeMode
import io.kotest.matchers.floats.shouldBeLessThan
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ContextMenuThemeTest {
    @Test
    fun `right-click menus follow the dark theme`() = runComposeUiTest {
        val graph = startTestApp()
        graph.uiPreferences.themeMode.set(ThemeMode.DARK)
        setContent {
            AppProviders(graph) {
                ContextMenuArea(items = { listOf(ContextMenuItem("Elemento de José") {}) }) {
                    Box(Modifier.size(200.dp).testTag("area"))
                }
            }
        }

        onNodeWithTag("area").performMouseInput { rightClick(center) }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Elemento de José")).fetchSemanticsNodes().isNotEmpty() }

        val pixels = onNodeWithText("Elemento de José").captureToImage().toPixelMap()
        pixels[1, 1].luminance() shouldBeLessThan 0.3f
    }
}
