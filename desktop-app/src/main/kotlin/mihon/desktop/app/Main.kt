package mihon.desktop.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent
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
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.StringDesc
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.desktop.app.browser.EmbeddedBrowserWindow
import mihon.desktop.app.image.DesktopImageLoader
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.main.shortcutFor
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.platform.ExitHooks
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.platform.WindowControls
import mihon.desktop.app.startup.MIN_HEIGHT
import mihon.desktop.app.startup.MIN_WIDTH
import mihon.desktop.app.startup.SavedWindow
import mihon.desktop.app.startup.Startup
import mihon.desktop.app.startup.WindowStateStore
import mihon.desktop.app.startup.placeOnScreens
import mihon.desktop.core.AppDirs
import mihon.desktop.core.Platform
import mihon.desktop.core.SingleInstance
import mihon.desktop.core.jobs.JobScheduler
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
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
    // Loads the installed extensions (their sources feed SourceManager) and the store listings at startup
    graph.mihonExtensionManager
    // Periodic jobs while the app is open or in the tray (parent spec §5.1); only the library update for now
    JobScheduler(
        listOf(graph.libraryUpdateJob.periodicJob()),
    ).start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    val windowStore = WindowStateStore(graph.preferenceStore)
    // Each screen's work area: its bounds without the taskbar, so a default-sized window never hides under it
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
        val config = device.defaultConfiguration
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        config.bounds.run {
            Rectangle(
                x + insets.left,
                y + insets.top,
                width - insets.left - insets.right,
                height - insets.top - insets.bottom,
            )
        }
    }
    val initial = placeOnScreens(windowStore.load(), screens)

    application {
        val windowState = rememberWindowState(
            placement = if (initial.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = WindowPosition(initial.x.dp, initial.y.dp),
            size = DpSize(initial.width.dp, initial.height.dp),
        )
        val windowControls = remember { WindowControls(windowState) }
        val exitScope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }
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
                    // Once, even if the close button is clicked again while the exit work runs
                    if (!closing) {
                        closing = true
                        // The reader's last progress write and history finish first (at most 2 s)
                        exitScope.launch {
                            ExitHooks.runAll()
                            windowStore.save(windowControls.toSaved())
                            instance.close()
                            exitApplication()
                        }
                    }
                },
                title = AppDirs.APP_NAME,
                onPreviewKeyEvent = { event -> handleShortcut(event, beforeFocusedComponent = true) },
                onKeyEvent = { event -> handleShortcut(event, beforeFocusedComponent = false) },
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
                CompositionLocalProvider(LocalWindowControls provides windowControls) {
                    key(restarts) {
                        AppContent(graph, crash) {
                            crash = null
                            restarts++
                        }
                    }
                }
            }
            // The WebView window, while a page is open in it (spec 2b §3 E)
            EmbeddedBrowserWindow(graph)
        }
    }
}

private fun handleShortcut(event: KeyEvent, beforeFocusedComponent: Boolean): Boolean =
    event.type == KeyEventType.KeyDown &&
        shortcutFor(event.key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
            ?.takeIf { it.beforeFocusedComponent == beforeFocusedComponent }
            ?.let(AppEvents::send) == true

// Never the fullscreen bounds: the window opens normal next time
private fun WindowControls.toSaved() = SavedWindow(
    x = positionToSave.x.value.toInt(),
    y = positionToSave.y.value.toInt(),
    width = sizeToSave.width.value.toInt(),
    height = sizeToSave.height.value.toInt(),
    maximized = placementToSave == WindowPlacement.Maximized,
)
