package mihon.desktop.core.cef

import kotlinx.coroutines.runBlocking
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopGraph
import mihon.desktop.core.Platform
import xyz.nulldev.androidcompat.webkit.CefHelper
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory

/** The platform with the real embedded browser, installed once into the folder cefTest passes as `cef.dir`. */
fun startWithRealCef(): DesktopGraph {
    val root = createTempDirectory("cef test José")
    val graph = Platform.start(AppDirs(root.resolve("data"), root.resolve("local")))
    val dir = Path(System.getProperty("cef.dir") ?: error("Run these with ./gradlew :desktop-core:cefTest"))
    val installer = CefInstaller(dir)
    if (!installer.isInstalled()) runBlocking { installer.install() }
    CefRuntime.start(dir, root.resolve("cef-cache"))
    check(CefRuntime.isRunning) {
        "The embedded browser didn't start: ${CefHelper.cefApp.value.exceptionOrNull()?.stackTraceToString()}"
    }
    return graph
}
