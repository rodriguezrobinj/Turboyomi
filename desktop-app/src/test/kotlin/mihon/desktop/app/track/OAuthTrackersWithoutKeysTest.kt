package mihon.desktop.app.track

import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

class OAuthTrackersWithoutKeysTest {
    private val graph: TestAppGraph = startTestApp()

    @Test
    fun `without keys no OAuth tracker is registered`() {
        listOf(1L, 2L, 4L, 5L, 10L, 11L, 12L).forEach { graph.trackerManager.get(it) shouldBe null }
    }
}
