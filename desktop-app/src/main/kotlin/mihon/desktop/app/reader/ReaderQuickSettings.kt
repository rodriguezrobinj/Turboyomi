package mihon.desktop.app.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BaseSliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.keyboardScrollable

/**
 * The reader's quick settings (spec 2a §4): mode for this manga, scale, background, dual pages, and the webtoon's
 * side padding, scroll step and automatic scrolling speed.
 */
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
    val sidePadding by preferences.webtoonSidePadding.collectAsState()
    val scrollStep by preferences.webtoonScrollStep.collectAsState()
    val autoScrollSpeed by preferences.webtoonAutoScrollSpeed.collectAsState()
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            // Taller than a low window with every filter on
            val scroll = rememberScrollState()
            Column(
                Modifier.keyboardScrollable(scroll).verticalScroll(scroll).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
                if (mode.type == ReadingMode.ViewerType.Webtoon) {
                    BaseSliderItem(
                        value = sidePadding,
                        valueRange = ReaderPreferences.WEBTOON_PADDING_MIN..ReaderPreferences.WEBTOON_PADDING_MAX,
                        title = stringResource(MR.strings.pref_webtoon_side_padding),
                        valueString = "$sidePadding %",
                        onChange = preferences.webtoonSidePadding::set,
                        titleStyle = MaterialTheme.typography.titleSmall,
                    )
                    BaseSliderItem(
                        value = scrollStep,
                        valueRange =
                        ReaderPreferences.WEBTOON_SCROLL_STEP_MIN..ReaderPreferences.WEBTOON_SCROLL_STEP_MAX,
                        // Stops every 5 %, like the Ctrl keys: one per percent is a blur of 95 ticks
                        steps =
                        (ReaderPreferences.WEBTOON_SCROLL_STEP_MAX - ReaderPreferences.WEBTOON_SCROLL_STEP_MIN) /
                            ReaderPreferences.WEBTOON_KEY_STEP - 1,
                        title = stringResource(MR.strings.desktop_webtoon_scroll_step),
                        valueString = "$scrollStep %",
                        onChange = preferences.webtoonScrollStep::set,
                        titleStyle = MaterialTheme.typography.titleSmall,
                    )
                    BaseSliderItem(
                        value = autoScrollSpeed,
                        valueRange =
                        ReaderPreferences.WEBTOON_AUTOSCROLL_MIN..ReaderPreferences.WEBTOON_AUTOSCROLL_MAX,
                        // Stops every 2 %/s, like Ctrl+Shift+↑ and ↓
                        steps = (ReaderPreferences.WEBTOON_AUTOSCROLL_MAX - ReaderPreferences.WEBTOON_AUTOSCROLL_MIN) /
                            ReaderPreferences.WEBTOON_AUTOSCROLL_STEP - 1,
                        title = stringResource(MR.strings.desktop_webtoon_autoscroll_speed),
                        valueString = "$autoScrollSpeed %/s",
                        onChange = preferences.webtoonAutoScrollSpeed::set,
                        titleStyle = MaterialTheme.typography.titleSmall,
                    )
                }
                WidePages(preferences, webtoon = mode.type == ReadingMode.ViewerType.Webtoon)
                ImageFilters(preferences)
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

/** Mihon's color filter page (ColorFilterPage), as one group; the brightness only dims on the desktop (spec 8b). */
@Composable
private fun ImageFilters(preferences: ReaderPreferences) {
    val customBrightness by preferences.customBrightness.collectAsState()
    val customBrightnessValue by preferences.customBrightnessValue.collectAsState()
    val colorFilter by preferences.colorFilter.collectAsState()
    val colorFilterValue by preferences.colorFilterValue.collectAsState()
    val colorFilterMode by preferences.colorFilterMode.collectAsState()
    val grayscale by preferences.grayscale.collectAsState()
    val invertedColors by preferences.invertedColors.collectAsState()
    Group(MR.strings.custom_filter) {
        Chip(MR.strings.pref_custom_brightness, selected = customBrightness) {
            preferences.customBrightness.set(!customBrightness)
        }
        Chip(MR.strings.pref_custom_color_filter, selected = colorFilter) { preferences.colorFilter.set(!colorFilter) }
        Chip(MR.strings.pref_grayscale, selected = grayscale) { preferences.grayscale.set(!grayscale) }
        Chip(MR.strings.pref_inverted_colors, selected = invertedColors) {
            preferences.invertedColors.set(!invertedColors)
        }
    }
    if (customBrightness) {
        FilterSlider(MR.strings.pref_custom_brightness, customBrightnessValue.coerceAtMost(0), -75..0) {
            preferences.customBrightnessValue.set(it)
        }
    }
    if (colorFilter) {
        ColorChannels.forEach { (label, shift) ->
            FilterSlider(label, colorFilterValue ushr shift and 0xFF, 0..255) { value ->
                preferences.colorFilterValue.getAndSet { (it and (0xFF shl shift).inv()) or (value shl shift) }
            }
        }
        Group(MR.strings.pref_color_filter_mode) {
            ReaderPreferences.ColorFilterMode.forEachIndexed { index, (label, _) ->
                Chip(label, selected = colorFilterMode == index) { preferences.colorFilterMode.set(index) }
            }
        }
    }
}

// The color filter's ARGB channels and where each sits in the Int
private val ColorChannels = listOf(
    MR.strings.color_filter_r_value to 16,
    MR.strings.color_filter_g_value to 8,
    MR.strings.color_filter_b_value to 0,
    MR.strings.color_filter_a_value to 24,
)

@Composable
private fun FilterSlider(label: StringResource, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    BaseSliderItem(
        value = value,
        valueRange = range,
        title = stringResource(label),
        onChange = onChange,
        steps = 0,
        titleStyle = MaterialTheme.typography.titleSmall,
    )
}

/** Crop, split and turn for the mode's kind of viewer (spec 8c); splitting and turning exclude each other, as in Mihon. */
@Composable
private fun WidePages(preferences: ReaderPreferences, webtoon: Boolean) {
    val crop = if (webtoon) preferences.cropBordersWebtoon else preferences.cropBorders
    val split = if (webtoon) preferences.dualPageSplitWebtoon else preferences.dualPageSplitPaged
    val splitInvert = if (webtoon) preferences.dualPageInvertWebtoon else preferences.dualPageInvertPaged
    val rotate = if (webtoon) preferences.dualPageRotateToFitWebtoon else preferences.dualPageRotateToFit
    val rotateInvert = if (webtoon) {
        preferences.dualPageRotateToFitInvertWebtoon
    } else {
        preferences.dualPageRotateToFitInvert
    }
    val cropOn by crop.collectAsState()
    val splitOn by split.collectAsState()
    val splitInvertOn by splitInvert.collectAsState()
    val rotateOn by rotate.collectAsState()
    val rotateInvertOn by rotateInvert.collectAsState()
    val showPageNumber by preferences.showPageNumber.collectAsState()
    val alwaysShowTransition by preferences.alwaysShowChapterTransition.collectAsState()
    val navigation = if (webtoon) preferences.navigationModeWebtoon else preferences.navigationModePager
    val navigationInvert = if (webtoon) preferences.webtoonNavInverted else preferences.pagerNavInverted
    val navigationOn by navigation.collectAsState()
    val navigationInvertOn by navigationInvert.collectAsState()
    Group(MR.strings.pref_category_display) {
        Chip(MR.strings.pref_show_page_number, selected = showPageNumber) {
            preferences.showPageNumber.set(!showPageNumber)
        }
        Chip(MR.strings.pref_always_show_chapter_transition, selected = alwaysShowTransition) {
            preferences.alwaysShowChapterTransition.set(!alwaysShowTransition)
        }
        Chip(MR.strings.pref_crop_borders, selected = cropOn) { crop.set(!cropOn) }
        Chip(MR.strings.pref_dual_page_split, selected = splitOn) {
            // Read before the write: the state may already hold the new value after it
            val on = !splitOn
            split.set(on)
            if (on) rotate.set(false)
        }
        if (splitOn) {
            Chip(MR.strings.pref_dual_page_invert, selected = splitInvertOn) { splitInvert.set(!splitInvertOn) }
        }
        Chip(MR.strings.pref_page_rotate, selected = rotateOn) {
            val on = !rotateOn
            rotate.set(on)
            if (on) split.set(false)
        }
        if (rotateOn) {
            Chip(MR.strings.pref_page_rotate_invert, selected = rotateInvertOn) { rotateInvert.set(!rotateInvertOn) }
        }
    }
    // Spec 8d: Mihon's tap zones, mirrored on request
    Group(MR.strings.pref_viewer_nav) {
        ReaderPreferences.TapZones.forEachIndexed { index, label ->
            Chip(label, selected = navigationOn == index) { navigation.set(index) }
        }
    }
    if (navigationOn != TapNavigation.DISABLED) {
        Group(MR.strings.pref_read_with_tapping_inverted) {
            ReaderPreferences.TappingInvertMode.entries.forEach {
                Chip(it.titleRes, selected = navigationInvertOn == it) { navigationInvert.set(it) }
            }
        }
    }
}
