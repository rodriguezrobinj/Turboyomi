package mihon.desktop.core

import mihon.domain.extension.model.ContentWarning
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnumSet

/** Mihon's content warning preferences (same keys as its SourcePreferences), with the desktop default. */
object ContentWarningPrefs {
    const val ENABLED_KEY = "enabled_content_warnings"
    const val APPLY_TO_INSTALLED_KEY = "apply_content_warnings_to_installed"

    /** Spec 2a: adult content stays hidden until the user allows it in the settings. */
    val DEFAULT_ENABLED: Set<ContentWarning> = setOf(ContentWarning.SAFE, ContentWarning.MIXED)

    fun enabled(store: PreferenceStore): Preference<Set<ContentWarning>> = store.getEnumSet(
        ENABLED_KEY,
        DEFAULT_ENABLED,
    )

    fun applyToInstalled(store: PreferenceStore): Preference<Boolean> = store.getBoolean(APPLY_TO_INSTALLED_KEY, true)

    fun isAllowed(store: PreferenceStore, warning: ContentWarning): Boolean =
        !applyToInstalled(store).get() || warning in enabled(store).get()
}
