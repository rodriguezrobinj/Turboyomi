// Null implementation until subproject 2a plan 2 ports the reader; same key and default as Mihon.
package eu.kanade.tachiyomi.ui.reader.setting

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class ReaderPreferences(preferenceStore: PreferenceStore) {
    val skipFiltered: Preference<Boolean> = preferenceStore.getBoolean("skip_filtered", true)
}
