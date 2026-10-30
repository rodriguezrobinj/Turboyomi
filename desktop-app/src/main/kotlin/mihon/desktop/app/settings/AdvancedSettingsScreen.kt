package mihon.desktop.app.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.PlatformContext
import coil3.SingletonImageLoader
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_CONTROLD
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_MULLVAD
import eu.kanade.tachiyomi.network.PREF_DOH_NJALLA
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.network.PREF_DOH_SHECAN
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.desktop.app.browser.EmbeddedBrowser
import mihon.desktop.core.cef.CefCookieSync
import mihon.desktop.core.cef.CefData
import mihon.desktop.core.cef.CefRuntime
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.awt.Desktop

/** Spec 2a §3, 2b §2 and 9a §2: Mihon's advanced settings that apply on Windows, and the embedded browser. */
object AdvancedSettingsScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val networkPreferences = remember { context.appGraph.networkPreferences }
        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = networkPreferences.verboseLogging,
                title = stringResource(MR.strings.pref_verbose_logging),
                subtitle = stringResource(MR.strings.pref_verbose_logging_summary),
                onValueChanged = {
                    context.toast(MR.strings.requires_app_restart)
                    true
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = networkPreferences.redactSensitiveHeaders,
                title = stringResource(MR.strings.pref_redact_sensitive_headers),
                subtitle = stringResource(MR.strings.pref_redact_sensitive_headers_summary),
                onValueChanged = {
                    context.toast(MR.strings.requires_app_restart)
                    true
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_debug_info),
                onClick = { navigator.push(DebugInfoScreen) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_dump_crash_logs),
                subtitle = stringResource(MR.strings.pref_dump_crash_logs_summary),
                onClick = {
                    scope.launch {
                        try {
                            val file = context.appGraph.crashLogUtil.dumpLogs()
                            ProcessBuilder("explorer.exe", "/select,$file").start()
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e)
                            context.toast(MR.strings.desktop_dump_logs_failed)
                        }
                    }
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.desktop_open_logs_folder),
                onClick = { Desktop.getDesktop().open(context.appGraph.appDirs.logs.toFile()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.desktop_clear_image_cache),
                onClick = {
                    val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                    scope.launch {
                        context.appGraph.chapterCache.clear() // spec 2a §3: Avanzado › borrar caché
                        context.toast(MR.strings.desktop_image_cache_cleared)
                    }
                },
            ),
            dataGroup(context, navigator),
            networkGroup(context),
            libraryGroup(context),
            embeddedBrowser(context),
        )
    }

    @Composable
    private fun dataGroup(context: Context, navigator: Navigator) = Preference.PreferenceGroup(
        title = stringResource(MR.strings.label_data),
        preferenceItems = listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_invalidate_download_cache),
                subtitle = stringResource(MR.strings.pref_invalidate_download_cache_summary),
                onClick = {
                    context.appGraph.downloadCache.invalidateCache()
                    context.toast(MR.strings.download_cache_invalidated)
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_clear_database),
                subtitle = stringResource(MR.strings.pref_clear_database_summary),
                onClick = { navigator.push(ClearDatabaseScreen()) },
            ),
        ),
    )

    @Composable
    private fun networkGroup(context: Context): Preference.PreferenceGroup {
        val networkPreferences = remember { context.appGraph.networkPreferences }
        val userAgentPref = networkPreferences.defaultUserAgent
        val userAgent by userAgentPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_network),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_cookies),
                    onClick = {
                        CefCookieSync.clear(url = null)
                        context.toast(MR.strings.cookies_cleared)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_webview_data),
                    onClick = {
                        val cleared = CefData(context.appGraph.appDirs.cefCache).clear(running = CefRuntime.isRunning)
                        context.toast(if (cleared) MR.strings.webview_data_deleted else MR.strings.requires_app_restart)
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = networkPreferences.dohProvider,
                    entries = mapOf(
                        -1 to stringResource(MR.strings.disabled),
                        PREF_DOH_CLOUDFLARE to "Cloudflare",
                        PREF_DOH_GOOGLE to "Google",
                        PREF_DOH_ADGUARD to "AdGuard",
                        PREF_DOH_QUAD9 to "Quad9",
                        PREF_DOH_ALIDNS to "AliDNS",
                        PREF_DOH_DNSPOD to "DNSPod",
                        PREF_DOH_360 to "360",
                        PREF_DOH_QUAD101 to "Quad 101",
                        PREF_DOH_MULLVAD to "Mullvad",
                        PREF_DOH_CONTROLD to "Control D",
                        PREF_DOH_NJALLA to "Njalla",
                        PREF_DOH_SHECAN to "Shecan",
                    ),
                    title = stringResource(MR.strings.pref_dns_over_https),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = userAgentPref,
                    title = stringResource(MR.strings.pref_user_agent_string),
                    onValueChanged = {
                        if (!isValidUserAgent(it)) {
                            context.toast(MR.strings.error_user_agent_string_invalid)
                            return@EditTextPreference false
                        }
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_user_agent_string),
                    visible = remember(userAgent) { userAgent != userAgentPref.defaultValue() },
                    onClick = {
                        userAgentPref.delete()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun libraryGroup(context: Context): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val libraryPreferences = remember { context.appGraph.libraryPreferences }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_library),
            preferenceItems = listOf(
                // desktop: Mihon's "Refresh library metadata", on the library update job (spec 3a §3 D)
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_refresh_library_covers),
                    onClick = {
                        val started = context.appGraph.libraryUpdateJob.startMetadata()
                        context.toast(if (started) MR.strings.updating_library else MR.strings.update_already_running)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_viewer_flags),
                    subtitle = stringResource(MR.strings.pref_reset_viewer_flags_summary),
                    onClick = {
                        scope.launch {
                            val success = context.appGraph.resetViewerFlags.await()
                            context.toast(
                                if (success) {
                                    MR.strings.pref_reset_viewer_flags_success
                                } else {
                                    MR.strings.pref_reset_viewer_flags_error
                                },
                            )
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.updateMangaTitles,
                    title = stringResource(MR.strings.pref_update_library_manga_titles),
                    subtitle = stringResource(MR.strings.pref_update_library_manga_titles_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.disallowNonAsciiFilenames,
                    title = stringResource(MR.strings.pref_disallow_non_ascii_filenames),
                    subtitle = stringResource(MR.strings.pref_disallow_non_ascii_filenames_details),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.enableChapterNameHash,
                    title = stringResource(MR.strings.pref_enable_chapter_name_hash),
                    subtitle = stringResource(MR.strings.pref_enable_chapter_name_hash_details),
                ),
            ),
        )
    }

    /** The embedded browser (spec 2b §2): its state, install or remove it, and its licenses. */
    @Composable
    private fun embeddedBrowser(context: Context): Preference.PreferenceGroup {
        val installer = context.appGraph.cefInstaller
        val state by CefHelper.cefApp.collectAsState()
        var changed by remember { mutableIntStateOf(0) }
        val installed = remember(state, changed) { installer.isInstalled() }
        val removalPending = remember(state, changed) { installer.isRemovalPending() }
        val running = state.getOrNull() != null
        val status = when {
            removalPending -> stringResource(MR.strings.desktop_cef_status_remove_pending)
            !installed -> stringResource(MR.strings.desktop_cef_status_not_installed)
            running -> stringResource(MR.strings.desktop_cef_status_installed)
            state.isSuccess -> stringResource(MR.strings.desktop_cef_status_starting)
            else -> stringResource(
                MR.strings.desktop_cef_status_failed,
                state.exceptionOrNull()?.cause?.message ?: state.exceptionOrNull()?.message.orEmpty(),
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.desktop_cef_title),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(title = status),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.desktop_cef_install),
                    visible = !installed && !removalPending,
                    onClick = EmbeddedBrowser::requestInstall,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.desktop_cef_remove),
                    visible = installed,
                    onClick = {
                        installer.uninstall(running = CefRuntime.isRunning)
                        changed++
                        context.toast(
                            if (CefRuntime.isRunning) {
                                MR.strings.desktop_cef_status_remove_pending
                            } else {
                                MR.strings.desktop_cef_removed
                            },
                        )
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.desktop_cef_licenses),
                    visible = installed && running,
                    onClick = { EmbeddedBrowser.open(context, "chrome://credits") },
                ),
            ),
        )
    }
}

/** OkHttp checks header values itself, as in Mihon; blank isn't a user agent either. */
internal fun isValidUserAgent(value: String): Boolean =
    value.isNotBlank() && runCatching { Headers.Builder().add("User-Agent", value) }.isSuccess
