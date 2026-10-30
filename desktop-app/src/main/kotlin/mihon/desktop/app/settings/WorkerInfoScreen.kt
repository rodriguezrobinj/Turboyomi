package mihon.desktop.app.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import mihon.desktop.app.DesktopApp
import mihon.desktop.core.jobs.PeriodicJob
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ContentCopy
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.keyboardScrollable
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Mihon's WorkManager info, here the in-process JobScheduler's jobs (spec 11a §2). English, like Mihon's. */
class WorkerInfoScreen : Screen() {

    companion object {
        const val TITLE = "Worker info"
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val text = remember {
            val scheduler = DesktopApp.jobScheduler ?: return@remember "No jobs"
            scheduler.jobs.joinToString("\n\n") { job ->
                jobSummary(job, scheduler.isRunning(job.name), System.currentTimeMillis(), ZoneId.systemDefault())
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = TITLE,
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_copy_to_clipboard),
                                    icon = MaterialSymbols.Rounded.ContentCopy,
                                    onClick = { context.copyToClipboard(TITLE, text) },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            val scroll = rememberScrollState()
            Text(
                text = text,
                modifier = Modifier
                    .keyboardScrollable(scroll)
                    .verticalScroll(scroll)
                    .padding(contentPadding)
                    .padding(16.dp),
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** A job's state as Mihon's worker info lists it: name, interval, last and next run, running. */
fun jobSummary(job: PeriodicJob, running: Boolean, now: Long, zone: ZoneId): String {
    val interval = job.interval()
    val lastRun = job.lastRun.get()
    fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)
    val next = when {
        interval == null -> "-"
        // The scheduler checks every minute, so an overdue job starts at the next check
        lastRun + interval.inWholeMilliseconds <= now -> "next check"
        else -> time(lastRun + interval.inWholeMilliseconds).toString()
    }
    return listOf(
        "Name: ${job.name}",
        "Interval: ${interval ?: "off"}",
        "Last run: ${if (lastRun > 0) time(lastRun) else "never"}",
        "Next run: $next",
        "Running: $running",
    ).joinToString("\n")
}
