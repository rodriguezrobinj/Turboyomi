package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import mihon.domain.extension.model.ContentWarning
import org.junit.jupiter.api.Test

class ExtensionManifestParserTest {
    private fun info(meta: Map<String, String>, versionName: String = "1.4.12", label: String = "Tachiyomi: Example") =
        ApkInfo("eu.kanade.tachiyomi.extension.en.example", label, versionName, 12, meta, listOf("abc"))

    @Test
    fun `relative source classes get the package prefix`() {
        ExtensionManifestParser.sourceClasses(info(mapOf("tachiyomi.extension.class" to ".A; com.other.B"))) shouldBe
            listOf("eu.kanade.tachiyomi.extension.en.example.A", "com.other.B")
    }

    @Test
    fun `lib version comes from metadata or falls back to the version name`() {
        ExtensionManifestParser.libVersion(info(mapOf("tachiyomix.extensionLib" to "1.6"))) shouldBe 1.6
        ExtensionManifestParser.libVersion(info(emptyMap(), versionName = "1.4.3")) shouldBe 1.4
        ExtensionManifestParser.libVersion(info(emptyMap(), versionName = "garbage")) shouldBe null
    }

    @Test
    fun `content warning follows Mihon's precedence`() {
        ExtensionManifestParser.contentWarning(mapOf("tachiyomix.contentWarning" to "1")) shouldBe ContentWarning.MIXED
        ExtensionManifestParser.contentWarning(mapOf("tachiyomix.contentWarning" to "2")) shouldBe ContentWarning.NSFW
        ExtensionManifestParser.contentWarning(mapOf("tachiyomi.extension.nsfw" to "1")) shouldBe ContentWarning.NSFW
        ExtensionManifestParser.contentWarning(emptyMap()) shouldBe ContentWarning.SAFE
    }

    @Test
    fun `name prefers metadata then label without the Tachiyomi prefix`() {
        ExtensionManifestParser.name(info(mapOf("tachiyomix.name" to "Nice"))) shouldBe "Nice"
        ExtensionManifestParser.name(info(emptyMap())) shouldBe "Example"
        ExtensionManifestParser.name(info(emptyMap(), label = "")) shouldBe "eu.kanade.tachiyomi.extension.en.example"
    }
}
