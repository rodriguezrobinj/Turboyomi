package mihon.desktop.core.cef

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.core.DesktopGraph
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

@Tag("cef")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebViewCookiesTest {
    private val server = MockWebServer()
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        graph = startWithRealCef()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val script = if (request.path == "/galleta") "document.cookie = 'sesion=jose; path=/'" else ""
                return MockResponse().setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<html><body>José<script>$script</script></body></html>")
            }
        }
        server.start()
    }

    @AfterAll
    fun stop() = server.shutdown()

    /** Loads [url] in AndroidCompat's WebView, on the main looper as extensions do, until onPageFinished. */
    private fun load(url: String) {
        val finished = CompletableDeferred<Unit>()
        var webView: WebView? = null
        Handler(Looper.getMainLooper()).post {
            webView = WebView(graph.context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        finished.complete(Unit)
                    }
                }
                loadUrl(url)
            }
        }
        runBlocking { withTimeout(30.seconds) { finished.await() } }
        Handler(Looper.getMainLooper()).post { webView?.destroy() }
    }

    @Test
    fun `a cookie a page sets reaches the app's cookie jar`() {
        load(server.url("/galleta").toString())

        CookieManager.getInstance().getCookie(server.url("/").toString()).shouldNotBeNull() shouldContain "sesion=jose"
    }

    @Test
    fun `a page without cookies isn't held up waiting for them`() {
        measureTimeMillis { load(server.url("/nada").toString()) } shouldBeLessThan 10_000
    }
}
