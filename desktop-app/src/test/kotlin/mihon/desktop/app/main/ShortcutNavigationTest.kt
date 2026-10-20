package mihon.desktop.app.main

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class ShortcutNavigationTest {
    @Test
    fun `ctrl+F opens the global search from a screen above the home screen`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        // Push a screen on top of HomeScreen: the extension stores
        onNodeWithText(str(MR.strings.label_extensions)).performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.extensionStores))).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(str(MR.strings.extensionStores)).performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.extensionStoresScreen_emptyLabel))).fetchSemanticsNodes().isNotEmpty()
        }

        runOnIdle { AppEvents.send(Shortcut.GlobalSearch) }

        // GlobalSearchScreen's "has results" filter chip
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.has_results))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `back closes an open dialog first, then the screen under it`() = runComposeUiTest {
        val graph = startTestApp()
        var dialogOpen by mutableStateOf(false)
        setContent {
            AppContent(graph, crash = null, onRestart = {})
            if (dialogOpen) {
                AlertDialog(
                    onDismissRequest = { dialogOpen = false },
                    confirmButton = {},
                    title = { Text("Diálogo de José") },
                )
            }
        }
        fun waitFor(text: String) = waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText(str(MR.strings.label_extensions)).performClick()
        waitFor(str(MR.strings.extensionStores))
        onNodeWithText(str(MR.strings.extensionStores)).performClick()
        waitFor(str(MR.strings.extensionStoresScreen_emptyLabel))
        dialogOpen = true
        waitFor("Diálogo de José")

        runOnIdle { AppEvents.send(Shortcut.Back) }
        waitForIdle()
        dialogOpen shouldBe false
        onNodeWithText(str(MR.strings.extensionStoresScreen_emptyLabel)).assertExists()

        runOnIdle { AppEvents.send(Shortcut.Back) }
        waitFor(str(MR.strings.label_extensions))
    }

    @Test
    fun `ctrl+comma opens the settings tab from a screen above the home screen`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }
        fun waitFor(text: String) = waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText(str(MR.strings.label_extensions)).performClick()
        waitFor(str(MR.strings.extensionStores))
        onNodeWithText(str(MR.strings.extensionStores)).performClick()
        waitFor(str(MR.strings.extensionStoresScreen_emptyLabel))

        runOnIdle { AppEvents.send(Shortcut.Settings) }

        waitFor(str(MR.strings.pref_category_appearance))
    }
}
