package mihon.desktop.app.testing

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import mihon.app.di.AppGraph
import mihon.core.metro.IsDebugBuild
import mihon.desktop.app.DesktopApp
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopBindings
import mihon.desktop.core.DesktopSourceManager
import mihon.desktop.core.Platform
import tachiyomi.domain.source.service.SourceManager
import kotlin.io.path.createTempDirectory

/** The app graph with [TestSources.fake] as the only source, instead of the installed extensions. */
@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [DesktopBindings::class, TestBindings::class],
    excludes = [DesktopSourceManager::class],
)
interface TestAppGraph : AppGraph {
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
    fun sourceManager(): SourceManager = FakeSourceManager(TestSources.fake)
}

/** Boots the platform once for this JVM (the test task forks per class) in a folder with a non-ASCII path. */
fun startTestApp(): TestAppGraph {
    val root = createTempDirectory("José Pérez app")
    val graph = Platform.start(AppDirs(root.resolve("data"), root.resolve("local"))) { context, dirs, debug ->
        createGraphFactory<TestAppGraph.Factory>().create(context, dirs, debug)
    }
    DesktopApp.graph = graph
    return graph
}
