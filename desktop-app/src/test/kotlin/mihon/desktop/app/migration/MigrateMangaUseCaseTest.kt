package mihon.desktop.app.migration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.domain.migration.models.MigrationFlag
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class MigrateMangaUseCaseTest {
    private val graph: TestAppGraph = startTestApp()

    /** A manga of [TestSources.fake] (five chapters once refreshed), not in the library yet. */
    private suspend fun target(sourceId: Long = TestSources.fake.id): Manga = graph.networkToLocalManga(
        Manga.create().copy(url = "/destino/${next.incrementAndGet()}", title = "Destino", source = sourceId),
    )

    private fun flags(vararg flags: MigrationFlag) = graph.sourcePreferences.migrationFlags.set(flags.toSet())

    @Test
    fun `chapters up to the last one read become read, and bookmarks and fetch dates carry over`(): Unit =
        runBlocking {
            flags(MigrationFlag.CHAPTER)
            val current = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
            val old = graph.getChaptersByMangaId.await(current.id).sortedBy { it.chapterNumber }
            graph.updateChapter.awaitAll(
                listOf(
                    ChapterUpdate(old[0].id) { read = true },
                    ChapterUpdate(old[1].id) {
                        read = true
                        bookmark = true
                    },
                ),
            )
            val target = target()

            graph.migrateMangaUseCase(current, target, replace = true)

            val migrated = graph.getChaptersByMangaId.await(target.id).sortedBy { it.chapterNumber }
            migrated.map { it.read } shouldContainExactly listOf(true, true, false, false, false)
            migrated.map { it.bookmark } shouldContainExactly listOf(false, true, false, false, false)
            migrated[1].dateFetch shouldBe old[1].dateFetch
        }

    @Test
    fun `categories and notes carry over only with their flag`(): Unit = runBlocking {
        val category = TestLibrary.category(graph)
        val current = TestLibrary.libraryManga(graph, category)
        graph.updateManga.await(MangaUpdate(current.id) { notes = "Mis notas" })
        val withNotes = graph.getManga.await(current.id)!!

        flags(MigrationFlag.CATEGORY, MigrationFlag.NOTES)
        val first = target()
        graph.migrateMangaUseCase(withNotes, first, replace = false)
        graph.getCategories.await(first.id).map { it.id } shouldContainExactly listOf(category)
        graph.getManga.await(first.id)!!.notes shouldBe "Mis notas"

        flags()
        val second = target()
        graph.migrateMangaUseCase(withNotes, second, replace = false)
        graph.getCategories.await(second.id).map { it.id } shouldContainExactly emptyList()
        graph.getManga.await(second.id)!!.notes shouldBe ""
    }

    @Test
    fun `a custom cover is copied to the target`(): Unit = runBlocking {
        flags(MigrationFlag.CUSTOM_COVER)
        val current = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        graph.coverCache.setCustomCoverToCache(current, "portada".byteInputStream())
        val target = target()

        graph.migrateMangaUseCase(current, target, replace = true)

        graph.coverCache.getCustomCoverFile(target.id).readText() shouldBe "portada"
    }

    @Test
    fun `migrating takes the origin out of the library, copying keeps both, and flags pass to the target`(): Unit =
        runBlocking {
            flags()
            val current = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
            graph.updateManga.await(
                MangaUpdate(current.id) {
                    chapterFlags = 0x100L
                    viewerFlags = 0x3L
                },
            )
            val origin = graph.getManga.await(current.id)!!

            val copied = target()
            graph.migrateMangaUseCase(origin, copied, replace = false)
            graph.getManga.await(origin.id)!!.favorite shouldBe true
            graph.getManga.await(copied.id)!!.favorite shouldBe true

            val migrated = target()
            graph.migrateMangaUseCase(origin, migrated, replace = true)
            graph.getManga.await(origin.id)!!.favorite shouldBe false
            graph.getManga.await(migrated.id)!!.let {
                it.favorite shouldBe true
                it.favoriteAt shouldBe origin.favoriteAt
                it.chapterFlags shouldBe 0x100L
                it.viewerFlags shouldBe 0x3L
            }
        }

    @Test
    fun `if refreshing the target fails, nothing changes`(): Unit = runBlocking {
        flags(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY)
        val current = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        val target = target(TestSources.updatable.id)
        TestSources.updatable.failing[target.url] = IOException("sin red")
        val errors = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(errors)

        try {
            graph.migrateMangaUseCase(current, target, replace = true)
        } finally {
            root.detachAppender(errors)
        }

        // Logged once, by the refresh: the migration doesn't log the same failure again
        errors.list.count { it.level == Level.ERROR && "sin red" in it.formattedMessage } shouldBe 1
        graph.getManga.await(current.id)!!.favorite shouldBe true
        graph.getManga.await(target.id)!!.favorite shouldBe false
        graph.getCategories.await(target.id) shouldBe emptyList()
    }

    @Test
    fun `a target already in the library ends up only in the origin's categories`(): Unit = runBlocking {
        flags(MigrationFlag.CATEGORY)
        val originCategory = TestLibrary.category(graph)
        val current = TestLibrary.libraryManga(graph, originCategory)
        val target = TestLibrary.libraryManga(graph, TestLibrary.category(graph))

        graph.migrateMangaUseCase(current, target, replace = true)

        graph.getCategories.await(target.id).map { it.id } shouldContainExactly listOf(originCategory)
        graph.getManga.await(target.id)!!.favorite shouldBe true
    }

    @Test
    fun `an origin from an uninstalled source migrates all the same`(): Unit = runBlocking {
        flags(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY)
        val current = TestLibrary.libraryManga(graph, TestLibrary.category(graph), sourceId = 999L)
        val target = target()

        graph.migrateMangaUseCase(current, target, replace = true)

        graph.getManga.await(current.id)!!.favorite shouldBe false
        graph.getManga.await(target.id)!!.favorite shouldBe true
    }

    private companion object {
        val next = AtomicInteger()
    }
}
