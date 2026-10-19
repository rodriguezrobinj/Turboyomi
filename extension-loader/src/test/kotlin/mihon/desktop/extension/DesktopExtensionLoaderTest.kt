package mihon.desktop.extension

import eu.kanade.tachiyomi.extension.model.Extension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import kotlin.io.path.Path

class DesktopExtensionLoaderTest {
    private val store = ExtensionStore("https://repo/index.min.json", "Repo", "R", "storekey", ExtensionStore.Contact("https://repo", null), false, null)
    private val loader = DesktopExtensionLoader(ExtensionTrust(InMemoryPreferenceStore()))

    private fun installed(meta: Map<String, String>, signatures: List<String> = listOf("storekey"), versionName: String = "1.4.1") =
        InstalledExtension(
            apk = Path("x.apk"),
            jar = Path("x.jar"),
            info = ApkInfo("pkg.example", "Tachiyomi: Example", versionName, 1, meta, signatures),
        )

    @Test
    fun `loads trusted extensions with supported libs`() {
        val ext = loader.load(installed(mapOf("tachiyomi.extension.class" to FakeSource::class.java.name)), listOf(store))
        ext.shouldBeInstanceOf<Extension.Loaded>().sources.map { it.name } shouldBe listOf("Fake")
        ext.lang shouldBe "en"
        ext.store shouldBe store
    }

    @Test
    fun `unsupported lib versions are not loaded`() {
        val ext = loader.load(installed(mapOf("tachiyomix.extensionLib" to "1.2", "tachiyomi.extension.class" to "x")), listOf(store))
        ext.shouldBeInstanceOf<Extension.NotLoaded>().reason shouldBe Extension.NotLoaded.Reason.UnsupportedLibVersion
    }

    @Test
    fun `untrusted signatures are reported with the last signature`() {
        val ext = loader.load(installed(mapOf("tachiyomi.extension.class" to "x"), signatures = listOf("a", "b")), listOf(store))
        ext.shouldBeInstanceOf<Extension.NotLoaded>().reason shouldBe Extension.NotLoaded.Reason.Untrusted("b")
    }

    @Test
    fun `missing source class is malformed`() {
        val ext = loader.load(installed(emptyMap()), listOf(store))
        ext.shouldBeInstanceOf<Extension.NotLoaded>().reason shouldBe Extension.NotLoaded.Reason.Malformed
    }

    @Test
    fun `missing classes become a failed reason instead of crashing`() {
        val ext = loader.load(installed(mapOf("tachiyomi.extension.class" to "does.not.Exist")), listOf(store))
        ext.shouldBeInstanceOf<Extension.NotLoaded>().reason.shouldBeInstanceOf<Extension.NotLoaded.Reason.Failed>()
    }
}
