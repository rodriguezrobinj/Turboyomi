package mihon.desktop.app.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/** The reader's quick settings (spec 2a §4): mode for this manga, scale, background and dual pages. */
@Composable
fun ReaderQuickSettings(
    preferences: ReaderPreferences,
    mode: ReadingMode,
    shifted: Boolean,
    onModeChange: (ReadingMode) -> Unit,
    onShiftChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val scale by preferences.imageScaleType.collectAsState()
    val background by preferences.readerTheme.collectAsState()
    val dual by preferences.dualPageView.collectAsState()
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Group(MR.strings.pref_viewer_type) {
                    ReadingMode.entries.filter { it != ReadingMode.DEFAULT }.forEach {
                        Chip(it.stringRes, selected = it == mode) { onModeChange(it) }
                    }
                }
                Group(MR.strings.pref_image_scale_type) {
                    ImageScale.entries.forEach {
                        Chip(it.label, selected = it == ImageScale.fromPreference(scale)) {
                            preferences.imageScaleType.set(it.prefValue)
                        }
                    }
                }
                Group(MR.strings.pref_reader_theme) {
                    ReaderBackground.entries.forEach {
                        Chip(it.label, selected = it == ReaderBackground.fromPreference(background)) {
                            preferences.readerTheme.set(it.prefValue)
                        }
                    }
                }
                if (mode.direction == ReadingMode.Direction.Horizontal) {
                    Group(MR.strings.pref_dual_page_view) {
                        ReaderPreferences.DualPageView.entries.forEach {
                            Chip(it.titleRes, selected = it == dual) { preferences.dualPageView.set(it) }
                        }
                        if (dual != ReaderPreferences.DualPageView.NEVER) {
                            Chip(MR.strings.desktop_dual_page_shift, selected = shifted) { onShiftChange(!shifted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: StringResource, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Chip(label: StringResource, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}
