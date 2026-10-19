package mihon.desktop.core

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.metro.IsDebugBuild
import mihon.desktop.extension.ExtensionTrust
import mihon.domain.extension.repository.ExtensionStoreRepository
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.Database
import tachiyomi.domain.source.service.SourceManager

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [DesktopBindings::class],
)
interface DesktopGraph {
    val context: Context
    val appDirs: AppDirs

    val json: Json
    val protoBuf: ProtoBuf
    val xml: XML
    val networkHelper: NetworkHelper
    val javaScriptEngine: JavaScriptEngine
    val preferenceStore: PreferenceStore
    val database: Database

    val extensionStoreRepository: ExtensionStoreRepository
    val extensionTrust: ExtensionTrust
    val extensionManager: DesktopExtensionManager
    val sourceManager: SourceManager

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
            @Provides appDirs: AppDirs,
            @Provides @IsDebugBuild isDebugBuild: Boolean,
        ): DesktopGraph
    }
}
