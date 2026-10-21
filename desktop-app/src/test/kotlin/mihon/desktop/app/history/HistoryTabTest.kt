package mihon.desktop.app.history

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.reader.READER_TAG
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.i18n.MR
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class HistoryTabTest {
    @Test
    fun `a read chapter is listed, resumes in the reader and can be removed`() = runComposeUiTest {
        val graph = startTestApp()
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        // Only this test's entry: the tests of this class share the database
        runBlocking { graph.removeHistory.awaitAll() }
        runBlocking { graph.upsertHistory.await(HistoryUpdate(chapters[0].id, Clock.System.now(), 60_000)) }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_recent_manga)).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(manga.title)).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithContentDescription(str(MR.strings.action_delete)).performClick()
        onAllNodesWithText(str(MR.strings.action_remove)).onLast().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(manga.title)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `clicking an entry continues reading in the reader`() = runComposeUiTest {
        val graph = startTestApp()
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        // Only this test's entry: the tests of this class share the database
        runBlocking { graph.removeHistory.awaitAll() }
        runBlocking { graph.upsertHistory.await(HistoryUpdate(chapters[0].id, Clock.System.now(), 60_000)) }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_recent_manga)).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(manga.title)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(manga.title).performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasTestTag(READER_TAG)).fetchSemanticsNodes().isNotEmpty() }
    }
}
