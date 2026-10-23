package mihon.desktop.app.download

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.util.storage.DiskUtil
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.core.archive.ArchiveReader
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.io.File
import kotlin.time.Duration.Companion.seconds

class DownloaderTest {
    private val graph: TestAppGraph = startTestApp()
    private val source = TestSources.http

    /** Each test starts with an empty queue: one left by another test would hold off this test's auto start. */
    @BeforeEach
    fun emptyQueue(): Unit = runBlocking { graph.downloadManager.clearQueue() }

    private suspend fun awaitQueue(done: (List<Download>) -> Boolean) = withTimeout(60.seconds) {
        while (!done(graph.downloadManager.queueState.value)) delay(100)
    }

    private fun chapterFile(manga: Manga, chapter: Chapter, cbz: Boolean): File {
        val provider = graph.downloadProvider
        val mangaDir = File(graph.storageManager.getDownloadsDirectory()!!.filePath!!)
            .resolve(provider.getSourceDirName(source)).resolve(provider.getMangaDirName(manga.title))
        val name = provider.getChapterDirName(chapter.name, chapter.scanlator, chapter.url)
        return mangaDir.resolve(if (cbz) "$name.cbz" else name)
    }

    @Test
    fun `a chapter downloads as a CBZ with its ComicInfo and its pages in order`(): Unit = runBlocking {
        graph.downloadPreferences.saveChaptersAsCBZ.set(true)
        source.pageCount = 3
        val (manga, chapters) = TestLibrary.httpManga(graph)

        graph.downloadManager.downloadChapters(manga, listOf(chapters[0]))
        awaitQueue { it.isEmpty() }

        ArchiveReader(chapterFile(manga, chapters[0], cbz = true)).use { reader ->
            val names = reader.useEntries { e -> e.map { it.name }.toList() }.sorted()
            names shouldContainExactly listOf("001.png", "002.png", "003.png", "ComicInfo.xml")
            reader.getInputStream("ComicInfo.xml")!!.use { it.readBytes().decodeToString() } shouldContain manga.title
        }
        graph.downloadManager.isChapterDownloaded(
            chapters[0].name,
            chapters[0].scanlator,
            chapters[0].url,
            manga.title,
            manga.source,
        ) shouldBe true
    }

    @Test
    fun `with CBZ off a chapter downloads as a folder`(): Unit = runBlocking {
        graph.downloadPreferences.saveChaptersAsCBZ.set(false)
        source.pageCount = 2
        val (manga, chapters) = TestLibrary.httpManga(graph)

        graph.downloadManager.downloadChapters(manga, listOf(chapters[1]))
        awaitQueue { it.isEmpty() }

        chapterFile(manga, chapters[1], cbz = false).list()!!.sorted() shouldContainExactly
            listOf("001.png", "002.png", "ComicInfo.xml")
        graph.downloadPreferences.saveChaptersAsCBZ.set(true)
    }

    @Test
    fun `a page that fails three times leaves its chapter in error and the queue goes on`(): Unit = runBlocking {
        source.pageCount = 2
        val (manga, chapters) = TestLibrary.httpManga(graph)
        source.images.failing += "${chapters[0].url}/1.png"

        graph.downloadManager.downloadChapters(manga, chapters.take(2))
        awaitQueue { queue -> queue.size == 1 && queue.single().status == Download.State.ERROR }

        graph.downloadManager.queueState.value.single().chapter.id shouldBe chapters[0].id
        chapterFile(manga, chapters[1], cbz = true).exists() shouldBe true
        graph.downloadManager.clearQueue()
        source.images.failing.clear()
    }

    @Test
    fun `with too little free space the chapter fails with Mihon's message and others are not lost`(): Unit =
        runBlocking {
            val (manga, chapters) = TestLibrary.httpManga(graph)
            val previous = DiskUtil.usableSpace
            try {
                DiskUtil.usableSpace = { 1L }
                graph.downloadManager.downloadChapters(manga, listOf(chapters[2]))
                awaitQueue { queue -> queue.singleOrNull()?.status == Download.State.ERROR }
            } finally {
                DiskUtil.usableSpace = previous
            }
            chapterFile(manga, chapters[2], cbz = true).exists() shouldBe false
            graph.downloadManager.clearQueue()
        }

    @Test
    fun `queued without starting, the queue is saved, restored paused, and resuming reuses the pages in _tmp`(): Unit =
        runBlocking {
            source.pageCount = 3
            val (manga, chapters) = TestLibrary.httpManga(graph)
            graph.downloadManager.downloadChapters(manga, listOf(chapters[0]), autoStart = false)

            graph.downloadStore.restore().map { it.chapter.id } shouldContainExactly listOf(chapters[0].id)
            // The running state follows the downloader within a poll; queued without starting, it settles on false
            withTimeout(5.seconds) { graph.downloadManager.isDownloaderRunning.first { !it } }

            // A page left in _tmp by a run that was cut short
            val tmp = File(chapterFile(manga, chapters[0], cbz = false).path + "_tmp").apply { mkdirs() }
            File(tmp, "001.png").writeBytes(source.images.png())
            val served = source.images.served.get()

            graph.downloadManager.startDownloads()
            awaitQueue { it.isEmpty() }
            (source.images.served.get() - served) shouldBe 2
        }

    @Test
    fun `two sources download side by side, and an error in one doesn't stop the other`(): Unit = runBlocking {
        source.pageCount = 2
        val (httpManga, httpChapters) = TestLibrary.httpManga(graph)
        val (otherManga, otherChapters) = TestLibrary.httpManga(graph, TestSources.http2)
        TestSources.http2.failPageList = IllegalStateException("caída")

        graph.downloadManager.downloadChapters(otherManga, listOf(otherChapters[0]))
        graph.downloadManager.downloadChapters(httpManga, listOf(httpChapters[0]))
        awaitQueue { queue -> queue.all { it.status == Download.State.ERROR } }

        chapterFile(httpManga, httpChapters[0], cbz = true).exists() shouldBe true
        graph.downloadManager.queueState.value.map { it.chapter.id } shouldContainExactly listOf(otherChapters[0].id)
        graph.downloadManager.clearQueue()
    }
}
