package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsViewModel
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.domain.extension.model.ContentWarning
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class ExtensionPillsTest {
    private val installed = Extension.Loaded(
        name = "Example", pkgName = "pkg.example", versionName = "1.4.1", versionCode = 1, libVersion = 1.4,
        lang = "en", contentWarning = ContentWarning.SAFE, isShared = false, signatures = listOf("key"),
        pkgFactory = null, sources = emptyList(),
    )

    private inner class ListScreen : Screen() {
        @Composable
        override fun Content() {
            ExtensionScreen(
                state = ExtensionsViewModel.State(
                    isLoading = false,
                    items = mapOf(
                        ExtensionUiModel.Header.Resource(MR.strings.ext_installed) to
                            listOf(ExtensionUiModel.Item(installed, InstallStep.Idle)),
                    ),
                ),
                contentPadding = PaddingValues(),
                searchQuery = null,
                onLongClickItem = {},
                onClickItemCancel = {},
                onOpenWebView = {},
                onInstallExtension = {},
                onUninstallExtension = {},
                onUpdateExtension = {},
                onTrustExtension = {},
                onOpenExtension = {},
                onClickUpdateAll = {},
                onRefresh = {},
            )
        }
    }

    @Test
    fun `installed extensions don't carry a private pill, every desktop install is private`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppProviders(graph) { Navigator(ListScreen()) } }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Example")).fetchSemanticsNodes().isNotEmpty() }

        onAllNodes(hasText(str(MR.strings.ext_installer_private))).fetchSemanticsNodes().size shouldBe 0
    }
}
