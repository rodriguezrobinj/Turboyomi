package mihon.desktop.app

import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import mihon.desktop.core.AppDirs

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = AppDirs.APP_NAME) {
        Text(AppDirs.APP_NAME)
    }
}
