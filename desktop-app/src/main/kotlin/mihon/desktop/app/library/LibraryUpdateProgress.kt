package mihon.desktop.app.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import mihon.desktop.app.platform.IconTooltip
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The running library update as an entry of the navigation rail, where it doesn't push the screen down: a ring with
 * done/total, the manga being updated on hover, and Cancel on click (spec 3a §3 G). Nothing while none runs.
 */
@Composable
fun LibraryUpdateRailItem(job: LibraryUpdateJob) {
    val progress by job.progress.collectAsState()
    val current = progress ?: return
    var menu by remember { mutableStateOf(false) }
    val text = stringResource(
        MR.strings.desktop_update_progress,
        current.done,
        current.total,
        current.current.firstOrNull().orEmpty(),
    )
    Box {
        IconTooltip(text) {
            NavigationRailItem(
                selected = false,
                onClick = { menu = true },
                icon = {
                    if (current.total == 0) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    } else {
                        CircularProgressIndicator(
                            progress = { current.done.toFloat() / current.total },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                },
                label = { Text("${current.done}/${current.total}") },
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(text) }, onClick = {}, enabled = false)
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_cancel)) },
                onClick = {
                    job.cancel()
                    menu = false
                },
            )
        }
    }
}
