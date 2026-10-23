package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import mihon.app.di.appGraph
import mihon.desktop.app.platform.FileDialogs
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.io.File
import java.net.URI

/** Mihon's data and storage settings with only the storage location; backups, cache and export come with subproject 5. */
object SettingsDataScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_data_storage

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val storagePreferences = remember { context.appGraph.storagePreferences }
        val storageDir by storagePreferences.baseStorageDirectory.collectAsState()
        val chooserTitle = stringResource(MR.strings.pref_storage_location)
        // desktop: always the path, also for the default folder, which works without picking one (spec 4a §3 C)
        val path = remember(storageDir) { runCatching { File(URI(storageDir)).path }.getOrNull() }

        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_storage_location),
                subtitle = path ?: stringResource(MR.strings.invalid_location, storageDir),
                // desktop: a Windows folder chooser instead of Android's document tree picker
                onClick = {
                    FileDialogs.chooseDirectory(chooserTitle)?.let {
                        storagePreferences.baseStorageDirectory.set(it.toURI().toString())
                    }
                },
            ),
            Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.pref_storage_location_info)),
        )
    }
}
