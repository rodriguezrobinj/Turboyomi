package mihon.desktop.app.download

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Duration.Companion.seconds

class DownloadedReaderTest {
    private val graph: TestAppGraph = startTestApp()

    private suspend fun downloaded(manga: Manga, chapters: List<Chapter>) {
        graph.downloadManager.downloadChapters(manga, chapters)
        withTimeout(60.seconds) { while (graph.downloadManager.queueState.value.isNotEmpty()) delay(100) }
    }

    private fun isDownloaded(manga: Manga, chapter: Chapter) = graph.downloadManager.isChapterDownloaded(
        chapter.name,
        chapter.scanlator,
        chapter.url,
        manga.title,
        manga.source,
    )

    private suspend fun open(manga: Manga, chapter: Chapter) =
        graph.readerViewModelFactory.create(manga.id, chapter.id).also { viewModel ->
            withTimeout(15.seconds) {
                viewModel.state.first { it.viewerChapters != null || it.initError != null }
            }
            viewModel.state.value.initError shouldBe null
        }

    @Test
    fun `a downloaded chapter opens from the disk, with the server down`(): Unit = runBlocking {
        TestSources.http.pageCount = 3
        val (manga, chapters) = TestLibrary.httpManga(graph)
        downloaded(manga, listOf(chapters[0]))

        TestSources.http.images.failing += (0 until 3).map { "${chapters[0].url}/$it.png" }
        try {
            val pages = open(manga, chapters[0]).state.value.currentChapter.shouldNotBeNull().pages.shouldNotBeNull()
            pages.size shouldBe 3
            pages.all { page -> page.stream!!().use { it.readBytes().isNotEmpty() } } shouldBe true
        } finally {
            TestSources.http.images.failing.clear()
        }
    }

    @Test
    fun `remove after read deletes the read chapter only once the reader is left`(): Unit = runBlocking {
        TestSources.http.pageCount = 2
        graph.downloadPreferences.removeAfterReadSlots.set(0)
        val (manga, chapters) = TestLibrary.httpManga(graph)
        downloaded(manga, listOf(chapters[0]))

        val viewModel = open(manga, chapters[0])
        val pages = viewModel.state.value.currentChapter!!.pages!!
        viewModel.onPageSelected(pages.last())
        delay(1_000)
        isDownloaded(manga, chapters[0]) shouldBe true

        viewModel.saveHistory()
        withTimeout(10.seconds) { while (isDownloaded(manga, chapters[0])) delay(100) }
        graph.downloadPreferences.removeAfterReadSlots.set(-1)
    }

    @Test
    fun `reading a downloaded chapter queues the next ones when the following one is downloaded too`(): Unit =
        runBlocking {
            TestSources.http.pageCount = 4
            graph.downloadPreferences.autoDownloadWhileReading.set(2)
            val (manga, chapters) = TestLibrary.httpManga(graph)
            downloaded(manga, chapters.take(2))

            val viewModel = open(manga, chapters[0])
            viewModel.onPageSelected(viewModel.state.value.currentChapter!!.pages!![2])

            withTimeout(60.seconds) { while (!isDownloaded(manga, chapters[2])) delay(200) }
            graph.downloadPreferences.autoDownloadWhileReading.set(0)
        }
}
