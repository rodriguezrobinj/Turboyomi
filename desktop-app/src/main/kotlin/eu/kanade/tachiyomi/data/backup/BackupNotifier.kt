package eu.kanade.tachiyomi.data.backup

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.awt.Desktop
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Desktop counterpart of Mihon's BackupNotifier (spec 5a §2): the restore's progress is a flow the Data screen shows;
 * finished backups and restores and their errors go to the tray, or to an in-app message without it.
 */
@Inject
@SingleIn(AppScope::class)
class BackupNotifier(private val context: Context) {
    data class Progress(val content: String, val done: Int, val total: Int)

    private val _restoreProgress = MutableStateFlow<Progress?>(null)
    val restoreProgress: StateFlow<Progress?> = _restoreProgress.asStateFlow()

    fun showBackupComplete(file: File) {
        TrayNotifier.send(Notice(context.stringResource(MR.strings.backup_created), file.path, opensUpdates = false))
    }

    fun showBackupError(error: String?) {
        TrayNotifier.send(
            Notice(
                context.stringResource(MR.strings.creating_backup_error),
                error.orEmpty(),
                isError = true,
                opensUpdates = false,
            ),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun showRestoreProgress(content: String = "", progress: Int = 0, maxAmount: Int = 100, sync: Boolean = false) {
        _restoreProgress.value = Progress(content, progress, maxAmount)
    }

    fun showRestoreError(error: String?) {
        _restoreProgress.value = null
        TrayNotifier.send(
            Notice(
                context.stringResource(MR.strings.restoring_backup_error),
                error.orEmpty(),
                isError = true,
                opensUpdates = false,
            ),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun showRestoreComplete(time: Long, errorCount: Int, path: String?, file: String?, sync: Boolean) {
        _restoreProgress.value = null
        val timeString = context.stringResource(
            MR.strings.restore_duration,
            time.milliseconds.inWholeMinutes,
            time.milliseconds.inWholeSeconds - (time.milliseconds.inWholeMinutes * 60),
        )
        val message = context.pluralStringResource(
            MR.plurals.restore_completed_message,
            errorCount,
            timeString,
            errorCount,
        )
        TrayNotifier.send(
            Notice(
                context.stringResource(MR.strings.restore_completed),
                message,
                isError = errorCount > 0,
                opensUpdates = false,
            ),
        )
        if (errorCount > 0 && !path.isNullOrEmpty() && !file.isNullOrEmpty()) {
            // The tray can't carry a button, so the log opens from an in-app message, as in 3a
            AppMessages.show(message, context.stringResource(MR.strings.action_show_errors)) {
                runCatching { Desktop.getDesktop().open(File(path, file)) }
            }
        }
    }
}
