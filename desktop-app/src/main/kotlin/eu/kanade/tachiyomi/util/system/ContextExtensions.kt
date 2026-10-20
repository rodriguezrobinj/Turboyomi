package eu.kanade.tachiyomi.util.system

import android.content.Context
import eu.kanade.tachiyomi.util.lang.truncateCenter
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

// Desktop versions of the Context helpers in Mihon's ContextExtensions.kt that ported screens call.

@Suppress("UNUSED_PARAMETER")
fun Context.copyToClipboard(label: String, content: String) {
    if (content.isBlank()) return
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(content), null)
        toast(stringResource(MR.strings.copied_to_clipboard, content.truncateCenter(50)))
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e)
        toast(MR.strings.clipboard_copy_error)
    }
}

@Suppress("UNUSED_PARAMETER")
fun Context.openInBrowser(url: String, forceDefaultBrowser: Boolean = false) {
    try {
        Desktop.getDesktop().browse(URI(url))
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e)
        toast(e.message)
    }
}

// desktop: extensions are never system packages, so the private uninstall path always applies
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun Context.isPackageInstalled(packageName: String): Boolean = false
