package mihon.desktop.app.security

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import eu.kanade.tachiyomi.core.security.SecurityPreferences.SecureScreenMode
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.awt.Window

/**
 * Mihon's secure screen (spec 11b §2): screenshots, recordings and screen sharing leave the window out. Android's
 * FLAG_SECURE is here the window's display affinity.
 */
object SecureScreen {
    /** Mihon's rule: always, or in incognito mode when the setting says so. */
    fun isSecure(mode: SecureScreenMode, incognito: Boolean): Boolean =
        mode == SecureScreenMode.ALWAYS || (mode == SecureScreenMode.INCOGNITO && incognito)

    fun apply(window: Window, secure: Boolean) {
        if (!window.isDisplayable) return
        val hwnd = WinDef.HWND(Native.getWindowPointer(window))
        val user32 = User32Affinity.INSTANCE
        if (!secure) {
            user32.SetWindowDisplayAffinity(hwnd, WDA_NONE)
            return
        }
        // Windows 10 before 2004 lacks WDA_EXCLUDEFROMCAPTURE; WDA_MONITOR shows the window black instead
        if (!user32.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE) &&
            !user32.SetWindowDisplayAffinity(hwnd, WDA_MONITOR)
        ) {
            logcat(LogPriority.WARN) { "Couldn't hide the window from screen captures: ${Native.getLastError()}" }
        }
    }

    @Suppress("FunctionName")
    private interface User32Affinity : StdCallLibrary {
        fun SetWindowDisplayAffinity(hwnd: WinDef.HWND, affinity: Int): Boolean

        companion object {
            val INSTANCE: User32Affinity =
                Native.load("user32", User32Affinity::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    private const val WDA_NONE = 0
    private const val WDA_MONITOR = 1
    private const val WDA_EXCLUDEFROMCAPTURE = 0x11
}
