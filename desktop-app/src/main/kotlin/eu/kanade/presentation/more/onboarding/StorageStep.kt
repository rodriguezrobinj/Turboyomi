package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import mihon.app.di.appGraph
import mihon.desktop.app.platform.FileDialogs
import mihon.desktop.core.AppDirs
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.io.File
import java.net.URI

internal class StorageStep : OnboardingStep {

    // desktop: there is a default folder (AppDirs) that works without picking one, so the step is done from the start
    override val isComplete: Boolean = true

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val handler = LocalUriHandler.current

        val storagePref = remember { context.appGraph.storagePreferences.baseStorageDirectory }
        val storageDir by storagePref.collectAsState()
        val chooserTitle = stringResource(MR.strings.pref_storage_location)

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(
                stringResource(
                    MR.strings.onboarding_storage_info,
                    AppDirs.APP_NAME,
                    // desktop: the folder's path, as Settings › Data shows it
                    remember(storageDir) { runCatching { File(URI(storageDir)).path }.getOrDefault(storageDir) },
                ),
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                // desktop: a Windows folder chooser instead of Android's document tree picker
                onClick = {
                    FileDialogs.chooseDirectory(chooserTitle)?.let { storagePref.set(it.toURI().toString()) }
                },
            ) {
                Text(stringResource(MR.strings.onboarding_storage_action_select))
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(stringResource(MR.strings.onboarding_storage_help_info, AppDirs.APP_NAME))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { handler.openUri(SettingsDataScreen.HELP_URL) },
            ) {
                Text(stringResource(MR.strings.onboarding_storage_help_action))
            }
        }
    }
}
