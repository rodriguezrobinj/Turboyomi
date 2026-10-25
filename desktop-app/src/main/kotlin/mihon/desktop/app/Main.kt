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
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.desktop.app.browser.EmbeddedBrowserWindow
import mihon.desktop.app.image.DesktopImageLoader
import mihon.desktop.app.local.startStorage
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
import mihon.desktop.app.startup.Smoke
import mihon.desktop.app.startup.Startup
import mihon.desktop.app.startup.WindowStateStore
import mihon.desktop.app.startup.placeOnScreens
import mihon.desktop.app.track.LoginUrls
import mihon.desktop.app.track.ProtocolRegistrar
import mihon.desktop.app.tray.AppTray
import mihon.desktop.app.tray.CloseAction
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import mihon.desktop.app.tray.closeAction
import mihon.desktop.app.update.UpdateState
import mihon.desktop.core.AppDirs
import mihon.desktop.core.LegacyMigration
import mihon.desktop.core.Platform
import mihon.desktop.core.SingleInstance
import mihon.desktop.core.jobs.JobScheduler
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.handleScrollKey
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Packaging's check (spec 6a §2): fresh data, no window, before the migration and the single-instance lock
    if ("--smoke" in args) {
        val ok = Smoke.run(createTempDirectory("turboyomi-smoke"))
        println(if (ok) "OK" else "FAILED")
        exitProcess(if (ok) 0 else 1)
    }
    // The development build's data comes over before anything creates or opens it (spec 6a §3 A). On a failure the
    // app stops here, before Turboyomi's folders exist: a start with fresh data would make every later start skip it.
    // Logging isn't set up yet, so the error goes in the dialog
    val migration = LegacyMigration.run()
    if (migration is LegacyMigration.Result.Failed) {
        Startup.stop(
            StringDesc.Resource(MR.strings.desktop_legacy_migration_failed).localized() + "\n\n" + migration.error,
        )
    }
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
    // OAuth redirects (spec 5b-2 §3 A): a second instance's, and this start's own, wait here until the graph exists
    val loginUrls = Channel<String>(Channel.UNLIMITED)
    LoginUrls.route(args.toList()) { loginUrls.trySend(it) }
    // A second launch forwards its arguments here; accept() blocks, so it runs on IO, never on the UI thread
    val bringToFront = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    instance.listen(CoroutineScope(SupervisorJob() + Dispatchers.IO)) { forwarded ->
        LoginUrls.route(forwarded) { loginUrls.trySend(it) }
        bringToFront.tryEmit(Unit)
    }

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
    graph.startStorage()
    ProtocolRegistrar.register(dirs.local.resolve("protocol-launch.args").toFile())
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        for (url in loginUrls) graph.trackLoginHandler.handle(url)
    }
    // Mihon clears the chapter cache on launch when asked to (MainActivity)
    if (graph.libraryPreferences.autoClearChapterCache.get()) {
        CoroutineScope(Dispatchers.IO).launch { graph.chapterCache.clear() }
    }
    // Periodic jobs while the app is open or in the tray (parent spec §5.1)
    JobScheduler(
        listOf(
            graph.libraryUpdateJob.periodicJob(),
            graph.backupJob.periodicJob(),
            graph.delayedTrackingJob.periodicJob(),
            graph.appUpdater.periodicJob(),
        ),
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
        // The scroll keys' animations run here (spec 7 §3 C)
        val scrollScope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }
        var windowVisible by remember { mutableStateOf(true) }
        // Exit from the close button (no tray) or the tray's Exit: the reader's last write first, and the update stops
        val exit = {
            if (!closing) {
                closing = true
                graph.libraryUpdateJob.cancel()
                exitScope.launch {
                    ExitHooks.runAll()
                    windowStore.save(windowControls.toSaved())
                    instance.close()
                    exitApplication()
                }
            }
        }
        // desktop: the update's msiexec waits for this process to end (spec 6b §3 B)
        LaunchedEffect(Unit) {
            graph.appUpdater.state.first { it == UpdateState.Installing }
            exit()
        }
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
                visible = windowVisible,
                onCloseRequest = {
                    val trayPreferences = graph.trayPreferences
                    when (closeAction(trayPreferences.traySupported, trayPreferences.closeToTray.get())) {
                        // Once, even if the close button is clicked again while the exit work runs (at most 2 s)
                        CloseAction.Exit -> exit()
                        CloseAction.Hide -> {
                            windowVisible = false
                            if (!trayPreferences.hintShown.get()) {
                                trayPreferences.hintShown.set(true)
                                TrayNotifier.send(
                                    Notice(
                                        AppDirs.APP_NAME,
                                        graph.context.stringResource(MR.strings.desktop_tray_hint),
                                        opensUpdates = false,
                                    ),
                                )
                            }
                        }
                    }
                },
                title = AppDirs.APP_NAME,
                // desktop: the app's own icon (spec 6a §2)
                icon = painterResource("turboyomi.svg"),
                onPreviewKeyEvent = { event -> handleShortcut(event, beforeFocusedComponent = true, scrollScope) },
                onKeyEvent = { event -> handleShortcut(event, beforeFocusedComponent = false, scrollScope) },
            ) {
                window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
                LaunchedEffect(Unit) {
                    bringToFront.collect {
                        windowVisible = true
                        windowState.isMinimized = false
                        // Windows doesn't let a background process take the foreground with toFront() alone
                        window.isAlwaysOnTop = true
                        window.toFront()
                        window.isAlwaysOnTop = false
                        // A notification clicked while the window was away opens on the updates it announced
                        if (TrayNotifier.unread) {
                            TrayNotifier.unread = false
                            AppEvents.openUpdates()
                        }
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
            // The tray icon, notifications and close to tray (spec 3a §3 G)
            AppTray(graph, onOpen = { bringToFront.tryEmit(Unit) }, onExit = exit)
        }
    }
    // A stray non-daemon thread mustn't keep the process, which an update's msiexec waits for (spec 6b §3 B)
    exitProcess(0)
}

private fun handleShortcut(event: KeyEvent, beforeFocusedComponent: Boolean, scrollScope: CoroutineScope): Boolean {
    if (event.type == KeyEventType.KeyDown) {
        val shortcut = shortcutFor(event.key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
        if (shortcut != null && shortcut.beforeFocusedComponent == beforeFocusedComponent && AppEvents.send(shortcut)) {
            return true
        }
    }
    // After the focused component: a text field keeps its own Home, End, arrows and Space (spec 7 §3 C)
    return !beforeFocusedComponent && handleScrollKey(event, scrollScope)
}

// Never the fullscreen bounds: the window opens normal next time
private fun WindowControls.toSaved() = SavedWindow(
    x = positionToSave.x.value.toInt(),
    y = positionToSave.y.value.toInt(),
    width = sizeToSave.width.value.toInt(),
    height = sizeToSave.height.value.toInt(),
    maximized = placementToSave == WindowPlacement.Maximized,
)
