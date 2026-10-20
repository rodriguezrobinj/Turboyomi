// Null implementation until subproject 5; replace with the Mihon port.
package eu.kanade.domain.track.interactor

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.model.Manga

@Suppress("UNUSED_PARAMETER")
@Inject
class TrackChapter {
    suspend fun await(context: Context, mangaId: Long, chapterNumber: Double, setupJobOnFailure: Boolean = true) {}
}

@Suppress("UNUSED_PARAMETER")
@Inject
class RefreshTracks {
    suspend fun await(mangaId: Long): List<Pair<Tracker?, Throwable>> = emptyList()
}

@Suppress("UNUSED_PARAMETER")
@Inject
class AddTracks {
    suspend fun bindEnhancedTrackers(manga: Manga, source: Source) {}
}
