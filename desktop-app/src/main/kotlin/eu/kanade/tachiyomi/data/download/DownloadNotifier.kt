package eu.kanade.tachiyomi.data.download

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.download.model.Download
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

/**
 * Desktop counterpart of Mihon's DownloadNotifier (spec 4b §2): the queue shows the progress; the tray, or an in-app
 * message without it, tells of a finished queue, an error and a warning. Errors come at most every 5 s: the tray stacks
 * them, where Android replaces one notification.
 */
@Inject
@SingleIn(AppScope::class)
class DownloadNotifier(
    private val context: Context,
    private val securityPreferences: SecurityPreferences,
) {
    @Volatile
    private var downloaded = false

    @Volatile
    private var lastError = 0L

    private val title get() = context.stringResource(MR.strings.download_notifier_downloader_title)

    fun onProgressChange(download: Download) {
        if (download.status == Download.State.DOWNLOADING) downloaded = true
    }

    fun dismissProgress() {}

    fun onPaused() {
        downloaded = false
    }

    fun onComplete() {
        if (!downloaded) return
        downloaded = false
        TrayNotifier.send(
            Notice(title, context.stringResource(MR.strings.desktop_downloads_finished), opensUpdates = false),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun onWarning(reason: String, timeout: Long? = null, mangaId: Long? = null) {
        TrayNotifier.send(Notice(title, reason, opensUpdates = false))
    }

    @Suppress("UNUSED_PARAMETER")
    fun onError(error: String? = null, chapter: String? = null, mangaTitle: String? = null, mangaId: Long? = null) {
        val now = System.currentTimeMillis()
        if (now - lastError < ERROR_INTERVAL_MS) return
        lastError = now
        TrayNotifier.send(
            Notice(
                // Mihon's "hide notification content" (spec 11b §2): no title or chapter
                title = mangaTitle?.takeUnless { securityPreferences.hideNotificationContent.get() }
                    ?.plus(": $chapter") ?: title,
                message = error ?: context.stringResource(MR.strings.download_notifier_unknown_error),
                isError = true,
                opensUpdates = false,
            ),
        )
    }

    private companion object {
        const val ERROR_INTERVAL_MS = 5_000L
    }
}
