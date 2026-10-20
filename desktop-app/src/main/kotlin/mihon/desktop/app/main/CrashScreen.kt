package mihon.desktop.app.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Shown instead of the window content when composing it threw; [onRestart] composes it again from scratch. */
@Composable
fun CrashScreen(error: Throwable, onRestart: () -> Unit) {
    val context = LocalContext.current
    val trace = error.stackTraceToString()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(MR.strings.crash_screen_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = trace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { context.copyToClipboard("crash", trace) }) {
                Text(stringResource(MR.strings.action_copy_to_clipboard))
            }
            Button(onClick = onRestart) { Text(stringResource(MR.strings.crash_screen_restart_application)) }
        }
    }
}
