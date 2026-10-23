package mihon.desktop.core

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.storage.FolderProvider
import java.io.File

/** Mihon's default storage folder on Windows: [AppDirs.storage], as a file: uri StorageManager can parse. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopFolderProvider(private val appDirs: AppDirs) : FolderProvider {
    override fun directory(): File = appDirs.storage.toFile()

    override fun path(): String = directory().toURI().toString()
}
