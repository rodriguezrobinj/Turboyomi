package mihon.desktop.app.local

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.LocalFixtures
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import java.io.File

@OptIn(ExperimentalTestApi::class)
class LocalScreensTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `Browse lists the local source, which lists the manga folders`() = runComposeUiTest {
        val graph = startTestApp()
        File(LocalFixtures.localDir(graph), "Explorar José").mkdirs()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitForText(str(MR.strings.browse))
        onAllNodesWithText(str(MR.strings.browse)).onFirst().performClick()
        waitForText(str(MR.strings.local_source))
        onAllNodesWithText(str(MR.strings.local_source)).onFirst().performClick()
        waitForText("Explorar José")
    }

    @Test
    fun `Settings › Data and storage shows the storage folder`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitForText(str(MR.strings.label_settings))
        onAllNodesWithText(str(MR.strings.label_settings)).onFirst().performClick()
        waitForText(str(MR.strings.label_data_storage))
        onAllNodesWithText(str(MR.strings.label_data_storage)).onFirst().performClick()
        waitForText(graph.appDirs.storage.toString())
    }
}
