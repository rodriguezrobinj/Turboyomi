package mihon.desktop.core

import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.network.GET
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import mihon.desktop.core.cef.ChallengeSite
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.io.path.createTempDirectory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CloudflareWithoutBrowserTest {
    private val server = MockWebServer()
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        val root = createTempDirectory("cloudflare")
        graph = Platform.start(AppDirs(root.resolve("data"), root.resolve("local")))
        server.dispatcher = ChallengeSite()
        server.start()
    }

    @AfterAll
    fun stop() = server.shutdown()

    @Test
    fun `a challenge without the embedded browser says the browser is missing`() {
        val error = shouldThrow<EmbeddedBrowserUnavailableException> {
            graph.networkHelper.client.newCall(GET(server.url("/manga/1").toString())).execute()
        }

        error.reason shouldBe EmbeddedBrowserUnavailableException.Reason.NOT_INSTALLED
    }
}
