package mihon.desktop.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class AdvancedSettingsTest {
    @Test
    fun `a user agent must be a valid header value`() {
        isValidUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)") shouldBe true
        isValidUserAgent("") shouldBe false
        isValidUserAgent("Mozilla\nX-Injected: 1") shouldBe false
    }

    @Test
    fun `Mihon's network and library options are there, and resetting the user agent restores the default`() =
        runComposeUiTest {
            val graph = startTestApp()
            val userAgent = graph.networkPreferences.defaultUserAgent
            userAgent.set("Turboyomi test agent")
            setContent {
                AppProviders(graph) { Navigator(AdvancedSettingsScreen) { DefaultNavigatorScreenTransition(it) } }
            }

            waitUntil(timeoutMillis = 30_000) {
                onAllNodes(hasText(str(MR.strings.pref_verbose_logging))).fetchSemanticsNodes().isNotEmpty()
            }
            val list = onNode(hasScrollToNodeAction())
            list.performScrollToNode(hasText(str(MR.strings.pref_dns_over_https)))
            list.performScrollToNode(hasText(str(MR.strings.pref_reset_user_agent_string)))
            onNodeWithText(str(MR.strings.pref_reset_user_agent_string)).performClick()
            waitForIdle()
            userAgent.get() shouldBe userAgent.defaultValue()

            list.performScrollToNode(hasText(str(MR.strings.pref_enable_chapter_name_hash)))
        }
}
