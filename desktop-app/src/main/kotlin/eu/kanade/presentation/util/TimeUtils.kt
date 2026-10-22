package eu.kanade.presentation.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun Duration.toDurationString(context: Context, fallback: String): String {
    return toComponents { days, hours, minutes, seconds, _ ->
        buildList(4) {
            if (days != 0L) add(context.stringResource(MR.strings.day_short, days))
            if (hours != 0) add(context.stringResource(MR.strings.hour_short, hours))
            if (minutes != 0 && (days == 0L || hours == 0)) {
                add(
                    context.stringResource(MR.strings.minute_short, minutes),
                )
            }
            if (seconds != 0 && days == 0L && hours == 0) add(context.stringResource(MR.strings.seconds_short, seconds))
        }.joinToString(" ").ifBlank { fallback }
    }
}

@Composable
@ReadOnlyComposable
fun relativeTimeSpanString(epochMillis: Long): String =
    relativeTimeSpan(LocalContext.current, epochMillis, Clock.System.now().toEpochMilliseconds())

// desktop: no android.text.format.DateUtils; this stands in for its getRelativeTimeSpanString
fun relativeTimeSpan(context: Context, epochMillis: Long, now: Long): String = when {
    epochMillis <= 0L -> context.stringResource(MR.strings.relative_time_span_never)
    now - epochMillis < 1.minutes.inWholeMilliseconds -> context.stringResource(
        MR.strings.updates_last_update_info_just_now,
    )
    now - epochMillis < 1.hours.inWholeMilliseconds -> ((now - epochMillis) / 1.minutes.inWholeMilliseconds).toInt()
        .let { context.pluralStringResource(MR.plurals.desktop_minutes_ago, it, it) }
    now - epochMillis < 1.days.inWholeMilliseconds -> ((now - epochMillis) / 1.hours.inWholeMilliseconds).toInt()
        .let { context.pluralStringResource(MR.plurals.desktop_hours_ago, it, it) }
    // "Yesterday", "3 days ago", then the date, like DateUtils
    now - epochMillis < 7.days.inWholeMilliseconds -> ((now - epochMillis) / 1.days.inWholeMilliseconds).toInt()
        .let { context.pluralStringResource(MR.plurals.relative_time, it, it) }
    else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}
