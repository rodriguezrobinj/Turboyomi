package mihon.desktop.app.testing

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import tachiyomi.domain.track.model.Track as DomainTrack

/** A tracker kept in memory: [remote] is what the "service" holds per remote id; [failing] plays a network outage. */
class FakeTracker(id: Long, name: String) : BaseTracker(id, name) {
    val remote = ConcurrentHashMap<Long, Double>()

    @Volatile
    var failing = false

    override fun getLogo() = "drawable/brand_kitsu.xml"

    override fun getStatusList(): List<Long> = listOf(READING, COMPLETED)

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        else -> null
    }

    override fun getReadingStatus() = READING

    override fun getRereadingStatus() = -1L

    override fun getCompletionStatus() = COMPLETED

    override fun getScoreList(): List<String> = listOf("0", "5", "10")

    override fun displayScore(track: DomainTrack) = track.score.toString()

    private fun network() {
        if (failing) throw IOException("sin red")
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        network()
        remote[track.remote_id] = track.last_chapter_read
        return track
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track = update(track)

    override suspend fun search(query: String): List<TrackSearch> {
        network()
        return listOf(
            TrackSearch.create(id).apply {
                remote_id = 42
                title = "Resultado $query"
            },
        )
    }

    override suspend fun refresh(track: Track): Track {
        network()
        track.last_chapter_read = remote[track.remote_id] ?: track.last_chapter_read
        return track
    }

    override suspend fun login(username: String, password: String) = saveCredentials(username, password)

    override suspend fun updateUserConfig() {}

    private companion object {
        const val READING = 1L
        const val COMPLETED = 2L
    }
}

object TestTrackers {
    val fake = FakeTracker(999L, "Falso José")
    val other = FakeTracker(998L, "Otro José")
}
