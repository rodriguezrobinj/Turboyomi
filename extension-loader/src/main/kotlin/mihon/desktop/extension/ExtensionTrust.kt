package mihon.desktop.extension

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getAndSet

/** Mihon's trust rules: signed by a store's key, or explicitly trusted by the user for one exact version. */
@Inject
@SingleIn(AppScope::class)
class ExtensionTrust(preferenceStore: PreferenceStore) {

    // Same key as Mihon's SourcePreferences.trustedExtensions, so backups carry it over
    private val trusted = preferenceStore.getStringSet(Preference.appStateKey("trusted_extensions"), emptySet())

    fun isTrusted(pkgName: String, versionCode: Long, fingerprints: List<String>, stores: List<ExtensionStore>): Boolean {
        if (fingerprints.isEmpty()) return false
        val storeKeys = stores.mapTo(HashSet()) { it.signingKey }
        return fingerprints.any { it in storeKeys } || "$pkgName:$versionCode:${fingerprints.last()}" in trusted.get()
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        trusted.getAndSet { set ->
            set.filterNot { it.startsWith("$pkgName:") }.toSet() + "$pkgName:$versionCode:$signatureHash"
        }
    }
}
