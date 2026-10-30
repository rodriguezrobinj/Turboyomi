package mihon.desktop.core

import org.junit.jupiter.api.Test

class DeviceStateTest {
    @Test
    fun `reading the connection and the power goes through Windows without failing`() {
        // The real WinRT and kernel32 calls: a wrong IID or vtable slot throws here. Any answer passes
        println("Internet: ${DeviceState.internet()}, on AC power: ${DeviceState.onAcPower()}")
    }
}
