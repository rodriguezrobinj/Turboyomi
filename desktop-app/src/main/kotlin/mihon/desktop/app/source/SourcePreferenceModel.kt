package mihon.desktop.app.source

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.sourcePreferences

/**
 * An extension's settings, built as Suwayomi builds them: AndroidCompat's androidx.preference models, each one
 * reading and writing the source's own SharedPreferences, where the extension reads them back.
 */
object SourcePreferenceModel {
    fun load(source: ConfigurableSource, context: Context): List<Preference> {
        val screen = PreferenceScreen(context)
        // Set before setup: addPreference hands the screen's SharedPreferences to each preference
        screen.sharedPreferences = source.sourcePreferences()
        source.setupPreferenceScreen(screen)
        return screen.preferences
    }

    /** Saves [value] only when the extension's change listener accepts it, like androidx.preference does. */
    fun set(pref: Preference, value: Any): Boolean {
        if (!pref.callChangeListener(value)) return false
        pref.saveNewValue(value)
        return true
    }

    /** The summary, with a list's "%s" replaced by the chosen entry as on Android. */
    fun summaryOf(pref: Preference): String? {
        val summary = pref.summary?.toString() ?: return null
        if (pref !is ListPreference || "%s" !in summary) return summary
        val index = pref.findIndexOfValue(pref.currentValue as? String)
        val entry = pref.entries?.getOrNull(index)?.toString().orEmpty()
        return summary.replace("%s", entry)
    }
}
