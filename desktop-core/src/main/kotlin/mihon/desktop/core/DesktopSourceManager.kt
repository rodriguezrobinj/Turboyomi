package mihon.desktop.core

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopSourceManager(
    extensionManager: DesktopExtensionManager,
    @AppCoroutineScope scope: CoroutineScope,
) : SourceManager {

    override val sources: StateFlow<List<Source>> = extensionManager.installed
        .map { list -> list.filterIsInstance<Extension.Loaded>().flatMap { it.sources } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun get(sourceKey: Long): Source? = sources.value.find { it.id == sourceKey }

    override suspend fun getOrStub(sourceKey: Long): Source = get(sourceKey) ?: StubSource(sourceKey, "", "")

    override suspend fun getAll(): List<Source> = sources.value

    override suspend fun getOnlineSources(): List<HttpSource> = sources.value.filterIsInstance<HttpSource>()

    // ponytail: stubs for library entries whose extension is gone come with the library (subproject 3)
    override suspend fun getStubSources(): List<StubSource> = emptyList()
}
