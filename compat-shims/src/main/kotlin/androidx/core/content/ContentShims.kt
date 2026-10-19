package androidx.core.content

import android.content.Context
import android.content.SharedPreferences
import java.io.File

inline fun SharedPreferences.edit(commit: Boolean = false, action: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    editor.action()
    if (commit) editor.commit() else editor.apply()
}

inline fun <reified T : Any> Context.getSystemService(): T? = getSystemService(T::class.java)

object ContextCompat {
    @JvmStatic
    fun getExternalFilesDirs(context: Context, type: String?): Array<File?> = context.getExternalFilesDirs(type)
}
