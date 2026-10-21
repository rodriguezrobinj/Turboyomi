package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.source.model.Page
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.chapter.model.Chapter
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderViewModelTest {
    private lateinit var graph: TestAppGraph

    @BeforeAll
    fun start() {
        graph = startTestApp()
    }

    private suspend fun reader(chapterIndex: Int = 0, pages: Int = 5): Pair<ReaderViewModel, List<Chapter>> {
        TestSources.http.pageCount = pages
        val (manga, chapters) = TestLibrary.httpManga(graph)
        val viewModel = graph.readerViewModelFactory.create(manga.id, chapters[chapterIndex].id)
        withTimeout(10.seconds) { viewModel.state.first { it.viewerChapters != null || it.initError != null } }
        return viewModel to chapters
    }

    private val ReaderViewModel.pages get() = state.value.currentChapter.shouldNotBeNull().pages.shouldNotBeNull()

    private suspend fun stored(chapter: Chapter): Chapter =
        graph.getChaptersByMangaId.await(chapter.mangaId).single { it.id == chapter.id }

    private suspend fun eventually(check: suspend () -> Boolean) = withTimeout(5.seconds) {
        while (!check()) delay(50)
    }

    @Test
    fun `turning pages saves the last page read once the reader settles`(): Unit = runBlocking {
        val (viewModel, chapters) = reader()
        viewModel.onPageSelected(viewModel.pages[1])
        viewModel.onPageSelected(viewModel.pages[2])

        // Grouped: nothing written right away
        stored(chapters[0]).lastPageRead shouldBe 0
        eventually { stored(chapters[0]).lastPageRead == 2 }
    }

    @Test
    fun `the last page marks the chapter read`(): Unit = runBlocking {
        val (viewModel, chapters) = reader()
        viewModel.onPageSelected(viewModel.pages.last())
        eventually { stored(chapters[0]).read }
    }

    @Test
    fun `flush writes a page turn made just before the window closes, and the history`(): Unit = runBlocking {
        val (viewModel, chapters) = reader()
        viewModel.onPageSelected(viewModel.pages[3])
        viewModel.flush()

        stored(chapters[0]).lastPageRead shouldBe 3
        graph.getHistory.await(chapters[0].mangaId).map { it.chapterId } shouldContain chapters[0].id
    }

    @Test
    fun `next and previous chapter, updating the history on the way`(): Unit = runBlocking {
        val (viewModel, chapters) = reader()
        viewModel.loadNextChapter()
        viewModel.state.value.currentChapter.shouldNotBeNull().chapter.id shouldBe chapters[1].id
        graph.getHistory.await(chapters[0].mangaId).map { it.chapterId } shouldContain chapters[0].id

        viewModel.loadPreviousChapter()
        viewModel.state.value.currentChapter.shouldNotBeNull().chapter.id shouldBe chapters[0].id
    }

    @Test
    fun `a next chapter asked for from a chapter that's no longer shown doesn't skip one`(): Unit = runBlocking {
        val (viewModel, chapters) = reader()
        val first = viewModel.state.value.currentChapter.shouldNotBeNull()
        viewModel.loadNextChapter(from = first)
        // Late, like a wheel turn at the end of the first chapter before its viewer is replaced
        viewModel.loadNextChapter(from = first)
        viewModel.state.value.currentChapter.shouldNotBeNull().chapter.id shouldBe chapters[1].id
    }

    @Test
    fun `a failed page can be retried`(): Unit = runBlocking {
        // Page 8: opening the chapter already loads pages 0 to 4, maybe before the test makes this one fail
        val (viewModel, chapters) = reader(pages = 10)
        val page = viewModel.pages[8]
        val path = "${chapters[0].url}/8.png"
        TestSources.images.failing += path
        page.chapter.pageLoader.shouldNotBeNull().loadPage(page)
        withTimeout(10.seconds) { page.statusFlow.first { it is Page.State.Error } }

        TestSources.images.failing -= path
        viewModel.retryPage(page)
        withTimeout(10.seconds) { page.statusFlow.first { it == Page.State.Ready } }
    }

    @Test
    fun `a chapter whose page list fails reports it and can retry`(): Unit = runBlocking {
        TestSources.http.failPageList = IOException("Sin páginas de José")
        val (viewModel, _) = reader()
        viewModel.state.value.initError.shouldNotBeNull().message.shouldNotBeNull() shouldContain "José"

        viewModel.retryInit()
        withTimeout(10.seconds) { viewModel.state.first { it.viewerChapters != null } }
    }
}
