package mihon.desktop.app.track

import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListInterceptor
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.network.GET
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackLoginHandlerTest {
    private val graph: TestAppGraph = TrackerKeys.withTestKeys { startTestApp() }
    private val handler get() = graph.trackLoginHandler
    private val server = MockWebServer()
    private val responses = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()
    private val notices = CopyOnWriteArrayList<Notice>()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @BeforeAll
    fun redirect() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // The interceptor below moved the service's host into the path: "POST bgm.tv/oauth/access_token"
                val key = "${request.method} ${request.requestUrl!!.encodedPath.removePrefix("/")}"
                requests += key to request.body.readUtf8()
                return responses[key]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val local = server.url("/")
        BaseTracker.testInterceptor = Interceptor { chain ->
            val original = chain.request().url
            val url = original.newBuilder().scheme("http").host(local.host).port(local.port)
                .encodedPath("/" + original.host + original.encodedPath).build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }
    }

    @AfterAll
    fun stop() {
        BaseTracker.testInterceptor = null
        server.shutdown()
    }

    @BeforeEach
    fun capture() {
        notices.clear()
        requests.clear()
        TrayNotifier.sink = { notices += it }
    }

    private fun stateOf(url: String) = url.toHttpUrl().queryParameter("state")!!

    private fun sent(path: String) = requests.single { it.first.endsWith(path) }.second

    @Test
    fun `AniList takes the implicit token from the fragment and reads the viewer`(): Unit = runBlocking {
        responses["POST graphql.anilist.co/"] = {
            json("""{"data":{"Viewer":{"id":5,"name":"José","mediaListOptions":{"scoreFormat":"POINT_10"}}}}""")
        }
        handler.authorizationUrl("anilist")

        handler.handle("turboyomi://anilist-auth#access_token=tok%2Ben+x&token_type=Bearer&expires_in=31536000")

        val aniList = graph.trackerManager.aniList!!
        aniList.isLoggedIn shouldBe true
        // Decoded once: %2B is a plus, and a literal plus stays a plus
        aniList.getPassword() shouldContain "tok+en+x"
        notices.single().isError shouldBe false
        aniList.logout()
    }

    @Test
    fun `MyAnimeList exchanges the code with the PKCE verifier of its authorization url`(): Unit = runBlocking {
        responses["POST myanimelist.net/v1/oauth2/token"] = {
            json("""{"refresh_token":"r","access_token":"a","expires_in":3600}""")
        }
        responses["GET api.myanimelist.net/v2/users/@me"] = { json("""{"name":"josé"}""") }
        val auth = handler.authorizationUrl("myanimelist")!!
        val challenge = auth.toHttpUrl().queryParameter("code_challenge")!!

        handler.handle("turboyomi://myanimelist-auth?code=c1&state=${stateOf(auth)}")

        val form = sent("myanimelist.net/v1/oauth2/token")
        form shouldContain "code=c1"
        // MAL's PKCE is "plain": the verifier sent is the challenge of the authorization url
        form shouldContain "code_verifier=$challenge"
        graph.trackerManager.myAnimeList!!.isLoggedIn shouldBe true
        graph.trackerManager.myAnimeList!!.logout()
    }

    @Test
    fun `Bangumi exchanges the code with its secret and our redirect`(): Unit = runBlocking {
        responses["POST bgm.tv/oauth/access_token"] = {
            json("""{"access_token":"a","expires_in":3600,"refresh_token":"r"}""")
        }
        responses["GET api.bgm.tv/v0/me"] = { json("""{"username":"jose","nickname":"José"}""") }
        val auth = handler.authorizationUrl("bangumi")!!

        handler.handle("turboyomi://bangumi-auth?code=c2&state=${stateOf(auth)}")

        val form = sent("bgm.tv/oauth/access_token")
        form shouldContain "client_secret=test-bangumi-secret"
        form shouldContain "redirect_uri=turboyomi%3A%2F%2Fbangumi-auth"
        graph.trackerManager.bangumi!!.isLoggedIn shouldBe true
        graph.trackerManager.bangumi!!.logout()
    }

    @Test
    fun `Comick takes its own state and PKCE through app turboyomi`(): Unit = runBlocking {
        responses["POST comick.dev/api/auth/oauth2/token"] = {
            json(
                """{"access_token":"a","expires_at":${System.currentTimeMillis() / 1000 + 3600},""" +
                    """"refresh_token":"r","scope":"library:read library:write offline_access"}""",
            )
        }
        responses["GET api.comick.dev/integrations/v1/me"] = { json("""{"id":"u1","username":"jose"}""") }
        val auth = handler.authorizationUrl("comick")!!

        handler.handle("app.turboyomi://comick-auth?code=c3&state=${stateOf(auth)}")

        graph.trackerManager.comick!!.isLoggedIn shouldBe true
        graph.trackerManager.comick!!.logout()
    }

    @Test
    fun `Hikka takes its reference for a token`(): Unit = runBlocking {
        responses["POST api.hikka.io/auth/token"] = { json("""{"secret":"a","expiration":9999999999,"created":1}""") }
        responses["GET api.hikka.io/user/me"] = { json("""{"reference":"u1","username":"jose"}""") }
        handler.authorizationUrl("hikka")

        handler.handle("turboyomi://hikka-auth?reference=ref-1")

        sent("api.hikka.io/auth/token") shouldContain "ref-1"
        graph.trackerManager.hikka!!.isLoggedIn shouldBe true
        graph.trackerManager.hikka!!.logout()
    }

    @Test
    fun `a redirect without a pending login is ignored with a notice to try again`(): Unit = runBlocking {
        handler.handle("turboyomi://anilist-auth#access_token=intruso")

        graph.trackerManager.aniList!!.isLoggedIn shouldBe false
        notices.single().isError shouldBe true
    }

    @Test
    fun `a wrong state is ignored, and the right one still works afterwards`(): Unit = runBlocking {
        responses["POST bgm.tv/oauth/access_token"] = {
            json("""{"access_token":"a","expires_in":3600,"refresh_token":"r"}""")
        }
        responses["GET api.bgm.tv/v0/me"] = { json("""{"username":"jose","nickname":null}""") }
        val auth = handler.authorizationUrl("bangumi")!!

        handler.handle("turboyomi://bangumi-auth?code=c&state=otro")
        graph.trackerManager.bangumi!!.isLoggedIn shouldBe false
        requests.none { it.first.endsWith("oauth/access_token") } shouldBe true

        handler.handle("turboyomi://bangumi-auth?code=c&state=${stateOf(auth)}")
        graph.trackerManager.bangumi!!.isLoggedIn shouldBe true
        graph.trackerManager.bangumi!!.logout()
    }

    @Test
    fun `a cancelled login warns and saves nothing, and the same redirect twice is handled once`(): Unit = runBlocking {
        val auth = handler.authorizationUrl("shikimori")!!

        handler.handle("turboyomi://shikimori-auth?error=access_denied&state=${stateOf(auth)}")
        handler.handle("turboyomi://shikimori-auth?error=access_denied&state=${stateOf(auth)}")

        graph.trackerManager.shikimori!!.isLoggedIn shouldBe false
        // The first warns with the service's error; the second finds no pending login and says so
        notices.size shouldBe 2
        notices[0].message shouldContain "access_denied"
    }

    @Test
    fun `two logins open at once each close only their own`(): Unit = runBlocking {
        responses["POST graphql.anilist.co/"] = {
            json("""{"data":{"Viewer":{"id":5,"name":"José","mediaListOptions":{"scoreFormat":"POINT_10"}}}}""")
        }
        handler.authorizationUrl("anilist")
        handler.authorizationUrl("hikka")

        handler.handle("turboyomi://anilist-auth#access_token=t")

        graph.trackerManager.aniList!!.isLoggedIn shouldBe true
        graph.pendingLogins.take("hikka", null) shouldBe PendingLogins.Take.Ok
        graph.trackerManager.aniList!!.logout()
    }

    @Test
    fun `Shikimori exchanges the code with its secret and reads the user through GraphQL`(): Unit = runBlocking {
        responses["POST shikimori.io/oauth/token"] = {
            json(
                """{"access_token":"a","created_at":${System.currentTimeMillis() / 1000},"expires_in":86400,"refresh_token":"r"}""",
            )
        }
        responses["POST shikimori.io/api/graphql"] =
            { json("""{"data":{"currentUser":{"id":"7","nickname":"José"}}}""") }
        val auth = handler.authorizationUrl("shikimori")!!

        handler.handle("turboyomi://shikimori-auth?code=c4&state=${stateOf(auth)}")

        sent("shikimori.io/oauth/token") shouldContain "client_secret=test-shikimori-secret"
        graph.trackerManager.shikimori!!.isLoggedIn shouldBe true
        graph.trackerManager.shikimori!!.logout()
    }

    @Test
    fun `MangaBaka takes its own state and PKCE`(): Unit = runBlocking {
        responses["POST mangabaka.org/auth/oauth2/token"] = {
            json(
                """{"access_token":"a","refresh_token":"r","expires_at":${System.currentTimeMillis() / 1000 + 3600}}""",
            )
        }
        responses["GET api.mangabaka.org/v1/my/profile"] = {
            json("""{"data":{"id":"u1","rating_steps":1,"nickname":"José","preferred_username":"jose"}}""")
        }
        val auth = handler.authorizationUrl("mangabaka")!!

        handler.handle("turboyomi://mangabaka-auth?code=c5&state=${stateOf(auth)}")

        sent("mangabaka.org/auth/oauth2/token") shouldContain "code_verifier="
        graph.trackerManager.mangaBaka!!.isLoggedIn shouldBe true
        graph.trackerManager.mangaBaka!!.logout()
    }

    /**
     * MyAnimeList refreshes inside its interceptor (chain.proceed), which goes on through the interceptors after it:
     * here the redirect to the test server comes after MAL's, so the real refresh code is what runs.
     */
    private fun malClientWithAStaleToken(): OkHttpClient {
        val interceptor = MyAnimeListInterceptor(graph.trackerManager.myAnimeList!!)
        // expires_in 0: the token is already stale, so the next request refreshes it first
        interceptor.setAuth(MALOAuth(refreshToken = "r", accessToken = "a", expiresIn = 0))
        val local = server.url("/")
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                val original = chain.request().url
                val url = original.newBuilder().scheme("http").host(local.host).port(local.port)
                    .encodedPath("/" + original.host + original.encodedPath).build()
                chain.proceed(chain.request().newBuilder().url(url).build())
            }
            .build()
    }

    @Test
    fun `MyAnimeList refreshes a stale token with its refresh token`() {
        val mal = graph.trackerManager.myAnimeList!!
        responses["POST myanimelist.net/v1/oauth2/token"] = {
            json("""{"refresh_token":"r2","access_token":"a2","expires_in":3600}""")
        }
        responses["GET api.myanimelist.net/v2/users/@me"] = { json("""{"name":"josé"}""") }

        malClientWithAStaleToken().newCall(GET("https://api.myanimelist.net/v2/users/@me")).execute().use {
            it.code shouldBe 200
        }

        sent("myanimelist.net/v1/oauth2/token") shouldContain "grant_type=refresh_token"
        graph.trackPreferences.trackAuthExpired(mal).get() shouldBe false
    }

    @Test
    fun `MyAnimeList marks the session expired when its refresh is refused`() {
        val mal = graph.trackerManager.myAnimeList!!
        responses["POST myanimelist.net/v1/oauth2/token"] = { json("""{"error":"invalid_grant"}""", code = 401) }

        runCatching { malClientWithAStaleToken().newCall(GET("https://api.myanimelist.net/v2/users/@me")).execute() }

        graph.trackPreferences.trackAuthExpired(mal).get() shouldBe true
        graph.trackPreferences.trackAuthExpired(mal).set(false)
    }
}
