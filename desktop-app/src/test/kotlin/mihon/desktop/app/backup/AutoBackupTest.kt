package mihon.desktop.app.backup

import eu.kanade.tachiyomi.data.backup.BackupJob
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestBackups
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory

class AutoBackupTest {
    private val graph: TestAppGraph = startTestApp()

    private fun autobackupDir() = File(graph.storageManager.getAutomaticBackupsDirectory()!!.filePath!!)

    private fun backups() = autobackupDir().listFiles().orEmpty().map { it.name }.filter { it.endsWith(".tachibk") }

    @Test
    fun `an automatic backup goes to the autobackup folder and only the last 4 stay`(): Unit = runBlocking {
        TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        val old = (1..5).map { "app.mangareader_2020-01-0${it}_00-00.tachibk" }
        old.forEach { File(autobackupDir(), it).writeText("viejo") }

        graph.backupJob.createAutomatic()

        val kept = backups()
        kept.size shouldBe 4
        kept shouldContainAll old.takeLast(3)
        kept shouldNotContain old[0]
        graph.backupPreferences.lastAutoBackupTimestamp.get() shouldNotBe 0L
    }

    @Test
    fun `a failed automatic backup says so and keeps the last backup time`(): Unit = runBlocking {
        val notices = CopyOnWriteArrayList<Notice>()
        TrayNotifier.sink = { notices += it }
        val storage = graph.storagePreferences.baseStorageDirectory.get()
        // A storage folder whose autobackup is a file: the folder can't be created
        val broken = createTempDirectory("Almacén José").toFile().apply { resolve("autobackup").writeText("x") }
        graph.storagePreferences.baseStorageDirectory.set(broken.toURI().toString())
        val before = graph.backupPreferences.lastAutoBackupTimestamp.get()
        try {
            graph.backupJob.createAutomatic()
        } finally {
            graph.storagePreferences.baseStorageDirectory.set(storage)
            TrayNotifier.sink = null
        }

        graph.backupPreferences.lastAutoBackupTimestamp.get() shouldBe before
        notices.single().isError shouldBe true
    }

    @Test
    fun `the automatic jobs wait for a restore to end, and the backup then holds what it restored`(): Unit =
        runBlocking {
            val file = createTempDirectory("Grande José").toFile().resolve("grande.tachibk")
            val mangas = (1..400).map { n ->
                BackupManga(
                    source = TestSources.updatable.id,
                    url = "/grande/$n",
                    title = "Grande $n",
                    chapters = (1..20).map { BackupChapter(url = "/grande/$n/$it", name = "Capítulo $it") },
                )
            }
            TestBackups.write(file, Backup(backupManga = mangas))
            val lastUpdate = graph.libraryPreferences.lastUpdatedTimestamp.get()

            graph.backupJob.restore(file, RestoreOptions()) shouldBe true
            graph.backupJob.running.value shouldBe BackupJob.Operation.Restore
            val backup = async { graph.backupJob.periodicJob().run() }
            val update = async { graph.libraryUpdateJob.periodicJob().run() }
            delay(300)
            assumeTrue(graph.backupJob.running.value == BackupJob.Operation.Restore) { "the restore ended too soon" }
            graph.libraryPreferences.lastUpdatedTimestamp.get() shouldBe lastUpdate
            backup.await()
            update.await()

            val newest = autobackupDir().listFiles().orEmpty().filter { it.name.endsWith(".tachibk") }.maxBy { it.name }
            val urls = graph.backupDecoder.decodeEntries(newest).toList().map { it.url }
            urls shouldContainAll mangas.map { it.url }
        }
}
