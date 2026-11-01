package mihon.desktop.app.main

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ForwardHistoryTest {
    private val history = ForwardHistory<String>().apply { onStackChanged(listOf("home")) }

    @Test
    fun `back keeps the screen it closed, forward puts it back once`() {
        history.onStackChanged(listOf("home", "manga"))
        history.next() shouldBe null
        history.onStackChanged(listOf("home"))
        history.next() shouldBe "manga"
        history.onStackChanged(listOf("home", "manga"))
        history.next() shouldBe null
    }

    @Test
    fun `going back over several screens comes forward in the order they were opened`() {
        history.onStackChanged(listOf("home", "manga"))
        history.onStackChanged(listOf("home", "manga", "reader"))
        history.onStackChanged(listOf("home"))
        history.next() shouldBe "manga"
        history.onStackChanged(listOf("home", "manga"))
        history.next() shouldBe "reader"
    }

    @Test
    fun `opening another screen empties the forward history`() {
        history.onStackChanged(listOf("home", "manga"))
        history.onStackChanged(listOf("home"))
        history.onStackChanged(listOf("home", "settings"))
        history.next() shouldBe null
    }

    @Test
    fun `a replaced stack empties the forward history`() {
        history.onStackChanged(listOf("home", "manga"))
        history.onStackChanged(listOf("home"))
        history.onStackChanged(listOf("onboarding"))
        history.next() shouldBe null
    }
}
