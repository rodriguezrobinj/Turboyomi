package eu.kanade.tachiyomi.ui.browse.extension.details

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.MultiSelectListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.ConfigurableSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import mihon.desktop.app.source.SourcePreferenceModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

// desktop: Mihon's version hosts a PreferenceFragmentCompat (Android Views); this one draws the same preferences
class SourcePreferencesScreen(val sourceId: Long) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val source by produceState<ConfigurableSource?>(null) {
            value = context.appGraph.sourceManager.get(sourceId) as? ConfigurableSource
        }
        when (val current = source) {
            null -> EmptyScreen(MR.strings.empty_screen)
            else -> SourcePreferencesContent(current, navigator::pop)
        }
    }
}

@Composable
fun SourcePreferencesContent(source: ConfigurableSource, navigateUp: () -> Unit) {
    val context = LocalContext.current
    // Extensions may change other preferences from a listener, so every change reloads the whole screen
    var version by remember { mutableIntStateOf(0) }
    val preferences by produceState<List<Preference>?>(null, version) {
        value = withContext(Dispatchers.IO) { SourcePreferenceModel.load(source, context) }
    }
    val onChange: (Preference, Any) -> Unit = { pref, value ->
        if (SourcePreferenceModel.set(pref, value)) version++
    }

    Scaffold(
        topBar = { AppBar(title = source.name, navigateUp = navigateUp, scrollBehavior = it) },
    ) { padding ->
        val list = preferences
        if (list == null) {
            LoadingScreen()
            return@Scaffold
        }
        LazyColumn(contentPadding = padding) {
            items(list.filter { it.visible }, key = { it.key ?: it.title.toString() }) { pref ->
                key(version) { SourcePreference(pref, onChange) }
            }
        }
    }
}

@Composable
private fun SourcePreference(pref: Preference, onChange: (Preference, Any) -> Unit) {
    val title = pref.title?.toString().orEmpty()
    val summary = SourcePreferenceModel.summaryOf(pref)
    when (pref) {
        is TwoStatePreference -> SwitchPreferenceWidget(
            title = title,
            subtitle = summary,
            checked = pref.currentValue as Boolean,
            onCheckedChanged = { onChange(pref, it) },
        )
        is ListPreference -> ListPreferenceWidget(
            value = pref.currentValue as String,
            title = title,
            subtitle = summary,
            icon = null,
            entries = pref.entryValues.map { it.toString() }.zip(pref.entries.map { it.toString() }).toMap(),
            onValueChange = { onChange(pref, it) },
        )
        is MultiSelectListPreference ->
            @Suppress("UNCHECKED_CAST")
            MultiSelectListPreferenceWidget(
                values = pref.currentValue as Set<String>,
                title = title,
                subtitle = summary,
                icon = null,
                entries = pref.entryValues.map { it.toString() }.zip(pref.entries.map { it.toString() }).toMap(),
                onValuesChange = { onChange(pref, it) },
            )
        is EditTextPreference -> EditTextPreferenceWidget(
            title = title,
            subtitle = summary,
            icon = null,
            value = pref.currentValue as String,
            onConfirm = {
                onChange(pref, it)
                true
            },
        )
        else -> TextPreferenceWidget(title = title, subtitle = summary)
    }
}
