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
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name

class InvalidExtensionException(message: String) : Exception(message)

class SignatureMismatchException(pkgName: String) :
    Exception("The update for $pkgName is signed with a different key than the installed version")

/**
 * Keeps `<pkg>-<versionCode>.apk` and `.jar` side by side in [dir]. The apk stays because it is the signed
 * artifact: it is verified again on every start.
 *
 * Not thread-safe: callers serialize install, installed and uninstall (DesktopExtensionManager holds a mutex).
 */
class ExtensionInstaller(
    private val dir: Path,
    private val inspect: (Path) -> ApkInfo = ApkInspector::inspect,
    private val convert: (Path, Path) -> Unit = { apk, jar ->
        Dex2Jar.convert(apk, jar)
        AndroidBytecodeFixer.fix(jar)
    },
) {
    /** Where the apk of this version lives once installed. */
    fun apkFor(pkgName: String, versionCode: Long): Path = dir.resolve("$pkgName-$versionCode.apk")

    fun install(downloadedApk: Path, expectedPkgName: String? = null): InstalledExtension {
        val info = inspect(downloadedApk)
        // The package name comes from a not yet trusted apk and becomes a file name: validate before any write
        if (!PACKAGE_NAME.matches(
                info.pkgName,
            )
        ) {
            throw InvalidExtensionException("Invalid package name: ${info.pkgName}")
        }
        if (expectedPkgName != null && info.pkgName != expectedPkgName) {
            throw InvalidExtensionException("Expected $expectedPkgName but the apk is ${info.pkgName}")
        }

        val current = installed().firstOrNull { it.info.pkgName == info.pkgName }
        if (current != null) {
            if (!info.signatures.containsAll(current.info.signatures)) throw SignatureMismatchException(info.pkgName)
            if (info.versionCode < current.info.versionCode) {
                throw InvalidExtensionException(
                    "${info.pkgName} ${info.versionCode} is older than the installed ${current.info.versionCode}",
                )
            }
        }

        val base = "${info.pkgName}-${info.versionCode}"
        val apk = apkFor(info.pkgName, info.versionCode)
        val jar = dir.resolve("$base.jar")
        val tmpApk = dir.resolve("$base.apk.tmp")
        val tmpJar = dir.resolve("$base.jar.tmp")
        check(apk.normalize().parent == dir.normalize()) { "Refusing to write outside $dir: $apk" }
        val replacesCurrent = current?.jar == jar
        try {
            convert(downloadedApk, tmpJar)
            downloadedApk.copyTo(tmpApk, REPLACE_EXISTING)
            // Same version reinstalled: close its jar only now that the new one is ready, so a failure keeps it usable
            if (replacesCurrent) SourceInstantiator.release(jar)
            tmpJar.moveTo(jar, ATOMIC_MOVE, REPLACE_EXISTING)
            tmpApk.moveTo(apk, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (e: Throwable) {
            tmpJar.deleteIfExists()
            tmpApk.deleteIfExists()
            if (!replacesCurrent) {
                jar.deleteIfExists()
                apk.deleteIfExists()
            }
            throw e
        }

        if (current != null && !replacesCurrent) deleteQuietly(current.jar, current.apk)
        return InstalledExtension(apk, jar, info)
    }

    /**
     * Newest version of each package that still verifies. Everything else in [dir] is deleted when possible:
     * older or broken versions, jars whose apk is gone and leftovers of interrupted installs.
     */
    fun installed(): List<InstalledExtension> {
        if (!dir.exists()) return emptyList()
        val kept = dir.listDirectoryEntries("*.apk")
            .mapNotNull(::versionOf)
            .groupBy { it.pkgName }
            .values
            .mapNotNull { versions ->
                versions.sortedByDescending { it.versionCode }.firstNotNullOfOrNull { version ->
                    if (!version.jar.exists()) return@firstNotNullOfOrNull null
                    runCatching { InstalledExtension(version.apk, version.jar, inspect(version.apk)) }
                        .onFailure {
                            logcat(LogPriority.ERROR, it) { "Skipping invalid extension ${version.apk.name}" }
                        }
                        .getOrNull()
                }
            }

        val keep = kept.flatMapTo(HashSet()) { listOf(it.apk, it.jar) }
        dir.listDirectoryEntries()
            .filter { it.isRegularFile() && it !in keep && LEFTOVER_SUFFIXES.any(it.name::endsWith) }
            .forEach { deleteQuietly(it) }
        return kept
    }

    fun uninstall(pkgName: String) {
        installed().filter { it.info.pkgName == pkgName }.forEach { deleteQuietly(it.jar, it.apk) }
    }

    private class Version(val pkgName: String, val versionCode: Long, val apk: Path) {
        val jar: Path get() = apk.resolveSibling(apk.name.removeSuffix(".apk") + ".jar")
    }

    private fun versionOf(apk: Path): Version? {
        val base = apk.name.removeSuffix(".apk")
        val separator = base.lastIndexOf('-')
        if (separator <= 0) return null
        val versionCode = base.substring(separator + 1).toLongOrNull() ?: return null
        return Version(base.substring(0, separator), versionCode, apk)
    }

    // Jar first: a jar Windows keeps locked is retried by the next installed() call, which sweeps orphan jars
    private fun deleteQuietly(vararg paths: Path) = paths.forEach { path ->
        if (path.name.endsWith(".jar")) SourceInstantiator.release(path)
        runCatching { path.deleteIfExists() }
            .onFailure { logcat(LogPriority.WARN, it) { "Could not delete $path yet" } }
    }

    private companion object {
        /** Android's package name grammar: at least two dot-separated identifiers, nothing a path could use. */
        val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        val LEFTOVER_SUFFIXES = listOf(".apk", ".jar", ".tmp", ".dex2jar-error.txt")
    }
}
