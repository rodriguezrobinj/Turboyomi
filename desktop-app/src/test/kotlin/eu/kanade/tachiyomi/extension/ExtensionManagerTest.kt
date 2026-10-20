package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtensionManagerTest {
    private lateinit var graph: TestAppGraph

    @BeforeAll
    fun start() {
        graph = startTestApp()
    }

    private fun available(apkUrl: String) = Extension.Available(
        name = "Example", pkgName = "pkg.example", versionName = "1.4.1", versionCode = 1, libVersion = 1.4,
        lang = "en", contentWarning = ContentWarning.SAFE, sources = emptyList(), apkUrl = apkUrl, iconUrl = "",
        store = ExtensionStore("u", "Repo", "R", "key", ExtensionStore.Contact("w", null), false, null),
    )

    @Test
    fun `a failed install reports its steps and ends in error instead of throwing`(): Unit = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("PK\u0003 truncated"))
        server.start()

        val steps = graph.mihonExtensionManager.installExtension(available(server.url("/ext.apk").toString())).toList()

        steps shouldBe listOf(InstallStep.Pending, InstallStep.Downloading, InstallStep.Error)
        server.shutdown()
    }

    @Test
    fun `an extension with an update is flagged against the store listing`() {
        val installed = Extension.Loaded(
            name = "Example", pkgName = "pkg.example", versionName = "1.4.1", versionCode = 1, libVersion = 1.4,
            lang = "en", contentWarning = ContentWarning.SAFE, isShared = false, signatures = listOf("key"),
            pkgFactory = null, sources = emptyList(),
        )
        val newer = available("").copy(versionCode = 2)

        ExtensionManager.withStatus(installed, listOf(newer)).hasUpdate shouldBe true
        ExtensionManager.withStatus(installed, listOf(newer)).let { (it as Extension.Loaded).isObsolete } shouldBe false
        ExtensionManager.withStatus(installed, emptyList()) shouldBe installed
    }

    @Test
    fun `a per-language row installs the store's listing, whose package name is the apk's`() {
        // GetExtensionsByType splits multi-language extensions into rows named "<pkg>-<sourceId>"
        val listing = available("https://repo/all.mangadex.apk")
        val row = listing.copy(pkgName = "${listing.pkgName}-2499283573021220255")

        ExtensionManager.listingFor(row, listOf(listing)) shouldBe listing
        ExtensionManager.listingFor(listing, emptyList()) shouldBe listing
    }
}
