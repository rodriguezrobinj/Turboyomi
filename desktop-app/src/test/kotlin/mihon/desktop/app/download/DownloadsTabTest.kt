package mihon.desktop.app.download

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class DownloadsTabTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun `the rail shows Downloads with the queue count, and the queue pauses and resumes`() = runComposeUiTest {
        val graph = startTestApp()
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        runBlocking { graph.downloadManager.downloadChapters(manga, chapters, autoStart = false) }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitFor(hasText(str(MR.strings.pref_category_downloads)))
        waitFor(hasText("3"))
        onAllNodesWithText(str(MR.strings.pref_category_downloads)).onFirst().performClick()
        waitFor(hasText(manga.title))
        waitFor(hasText(chapters[0].name))

        onNodeWithContentDescription(str(MR.strings.action_resume)).performClick()
        waitUntil(timeoutMillis = 15_000) { graph.downloadManager.isDownloaderRunning.value }
        waitFor(hasContentDescription(str(MR.strings.action_pause)))
        onNodeWithContentDescription(str(MR.strings.action_pause)).performClick()
        waitUntil(timeoutMillis = 15_000) { !graph.downloadManager.isDownloaderRunning.value }

        runBlocking { graph.downloadManager.clearQueue() }
        graph.downloadManager.queueState.value.size shouldBe 0
    }
}
