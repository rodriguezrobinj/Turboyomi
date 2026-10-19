package mihon.desktop.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class SingleInstanceTest {
    @Test
    fun `second instance forwards its arguments to the first`() = runBlocking {
        val lock = createTempDirectory("single").resolve(".lock")
        val first = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        val received = CompletableDeferred<List<String>>()
        val job = first.instance.listen(CoroutineScope(Dispatchers.IO)) { received.complete(it) }

        SingleInstance.acquire(lock, listOf("mangareader://anilist-auth?code=1")) shouldBe SingleInstance.Result.Forwarded

        withTimeout(5_000) { received.await() } shouldBe listOf("mangareader://anilist-auth?code=1")
        job.cancel()
        first.instance.close()
    }

    @Test
    fun `lock is released on close`() {
        val lock = createTempDirectory("single").resolve(".lock")
        val first = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        first.instance.close()

        val again = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        again.instance.close()
    }
}
