package mihon.desktop.core

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/** Where the app keeps its files: roaming data (synced with the Windows profile) and local machine data. */
data class AppDirs(val data: Path, val local: Path) {
    val db: Path get() = data.resolve("db")
    val backups: Path get() = data.resolve("backups")
    val settings: Path get() = data.resolve("settings")
    val lockFile: Path get() = data.resolve(".lock")
    val cache: Path get() = local.resolve("cache")
    val extensions: Path get() = local.resolve("extensions")
    val logs: Path get() = local.resolve("logs")

    fun createAll() {
        listOf(data, db, backups, settings, local, cache, extensions, logs).forEach { it.createDirectories() }
    }

    companion object {
        /** Provisional product name; the only place it is spelled out. */
        const val APP_NAME = "MangaReader"
        const val HOME_ENV = "MANGAREADER_HOME"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppDirs {
            env[HOME_ENV]?.let { home -> return AppDirs(Path(home, "data"), Path(home, "local")) }
            val roaming = env["APPDATA"] ?: error("APPDATA is not set")
            val local = env["LOCALAPPDATA"] ?: error("LOCALAPPDATA is not set")
            return AppDirs(Path(roaming, APP_NAME), Path(local, APP_NAME))
        }
    }
}
