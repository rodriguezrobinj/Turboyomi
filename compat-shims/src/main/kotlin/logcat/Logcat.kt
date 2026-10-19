package logcat

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter

enum class LogPriority(val priorityInt: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
    ASSERT(7),
}

inline fun Any.logcat(
    priority: LogPriority = LogPriority.DEBUG,
    tag: String? = null,
    message: () -> String,
) {
    val logger = LoggerFactory.getLogger(tag ?: outerClassSimpleName(this))
    if (logger.isEnabled(priority)) logger.write(priority, message())
}

inline fun logcat(
    tag: String,
    priority: LogPriority = LogPriority.DEBUG,
    message: () -> String,
) {
    val logger = LoggerFactory.getLogger(tag)
    if (logger.isEnabled(priority)) logger.write(priority, message())
}

fun Throwable.asLog(): String {
    val writer = StringWriter(256)
    PrintWriter(writer, false).use { printStackTrace(it) }
    return writer.toString()
}

@PublishedApi
internal fun outerClassSimpleName(any: Any): String =
    any.javaClass.name.substringBefore('$').substringAfterLast('.')

@PublishedApi
internal fun Logger.isEnabled(priority: LogPriority): Boolean = when (priority) {
    LogPriority.VERBOSE -> isTraceEnabled
    LogPriority.DEBUG -> isDebugEnabled
    LogPriority.INFO -> isInfoEnabled
    LogPriority.WARN -> isWarnEnabled
    LogPriority.ERROR, LogPriority.ASSERT -> isErrorEnabled
}

@PublishedApi
internal fun Logger.write(priority: LogPriority, message: String) = when (priority) {
    LogPriority.VERBOSE -> trace(message)
    LogPriority.DEBUG -> debug(message)
    LogPriority.INFO -> info(message)
    LogPriority.WARN -> warn(message)
    LogPriority.ERROR, LogPriority.ASSERT -> error(message)
}
