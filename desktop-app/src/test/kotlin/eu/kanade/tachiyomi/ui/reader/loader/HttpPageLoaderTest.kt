package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.chapter.model.Chapter
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpPageLoaderTest {
    private lateinit var graph: TestAppGraph
    private val scope = CoroutineScope(SupervisorJob())

    @BeforeAll
    fun start() {
        graph = startTestApp()
    }

    private suspend fun loaded(url: String, pages: Int): Pair<HttpPageLoader, List<ReaderPage>> {
        TestSources.http.pageCount = pages
        val chapter = ReaderChapter(Chapter.create().copy(id = url.hashCode().toLong(), url = url, name = url))
        val loader = HttpPageLoader(chapter, TestSources.http, graph.chapterCache, scope, pagesAhead = 4)
        val list = loader.getPages().onEach { it.chapter = chapter }
        chapter.state = ReaderChapter.State.Loaded(list)
        return loader to list
    }

    private suspend fun ReaderPage.awaitStatus(match: (Page.State) -> Boolean) =
        withTimeout(10.seconds) { statusFlow.first(match) }

    @Test
    fun `a page is downloaded into the chapter cache with the four pages after it`(): Unit = runBlocking {
        val (loader, pages) = loaded("/m/loader/c1", pages = 10)
        pages.size shouldBe 10

        loader.loadPage(pages[0])
        (0..4).forEach { pages[it].awaitStatus { s -> s == Page.State.Ready } }

        ImageIO.read(pages[0].stream.shouldNotBeNull().invoke()).width shouldBe 600
        // Past the window (index - 1 .. index + 4), nothing is downloaded
        (5..9).forEach { pages[it].status shouldBe Page.State.Queue }
    }

    @Test
    fun `a page that failed can be retried`(): Unit = runBlocking {
        val (loader, pages) = loaded("/m/loader/c2", pages = 3)
        TestSources.images.failing += "/m/loader/c2/0.png"

        loader.loadPage(pages[0])
        pages[0].awaitStatus { it is Page.State.Error }

        TestSources.images.failing -= "/m/loader/c2/0.png"
        loader.retryPage(pages[0])
        pages[0].awaitStatus { it == Page.State.Ready }
    }
}
