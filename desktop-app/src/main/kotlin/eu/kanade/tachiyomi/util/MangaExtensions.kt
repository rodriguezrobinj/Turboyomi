package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.isLocal
import kotlin.time.Clock

fun Manga.removeCovers(coverCache: CoverCache): Manga {
    if (isLocal()) return this
    return if (coverCache.deleteFromCache(this, true) > 0) {
        copy(coverLastModified = Clock.System.now())
    } else {
        this
    }
}

// desktop: editCover needs LocalCoverManager (source-local, subproject 4); MangaCoverViewModel uses CoverCache
