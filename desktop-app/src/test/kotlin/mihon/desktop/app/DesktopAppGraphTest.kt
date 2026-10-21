package mihon.desktop.app

import android.content.Context
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.runBlocking
import mihon.app.di.MihonViewModelFactory
import mihon.app.di.appGraph
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopAppGraphTest {
    private lateinit var graph: TestAppGraph

    @BeforeAll
    fun start() {
        graph = startTestApp()
    }

    @Test
    fun `the leaf graph replaces the source manager and keeps the platform bindings`(): Unit = runBlocking {
        graph.sourceManager.getAll() shouldBe listOf(TestSources.fake, TestSources.http)
        graph.extensionStoreRepository.getAll() shouldBe emptyList()
    }

    @Test
    fun `ported code reaches the same graph through the context`() {
        Injekt.get<Context>().appGraph shouldBeSameInstanceAs graph
    }

    @Test
    fun `the view model factory is Mihon's, fed by this module's contributions`() {
        graph.metroViewModelFactory.shouldBeInstanceOf<MihonViewModelFactory>()
    }
}
