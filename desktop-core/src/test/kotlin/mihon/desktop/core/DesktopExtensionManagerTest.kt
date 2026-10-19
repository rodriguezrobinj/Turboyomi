package mihon.desktop.core

import eu.kanade.tachiyomi.extension.model.Extension
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopExtensionManagerTest {
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        val root = createTempDirectory("José Pérez manager")
        graph = Platform.start(AppDirs(root.resolve("data"), root.resolve("local")))
    }

    private fun available(apkUrl: String) = Extension.Available(
        name = "Example", pkgName = "pkg.example", versionName = "1.4.1", versionCode = 1, libVersion = 1.4,
        lang = "en", contentWarning = ContentWarning.SAFE, sources = emptyList(), apkUrl = apkUrl, iconUrl = "",
        store = ExtensionStore(
            "https://repo/index.min.json",
            "Repo",
            "R",
            "key",
            ExtensionStore.Contact("https://repo", null),
            false,
            null,
        ),
    )

    @Test
    fun `a corrupt download fails cleanly and leaves nothing behind`(): Unit = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("PK\u0003 truncated"))
        server.start()

        shouldThrowAny { graph.extensionManager.install(available(server.url("/ext.apk").toString())) }

        graph.appDirs.extensions.listDirectoryEntries() shouldBe emptyList()
        graph.appDirs.cache.listDirectoryEntries("*.apk") shouldBe emptyList()
        graph.extensionManager.installed.value shouldBe emptyList()
        server.shutdown()
    }

    @Test
    fun `source manager starts empty and stubs unknown ids`(): Unit = runBlocking {
        graph.sourceManager.getAll() shouldBe emptyList()
        graph.sourceManager.getOrStub(42L).id shouldBe 42L
    }
}
