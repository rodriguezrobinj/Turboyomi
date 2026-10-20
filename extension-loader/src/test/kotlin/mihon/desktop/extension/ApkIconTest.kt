package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import net.dongliu.apk.parser.bean.Icon
import org.junit.jupiter.api.Test

class ApkIconTest {
    @Test
    fun `the densest bitmap launcher icon wins`() {
        val icons = listOf(
            Icon("res/mipmap-mdpi-v4/ic_launcher.png", 160, byteArrayOf(1)),
            Icon("res/mipmap-xxxhdpi-v4/ic_launcher.png", 640, byteArrayOf(4)),
            Icon("res/mipmap-anydpi-v26/ic_launcher.xml", 65534, byteArrayOf(9)),
            Icon("res/mipmap-xhdpi-v4/ic_launcher.png", 320, null),
        )
        ApkInspector.pickIcon(icons)?.toList() shouldBe listOf<Byte>(4)
    }

    @Test
    fun `an apk with only an adaptive icon has no bitmap to show`() {
        ApkInspector.pickIcon(listOf(Icon("res/mipmap-anydpi-v26/ic_launcher.xml", 65534, byteArrayOf(9)))) shouldBe
            null
    }
}
