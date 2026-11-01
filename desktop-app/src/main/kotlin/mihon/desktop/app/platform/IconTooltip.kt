package mihon.desktop.app.platform

import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable

/**
 * What an icon-only button does, on mouse hover (spec 12 §4), the way AppBar shows its actions' titles. [text] is the
 * button's content description.
 */
@Composable
fun IconTooltip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        focusable = false,
        content = content,
    )
}
