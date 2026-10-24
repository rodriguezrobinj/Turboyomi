package mihon.desktop.app.track

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Registers mangareader:// and app.mangareader:// for this user (HKCU, no admin rights) so the browser can hand an
 * OAuth redirect back to the app (spec 5b-2 §1). Written on every start, so it points at the app that ran last: the
 * exe once packaged, the JVM with its classpath during development.
 */
object ProtocolRegistrar {
    /** Null until [register] runs; false makes Settings › Tracking warn (spec 5b-2 §4). */
    @Volatile
    var registered: Boolean? = null

    fun register(root: String = "Software\\Classes"): Boolean {
        val info = ProcessHandle.current().info()
        val executable = info.command().orElse(null) ?: return false.also { registered = it }
        val command = command(executable, info.arguments().map { it.toList() }.orElse(emptyList()))
        return try {
            TrackLoginHandler.SCHEMES.forEach { scheme ->
                val key = "$root\\$scheme"
                Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, "$key\\shell\\open\\command")
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, "", "URL:MangaReader")
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, "URL Protocol", "")
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, "$key\\shell\\open\\command", "", command)
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Couldn't register the mangareader:// protocol" }
            false
        }.also { registered = it }
    }

    /** The executable always quoted, arguments quoted when they hold spaces, and the url as "%1". */
    fun command(executable: String, arguments: List<String>): String =
        (listOf("\"$executable\"") + arguments.map(::quote)).joinToString(" ") + " \"%1\""

    private fun quote(part: String) = if (part.any { it == ' ' || it == '\t' }) "\"$part\"" else part
}
