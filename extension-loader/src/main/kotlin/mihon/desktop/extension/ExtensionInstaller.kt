package mihon.desktop.extension

import logcat.LogPriority
import mihon.desktop.extension.vendor.Dex2Jar
import tachiyomi.core.common.util.system.logcat
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.copyTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name

class SignatureMismatchException(pkgName: String) :
    Exception("The update for $pkgName is signed with a different key than the installed version")

/**
 * Keeps `<pkg>-<versionCode>.apk` and `.jar` side by side in [dir]. The apk stays because it is the signed
 * artifact: it is verified again on every start.
 */
class ExtensionInstaller(
    private val dir: Path,
    private val inspect: (Path) -> ApkInfo = ApkInspector::inspect,
    private val convert: (Path, Path) -> Unit = Dex2Jar::convert,
) {
    fun install(downloadedApk: Path): InstalledExtension {
        val info = inspect(downloadedApk)
        val current = installed().firstOrNull { it.info.pkgName == info.pkgName }
        if (current != null && !info.signatures.containsAll(current.info.signatures)) {
            throw SignatureMismatchException(info.pkgName)
        }

        val base = "${info.pkgName}-${info.versionCode}"
        val apk = dir.resolve("$base.apk")
        val jar = dir.resolve("$base.jar")
        val tmpJar = dir.resolve("$base.jar.tmp")
        if (current?.jar == jar) SourceInstantiator.release(jar)
        try {
            convert(downloadedApk, tmpJar)
            tmpJar.moveTo(jar, ATOMIC_MOVE, REPLACE_EXISTING)
            downloadedApk.copyTo(apk, REPLACE_EXISTING)
        } catch (e: Throwable) {
            tmpJar.deleteIfExists()
            if (current?.jar != jar) jar.deleteIfExists()
            throw e
        }

        if (current != null && current.info.versionCode != info.versionCode) deleteQuietly(current)
        return InstalledExtension(apk, jar, info)
    }

    /** Newest complete version of each package. Older or incomplete leftovers are deleted when possible. */
    fun installed(): List<InstalledExtension> {
        if (!dir.exists()) return emptyList()
        val versions = dir.listDirectoryEntries("*.apk").mapNotNull { apk ->
            val base = apk.name.removeSuffix(".apk")
            val separator = base.lastIndexOf('-')
            val versionCode = base.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
            Triple(base.substring(0, separator), versionCode, apk)
        }
        return versions.groupBy { it.first }.mapNotNull { (_, list) ->
            val sorted = list.sortedByDescending { it.second }
            sorted.drop(1).forEach { (_, _, apk) -> deleteQuietly(apk, apk.resolveSibling(apk.name.removeSuffix(".apk") + ".jar")) }
            val (_, _, apk) = sorted.first()
            val jar = apk.resolveSibling(apk.name.removeSuffix(".apk") + ".jar")
            if (!jar.exists()) return@mapNotNull null
            runCatching { InstalledExtension(apk, jar, inspect(apk)) }
                .onFailure { logcat(LogPriority.ERROR, it) { "Skipping invalid extension ${apk.name}" } }
                .getOrNull()
        }
    }

    fun uninstall(pkgName: String) {
        installed().filter { it.info.pkgName == pkgName }.forEach { deleteQuietly(it) }
    }

    private fun deleteQuietly(ext: InstalledExtension) {
        SourceInstantiator.release(ext.jar)
        deleteQuietly(ext.apk, ext.jar)
    }

    // A jar still mapped by a class loader can't be deleted on Windows; the next installed() call retries
    private fun deleteQuietly(vararg paths: Path) = paths.forEach { path ->
        runCatching { path.deleteIfExists() }
            .onFailure { logcat(LogPriority.WARN, it) { "Could not delete $path yet" } }
    }
}
