package mihon.desktop.app.platform

import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton

/** Right click does what a long press does on Android (select, add to library). */
fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier =
    this.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onClick)
