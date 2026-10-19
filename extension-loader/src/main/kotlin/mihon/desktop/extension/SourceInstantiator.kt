package mihon.desktop.extension

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import mihon.desktop.extension.vendor.ChildFirstURLClassLoader
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** One class loader per extension jar, kept so its sources keep working until the jar is released. */
object SourceInstantiator {
    private val loaders = ConcurrentHashMap<Path, ChildFirstURLClassLoader>()

    fun instantiate(jar: Path, classNames: List<String>): List<Source> {
        val loader = loaders.getOrPut(jar.toAbsolutePath()) { ChildFirstURLClassLoader(arrayOf(jar.toUri().toURL())) }
        return classNames.flatMap { className ->
            when (val obj = Class.forName(className, false, loader).getDeclaredConstructor().newInstance()) {
                is Source -> listOf(obj)
                is SourceFactory -> obj.createSources()
                else -> throw IllegalStateException("Unknown source class type: ${obj.javaClass}")
            }
        }
    }

    /** Closes the jar so Windows lets it be deleted. Its sources must not be used afterwards. */
    fun release(jar: Path) {
        loaders.remove(jar.toAbsolutePath())?.close()
    }
}
