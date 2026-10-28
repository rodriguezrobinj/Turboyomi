package eu.kanade.tachiyomi.util

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.core.DesktopBuildInfo
import org.junit.jupiter.api.Test

class CrashLogUtilTest {
    private val graph = startTestApp()

    @Test
    fun `the dump has the debug info and nothing else without problem extensions`(): Unit = runBlocking {
        val util = graph.crashLogUtil
        util.extensionsInfo().shouldBeNull()

        val file = util.dumpLogs()
        file.parentFile.toPath() shouldBe graph.appDirs.logs
        file.readText() shouldContain "App version: ${DesktopBuildInfo.VERSION_NAME}"
        file.readText() shouldContain "OS: ${System.getProperty("os.name")}"
    }
}
