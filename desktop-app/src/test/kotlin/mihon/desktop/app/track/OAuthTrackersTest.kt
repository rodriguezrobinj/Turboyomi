package mihon.desktop.app.track

import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.comick.ComickApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

class OAuthTrackersTest {
    // Keys must be in place before the graph builds TrackerManager (once per test JVM)
    private val graph: TestAppGraph = TrackerKeys.withTestKeys { startTestApp() }

    @Test
    fun `with keys the seven OAuth trackers are registered with Mihon's ids`() {
        mapOf(
            1L to "MyAnimeList",
            2L to "AniList",
            4L to "Shikimori",
            5L to "Bangumi",
            10L to "Hikka",
            11L to "MangaBaka",
            12L to "Comick",
        ).forEach { (id, name) ->
            graph.trackerManager.get(id)!!.name shouldBe name
        }
    }

    @Test
    fun `the authorization urls carry our client id and the mangareader redirect`() {
        AnilistApi.authUrl().toString() shouldContain "client_id=test-anilist"
        MyAnimeListApi.authUrl(state = "s1").toString().let {
            it shouldStartWith "https://myanimelist.net/v1/oauth2/authorize"
            it shouldContain "client_id=test-mal"
            it shouldContain "state=s1"
            it shouldContain "code_challenge="
        }
        ComickApi.authUrl().toString() shouldContain "redirect_uri=app.mangareader%3A%2F%2Fcomick-auth"
    }

    @Test
    fun `the Uri shim builds paths and encodes query values once`() {
        val url = "https://api.example.org/v2/manga".toUri().buildUpon()
            .appendPath("42")
            .appendQueryParameter("q", "José + María")
            .build()

        url.toString() shouldBe "https://api.example.org/v2/manga/42?q=Jos%C3%A9%20%2B%20Mar%C3%ADa"
    }
}
