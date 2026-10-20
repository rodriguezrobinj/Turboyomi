package mihon.desktop.app.platform

import android.widget.Toast
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Short messages for the window's snackbar: Android toasts from extensions and from ported Mihon code. */
object AppMessages {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun install() {
        Toast.listener = java.util.function.Consumer { show(it.toString()) }
    }

    fun show(message: String) {
        if (message.isNotBlank()) _messages.tryEmit(message)
    }
}
