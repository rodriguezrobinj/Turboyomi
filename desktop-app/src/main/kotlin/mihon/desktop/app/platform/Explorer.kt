package mihon.desktop.app.platform

import android.content.Context
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.awt.Desktop
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

/** Shows a download in Windows' File Explorer (spec 13 §3). */
object Explorer {
    /** A folder opens; a file opens in its folder, selected. */
    fun show(path: Path) {
        if (path.isDirectory()) {
            Desktop.getDesktop().open(
                path.toFile(),
            )
        } else {
            ProcessBuilder(selectCommand(path)).start()
        }
    }

    /** A manga's or chapter's download as DownloadProvider finds it; null tells that nothing is downloaded. */
    fun showDownload(context: Context, path: String?) {
        if (path == null) {
            context.toast(MR.strings.desktop_show_in_explorer_nothing)
            return
        }
        runCatching { show(Path(path)) }.onFailure {
            logcat(LogPriority.ERROR, it) { "Couldn't open File Explorer" }
            context.toast(it.message)
        }
    }

    // The path as its own argument: explorer takes "/select," and the quoted path that follows it
    fun selectCommand(file: Path) = listOf("explorer.exe", "/select,", file.toString())
}
