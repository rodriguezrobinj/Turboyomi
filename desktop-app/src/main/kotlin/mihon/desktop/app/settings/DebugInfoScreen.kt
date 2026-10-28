package mihon.desktop.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.util.system.copyToClipboard
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Mihon's debug info, cut to the app and the system (spec 9a §2): the same text "Dump crash logs" writes. */
object DebugInfoScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_debug_info

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val info = remember { context.appGraph.crashLogUtil.debugInfo() }
        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.action_copy_to_clipboard),
                onClick = { context.copyToClipboard("debug info", info) },
            ),
        ) + info.lines().map { line ->
            Preference.PreferenceItem.TextPreference(
                title = line.substringBefore(": "),
                subtitle = line.substringAfter(": "),
            )
        }
    }
}
