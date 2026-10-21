package mihon.desktop.core.cef

import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@Tag("cef")
class WebViewHttpErrorTest {
    @Test
    fun `an HTTP error reaches onReceivedHttpError with its headers, like on Android`(): Unit = runBlocking {
        val graph = startWithRealCef()
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(403)
                    .setHeader("cf-mitigated", "challenge")
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><body>Comprobando</body></html>"),
            )
            start()
        }
        val error = CompletableDeferred<Pair<WebResourceRequest, WebResourceResponse>>()
        var webView: WebView? = null
        Handler(Looper.getMainLooper()).post {
            webView = WebView(graph.context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        if (request != null && errorResponse != null) error.complete(request to errorResponse)
                    }
                }
                loadUrl(server.url("/manga").toString())
            }
        }

        val (request, response) = withTimeout(30.seconds) { error.await() }

        request.isForMainFrame shouldBe true
        response.statusCode shouldBe 403
        response.responseHeaders["cf-mitigated"] shouldBe "challenge"
        Handler(Looper.getMainLooper()).post { webView?.destroy() }
        server.shutdown()
    }
}
