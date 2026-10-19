package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class ExtensionTrustTest {
    private val store =
        ExtensionStore(
            "https://repo/index.min.json",
            "Repo",
            "R",
            "storekey",
            ExtensionStore.Contact("https://repo", null),
            false,
            null,
        )

    @Test
    fun `signed by a store key is trusted`() {
        ExtensionTrust(InMemoryPreferenceStore()).isTrusted("pkg", 1, listOf("storekey"), listOf(store)) shouldBe true
    }

    @Test
    fun `unknown key needs explicit trust for that exact version`() {
        val trust = ExtensionTrust(InMemoryPreferenceStore())
        trust.isTrusted("pkg", 1, listOf("other"), listOf(store)) shouldBe false
        trust.trust("pkg", 1, "other")
        trust.isTrusted("pkg", 1, listOf("other"), listOf(store)) shouldBe true
        trust.isTrusted("pkg", 2, listOf("other"), listOf(store)) shouldBe false
    }

    @Test
    fun `no signatures is never trusted`() {
        ExtensionTrust(InMemoryPreferenceStore()).isTrusted("pkg", 1, emptyList(), listOf(store)) shouldBe false
    }
}
