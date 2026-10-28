package eu.kanade.tachiyomi.util

import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopBuildInfo
import mihon.desktop.core.cef.CefInstaller
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.time.Clock

/**
 * Mihon's crash log dump. Desktop: the app log is already a file in the logs folder, so the dump is the debug info
 * and the problem extensions next to it, instead of a logcat capture to share.
 */
@Inject
class CrashLogUtil(
    private val appDirs: AppDirs,
    private val extensionManager: ExtensionManager,
    private val preferences: BasePreferences,
    private val cefInstaller: CefInstaller,
) {

    /** Writes `debug_info.txt` in the logs folder and returns it. */
    suspend fun dumpLogs(): File = withContext(Dispatchers.IO) {
        val file = appDirs.logs.createDirectories().resolve("debug_info.txt").toFile()
        file.writeText(listOfNotNull(debugInfo(), extensionsInfo()).joinToString("\n\n", postfix = "\n"))
        file
    }

    /** One "Key: value" per line, in English: it's pasted into bug reports. */
    fun debugInfo(): String {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        return """
            App ID: ${DesktopBuildInfo.APPLICATION_ID}
            App version: ${DesktopBuildInfo.VERSION_NAME} (${DesktopBuildInfo.COMMIT_SHA}, ${DesktopBuildInfo.VERSION_CODE})
            Installation ID: ${preferences.installationId.get()}
            Skipped app update: ${preferences.skippedAppUpdate.get().ifEmpty { "None" }}
            Data folder: ${appDirs.data}
            OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})
            Java: ${System.getProperty("java.vendor")} ${System.getProperty("java.version")}
            Embedded browser: ${cefInstaller.installedVersion() ?: "Not installed"}
            Current time: ${now.toLocalDateTime(tz)}${tz.offsetAt(now)}
        """.trimIndent()
    }

    suspend fun extensionsInfo(): String? {
        val availableExtensions = extensionManager.availableExtensionsFlow.value.associateBy { it.pkgName }

        val outdatedInfoList = extensionManager.getLoadedExtensions()
            .sortedBy { it.name }
            .mapNotNull {
                val availableExtension = availableExtensions[it.pkgName]
                val hasUpdate = (availableExtension?.versionCode ?: 0) > it.versionCode

                if (!hasUpdate && !it.isObsolete) return@mapNotNull null

                """
                    - ${it.name}
                      Installed: ${it.versionName} / Available: ${availableExtension?.versionName ?: "?"}
                      Orphaned: ${it.isObsolete}
                """.trimIndent()
            }

        val notLoadedInfoList = extensionManager.notLoadedExtensionsFlow.first()
            .sortedBy { it.name }
            .map { extension ->
                buildString {
                    appendLine("- ${extension.name}")
                    appendLine("  Installed: ${extension.versionName} (lib ${extension.libVersion ?: "?"})")
                    append("  Not loaded: ${extension.reason.description}")

                    val reason = extension.reason
                    if (reason is Extension.NotLoaded.Reason.Failed) {
                        appendLine()
                        append(reason.stackTrace.trimEnd().prependIndent("  "))
                    }
                }
            }

        val extensionInfoList = outdatedInfoList + notLoadedInfoList

        return if (extensionInfoList.isNotEmpty()) {
            (listOf("Problematic extensions:") + extensionInfoList)
                .joinToString("\n")
        } else {
            null
        }
    }
}

private val Extension.NotLoaded.Reason.description: String
    get() = when (this) {
        is Extension.NotLoaded.Reason.Untrusted -> "Untrusted"
        Extension.NotLoaded.Reason.Filtered -> "Filtered by content warning"
        Extension.NotLoaded.Reason.Unsigned -> "Unsigned"
        Extension.NotLoaded.Reason.UnsupportedLibVersion -> "Unsupported lib version"
        Extension.NotLoaded.Reason.Malformed -> "Malformed"
        is Extension.NotLoaded.Reason.Failed -> "Failed ($message)"
    }
