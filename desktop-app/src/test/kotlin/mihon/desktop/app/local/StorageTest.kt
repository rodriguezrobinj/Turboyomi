package mihon.desktop.app.local

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds

class StorageTest {
    private val graph = startTestApp()

    @Test
    fun `by default local and downloads live in the app's storage folder`() {
        val storage = graph.appDirs.storage
        File(graph.storageManager.getLocalSourceDirectory()!!.filePath!!) shouldBe storage.resolve("local").toFile()
        File(graph.storageManager.getDownloadsDirectory()!!.filePath!!) shouldBe storage.resolve("downloads").toFile()
    }

    @Test
    fun `choosing another folder creates local, downloads and autobackup in it and reads from it`(): Unit =
        runBlocking {
            val manager = graph.storageManager
            val other = createTempDirectory("Otra José Pérez").toFile()
            // StorageManager starts listening on the app scope; in the app it exists long before anyone changes the folder
            delay(500)

            graph.storagePreferences.baseStorageDirectory.set(other.toURI().toString())
            try {
                withTimeout(5.seconds) {
                    while (!listOf("local", "downloads", "autobackup").all { File(other, it).isDirectory }) delay(50)
                }
                File(manager.getLocalSourceDirectory()!!.filePath!!) shouldBe File(other, "local")
            } finally {
                graph.storagePreferences.baseStorageDirectory.set(
                    graph.storagePreferences.baseStorageDirectory.defaultValue(),
                )
                // StorageManager follows the change asynchronously: back on the default folder before the next test
                val defaultLocal = graph.appDirs.storage.resolve("local").toFile()
                withTimeout(5.seconds) {
                    while (File(manager.getLocalSourceDirectory()!!.filePath!!) != defaultLocal) delay(50)
                }
            }
        }
}
