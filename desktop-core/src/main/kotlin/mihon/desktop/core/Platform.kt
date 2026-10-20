package mihon.desktop.core

import android.app.Application
import android.os.Looper
import dev.zacsweers.metro.createGraphFactory
import org.koin.core.context.startKoin
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.androidcompat.androidCompatModule
import xyz.nulldev.ts.config.configManagerModule
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

object DesktopBuildInfo {
    // ponytail: hardcoded until packaging (subproject 5/6) generates it from the release tag
    const val VERSION_CODE = 1
    const val VERSION_NAME = "0.1.0"

    /** Provisional application id; names the default shared preferences file. */
    const val APPLICATION_ID = "app.mangareader"
}

// AndroidCompat's CustomContext has no package name; Mihon's app code and some extensions expect one
private class DesktopApplication : Application() {
    override fun getPackageName(): String = DesktopBuildInfo.APPLICATION_ID
}

object Platform {
    /** Android's main thread: extensions post to Handler(Looper.getMainLooper()), komga even in its constructor. */
    private fun startMainLooper() {
        val ready = CountDownLatch(1)
        thread(name = "android-main", isDaemon = true) {
            Looper.prepareMainLooper()
            ready.countDown()
            Looper.loop()
        }
        ready.await()
    }

    /** Boots AndroidCompat, then the Metro graph, then the Injekt view extensions use. Call once per process. */
    fun start(dirs: AppDirs, isDebugBuild: Boolean = false): DesktopGraph {
        dirs.createAll()
        // AndroidCompat resolves every path (settings/, android-compat/) from this root
        System.setProperty("suwayomi.tachidesk.config.server.rootDir", dirs.data.toString())

        startKoin { modules(androidCompatModule(), configManagerModule()) }
        startMainLooper()
        AndroidCompatInitializer().init()
        val application = DesktopApplication()
        AndroidCompat().startApp(application)

        // Like Mihon, the application is the Context everything gets; it delegates to AndroidCompat's CustomContext
        val graph = createGraphFactory<DesktopGraph.Factory>().create(application, dirs, isDebugBuild)
        Injekt = InjektScope(DesktopInjektRegistrar(application, graph))
        return graph
    }
}
