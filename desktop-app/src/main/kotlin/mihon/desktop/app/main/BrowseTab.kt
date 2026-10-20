package mihon.desktop.app.main

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.source.sourcesTab
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Explore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Mihon's BrowseTab without migration (subproject 3) and with a static rail icon. */
data object BrowseTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = stringResource(MR.strings.browse),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Explore),
        )

    @Composable
    override fun Content() {
        // Hoisted for the extensions tab's search bar
        val extensionsViewModel = metroViewModel<ExtensionsViewModel>()
        val extensionsSearchQuery by extensionsViewModel.searchQuery.collectAsStateWithLifecycle()

        val tabs = listOf(
            sourcesTab(),
            extensionsTab(extensionsViewModel),
        )
        val state = rememberPagerState { tabs.size }

        LaunchedEffect(Unit) { AppEvents.refresh.collect { extensionsViewModel.findAvailableExtensions() } }

        TabbedScreen(
            titleRes = MR.strings.browse,
            tabs = tabs,
            state = state,
            searchQuery = extensionsSearchQuery,
            onChangeSearchQuery = extensionsViewModel::search,
        )
    }
}
