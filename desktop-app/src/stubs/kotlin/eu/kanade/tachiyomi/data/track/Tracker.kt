// Null implementation until subproject 5; replace with the Mihon port.
package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track

interface Tracker {
    val id: Long
    val name: String

    fun get10PointScore(track: Track): Double = 0.0
}

interface EnhancedTracker {
    fun accept(source: Source): Boolean

    fun isTrackFrom(track: Track, manga: Manga, source: Source?): Boolean = false

    fun migrateTrack(track: Track, manga: Manga, newSource: Source): Track? = null
}
