package mihon.desktop.app.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class ExploreEmptyStateTest {
    @Test
    fun `with no extension stores the extensions tab points to adding one`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        // The app opens on the library (spec 3a)
        onNodeWithText(str(MR.strings.browse)).performClick()
        onNodeWithText(str(MR.strings.label_extensions)).performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(
                androidx.compose.ui.test.hasText(str(MR.strings.extensionStores)),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(str(MR.strings.extensionStores)).performClick()

        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(androidx.compose.ui.test.hasText(str(MR.strings.extensionStoresScreen_emptyLabel)))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
