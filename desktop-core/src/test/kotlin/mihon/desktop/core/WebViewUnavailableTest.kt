package mihon.desktop.core

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.io.path.createTempDirectory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebViewUnavailableTest {
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        val root = createTempDirectory("webview")
        graph = Platform.start(AppDirs(root.resolve("data"), root.resolve("local")))
    }

    private fun onMain(block: () -> Unit): CompletableDeferred<Result<Unit>> =
        CompletableDeferred<Result<Unit>>().also { done ->
            Handler(Looper.getMainLooper()).post { done.complete(runCatching(block)) }
        }

    @Test
    fun `creating a WebView fails fast and keeps the main looper free`(): Unit = runBlocking {
        // cubari and mangaup build a WebView on the main thread; without KCEF it used to wait forever there,
        // freezing the looper every other extension posts to
        val created = withTimeout(5_000) { onMain { WebView(graph.context) }.await() }
        created.isFailure shouldBe true
        created.exceptionOrNull()!!.stackTraceToString() shouldContain "WebView is not available"

        withTimeout(5_000) { onMain {}.await() }.isSuccess shouldBe true
    }
}
