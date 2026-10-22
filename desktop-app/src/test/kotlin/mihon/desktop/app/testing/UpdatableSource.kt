package mihon.desktop.app.testing

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * A source for library updates: [chapters] says how many chapters each manga url has now (3 if unset), [failing]
 * makes a manga's update throw, and [gate] holds every update until it's completed (for cancel and "already
 * running").
 */
class UpdatableSource : Source {
    override val id = 3L
    override val name = "Actualizable"
    override val lang = "es"
    override val supportsLatest = false

    val chapters: MutableMap<String, Int> = ConcurrentHashMap()
    val failing: MutableMap<String, Exception> = ConcurrentHashMap()

    @Volatile
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)

    override suspend fun getLatestUpdates(page: Int) = MangasPage(emptyList(), false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) = MangasPage(emptyList(), false)

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        gate?.await()
        failing[manga.url]?.let { throw it }
        val count = this.chapters[manga.url] ?: 3
        return SMangaUpdate(
            manga,
            (count downTo 1).map { n ->
                SChapter.create().apply {
                    url = "${manga.url}/c$n"
                    name = "Capítulo $n"
                    chapter_number = n.toFloat()
                    date_upload = 1_700_000_000_000L + n
                }
            },
        )
    }
}
