package mihon.desktop.core

import android.content.Context
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.core.cef.CefInstaller
import mihon.desktop.extension.ExtensionTrust
import mihon.domain.extension.repository.ExtensionStoreRepository
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.Database
import tachiyomi.domain.source.service.SourceManager

/**
 * What every desktop graph exposes. The @DependencyGraph itself lives in the leaf module (CoreGraph here,
 * DesktopAppGraph in desktop-app), because Metro only merges the contributions the graph's module can see.
 */
interface DesktopGraph {
    val context: Context
    val appDirs: AppDirs

    val json: Json
    val protoBuf: ProtoBuf
    val xml: XML
    val networkHelper: NetworkHelper
    val networkPreferences: NetworkPreferences
    val javaScriptEngine: JavaScriptEngine
    val preferenceStore: PreferenceStore
    val database: Database

    val extensionStoreRepository: ExtensionStoreRepository
    val extensionTrust: ExtensionTrust
    val extensionManager: DesktopExtensionManager
    val sourceManager: SourceManager
    val cefInstaller: CefInstaller
}
