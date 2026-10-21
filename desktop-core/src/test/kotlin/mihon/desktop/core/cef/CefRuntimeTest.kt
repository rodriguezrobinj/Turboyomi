package mihon.desktop.core.cef

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import xyz.nulldev.androidcompat.webkit.CefHelper
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

@Tag("cef")
class CefRuntimeTest {
    @Test
    fun `the embedded browser starts from the downloaded build`(): Unit = runBlocking {
        startWithRealCef()

        val installed = CefInstaller(Path(System.getProperty("cef.dir"))).installedVersion().shouldNotBeNull()
        installed.substringBefore("-chromium") shouldBe CefRelease.JCEF_VERSION.substringBefore("-chromium")
        withTimeout(30.seconds) { CefHelper.waitForInit().first() }.shouldNotBeNull()
    }
}
