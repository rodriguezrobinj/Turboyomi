package mihon.desktop.app.testing

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.MockWebServer
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager

/** In-memory source: 60 manga in pages of 25, five chapters each. [failNext] makes the next listing call throw. */
class FakeSource(
    override val id: Long = 1L,
    override val name: String = "Fake",
    override val lang: String = "en",
) : Source {
    override val supportsLatest: Boolean = true

    @Volatile
    var failNext: Exception? = null

    val mangas: List<SManga> = (1..60).map { i ->
        SManga.create().apply {
            url = "/manga/$i"
            title = "Manga $i"
            // Like a real listing: details (description) are fetched when the manga is opened
            initialized = false
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = page(mangas, page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = page(mangas.reversed(), page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        page(mangas.filter { it.title.contains(query, ignoreCase = true) }, page)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (!fetchDetails) {
            manga
        } else {
            SManga.create().also {
                it.url = manga.url
                it.title = manga.title
                it.description = "Description of ${manga.title}"
                it.initialized = true
            }
        }
        val list = if (!fetchChapters) {
            chapters
        } else {
            (5 downTo 1).map { n ->
                SChapter.create().apply {
                    url = "${manga.url}/$n"
                    name = "Chapter $n"
                    chapter_number = n.toFloat()
                    date_upload = 1_700_000_000_000L + n
                }
            }
        }
        return SMangaUpdate(details, list)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    private fun page(list: List<SManga>, page: Int): MangasPage {
        failNext?.let {
            failNext = null
            throw it
        }
        return MangasPage(list.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE), page * PAGE_SIZE < list.size)
    }

    private companion object {
        const val PAGE_SIZE = 25
    }
}

class FakeSourceManager(vararg initial: Source) : SourceManager {
    override val sources = MutableStateFlow(initial.toList())

    override suspend fun get(sourceKey: Long): Source? = sources.value.find { it.id == sourceKey }

    override suspend fun getOrStub(sourceKey: Long): Source = get(sourceKey) ?: StubSource(sourceKey, "", "")

    override suspend fun getAll(): List<Source> = sources.value

    override suspend fun getOnlineSources(): List<HttpSource> = sources.value.filterIsInstance<HttpSource>()

    override suspend fun getStubSources(): List<StubSource> = emptyList()
}

object TestSources {
    val fake = FakeSource()

    /** For library updates; see [UpdatableSource]. */
    val updatable = UpdatableSource()

    val images = FakeImages()
    private val server = MockWebServer().apply {
        dispatcher = images
        start()
    }

    /** One per test JVM (the test task forks per class), like [fake]. */
    val http = FakeHttpSource(server, images)

    /** A second online source, for downloads from two sources at once. */
    val http2 = FakeHttpSource(server, images, name = "Fake HTTP 2")
}
