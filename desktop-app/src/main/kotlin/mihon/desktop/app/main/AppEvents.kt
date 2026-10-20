package mihon.desktop.app.main

import androidx.activity.compose.BackPressDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Window shortcuts the visible screen reacts to; only the screen on top is composed, so only it collects. */
object AppEvents {
    private val _refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refresh: SharedFlow<Unit> = _refresh

    private val _globalSearch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val globalSearch: SharedFlow<Unit> = _globalSearch

    private val _openSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openSettings: SharedFlow<Unit> = _openSettings

    /** True when something handled it, so the key event stops there. */
    fun send(shortcut: Shortcut): Boolean = when (shortcut) {
        Shortcut.Back -> BackPressDispatcher.dispatch()
        Shortcut.Refresh -> _refresh.tryEmit(Unit)
        Shortcut.GlobalSearch -> _globalSearch.tryEmit(Unit)
        Shortcut.Settings -> _openSettings.tryEmit(Unit)
    }
}
