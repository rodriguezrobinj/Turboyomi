package eu.kanade.domain.manga.model

import android.content.Context
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.model.SManga
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// desktop: readingMode and readerOrientation come back with the reader (plan 2 of subproject 2a)

// TODO: move these into the domain model
val Manga.downloadedFilter: TriState
    get() {
        // desktop: no "downloaded only" mode until downloads (subproject 4)
        return when (downloadedFilterRaw) {
            Manga.CHAPTER_SHOW_DOWNLOADED -> TriState.ENABLED_IS
            Manga.CHAPTER_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }
fun Manga.chaptersFiltered(): Boolean {
    return unreadFilter != TriState.DISABLED ||
        downloadedFilter != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED
}

fun Manga.toSManga(): SManga = SManga.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
    it.memo = memo
}

fun Manga.copyFrom(other: SManga): Manga {
    val author = other.author ?: author
    val artist = other.artist ?: artist
    val description = other.description ?: description
    val genres = if (other.genre != null) {
        other.getGenres()
    } else {
        genre
    }
    val thumbnailUrl = other.thumbnail_url ?: thumbnailUrl
    return this.copy(
        author = author,
        artist = artist,
        description = description,
        genre = genres,
        thumbnailUrl = thumbnailUrl,
        status = other.status.toLong(),
        updateStrategy = other.update_strategy,
        initialized = other.initialized && initialized,
        memo = other.memo,
    )
}

fun Manga.hasCustomCover(coverCache: CoverCache = Injekt.get<Context>().appGraph.coverCache): Boolean {
    return coverCache.hasCustomCover(id)
}

// desktop: getComicInfo comes back with downloads (subproject 4)
