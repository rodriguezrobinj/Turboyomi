package eu.kanade.tachiyomi.util.system

import android.content.Context

// desktop: no connectivity manager; a failed request reports the real error instead
@Suppress("UnusedReceiverParameter")
fun Context.isOnline(): Boolean = true
