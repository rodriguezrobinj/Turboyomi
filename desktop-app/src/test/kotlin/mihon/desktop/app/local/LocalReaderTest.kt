package mihon.desktop.app.local

import eu.kanade.domain.manga.model.toSManga
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.LocalFixtures
import mihon.desktop.app.testing.LocalFixtures.png
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import java.io.File
import kotlin.time.Duration.Companion.seconds

class LocalReaderTest {
    private val graph: TestAppGraph = startTestApp()

    /** A local manga folder built by [build], added to the database with its chapters, oldest first. */
    private suspend fun localManga(name: String, build: (File) -> Unit): Pair<Manga, List<Chapter>> {
        val dir = File(LocalFixtures.localDir(graph), name).apply { mkdirs() }
        build(dir)
        val manga = graph.networkToLocalManga(Manga.create().copy(url = name, title = name, source = LocalSource.ID))
        val update = graph.localSource.getMangaUpdate(manga.toSManga(), emptyList(), false, true)
        graph.syncChaptersWithSource.await(update.chapters, manga, graph.localSource)
        return manga to graph.getChaptersByMangaId.await(manga.id).sortedBy { it.sourceOrder }.reversed()
    }

    /** The page bytes of [chapter] as the reader loads them, or the reader's error. */
    private suspend fun open(manga: Manga, chapter: Chapter): Result<List<ByteArray>> {
        val viewModel = graph.readerViewModelFactory.create(manga.id, chapter.id)
        val state = withTimeout(15.seconds) { viewModel.state.first { it.viewerChapters != null || it.initError != null } }
        state.initError?.let { return Result.failure(it) }
        val pages = state.currentChapter.shouldNotBeNull().pages.shouldNotBeNull()
        return Result.success(pages.map { page -> page.stream!!().use { it.readBytes() } })
    }

    @Test
    fun `a folder, a CBZ and an EPUB chapter open with their pages in natural order, upper-case extensions included`(): Unit =
        runBlocking {
            val (manga, chapters) = localManga("Lectura José") { dir ->
                File(dir, "Cap 1").apply { mkdirs() }.also { cap ->
                    File(cap, "10.png").writeBytes(png(3))
                    File(cap, "2.png").writeBytes(png(2))
                    File(cap, "1.png").writeBytes(png(1))
                }
                LocalFixtures.zip(
                    File(dir, "Cap 2.cbz"),
                    mapOf("01.png" to png(1), "02.png" to png(2), "03.JPG" to png(3), "nota.txt" to "x".toByteArray()),
                )
                LocalFixtures.epub(File(dir, "Cap 3.epub"), listOf(png(1), png(2)))
            }
            val byName = chapters.associateBy { it.name }

            open(manga, byName.getValue("Cap 1")).getOrThrow().map { it.toList() } shouldBe
                listOf(png(1), png(2), png(3)).map { it.toList() }
            open(manga, byName.getValue("Cap 2")).getOrThrow().size shouldBe 3
            open(manga, byName.getValue("Cap 3")).getOrThrow().map { it.toList() } shouldBe
                listOf(png(1), png(2)).map { it.toList() }
        }

    @Test
    fun `a CB7 chapter opens with its pages`(): Unit = runBlocking {
        val (manga, chapters) = localManga("Siete José") { dir -> LocalFixtures.cb7(File(dir, "Cap 1.cb7")) }

        open(manga, chapters.single()).getOrThrow().size shouldBe 3
    }

    @Test
    fun `a damaged archive shows the reader's error instead of crashing`(): Unit = runBlocking {
        val (manga, chapters) = localManga("Roto José") { dir ->
            File(dir, "Cap 1.cbz").writeBytes(ByteArray(512) { (it * 7).toByte() })
        }

        open(manga, chapters.single()).isFailure shouldBe true
    }

    @Test
    fun `a manga folder deleted after it was added says the chapter wasn't found`(): Unit = runBlocking {
        val (manga, chapters) = localManga("Borrado José") { dir -> LocalFixtures.zip(File(dir, "Cap 1.cbz"), mapOf("01.png" to png())) }
        File(LocalFixtures.localDir(graph), "Borrado José").deleteRecursively()

        open(manga, chapters.single()).exceptionOrNull()?.message shouldBe str(MR.strings.chapter_not_found)
    }
}
