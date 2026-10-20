package eu.kanade.tachiyomi.ui.browse.extension.details

import androidx.compose.runtime.Composable
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen

// desktop: placeholder so ExtensionDetailsScreen compiles; Task 10 of plan 1 replaces it with the Compose rewrite
class SourcePreferencesScreen(val sourceId: Long) : Screen() {
    @Composable
    override fun Content() {
        EmptyScreen(MR.strings.empty_screen)
    }
}
