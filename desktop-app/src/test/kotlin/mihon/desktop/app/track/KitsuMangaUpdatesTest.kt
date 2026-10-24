package mihon.desktop.app.track

import eu.kanade.tachiyomi.data.track.BaseTracker
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import okhttp3.Interceptor
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.core.common.preference.Preference
import java.util.concurrent.ConcurrentHashMap

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KitsuMangaUpdatesTest {
    private val graph: TestAppGraph = startTestApp()
    private val server = MockWebServer()
    private val responses = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    @BeforeAll
    fun redirect() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.requestUrl!!.encodedPath}"
                return responses[key]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val local = server.url("/")
        // The trackers' real hosts go to the test server, path and query untouched
        BaseTracker.testInterceptor = Interceptor { chain ->
            val url = chain.request().url.newBuilder().scheme("http").host(local.host).port(local.port).build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }
    }

    @AfterAll
    fun stop() {
        BaseTracker.testInterceptor = null
        server.shutdown()
    }

    @Test
    fun `MangaUpdates signs in and searches, and its session token is sealed`(): Unit = runBlocking {
        val mu = graph.trackerManager.mangaUpdates
        responses["PUT /v1/account/login"] = { json("""{"context":{"session_token":"token-jose-77","uid":77}}""") }
        responses["GET /v1/account/profile"] = { json("""{"username":"josé"}""") }
        responses["POST /v1/series/search"] = {
            json(
                """{"results":[{"record":{"series_id":5,"title":"Berserk",""" +
                    """"url":"https://www.mangaupdates.com/series/5"}}]}""",
            )
        }

        mu.login("josé", "clave")

        mu.isLoggedIn shouldBe true
        mu.getDisplayUsername() shouldBe "josé"
        graph.preferenceStore.getString(Preference.privateKey("pref_mangasync_password_7"), "").get() shouldStartWith
            "dpapi:"
        mu.search("Berserk").single().title shouldBe "Berserk"
        mu.logout()
    }

    @Test
    fun `a wrong MangaUpdates password fails and saves nothing`(): Unit = runBlocking {
        val mu = graph.trackerManager.mangaUpdates
        responses["PUT /v1/account/login"] =
            { json("""{"status":"exception","reason":"Bad credentials"}""", code = 401) }

        shouldThrowAny { mu.login("josé", "mala") }

        mu.isLoggedIn shouldBe false
    }

    @Test
    fun `Kitsu signs in with its OAuth password grant and reads the account`(): Unit = runBlocking {
        val kitsu = graph.trackerManager.kitsu
        responses["POST /api/oauth/token"] = {
            json(
                """{"access_token":"a","created_at":${System.currentTimeMillis() / 1000},""" +
                    """"expires_in":3600,"refresh_token":"r"}""",
            )
        }
        responses["POST /api/graphql"] = {
            json("""{"data":{"currentAccount":{"id":"9","ratingSystem":"ADVANCED","profile":{"name":"José"}}}}""")
        }

        kitsu.login("jose@example.org", "clave")

        kitsu.isLoggedIn shouldBe true
        kitsu.getDisplayUsername() shouldBe "José"
        kitsu.logout()
    }

    @Test
    fun `a wrong Kitsu password fails and saves nothing`(): Unit = runBlocking {
        val kitsu = graph.trackerManager.kitsu
        responses["POST /api/oauth/token"] = { json("""{"error":"invalid_grant"}""", code = 400) }

        shouldThrowAny { kitsu.login("jose@example.org", "mala") }

        kitsu.isLoggedIn shouldBe false
    }
}
