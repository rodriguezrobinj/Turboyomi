package mihon.desktop.app.testing

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import mihon.app.di.AppGraph
import mihon.core.metro.IsDebugBuild
import mihon.desktop.app.DesktopApp
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopBindings
import mihon.desktop.core.DesktopSourceManager
import mihon.desktop.core.Platform
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.updates.service.UpdatesPreferences
import kotlin.io.path.createTempDirectory

/** The app graph with [TestSources.fake] as the only source, instead of the installed extensions. */
@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [DesktopBindings::class, TestBindings::class],
    excludes = [DesktopSourceManager::class],
)
interface TestAppGraph : AppGraph {
    val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId
    val getChaptersByMangaId: GetChaptersByMangaId
    val readerViewModelFactory: ReaderViewModel.Factory
    val networkToLocalManga: NetworkToLocalManga
    val syncChaptersWithSource: SyncChaptersWithSource
    val getHistory: GetHistory
    val upsertHistory: UpsertHistory
    val removeHistory: RemoveHistory
    val updateManga: UpdateManga
    val setMangaCategories: SetMangaCategories
    val createCategoryWithName: CreateCategoryWithName
    val getLibraryManga: GetLibraryManga
    val getManga: GetManga
    val updatesPreferences: UpdatesPreferences

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
            @Provides appDirs: AppDirs,
            @Provides @IsDebugBuild isDebugBuild: Boolean,
        ): TestAppGraph
    }
}

@BindingContainer
object TestBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun sourceManager(): SourceManager = FakeSourceManager(TestSources.fake, TestSources.http, TestSources.updatable)
}

private var started: TestAppGraph? = null

/**
 * Boots the platform once for this JVM (the test task forks per class) in a folder with a non-ASCII path.
 * Later calls in the same class return the same graph: the platform can't start twice.
 */
@Synchronized
fun startTestApp(): TestAppGraph {
    started?.let { return it }
    val root = createTempDirectory("José Pérez app")
    val graph = Platform.start(AppDirs(root.resolve("data"), root.resolve("local"))) { context, dirs, debug ->
        createGraphFactory<TestAppGraph.Factory>().create(context, dirs, debug)
    }
    DesktopApp.graph = graph
    started = graph
    return graph
}
