package mihon.desktop.app.main

import androidx.compose.ui.input.key.Key

enum class Shortcut { Back, GlobalSearch, Refresh, Settings }

/** The window-wide shortcuts (spec 2a, section 3). Anything else stays with the focused component. */
fun shortcutFor(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean): Shortcut? = when {
    key == Key.Escape && !ctrl && !alt && !shift -> Shortcut.Back
    key == Key.DirectionLeft && alt && !ctrl -> Shortcut.Back
    key == Key.F && ctrl && !alt -> Shortcut.GlobalSearch
    key == Key.F5 && !ctrl && !alt -> Shortcut.Refresh
    key == Key.Comma && ctrl && !alt -> Shortcut.Settings
    else -> null
}
