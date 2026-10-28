package mihon.desktop.app.settings

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen
import eu.kanade.presentation.more.settings.screen.SettingsLibraryScreen
import eu.kanade.presentation.more.settings.screen.SettingsSearchScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Code
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Explore
import mihon.icons.materialsymbols.rounded.Image
import mihon.icons.materialsymbols.rounded.Info
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.QueryStats
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.rounded.Settings
import mihon.icons.materialsymbols.rounded.Storage
import mihon.icons.materialsymbols.rounded.Sync
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
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

    /** The settings categories, in the list's order; the settings search indexes these too (spec 9b §2). */
    val screens: List<Triple<StringResource, ImageVector, Screen>> = listOf(
        // desktop: Mihon's More tab is this list, so its Statistics entry comes first (spec 9c §2)
        Triple(MR.strings.label_stats, MaterialSymbols.Rounded.QueryStats, StatsScreen()),
        Triple(
            MR.strings.pref_category_library,
            MaterialSymbols.Rounded.CollectionsBookmark,
            SettingsLibraryScreen,
        ),
        Triple(MR.strings.pref_category_appearance, MaterialSymbols.Rounded.Palette, SettingsAppearanceScreen),
        Triple(MR.strings.browse, MaterialSymbols.Rounded.Explore, SettingsBrowseScreen),
        Triple(MR.strings.pref_category_reader, MaterialSymbols.Rounded.Image, SettingsReaderScreen),
        Triple(MR.strings.pref_category_downloads, MaterialSymbols.Rounded.Download, SettingsDownloadScreen),
        Triple(MR.strings.pref_category_tracking, MaterialSymbols.Rounded.Sync, SettingsTrackingScreen),
        Triple(MR.strings.label_data_storage, MaterialSymbols.Rounded.Storage, SettingsDataScreen),
        Triple(MR.strings.pref_category_advanced, MaterialSymbols.Rounded.Code, AdvancedSettingsScreen),
        Triple(MR.strings.pref_category_about, MaterialSymbols.Rounded.Info, AboutScreen),
    )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Scaffold(topBar = {
            AppBar(
                title = stringResource(MR.strings.label_settings),
                actions = {
                    AppBarActions(
                        listOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_search_settings),
                                icon = MaterialSymbols.Rounded.Search,
                                onClick = { navigator.push(SettingsSearchScreen()) },
                            ),
                        ),
                    )
                },
                scrollBehavior = it,
            )
        }) { padding ->
            FastScrollLazyColumn(contentPadding = padding) {
                items(screens) { (title, icon, screen) ->
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
