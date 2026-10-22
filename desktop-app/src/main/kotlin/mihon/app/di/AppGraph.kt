package mihon.app.di

import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import mihon.desktop.app.tray.TrayPreferences
import mihon.desktop.core.DesktopGraph
import mihon.domain.extension.interactor.GetExtensionStoreCountAsFlow
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.library.service.LibraryPreferences

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
    val readerPreferences: ReaderPreferences
    val getExtensionStoreCountAsFlow: GetExtensionStoreCountAsFlow
    val libraryPreferences: LibraryPreferences
    val libraryUpdateJob: LibraryUpdateJob
    val getCategories: GetCategories
    val resetCategoryFlags: ResetCategoryFlags
    val trayPreferences: TrayPreferences
}
