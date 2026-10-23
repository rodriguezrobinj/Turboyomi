package tachiyomi.core.common.util.system

import java.io.InputStream

/**
 * Desktop copy of the parts of Mihon's ImageUtil that the local source and the page loaders use. Mihon tells image
 * types apart with Android's ImageDecoder; this reads the magic bytes. The rest arrives with what needs it.
 */
object ImageUtil {

    fun isImage(name: String?, openStream: (() -> InputStream)? = null): Boolean {
        if (name == null) return false

        val extension = name.substringAfterLast('.')
        return ImageType.entries.any { it.extension == extension } || openStream?.let { findImageType(it) } != null
    }

    fun findImageType(openStream: () -> InputStream): ImageType? {
        return openStream().use { findImageType(it) }
    }

    fun findImageType(stream: InputStream): ImageType? {
        val head = ByteArray(16)
        val read = stream.readNBytes(head, 0, head.size)
        fun at(offset: Int, vararg bytes: Int) =
            read >= offset + bytes.size && bytes.indices.all { head[offset + it] == bytes[it].toByte() }
        fun ascii(offset: Int, text: String) = at(offset, *text.map { it.code }.toIntArray())
        val brand = if (ascii(4, "ftyp") && read >= 12) String(head, 8, 4, Charsets.US_ASCII) else null

        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> ImageType.JPEG
            at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> ImageType.PNG
            ascii(0, "GIF8") -> ImageType.GIF
            ascii(0, "RIFF") && ascii(8, "WEBP") -> ImageType.WEBP
            brand == "avif" || brand == "avis" -> ImageType.AVIF
            brand in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1") -> ImageType.HEIF
            at(0, 0xFF, 0x0A) || at(0, 0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20, 0x0D, 0x0A, 0x87, 0x0A) ->
                ImageType.JXL
            at(0, 0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A, 0x87, 0x0A) -> ImageType.JP2
            else -> null
        }
    }

    fun getExtensionFromMimeType(mime: String?, openStream: () -> InputStream): String {
        val type = mime?.let { ImageType.entries.find { it.mime == mime } } ?: findImageType(openStream)
        return type?.extension ?: "jpg"
    }

    enum class ImageType(val mime: String, val extension: String) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),
        JXL("image/jxl", "jxl"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
        JP2("image/jp2", "jp2"),
        JPX("image/jpx", "jpx"),
    }
}
