package mihon.app.di

import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.extension.ExtensionManager
import mihon.desktop.core.DesktopGraph
import mihon.domain.extension.interactor.GetExtensionStoreCountAsFlow

/**
 * Desktop counterpart of Mihon's AppGraph: the accessors ported code reaches through `context.appGraph`.
 * The graph itself is DesktopAppGraph (tests: TestAppGraph).
 */
interface AppGraph : DesktopGraph, ViewModelGraph {
    val uiPreferences: UiPreferences
    val basePreferences: BasePreferences
    val coverCache: CoverCache
    val chapterCache: ChapterCache
    val mihonExtensionManager: ExtensionManager
    val sourcePreferences: SourcePreferences
    val getExtensionStoreCountAsFlow: GetExtensionStoreCountAsFlow
}
