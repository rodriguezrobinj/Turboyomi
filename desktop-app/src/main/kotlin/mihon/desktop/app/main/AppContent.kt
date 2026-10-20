package mihon.desktop.app.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cafe.adriel.voyager.navigator.Navigator
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import mihon.app.di.AppGraph
import mihon.desktop.app.platform.AppMessages

/** What every screen needs around it: context, view model factory and root store, theme. Tests reuse it. */
@Composable
fun AppProviders(graph: AppGraph, content: @Composable () -> Unit) {
    val rootViewModels = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    CompositionLocalProvider(
        LocalContext provides graph.context,
        LocalMetroViewModelFactory provides graph.metroViewModelFactory,
        LocalViewModelStoreOwner provides rootViewModels,
    ) {
        TachiyomiTheme(content = content)
    }
}

/** Everything inside the window: the crash screen if composing threw, else the navigator and the snackbar. */
@Composable
fun AppContent(graph: AppGraph, crash: Throwable?, onRestart: () -> Unit) {
    AppProviders(graph) {
        // On Android the Activity theme paints the window background and content color; here the Surface does
        Surface(Modifier.fillMaxSize()) {
            if (crash != null) {
                CrashScreen(crash, onRestart)
                return@Surface
            }
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(Unit) { AppMessages.messages.collect { snackbar.showSnackbar(it) } }
            Box(Modifier.fillMaxSize()) {
                Navigator(HomeScreen) { DefaultNavigatorScreenTransition(it) }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}
