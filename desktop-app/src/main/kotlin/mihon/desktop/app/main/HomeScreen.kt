package mihon.desktop.app.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.app.di.appGraph
import mihon.desktop.app.settings.SettingsTab
import tachiyomi.presentation.core.util.collectAsState

/**
 * Root screen: the navigation rail and the current tab (spec 3a §2: Mihon's order, the library first).
 * The tabs' TabNavigator lives in AppContent, above the screens' navigator, so their state survives while
 * another screen covers this one.
 */
object HomeScreen : Screen() {
    /** In rail order; the first one opens at start. Settings must stay last for [AppEvents.openSettings]. */
    val tabs: List<Tab> = listOf(LibraryTab, UpdatesTab, HistoryTab, BrowseTab, SettingsTab)

    private val librarySearch = Channel<String>()

    /** Mihon's HomeScreen.search: opens the library and searches it (from a manga's tag or title). */
    suspend fun search(query: String) = librarySearch.send(query)

    @Composable
    override fun Content() {
        val tabNavigator = LocalTabNavigator.current
        val context = LocalContext.current
        val libraryPreferences = remember { context.appGraph.libraryPreferences }
        val newUpdates by libraryPreferences.newUpdatesCount.collectAsState()
        val showNewUpdates by libraryPreferences.newShowUpdatesCount.collectAsState()
        LaunchedEffect(Unit) {
            librarySearch.receiveAsFlow().collect {
                tabNavigator.current = LibraryTab
                LibraryTab.search(it)
            }
        }
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                tabs.forEach { tab ->
                    val options = tab.options
                    NavigationRailItem(
                        selected = tabNavigator.current.key == tab.key,
                        onClick = { tabNavigator.current = tab },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == UpdatesTab && showNewUpdates &&
                                        newUpdates > 0
                                    ) {
                                        Badge { Text("$newUpdates") }
                                    }
                                },
                            ) { options.icon?.let { Icon(it, contentDescription = options.title) } }
                        },
                        label = { Text(options.title) },
                    )
                }
            }
            CurrentTab()
        }
    }
}
