package mihon.desktop.app.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The native Windows file dialogs. */
object FileDialogs {
    fun saveFile(title: String, defaultName: String): File? = show(title, FileDialog.SAVE) { file = defaultName }

    fun openImage(title: String): File? = show(title, FileDialog.LOAD) {
        setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS }
    }

    private fun show(title: String, mode: Int, setup: FileDialog.() -> Unit): File? {
        val dialog = FileDialog(null as Frame?, title, mode).apply(setup)
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
}
