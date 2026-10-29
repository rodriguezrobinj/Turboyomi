package mihon.desktop.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory

object DesktopLogging {
    /** "Verbose logging" in Settings › Advanced: debug messages in the app log too. */
    fun setVerbose(on: Boolean) {
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger)?.level = if (on) Level.DEBUG else Level.INFO
    }
}
