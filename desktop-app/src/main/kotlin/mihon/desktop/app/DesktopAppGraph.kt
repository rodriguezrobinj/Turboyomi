package mihon.desktop.app

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import mihon.app.di.AppGraph
import mihon.core.metro.IsDebugBuild
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopBindings

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [DesktopBindings::class],
)
interface DesktopAppGraph : AppGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
            @Provides appDirs: AppDirs,
            @Provides @IsDebugBuild isDebugBuild: Boolean,
        ): DesktopAppGraph
    }
}
