package mihon.desktop.app.browser

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.core.cef.CefRuntime
import okhttp3.Headers.Companion.headersOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.nulldev.androidcompat.webkit.CefHelper

class EmbeddedBrowserTest {
    private val graph = startTestApp()

    @AfterEach
    fun reset() {
        CefRuntime.markNotInstalled()
        EmbeddedBrowser.close()
        EmbeddedBrowser.dismissConsent()
    }

    @Test
    fun `without the browser, WebView asks to download it and then opens the page`() {
        EmbeddedBrowser.open(graph.context, "https://example.org/manga")

        EmbeddedBrowser.window.shouldBeNull()
        EmbeddedBrowser.consent.shouldNotBeNull().then?.url shouldBe "https://example.org/manga"

        EmbeddedBrowser.installed()
        EmbeddedBrowser.window?.url shouldBe "https://example.org/manga"
        EmbeddedBrowser.consent.shouldBeNull()
    }

    @Test
    fun `while the browser is still starting the window opens and waits for it`() {
        // What CefRuntime publishes while it starts in the background at launch
        CefHelper.cefApp.value = Result.success(null)

        EmbeddedBrowser.open(graph.context, "https://example.org")

        EmbeddedBrowser.window.shouldNotBeNull()
        EmbeddedBrowser.consent.shouldBeNull()
    }

    @Test
    fun `the source's user agent goes with the page`() {
        EmbeddedBrowser.open(graph.context, "https://example.org", headersOf("User-Agent", "Agente/1.0 (Jose)"))

        EmbeddedBrowser.consent?.then?.userAgent shouldBe "Agente/1.0 (Jose)"
    }

    @Test
    fun `installing from the settings opens no page`() {
        EmbeddedBrowser.requestInstall()
        EmbeddedBrowser.installed()

        EmbeddedBrowser.window.shouldBeNull()
    }
}
