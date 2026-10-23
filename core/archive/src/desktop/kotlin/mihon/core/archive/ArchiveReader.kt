package mihon.core.archive

import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.StringDesc
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import tachiyomi.i18n.MR
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Desktop copy of Mihon's ArchiveReader: the same API, read with 7-Zip (7-Zip-JBinding) instead of libarchive over
 * mmap. Entry names use '/' whatever the archive stored, as libarchive gives them. A 7-Zip archive isn't thread-safe,
 * so every extraction holds this reader's lock; an entry is extracted to memory only when it's asked for.
 */
class ArchiveReader(file: File) : Closeable {
    private val input = RandomAccessFile(file, "r")
    private val archive: IInArchive = try {
        SevenZip.openInArchive(null, RandomAccessFileInStream(input))
    } catch (e: SevenZipNativeInitializationException) {
        input.close()
        throw IOException(StringDesc.Resource(MR.strings.desktop_archive_reader_unavailable).localized(), e)
    } catch (e: LinkageError) {
        input.close()
        throw IOException(StringDesc.Resource(MR.strings.desktop_archive_reader_unavailable).localized(), e)
    } catch (e: SevenZipException) {
        input.close()
        throw IOException("Can't open ${file.name}: ${e.message}", e)
    }

    private val lock = Any()
    private val items: List<ISimpleInArchiveItem> = archive.simpleInterface.archiveItems.toList()
    private val entries: List<ArchiveEntry> = items.map { ArchiveEntry(it.path.replace('\\', '/'), !it.isFolder) }

    fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T = block(entries.asSequence())

    fun getInputStream(entryName: String): InputStream? {
        val index = entries.indexOfFirst { it.isFile && it.name == entryName }
        if (index < 0) return null
        val bytes = ByteArrayOutputStream()
        val result = synchronized(lock) {
            items[index].extractSlow { data ->
                bytes.write(data)
                data.size
            }
        }
        if (result != ExtractOperationResult.OK) throw IOException("Can't extract $entryName: $result")
        return ByteArrayInputStream(bytes.toByteArray())
    }

    override fun close() {
        synchronized(lock) {
            archive.close()
            input.close()
        }
    }
}
