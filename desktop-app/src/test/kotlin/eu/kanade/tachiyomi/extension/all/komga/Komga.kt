package eu.kanade.tachiyomi.extension.all.komga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.ConcurrentHashMap

/**
 * Stands for the Komga extension: the Komga tracker accepts a source by this class name. Its server answers a series
 * and its read progress the way a Komga server does; [progress] is what was last saved per series.
 */
class Komga : HttpSource() {
    val progress = ConcurrentHashMap<String, String>()

    val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val series = Regex("/api/v[12]/series/([^/]+)").find(path)?.groupValues?.get(1)
                    ?: return MockResponse().setResponseCode(404)
                return when {
                    request.method == "PUT" -> {
                        progress[series] = request.body.readUtf8()
                        MockResponse().setResponseCode(204)
                    }
                    path.endsWith("/read-progress/tachiyomi") -> json(
                        """{"booksCount":10,"booksReadCount":3,"booksUnreadCount":7,"booksInProgressCount":0,""" +
                            """"lastReadContinuousNumberSort":3.0,"maxNumberSort":10.0}""",
                    )
                    else -> json(seriesJson(series))
                }
            }
        }
        start()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun seriesJson(id: String) =
        """{"id":"$id","libraryId":"L","name":"Serie $id","created":null,"lastModified":null,""" +
            """"fileLastModified":"2026-01-01T00:00:00Z","booksCount":10,"booksReadCount":3,"booksUnreadCount":7,""" +
            """"booksInProgressCount":0,"metadata":{"status":"ONGOING","created":null,"lastModified":null,""" +
            """"title":"Serie $id","titleSort":"Serie $id","summary":"","summaryLock":false,""" +
            """"readingDirection":"","readingDirectionLock":false,"publisher":"","publisherLock":false,""" +
            """"ageRating":null,"ageRatingLock":false,"language":"es","languageLock":false,"genres":[],""" +
            """"genresLock":false,"tags":[],"tagsLock":false},"booksMetadata":{"authors":[],"releaseDate":null,""" +
            """"summary":"","summaryNumber":"","created":"2026-01-01T00:00:00Z",""" +
            """"lastModified":"2026-01-01T00:00:00Z"}}"""

    override val name = "Komga José"
    override val lang = "es"
    override val supportsLatest = false
    override val baseUrl: String get() = server.url("/").toString().removeSuffix("/")

    override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) =
        MangasPage(emptyList(), false)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = SMangaUpdate(manga, chapters)

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}
