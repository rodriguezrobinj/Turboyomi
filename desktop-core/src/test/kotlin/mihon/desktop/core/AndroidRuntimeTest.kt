package mihon.desktop.core

import android.os.Handler
import android.os.Looper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.io.path.createTempDirectory

/** Runtime pieces Android gives every app and extensions take for granted. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AndroidRuntimeTest {
    @BeforeAll
    fun start() {
        val root = createTempDirectory("runtime")
        Platform.start(AppDirs(root.resolve("data"), root.resolve("local")))
    }

    @Test
    fun `a main dispatcher exists`(): Unit = runBlocking {
        // cubari and mangaup switch to Dispatchers.Main
        withContext(Dispatchers.Main) { "on main" } shouldBe "on main"
    }

    @Test
    fun `the main looper runs posted work`(): Unit = runBlocking {
        // komga creates a Handler on the main looper in its constructor
        Looper.getMainLooper() shouldNotBe null
        val ran = CompletableDeferred<Boolean>()
        Handler(Looper.getMainLooper()).post { ran.complete(true) }
        withTimeout(5_000) { ran.await() } shouldBe true
    }
}
