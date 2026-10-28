package mihon.desktop.app.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import mihon.desktop.app.reader.ReaderAction
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.keyboardScrollable

const val SHORTCUT_HELP_TAG = "keyboard help"

/** One line of the keyboard help (spec 8a §3 C): the keys as they're pressed and what they do. */
data class ShortcutRow(
    val keys: StringResource,
    val description: StringResource,
    /** The window shortcuts or reader actions it explains, so a test can tell none is missing. */
    val covers: Set<Enum<*>> = emptySet(),
    /** A reader line for one kind of viewer only; null for both. */
    val viewer: ReadingMode.ViewerType? = null,
)

/** Whether the keyboard help is open, and the open reader's kind of viewer, whose keys it then shows. */
object ShortcutHelp {
    var open by mutableStateOf(false)
    var readerViewer by mutableStateOf<ReadingMode.ViewerType?>(null)

    val appRows = listOf(
        ShortcutRow(MR.strings.desktop_help_keys_help, MR.strings.desktop_help_help, setOf(Shortcut.Help)),
        ShortcutRow(
            MR.strings.desktop_help_keys_global_search,
            MR.strings.desktop_help_global_search,
            setOf(Shortcut.GlobalSearch),
        ),
        ShortcutRow(MR.strings.desktop_help_keys_refresh, MR.strings.desktop_help_refresh, setOf(Shortcut.Refresh)),
        ShortcutRow(
            MR.strings.desktop_help_keys_fullscreen,
            MR.strings.desktop_help_fullscreen,
            setOf(Shortcut.Fullscreen),
        ),
        ShortcutRow(MR.strings.desktop_help_keys_settings, MR.strings.desktop_help_settings, setOf(Shortcut.Settings)),
        ShortcutRow(
            MR.strings.desktop_help_keys_select_all,
            MR.strings.desktop_help_select_all,
            setOf(Shortcut.SelectAll),
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_categories,
            MR.strings.desktop_help_categories,
            setOf(Shortcut.NextCategory, Shortcut.PreviousCategory),
        ),
        ShortcutRow(MR.strings.desktop_help_keys_back, MR.strings.desktop_help_back, setOf(Shortcut.Back)),
        // Subproyecto 7's keys: no Shortcut of their own
        ShortcutRow(MR.strings.desktop_help_keys_scroll_lists, MR.strings.desktop_help_scroll_lists),
    )

    val readerRows = listOf(
        ShortcutRow(
            MR.strings.desktop_help_keys_turn_paged,
            MR.strings.desktop_help_turn,
            setOf(ReaderAction.NextPage, ReaderAction.PreviousPage),
            ReadingMode.ViewerType.Pager,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_turn_webtoon,
            MR.strings.desktop_help_turn,
            setOf(ReaderAction.NextPage, ReaderAction.PreviousPage),
            ReadingMode.ViewerType.Webtoon,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_scroll,
            MR.strings.desktop_help_scroll,
            setOf(ReaderAction.ScrollDown, ReaderAction.ScrollUp),
            ReadingMode.ViewerType.Webtoon,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_first_last,
            MR.strings.desktop_help_first_last,
            setOf(ReaderAction.FirstPage, ReaderAction.LastPage),
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_chapters,
            MR.strings.desktop_help_chapters,
            setOf(ReaderAction.NextChapter, ReaderAction.PreviousChapter),
        ),
        ShortcutRow(MR.strings.desktop_help_keys_menu, MR.strings.desktop_help_menu, setOf(ReaderAction.ToggleMenu)),
        ShortcutRow(
            MR.strings.desktop_help_keys_zoom,
            MR.strings.desktop_help_zoom,
            setOf(ReaderAction.ZoomIn, ReaderAction.ZoomOut, ReaderAction.ZoomReset),
            ReadingMode.ViewerType.Pager,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_side_padding,
            MR.strings.desktop_help_side_padding,
            setOf(ReaderAction.ZoomIn, ReaderAction.ZoomOut),
            ReadingMode.ViewerType.Webtoon,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_scroll_step,
            MR.strings.desktop_help_scroll_step,
            setOf(ReaderAction.ScrollStepUp, ReaderAction.ScrollStepDown),
            ReadingMode.ViewerType.Webtoon,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_autoscroll,
            MR.strings.desktop_help_autoscroll,
            setOf(ReaderAction.ToggleAutoScroll),
            ReadingMode.ViewerType.Webtoon,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_autoscroll_speed,
            MR.strings.desktop_help_autoscroll_speed,
            setOf(ReaderAction.AutoScrollFaster, ReaderAction.AutoScrollSlower),
            ReadingMode.ViewerType.Webtoon,
        ),
    )

    /** The reader lines for [viewer], or for both kinds when no reader is open. */
    fun readerRowsFor(viewer: ReadingMode.ViewerType?): List<ShortcutRow> =
        readerRows.filter { viewer == null || it.viewer == null || it.viewer == viewer }
}

/** The keyboard help: the app's shortcuts, then the reader's for [viewer] (both kinds when null). */
@Composable
fun ShortcutHelpDialog(viewer: ReadingMode.ViewerType?, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 720.dp).testTag(SHORTCUT_HELP_TAG),
        ) {
            val scroll = rememberScrollState()
            Column(
                Modifier.keyboardScrollable(scroll).verticalScroll(scroll).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(MR.strings.desktop_help_title), style = MaterialTheme.typography.headlineSmall)
                Section(MR.strings.desktop_help_app, ShortcutHelp.appRows, markViewer = false)
                Section(MR.strings.desktop_help_reader, ShortcutHelp.readerRowsFor(viewer), markViewer = viewer == null)
            }
        }
    }
}

@Composable
private fun Section(title: StringResource, rows: List<ShortcutRow>, markViewer: Boolean) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(row.keys),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(280.dp),
            )
            val mark = when {
                !markViewer || row.viewer == null -> ""
                row.viewer == ReadingMode.ViewerType.Webtoon ->
                    " " +
                        stringResource(MR.strings.desktop_help_only_webtoon)
                else -> " " + stringResource(MR.strings.desktop_help_only_paged)
            }
            Text(stringResource(row.description) + mark, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
