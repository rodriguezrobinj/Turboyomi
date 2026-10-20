// Null implementation until subproject 5; replace with the Mihon port.
package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.source.Source

interface Tracker {
    val id: Long
}

interface EnhancedTracker {
    fun accept(source: Source): Boolean
}
