package mihon.desktop.app.local

import com.hippo.unifile.UniFile
import tachiyomi.source.local.io.Archive
import java.nio.file.Path
import kotlin.io.path.CopyActionResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/**
 * Files dropped on the window, copied into the local source (spec 12 §5): a folder is a manga, a chapter archive a
 * chapter of the manga named after it. Copies, and never overwrites: a file already there is left as it is.
 */
object LocalDrop {
    /** The mangas that got something, and the dropped files that aren't a folder or a chapter archive. */
    data class Result(val mangas: List<String>, val unsupported: List<String>)

    @OptIn(ExperimentalPathApi::class)
    fun import(dropped: List<Path>, localDir: Path): Result {
        val mangas = mutableListOf<String>()
        val unsupported = mutableListOf<String>()
        for (path in dropped) {
            when {
                path.isDirectory() -> {
                    path.copyToRecursively(localDir.resolve(path.name), followLinks = false) { source, target ->
                        if (source.isDirectory()) {
                            target.createDirectories()
                        } else if (!target.exists()) {
                            source.copyTo(target)
                        }
                        CopyActionResult.CONTINUE
                    }
                    mangas += path.name
                }
                UniFile.fromFile(path.toFile())?.let(Archive::isSupported) == true -> {
                    val manga = localDir.resolve(path.nameWithoutExtension).createDirectories()
                    val target = manga.resolve(path.name)
                    if (!target.exists()) path.copyTo(target)
                    mangas += path.nameWithoutExtension
                }
                else -> unsupported += path.name
            }
        }
        return Result(mangas.distinct(), unsupported)
    }
}
