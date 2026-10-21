package mihon.desktop.app.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Work that must finish before the window closes, such as the reader's last progress write. */
object ExitHooks {
    private val hooks = CopyOnWriteArraySet<suspend () -> Unit>()

    fun add(hook: suspend () -> Unit) {
        hooks += hook
    }

    fun remove(hook: suspend () -> Unit) {
        hooks -= hook
    }

    /** Runs every hook; a failing one doesn't stop the others, and together they can't hold the exit past [timeout]. */
    suspend fun runAll(timeout: Duration = 2.seconds) {
        withTimeoutOrNull(timeout) {
            hooks.forEach { hook ->
                try {
                    hook()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Exit hook failed" }
                }
            }
        }
    }
}
