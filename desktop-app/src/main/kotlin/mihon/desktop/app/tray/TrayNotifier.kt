package mihon.desktop.app.tray

import android.content.Context
import mihon.desktop.app.platform.AppMessages
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR

/** A notification (spec 3a §3 G). [opensUpdates]: clicking it opens the Updates tab. */
data class Notice(
    val title: String,
    val message: String,
    val isError: Boolean = false,
    val opensUpdates: Boolean = true,
)

/**
 * Where notifications go: the tray sets [sink] while its icon is up; without it they're in-app messages.
 * AWT hands every notification click to the tray icon's one action, so [unread] tells that action whether the last
 * notification asked to open the Updates tab.
 */
object TrayNotifier {
    @Volatile
    var sink: ((Notice) -> Unit)? = null

    @Volatile
    var unread: Boolean = false

    fun send(notice: Notice) {
        val tray = sink
        if (tray == null) {
            AppMessages.show("${notice.title}\n${notice.message}")
            return
        }
        if (notice.opensUpdates) unread = true
        tray(notice)
    }
}

/** "14 new chapters", then the first three titles and "and N more". */
fun newChaptersNotice(context: Context, updates: List<Pair<Manga, List<Chapter>>>): Notice {
    val chapters = updates.sumOf { it.second.size }
    val titles = updates.map { it.first.title }
    val lines = buildList {
        add(context.pluralStringResource(MR.plurals.notification_chapters_generic, chapters, chapters))
        addAll(titles.take(SHOWN_TITLES))
        if (titles.size > SHOWN_TITLES) {
            add(context.stringResource(MR.strings.desktop_and_more, titles.size - SHOWN_TITLES))
        }
    }
    return Notice(context.stringResource(MR.strings.notification_new_chapters), lines.joinToString("\n"))
}

fun errorsNotice(context: Context, count: Int): Notice = Notice(
    title = context.pluralStringResource(MR.plurals.notification_update_error, count, count),
    message = context.stringResource(MR.strings.desktop_update_errors_hint),
    isError = true,
)

private const val SHOWN_TITLES = 3
