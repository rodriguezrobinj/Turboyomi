package eu.kanade.tachiyomi.data.backup

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.desktop.core.jobs.PeriodicJob
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import java.io.File
import kotlin.time.Duration.Companion.hours

/**
 * Desktop counterpart of Mihon's BackupCreateWorker and BackupRestoreWorker (spec 5a §2): one backup or restore at a
 * time on the app scope, which outlives the window while the app is in the tray.
 */
@Inject
@SingleIn(AppScope::class)
class BackupJob(
    private val context: Context,
    private val backupCreatorFactory: BackupCreator.Factory,
    private val backupRestorerFactory: BackupRestorer.Factory,
    private val notifier: BackupNotifier,
    private val storageManager: StorageManager,
    private val backupPreferences: BackupPreferences,
    preferenceStore: PreferenceStore,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    enum class Operation { Create, Restore }

    private val _running = MutableStateFlow<Operation?>(null)
    val running: StateFlow<Operation?> = _running.asStateFlow()

    @Volatile
    private var job: Job? = null

    // The scheduler marks a run before it starts; Mihon's lastAutoBackupTimestamp moves only on success (plan 5a, dev. 4)
    private val lastAutoBackupAttempt =
        preferenceStore.getLong(Preference.appStateKey("desktop_last_auto_backup_attempt"), 0L)

    /** Writes a backup to [file]. False if a backup or a restore is already running. */
    fun create(file: File, options: BackupOptions): Boolean = launch(Operation.Create) {
        try {
            val path = backupCreatorFactory.create(isAutoBackup = false).backup(file, options)
            notifier.showBackupComplete(File(path))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notifier.showBackupError(e.message)
        }
    } != null

    /** Restores [file]. False if a backup or a restore is already running. */
    fun restore(file: File, options: RestoreOptions): Boolean = launch(Operation.Restore) {
        try {
            backupRestorerFactory.create(notifier = notifier, isSync = false).restore(file, options)
        } catch (e: CancellationException) {
            notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_canceled))
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showRestoreError(e.message)
        }
    } != null

    suspend fun awaitIdle() {
        job?.join()
    }

    /** Returns once no restore is running: the automatic jobs hold back meanwhile (spec 5a §3 E). */
    suspend fun awaitRestore() {
        running.first { it != Operation.Restore }
    }

    /** The automatic backup, for the scheduler: [BackupPreferences.backupInterval] hours, 0 = off. */
    fun periodicJob(): PeriodicJob = PeriodicJob(
        name = "AutoBackup",
        interval = { backupPreferences.backupInterval.get().takeIf { it > 0 }?.hours },
        lastRun = lastAutoBackupAttempt,
        run = ::createAutomatic,
    )

    /** One automatic backup into the storage's autobackup folder, once whatever backup or restore is running ends. */
    suspend fun createAutomatic() {
        while (true) {
            running.first { it == null }
            val started = launch(Operation.Create) {
                try {
                    val dir = storageManager.getAutomaticBackupsDirectory()?.filePath?.let(::File)
                        ?: error(context.stringResource(MR.strings.create_backup_file_error))
                    backupCreatorFactory.create(isAutoBackup = true).backup(dir, BackupOptions())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    notifier.showBackupError(e.message)
                }
            } ?: continue
            started.join()
            return
        }
    }

    private fun launch(operation: Operation, block: suspend () -> Unit): Job? = synchronized(this) {
        if (_running.value != null) return null
        _running.value = operation
        scope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                _running.value = null
            }
        }.also { job = it }
    }
}
