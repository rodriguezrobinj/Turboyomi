package mihon.desktop.app.update

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopBuildInfo
import mihon.desktop.core.jobs.PeriodicJob
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlin.time.Duration.Companion.hours

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    data class Failed(val release: AppRelease, val message: String) : UpdateState
    data object Installing : UpdateState
}

/**
 * The app's own updates (spec 6b §2, §3): a daily or manual check, then download, SHA-256 against the release's
 * SHA256SUMS, and msiexec once the app is gone. The window shows [state]; Main exits on [UpdateState.Installing].
 */
@Inject
@SingleIn(AppScope::class)
class AppUpdater(
    private val context: Context,
    private val releases: AppReleases,
    private val network: NetworkHelper,
    private val appDirs: AppDirs,
    private val preferences: BasePreferences,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var download: Job? = null

    // Seams for tests, like BaseTracker.testInterceptor
    var apiBase: String = AppReleases.API_BASE
    var install: UpdateInstaller = UpdateInstaller()
    var openPage: (String) -> Unit = { runCatching { Desktop.getDesktop().browse(URI(it)) } }

    /** [manual]: Settings › About's button, which always reports back; the daily check stays quiet on errors. */
    suspend fun check(manual: Boolean) {
        val release = try {
            releases.latest(apiBase)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Couldn't check for app updates" }
            if (manual) AppMessages.show(e.message ?: e.toString())
            return
        }
        if (release != null && AppVersion.isNewer(release.version, DesktopBuildInfo.VERSION_NAME)) {
            _state.value = UpdateState.Available(release)
            return
        }
        if (release == null) logcat(LogPriority.WARN) { "The latest release has no MSI or SHA256SUMS" }
        if (manual) AppMessages.show(context.stringResource(MR.strings.update_check_no_new_updates))
    }

    /** Downloads, verifies and installs [release]; outside the installed app (the ZIP), opens its page instead. */
    fun update(release: AppRelease) {
        if (!install.isInstalled()) {
            openPage(release.page)
            _state.value = UpdateState.Idle
            return
        }
        synchronized(this) {
            if (download?.isActive == true) return
            _state.value = UpdateState.Downloading(release, null)
            download = scope.launch { downloadAndInstall(release) }
        }
    }

    fun cancel() {
        download?.cancel()
    }

    fun dismiss() {
        download?.cancel()
        _state.value = UpdateState.Idle
    }

    fun periodicJob(): PeriodicJob = PeriodicJob(
        name = "AppUpdateCheck",
        interval = { 24.hours.takeIf { preferences.autoCheckAppUpdates.get() && !install.isDevelopment() } },
        lastRun = preferences.lastAppUpdateCheck,
        run = { check(manual = false) },
    )

    private suspend fun downloadAndInstall(release: AppRelease) {
        val dir = appDirs.cache.resolve("updates").toFile().apply { mkdirs() }
        val part = File(dir, "${release.msiName}.part")
        val msi = File(dir, release.msiName)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            network.client.newCall(GET(release.msiUrl)).awaitSuccess().use { response ->
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            _state.value = UpdateState.Downloading(release, total?.let { done.toFloat() / it })
                        }
                    }
                }
            }
            val sums = network.client.newCall(GET(release.sumsUrl)).awaitSuccess().use { it.body.string() }
            val expected = expectedHash(sums, release.msiName)
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expected == null || !expected.equals(actual, ignoreCase = true)) {
                part.delete()
                _state.value = UpdateState.Failed(release, context.stringResource(MR.strings.desktop_update_corrupt))
                return
            }
            msi.delete()
            check(part.renameTo(msi)) { "Couldn't move the download into place" }
            install.launch(install.command(msi, ProcessHandle.current().pid()))
            _state.value = UpdateState.Installing
        } catch (e: CancellationException) {
            part.delete()
            // Cancel goes back to the offer; dismiss has already closed it
            if (_state.value is UpdateState.Downloading) _state.value = UpdateState.Available(release)
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "App update failed" }
            part.delete()
            _state.value = UpdateState.Failed(release, e.message ?: e.toString())
        }
    }

    companion object {
        /** "<hash>  <file>" or "<hash> *<file>", as sha256sum writes them in text and binary mode. */
        internal fun expectedHash(sums: String, file: String): String? = sums.lineSequence()
            .map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].removePrefix("*") == file }
            ?.first()
    }
}
