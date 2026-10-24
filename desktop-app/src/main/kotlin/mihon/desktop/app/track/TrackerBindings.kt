package mihon.desktop.app.track

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import eu.kanade.tachiyomi.data.track.Tracker

/** The trackers outside TrackerManager's own list: none in the app, the fake ones in tests (plan 5b-1, dev. 4). */
@ContributesTo(AppScope::class)
interface TrackerBindings {
    @Multibinds(allowEmpty = true)
    fun extraTrackers(): Set<Tracker>
}
