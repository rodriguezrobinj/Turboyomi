package mihon.desktop.core.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalPathApi::class)
class CefInstallerTest {
    private val server = MockWebServer()
    private lateinit var root: Path

    @BeforeEach
    fun setUp() {
        server.start()
        root = createTempDirectory("cef José")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        root.deleteRecursively()
    }

    private fun installer(fake: FakeJbr): CefInstaller {
        server.dispatcher = fake
        return CefInstaller(root.resolve("cef"), url = server.url("/jbr.zip").toString())
    }

    // Whatever install left next to the install folder: its staging folder must be gone
    private fun leftovers() = root.listDirectoryEntries().map { it.name }.filter { it != "cef" }

    @Test
    fun `installs the browser, and then it counts as installed`(): Unit = runBlocking {
        val installer = installer(FakeJbr())
        val progress = mutableListOf<Float>()

        installer.install { progress += it }

        installer.isInstalled() shouldBe true
        root.resolve("cef/libcef.dll").exists() shouldBe true
        root.resolve("cef/icudtl.dat").exists() shouldBe true
        installer.installedVersion() shouldBe CefRelease.JCEF_VERSION
        progress.last() shouldBe 1f
        progress.zipWithNext().all { (a, b) -> b >= a } shouldBe true
        leftovers().shouldBeEmpty()
    }

    @Test
    fun `a download that doesn't match its checksum installs nothing`(): Unit = runBlocking {
        val installer = installer(FakeJbr().apply { checksum = "0".repeat(128) })

        shouldThrow<IOException> { installer.install() }

        installer.isInstalled() shouldBe false
        root.resolve("cef").exists() shouldBe false
        leftovers().shouldBeEmpty()
    }

    @Test
    fun `an entry that climbs out of the folder is refused`(): Unit = runBlocking {
        val installer = installer(FakeJbr(extraEntry = "../../evil.txt"))

        shouldThrow<IOException> { installer.install() }

        root.resolve("evil.txt").exists() shouldBe false
        installer.isInstalled() shouldBe false
    }

    @Test
    fun `a download cut halfway leaves it not installed`(): Unit = runBlocking {
        val installer = installer(FakeJbr().apply { disconnect = true })

        shouldThrow<IOException> { installer.install() }

        installer.isInstalled() shouldBe false
        leftovers().shouldBeEmpty()
    }

    @Test
    fun `a build of another JCEF version isn't installed`(): Unit = runBlocking {
        val installer = installer(FakeJbr(version = "143.0.1-gabcdef1-chromium-143.0.7499.1-api-1.20-1-b1"))

        shouldThrow<IOException> { installer.install() }

        installer.isInstalled() shouldBe false
    }

    @Test
    fun `an install whose release file declares another version doesn't count`() {
        root.resolve("cef").createDirectories().resolve("release")
            .writeText("JCEF_VERSION_DETAILED=\"143.0.1-gabcdef1-chromium-143.0.7499.1\"\n")

        installer(FakeJbr()).isInstalled() shouldBe false
    }

    @Test
    fun `cancelling during the download leaves nothing behind`(): Unit = runBlocking {
        val installer = installer(FakeJbr().apply { slow = true })
        val downloading = CompletableDeferred<Unit>()

        val job = launch(Dispatchers.IO) { installer.install { if (it > 0f) downloading.complete(Unit) } }
        withTimeout(10.seconds) { downloading.await() }
        job.cancelAndJoin()

        installer.isInstalled() shouldBe false
        leftovers().shouldBeEmpty()
    }

    @Test
    fun `removing it while it runs waits for the next start`(): Unit = runBlocking {
        val installer = installer(FakeJbr())
        installer.install()

        installer.uninstall(running = true)
        installer.isInstalled() shouldBe false
        installer.isRemovalPending() shouldBe true
        root.resolve("cef").exists() shouldBe true

        installer.applyPendingRemoval()
        root.resolve("cef").exists() shouldBe false
        installer.isRemovalPending() shouldBe false
    }

    @Test
    fun `removing it when it doesn't run deletes it at once`(): Unit = runBlocking {
        val installer = installer(FakeJbr())
        installer.install()

        installer.uninstall(running = false)

        root.resolve("cef").exists() shouldBe false
        installer.isRemovalPending() shouldBe false
    }

    @Test
    fun `the download size comes from the server`(): Unit = runBlocking {
        val fake = FakeJbr()
        installer(fake).downloadSize() shouldBe fake.zip.size.toLong()
    }

    @Test
    fun `removing it while its files are in use waits for the next start instead of failing`(): Unit = runBlocking {
        val installer = installer(FakeJbr())
        installer.install()

        // What a failed start leaves behind: its DLLs loaded, which Windows won't let anyone delete
        FileInputStream(root.resolve("cef/libcef.dll").toFile()).use { installer.uninstall(running = false) }

        installer.isInstalled() shouldBe false
        installer.isRemovalPending() shouldBe true
        installer.applyPendingRemoval()
        root.resolve("cef").exists() shouldBe false
    }

    @Test
    fun `installing over files in use says to restart the app`(): Unit = runBlocking {
        val installer = installer(FakeJbr())
        installer.install()

        val error = FileInputStream(root.resolve("cef/libcef.dll").toFile()).use {
            shouldThrow<IOException> { installer.install() }
        }

        error.message.shouldNotBeNull() shouldContain "restart"
        // What was left of it goes at the next start
        installer.isRemovalPending() shouldBe true
        leftovers().filter { !it.endsWith(".remove") }.shouldBeEmpty()
    }
}
