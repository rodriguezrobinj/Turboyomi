package mihon.desktop.extension

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import logcat.LogPriority
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.system.logcat
import java.nio.file.Path

data class InstalledExtension(val apk: Path, val jar: Path, val info: ApkInfo)

/** Desktop counterpart of Mihon's ExtensionLoader.loadExtension: same checks, same order, same reasons. */
class DesktopExtensionLoader(
    private val trust: ExtensionTrust,
    private val instantiate: (InstalledExtension, List<String>) -> List<Source> = { ext, classNames ->
        SourceInstantiator.instantiate(ext.jar, classNames, ext.apk)
    },
) {
    fun load(installed: InstalledExtension, stores: List<ExtensionStore>): Extension.Installed {
        val info = installed.info
        val name = ExtensionManifestParser.name(info)
        val contentWarning = ExtensionManifestParser.contentWarning(info.metaData)
        val store = stores.firstOrNull { it.signingKey in info.signatures }

        fun notLoaded(reason: Extension.NotLoaded.Reason, libVersion: Double? = null) = Extension.NotLoaded(
            name = name,
            pkgName = info.pkgName,
            versionName = info.versionName,
            versionCode = info.versionCode,
            isShared = false,
            contentWarning = contentWarning,
            signatures = info.signatures,
            libVersion = libVersion,
            store = store,
            reason = reason,
        )

        if (info.versionName.isEmpty()) return notLoaded(Extension.NotLoaded.Reason.Malformed)

        val libVersion = ExtensionManifestParser.libVersion(info)
        if (libVersion == null || libVersion !in ExtensionManifestParser.SUPPORTED_LIB_VERSIONS) {
            return notLoaded(Extension.NotLoaded.Reason.UnsupportedLibVersion, libVersion)
        }

        if (info.signatures.isEmpty()) return notLoaded(Extension.NotLoaded.Reason.Unsigned, libVersion)
        if (!trust.isTrusted(info.pkgName, info.versionCode, info.signatures, stores)) {
            return notLoaded(Extension.NotLoaded.Reason.Untrusted(info.signatures.last()), libVersion)
        }

        val classNames = ExtensionManifestParser.sourceClasses(info)
        if (classNames.isEmpty()) return notLoaded(Extension.NotLoaded.Reason.Malformed, libVersion)

        val sources = try {
            instantiate(installed, classNames)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $name (${info.pkgName})" }
            val root = generateSequence(e) { it.cause }.last()
            return notLoaded(
                Extension.NotLoaded.Reason.Failed(root.message ?: root.toString(), e.stackTraceToString()),
                libVersion,
            )
        }

        val langs = sources.map { it.lang }.toSet()
        return Extension.Loaded(
            name = name,
            pkgName = info.pkgName,
            versionName = info.versionName,
            versionCode = info.versionCode,
            libVersion = libVersion,
            lang = when (langs.size) {
                0 -> ""
                1 -> langs.first()
                else -> "all"
            },
            contentWarning = contentWarning,
            isShared = false,
            signatures = info.signatures,
            pkgFactory = ExtensionManifestParser.factory(info),
            sources = sources,
            store = store,
        )
    }
}
