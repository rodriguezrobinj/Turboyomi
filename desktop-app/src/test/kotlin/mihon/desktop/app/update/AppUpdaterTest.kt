package mihon.desktop.app.update

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.testing.startTestApp
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration.Companion.hours

class AppUpdaterTest {
    private val server = MockWebServer().apply { start() }
    private val graph = startTestApp()
    private val updater = graph.appUpdater
    private val msiBytes = ByteArray(200_000) { (it % 251).toByte() }
    private val hash = MessageDigest.getInstance("SHA-256").digest(msiBytes).joinToString("") { "%02x".format(it) }
    private val launched = CopyOnWriteArrayList<List<String>>()
    private val opened = CopyOnWriteArrayList<String>()

    private open inner class FakeInstaller(
        private val installed: Boolean = true,
        private val development: Boolean = false,
    ) : UpdateInstaller(exe = { null }, localAppData = { null }) {
        override fun isInstalled() = installed
        override fun isDevelopment() = development
        override fun launch(command: List<String>) {
            launched += command
        }
    }

    init {
        updater.apiBase = server.url("/").toString().trimEnd('/')
        updater.install = FakeInstaller()
        updater.openPage = { opened += it }
    }

    @AfterEach
    fun stop() {
        updater.dismiss()
        server.shutdown()
        updates().toFile().deleteRecursively()
    }

    private fun release() = AppRelease(
        version = "v9.0.0",
        notes = "notas",
        page = "https://github.com/x/releases/tag/v9.0.0",
        msiName = "Turboyomi-9.0.0.msi",
        msiUrl = server.url("/Turboyomi-9.0.0.msi").toString(),
        sumsUrl = server.url("/SHA256SUMS").toString(),
    )

    private fun enqueueDownload(sums: String) {
        server.enqueue(MockResponse().setBody(Buffer().write(msiBytes)))
        server.enqueue(MockResponse().setBody(sums))
    }

    private fun updates() = graph.appDirs.cache.resolve("updates")

    private suspend fun waitFor(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            delay(50)
        }
        error("Condition not met in 10 s; state: ${updater.state.value}")
    }

    @Test
    fun `a newer release is offered`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v9.0.0","body":"b","html_url":"p","assets":[""" +
                    """{"name":"Turboyomi-9.0.0.msi","browser_download_url":"m"},""" +
                    """{"name":"SHA256SUMS","browser_download_url":"s"}]}""",
            ),
        )

        updater.check(manual = false)

        updater.state.value.shouldBeInstanceOf<UpdateState.Available>().release.version shouldBe "v9.0.0"
    }

    @Test
    fun `the automatic check says nothing when GitHub refuses, the manual one does`(): Unit = runBlocking {
        val messages = CopyOnWriteArrayList<String>()
        val collector = CoroutineScope(Dispatchers.IO).launch { AppMessages.messages.collect { messages += it.text } }
        delay(300)
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(403))

        updater.check(manual = false)
        delay(300)
        messages.shouldBeEmpty()
        updater.check(manual = true)
        waitFor { messages.isNotEmpty() }
        collector.cancel()
        updater.state.value shouldBe UpdateState.Idle
    }

    @Test
    fun `a verified download hands the msi to msiexec and the app goes into Installing`(): Unit = runBlocking {
        // The binary form "<hash> *<file>" and upper case, as other tools write it
        enqueueDownload("${hash.uppercase()} *Turboyomi-9.0.0.msi\nffff  Turboyomi-9.0.0.zip\n")

        updater.update(release())

        waitFor { updater.state.value == UpdateState.Installing }
        launched.single().first() shouldBe "powershell.exe"
        updates().resolve("Turboyomi-9.0.0.msi").toFile().readBytes().contentEquals(msiBytes) shouldBe true
    }

    @Test
    fun `a download that doesn't match its checksum is deleted and reported`(): Unit = runBlocking {
        enqueueDownload("${"0".repeat(64)}  Turboyomi-9.0.0.msi\n")

        updater.update(release())

        waitFor { updater.state.value is UpdateState.Failed }
        launched.shouldBeEmpty()
        updates().listDirectoryEntries().shouldBeEmpty()
    }

    @Test
    fun `cancelling a download removes the partial file`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(msiBytes)).throttleBody(10_000, 1, TimeUnit.SECONDS))

        updater.update(release())
        waitFor { (updater.state.value as? UpdateState.Downloading)?.progress != null }
        updater.cancel()

        waitFor { updater.state.value is UpdateState.Available }
        waitFor { updates().createDirectories().listDirectoryEntries().isEmpty() }
        launched.shouldBeEmpty()
    }

    @Test
    fun `a second click while downloading starts no second download`(): Unit = runBlocking {
        enqueueDownload("$hash  Turboyomi-9.0.0.msi\n")

        updater.update(release())
        updater.update(release())

        waitFor { updater.state.value == UpdateState.Installing }
        server.requestCount shouldBe 2 // one msi, one SHA256SUMS
        launched.size shouldBe 1
    }

    @Test
    fun `the daily check during a download leaves the download alone`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(msiBytes)).throttleBody(10_000, 1, TimeUnit.SECONDS))
        updater.update(release())
        waitFor { updater.state.value is UpdateState.Downloading }
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v9.0.0","body":"b","html_url":"p","assets":[""" +
                    """{"name":"Turboyomi-9.0.0.msi","browser_download_url":"m"},""" +
                    """{"name":"SHA256SUMS","browser_download_url":"s"}]}""",
            ),
        )

        updater.check(manual = false)

        updater.state.value.shouldBeInstanceOf<UpdateState.Downloading>()
    }

    @Test
    fun `outside the installed app, updating opens the release page`(): Unit = runBlocking {
        updater.install = FakeInstaller(installed = false)

        updater.update(release())

        opened shouldBe listOf("https://github.com/x/releases/tag/v9.0.0")
        server.requestCount shouldBe 0
    }

    @Test
    fun `the daily job follows the switch and stays off in development`() {
        graph.basePreferences.autoCheckAppUpdates.set(true)
        updater.periodicJob().interval() shouldBe 24.hours
        graph.basePreferences.autoCheckAppUpdates.set(false)
        updater.periodicJob().interval() shouldBe null
        graph.basePreferences.autoCheckAppUpdates.set(true)
        updater.install = FakeInstaller(development = true)
        updater.periodicJob().interval() shouldBe null
    }
}
