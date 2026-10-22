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

    private val _selectAll = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val selectAll: SharedFlow<Unit> = _selectAll

    /** +1 for the next library category, -1 for the previous one. */
    private val _categoryStep = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val categoryStep: SharedFlow<Int> = _categoryStep

    /** True when something handled it, so the key event stops there. */
    fun send(shortcut: Shortcut): Boolean = when (shortcut) {
        Shortcut.Back -> {
            WindowBackInput.back()
            true
        }
        Shortcut.Refresh -> _refresh.tryEmit(Unit)
        Shortcut.GlobalSearch -> _globalSearch.tryEmit(Unit)
        Shortcut.Settings -> _openSettings.tryEmit(Unit)
        Shortcut.SelectAll -> _selectAll.tryEmit(Unit)
        Shortcut.NextCategory -> _categoryStep.tryEmit(1)
        Shortcut.PreviousCategory -> _categoryStep.tryEmit(-1)
    }
}
