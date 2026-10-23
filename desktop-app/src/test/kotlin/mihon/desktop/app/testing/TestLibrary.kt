package mihon.desktop.app.testing

import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

object TestLibrary {
    private val next = AtomicInteger()

    /** A new manga of [TestSources.http] in the database with its three chapters, oldest first. */
    suspend fun httpManga(graph: TestAppGraph, source: FakeHttpSource = TestSources.http): Pair<Manga, List<Chapter>> {
        val n = next.incrementAndGet()
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "/m/$n", title = "Manga HTTP $n", source = source.id),
        )
        val chapters = source.getMangaUpdate(manga.toSManga(), emptyList(), fetchDetails = false, fetchChapters = true)
            .chapters
        graph.syncChaptersWithSource.await(chapters, manga, source)
        return manga to graph.getChaptersByMangaId.await(manga.id).sortedBy { it.chapterNumber }
    }

    /**
     * A manga of [TestSources.updatable] (or of [sourceId], which may be an uninstalled source) in the library and in
     * [category], with its current chapters already synced.
     */
    suspend fun libraryManga(graph: TestAppGraph, category: Long, sourceId: Long = TestSources.updatable.id): Manga {
        val n = next.incrementAndGet()
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "/u/$n", title = "Manga José $n", source = sourceId),
        )
        if (sourceId == TestSources.updatable.id) {
            val chapters = TestSources.updatable.getMangaUpdate(manga.toSManga(), emptyList(), false, true).chapters
            graph.syncChaptersWithSource.await(chapters, manga, TestSources.updatable)
        }
        graph.updateManga.awaitUpdateFavorite(manga.id, true)
        graph.setMangaCategories.await(manga.id, listOf(category))
        return graph.getManga.await(manga.id)!!
    }

    /** A library manga with [status] and [nextUpdate], without chapters: what Upcoming reads. */
    suspend fun ongoingManga(
        graph: TestAppGraph,
        category: Long,
        nextUpdate: Instant?,
        status: Long = SManga.ONGOING.toLong(),
    ): Manga {
        val n = next.incrementAndGet()
        val manga = graph.networkToLocalManga(
            Manga.create().copy(
                url = "/p/$n",
                title = "Próximo $n",
                source = TestSources.updatable.id,
                status = status,
            ),
        )
        graph.updateManga.awaitUpdateFavorite(manga.id, true)
        graph.setMangaCategories.await(manga.id, listOf(category))
        if (nextUpdate != null) graph.updateManga.await(MangaUpdate(manga.id) { this.nextUpdate = nextUpdate })
        return graph.getManga.await(manga.id)!!
    }

    /** A new category of its own, so each test updates only its manga. */
    suspend fun category(graph: TestAppGraph): Long {
        val name = "Categoría ${next.incrementAndGet()}"
        graph.createCategoryWithName.await(name)
        return graph.getCategories.await().first { it.name == name }.id
    }
}
