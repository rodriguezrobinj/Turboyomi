package eu.kanade.tachiyomi.extension

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.desktop.core.DesktopExtensionManager
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import java.util.concurrent.ConcurrentHashMap

/**
 * Mihon's ExtensionManager API over [DesktopExtensionManager], so its extension screens and view models run
 * unchanged: install progress as [InstallStep]s, update and obsolete flags derived from the store listings.
 */
@Inject
@SingleIn(AppScope::class)
class ExtensionManager(
    private val manager: DesktopExtensionManager,
    private val preferences: SourcePreferences,
    private val context: Context,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    // Every store's listing, since more than one store can list the same extension
    private val availableListings = MutableStateFlow(emptyList<Extension.Available>())

    private val installedWithStatus: StateFlow<List<Extension.Installed>> =
        combine(manager.installed, availableListings) { installed, available ->
            installed.map { withStatus(it, available) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val loadedExtensionsFlow: Flow<List<Extension.Loaded>> =
        installedWithStatus.map { it.filterIsInstance<Extension.Loaded>() }

    val notLoadedExtensionsFlow: Flow<List<Extension.NotLoaded>> =
        installedWithStatus.map { it.filterIsInstance<Extension.NotLoaded>() }

    // Stores sharing a signing key serve the same apks, so only the newest of their listings is shown
    val availableExtensionsFlow: StateFlow<List<Extension.Available>> = availableListings
        .map { extensions ->
            extensions.groupBy { it.pkgName to it.store.signingKey }.values.map { listings ->
                listings.maxWith(compareBy<Extension.Available> { it.versionCode }.thenBy { it.libVersion })
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val installJobs = ConcurrentHashMap<String, Job>()

    init {
        scope.launch {
            manager.reload()
            findAvailableExtensions()
        }
        scope.launch {
            installedWithStatus.collect { list -> preferences.extensionUpdatesCount.set(list.count { it.hasUpdate }) }
        }
    }

    suspend fun getLoadedExtensions(): List<Extension.Loaded> = loadedExtensionsFlow.first()

    suspend fun getExtensionPackage(sourceId: Long): String? =
        getLoadedExtensions().find { ext -> ext.sources.any { it.id == sourceId } }?.pkgName

    fun getExtensionPackageAsFlow(sourceId: Long): Flow<String?> =
        loadedExtensionsFlow.map { list -> list.find { ext -> ext.sources.any { it.id == sourceId } }?.pkgName }

    fun getSourceData(id: Long): StubSource? = availableListings.value
        .asSequence()
        .flatMap { it.sources }
        .find { it.id == id }
        ?.toStubSource()

    suspend fun findAvailableExtensions() {
        availableListings.value = try {
            manager.available()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            context.toast(MR.strings.extension_api_error)
            return
        }
    }

    fun installExtension(extension: Extension.Available): Flow<InstallStep> = flow {
        installJobs[extension.pkgName] = currentCoroutineContext().job
        try {
            emit(InstallStep.Pending)
            // Downloading and installing are one call on desktop; the list shows it as downloading
            emit(InstallStep.Downloading)
            manager.install(listingFor(extension, availableListings.value))
            emit(InstallStep.Installed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install ${extension.pkgName}" }
            context.toast(e.message)
            emit(InstallStep.Error)
        } finally {
            installJobs.remove(extension.pkgName)
        }
    }

    fun updateExtension(extension: Extension.Installed): Flow<InstallStep> {
        val update = extension.findUpdate(availableListings.value) ?: return emptyFlow()
        return installExtension(update)
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        installJobs.remove(extension.pkgName)?.cancel()
    }

    fun uninstallExtension(extension: Extension.Installed) {
        scope.launch(Dispatchers.IO) { manager.uninstall(extension.pkgName) }
    }

    fun trust(extension: Extension.NotLoaded) {
        scope.launch(Dispatchers.IO) { manager.trust(extension) }
    }

    companion object {
        /**
         * The store listing behind a row of the extensions list. GetExtensionsByType shows a multi-language
         * extension as one row per language, named "<pkg>-<sourceId>"; the installer needs the apk's own name.
         */
        fun listingFor(extension: Extension.Available, listings: List<Extension.Available>): Extension.Available =
            listings.find { it.apkUrl == extension.apkUrl && it.store == extension.store } ?: extension

        /** What the listings say about an installed extension; with no listing at all, nothing is derived. */
        fun withStatus(extension: Extension.Installed, available: List<Extension.Available>): Extension.Installed {
            if (available.isEmpty()) return extension
            val hasUpdate = extension.findUpdate(available) != null
            return when (extension) {
                is Extension.Loaded -> extension.copy(
                    hasUpdate = hasUpdate,
                    isObsolete = extension.findListing(available) == null,
                )
                is Extension.NotLoaded -> extension.copy(hasUpdate = hasUpdate)
            }
        }
    }
}
