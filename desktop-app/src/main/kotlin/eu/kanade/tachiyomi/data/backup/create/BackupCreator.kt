package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupWriter
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionStoresBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.desktop.core.DesktopBuildInfo
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.MR
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Clock

@AssistedInject
class BackupCreator(
    @Assisted private val isAutoBackup: Boolean,
    private val context: Context,
    private val parser: ProtoBuf,
    private val getFavorites: GetFavorites,
    private val backupPreferences: BackupPreferences,
    private val mangaRepository: MangaRepository,
    private val categoriesBackupCreator: CategoriesBackupCreator,
    private val mangaBackupCreator: MangaBackupCreator,
    private val preferenceBackupCreator: PreferenceBackupCreator,
    private val extensionStoresBackupCreator: ExtensionStoresBackupCreator,
    private val sourcesBackupCreator: SourcesBackupCreator,
    private val backupFileValidator: BackupFileValidator,
) {
    @AssistedFactory
    fun interface Factory {
        fun create(isAutoBackup: Boolean): BackupCreator
    }

    /**
     * desktop: [file] is the destination, or the folder for an automatic backup. The backup is written next to it
     * as a temporary file and only replaces it once complete and valid, so a failed backup never leaves a partial
     * .tachibk nor loses the one it was replacing (spec 5a §3 A). Older automatic backups go once the new one is in.
     */
    suspend fun backup(file: File, options: BackupOptions): String {
        var tmp: File? = null
        try {
            val destination = if (isAutoBackup) {
                file.mkdirs()
                File(file, getFilename())
            } else {
                file
            }
            if (destination.parentFile?.isDirectory != true) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }
            tmp = File(destination.path + ".tmp")

            val nonFavoriteManga = if (options.readEntries) mangaRepository.getReadMangaNotInLibrary() else emptyList()
            val backupManga = backupMangas(getFavorites.await() + nonFavoriteManga, options)

            tmp.outputStream().sink().gzip().buffer().use {
                val writer = BackupWriter(it, parser)
                val sourceIds = mutableSetOf<Long>()
                backupManga.collect { manga ->
                    writer.writeEntry(manga)
                    sourceIds += manga.source
                }
                writer.writeMetadata(
                    Backup(
                        backupManga = emptyList(),
                        backupCategories = backupCategories(options),
                        backupSources = backupSources(sourceIds),
                        backupPreferences = backupAppPreferences(options),
                        backupExtensionStores = backupExtensionStores(options),
                        backupSourcePreferences = backupSourcePreferences(options),
                    ),
                )
                if (writer.isEmpty) {
                    throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
                }
            }

            // Make sure it's a valid backup file
            backupFileValidator.validate(tmp)
            Files.move(tmp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)

            if (isAutoBackup) {
                file.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(MAX_AUTO_BACKUPS)
                    .forEach { it.delete() }
                backupPreferences.lastAutoBackupTimestamp.set(Clock.System.now().toEpochMilliseconds())
            }

            return destination.path
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            tmp?.takeIf { it.isFile }?.delete()
            throw e
        }
    }

    private suspend fun backupCategories(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return categoriesBackupCreator()
    }

    private fun backupMangas(mangas: List<Manga>, options: BackupOptions): Flow<BackupManga> {
        if (!options.libraryEntries) return emptyFlow()

        return mangaBackupCreator(mangas, options)
    }

    private suspend fun backupSources(sourceIds: Set<Long>): List<BackupSource> {
        return sourcesBackupCreator(sourceIds)
    }

    private fun backupAppPreferences(options: BackupOptions): List<BackupPreference> {
        if (!options.appSettings) return emptyList()

        return preferenceBackupCreator.createApp(includePrivatePreferences = options.privateSettings)
    }

    private suspend fun backupExtensionStores(options: BackupOptions): List<BackupExtensionStore> {
        if (!options.extensionStores) return emptyList()

        return extensionStoresBackupCreator()
    }

    private suspend fun backupSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createSource(includePrivatePreferences = options.privateSettings)
    }

    companion object {
        private const val MAX_AUTO_BACKUPS: Int = 4
        // desktop: the desktop app's id
        private val FILENAME_REGEX = """${DesktopBuildInfo.APPLICATION_ID}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk""".toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${DesktopBuildInfo.APPLICATION_ID}_$date.tachibk" // desktop: the desktop app's id
        }
    }
}
