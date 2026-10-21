package mihon.desktop.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import coil3.PlatformContext
import coil3.SingletonImageLoader
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.awt.Desktop

/** Spec 2a, section 3: clear the image cache and open the logs folder. */
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
        )
    }
}
