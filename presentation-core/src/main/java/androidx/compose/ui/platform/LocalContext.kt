package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

// desktop: Compose Multiplatform has no LocalContext; the window provides AndroidCompat's application context
val LocalContext = staticCompositionLocalOf<Context> { error("LocalContext is provided by the app window") }
