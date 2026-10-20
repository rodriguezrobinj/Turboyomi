package mihon.desktop.app.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import eu.kanade.tachiyomi.data.coil.BufferedSourceFetcher
import eu.kanade.tachiyomi.data.coil.CoverRequestInterceptor
import eu.kanade.tachiyomi.data.coil.ExtensionIconFetcher
import eu.kanade.tachiyomi.data.coil.InstalledExtensionIconKeyer
import eu.kanade.tachiyomi.data.coil.InstalledSourceIconKeyer
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer
import eu.kanade.tachiyomi.data.coil.MangaKeyer
import kotlinx.coroutines.Dispatchers
import mihon.app.di.AppGraph
import okio.Path.Companion.toOkioPath

/** Mihon's App.newImageLoader for desktop: same fetchers and keyers, Skia decoding, caches under AppDirs. */
object DesktopImageLoader {
    fun create(graph: AppGraph): ImageLoader {
        val callFactory = lazy { graph.networkHelper.client }
        return ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { callFactory.value }))
                add(BufferedSourceFetcher.Factory())
                add(MangaCoverFetcher.MangaCoverFactory(callFactory, graph.coverCache, graph.sourceManager))
                add(MangaCoverFetcher.MangaFactory(callFactory, graph.coverCache, graph.sourceManager))
                add(ExtensionIconFetcher.ExtensionFactory(graph.extensionManager))
                add(ExtensionIconFetcher.SourceFactory(graph.extensionManager))
                add(MangaCoverKeyer(graph.coverCache))
                add(MangaKeyer())
                add(InstalledExtensionIconKeyer())
                add(InstalledSourceIconKeyer())
                add(CoverRequestInterceptor())
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(PlatformContext.INSTANCE, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(graph.appDirs.cache.resolve("covers").toOkioPath())
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            // Coil spawns a new thread for every image load by default
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
            .build()
    }

    fun install(graph: AppGraph) {
        SingletonImageLoader.setSafe { create(graph) }
    }
}
