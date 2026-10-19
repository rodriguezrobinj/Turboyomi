package androidx.core.text

import android.text.Html
import android.text.Spanned

fun String.parseAsHtml(flags: Int = Html.FROM_HTML_MODE_LEGACY): Spanned = Html.fromHtml(this, flags)
