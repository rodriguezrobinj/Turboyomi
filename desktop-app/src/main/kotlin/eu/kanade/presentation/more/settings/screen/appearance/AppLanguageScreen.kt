package eu.kanade.presentation.more.settings.screen.appearance

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.app.di.appGraph
import mihon.desktop.app.settings.AppLanguage
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.keyboardScrollable

class AppLanguageScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val langs = remember { getLangs(context) }
        // desktop: the app keeps the choice itself; Main applies it and redraws the window (spec 11d §2)
        val languagePref = remember { context.appGraph.uiPreferences.appLanguage }
        var currentLanguage by remember { mutableStateOf(languagePref.get()) }

        LaunchedEffect(currentLanguage) {
            if (languagePref.get() != currentLanguage) languagePref.set(currentLanguage)
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_app_language),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.padding(contentPadding).keyboardScrollable(listState),
                state = listState,
            ) {
                items(langs) {
                    ListItem(
                        modifier = Modifier.clickable {
                            currentLanguage = it.langTag
                        },
                        trailingContent = {
                            if (currentLanguage == it.langTag) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        supportingContent = {
                            it.localizedDisplayName?.let {
                                Text(it)
                            }
                        },
                        // desktop: Compose Desktop's Material 3 ListItem names it headlineContent
                        headlineContent = { Text(it.displayName) },
                    )
                }
            }
        }
    }

    private fun getLangs(context: Context): List<Language> = buildList {
        // desktop: the moko translations instead of locales_config.xml
        AppLanguage.TAGS.forEach { langTag ->
            val displayName = LocaleHelper.getLocalizedDisplayName(langTag)
            if (displayName.isNotEmpty()) {
                add(Language(langTag, displayName, LocaleHelper.getDisplayName(langTag)))
            }
        }

        sortBy { it.displayName }
        add(0, Language("", context.stringResource(MR.strings.label_default), null))
    }

    private data class Language(
        val langTag: String,
        val displayName: String,
        val localizedDisplayName: String?,
    )
}
