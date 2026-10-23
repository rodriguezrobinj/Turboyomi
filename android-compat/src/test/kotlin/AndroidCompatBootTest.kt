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
import xyz.nulldev.ts.config.configManagerModule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory

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
}
