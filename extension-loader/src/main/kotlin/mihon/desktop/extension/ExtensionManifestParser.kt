package mihon.desktop.extension

import mihon.domain.extension.model.ContentWarning

/** Reads the same manifest metadata Mihon's ExtensionLoader reads, with the same fallbacks. */
object ExtensionManifestParser {
    val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)

    const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    const val METADATA_NAME = "tachiyomix.name"
    const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"

    fun name(info: ApkInfo): String =
        info.metaData[METADATA_NAME]
            ?: info.label.substringAfter("Tachiyomi: ").takeIf { it.isNotBlank() }
            ?: info.pkgName

    fun contentWarning(metaData: Map<String, String>): ContentWarning = when {
        METADATA_CONTENT_WARNING in metaData -> when (metaData[METADATA_CONTENT_WARNING]?.toIntOrNull()) {
            1 -> ContentWarning.MIXED
            2 -> ContentWarning.NSFW
            else -> ContentWarning.SAFE
        }
        metaData[METADATA_NSFW]?.toIntOrNull() == 1 -> ContentWarning.NSFW
        else -> ContentWarning.SAFE
    }

    fun libVersion(info: ApkInfo): Double? =
        info.metaData[METADATA_EXTENSION_LIB]?.toDoubleOrNull()?.takeUnless { it == 0.0 }
            ?: info.versionName.substringBeforeLast('.').toDoubleOrNull()

    fun sourceClasses(info: ApkInfo): List<String> =
        info.metaData[METADATA_SOURCE_CLASS].orEmpty()
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) info.pkgName + it else it }

    fun factory(info: ApkInfo): String? = info.metaData[METADATA_SOURCE_FACTORY]
}
