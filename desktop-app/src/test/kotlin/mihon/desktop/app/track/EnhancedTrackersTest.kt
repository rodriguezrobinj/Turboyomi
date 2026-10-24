package mihon.desktop.app.track

import eu.kanade.tachiyomi.data.track.EnhancedTracker
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class EnhancedTrackersTest {
    private val graph: TestAppGraph = startTestApp()

    @Test
    fun `the three enhanced trackers are there with Mihon's ids`() {
        graph.trackerManager.get(6)!!.name shouldBe "Komga"
        graph.trackerManager.get(8)!!.name shouldBe "Kavita"
        graph.trackerManager.get(9)!!.name shouldBe "Suwayomi"
    }

    @Test
    fun `a Komga manga added to the library binds itself to the enabled Komga tracker`(): Unit = runBlocking {
        val komga = TestSources.komga
        (graph.trackerManager.get(6) as EnhancedTracker).loginNoop()
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "${komga.baseUrl}/api/v1/series/S1", title = "Serie José", source = komga.id),
        )

        graph.addTracks.bindEnhancedTrackers(manga, komga)

        val track = graph.trackRepository.getTracksByMangaId(manga.id).single()
        track.trackerId shouldBe 6L
        track.lastChapterRead shouldBe 3.0
    }
}
