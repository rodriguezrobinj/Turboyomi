package eu.kanade.domain.track.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import mihon.desktop.app.track.SealedPreference
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

// desktop: the credentials go through SealedPreference, sealed with DPAPI (spec 5b-1 §3 E)
@Inject
@SingleIn(AppScope::class)
class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun trackUsername(tracker: Tracker) = SealedPreference(
        preferenceStore.getString(
            Preference.privateKey("pref_mangasync_username_${tracker.id}"),
            "",
        ),
    )

    fun trackDisplayUsername(tracker: Tracker) = preferenceStore.getString(
        Preference.privateKey("pref_mangasync_displayname_${tracker.id}"),
        "",
    )

    fun trackPassword(tracker: Tracker) = SealedPreference(
        preferenceStore.getString(
            Preference.privateKey("pref_mangasync_password_${tracker.id}"),
            "",
        ),
    )

    fun trackAuthExpired(tracker: Tracker) = preferenceStore.getBoolean(
        Preference.privateKey("pref_tracker_auth_expired_${tracker.id}"),
        false,
    )

    fun setCredentials(tracker: Tracker, username: String, password: String) {
        trackUsername(tracker).set(username)
        trackPassword(tracker).set(password)
        trackAuthExpired(tracker).set(false)
    }

    fun trackToken(tracker: Tracker) =
        SealedPreference(preferenceStore.getString(Preference.privateKey("track_token_${tracker.id}"), ""))

    val kitsuScoreType: Preference<String> = preferenceStore.getString("kitsu_score_type", Kitsu.RATING_ADVANCED)

    // desktop: the AniList and MangaBaka score types come with their trackers in 5b-2

    val autoUpdateTrack: Preference<Boolean> = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    val autoUpdateTrackOnMarkRead: Preference<AutoTrackState> = preferenceStore.getEnum(
        "pref_auto_update_manga_on_mark_read",
        AutoTrackState.ALWAYS,
    )
}
