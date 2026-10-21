package mihon.desktop.core.cef

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.jetbrains.cef.JCefAppConfig
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException.Reason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import org.cef.CefApp
import org.cef.SystemBootstrap
import tachiyomi.core.common.util.system.logcat
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

/**
 * Starts the embedded browser (spec 2b §4) and publishes it in [CefHelper.cefApp], where AndroidCompat's WebView and
 * the WebView window wait for it. Ported from Suwayomi's CEFManager, Windows only.
 */
object CefRuntime {
    val isRunning: Boolean get() = CefHelper.cefApp.value.getOrNull() != null

    /** Until the browser runs, WebViews fail fast with this instead of waiting forever on the main looper. */
    fun markNotInstalled() {
        CefHelper.cefApp.value = Result.failure(EmbeddedBrowserUnavailableException(Reason.NOT_INSTALLED))
    }

    /** At app launch and after the download: on its own thread, it loads big DLLs. */
    fun startAsync(installDir: Path, cacheDir: Path) {
        // Starting from now, not from when the thread gets going: a WebView window opened right after the download
        // must wait for the browser, not read "not installed"
        if (!isRunning) CefHelper.cefApp.value = Result.success(null)
        thread(name = "cef-start", isDaemon = true) { start(installDir, cacheDir) }
    }

    @Synchronized
    fun start(installDir: Path, cacheDir: Path) {
        if (isRunning) return
        // Starting: WebViews created now wait for it instead of failing
        CefHelper.cefApp.value = Result.success(null)
        try {
            System.loadLibrary("jawt")
            val config = JCefAppConfig.getInstance(installDir.toAbsolutePath().toString(), false).apply {
                getAppArgsAsList().addAll(
                    listOf("--disable-gpu", "--off-screen-rendering-enabled", "--enable-widevine-cdm"),
                )
                getCefSettings().apply {
                    // AndroidCompat's WebView draws without a window; the WebView window still gets a native one
                    windowless_rendering_enabled = true
                    cache_path = cacheDir.toAbsolutePath().toString()
                }
            }
            // As Suwayomi: JCEF keeps a failed native load in a future nobody can read, so they're loaded here first
            listOf("chrome_elf", "libcef", "jcef").forEach { config.getLoader().loadLibrary(it) }
            CefApp.setIsRemoteEnabled(config.isRemoteEnabled())
            SystemBootstrap.setLoader(config.getLoader())
            CefApp.startup(config.getAppArgs())
            val app = CefApp.getInstance(config.getAppArgs(), config.getCefSettings(), config.getServerExe())
            CefHelper.cefApp.value = Result.success(app)
            runBlocking { withTimeout(30.seconds) { CefHelper.waitForInit().first() } }
            Runtime.getRuntime().addShutdownHook(thread(start = false, name = "cef-stop") { app.dispose() })
            logcat(LogPriority.INFO) { "The embedded browser started" }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "The embedded browser didn't start" }
            CefHelper.cefApp.value = Result.failure(EmbeddedBrowserUnavailableException(Reason.NOT_STARTED, e))
        }
    }
}
