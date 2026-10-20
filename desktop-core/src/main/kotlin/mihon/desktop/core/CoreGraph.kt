package mihon.desktop.core

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import mihon.core.metro.IsDebugBuild

/** The platform without UI: the compatibility harness and desktop-core's own tests run on it. */
@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [DesktopBindings::class],
)
interface CoreGraph : DesktopGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
            @Provides appDirs: AppDirs,
            @Provides @IsDebugBuild isDebugBuild: Boolean,
        ): CoreGraph
    }
}
