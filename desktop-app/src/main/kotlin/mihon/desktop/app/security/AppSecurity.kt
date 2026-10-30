package mihon.desktop.app.security

import com.sun.jna.Native
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.core.security.WindowsHello
import java.awt.Window

/** The window Windows Hello's dialog belongs to, and the checks the lock and Settings › Security ask for (11b). */
object AppSecurity {
    @Volatile
    var window: Window? = null

    /** Blocking the first time (a WinRT call), cached after; ask it off the UI thread when you can. */
    val helloAvailable: Boolean by lazy { WindowsHello.isAvailable() }

    suspend fun verify(message: String): Boolean = withContext(Dispatchers.IO) {
        val owner = window?.takeIf { it.isDisplayable } ?: return@withContext false
        WindowsHello.verify(Native.getWindowPointer(owner), message)
    }
}
