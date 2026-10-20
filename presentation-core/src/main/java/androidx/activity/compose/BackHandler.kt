package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Desktop stand-in for AndroidX's OnBackPressedDispatcher: the window sends Esc and Alt+Left here. Like on
 * Android, the handler registered last that is enabled wins.
 */
object BackPressDispatcher {
    internal class Entry(var enabled: Boolean, val onBack: () -> Unit)

    private val entries = mutableListOf<Entry>()

    internal fun add(entry: Entry) {
        entries += entry
    }

    internal fun remove(entry: Entry) {
        entries -= entry
    }

    /** Runs the innermost enabled handler; false when none would handle it. */
    fun dispatch(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }
}

// desktop: same signature as androidx.activity.compose.BackHandler
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val entry = remember { BackPressDispatcher.Entry(enabled) { currentOnBack() } }
    SideEffect { entry.enabled = enabled }
    DisposableEffect(entry) {
        BackPressDispatcher.add(entry)
        onDispose { BackPressDispatcher.remove(entry) }
    }
}
