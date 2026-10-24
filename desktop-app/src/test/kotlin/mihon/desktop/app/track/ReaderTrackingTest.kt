package mihon.desktop.app.track

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.TestTrackers
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class ReaderTrackingTest {
    private val graph: TestAppGraph = startTestApp()

    /** Opens [chapter] in the reader, goes to its last page and leaves, as a reader would. */
    private suspend fun readToTheEnd(manga: Manga, chapter: Chapter) {
        val viewModel = graph.readerViewModelFactory.create(manga.id, chapter.id)
        withTimeout(15.seconds) { viewModel.state.first { it.viewerChapters != null || it.initError != null } }
        viewModel.state.value.initError shouldBe null
        viewModel.onPageSelected(viewModel.state.value.currentChapter!!.pages!!.last())
        viewModel.saveHistory()
    }

    @Test
    fun `finishing a chapter in the reader updates the tracker, unless auto update is off or incognito is on`(): Unit =
        runBlocking {
            TestSources.http.pageCount = 2
            val (manga, chapters) = TestLibrary.httpManga(graph)
            TestTrackers.fake.login("josé", "clave")
            graph.trackRepository.upsert(
                Track(
                    id = 0, mangaId = manga.id, trackerId = TestTrackers.fake.id, remoteId = 501, libraryId = null,
                    title = manga.title, lastChapterRead = 0.0, totalChapters = 0, status = 1, score = 0.0,
                    remoteUrl = "", startDate = null, finishDate = null, private = false,
                ),
            )

            readToTheEnd(manga, chapters[0])
            withTimeout(10.seconds) { while (TestTrackers.fake.remote[501] != chapters[0].chapterNumber) delay(100) }

            graph.trackPreferences.autoUpdateTrack.set(false)
            readToTheEnd(manga, chapters[1])
            delay(1_000)
            TestTrackers.fake.remote[501] shouldBe chapters[0].chapterNumber
            graph.trackPreferences.autoUpdateTrack.set(true)

            graph.basePreferences.incognitoMode.set(true)
            readToTheEnd(manga, chapters[2])
            delay(1_000)
            TestTrackers.fake.remote[501] shouldBe chapters[0].chapterNumber
            graph.getHistory.await(manga.id).none { it.chapterId == chapters[2].id } shouldBe true
            graph.basePreferences.incognitoMode.set(false)
        }

    @Test
    fun `incognito shows Mihon's banner over the window`() = runComposeUiTest {
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        graph.basePreferences.incognitoMode.set(true)

        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.pref_incognito_mode))).fetchSemanticsNodes().isNotEmpty()
        }
        graph.basePreferences.incognitoMode.set(false)
    }
}
