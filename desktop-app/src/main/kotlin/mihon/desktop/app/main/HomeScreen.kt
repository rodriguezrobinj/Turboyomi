package mihon.desktop.app.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab

/** Root screen: the navigation rail and the current tab (spec 2a, section 2: Explorar and Ajustes in plan 1). */
object HomeScreen : Screen() {
    /** In rail order; the first one opens at start. Settings must stay last for [AppEvents.openSettings]. */
    val tabs: List<Tab> = listOf(BrowseTab)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        TabNavigator(tabs.first()) { tabNavigator ->
            LaunchedEffect(Unit) { AppEvents.openSettings.collect { tabNavigator.current = tabs.last() } }
            // Like Mihon's HomeScreen: tabs push screens onto the root navigator, not the tab navigator
            CompositionLocalProvider(LocalNavigator provides navigator) {
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
}
