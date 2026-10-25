package mihon.desktop.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.desktop.app.update.AppReleases
import mihon.desktop.core.DesktopBuildInfo
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Mihon's About, cut to what the desktop has (spec 6b §2): version, update check, the repo and its licenses. */
object AboutScreen : SearchableSettings {
    private const val REPO_URL = "https://github.com/${AppReleases.REPO}"

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_about

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val graph = context.appGraph
        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.version),
                subtitle = "${DesktopBuildInfo.VERSION_NAME} (${DesktopBuildInfo.COMMIT_SHA})",
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.check_for_updates),
                onClick = { scope.launch { graph.appUpdater.check(manual = true) } },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = graph.basePreferences.autoCheckAppUpdates,
                title = stringResource(MR.strings.desktop_update_check_auto),
            ),
            Preference.PreferenceItem.TextPreference(
                title = "GitHub",
                subtitle = REPO_URL,
                onClick = { context.openInBrowser(REPO_URL) },
            ),
            // desktop: no aboutlibraries here; NOTICE lists the bundled code and its licenses
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.licenses),
                onClick = { context.openInBrowser("$REPO_URL/blob/master/NOTICE") },
            ),
        )
    }
}
