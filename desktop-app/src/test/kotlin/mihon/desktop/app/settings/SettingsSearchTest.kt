package mihon.desktop.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class SettingsSearchTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 30_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun androidx.compose.ui.test.ComposeUiTest.openSearch() {
        onNodeWithText(str(MR.strings.label_settings)).performClick()
        waitForText(str(MR.strings.pref_category_advanced))
        onNodeWithContentDescription(str(MR.strings.action_search_settings)).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a setting is found by part of its name and opens its screen`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }
        openSearch()

        onNode(hasSetTextAction()).performTextInput("DNS")
        val title = str(MR.strings.pref_dns_over_https)
        waitForText("${str(MR.strings.pref_category_advanced)} > ${str(MR.strings.label_network)}")
        onNodeWithText(title).performClick()

        // Advanced replaces the search, scrolled to the setting it highlights
        waitUntil(timeoutMillis = 30_000) {
            onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() &&
                onAllNodes(hasText(str(MR.strings.pref_user_agent_string))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `a search without matches says so`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }
        openSearch()

        onNode(hasSetTextAction()).performTextInput("zzzqqq")
        waitForText(str(MR.strings.no_results_found))
    }
}
