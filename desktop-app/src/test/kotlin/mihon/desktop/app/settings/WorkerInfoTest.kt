package mihon.desktop.app.settings

import io.kotest.matchers.string.shouldContain
import mihon.desktop.core.jobs.PeriodicJob
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class WorkerInfoTest {
    private val hour = 1.hours.inWholeMilliseconds

    private fun job(interval: Duration?, lastRun: Long) = PeriodicJob(
        name = "LibraryUpdate",
        interval = { interval },
        lastRun = InMemoryPreferenceStore().getLong("last", lastRun),
        run = {},
    )

    @Test
    fun `a turned off job never runs`() {
        val text = jobSummary(job(null, 0), running = false, now = 10 * hour, zone = ZoneOffset.UTC)
        text shouldContain "Interval: off"
        text shouldContain "Last run: never"
        text shouldContain "Next run: -"
    }

    @Test
    fun `a pending job runs one interval after its last run`() {
        val text = jobSummary(job(12.hours, 2 * hour), running = false, now = 3 * hour, zone = ZoneOffset.UTC)
        text shouldContain "Last run: 1970-01-01T02:00"
        text shouldContain "Next run: 1970-01-01T14:00"
        text shouldContain "Running: false"
    }

    @Test
    fun `an overdue job runs at the next check`() {
        val text = jobSummary(job(1.hours, 0), running = true, now = 5 * hour, zone = ZoneOffset.UTC)
        text shouldContain "Next run: next check"
        text shouldContain "Running: true"
    }
}
