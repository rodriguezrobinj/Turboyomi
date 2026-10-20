package mihon.desktop.app

import mihon.app.di.AppGraph

/** The running app's graph, for code ported from Mihon that reaches it through `context.appGraph`. */
object DesktopApp {
    lateinit var graph: AppGraph
}
