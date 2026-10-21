package mihon.desktop.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import java.util.WeakHashMap

/**
 * On Android, Voyager gives every screen its own ViewModelStore; on desktop it doesn't, so two MangaScreens would
 * share one MangaViewModel. Each screen key gets a store here, cleared when the screen leaves the stack.
 */
class ScreenViewModelStores {
    private val stores = HashMap<String, ViewModelStore>()

    fun ownerFor(key: String): ViewModelStoreOwner {
        val store = stores.getOrPut(key) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    fun retainOnly(keys: Set<String>) {
        val gone = stores.keys - keys
        gone.forEach { stores.remove(it)?.clear() }
    }
}

// Per navigator, outside any screen's composition: a screen covered by another one leaves the composition, and
// its view models must still be there when it comes back. Weak keys: a navigator that goes away takes them along.
private val storesByNavigator = WeakHashMap<Navigator, ScreenViewModelStores>()

@Composable
fun ProvideScreenViewModelStore(navigator: Navigator, screen: Screen, content: @Composable () -> Unit) {
    val stores = remember(navigator) { storesByNavigator.getOrPut(navigator) { ScreenViewModelStores() } }
    LaunchedEffect(navigator.items) { stores.retainOnly(navigator.items.mapTo(HashSet()) { it.key }) }
    CompositionLocalProvider(LocalViewModelStoreOwner provides stores.ownerFor(screen.key), content = content)
}
