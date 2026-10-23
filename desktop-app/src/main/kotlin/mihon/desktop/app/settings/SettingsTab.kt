package mihon.desktop.app.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen
import eu.kanade.presentation.more.settings.screen.SettingsLibraryScreen
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Tab
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Code
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Explore
import mihon.icons.materialsymbols.rounded.Image
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.Settings
import mihon.icons.materialsymbols.rounded.Storage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

/** The settings rail entry: the desktop subset of Mihon's settings categories. */
data object SettingsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 9u,
            title = stringResource(MR.strings.label_settings),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Settings),
        )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val entries = listOf(
            Triple(
                MR.strings.pref_category_library,
                MaterialSymbols.Rounded.CollectionsBookmark,
                SettingsLibraryScreen,
            ),
            Triple(MR.strings.pref_category_appearance, MaterialSymbols.Rounded.Palette, SettingsAppearanceScreen),
            Triple(MR.strings.browse, MaterialSymbols.Rounded.Explore, SettingsBrowseScreen),
            Triple(MR.strings.pref_category_reader, MaterialSymbols.Rounded.Image, SettingsReaderScreen),
            Triple(MR.strings.pref_category_downloads, MaterialSymbols.Rounded.Download, SettingsDownloadScreen),
            Triple(MR.strings.label_data_storage, MaterialSymbols.Rounded.Storage, SettingsDataScreen),
            Triple(MR.strings.pref_category_advanced, MaterialSymbols.Rounded.Code, AdvancedSettingsScreen),
        )
        Scaffold(topBar = {
            AppBar(title = stringResource(MR.strings.label_settings), scrollBehavior = it)
        }) { padding ->
            LazyColumn(contentPadding = padding) {
                items(entries) { (title, icon, screen) ->
                    TextPreferenceWidget(
                        title = stringResource(title),
                        icon = icon,
                        onPreferenceClick = { navigator.push(screen) },
                    )
                }
            }
        }
    }
}
