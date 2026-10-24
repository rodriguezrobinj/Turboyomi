package mihon.desktop.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.Socket
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText

class SingleInstanceTest {
    @Test
    fun `second instance forwards its arguments to the first`(): Unit = runBlocking {
        val lock = createTempDirectory("single").resolve(".lock")
        val first = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        val received = CompletableDeferred<List<String>>()
        val job = first.instance.listen(CoroutineScope(Dispatchers.IO)) { received.complete(it) }

        SingleInstance.acquire(lock, listOf("turboyomi://anilist-auth?code=1")) shouldBe
            SingleInstance.Result.Forwarded

        withTimeout(5_000) { received.await() } shouldBe listOf("turboyomi://anilist-auth?code=1")
        job.cancel()
        first.instance.close()
    }

    @Test
    fun `lock is released on close`() {
        val lock = createTempDirectory("single").resolve(".lock")
        val first = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        first.instance.close()
        lock.resolveSibling(".lock.port").exists() shouldBe false

        val again = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        again.instance.close()
    }

    @Test
    fun `connections without the token are ignored`(): Unit = runBlocking {
        val lock = createTempDirectory("single").resolve(".lock")
        val first = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        val received = Channel<List<String>>(Channel.UNLIMITED)
        val job = first.instance.listen(CoroutineScope(Dispatchers.IO)) { received.trySend(it) }

        // Any local process (or a web page) can reach a loopback port; it does not know the token
        val port = lock.resolveSibling(".lock.port").readText().substringBefore(':').toInt()
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.getOutputStream().bufferedWriter().use { it.appendLine("turboyomi://anilist-auth?code=forged") }
        }
        SingleInstance.acquire(lock, listOf("real")) shouldBe SingleInstance.Result.Forwarded

        withTimeout(5_000) { received.receive() } shouldBe listOf("real")
        received.tryReceive().getOrNull() shouldBe null
        job.cancel()
        first.instance.close()
    }

    @Test
    fun `a client that never closes does not block forwarding`(): Unit = runBlocking {
        val lock = createTempDirectory("single").resolve(".lock")
        val first = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        val received = CompletableDeferred<List<String>>()
        val job = first.instance.listen(CoroutineScope(Dispatchers.IO)) { received.complete(it) }

        val port = lock.resolveSibling(".lock.port").readText().substringBefore(':').toInt()
        Socket(InetAddress.getLoopbackAddress(), port).use {
            SingleInstance.acquire(lock, listOf("real")) shouldBe SingleInstance.Result.Forwarded
            withTimeout(10_000) { received.await() } shouldBe listOf("real")
        }
        job.cancel()
        first.instance.close()
    }

    @Test
    fun `a failing handler does not stop the listener`(): Unit = runBlocking {
        val lock = createTempDirectory("single").resolve(".lock")
        val first = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        val received = CompletableDeferred<List<String>>()
        val job = first.instance.listen(CoroutineScope(Dispatchers.IO)) {
            if (it == listOf("boom")) error("handler failed")
            received.complete(it)
        }

        SingleInstance.acquire(lock, listOf("boom")) shouldBe SingleInstance.Result.Forwarded
        SingleInstance.acquire(lock, listOf("real")) shouldBe SingleInstance.Result.Forwarded

        withTimeout(5_000) { received.await() } shouldBe listOf("real")
        job.cancel()
        first.instance.close()
    }

    @Test
    fun `an unreachable first instance is reported instead of crashing`() {
        val lock = createTempDirectory("single").resolve(".lock")
        val first = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        // First instance still starting up (or its port file was lost)
        lock.resolveSibling(".lock.port").deleteIfExists()

        SingleInstance.acquire(lock, listOf("x")) shouldBe SingleInstance.Result.Unreachable
        first.instance.close()
    }
}
