package mihon.desktop.app.backup

import eu.kanade.tachiyomi.data.backup.BackupJob
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestBackups
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory

class BackupRestoreTest {
    private val graph: TestAppGraph = startTestApp()
    private val dir = createTempDirectory("Restaurar José").toFile()
    private val notices = CopyOnWriteArrayList<Notice>()

    @BeforeEach
    fun captureNotices() {
        TrayNotifier.sink = { notices += it }
    }

    @AfterEach
    fun releaseNotices() {
        TrayNotifier.sink = null
    }

    private fun manga(url: String, title: String, memo: ByteArray? = null) = BackupManga(
        source = TestSources.updatable.id,
        url = url,
        title = title,
        chapters = listOf(
            BackupChapter(url = "$url/c1", name = "Capítulo 1", read = true, chapterNumber = 1f),
            BackupChapter(url = "$url/c2", name = "Capítulo 2", chapterNumber = 2f),
        ),
        categories = listOf(50),
        history = listOf(BackupHistory(url = "$url/c1", lastRead = 1_700_000_000_000, readDuration = 30_000)),
        // Kept in the database before any tracker exists (spec 5a §3 F)
        tracking = listOf(
            BackupTracking(syncId = 2, libraryId = 0, trackingUrl = "https://example.org$url", title = title),
        ),
    ).apply { if (memo != null) this.memo = memo }

    private suspend fun restore(backup: Backup, options: RestoreOptions = RestoreOptions()) {
        val file = File(dir, "copia-${System.nanoTime()}.tachibk").also { TestBackups.write(it, backup) }
        graph.backupJob.restore(file, options) shouldBe true
        graph.backupJob.awaitIdle()
    }

    @Test
    fun `a backup restores its manga with chapters, categories and history, and twice adds nothing`(): Unit =
        runBlocking {
            val backup = Backup(
                backupManga = listOf(manga("/restaurar/1", "Restaurado José")),
                backupCategories = listOf(BackupCategory(name = "Restaurada José", order = 50)),
            )

            restore(backup)
            restore(backup)

            val restored = graph.getMangaByUrlAndSourceId.await("/restaurar/1", TestSources.updatable.id)!!
            restored.favorite shouldBe true
            val chapters = graph.getChaptersByMangaId.await(restored.id)
            chapters.size shouldBe 2
            chapters.single { it.url == "/restaurar/1/c1" }.read shouldBe true
            graph.getHistory.await(restored.id).size shouldBe 1
            graph.trackRepository.getTracksByMangaId(restored.id).single().remoteUrl shouldBe
                "https://example.org/restaurar/1"
            val category = graph.getCategories.await().single { it.name == "Restaurada José" }
            graph.getCategories.await(restored.id).map { it.id } shouldBe listOf(category.id)
            graph.backupNotifier.restoreProgress.value shouldBe null
        }

    @Test
    fun `a corrupt file fails with an error and the library stays as it was`(): Unit = runBlocking {
        val before = graph.getFavorites.await().size
        val file = File(dir, "roto.tachibk").apply { writeText("esto no es un backup") }

        graph.backupJob.restore(file, RestoreOptions()) shouldBe true
        graph.backupJob.awaitIdle()

        graph.getFavorites.await().size shouldBe before
        notices.single().title shouldBe graph.context.stringResource(MR.strings.restoring_backup_error)
    }

    @Test
    fun `a manga with bad data goes to the error log and the others are restored`(): Unit = runBlocking {
        restore(
            Backup(
                backupManga = listOf(
                    manga("/restaurar/bien", "Bueno José"),
                    manga("/restaurar/mal", "Malo José", memo = "no es json".encodeToByteArray()),
                ),
            ),
        )

        graph.getMangaByUrlAndSourceId.await("/restaurar/bien", TestSources.updatable.id) shouldNotBe null
        graph.getMangaByUrlAndSourceId.await("/restaurar/mal", TestSources.updatable.id) shouldBe null
        graph.appDirs.logs.resolve("restore_errors.txt").toFile().readText() shouldContain "Malo José"
        notices.single().isError shouldBe true
    }

    @Test
    fun `restored settings keep their types, and app state and mistyped keys are left alone`(): Unit = runBlocking {
        val storageDir = graph.storagePreferences.baseStorageDirectory.get()
        graph.preferenceStore.getString("desktop_test_text").set("texto")

        restore(
            Backup(
                backupManga = emptyList(),
                backupPreferences = listOf(
                    BackupPreference("backup_interval", IntPreferenceValue(48)),
                    BackupPreference("desktop_test_flag", BooleanPreferenceValue(true)),
                    BackupPreference(
                        Preference.appStateKey("storage_dir"),
                        StringPreferenceValue("content://com.android.externalstorage.documents/tree/x"),
                    ),
                    // On this side the key holds text: an Int of the same name mustn't replace it
                    BackupPreference("desktop_test_text", IntPreferenceValue(3)),
                ),
            ),
        )

        graph.backupPreferences.backupInterval.get() shouldBe 48
        graph.preferenceStore.getBoolean("desktop_test_flag").get() shouldBe true
        graph.storagePreferences.baseStorageDirectory.get() shouldBe storageDir
        graph.preferenceStore.getString("desktop_test_text").get() shouldBe "texto"
        graph.backupPreferences.backupInterval.set(12)
    }

    @Test
    fun `one backup or restore at a time`(): Unit = runBlocking {
        val file = File(dir, "uno.tachibk")
        TestBackups.write(file, Backup(backupManga = (1..50).map { manga("/uno/$it", "Uno $it") }))

        graph.backupJob.restore(file, RestoreOptions()) shouldBe true
        graph.backupJob.running.value shouldBe BackupJob.Operation.Restore
        graph.backupJob.restore(file, RestoreOptions()) shouldBe false
        graph.backupJob.create(File(dir, "otro.tachibk"), BackupOptions()) shouldBe false
        graph.backupJob.awaitIdle()

        graph.backupJob.running.value shouldBe null
    }
}
