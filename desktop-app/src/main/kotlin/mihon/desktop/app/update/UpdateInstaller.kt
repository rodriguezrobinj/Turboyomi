package mihon.desktop.app.update

import java.io.File
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.Path

/**
 * Where the app runs from and how an MSI replaces it (spec 6b §3 B, C). msiexec runs once this process is gone: a
 * hidden PowerShell waits for it, so the installer never meets the app's files in use.
 */
open class UpdateInstaller(
    private val exe: () -> Path? = { ProcessHandle.current().info().command().orElse(null)?.let(::Path) },
    private val localAppData: () -> Path? = { System.getenv("LOCALAPPDATA")?.let(::Path) },
) {
    open fun isInstalled(): Boolean {
        val installed = localAppData()?.resolve("Programs")?.resolve("Turboyomi")?.resolve("Turboyomi.exe")
            ?: return false
        // Windows paths compare without case
        return exe()?.toString().equals(installed.toString(), ignoreCase = true)
    }

    /** Running from Gradle or an IDE: a java process instead of Turboyomi.exe. */
    open fun isDevelopment(): Boolean = !exe()?.fileName?.toString().equals("Turboyomi.exe", ignoreCase = true)

    fun command(msi: File, pid: Long): List<String> {
        // A PowerShell string in single quotes: only the quote itself needs doubling
        val path = msi.path.replace("'", "''")
        val script = "Wait-Process -Id $pid -ErrorAction SilentlyContinue; " +
            "Start-Process -FilePath 'msiexec.exe' -ArgumentList '/i', '\"$path\"', '/passive', '/norestart'"
        // Encoded, so nothing in the path goes through a command line parser
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return listOf("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
    }

    open fun launch(command: List<String>) {
        ProcessBuilder(command).start()
    }
}
