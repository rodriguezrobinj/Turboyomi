package mihon.desktop.app.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** The running library update under the top bar, with Cancel (spec 3a §3 G). Nothing while none runs. */
@Composable
fun LibraryUpdateProgress(job: LibraryUpdateJob, modifier: Modifier = Modifier) {
    val progress by job.progress.collectAsState()
    val current = progress ?: return
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { if (current.total == 0) 0f else current.done.toFloat() / current.total },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    MR.strings.desktop_update_progress,
                    current.done,
                    current.total,
                    current.current.firstOrNull().orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = job::cancel) { Text(stringResource(MR.strings.action_cancel)) }
        }
    }
}
