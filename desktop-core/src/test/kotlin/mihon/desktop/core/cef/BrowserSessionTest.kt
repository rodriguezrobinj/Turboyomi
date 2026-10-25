package mihon.desktop.core.cef

import android.webkit.CookieManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.awt.Point
import java.awt.Robot
import java.awt.event.InputEvent
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

@Tag("cef")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrowserSessionTest {
    private val server = MockWebServer()

    @BeforeAll
    fun start() {
        startWithRealCef()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(
                    when (request.path) {
                        "/otra" -> "<html><head><title>Otra</title></head></html>"
                        // A button over the whole page: a real click on it is the user gesture Chromium wants
                        // before it lets a page open a window
                        "/emergente" ->
                            "<html><head><title>Emergente</title></head><body style='margin:0'>" +
                                "<button style='width:100vw;height:100vh' " +
                                "onclick=\"window.open('/otra')\">Abrir</button>" +
                                "</body></html>"
                        else ->
                            "<html><head><title>Hola José</title></head>" +
                                "<body><script>document.cookie = 'login=si; path=/'</script></body></html>"
                    },
                )
        }
        server.start()
    }

    @AfterAll
    fun stop() = server.shutdown()

    /** A session shown in a real window, as the WebView window shows it (spec 2b risk 2). */
    private fun <T> shown(
        path: String,
        userAgent: String? = null,
        block: suspend (BrowserSession, JFrame) -> T,
    ): T = runBlocking {
        val app = CefHelper.waitForInit().first()
        val session = BrowserSession(app, server.url(path).toString(), userAgent)
        lateinit var frame: JFrame
        SwingUtilities.invokeAndWait {
            frame = JFrame().apply {
                add(session.browser.uiComponent)
                setSize(800, 600)
                // Windows keeps a window a background process opens behind the active one; the click must reach it
                isAlwaysOnTop = true
                isVisible = true
            }
        }
        try {
            block(session, frame)
        } finally {
            session.dispose()
            SwingUtilities.invokeAndWait { frame.dispose() }
        }
    }

    @Test
    fun `the window's browser shows the page with the source's user agent and brings back its cookies`(): Unit =
        shown("/", userAgent = "Agente/1.0 (Jose)") { session, _ ->
            withTimeout(30.seconds) { session.title.first { it == "Hola José" } }
            // The queue also holds the other test's requests
            val page = generateSequence { server.takeRequest(5, TimeUnit.SECONDS) }.first { it.path == "/" }
            page.getHeader("User-Agent") shouldBe "Agente/1.0 (Jose)"
            withTimeout(10.seconds) {
                while (CookieManager.getInstance().getCookie(server.url("/").toString())?.contains("login=si") !=
                    true
                ) {
                    delay(100)
                }
            }
        }

    @Test
    fun `a link that opens a new window loads it in the same browser`() {
        // A real mouse click: GitHub's runners don't deliver Robot input to a native window reliably
        assumeTrue(System.getenv("CI") == null, "Needs real input, which CI runners don't give")
        newWindowLink()
    }

    private fun newWindowLink(): Unit = shown("/emergente") { session, frame ->
        withTimeout(30.seconds) { session.title.first { it == "Emergente" } }

        // A real click, as the user's: the page's button covers the whole window
        val center = frame.contentPane.locationOnScreen.let { Point(it.x + 400, it.y + 280) }
        Robot().apply {
            mouseMove(center.x, center.y)
            mousePress(InputEvent.BUTTON1_DOWN_MASK)
            mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        }

        withTimeout(30.seconds) { session.title.first { it == "Otra" } }
    }
}
