package mihon.desktop.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class StatsTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 30_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `the statistics count the library and the chapters read`() = runComposeUiTest {
        val graph = startTestApp()
        runBlocking {
            val category = TestLibrary.category(graph)
            val first = TestLibrary.libraryManga(graph, category)
            TestLibrary.libraryManga(graph, category)
            val chapter = graph.getChaptersByMangaId.await(first.id).first()
            graph.updateChapter.await(ChapterUpdate(chapter.id) { read = true })
        }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_settings)).performClick()
        waitForText(str(MR.strings.label_stats))
        onNodeWithText(str(MR.strings.label_stats)).performClick()

        waitForText(str(MR.strings.label_overview_section))
        // Two titles in the library, one chapter read
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText("2")).fetchSemanticsNodes().isNotEmpty() &&
                onAllNodes(hasText("1")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
