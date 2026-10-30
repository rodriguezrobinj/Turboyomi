package mihon.desktop.core.jobs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A task the scheduler runs every [interval] (spec 3a §3 E). A null interval means it's turned off. */
class PeriodicJob(
    val name: String,
    val interval: () -> Duration?,
    /** Epoch milliseconds of the last start; the scheduler saves it before running, like Mihon's update worker. */
    val lastRun: Preference<Long>,
    /** False holds a due job until a later check, like WorkManager's constraints (spec 11f §2). */
    val canRun: () -> Boolean = { true },
    val run: suspend () -> Unit,
)

/**
 * In-process scheduler (parent spec §5.1): checks at start and every [CHECK_EVERY]. Jobs only run while the app is
 * open or in the tray; a missed run happens at the first check after the app comes back.
 */
class JobScheduler(
    val jobs: List<PeriodicJob>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var lastCheck: Long? = null
    private val running: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** For the debug screen's job list (spec 11a §2). */
    fun isRunning(name: String): Boolean = name in running

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            check(this)
            delay(CHECK_EVERY)
        }
    }

    /** One check: launches in [scope] every due job that isn't running yet. Returns the names it launched. */
    fun check(scope: CoroutineScope): List<String> {
        val time = now()
        val previous = lastCheck
        lastCheck = time
        // Back from sleep: the network takes a moment to return, so the due jobs wait for the next check
        if (previous != null && time - previous > RESUME_GAP.inWholeMilliseconds) return emptyList()

        val due = jobs.filter { job ->
            val interval = job.interval() ?: return@filter false
            job.name !in running && time - job.lastRun.get() >= interval.inWholeMilliseconds && job.canRun()
        }
        due.forEach { job ->
            running += job.name
            job.lastRun.set(time)
            scope.launch {
                try {
                    job.run()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Job ${job.name} failed" }
                } finally {
                    running -= job.name
                }
            }
        }
        return due.map { it.name }
    }

    companion object {
        val CHECK_EVERY = 1.minutes
        val RESUME_GAP = 2.minutes
    }
}
