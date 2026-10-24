package mihon.desktop.app.track

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Registers mangareader:// and app.mangareader:// for this user (HKCU, no admin rights) so the browser can hand an
 * OAuth redirect back to the app (spec 5b-2 §1). Written on every start, so it points at the app that ran last: the
 * exe once packaged, the JVM with its classpath during development.
 */
object ProtocolRegistrar {
    /** Null until [register] runs; false makes Settings › Tracking warn (spec 5b-2 §4). */
    @Volatile
    var registered: Boolean? = null

    /**
     * [argFile] holds the JVM's own arguments during development: Windows doesn't give a process its arguments back
     * (ProcessHandle's are empty), and a classpath in the command could pass its 32767 characters.
     */
    fun register(argFile: File, root: String = "Software\\Classes"): Boolean {
        val executable = ProcessHandle.current().info().command().orElse(null) ?: return false.also { registered = it }
        val command = try {
            commandFor(
                executable = executable,
                argFile = argFile,
                jvmArgs = { ManagementFactory.getRuntimeMXBean().inputArguments },
                classpath = System.getProperty("java.class.path").orEmpty(),
                mainClass = System.getProperty("sun.java.command").orEmpty().substringBefore(' '),
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Couldn't write the protocol's launch arguments" }
            return false.also { registered = it }
        }
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

    /** The packaged exe runs as it is; a JVM (development) runs from [argFile], written here with what it needs. */
    fun commandFor(
        executable: String,
        argFile: File,
        jvmArgs: List<String>,
        classpath: String,
        mainClass: String,
    ): String = commandFor(executable, argFile, { jvmArgs }, classpath, mainClass)

    private fun commandFor(
        executable: String,
        argFile: File,
        jvmArgs: () -> List<String>,
        classpath: String,
        mainClass: String,
    ): String {
        if (File(executable).name.lowercase() !in JVM_LAUNCHERS) return command(executable, emptyList())
        // Inside quotes an argument file takes backslashes as escapes, so paths go with forward slashes
        val lines = jvmArgs() + "-cp" + "\"${classpath.replace('\\', '/')}\"" + mainClass
        argFile.parentFile?.mkdirs()
        argFile.writeText(lines.joinToString("\n", postfix = "\n"))
        return command(executable, listOf("@${argFile.path}"))
    }

    private val JVM_LAUNCHERS = setOf("java.exe", "javaw.exe", "java")

    /** The executable always quoted, arguments quoted when they hold spaces, and the url as "%1". */
    fun command(executable: String, arguments: List<String>): String =
        (listOf("\"$executable\"") + arguments.map(::quote)).joinToString(" ") + " \"%1\""

    private fun quote(part: String) = if (part.any { it == ' ' || it == '\t' }) "\"$part\"" else part
}
