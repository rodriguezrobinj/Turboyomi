package mihon.app.di

import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import mihon.desktop.core.DesktopGraph

/**
 * Desktop counterpart of Mihon's AppGraph: the accessors ported code reaches through `context.appGraph`.
 * The graph itself is DesktopAppGraph (tests: TestAppGraph).
 */
interface AppGraph : DesktopGraph, ViewModelGraph {
    val uiPreferences: UiPreferences
    val coverCache: CoverCache
}
