package mihon.desktop.core.cef

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.writeText

class MoveWithRetryTest {
    @Test
    fun `a folder another program briefly holds a file of is still moved`(): Unit = runBlocking {
        val root = createTempDirectory("move José")
        val from = root.resolve("layout").createDirectories()
        from.resolve("libcef.dll").writeText("dll")
        // What Windows Defender does while it scans a new DLL: the folder can't be renamed until it lets go
        val held = from.resolve("libcef.dll").inputStream()
        thread {
            Thread.sleep(400)
            held.close()
        }

        moveWithRetry(from, root.resolve("cef"))

        root.resolve("cef/libcef.dll").exists() shouldBe true
        from.exists() shouldBe false
    }
}
