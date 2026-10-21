package mihon.desktop.core.cef

import eu.kanade.tachiyomi.network.GET
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mihon.desktop.core.DesktopGraph
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import kotlin.system.measureTimeMillis

@Tag("cef")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CloudflareChallengeTest {
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        graph = startWithRealCef()
    }

    // Every site here is localhost, and cookies ignore ports: one test's clearance would pass the next one's challenge
    @BeforeEach
    fun clearCookies() = CefCookieSync.clear(url = null)

    private fun site(site: ChallengeSite) = MockWebServer().apply {
        dispatcher = site
        start()
    }

    private fun fetch(server: MockWebServer, path: String): String =
        graph.networkHelper.client.newCall(GET(server.url(path).toString())).execute().use { it.body.string() }

    @Test
    fun `a challenge the page solves on its own is passed`() {
        val challenge = ChallengeSite()
        val server = site(challenge)

        fetch(server, "/manga/1") shouldBe "contenido de José"
        challenge.browserChallenges.get() shouldBe 1
        server.shutdown()
    }

    @Test
    fun `two requests to the same site at once solve one challenge`(): Unit = runBlocking {
        val challenge = ChallengeSite()
        val server = site(challenge)

        val bodies = listOf("/manga/1", "/manga/2").map { async(Dispatchers.IO) { fetch(server, it) } }.awaitAll()

        bodies shouldBe listOf("contenido de José", "contenido de José")
        challenge.browserChallenges.get() shouldBe 1
        server.shutdown()
    }

    @Test
    fun `a challenge that needs a click fails fast with Mihon's message`() {
        val server = site(ChallengeSite(interactive = true))

        val elapsed = measureTimeMillis {
            val error = shouldThrow<IOException> { fetch(server, "/manga/1") }
            error.message shouldBe graph.context.stringResource(MR.strings.information_cloudflare_bypass_failure)
        }

        // The page said so after 1 s: well before the interceptor's 30 s wait
        elapsed shouldBeLessThan 20_000
        server.shutdown()
    }

    @Test
    fun `when the browser isn't challenged it gives up at once, as Mihon does`() {
        val server = site(ChallengeSite(browserPasses = true))

        val elapsed = measureTimeMillis { shouldThrow<IOException> { fetch(server, "/manga/1") } }

        // Not the interceptor's 30 s wait: the browser's page had no challenge to solve
        elapsed shouldBeLessThan 20_000
        server.shutdown()
    }
}
