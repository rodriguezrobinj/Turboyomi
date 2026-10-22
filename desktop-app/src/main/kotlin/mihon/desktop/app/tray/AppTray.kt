package mihon.desktop.app.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import mihon.app.di.AppGraph
import mihon.desktop.core.AppDirs
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

enum class CloseAction { Hide, Exit }

/** What the window's close button does (spec 3a §3 G). */
fun closeAction(traySupported: Boolean, closeToTray: Boolean): CloseAction =
    if (traySupported && closeToTray) CloseAction.Hide else CloseAction.Exit

/**
 * The tray icon while the system has a tray: Open, Update library, Exit; double click (and any notification click,
 * AWT gives them the same action) opens the window. While it's up, [TrayNotifier] sends here.
 */
@Composable
fun ApplicationScope.AppTray(graph: AppGraph, onOpen: () -> Unit, onExit: () -> Unit) {
    if (!graph.trayPreferences.traySupported) return
    val context = graph.context
    val state = rememberTrayState()
    DisposableEffect(state) {
        TrayNotifier.sink = {
            state.sendNotification(
                Notification(it.title, it.message, if (it.isError) Notification.Type.Error else Notification.Type.Info),
            )
        }
        onDispose { TrayNotifier.sink = null }
    }
    val progress by graph.libraryUpdateJob.progress.collectAsState()
    val tooltip = progress?.let {
        context.stringResource(
            MR.strings.desktop_update_progress,
            it.done,
            it.total,
            it.current.firstOrNull().orEmpty(),
        )
    } ?: AppDirs.APP_NAME
    val vector = rememberVectorPainter(MaterialSymbols.Rounded.CollectionsBookmark)
    // ponytail: a tinted symbol until the app has its own icon (packaging), plan 3a deviation 2
    val icon = remember(vector) { TintedPainter(vector, TRAY_TINT) }
    Tray(
        icon = icon,
        state = state,
        tooltip = tooltip,
        onAction = onOpen,
        menu = {
            Item(context.stringResource(MR.strings.desktop_tray_open), onClick = onOpen)
            Item(context.stringResource(MR.strings.action_update_library), onClick = { graph.libraryUpdateJob.start() })
            Separator()
            Item(context.stringResource(MR.strings.desktop_tray_exit), onClick = onExit)
        },
    )
}

private val TRAY_TINT = Color(0xFF3B82F6)

/** Draws [inner] in one color, so a black symbol shows on light and dark taskbars. */
private class TintedPainter(private val inner: Painter, private val tint: Color) : Painter() {
    override val intrinsicSize: Size get() = inner.intrinsicSize

    override fun DrawScope.onDraw() {
        with(inner) { draw(size, colorFilter = ColorFilter.tint(tint)) }
    }
}
