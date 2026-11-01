package mihon.desktop.app.tray

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import logcat.LogPriority
import mihon.desktop.core.AppDirs
import tachiyomi.core.common.util.system.logcat
import java.nio.file.Path

/** Starting with Windows, in the tray (spec 13 §2): this user's Run entry, no admin rights. */
object Autostart {
    /** The start Windows makes at sign-in: the window stays hidden in the tray. */
    const val TRAY_ARG = "--tray"

    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    fun command(exe: Path) = "\"$exe\" $TRAY_ARG"

    /** Writes the entry for [exe], or removes it; written on every start so it follows the exe if it moved. */
    fun apply(enabled: Boolean, exe: Path, key: String = RUN_KEY) {
        try {
            if (enabled) {
                Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key)
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, AppDirs.APP_NAME, command(exe))
            } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, AppDirs.APP_NAME)) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, key, AppDirs.APP_NAME)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Couldn't change the start with Windows entry" }
        }
    }
}
