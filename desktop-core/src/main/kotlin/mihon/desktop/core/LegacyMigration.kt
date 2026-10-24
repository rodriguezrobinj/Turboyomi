package mihon.desktop.core

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import javax.swing.filechooser.FileSystemView
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

/**
 * Brings the development build's data ("MangaReader") over to Turboyomi on the first start (spec 6a §3 A): its two
 * app folders, its settings file, and the storage folder while it is still the default one. All or nothing: a move
 * that fails puts back what had moved, and the app starts fresh with a warning. It runs before logging is set up, so
 * the caller logs a failure.
 */
object LegacyMigration {
    sealed interface Result {
        data object NotNeeded : Result
        data object Migrated : Result
        data class Failed(val error: Exception) : Result
    }

    private const val STORAGE_KEY = "__APP_STATE_storage_dir"
    private const val OLD_SETTINGS = "app.mangareader_preferences.xml"

    fun run(
        env: Map<String, String> = System.getenv(),
        documents: () -> Path = { FileSystemView.getFileSystemView().defaultDirectory.toPath() },
    ): Result {
        if (env[AppDirs.HOME_ENV] != null) return Result.NotNeeded
        val roaming = env["APPDATA"]?.let(::Path) ?: return Result.NotNeeded
        val local = env["LOCALAPPDATA"]?.let(::Path) ?: return Result.NotNeeded
        val oldRoaming = roaming.resolve(AppDirs.LEGACY_APP_NAME)
        val newRoaming = roaming.resolve(AppDirs.APP_NAME)
        if (!oldRoaming.exists() || newRoaming.exists()) return Result.NotNeeded

        val done = mutableListOf<Pair<Path, Path>>()
        return try {
            val oldSettings = oldRoaming.resolve("settings").resolve(OLD_SETTINGS)
            val properties = Properties().apply {
                if (oldSettings.exists()) oldSettings.inputStream().use { loadFromXML(it) }
            }
            val oldStorage = documents().resolve(AppDirs.LEGACY_APP_NAME)
            val newStorage = documents().resolve(AppDirs.APP_NAME)
            // Compared as paths: File.toURI and Path.toUri spell the same folder differently
            val stored = properties.getProperty(STORAGE_KEY)?.let { runCatching { Path.of(URI(it)) }.getOrNull() }
            val storageIsDefault = stored == null || stored == oldStorage

            val moves = buildList {
                add(oldRoaming to newRoaming)
                local.resolve(AppDirs.LEGACY_APP_NAME).takeIf { it.exists() }
                    ?.let { add(it to local.resolve(AppDirs.APP_NAME)) }
                if (storageIsDefault && oldStorage.exists() && !newStorage.exists()) add(oldStorage to newStorage)
            }
            // A folder move is a rename: it fails on a file in use instead of leaving a half copy
            moves.forEach { (from, to) ->
                Files.move(from, to)
                done += from to to
            }
            val moved = newRoaming.resolve("settings").resolve(OLD_SETTINGS)
            if (moved.exists()) {
                if (stored == oldStorage) properties.setProperty(STORAGE_KEY, newStorage.toUri().toString())
                moved.resolveSibling("${DesktopBuildInfo.APPLICATION_ID}_preferences.xml").outputStream()
                    .use { properties.storeToXML(it, null) }
                Files.delete(moved)
            }
            Result.Migrated
        } catch (e: Exception) {
            // Newest first, so nothing ends up split between the two names
            done.asReversed().forEach { (from, to) -> runCatching { Files.move(to, from) } }
            Result.Failed(e)
        }
    }
}
