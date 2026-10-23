package mihon.desktop.core

import java.net.URI
import java.nio.file.Path
import javax.swing.filechooser.FileSystemView
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/**
 * Where the app keeps its files: roaming data (synced with the Windows profile), local machine data, and the storage
 * folder the user sees (spec 4a §2): Mihon's local/, downloads/ and autobackup/, in Documents by default.
 */
data class AppDirs(val data: Path, val local: Path, val storage: Path = data.resolveSibling("storage")) {
    val db: Path get() = data.resolve("db")
    val backups: Path get() = data.resolve("backups")
    val settings: Path get() = data.resolve("settings")
    val lockFile: Path get() = data.resolve(".lock")
    val cache: Path get() = local.resolve("cache")
    val extensions: Path get() = local.resolve("extensions")
    val logs: Path get() = local.resolve("logs")

    /** The embedded browser (spec 2b §2): its files, its cache, and the app's cookies, which only this machine reads. */
    val cef: Path get() = local.resolve("cef")
    val cefCache: Path get() = cache.resolve("cef")
    val cookies: Path get() = local.resolve("cookies.bin")

    fun createAll() {
        listOf(data, db, backups, settings, local, cache, extensions, logs).forEach { it.createDirectories() }
    }

    /**
     * Creates the default storage folder only while [chosen] (the storage_dir preference) is it: once the user moves the
     * storage elsewhere, Documents isn't given an empty MangaReader folder on every start.
     */
    fun createStorageIfChosen(chosen: String) {
        if (runCatching { Path.of(URI(chosen)) }.getOrNull() == storage) storage.createDirectories()
    }

    companion object {
        /** Provisional product name; the only place it is spelled out. */
        const val APP_NAME = "MangaReader"
        const val HOME_ENV = "MANGAREADER_HOME"

        /** [documents] is the user's Documents folder, which OneDrive may have moved (FileSystemView follows it). */
        fun fromEnvironment(
            env: Map<String, String> = System.getenv(),
            documents: () -> Path = { FileSystemView.getFileSystemView().defaultDirectory.toPath() },
        ): AppDirs {
            env[HOME_ENV]?.let { home -> return AppDirs(Path(home, "data"), Path(home, "local")) }
            val roaming = env["APPDATA"] ?: error("APPDATA is not set")
            val local = env["LOCALAPPDATA"] ?: error("LOCALAPPDATA is not set")
            return AppDirs(Path(roaming, APP_NAME), Path(local, APP_NAME), documents().resolve(APP_NAME))
        }
    }
}
