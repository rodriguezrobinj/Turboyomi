package mihon.desktop.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import dev.zacsweers.metro.createGraphFactory
import logcat.LogPriority
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.main.shortcutFor
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.core.AppDirs
import mihon.desktop.core.Platform
import tachiyomi.core.common.util.system.logcat

fun main() {
    val graph = Platform.start(AppDirs.fromEnvironment()) { context, dirs, debug ->
        createGraphFactory<DesktopAppGraph.Factory>().create(context, dirs, debug)
    }
    DesktopApp.graph = graph
    AppMessages.install()

    application {
        var crash by remember { mutableStateOf<Throwable?>(null) }
        var restarts by remember { mutableIntStateOf(0) }
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides WindowExceptionHandlerFactory {
                WindowExceptionHandler { error ->
                    logcat(LogPriority.ERROR, error) { "Window content crashed" }
                    crash = error
                }
            },
        ) {
            Window(
                onCloseRequest = ::exitApplication,
                title = AppDirs.APP_NAME,
                onPreviewKeyEvent = { event ->
                    event.type == KeyEventType.KeyDown &&
                        shortcutFor(event.key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
                            ?.let(AppEvents::send) == true
                },
            ) {
                key(restarts) {
                    AppContent(graph, crash) {
                        crash = null
                        restarts++
                    }
                }
            }
        }
    }
}
