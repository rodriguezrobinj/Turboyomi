package mihon.core.archive

import android.content.Context
import com.hippo.unifile.UniFile
import java.io.File

// desktop: no SAF file descriptors; on Windows an archive is always a file on disk

@Suppress("UnusedReceiverParameter")
fun UniFile.archiveReader(context: Context) = ArchiveReader(File(filePath ?: error("Not a file on disk: $uri")))

fun UniFile.epubReader(context: Context) = EpubReader(archiveReader(context))
