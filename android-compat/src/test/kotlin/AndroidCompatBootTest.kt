import android.app.Application
import android.content.Context
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatformTools
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.androidcompat.androidCompatModule
import xyz.nulldev.androidcompat.io.sharedprefs.JavaSharedPreferences
import xyz.nulldev.ts.config.ApplicationRootDir
import xyz.nulldev.ts.config.configManagerModule
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AndroidCompatBootTest {
    private lateinit var context: Context

    @BeforeAll
    fun boot() {
        val root = createTempDirectory("José Pérez compat")
        System.setProperty("suwayomi.tachidesk.config.server.rootDir", root.toString())
        startKoin { modules(androidCompatModule(), configManagerModule()) }
        AndroidCompatInitializer().init()
        AndroidCompat().startApp(object : Application() {})
        context = KoinPlatformTools.defaultContext().get().get()
    }

    @AfterAll
    fun shutdown() = stopKoin()

    @Test
    fun `shared preferences round-trip to disk`() {
        val prefs = context.getSharedPreferences("source_123", Context.MODE_PRIVATE)
        prefs.edit().putString("quality", "high").commit()

        context.getSharedPreferences("source_123", Context.MODE_PRIVATE)
            .getString("quality", null) shouldBe "high"
    }

    @Test
    fun `listeners registered from many threads all hear a change`() {
        val prefs = context.getSharedPreferences("listeners_race", Context.MODE_PRIVATE)
        val heard = AtomicInteger()
        val start = CountDownLatch(1)
        val threads = (1..8).map {
            thread {
                start.await()
                repeat(5_000) {
                    prefs.registerOnSharedPreferenceChangeListener { _, _ -> heard.incrementAndGet() }
                }
            }
        }
        start.countDown()
        threads.forEach { it.join() }

        prefs.edit().putString("key", "value").commit()

        heard.get() shouldBe 8 * 5_000
    }

    @Test
    fun `getAll gives each value its type, as on Android, also after reloading from disk`() {
        val prefs = context.getSharedPreferences("typed_all", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("s", "12")
            .putInt("i", 12)
            .putLong("l", 12L)
            .putFloat("f", 1.5f)
            .putBoolean("b", true)
            .putStringSet("set", setOf("a", "b"))
            .commit()
        val expected = mapOf("s" to "12", "i" to 12, "l" to 12L, "f" to 1.5f, "b" to true, "set" to setOf("a", "b"))

        prefs.all shouldBe expected
        // A new instance reads the file the first one wrote
        JavaSharedPreferences("typed_all").all shouldBe expected
    }

    @Test
    fun `a removed key leaves no trace in getAll`() {
        val prefs = context.getSharedPreferences("typed_remove", Context.MODE_PRIVATE)
        prefs.edit().putInt("i", 1).putStringSet("set", setOf("a")).putString("kept", "x").commit()

        prefs.edit().remove("i").remove("set").commit()

        prefs.all shouldBe mapOf("kept" to "x")
    }

    @Test
    fun `a key saved before the types were kept still reads, as text`() {
        val file = Path(ApplicationRootDir, "settings", "legacy_untyped.xml")
        file.createParentDirectories()
        val old = Properties().apply { setProperty("old", "x") }
        file.outputStream().use { old.storeToXML(it, null) }

        JavaSharedPreferences("legacy_untyped").all shouldBe mapOf("old" to "x")
    }
}
