package mihon.desktop.app.tray

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.awt.SystemTray

@Inject
@SingleIn(AppScope::class)
class TrayPreferences(preferenceStore: PreferenceStore) {
    val closeToTray: Preference<Boolean> = preferenceStore.getBoolean("desktop_close_to_tray", false)

    /** The "still in the tray" notification is shown once. */
    val hintShown: Preference<Boolean> =
        preferenceStore.getBoolean(Preference.appStateKey("desktop_tray_hint_shown"), false)

    /** Without a tray (some Windows shells, headless tests) there's nothing to close to. Tests may set it. */
    @Volatile
    var traySupported: Boolean = runCatching { SystemTray.isSupported() }.getOrDefault(false)
}
