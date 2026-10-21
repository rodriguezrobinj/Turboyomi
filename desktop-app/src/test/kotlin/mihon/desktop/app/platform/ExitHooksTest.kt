package mihon.desktop.app.platform

import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class ExitHooksTest {
    @Test
    fun `every hook runs even when one fails, and a slow one can't hold the exit`(): Unit = runBlocking {
        val ran = mutableListOf<String>()
        val failing: suspend () -> Unit = { error("falla") }
        val working: suspend () -> Unit = { ran += "guardado" }
        val stuck: suspend () -> Unit = { delay(60_000) }
        listOf(failing, working, stuck).forEach(ExitHooks::add)

        val elapsed = measureTime { ExitHooks.runAll(timeout = 300.milliseconds) }

        ran shouldBe listOf("guardado")
        elapsed.inWholeMilliseconds shouldBeLessThan 5_000
        listOf(failing, working, stuck).forEach(ExitHooks::remove)
    }
}
