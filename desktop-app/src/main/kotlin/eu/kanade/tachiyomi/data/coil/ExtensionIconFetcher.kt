package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.extension.model.Extension
import mihon.desktop.core.DesktopExtensionManager
import mihon.desktop.extension.ApkInspector
import okio.Buffer
import okio.FileSystem

/** The icon of an installed extension. [density] is ignored on desktop: the densest bitmap is always used. */
data class InstalledExtensionIcon(
    val pkgName: String,
    val versionCode: Long,
    val density: Int,
)

/** The icon of the installed extension that provides the source with [sourceId]. */
data class InstalledSourceIcon(val sourceId: Long)

// desktop: reads the launcher icon from the installed apk instead of Android's PackageManager
class ExtensionIconFetcher(
    private val getPkgName: suspend () -> String?,
    private val manager: DesktopExtensionManager,
) :
    Fetcher {
    override suspend fun fetch(): FetchResult {
        val pkgName = getPkgName() ?: error("No installed extension")
        val apk = manager.installedApk(pkgName) ?: error("Extension $pkgName isn't installed")
        val bytes = ApkInspector.icon(apk) ?: error("Extension $pkgName has no bitmap icon")
        return SourceFetchResult(
            source = ImageSource(source = Buffer().write(bytes), fileSystem = FileSystem.SYSTEM),
            mimeType = "image/png",
            dataSource = DataSource.DISK,
        )
    }

    class ExtensionFactory(private val manager: DesktopExtensionManager) : Fetcher.Factory<InstalledExtensionIcon> {
        override fun create(data: InstalledExtensionIcon, options: Options, imageLoader: ImageLoader): Fetcher =
            ExtensionIconFetcher({ data.pkgName }, manager)
    }

    class SourceFactory(private val manager: DesktopExtensionManager) : Fetcher.Factory<InstalledSourceIcon> {
        override fun create(data: InstalledSourceIcon, options: Options, imageLoader: ImageLoader): Fetcher =
            ExtensionIconFetcher(
                getPkgName = {
                    manager.installed.value
                        .filterIsInstance<Extension.Loaded>()
                        .find { ext -> ext.sources.any { it.id == data.sourceId } }
                        ?.pkgName
                },
                manager = manager,
            )
    }
}

class InstalledExtensionIconKeyer : Keyer<InstalledExtensionIcon> {
    override fun key(data: InstalledExtensionIcon, options: Options): String =
        "extension;${data.pkgName};${data.versionCode}"
}

class InstalledSourceIconKeyer : Keyer<InstalledSourceIcon> {
    override fun key(data: InstalledSourceIcon, options: Options): String = "source;${data.sourceId}"
}
