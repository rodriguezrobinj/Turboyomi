package mihon.desktop.app.main

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Window shortcuts. Refresh goes to the visible screen (only the top one is composed, so only it collects);
 * global search and settings are collected by AppContent at the root.
 */
object AppEvents {
    private val _refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refresh: SharedFlow<Unit> = _refresh

    private val _globalSearch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val globalSearch: SharedFlow<Unit> = _globalSearch

    private val _openSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openSettings: SharedFlow<Unit> = _openSettings

    /** True when something handled it, so the key event stops there. */
    fun send(shortcut: Shortcut): Boolean = when (shortcut) {
        Shortcut.Back -> {
            WindowBackInput.back()
            true
        }
        Shortcut.Refresh -> _refresh.tryEmit(Unit)
        Shortcut.GlobalSearch -> _globalSearch.tryEmit(Unit)
        Shortcut.Settings -> _openSettings.tryEmit(Unit)
    }
}
