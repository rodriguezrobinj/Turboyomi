package com.hippo.unifile

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Desktop replacement for the Android-only UniFile library: same API surface Mihon uses, backed by [File].
 * Only `file:` uris exist on Windows, so SAF document uris resolve to null.
 */
class UniFile private constructor(private val file: File) {

    val name: String? get() = file.name

    // desktop: java's file: uri (percent-encoded, forward slashes) parses back to the same path; Uri.fromFile's
    // string keeps Windows backslashes and doesn't
    val uri: Uri get() = Uri.parse(file.toURI().toString())
    val filePath: String? get() = file.absolutePath
    val parentFile: UniFile? get() = file.parentFile?.let(::UniFile)
    val isDirectory: Boolean get() = file.isDirectory
    val isFile: Boolean get() = file.isFile
    val type: String? get() = null

    fun exists(): Boolean = file.exists()
    fun canRead(): Boolean = file.canRead()
    fun canWrite(): Boolean = file.canWrite()
    fun length(): Long = file.length()
    fun lastModified(): Long = file.lastModified()

    fun listFiles(): Array<UniFile>? = file.listFiles()?.map(::UniFile)?.toTypedArray()

    fun listFiles(filter: (UniFile) -> Boolean): Array<UniFile>? = listFiles()?.filter(filter)?.toTypedArray()

    fun findFile(displayName: String): UniFile? = File(file, displayName).takeIf { it.exists() }?.let(::UniFile)

    fun subFile(displayName: String): UniFile = UniFile(File(file, displayName))

    fun createDirectory(displayName: String?): UniFile? {
        val dir = File(file, displayName ?: return null)
        return if (dir.isDirectory || dir.mkdirs()) UniFile(dir) else null
    }

    fun createFile(displayName: String?): UniFile? {
        val child = File(file, displayName ?: return null)
        return if (child.isFile || child.createNewFile()) UniFile(child) else null
    }

    fun ensureDir(): Boolean = file.isDirectory || file.mkdirs()

    fun ensureFile(): Boolean =
        file.isFile || (file.parentFile?.let { it.isDirectory || it.mkdirs() } != false && file.createNewFile())

    fun delete(): Boolean = file.deleteRecursively()

    fun renameTo(displayName: String): Boolean = file.renameTo(File(file.parentFile, displayName))

    fun openInputStream(): InputStream = file.inputStream()

    fun openOutputStream(): OutputStream = file.outputStream()

    fun openOutputStream(append: Boolean): OutputStream = FileOutputStream(file, append)

    override fun equals(other: Any?): Boolean = other is UniFile && other.file == file

    override fun hashCode(): Int = file.hashCode()

    override fun toString(): String = file.toString()

    companion object {
        @JvmStatic
        fun fromFile(file: File?): UniFile? = file?.let(::UniFile)

        @JvmStatic
        fun fromUri(context: Context?, uri: Uri?): UniFile? =
            uri?.takeIf { it.scheme == "file" }?.path?.let { UniFile(File(it)) }
    }
}
