package mihon.desktop.app.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.PlatformContext
import coil3.SingletonImageLoader
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import mihon.desktop.app.browser.EmbeddedBrowser
import mihon.desktop.core.cef.CefCookieSync
import mihon.desktop.core.cef.CefRuntime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.awt.Desktop

/** Spec 2a §3 and 2b §2: the image cache, the logs folder and the embedded browser. */
object AdvancedSettingsScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.desktop_clear_image_cache),
                onClick = {
                    val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                    context.appGraph.chapterCache.clear() // spec 2a §3: Avanzado › borrar caché
                    context.toast(MR.strings.desktop_image_cache_cleared)
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.desktop_open_logs_folder),
                onClick = { Desktop.getDesktop().open(context.appGraph.appDirs.logs.toFile()) },
            ),
            embeddedBrowser(context),
        )
    }

    /** The embedded browser (spec 2b §2): its state, install or remove it, its licenses, and the app's cookies. */
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
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_cookies),
                    onClick = {
                        CefCookieSync.clear(url = null)
                        context.toast(MR.strings.cookies_cleared)
                    },
                ),
            ),
        )
    }
}
