package mihon.desktop.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.collections.shouldContain
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.desktop.core.ContentWarningPrefs
import mihon.domain.extension.model.ContentWarning
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class SettingsTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `allowing 18+ content in the browse settings is saved where the extension loader reads it`() =
        runComposeUiTest {
            val graph = startTestApp()
            setContent { AppContent(graph, crash = null, onRestart = {}) }

            onNodeWithText(str(MR.strings.label_settings)).performClick()
            waitForText(str(MR.strings.browse))
            // "Explorar" is also the rail entry: the settings entry is the last one
            onAllNodesWithText(str(MR.strings.browse)).onLast().performClick()
            waitForText(str(MR.strings.pref_allowed_content_warnings))
            onNodeWithText(str(MR.strings.pref_allowed_content_warnings)).performClick()
            onNodeWithText(str(MR.strings.ext_content_warning_nsfw)).performClick()
            onNodeWithText(str(MR.strings.action_ok)).performClick()
            waitForIdle()

            ContentWarningPrefs.enabled(graph.preferenceStore).get() shouldContain ContentWarning.NSFW
        }

    @Test
    fun `the appearance and advanced settings open`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_settings)).performClick()
        onNodeWithText(str(MR.strings.pref_category_appearance)).performClick()
        waitForText(str(MR.strings.pref_app_theme))
        // A settings screen has its up arrow, which goes back to the list
        onNodeWithContentDescription(str(MR.strings.action_bar_up_description)).performClick()
        waitForText(str(MR.strings.pref_category_advanced))
        onNodeWithText(str(MR.strings.pref_category_advanced)).performClick()
        waitForText(str(MR.strings.desktop_open_logs_folder))
    }
}
