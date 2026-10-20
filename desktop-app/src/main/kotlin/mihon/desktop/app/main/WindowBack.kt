package mihon.desktop.app.main

import androidx.activity.compose.BackPressDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * Back for the whole window goes through Compose's own back dispatcher, which already gets Esc and puts dialogs
 * first. [BackPressDispatcher] (the screens' BackHandlers) sits under them, so Esc closes a dialog before the
 * screen behind it. Alt+Left enters the same way through [WindowBackInput].
 */
@Composable
fun ConnectWindowBack() {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return
    DisposableEffect(dispatcher) {
        val screens = object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, true) {
            override fun onBackCompleted() {
                BackPressDispatcher.dispatch()
            }
        }
        dispatcher.addHandler(screens)
        dispatcher.addInput(WindowBackInput)
        onDispose {
            screens.remove()
            dispatcher.removeInput(WindowBackInput)
        }
    }
}

/** Back from a window shortcut (Alt+Left), dispatched like Compose dispatches Esc. */
object WindowBackInput : NavigationEventInput() {
    fun back() = dispatchOnBackCompleted()
}
