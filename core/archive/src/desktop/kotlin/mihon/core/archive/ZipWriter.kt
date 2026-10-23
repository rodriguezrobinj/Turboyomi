package mihon.core.archive

import android.content.Context
import com.hippo.unifile.UniFile
import java.io.Closeable
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Desktop copy of Mihon's ZipWriter: the same API, written with java.util.zip instead of libarchive. Entries are
 * STORED, as Mihon writes them: the pages are already compressed images.
 */
class ZipWriter(@Suppress("UNUSED_PARAMETER") context: Context?, file: UniFile) : Closeable {
    private val zip = ZipOutputStream(file.openOutputStream())

    fun write(file: UniFile) {
        val bytes = file.openInputStream().use { it.readBytes() }
        val entry = ZipEntry(file.name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
            time = file.lastModified()
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    override fun close() {
        zip.close()
    }
}
