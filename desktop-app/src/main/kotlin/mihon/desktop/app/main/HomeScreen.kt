package mihon.desktop.app.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Explore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

/** Root screen: the navigation rail and the current tab (spec 2a, section 2: Explorar and Ajustes in plan 1). */
object HomeScreen : Screen() {
    /** In rail order; the first one opens at start. Settings must stay last for [AppEvents.openSettings]. */
    val tabs: List<Tab> = listOf(ExploreTabPlaceholder)

    @Composable
    override fun Content() {
        TabNavigator(tabs.first()) { tabNavigator ->
            LaunchedEffect(Unit) { AppEvents.openSettings.collect { tabNavigator.current = tabs.last() } }
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    tabs.forEach { tab ->
                        val options = tab.options
                        NavigationRailItem(
                            selected = tabNavigator.current.key == tab.key,
                            onClick = { tabNavigator.current = tab },
                            icon = { options.icon?.let { Icon(it, contentDescription = options.title) } },
                            label = { Text(options.title) },
                        )
                    }
                }
                CurrentTab()
            }
        }
    }
}

private data object ExploreTabPlaceholder : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = stringResource(MR.strings.browse),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Explore),
        )

    @Composable
    override fun Content() {
        EmptyScreen(MR.strings.empty_screen)
    }
}
