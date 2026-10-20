package mihon.desktop.app.image

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.manga.model.MangaCover
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopImageLoaderTest {
    private lateinit var graph: TestAppGraph

    @BeforeAll
    fun start() {
        graph = startTestApp()
    }

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(4, 6, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    @Test
    fun `a manga cover from a source is downloaded and decoded`(): Unit = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png())))
        server.start()

        val cover = MangaCover(
            mangaId = 1,
            sourceId = 1,
            isMangaFavorite = false,
            url = server.url("/cover.png").toString(),
            lastModified = null,
        )
        val result = DesktopImageLoader.create(graph).execute(
            ImageRequest.Builder(PlatformContext.INSTANCE).data(cover).build(),
        )

        result.shouldBeInstanceOf<SuccessResult>()
        server.shutdown()
    }
}
