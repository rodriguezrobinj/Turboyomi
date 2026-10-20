package mihon.desktop.app.reader

import android.content.Context
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR

/** Opens the reader. ponytail: only a notice until the reader exists (subproject 2a, plan 2). */
object ReaderLauncher {
    @Suppress("UNUSED_PARAMETER")
    fun open(context: Context, mangaId: Long, chapterId: Long?) {
        context.toast(MR.strings.desktop_reader_coming)
    }
}
