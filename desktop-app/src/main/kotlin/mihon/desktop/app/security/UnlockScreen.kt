package mihon.desktop.app.security

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.desktop.core.AppDirs
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Mihon's UnlockActivity as a cover over the whole window (spec 11b §2): asks Windows Hello once when it shows and
 * again from its button or Enter. It takes the focus and every key, and the Surface stops clicks reaching the app.
 */
@Composable
fun UnlockScreen(onUnlock: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var asking by remember { mutableStateOf(false) }
    val unlock: () -> Unit = {
        if (!asking) {
            asking = true
            scope.launch {
                try {
                    onUnlock()
                } finally {
                    asking = false
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        unlock()
    }
    Surface(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) unlock()
                true
            }
            .focusable(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Image(painterResource("turboyomi.svg"), contentDescription = null, Modifier.size(96.dp))
            Button(onClick = unlock, enabled = !asking) {
                Text(stringResource(MR.strings.unlock_app_title, AppDirs.APP_NAME))
            }
        }
    }
}
