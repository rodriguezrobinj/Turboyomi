package mihon.desktop.app.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/** The native Windows file dialogs. */
object FileDialogs {
    fun saveFile(title: String, defaultName: String): File? =
        show(title, FileDialog.SAVE) { file = defaultName }?.let { withDefaultExtension(it, defaultName) }

    fun openImage(title: String): File? = show(title, FileDialog.LOAD) {
        setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS }
    }

    /** Windows' dialog ignores FilenameFilter, so the "*.tachibk" name is what filters there; the filter covers others. */
    fun openBackup(title: String): File? = show(title, FileDialog.LOAD) {
        file = "*.tachibk"
        setFilenameFilter { _, name -> name.endsWith(".tachibk", ignoreCase = true) }
    }

    /** AWT's FileDialog can't pick folders on Windows, so this one is Swing's chooser in folders-only mode. */
    fun chooseDirectory(title: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    private fun show(title: String, mode: Int, setup: FileDialog.() -> Unit): File? {
        val dialog = FileDialog(null as Frame?, title, mode).apply(setup)
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
}

/**
 * AWT's save dialog keeps a name typed without extension as it is: [chosen] gets [defaultName]'s extension then, so a
 * backup saved as "copia" is "copia.tachibk" and the open dialog's filter finds it.
 */
internal fun withDefaultExtension(chosen: File, defaultName: String): File {
    val extension = defaultName.substringAfterLast('.', "")
    if (extension.isEmpty() || chosen.name.endsWith(".$extension", ignoreCase = true)) return chosen
    return File(chosen.path + ".$extension")
}
