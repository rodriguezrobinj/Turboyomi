package mihon.desktop.core

import io.kotest.matchers.shouldBe
import mihon.domain.extension.model.ContentWarning
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

class ContentWarningFilterTest {
    @Test
    fun `adult content is hidden by default on desktop`() {
        val store = InMemoryPreferenceStore()
        ContentWarningPrefs.isAllowed(store, ContentWarning.SAFE) shouldBe true
        ContentWarningPrefs.isAllowed(store, ContentWarning.MIXED) shouldBe true
        ContentWarningPrefs.isAllowed(store, ContentWarning.NSFW) shouldBe false
    }

    @Test
    fun `installed extensions load regardless when the filter doesn't apply to them`() {
        // InMemoryPreferenceStore doesn't keep set() values between lookups: seed them instead
        val store =
            InMemoryPreferenceStore(
                sequenceOf(InMemoryPreference(ContentWarningPrefs.APPLY_TO_INSTALLED_KEY, false, true)),
            )
        ContentWarningPrefs.isAllowed(store, ContentWarning.NSFW) shouldBe true
    }

    @Test
    fun `allowing NSFW in the settings lets it load`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(InMemoryPreference(ContentWarningPrefs.ENABLED_KEY, ContentWarning.entries.toSet(), emptySet())),
        )
        ContentWarningPrefs.isAllowed(store, ContentWarning.NSFW) shouldBe true
    }
}
