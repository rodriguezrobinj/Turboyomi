package mihon.desktop.core

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mihon.desktop.extension.DesktopExtensionLoader
import mihon.desktop.extension.ExtensionInstaller
import mihon.desktop.extension.ExtensionTrust
import mihon.domain.extension.repository.ExtensionStoreRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.deleteIfExists

/** Desktop counterpart of Mihon's ExtensionManager: stores, downloads, installs and keeps the loaded list. */
@Inject
@SingleIn(AppScope::class)
class DesktopExtensionManager(
    private val appDirs: AppDirs,
    private val network: NetworkHelper,
    private val storeRepository: ExtensionStoreRepository,
    private val trust: ExtensionTrust,
) {
    private val installer = ExtensionInstaller(appDirs.extensions)
    private val loader = DesktopExtensionLoader(trust)

    // The installer prunes files while listing, so installs, reloads and uninstalls must not overlap
    private val mutex = Mutex()

    private val _installed = MutableStateFlow<List<Extension.Installed>>(emptyList())
    val installed: StateFlow<List<Extension.Installed>> = _installed.asStateFlow()

    suspend fun addStore(indexUrl: String): Result<Unit> = storeRepository.insert(indexUrl)

    suspend fun available(): List<Extension.Available> = storeRepository.fetchExtensions()

    suspend fun reload() = mutex.withLock { reloadLocked() }

    private suspend fun reloadLocked() = withContext(Dispatchers.IO) {
        val stores = storeRepository.getAll()
        _installed.value = installer.installed().map { loader.load(it, stores) }
    }

    suspend fun install(ext: Extension.Available): Extension.Installed = mutex.withLock { installLocked(ext) }

    private suspend fun installLocked(ext: Extension.Available): Extension.Installed = withContext(Dispatchers.IO) {
        val download = Files.createTempFile(appDirs.cache, "download-", ".apk")
        try {
            network.client.newCall(GET(ext.apkUrl)).awaitSuccess().use { response ->
                response.body.byteStream().use { Files.copy(it, download, REPLACE_EXISTING) }
            }
            val installedExt = installer.install(download, expectedPkgName = ext.pkgName)
            val result = loader.load(installedExt, storeRepository.getAll())
            _installed.update { list -> list.filterNot { it.pkgName == result.pkgName } + result }
            result
        } finally {
            download.deleteIfExists()
        }
    }

    suspend fun trust(ext: Extension.NotLoaded) {
        val reason = ext.reason as? Extension.NotLoaded.Reason.Untrusted ?: return
        trust.trust(ext.pkgName, ext.versionCode, reason.signatureHash)
        reload()
    }

    /** The apk of the installed version of [pkgName], for reading its icon. */
    fun installedApk(pkgName: String): Path? = installed.value
        .find { it.pkgName == pkgName }
        ?.let { installer.apkFor(it.pkgName, it.versionCode) }

    suspend fun uninstall(pkgName: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            installer.uninstall(pkgName)
            _installed.update { list -> list.filterNot { it.pkgName == pkgName } }
        }
    }
}
