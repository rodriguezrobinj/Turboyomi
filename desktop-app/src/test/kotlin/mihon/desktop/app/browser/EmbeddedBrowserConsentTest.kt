package mihon.desktop.app.browser

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.desktop.core.cef.CefInstaller
import mihon.desktop.core.cef.FakeJbr
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalTestApi::class)
class EmbeddedBrowserConsentTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        server.shutdown()
        EmbeddedBrowser.dismissConsent()
        EmbeddedBrowser.close()
    }

    private fun ComposeUiTest.waitFor(text: String) =
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

    /** The dialog over an installer that downloads from [fake], after asking for a page in WebView. */
    private fun ComposeUiTest.show(fake: FakeJbr, onStart: () -> Unit = {}): CefInstaller {
        val graph = startTestApp()
        server.dispatcher = fake
        server.start()
        val installer =
            CefInstaller(createTempDirectory("cef José").resolve("cef"), url = server.url("/jbr.zip").toString())
        setContent { AppProviders(graph) { EmbeddedBrowserConsent(installer, onStart) } }
        runOnIdle { EmbeddedBrowser.open(graph.context, "https://example.org/manga") }
        waitFor(str(MR.strings.desktop_cef_download))
        return installer
    }

    @Test
    fun `the dialog says how big the download is, and installing opens the page asked for`() = runComposeUiTest {
        var started = false
        val installer = show(FakeJbr()) { started = true }
        waitFor("MB")

        onNodeWithText(str(MR.strings.desktop_cef_download)).performClick()
        waitUntil(timeoutMillis = 15_000) { EmbeddedBrowser.window != null }

        installer.isInstalled() shouldBe true
        started shouldBe true
        EmbeddedBrowser.window?.url shouldBe "https://example.org/manga"
        EmbeddedBrowser.consent.shouldBeNull()
    }

    @Test
    fun `not now closes it without downloading anything`() = runComposeUiTest {
        val installer = show(FakeJbr())

        onNodeWithText(str(MR.strings.desktop_cef_not_now)).performClick()
        waitForIdle()

        EmbeddedBrowser.consent.shouldBeNull()
        EmbeddedBrowser.window.shouldBeNull()
        installer.isInstalled() shouldBe false
    }

    @Test
    fun `a failed download says why and offers to try again`() = runComposeUiTest {
        show(FakeJbr().apply { checksum = "0".repeat(128) })

        onNodeWithText(str(MR.strings.desktop_cef_download)).performClick()

        waitFor(str(MR.strings.desktop_cef_install_failed, "").trim())
        onNodeWithText(str(MR.strings.action_retry)).assertExists()
    }

    @Test
    fun `cancelling a download in progress installs nothing`() = runComposeUiTest {
        val installer = show(FakeJbr().apply { slow = true })

        onNodeWithText(str(MR.strings.desktop_cef_download)).performClick()
        waitFor(str(MR.strings.desktop_cef_downloading))
        onNodeWithText(str(MR.strings.action_cancel)).performClick()
        waitForIdle()

        EmbeddedBrowser.consent.shouldBeNull()
        installer.isInstalled() shouldBe false
    }
}
