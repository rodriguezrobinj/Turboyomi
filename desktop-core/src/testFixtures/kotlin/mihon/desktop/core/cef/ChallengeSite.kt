package mihon.desktop.core.cef

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.atomic.AtomicInteger

/**
 * A site behind a Cloudflare-like challenge: 403 with `cf-mitigated: challenge` until a request brings
 * `cf_clearance=ok`, which the challenge page sets by JavaScript. With [interactive] the page asks for a click instead,
 * telling the interceptor so the way Cloudflare's page does. With [browserPasses] only OkHttp is challenged: the
 * browser gets the page straight away.
 */
class ChallengeSite(
    private val interactive: Boolean = false,
    private val browserPasses: Boolean = false,
) : Dispatcher() {
    /** Challenge pages served: to OkHttp and to the browser. */
    val challenges = AtomicInteger()

    /** Challenge pages served to the embedded browser (Chromium sends `Sec-Fetch-Mode`; OkHttp doesn't). */
    val browserChallenges = AtomicInteger()

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.getHeader("Cookie")?.contains("cf_clearance=ok") == true) {
            return MockResponse().setBody("contenido de José")
        }
        if (browserPasses && request.getHeader("Sec-Fetch-Mode") != null) return MockResponse().setBody("portada")
        challenges.incrementAndGet()
        if (request.getHeader("Sec-Fetch-Mode") != null) browserChallenges.incrementAndGet()
        val script = if (interactive) {
            """setTimeout(() => window.postMessage({ source: "cloudflare-challenge", event: "interactiveBegin" }, "*"), 1000)"""
        } else {
            """document.cookie = "cf_clearance=ok; path=/""""
        }
        return MockResponse().setResponseCode(403)
            .setHeader("cf-mitigated", "challenge")
            .setHeader("Server", "cloudflare")
            .setHeader("Content-Type", "text/html; charset=utf-8")
            .setBody("<html><body>Comprobando<script>$script</script></body></html>")
    }
}
