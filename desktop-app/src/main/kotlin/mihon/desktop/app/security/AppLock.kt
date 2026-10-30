package mihon.desktop.app.security

import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mihon's SecureActivityDelegate without Android (spec 11b §2): locked at start, and on coming back from the
 * background (minimized or in the tray) after "lock when idle". Only [unlock] with a verified user opens it.
 */
class AppLock(
    private val preferences: SecurityPreferences,
    /** Windows Hello is set up for this user; without it the app never locks, or nobody could open it. */
    private val isAvailable: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val enabled get() = preferences.useAuthenticator.get() && isAvailable()

    fun onStart() {
        _locked.value = enabled
    }

    fun onBackground() {
        if (!enabled || _locked.value) return
        if (preferences.lockAppAfter.get() > 0) preferences.lastAppClosed.set(now())
    }

    fun onForeground() {
        if (!enabled) return
        val lastClosed = preferences.lastAppClosed
        if (!_locked.value) {
            _locked.value = when (val minutes = preferences.lockAppAfter.get()) {
                -1 -> false
                0 -> true
                else -> lastClosed.get() + minutes * 60_000L <= now()
            }
        }
        lastClosed.delete()
    }

    /** Asks [verify] (Windows Hello) and opens the app if the user passed it. */
    suspend fun unlock(verify: suspend () -> Boolean): Boolean {
        if (!verify()) return false
        _locked.value = false
        return true
    }
}
