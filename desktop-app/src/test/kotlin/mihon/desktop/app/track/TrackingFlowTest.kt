package mihon.desktop.app.track

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestTrackers
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track
import kotlin.time.Duration.Companion.seconds

class TrackingFlowTest {
    private val graph: TestAppGraph = startTestApp()
    private val fake = TestTrackers.fake
    private val other = TestTrackers.other

    @BeforeEach
    fun signIn(): Unit = runBlocking {
        fake.failing = false
        other.failing = false
        fake.login("josé", "clave")
        other.login("ana", "clave")
        graph.delayedTrackingStore.getItems().forEach { graph.delayedTrackingStore.remove(it.trackId) }
    }

    private suspend fun linked(manga: Manga, tracker: Long, remoteId: Long) {
        graph.trackRepository.upsert(
            Track(
                id = 0, mangaId = manga.id, trackerId = tracker, remoteId = remoteId, libraryId = null,
                title = manga.title, lastChapterRead = 0.0, totalChapters = 0, status = 1, score = 0.0,
                remoteUrl = "", startDate = null, finishDate = null, private = false,
            ),
        )
    }

    @Test
    fun `a read chapter goes up to every linked tracker`(): Unit = runBlocking {
        val manga = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        linked(manga, fake.id, 101)
        linked(manga, other.id, 102)

        graph.trackChapter.await(graph.context, manga.id, 2.0)

        fake.remote[101] shouldBe 2.0
        other.remote[102] shouldBe 2.0
        graph.trackRepository.getTracksByMangaId(manga.id).map { it.lastChapterRead }.toSet() shouldBe setOf(2.0)
    }

    @Test
    fun `offline the change waits in the queue, and the job sends it once the tracker answers`(): Unit = runBlocking {
        val manga = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        linked(manga, fake.id, 201)
        linked(manga, other.id, 202)
        fake.failing = true

        graph.trackChapter.await(graph.context, manga.id, 3.0)

        other.remote[202] shouldBe 3.0
        fake.remote[201] shouldBe null
        graph.delayedTrackingStore.getItems().size shouldBe 1

        fake.failing = false
        graph.delayedTrackingJob.flush()

        fake.remote[201] shouldBe 3.0
        graph.delayedTrackingStore.getItems().size shouldBe 0
    }

    @Test
    fun `a tracker without a session gets nothing`(): Unit = runBlocking {
        val manga = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        linked(manga, fake.id, 301)
        fake.logout()

        graph.trackChapter.await(graph.context, manga.id, 4.0)

        fake.remote[301] shouldBe null
    }

    @Test
    fun `a signed-in tracker's password isn't in the settings files`(): Unit = runBlocking {
        fake.login("josé", "clave secreta José")
        // A settings save writes a temp file and renames it: one listed a moment ago may be gone when read
        val files = {
            graph.appDirs.data.toFile().walk().filter {
                it.isFile
            }.mapNotNull { runCatching { it.readText() }.getOrNull() }
                .toList()
        }
        val marker = "flush-${System.nanoTime()}"
        graph.preferenceStore.getString("desktop_test_flush", "").set(marker)
        withTimeout(10.seconds) { while (files().none { marker in it }) delay(100) }

        files().forEach { it shouldNotContain "clave secreta José" }
    }
}
