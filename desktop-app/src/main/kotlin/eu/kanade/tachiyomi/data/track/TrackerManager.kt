package eu.kanade.tachiyomi.data.track

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.comick.Comick
import eu.kanade.tachiyomi.data.track.hikka.Hikka
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdates
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import mihon.desktop.app.track.TrackerKeys

@Inject
@SingleIn(AppScope::class)
class TrackerManager(extra: Set<Tracker>) {
    // desktop: [extra] is what tests add (plan 5b-1, dev. 4)

    // desktop: Mihon's OAuth trackers, each only with its key (spec 5b-2 §1)
    val myAnimeList = MyAnimeList(1L).takeIf { TrackerKeys.myAnimeList != null }
    val aniList = Anilist(2L).takeIf { TrackerKeys.anilist != null }
    val shikimori = Shikimori(4L).takeIf { TrackerKeys.shikimori != null }
    val bangumi = Bangumi(5L).takeIf { TrackerKeys.bangumi != null }
    val hikka = Hikka(10L).takeIf { TrackerKeys.hikka != null }
    val mangaBaka = MangaBaka(11L).takeIf { TrackerKeys.mangaBaka != null }
    val comick = Comick(12L).takeIf { TrackerKeys.comick != null }

    val kitsu = Kitsu(3L)
    val komga = Komga(6L)
    val mangaUpdates = MangaUpdates(7L)
    val kavita = Kavita(8L)
    val suwayomi = Suwayomi(9L)

    val trackers: List<Tracker> = listOfNotNull<Tracker>(
        mangaBaka,
        myAnimeList,
        aniList,
        kitsu,
        shikimori,
        bangumi,
        komga,
        mangaUpdates,
        kavita,
        suwayomi,
        hikka,
        comick,
    ) + extra

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
