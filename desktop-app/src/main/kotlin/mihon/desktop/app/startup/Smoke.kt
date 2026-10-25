package mihon.desktop.app.startup

import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.DesktopApp
import mihon.desktop.app.DesktopAppGraph
import mihon.desktop.app.local.startStorage
import mihon.desktop.core.AppDirs
import mihon.desktop.core.Platform
import java.nio.file.Path

/**
 * `Turboyomi.exe --smoke` (spec 6a §2): builds the app graph on fresh data in [home], with no window, and reads the
 * database, which proves the packaged runtime has the modules the app itself needs.
 */
object Smoke {
    fun run(home: Path): Boolean = runCatching {
        val graph = Platform.start(AppDirs(home.resolve("data"), home.resolve("local"))) { context, dirs, debug ->
            createGraphFactory<DesktopAppGraph.Factory>().create(context, dirs, debug)
        }
        DesktopApp.graph = graph
        graph.startStorage()
        runBlocking { graph.getCategories.await() }
        true
    }.getOrElse {
        it.printStackTrace()
        false
    }
}
