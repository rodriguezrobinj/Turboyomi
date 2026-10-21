package mihon.desktop.core.cef

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.core.DesktopGraph
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.time.Duration.Companion.seconds

@Tag("cef")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebViewScriptTest {
    private val server = MockWebServer()
    private lateinit var graph: DesktopGraph
    private lateinit var webView: WebView

    @BeforeAll
    fun start() {
        graph = startWithRealCef()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html><body>José</body></html>"))
        server.start()
        val loaded = CompletableDeferred<Unit>()
        Handler(Looper.getMainLooper()).post {
            webView = WebView(graph.context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        loaded.complete(Unit)
                    }
                }
                loadUrl(server.url("/").toString())
            }
        }
        runBlocking { withTimeout(30.seconds) { loaded.await() } }
    }

    @AfterAll
    fun stop() {
        Handler(Looper.getMainLooper()).post { webView.destroy() }
        server.shutdown()
    }

    /** What Android's evaluateJavascript hands its callback for [script]: the value as JSON. */
    private fun evaluate(script: String): String? {
        val result = CompletableDeferred<String?>()
        Handler(Looper.getMainLooper()).post { webView.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(10.seconds) { result.await() } }
    }

    @Test
    fun `a script's value reaches the callback as JSON, like on Android`() {
        // mangaup and hdoujin read objects; AndroidCompat used to drop them and leave them waiting
        evaluate("({ titulo: 'José', paginas: 3 })") shouldBe """{"titulo":"José","paginas":3}"""
        evaluate("'José'") shouldBe "\"José\""
        evaluate("2 + 2") shouldBe "4"
        evaluate("undefined") shouldBe "null"
    }

    @Test
    fun `a script on a destroyed WebView is ignored, like on Android`() {
        // Mihon's Cloudflare interceptor can get a late onPageFinished after it destroyed its WebView
        val done = CompletableDeferred<Result<Unit>>()
        Handler(Looper.getMainLooper()).post {
            done.complete(
                runCatching {
                    WebView(graph.context).apply {
                        destroy()
                        evaluateJavascript("1", null)
                    }
                    Unit
                },
            )
        }

        runBlocking { withTimeout(30.seconds) { done.await() } }.isSuccess shouldBe true
    }
}
