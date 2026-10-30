package mihon.desktop.app

import mihon.app.di.AppGraph
import mihon.desktop.core.jobs.JobScheduler

/** The running app's graph, for code ported from Mihon that reaches it through `context.appGraph`. */
object DesktopApp {
    lateinit var graph: AppGraph

    /** Started by Main; null in tests, where the debug screen lists no jobs (spec 11a §2). */
    @Volatile
    var jobScheduler: JobScheduler? = null
}
