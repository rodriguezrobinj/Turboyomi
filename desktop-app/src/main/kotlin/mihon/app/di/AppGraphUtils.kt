package mihon.app.di

import android.content.Context
import mihon.desktop.app.DesktopApp

// desktop: Mihon casts applicationContext to GraphProvider; AndroidCompat's context isn't the Application
@Suppress("UnusedReceiverParameter")
val Context.appGraph: AppGraph get() = DesktopApp.graph
