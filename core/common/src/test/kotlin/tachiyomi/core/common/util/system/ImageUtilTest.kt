package tachiyomi.core.common.util.system

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ImageUtilTest {
    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    @Test
    fun `a known extension is an image without opening it`() {
        ImageUtil.isImage("01.png") shouldBe true
        ImageUtil.isImage("nota.txt") shouldBe false
        ImageUtil.isImage(null) shouldBe false
    }

    @Test
    fun `an unknown or upper-case extension is told by its first bytes`() {
        ImageUtil.isImage("03.JPG") { bytes(0xFF, 0xD8, 0xFF, 0xE0).inputStream() } shouldBe true
        ImageUtil.isImage("03.JPG") { "hola".byteInputStream() } shouldBe false
    }

    @Test
    fun `the first bytes say which image type it is`() {
        ImageUtil.findImageType(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).inputStream()) shouldBe
            ImageUtil.ImageType.PNG
        ImageUtil.findImageType("GIF89a".byteInputStream()) shouldBe ImageUtil.ImageType.GIF
        ImageUtil.findImageType("RIFF0000WEBPVP8 ".byteInputStream()) shouldBe ImageUtil.ImageType.WEBP
        ImageUtil.findImageType((bytes(0, 0, 0, 0x1C) + "ftypavif".toByteArray()).inputStream()) shouldBe
            ImageUtil.ImageType.AVIF
        ImageUtil.findImageType(ByteArray(0).inputStream()) shouldBe null
    }
}
