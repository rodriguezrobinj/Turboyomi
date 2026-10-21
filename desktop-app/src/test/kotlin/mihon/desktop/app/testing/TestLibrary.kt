package mihon.desktop.app.testing

import eu.kanade.domain.manga.model.toSManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.util.concurrent.atomic.AtomicInteger

object TestLibrary {
    private val next = AtomicInteger()

    /** A new manga of [TestSources.http] in the database with its three chapters, oldest first. */
    suspend fun httpManga(graph: TestAppGraph): Pair<Manga, List<Chapter>> {
        val n = next.incrementAndGet()
        val source = TestSources.http
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "/m/$n", title = "Manga HTTP $n", source = source.id),
        )
        val chapters = source.getMangaUpdate(manga.toSManga(), emptyList(), fetchDetails = false, fetchChapters = true)
            .chapters
        graph.syncChaptersWithSource.await(chapters, manga, source)
        return manga to graph.getChaptersByMangaId.await(manga.id).sortedBy { it.chapterNumber }
    }
}
