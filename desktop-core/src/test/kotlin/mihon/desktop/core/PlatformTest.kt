package mihon.desktop.core

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import mihon.desktop.core.cookies.PersistentCookieStore
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.CookieHandler
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformTest {
    private lateinit var dirs: AppDirs
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        val root = createTempDirectory("José Pérez platform")
        dirs = AppDirs(root.resolve("data"), root.resolve("local"))
        graph = Platform.start(dirs)
    }

    @Test
    fun `database opens in the app data folder`(): Unit = runBlocking {
        graph.extensionStoreRepository.getAll() shouldBe emptyList()
        dirs.db.resolve("app.db").exists() shouldBe true
    }

    @Test
    fun `extensions resolve the same objects through Injekt`() {
        Injekt.get<NetworkHelper>() shouldBe graph.networkHelper
        Injekt.get<Context>() shouldBe graph.context
        Injekt.get<Application>().packageName.shouldNotBeBlank()
    }

    @Test
    fun `source preferences are stored under the app data folder`(): Unit = runBlocking {
        graph.context.getSharedPreferences("source_1", Context.MODE_PRIVATE).edit().putInt("x", 1).commit()
        eventually(5.seconds) { dirs.settings.resolve("source_1.xml").exists() shouldBe true }
    }

    @Test
    fun `app info reports the image formats the v1 reader supports`() {
        eu.kanade.tachiyomi.AppInfo.getSupportedImageMimeTypes() shouldBe
            listOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")
    }

    @Test
    fun `the app's cookies are kept in the persistent store`() {
        (CookieHandler.getDefault() as java.net.CookieManager).cookieStore
            .shouldBeInstanceOf<PersistentCookieStore>()
    }
}
