// Null implementation until subproject 5; replace with the Mihon port (same keys).
package eu.kanade.domain.track.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.track.model.AutoTrackState
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

@Inject
@SingleIn(AppScope::class)
class TrackPreferences(preferenceStore: PreferenceStore) {
    val autoUpdateTrackOnMarkRead: Preference<AutoTrackState> = preferenceStore.getEnum(
        "pref_auto_update_manga_on_mark_read",
        AutoTrackState.ALWAYS,
    )
}
