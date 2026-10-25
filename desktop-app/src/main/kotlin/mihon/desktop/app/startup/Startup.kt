package mihon.desktop.app.startup

import logcat.LogPriority
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopBuildInfo
import tachiyomi.core.common.util.system.logcat
import java.nio.file.Path
import javax.swing.JOptionPane
import kotlin.system.exitProcess

object Startup {
    /** Must run before the first logger is created: points logback at the app config and its log folder. */
    fun configureLogging(dirs: AppDirs) {
        // The single-instance lock (in data/) and the log folder must exist before anything else
        dirs.createAll()
        System.setProperty("turboyomi.logs", dirs.logs.toString())
        System.setProperty("logback.configurationFile", "logback-app.xml")
        logcat(LogPriority.INFO) {
            "Starting ${AppDirs.APP_NAME} ${DesktopBuildInfo.VERSION_NAME} (data: ${dirs.data})"
        }
        // A failed view model coroutine ends here too (Dispatchers.Main is the Swing thread): log it and tell the
        // user, the screen stays up
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            logcat(LogPriority.ERROR, error) { "Uncaught exception in ${thread.name}" }
            AppMessages.show("Error: ${error.message ?: error::class.simpleName}")
        }
    }

    /** The app can't start: says why and where the log is, then exits with 1. Never leaves a windowless process. */
    fun fatal(message: String, logs: Path): Nothing = stop("$message\n\nLog: $logs")

    /** The app can't start and there is no log to point at: says why, then exits with 1. */
    fun stop(message: String): Nothing {
        JOptionPane.showMessageDialog(null, message, AppDirs.APP_NAME, JOptionPane.ERROR_MESSAGE)
        exitProcess(1)
    }
}
