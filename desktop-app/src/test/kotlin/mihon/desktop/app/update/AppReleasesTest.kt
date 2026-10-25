package mihon.desktop.app.update

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.startTestApp
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class AppReleasesTest {
    private val server = MockWebServer().apply { start() }
    private val graph = startTestApp()
    private val releases = AppReleases(graph.networkHelper, graph.json)
    private val base = server.url("/").toString().trimEnd('/')

    @AfterEach
    fun stop() = server.shutdown()

    private fun release(vararg assets: String) =
        """{"tag_name":"v0.2.0","body":"## Novedades\n- algo","html_url":"https://github.com/r/t/releases/tag/v0.2.0",""" +
            """"assets":[${assets.joinToString { """{"name":"$it","browser_download_url":"https://dl/$it","size":1}""" }}],""" +
            """"draft":false}"""

    @Test
    fun `the latest release gives its msi and its checksums`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody(release("Turboyomi-0.2.0.zip", "Turboyomi-0.2.0.msi", "SHA256SUMS")))

        val latest = releases.latest(base)!!

        server.takeRequest().path shouldBe "/repos/rodriguezrobinj/turboyomi/releases/latest"
        latest.version shouldBe "v0.2.0"
        latest.notes shouldBe "## Novedades\n- algo"
        latest.page shouldBe "https://github.com/r/t/releases/tag/v0.2.0"
        latest.msiName shouldBe "Turboyomi-0.2.0.msi"
        latest.msiUrl shouldBe "https://dl/Turboyomi-0.2.0.msi"
        latest.sumsUrl shouldBe "https://dl/SHA256SUMS"
    }

    @Test
    fun `a release without its msi or its checksums is no update`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody(release("Turboyomi-0.2.0.zip", "SHA256SUMS")))
        server.enqueue(MockResponse().setBody(release("Turboyomi-0.2.0.msi")))

        releases.latest(base).shouldBeNull()
        releases.latest(base).shouldBeNull()
    }

    @Test
    fun `GitHub's rate limit comes out as an error`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"API rate limit exceeded"}"""))

        shouldThrowAny { releases.latest(base) }
    }
}
