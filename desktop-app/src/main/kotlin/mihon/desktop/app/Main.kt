package mihon.desktop.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.StringDesc
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import logcat.LogPriority
import mihon.desktop.app.image.DesktopImageLoader
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.main.shortcutFor
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.startup.MIN_HEIGHT
import mihon.desktop.app.startup.MIN_WIDTH
import mihon.desktop.app.startup.SavedWindow
import mihon.desktop.app.startup.Startup
import mihon.desktop.app.startup.WindowStateStore
import mihon.desktop.app.startup.placeOnScreens
import mihon.desktop.core.AppDirs
import mihon.desktop.core.Platform
import mihon.desktop.core.SingleInstance
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val dirs = AppDirs.fromEnvironment()
    Startup.configureLogging(dirs)

    val instance = when (val result = SingleInstance.acquire(dirs.lockFile, args.toList())) {
        is SingleInstance.Result.Primary -> result.instance
        SingleInstance.Result.Forwarded -> exitProcess(0)
        // No Context exists yet, so the string is resolved with moko directly
        SingleInstance.Result.Unreachable -> Startup.fatal(
            StringDesc.Resource(MR.strings.desktop_instance_unreachable).localized(),
            dirs.logs,
        )
    }
    // A second launch forwards its arguments here; accept() blocks, so it runs on IO, never on the UI thread
    val bringToFront = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    instance.listen(CoroutineScope(SupervisorJob() + Dispatchers.IO)) { bringToFront.tryEmit(Unit) }

    val graph = try {
        Platform.start(dirs) { context, appDirs, debug ->
            createGraphFactory<DesktopAppGraph.Factory>().create(context, appDirs, debug)
        }
    } catch (e: Throwable) {
        Startup.logcat(LogPriority.ERROR, e) { "Startup failed" }
        Startup.fatal(e.message ?: e.toString(), dirs.logs)
    }
    DesktopApp.graph = graph
    AppMessages.install()
    DesktopImageLoader.install(graph)
    val windowStore = WindowStateStore(graph.preferenceStore)
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
    val initial = placeOnScreens(windowStore.load(), screens)

    application {
        val windowState = rememberWindowState(
            placement = if (initial.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = WindowPosition(initial.x.dp, initial.y.dp),
            size = DpSize(initial.width.dp, initial.height.dp),
        )
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
                state = windowState,
                onCloseRequest = {
                    windowStore.save(windowState.toSaved())
                    instance.close()
                    exitApplication()
                },
                title = AppDirs.APP_NAME,
                onPreviewKeyEvent = { event ->
                    event.type == KeyEventType.KeyDown &&
                        shortcutFor(event.key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
                            ?.let(AppEvents::send) == true
                },
            ) {
                window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
                LaunchedEffect(Unit) {
                    bringToFront.collect {
                        windowState.isMinimized = false
                        // Windows doesn't let a background process take the foreground with toFront() alone
                        window.isAlwaysOnTop = true
                        window.toFront()
                        window.isAlwaysOnTop = false
                    }
                }
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

private fun WindowState.toSaved() = SavedWindow(
    x = position.x.value.toInt(),
    y = position.y.value.toInt(),
    width = size.width.value.toInt(),
    height = size.height.value.toInt(),
    maximized = placement == WindowPlacement.Maximized,
)
