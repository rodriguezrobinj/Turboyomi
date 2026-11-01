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
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/**
 * Files dropped on the window, copied into the local source (spec 12 §5): a folder of chapters is a manga, a folder of
 * pages or a chapter file a chapter of the manga named after it. Copies, and never overwrites: a file already there is
 * left as it is.
 */
object LocalDrop {
    /** The mangas that got something, and what was dropped but not added. */
    data class Result(val mangas: List<String>, val unsupported: List<String>)

    fun import(dropped: List<Path>, localDir: Path): Result {
        val local = localDir.createDirectories().toRealPath()
        val mangas = mutableListOf<String>()
        val unsupported = mutableListOf<String>()
        for (path in dropped) {
            when {
                // The storage folder or anything in the local source: the copy would land inside what it copies
                path.isDirectory() && path.toRealPath().let { local.startsWith(it) || it.startsWith(local) } ->
                    unsupported += path.name
                path.isDirectory() -> {
                    val chapters = path.listDirectoryEntries().any { it.isDirectory() || isChapterFile(it) }
                    // A folder of pages alone would be a manga without chapters: LocalSource skips loose images
                    copyFolder(
                        path,
                        if (chapters) local.resolve(path.name) else local.resolve(path.name).resolve(path.name),
                    )
                    mangas += path.name
                }
                isChapterFile(path) -> {
                    val manga = local.resolve(path.nameWithoutExtension).createDirectories()
                    copyNew(path, manga.resolve(path.name))
                    mangas += path.nameWithoutExtension
                }
                else -> unsupported += path.name
            }
        }
        return Result(mangas.distinct(), unsupported)
    }

    /** What LocalSource reads as a chapter besides a folder (its getChapterList). */
    private fun isChapterFile(file: Path) =
        UniFile.fromFile(file.toFile())?.let(Archive::isSupported) == true || file.extension.equals("epub", true)

    @OptIn(ExperimentalPathApi::class)
    private fun copyFolder(source: Path, target: Path) {
        source.copyToRecursively(target, followLinks = false) { from, to ->
            if (from.isDirectory()) to.createDirectories() else copyNew(from, to)
            CopyActionResult.CONTINUE
        }
    }

    /**
     * Copies to a `.part` file first, which LocalSource ignores: a copy cut short (the app closed, a full disk) leaves
     * no broken chapter that the next drop would skip as already there.
     */
    private fun copyNew(source: Path, target: Path) {
        if (target.exists()) return
        val part = target.resolveSibling(target.name + ".part")
        source.copyTo(part, overwrite = true)
        part.moveTo(target)
    }
}
