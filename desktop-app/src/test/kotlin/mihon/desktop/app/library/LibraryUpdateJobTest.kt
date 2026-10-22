package mihon.desktop.app.library

import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import mihon.desktop.core.jobs.JobScheduler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

class LibraryUpdateJobTest {
    private lateinit var graph: TestAppGraph
    private lateinit var job: LibraryUpdateJob
    private val notices = CopyOnWriteArrayList<Notice>()

    @BeforeEach
    fun setUp() {
        graph = startTestApp()
        job = graph.libraryUpdateJob
        graph.libraryPreferences.autoUpdateMangaRestrictions.set(emptySet())
        TestSources.updatable.gate = null
        TestSources.updatable.failing.clear()
        TestSources.updatable.chapters.clear()
        TrayNotifier.sink = { notices += it }
    }

    @AfterEach
    fun tearDown() {
        TrayNotifier.sink = null
    }

    private suspend fun runUpdate(category: Long) {
        job.start(category) shouldBe true
        withTimeout(30.seconds) { job.awaitIdle() }
    }

    @Test
    fun `new chapters are saved, counted and notified`(): Unit = runBlocking {
        val category = TestLibrary.category(graph)
        val manga = TestLibrary.libraryManga(graph, category)
        val before = graph.libraryPreferences.newUpdatesCount.get()
        TestSources.updatable.chapters[manga.url] = 5

        runUpdate(category)

        graph.getChaptersByMangaId.await(manga.id).size shouldBe 5
        graph.libraryPreferences.newUpdatesCount.get() shouldBe before + 2
        notices.single().title shouldBe str(MR.strings.notification_new_chapters)
        notices.single().message shouldContain manga.title
        job.progress.value shouldBe null
    }

    @Test
    fun `only the requested category is updated`(): Unit = runBlocking {
        val mine = TestLibrary.category(graph)
        val other = TestLibrary.category(graph)
        val inMine = TestLibrary.libraryManga(graph, mine)
        val inOther = TestLibrary.libraryManga(graph, other)
        TestSources.updatable.chapters[inMine.url] = 4
        TestSources.updatable.chapters[inOther.url] = 4

        runUpdate(mine)

        graph.getChaptersByMangaId.await(inMine.id).size shouldBe 4
        graph.getChaptersByMangaId.await(inOther.id).size shouldBe 3
    }

    @Test
    fun `a manga with unread chapters is skipped when that restriction is on`(): Unit = runBlocking {
        graph.libraryPreferences.autoUpdateMangaRestrictions.set(setOf(LibraryPreferences.MANGA_HAS_UNREAD))
        val category = TestLibrary.category(graph)
        val manga = TestLibrary.libraryManga(graph, category)
        TestSources.updatable.chapters[manga.url] = 4

        runUpdate(category)

        graph.getChaptersByMangaId.await(manga.id).size shouldBe 3
    }

    @Test
    fun `failures, including an uninstalled source, are collected in the errors file and the rest still updates`():
        Unit = runBlocking {
        val category = TestLibrary.category(graph)
        val broken = TestLibrary.libraryManga(graph, category)
        val orphan = TestLibrary.libraryManga(graph, category, sourceId = 999L)
        val fine = TestLibrary.libraryManga(graph, category)
        TestSources.updatable.failing[broken.url] = IOException("Sin conexión con José")
        TestSources.updatable.chapters[fine.url] = 4

        runUpdate(category)

        graph.getChaptersByMangaId.await(fine.id).size shouldBe 4
        val errors = graph.appDirs.logs.resolve("update_errors.txt").readText()
        errors shouldContain broken.title
        errors shouldContain "Sin conexión con José"
        errors shouldContain orphan.title
        errors shouldContain str(MR.strings.loader_not_implemented_error)
        notices.filter { it.isError }.single().title shouldContain "2"
    }

    @Test
    fun `starting while one is running is refused`(): Unit = runBlocking {
        val category = TestLibrary.category(graph)
        TestLibrary.libraryManga(graph, category)
        val gate = CompletableDeferred<Unit>().also { TestSources.updatable.gate = it }

        job.start(category) shouldBe true
        job.start(category) shouldBe false
        job.startMetadata() shouldBe false

        gate.complete(Unit)
        withTimeout(30.seconds) { job.awaitIdle() }
        job.start(category) shouldBe true
        withTimeout(30.seconds) { job.awaitIdle() }
    }

    @Test
    fun `cancelling keeps what was saved, notifies nothing and clears the progress`(): Unit = runBlocking {
        val category = TestLibrary.category(graph)
        val manga = TestLibrary.libraryManga(graph, category)
        TestSources.updatable.chapters[manga.url] = 6
        TestSources.updatable.gate = CompletableDeferred()

        job.start(category) shouldBe true
        withTimeout(10.seconds) { job.progress.first { it?.current?.isNotEmpty() == true } }
        job.cancel()
        withTimeout(10.seconds) { job.awaitIdle() }

        job.progress.value shouldBe null
        notices.shouldBeEmpty()
        graph.getChaptersByMangaId.await(manga.id).size shouldBe 3
    }

    @Test
    fun `the scheduler runs the library update when its interval has passed`(): Unit = runBlocking {
        val category = TestLibrary.category(graph)
        val manga = TestLibrary.libraryManga(graph, category)
        TestSources.updatable.chapters[manga.url] = 4
        graph.libraryPreferences.autoUpdateInterval.set(12)
        graph.libraryPreferences.lastUpdatedTimestamp.set(0L)
        val scheduler = JobScheduler(listOf(job.periodicJob()))

        coroutineScope {
            scheduler.check(this) shouldContainExactly listOf("LibraryUpdate")
        }

        graph.getChaptersByMangaId.await(manga.id).size shouldBe 4
        graph.libraryPreferences.autoUpdateInterval.set(0)
    }
}
