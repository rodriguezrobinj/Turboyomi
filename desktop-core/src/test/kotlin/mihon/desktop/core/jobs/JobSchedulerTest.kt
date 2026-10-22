package mihon.desktop.core.jobs

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class JobSchedulerTest {
    // InMemoryPreferenceStore hands out a separate value per call: the job and the test share this one
    private val last = InMemoryPreferenceStore().getLong("last", 0L)
    private var time = 100.hours.inWholeMilliseconds

    private fun job(
        interval: Duration? = 12.hours,
        run: suspend () -> Unit = {},
    ) = PeriodicJob("Update", { interval }, last, run)

    @Test
    fun `a job whose interval has passed runs, and its last run is saved before it starts`(): Unit = runBlocking {
        val seenLastRun = CompletableDeferred<Long>()
        val job = job { seenLastRun.complete(last.get()) }
        val scheduler = JobScheduler(listOf(job)) { time }

        coroutineScope { scheduler.check(this) shouldContainExactly listOf("Update") }

        withTimeout(5.seconds) { seenLastRun.await() } shouldBe time
    }

    @Test
    fun `a job that ran less than an interval ago doesn't run`(): Unit = runBlocking {
        last.set(time - 11.hours.inWholeMilliseconds)
        val scheduler = JobScheduler(listOf(job())) { time }

        coroutineScope { scheduler.check(this).shouldBeEmpty() }
    }

    @Test
    fun `a job with no interval never runs`(): Unit = runBlocking {
        val scheduler = JobScheduler(listOf(job(interval = null))) { time }

        coroutineScope { scheduler.check(this).shouldBeEmpty() }
    }

    @Test
    fun `right after the computer wakes up it waits one more check, so the network is back`(): Unit = runBlocking {
        last.set(time - 1.hours.inWholeMilliseconds)
        val scheduler = JobScheduler(listOf(job())) { time }
        coroutineScope { scheduler.check(this).shouldBeEmpty() }

        // Asleep for 12 h: the job is due, but this check is the wake-up one
        time += 12.hours.inWholeMilliseconds
        coroutineScope { scheduler.check(this).shouldBeEmpty() }

        time += 1.minutes.inWholeMilliseconds
        coroutineScope { scheduler.check(this) shouldContainExactly listOf("Update") }
    }

    @Test
    fun `a job still running isn't launched again`(): Unit = runBlocking {
        val release = CompletableDeferred<Unit>()
        val scheduler = JobScheduler(listOf(job(interval = 1.minutes) { release.await() })) { time }

        coroutineScope {
            scheduler.check(this) shouldContainExactly listOf("Update")
            time += 1.minutes.inWholeMilliseconds
            scheduler.check(this).shouldBeEmpty()
            release.complete(Unit)
        }
    }

    @Test
    fun `a job that throws is logged and can run again later`(): Unit = runBlocking {
        val scheduler = JobScheduler(listOf(job(interval = 1.minutes) { error("boom") })) { time }

        coroutineScope { scheduler.check(this) shouldContainExactly listOf("Update") }
        time += 1.minutes.inWholeMilliseconds
        coroutineScope { scheduler.check(this) shouldContainExactly listOf("Update") }
    }
}
