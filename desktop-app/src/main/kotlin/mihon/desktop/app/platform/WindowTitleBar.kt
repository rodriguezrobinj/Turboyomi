package mihon.desktop.app.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The window's title bar where Windows can't paint its own in the theme's colors (Windows 10, see [CustomTitleBar]):
 * icon, title and Windows 10's three buttons. Windows moves the window from the rest of the bar, so [onGeometry]
 * tells it where the buttons are. It sits at the window's top.
 */
@Composable
fun WindowTitleBar(
    title: String,
    icon: Painter,
    active: Boolean,
    maximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    onGeometry: (TitleBarGeometry) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Windows dims an inactive window's bar
    val content = colors.onSurface.copy(alpha = if (active) 1f else 0.6f)
    Row(
        Modifier.fillMaxWidth().height(32.dp).background(colors.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(icon, null, Modifier.padding(horizontal = 8.dp).size(16.dp))
        Text(
            title,
            Modifier.weight(1f),
            color = content,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.onGloballyPositioned {
                onGeometry(TitleBarGeometry(it.size.height, it.positionInWindow().x.toInt()))
            },
        ) {
            val hover = colors.onSurface.copy(alpha = 0.08f)
            CaptionButton(stringResource(MR.strings.desktop_window_minimize), content, hover, content, onMinimize) {
                drawLine(it, Offset(0f, center.y), Offset(size.width, center.y), glyphStroke)
            }
            CaptionButton(
                stringResource(
                    if (maximized) MR.strings.desktop_window_restore else MR.strings.desktop_window_maximize,
                ),
                content,
                hover,
                content,
                onToggleMaximize,
            ) { color ->
                val stroke = Stroke(glyphStroke)
                if (maximized) {
                    // Two windows, the back one peeking out above and to the right
                    val step = size.width / 5
                    drawRect(color, Offset(0f, step), Size(size.width - step, size.height - step), style = stroke)
                    val back = Path().apply {
                        moveTo(step, step)
                        lineTo(step, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height - step)
                        lineTo(size.width - step, size.height - step)
                    }
                    drawPath(back, color, style = stroke)
                } else {
                    drawRect(color, style = stroke)
                }
            }
            CaptionButton(stringResource(MR.strings.action_close), content, CloseHover, Color.White, onClose) {
                drawLine(it, Offset.Zero, Offset(size.width, size.height), glyphStroke)
                drawLine(it, Offset(size.width, 0f), Offset(0f, size.height), glyphStroke)
            }
        }
    }
}

/** Windows 10's caption button: 46 × 32 dp, a 10 dp glyph, a plain background on hover. */
@Composable
private fun CaptionButton(
    description: String,
    color: Color,
    hoverBackground: Color,
    hoverColor: Color,
    onClick: () -> Unit,
    glyph: DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(46.dp, 32.dp)
            .background(if (hovered) hoverBackground else Color.Transparent)
            .hoverable(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) { glyph(if (hovered) hoverColor else color) }
    }
}

// The glyphs' 1 px line at 100 % scale, as Windows draws them
private val DrawScope.glyphStroke get() = 1.dp.toPx()

private val CloseHover = Color(0xFFE81123)
