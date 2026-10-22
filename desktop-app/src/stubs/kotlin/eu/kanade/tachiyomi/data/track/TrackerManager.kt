// Null implementation until subproject 5; replace with the Mihon port.
package eu.kanade.tachiyomi.data.track

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Inject
@SingleIn(AppScope::class)
class TrackerManager {
    fun loggedInTrackers(): List<Tracker> = emptyList()

    fun loggedInTrackersFlow(): Flow<List<Tracker>> = flowOf(emptyList())

    @Suppress("UNUSED_PARAMETER")
    fun getAll(ids: Set<Long>): List<Tracker> = emptyList()
}
