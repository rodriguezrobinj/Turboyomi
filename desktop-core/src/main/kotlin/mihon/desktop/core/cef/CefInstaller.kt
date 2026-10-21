package mihon.desktop.core.cef

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

/**
 * The embedded browser's files (spec 2b §2, §4): JetBrains Runtime's JCEF build in [installDir], downloaded on demand.
 *
 * Ported from Suwayomi's CEFManager, with a fixed [url] instead of the GitHub API, the .zip read with java.util.zip
 * instead of the .tar.gz, a SHA-512 check, and an install that only exists once complete: it's built in a folder
 * next to [installDir] and renamed at the end.
 */
@OptIn(ExperimentalPathApi::class)
class CefInstaller(
    private val installDir: Path,
    private val url: String = CefRelease.URL,
    private val expectedVersion: String = CefRelease.JCEF_VERSION,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val removalMarker: Path get() = installDir.resolveSibling("${installDir.name}.remove")

    /** Whether the build the app expects is installed, and not waiting to be removed. */
    fun isInstalled(): Boolean =
        !isRemovalPending() && installedVersion()?.let { sameBuild(it, expectedVersion) } == true

    fun isRemovalPending(): Boolean = removalMarker.exists()

    /** The JCEF version the installed build declares; null when there's none. */
    fun installedVersion(): String? = releaseVersion(installDir)

    /** Bytes the download takes, for the consent dialog; null when the server doesn't say. */
    suspend fun downloadSize(): Long? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
            response.header("Content-Length")?.toLongOrNull()?.takeIf { response.isSuccessful }
        }
    }

    /** Downloads, checks and installs the build, reporting 0..1 to [onProgress]. Cancellable; nothing is left half-done. */
    suspend fun install(onProgress: (Float) -> Unit = {}): Unit = withContext(Dispatchers.IO) {
        val parent = installDir.toAbsolutePath().parent.createDirectories()
        val staging = createTempDirectory(parent, "${installDir.name}-install")
        try {
            val checksum = fetchChecksum()
            val archive = staging / "jbr.zip"
            if (!download(archive, onProgress).equals(checksum, ignoreCase = true)) {
                throw IOException("The browser download is damaged: its SHA-512 doesn't match")
            }
            val layout = flatten(extract(archive, staging / "extracted"), staging / "layout")
            val version = releaseVersion(layout)
            if (version == null || !sameBuild(version, expectedVersion)) {
                throw IOException("The downloaded browser is version $version; the app needs $expectedVersion")
            }
            installDir.deleteRecursively()
            layout.moveTo(installDir, StandardCopyOption.ATOMIC_MOVE)
            removalMarker.deleteIfExists()
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Removes the browser. While it runs Windows keeps its DLLs open, so then it goes at the next start. */
    fun uninstall(running: Boolean) {
        if (running) {
            if (!removalMarker.exists()) removalMarker.createFile()
        } else {
            installDir.deleteRecursively()
            removalMarker.deleteIfExists()
        }
    }

    /** At startup, before anything loads the browser: finishes a removal asked for while it ran. */
    fun applyPendingRemoval() {
        if (removalMarker.exists()) uninstall(running = false)
    }

    private fun fetchChecksum(): String =
        client.newCall(Request.Builder().url("$url.checksum").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} getting the browser's checksum")
            response.body.string().trim().substringBefore(' ')
        }

    /** Writes the archive to [target] and returns its SHA-512. */
    private suspend fun download(target: Path, onProgress: (Float) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-512")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading the browser")
            val total = response.body.contentLength()
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        }
        onProgress(1f)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extract(archive: Path, target: Path): Path {
        val root = target.createDirectories().toAbsolutePath().normalize()
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val file = root.resolve(entry.name).normalize()
                if (!file.startsWith(root)) throw IOException("The browser archive has an unsafe path: ${entry.name}")
                if (entry.isDirectory) {
                    file.createDirectories()
                } else {
                    file.parent.createDirectories()
                    file.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        return root
    }

    /** JBR's folders to the layout JCefAppConfig loads: bin's files and lib's contents side by side, plus `release`. */
    private fun flatten(extracted: Path, layout: Path): Path {
        val jbr = extracted.listDirectoryEntries().firstOrNull { (it / "lib").isDirectory() }
            ?: throw IOException("The browser archive doesn't have the expected folders")
        layout.createDirectories()
        (jbr / "lib").listDirectoryEntries().forEach { it.moveTo(layout / it.name) }
        (jbr / "bin").takeIf { it.isDirectory() }?.listDirectoryEntries()?.forEach { it.moveTo(layout / it.name) }
        (jbr / "release").takeIf { it.exists() }?.moveTo(layout / "release")
        return layout
    }
}

private const val BUFFER_SIZE = 1 shl 16

/** The JCEF version a `release` file declares (`JCEF_VERSION_DETAILED`), without its quotes. */
private fun releaseVersion(dir: Path): String? = runCatching {
    (dir / "release").readLines().firstNotNullOfOrNull { line ->
        line.substringAfter("JCEF_VERSION_DETAILED=", "").trim().trim('"').takeIf { it.isNotEmpty() }
    }
}.getOrNull()

/** The same native build: the part before `-chromium` (JCEF's version and commit), as Suwayomi compares them. */
private fun sameBuild(installed: String, expected: String) =
    installed.substringBefore("-chromium") == expected.substringBefore("-chromium")
