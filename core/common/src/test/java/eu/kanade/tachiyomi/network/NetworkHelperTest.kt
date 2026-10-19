package eu.kanade.tachiyomi.network

import android.content.Context
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import kotlin.io.path.createTempDirectory

class NetworkHelperTest {
    private fun helper(): NetworkHelper {
        val context = mockk<Context> { every { cacheDir } returns createTempDirectory("net").toFile() }
        val preferences = NetworkPreferences(InMemoryPreferenceStore(), isDebugBuild = false)
        return NetworkHelper(CoroutineScope(Dispatchers.IO), context, preferences)
    }

    @Test
    fun `client sends the configured user agent`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()

        val network = helper()
        network.client.newCall(GET(server.url("/").toString())).execute().use { it.code shouldBe 200 }

        server.takeRequest().getHeader("User-Agent") shouldBe network.defaultUserAgentProvider()
        server.shutdown()
    }

    @Test
    fun `cloudflare client is the regular client`() {
        val network = helper()
        @Suppress("DEPRECATION")
        network.cloudflareClient shouldBe network.client
        network.client.interceptors.map { it::class.simpleName } shouldContain "UserAgentInterceptor"
    }

    @Test
    fun `client has the interceptors extensions look up by class name`() {
        // keiyoushi extensions rebuild the client and throw "<Name> must be present in default client" otherwise
        helper().client.interceptors.map { it.javaClass.simpleName } shouldContainAll
            listOf("UncaughtExceptionInterceptor", "UserAgentInterceptor", "CloudflareInterceptor")
    }

    @Test
    fun `cloudflare interceptor passes requests through`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody("challenge"))
        server.start()

        helper().client.newCall(GET(server.url("/").toString())).execute().use { it.code shouldBe 503 }
        server.shutdown()
    }
}
