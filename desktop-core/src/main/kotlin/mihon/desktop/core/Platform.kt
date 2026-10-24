package mihon.desktop.core

import android.app.Application
import android.content.Context
import android.os.Looper
import dev.zacsweers.metro.createGraphFactory
import mihon.desktop.core.cef.CefCookieSync
import mihon.desktop.core.cef.CefRuntime
import mihon.desktop.core.cookies.PersistentCookieStore
import org.koin.core.context.startKoin
import org.koin.dsl.module
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.androidcompat.androidCompatModule
import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider
import xyz.nulldev.ts.config.configManagerModule
import java.net.CookieHandler
import java.net.CookiePolicy
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

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

    /** Boots the platform with the UI-less [CoreGraph]. */
    fun start(dirs: AppDirs, isDebugBuild: Boolean = false): DesktopGraph =
        start(dirs, isDebugBuild) { context, appDirs, debug ->
            createGraphFactory<CoreGraph.Factory>().create(context, appDirs, debug)
        }

    /** Boots AndroidCompat, then the leaf module's Metro graph, then the Injekt view extensions use. Once per process. */
    fun <G : DesktopGraph> start(
        dirs: AppDirs,
        isDebugBuild: Boolean = false,
        createGraph: (Context, AppDirs, Boolean) -> G,
    ): G {
        dirs.createAll()
        // AndroidCompat's android.os.Build makes kotlinx-coroutines take its Android fast path, which picks
        // AndroidCompat's dispatcher (the android-main looper) as Dispatchers.Main. The UI thread is Swing's: read
        // the registered factories instead. Must run before anything touches Dispatchers.Main.
        System.setProperty("kotlinx.coroutines.fast.service.loader", "false")
        // AndroidCompat resolves every path (settings/, android-compat/) from this root
        System.setProperty("suwayomi.tachidesk.config.server.rootDir", dirs.data.toString())

        // Logins and Cloudflare clearances survive restarts (spec 2b §2). NetworkHelper keeps this handler
        val cookies = PersistentCookieStore(dirs.cookies)
        CookieHandler.setDefault(java.net.CookieManager(cookies, CookiePolicy.ACCEPT_ALL))
        Runtime.getRuntime().addShutdownHook(thread(start = false, name = "cookie-flush") { cookies.flush() })

        startKoin {
            modules(
                androidCompatModule(),
                configManagerModule(),
                // AndroidCompat's WebView asks Koin for this when it creates a browser: it brings in the app's cookies
                module { single<KcefWebViewProvider.InitBrowserHandler> { CefCookieSync } },
            )
        }
        startMainLooper()
        // Without this, a WebView waits forever for Chromium inside a runBlocking on the main looper, freezing it
        // for every extension; CefRuntime replaces it once the browser runs
        CefRuntime.markNotInstalled()
        AndroidCompatInitializer().init()
        val application = DesktopApplication()
        AndroidCompat().startApp(application)

        // Like Mihon, the application is the Context everything gets; it delegates to AndroidCompat's CustomContext
        val graph = createGraph(application, dirs, isDebugBuild)
        Injekt = InjektScope(DesktopInjektRegistrar(application, graph))
        // The embedded browser starts in the background when it's installed (spec 2b §2). A removal asked for while
        // it ran happens first, before anything loads its DLLs
        graph.cefInstaller.applyPendingRemoval()
        if (graph.cefInstaller.isInstalled()) CefRuntime.startAsync(dirs.cef, dirs.cefCache)
        return graph
    }
}
