package mihon.desktop.app.download

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class DownloadHooksTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 20_000) { onAllNodes(hasText(text), true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `a chapter's download button in the manga screen downloads it`() = runComposeUiTest {
        val graph = startTestApp()
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        setContent { AppProviders(graph) { Navigator(MangaScreen(manga.id)) { DefaultNavigatorScreenTransition(it) } } }

        waitForText(chapters[0].name)
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasContentDescription(str(MR.strings.manga_download))).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithContentDescription(str(MR.strings.manga_download)).onFirst().performClick()
        waitUntil(timeoutMillis = 60_000) {
            chapters.any {
                graph.downloadManager.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source)
            }
        }
    }

    @Test
    fun `downloaded only leaves the library with the manga that have downloads, under its banner`() =
        runComposeUiTest {
            val graph = startTestApp()
            val category = runBlocking { TestLibrary.category(graph) }
            val name = runBlocking { graph.getCategories.await().first { it.id == category }.name }
            val (kept, keptChapters) = runBlocking { TestLibrary.httpManga(graph) }
            val (hidden, _) = runBlocking { TestLibrary.httpManga(graph) }
            runBlocking {
                listOf(kept, hidden).forEach {
                    graph.updateManga.awaitUpdateFavorite(it.id, true)
                    graph.setMangaCategories.await(it.id, listOf(category))
                }
                graph.downloadManager.downloadChapters(kept, listOf(keptChapters[0]))
                withTimeout(60.seconds) { while (graph.downloadManager.queueState.value.isNotEmpty()) delay(100) }
            }
            setContent { AppContent(graph, crash = null, onRestart = {}) }

            waitForText(name)
            onNodeWithText(name).performClick()
            waitForText(hidden.title)
            onNodeWithContentDescription(str(MR.strings.action_menu_overflow_description)).performClick()
            onNodeWithText(str(MR.strings.label_downloaded_only)).performClick()

            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(hidden.title).fetchSemanticsNodes().isEmpty() }
            waitForText(kept.title)
            waitForText(str(MR.strings.label_downloaded_only))
            graph.basePreferences.downloadedOnly.set(false)
        }
}
