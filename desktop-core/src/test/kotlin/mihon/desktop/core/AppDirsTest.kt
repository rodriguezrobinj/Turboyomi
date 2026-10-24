package mihon.desktop.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isDirectory

class AppDirsTest {
    @Test
    fun `uses roaming and local app data on Windows`() {
        val dirs = AppDirs.fromEnvironment(
            mapOf(
                "APPDATA" to "C:\\Users\\José Pérez\\AppData\\Roaming",
                "LOCALAPPDATA" to "C:\\Users\\José Pérez\\AppData\\Local",
            ),
        )
        dirs.data shouldBe Path("C:\\Users\\José Pérez\\AppData\\Roaming", AppDirs.APP_NAME)
        dirs.extensions shouldBe Path("C:\\Users\\José Pérez\\AppData\\Local", AppDirs.APP_NAME, "extensions")
    }

    @Test
    fun `home override puts everything under one folder`() {
        val dirs = AppDirs.fromEnvironment(mapOf("TURBOYOMI_HOME" to "D:\\Portable"))
        dirs.data shouldBe Path("D:\\Portable", "data")
        dirs.local shouldBe Path("D:\\Portable", "local")
    }

    @Test
    fun `fails clearly without app data variables`() {
        shouldThrow<IllegalStateException> { AppDirs.fromEnvironment(emptyMap()) }
    }

    @Test
    fun `createAll creates every directory`() {
        val root = createTempDirectory("José Pérez")
        val dirs = AppDirs(root.resolve("data"), root.resolve("local"))
        dirs.createAll()
        listOf(dirs.db, dirs.settings, dirs.cache, dirs.extensions, dirs.logs).forEach {
            it.isDirectory() shouldBe
                true
        }
    }

    @Test
    fun `the storage folder is Documents, wherever Windows has it, plus the app name`() {
        val dirs = AppDirs.fromEnvironment(
            mapOf(
                "APPDATA" to "C:\\Users\\José Pérez\\AppData\\Roaming",
                "LOCALAPPDATA" to "C:\\Users\\José Pérez\\AppData\\Local",
            ),
            documents = { Path("C:\\Users\\José Pérez\\OneDrive\\Documentos") },
        )
        dirs.storage shouldBe Path("C:\\Users\\José Pérez\\OneDrive\\Documentos", AppDirs.APP_NAME)
    }

    @Test
    fun `with the home override the storage folder stays under the home folder`() {
        val dirs = AppDirs.fromEnvironment(mapOf("TURBOYOMI_HOME" to "D:\\Portable"))
        dirs.storage shouldBe Path("D:\\Portable", "storage")
    }

    @Test
    fun `the default storage folder is created only while it's the chosen one`() {
        val root = createTempDirectory("dirs José")
        val dirs = AppDirs(root.resolve("data"), root.resolve("local"))
        dirs.createAll()
        dirs.storage.isDirectory() shouldBe false

        dirs.createStorageIfChosen(root.resolve("otra").toUri().toString())
        dirs.storage.isDirectory() shouldBe false

        dirs.createStorageIfChosen(dirs.storage.toFile().toURI().toString())
        dirs.storage.isDirectory() shouldBe true
    }
}
