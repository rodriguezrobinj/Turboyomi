package mihon.desktop.app.startup

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.awt.Rectangle

data class SavedWindow(val x: Int, val y: Int, val width: Int, val height: Int, val maximized: Boolean)

const val MIN_WIDTH = 800
const val MIN_HEIGHT = 600
private const val DEFAULT_WIDTH = 1280
private const val DEFAULT_HEIGHT = 800

/**
 * Where the window opens: where it was if its top-left corner is still on a screen, else centered on the
 * first (primary) screen at the default size (or the screen size, if smaller). Never below the minimum size.
 */
fun placeOnScreens(saved: SavedWindow?, screens: List<Rectangle>): SavedWindow {
    val screen = saved?.let { s -> screens.find { it.contains(s.x, s.y) } }
    if (saved != null && screen != null) {
        // No bigger than the screen it is on, no smaller than the minimum
        return saved.copy(
            width = saved.width.coerceAtMost(screen.width).coerceAtLeast(MIN_WIDTH),
            height = saved.height.coerceAtMost(screen.height).coerceAtLeast(MIN_HEIGHT),
        )
    }
    val primary = screens.first()
    // A laptop screen can be smaller than the default size: keep the title bar on it
    val width = DEFAULT_WIDTH.coerceAtMost(primary.width)
    val height = DEFAULT_HEIGHT.coerceAtMost(primary.height)
    return SavedWindow(
        x = primary.x + (primary.width - width) / 2,
        y = primary.y + (primary.height - height) / 2,
        width = width,
        height = height,
        maximized = false,
    )
}

/** The window's last bounds, kept in the app preferences as "x,y,width,height,maximized". */
class WindowStateStore(store: PreferenceStore) {
    private val pref = store.getString(Preference.appStateKey("window_bounds"), "")

    fun load(): SavedWindow? {
        val parts = pref.get().split(',')
        if (parts.size != 5) return null
        val (x, y, w, h) = parts.take(4).map { it.toIntOrNull() ?: return null }
        return SavedWindow(x, y, w, h, parts[4].toBooleanStrictOrNull() ?: return null)
    }

    fun save(window: SavedWindow) {
        pref.set("${window.x},${window.y},${window.width},${window.height},${window.maximized}")
    }
}
