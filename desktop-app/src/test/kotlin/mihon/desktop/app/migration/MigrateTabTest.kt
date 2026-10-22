package mihon.desktop.app.migration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class MigrateTabTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `Browse has a Migrate tab listing the sources of the library, and a source lists its manga`() =
        runComposeUiTest {
            val graph = startTestApp()
            val manga = runBlocking { TestLibrary.libraryManga(graph, TestLibrary.category(graph)) }
            setContent { AppContent(graph, crash = null, onRestart = {}) }

            waitForText(str(MR.strings.browse))
            onAllNodesWithText(str(MR.strings.browse)).onFirst().performClick()
            waitForText(str(MR.strings.label_migration))
            onAllNodesWithText(str(MR.strings.label_migration)).onFirst().performClick()

            waitForText(TestSources.updatable.name)
            onAllNodesWithText(TestSources.updatable.name).onFirst().performClick()
            waitForText(manga.title)
        }
}
