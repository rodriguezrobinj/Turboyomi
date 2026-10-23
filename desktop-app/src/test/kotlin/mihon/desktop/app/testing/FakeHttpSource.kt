package mihon.desktop.app.testing

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
import okio.Buffer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

/**
 * Serves a 600×900 PNG for every `.png` path, except the [failing] (HTTP 500), [corrupt] (not an image) and
 * [short] (600×100) ones.
 */
class FakeImages : Dispatcher() {
    val failing: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val corrupt: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val short: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val png = png(600, 900)

    /** Successful page responses served so far, to tell what a download fetched. */
    val served = AtomicInteger()

    /** The page image every `.png` path serves. */
    fun png(): ByteArray = png
    private val shortPng = png(600, 100)

    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty()
        return when {
            path in failing -> MockResponse().setResponseCode(500)
            path in corrupt -> MockResponse().setHeader("Content-Type", "image/png").setBody("no es una imagen")
            path in short -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(shortPng))
            path.endsWith(".png") -> {
                served.incrementAndGet()
                MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png))
            }
            else -> MockResponse().setResponseCode(404)
        }
    }
}

/**
 * An online source backed by [server]: every manga has three chapters (`<manga url>/c1`..`c3`), each with
 * [pageCount] pages at `<chapter url>/<index>.png`. [failPageList] makes the next page list throw.
 */
class FakeHttpSource(
    private val server: MockWebServer,
    val images: FakeImages,
    override val name: String = "Fake HTTP",
) : HttpSource() {
    override val lang = "es"
    override val supportsLatest = false
    override val baseUrl: String get() = server.url("/").toString().removeSuffix("/")

    @Volatile
    var pageCount = 5

    @Volatile
    var failPageList: Exception? = null

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        MangasPage(emptyList(), false)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga,
        if (!fetchChapters) {
            chapters
        } else {
            (3 downTo 1).map { n ->
                SChapter.create().apply {
                    url = "${manga.url}/c$n"
                    name = "Capítulo $n"
                    chapter_number = n.toFloat()
                    date_upload = 1_700_000_000_000L + n
                }
            }
        },
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        failPageList?.let {
            failPageList = null
            throw it
        }
        return (0 until pageCount).map { Page(it, imageUrl = "$baseUrl${chapter.url}/$it.png") }
    }
}
