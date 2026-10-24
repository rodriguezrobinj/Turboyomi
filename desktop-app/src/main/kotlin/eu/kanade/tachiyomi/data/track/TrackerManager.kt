package eu.kanade.tachiyomi.data.track

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@Inject
@SingleIn(AppScope::class)
class TrackerManager(extra: Set<Tracker>) {
    // desktop: the trackers without OAuth (spec 5b-1); 5b-2 adds the rest with Mihon's ids. [extra] is what tests add

    val trackers: List<Tracker> = listOf<Tracker>() + extra

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow(): Flow<List<Tracker>> = if (trackers.isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(trackers.map { it.isLoggedInFlow }) {
            it.mapIndexedNotNull { index, isLoggedIn ->
                if (isLoggedIn) trackers[index] else null
            }
        }
    }

    fun get(id: Long) = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>) = trackers.filter { it.id in ids }
}
