package mihon.desktop.core.cef

import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException.Reason
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import xyz.nulldev.androidcompat.webkit.CefHelper
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds

class CefRuntimeStartTest {
    @Test
    fun `right after the download it counts as starting, so the window waits instead of saying it's missing`(): Unit =
        runBlocking {
            CefRuntime.markNotInstalled()
            val empty = createTempDirectory("cef José")

            CefRuntime.startAsync(empty, empty.resolve("cache"))

            // What the WebView window sees at once: starting (success, no app yet), not "not installed"
            val now = CefHelper.cefApp.value
            now.isSuccess shouldBe true
            now.getOrNull().shouldBeNull()

            // An empty folder has no browser to start: it ends as "didn't start"
            withTimeout(30.seconds) { while (CefHelper.cefApp.value.isSuccess) delay(50) }
            CefHelper.cefApp.value.exceptionOrNull()
                .shouldBeInstanceOf<EmbeddedBrowserUnavailableException>().reason shouldBe Reason.NOT_STARTED
        }
}
