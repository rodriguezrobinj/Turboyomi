package mihon.desktop.app.backup

import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The real thing: a backup another app of the ecosystem wrote, Suwayomi, in Mihon's format (spec 5a §5). It's the
 * developer's own library, so it stays out of git (.gitignore) and the test is skipped where the file isn't there.
 */
class MihonCompatibilityTest {
    private val graph: TestAppGraph = startTestApp()
    private val fixture = File("src/test/resources/backup/suwayomi.tachibk")

    @Test
    fun `a real backup from Suwayomi restores its library, categories, read chapters and history`(): Unit =
        runBlocking {
            assumeTrue(fixture.isFile, "The private fixture $fixture isn't on this machine")
            graph.backupFileValidator.validate(fixture)
            val entries = graph.backupDecoder.decodeEntries(fixture).toList()
            val metadata = graph.backupDecoder.decodeMetadata(fixture).backup
            entries.isNotEmpty() shouldBe true

            graph.backupJob.restore(fixture, RestoreOptions()) shouldBe true
            graph.backupJob.awaitIdle()

            graph.getCategories.await().map { it.name } shouldContainAll metadata.backupCategories.map { it.name }
            entries.forEach { entry ->
                val manga = graph.getMangaByUrlAndSourceId.await(entry.url, entry.source)
                manga shouldNotBe null
                manga!!.favorite shouldBe entry.favorite
                val readUrls = graph.getChaptersByMangaId.await(manga.id).filter { it.read }.map { it.url }
                readUrls shouldContainAll entry.chapters.filter { it.read }.map { it.url }
                graph.getHistory.await(manga.id).size shouldBe entry.history.distinctBy { it.url }.size
            }
        }
}
