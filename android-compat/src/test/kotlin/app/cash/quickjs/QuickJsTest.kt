package app.cash.quickjs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

class QuickJsTest {
    interface Api {
        fun greet(name: String, times: Int): String
    }

    private val api = object : Api {
        override fun greet(name: String, times: Int) = "hola $name".repeat(times)
    }

    @Test
    fun `scripts give back their values and call the interfaces they're handed`() {
        QuickJs.create().use { js ->
            js.evaluate("1 + 2") shouldBe 3
            js.evaluate("'a' + 'b'") shouldBe "ab"
            (js.evaluate("['x', 'y']") as Array<*>).toList() shouldBe listOf("x", "y")
            js.set("api", Api::class.java, api)
            js.evaluate("api.greet('José', 2)") shouldBe "hola Joséhola José"
        }
    }

    // The script often comes from the source's site: through a host object's getClass() it would reach the class
    // loader, and from there any class
    @Test
    fun `a script reaches only the methods of the interface it's handed`() {
        QuickJs.create().use { js ->
            js.set("api", Api::class.java, api)
            shouldThrow<QuickJsException> { js.evaluate("api.getClass()") }
            shouldThrow<QuickJsException> { js.evaluate("api.hashCode()") }
            js.evaluate("typeof Java") shouldBe "undefined"
        }
    }

    @Test
    fun `a script that never ends is stopped, and the engine still works afterwards`() {
        QuickJs(Duration.ofMillis(500)).use { js ->
            shouldThrow<QuickJsException> { js.evaluate("while (true) {}") }
            js.evaluate("40 + 2") shouldBe 42
        }
    }
}
