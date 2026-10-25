package mihon.desktop.app.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MarkdownRender
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Mihon's NewUpdateScreen as a dialog (spec 6b §3 B): the notes, then the download's progress or what went wrong. */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onUpdate: (AppRelease) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Failed -> state.release
        UpdateState.Idle, UpdateState.Installing -> return
    }
    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) onDismiss() },
        title = {
            Text("${stringResource(MR.strings.update_check_notification_update_available)} ${release.version}")
        },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state) {
                    is UpdateState.Downloading -> {
                        Text(stringResource(MR.strings.desktop_update_downloading, release.msiName))
                        val progress = state.progress
                        if (progress == null) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is UpdateState.Failed -> Text(state.message)
                    else -> MarkdownRender(content = release.notes)
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Downloading -> Unit
                is UpdateState.Failed -> TextButton(onClick = { onUpdate(release) }) {
                    Text(stringResource(MR.strings.action_retry))
                }
                else -> TextButton(onClick = { onUpdate(release) }) {
                    Text(stringResource(MR.strings.update_check_confirm))
                }
            }
        },
        dismissButton = {
            if (state is UpdateState.Downloading) {
                TextButton(onClick = onCancel) { Text(stringResource(MR.strings.action_cancel)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_not_now)) }
            }
        },
    )
}
