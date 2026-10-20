package mihon.desktop.app.main

import androidx.compose.ui.input.key.Key

enum class Shortcut(
    /**
     * Handled in the window's preview pass, before the focused component sees the key. Back is not: a dialog or a
     * search field closes itself on Esc, and only an Esc nobody handled goes back.
     */
    val beforeFocusedComponent: Boolean,
) {
    Back(false),
    GlobalSearch(true),
    Refresh(true),
    Settings(true),
}

/** The window-wide shortcuts (spec 2a, section 3). Anything else stays with the focused component. */
fun shortcutFor(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean): Shortcut? = when {
    // Esc is Compose's own back key (see ConnectWindowBack); only Alt+Left needs mapping
    key == Key.DirectionLeft && alt && !ctrl -> Shortcut.Back
    key == Key.F && ctrl && !alt -> Shortcut.GlobalSearch
    key == Key.F5 && !ctrl && !alt -> Shortcut.Refresh
    key == Key.Comma && ctrl && !alt -> Shortcut.Settings
    else -> null
}
