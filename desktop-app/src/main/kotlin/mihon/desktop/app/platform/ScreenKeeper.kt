package mihon.desktop.app.platform

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase

/**
 * Keeps the display on while the reader is open (Mihon's keepScreenOn, spec 8e). Windows ties the request to the
 * thread that makes it, so it's made and cleared on the UI thread. Returns the previous state, 0 off Windows.
 */
object ScreenKeeper {
    fun keepOn(on: Boolean): Int {
        if (!Platform.isWindows()) return 0
        val flags = if (on) WinBase.ES_CONTINUOUS or WinBase.ES_DISPLAY_REQUIRED else WinBase.ES_CONTINUOUS
        return Kernel32.INSTANCE.SetThreadExecutionState(flags)
    }
}
