package mihon.desktop.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import mihon.app.di.appGraph
import mihon.desktop.app.reader.ImageScale
import mihon.desktop.app.reader.ReaderBackground
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** The reader's defaults (spec 2a §3, Ajustes › Lector); the reader's quick panel changes the same preferences. */
object SettingsReaderScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val preferences = remember { context.appGraph.readerPreferences }
        return listOf(
            Preference.PreferenceItem.ListPreference(
                preference = preferences.defaultReadingMode,
                entries = ReadingMode.entries.filter { it != ReadingMode.DEFAULT }
                    .associate { it.flagValue to stringResource(it.stringRes) },
                title = stringResource(MR.strings.pref_viewer_type),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = preferences.imageScaleType,
                entries = ImageScale.entries.associate { it.prefValue to stringResource(it.label) },
                title = stringResource(MR.strings.pref_image_scale_type),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = preferences.readerTheme,
                entries = ReaderBackground.entries.associate { it.prefValue to stringResource(it.label) },
                title = stringResource(MR.strings.pref_reader_theme),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = preferences.dualPageView,
                entries = ReaderPreferences.DualPageView.entries.associateWith { stringResource(it.titleRes) },
                title = stringResource(MR.strings.pref_dual_page_view),
            ),
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.webtoonSidePadding,
                title = stringResource(MR.strings.pref_webtoon_side_padding),
                valueText = { "$it %" },
                valueRange = ReaderPreferences.WEBTOON_PADDING_MIN..ReaderPreferences.WEBTOON_PADDING_MAX,
            ),
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.webtoonScrollStep,
                title = stringResource(MR.strings.desktop_webtoon_scroll_step),
                valueText = { "$it %" },
                valueRange = ReaderPreferences.WEBTOON_SCROLL_STEP_MIN..ReaderPreferences.WEBTOON_SCROLL_STEP_MAX,
            ),
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.webtoonAutoScrollSpeed,
                title = stringResource(MR.strings.desktop_webtoon_autoscroll_speed),
                valueText = { "$it %/s" },
                valueRange = ReaderPreferences.WEBTOON_AUTOSCROLL_MIN..ReaderPreferences.WEBTOON_AUTOSCROLL_MAX,
            ),
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.preloadPages,
                title = stringResource(MR.strings.desktop_reader_preload_pages),
                valueRange = 1..10,
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.skipRead,
                title = stringResource(MR.strings.pref_skip_read_chapters),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.skipFiltered,
                title = stringResource(MR.strings.pref_skip_filtered_chapters),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.skipDupe,
                title = stringResource(MR.strings.pref_skip_dupe_chapters),
            ),
        )
    }
}
