package mihon.desktop.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.desktop.core.cef.CefInstaller
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The download dialog (spec 2b §3 D): its real size, progress, cancel, and the error with a retry. Shown while
 * [EmbeddedBrowser.consent] is set; once installed, [startBrowser] starts it and the page asked for opens.
 */
@Composable
fun EmbeddedBrowserConsent(installer: CefInstaller, startBrowser: () -> Unit) {
    EmbeddedBrowser.consent ?: return
    val scope = rememberCoroutineScope()
    val size by produceState<Long?>(null) { value = runCatching { installer.downloadSize() }.getOrNull() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun download() {
        error = null
        progress = 0f
        job = scope.launch {
            try {
                installer.install { progress = it }
                startBrowser()
                EmbeddedBrowser.installed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Couldn't install the embedded browser" }
                error = e.message ?: e.toString()
            } finally {
                progress = null
                job = null
            }
        }
    }

    AlertDialog(
        // Only the buttons close it: a click outside mustn't throw away a download in progress
        onDismissRequest = {},
        title = { Text(stringResource(MR.strings.desktop_cef_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val megabytes = size?.let { "%.0f MB".format(it / 1_000_000.0) } ?: "~280 MB"
                Text(stringResource(MR.strings.desktop_cef_consent_message, megabytes))
                progress?.let {
                    Text(stringResource(MR.strings.desktop_cef_downloading))
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                }
                error?.let {
                    Text(
                        stringResource(MR.strings.desktop_cef_install_failed, it),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (progress == null) {
                TextButton(onClick = ::download) {
                    Text(
                        stringResource(
                            if (error ==
                                null
                            ) {
                                MR.strings.desktop_cef_download
                            } else {
                                MR.strings.action_retry
                            },
                        ),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    job?.cancel()
                    EmbeddedBrowser.dismissConsent()
                },
            ) {
                Text(stringResource(if (progress == null) MR.strings.desktop_cef_not_now else MR.strings.action_cancel))
            }
        },
    )
}
