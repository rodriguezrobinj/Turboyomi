package mihon.desktop.app.platform

import android.widget.Toast
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A snackbar message; with [actionLabel], the snackbar shows a button that runs [onAction]. */
data class AppMessage(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/** Short messages for the window's snackbar: Android toasts from extensions and from ported Mihon code. */
object AppMessages {
    private val _messages = MutableSharedFlow<AppMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<AppMessage> = _messages.asSharedFlow()

    fun install() {
        Toast.listener = java.util.function.Consumer { show(it.toString()) }
    }

    fun show(message: String) {
        if (message.isNotBlank()) _messages.tryEmit(AppMessage(message))
    }

    fun show(message: String, actionLabel: String, onAction: () -> Unit) {
        if (message.isNotBlank()) _messages.tryEmit(AppMessage(message, actionLabel, onAction))
    }
}
