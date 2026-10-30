package mihon.desktop.core.security

import io.kotest.matchers.ints.shouldBeInRange
import org.junit.jupiter.api.Test

class WindowsHelloTest {
    @Test
    fun `asking whether Hello is set up goes through WinRT and gets an answer`() {
        // The real COM calls (activation factory, async operation, results): a wrong IID or vtable slot throws here.
        // CI machines have no Hello, so any of UserConsentVerifierAvailability's values passes
        WindowsHello.availability() shouldBeInRange 0..4
    }
}
