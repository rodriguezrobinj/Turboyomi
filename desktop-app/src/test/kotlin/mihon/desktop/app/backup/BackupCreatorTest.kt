package mihon.desktop.app.backup

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.track.model.Track
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.io.path.createTempDirectory
import kotlin.time.Instant

class BackupCreatorTest {
    private val graph: TestAppGraph = startTestApp()
    private val dir = createTempDirectory("Copias José").toFile()

    private suspend fun backup(name: String, options: BackupOptions = BackupOptions()): File {
        val file = File(dir, name)
        graph.backupCreatorFactory.create(isAutoBackup = false).backup(file, options) shouldBe file.path
        return file
    }

    @Test
    fun `a backup holds the library with its chapters, categories, history and tracks, and decodes back`(): Unit =
        runBlocking {
            val category = TestLibrary.category(graph)
            val categoryName = graph.getCategories.await().first { it.id == category }.name
            val manga = TestLibrary.libraryManga(graph, category)
            val chapter = graph.getChaptersByMangaId.await(manga.id).first()
            graph.updateChapter.await(ChapterUpdate(chapter.id) { read = true })
            graph.upsertHistory.await(
                HistoryUpdate(chapter.id, Instant.fromEpochMilliseconds(1_700_000_000_000), 60_000),
            )
            graph.trackRepository.upsert(
                Track(
                    id = 0, mangaId = manga.id, trackerId = 2, remoteId = 7, libraryId = null, title = manga.title,
                    lastChapterRead = 1.0, totalChapters = 10, status = 1, score = 8.0,
                    remoteUrl = "https://example.org/7", startDate = null, finishDate = null, private = false,
                ),
            )

            val file = backup("completo.tachibk")

            val entry = graph.backupDecoder.decodeEntries(file).toList().single { it.url == manga.url }
            entry.title shouldBe manga.title
            entry.chapters.single { it.url == chapter.url }.read shouldBe true
            entry.history.single().readDuration shouldBe 60_000
            entry.tracking.single().trackingUrl shouldBe "https://example.org/7"
            val metadata = graph.backupDecoder.decodeMetadata(file).backup
            metadata.backupCategories.map { it.name } shouldContain categoryName
            metadata.backupSources.map { it.sourceId } shouldContain manga.source
        }

    @Test
    fun `the file is gzip and Mihon's whole-backup decoding reads it`(): Unit = runBlocking {
        TestLibrary.libraryManga(graph, TestLibrary.category(graph))

        val file = backup("formato.tachibk")

        val bytes = file.readBytes()
        (bytes[0].toInt() and 0xff) shouldBe 0x1f
        (bytes[1].toInt() and 0xff) shouldBe 0x8b
        val proto = GZIPInputStream(file.inputStream()).use { it.readBytes() }
        // The entries are Backup's field 1, length-delimited: tag 0x0a
        proto[0].toInt() shouldBe 0x0a
        ProtoBuf.decodeFromByteArray(Backup.serializer(), proto).backupManga.isNotEmpty() shouldBe true
    }

    @Test
    fun `settings keep their types, app state never travels, private keys only with their option`(): Unit =
        runBlocking {
            TestLibrary.libraryManga(graph, TestLibrary.category(graph))
            graph.backupPreferences.backupInterval.set(24)
            val token = Preference.privateKey("desktop_test_token")
            graph.preferenceStore.getString(token).set("secreto")

            val plain = graph.backupDecoder.decodeMetadata(backup("ajustes.tachibk")).backup.backupPreferences
            val withPrivate = graph.backupDecoder.decodeMetadata(
                backup("privados.tachibk", BackupOptions(privateSettings = true)),
            ).backup.backupPreferences

            plain.single { it.key == "backup_interval" }.value shouldBe IntPreferenceValue(24)
            plain.map { it.key } shouldNotContain Preference.appStateKey("storage_dir")
            plain.map { it.key } shouldNotContain token
            withPrivate.map { it.key } shouldContain token
            graph.backupPreferences.backupInterval.set(12)
        }

    @Test
    fun `a backup that can't be written fails and keeps the file it was replacing`(): Unit = runBlocking {
        TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        val target = File(dir, "existente.tachibk").apply { writeText("anterior") }
        // A folder where the temporary file goes: writing it fails
        File(dir, "existente.tachibk.tmp/bloqueo").apply { parentFile.mkdirs() }.writeText("x")

        shouldThrowAny { graph.backupCreatorFactory.create(isAutoBackup = false).backup(target, BackupOptions()) }

        target.readText() shouldBe "anterior"
    }
}
