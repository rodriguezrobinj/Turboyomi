package mihon.desktop.app.update

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AppRelease(
    val version: String,
    val notes: String,
    val page: String,
    val msiName: String,
    val msiUrl: String,
    val sumsUrl: String,
)

/** The latest Turboyomi release on GitHub (spec 6b §2). Mihon's ReleaseServiceImpl picks assets by Android ABI. */
@Inject
class AppReleases(
    private val network: NetworkHelper,
    private val json: Json,
) {
    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tag: String,
        @SerialName("body") val body: String? = null,
        @SerialName("html_url") val page: String,
        val assets: List<Asset>,
    )

    @Serializable
    private data class Asset(val name: String, @SerialName("browser_download_url") val url: String)

    /** Null when the release lacks its MSI or its checksums; network and GitHub errors are thrown. */
    suspend fun latest(apiBase: String = API_BASE): AppRelease? = withContext(Dispatchers.IO) {
        val release = with(json) {
            network.client.newCall(GET("$apiBase/repos/$REPO/releases/latest")).awaitSuccess()
                .parseAs<GithubRelease>()
        }
        val msi = release.assets.firstOrNull { it.name.endsWith(".msi") } ?: return@withContext null
        val sums = release.assets.firstOrNull { it.name == "SHA256SUMS" } ?: return@withContext null
        AppRelease(release.tag, release.body.orEmpty(), release.page, msi.name, msi.url, sums.url)
    }

    companion object {
        const val REPO = "rodriguezrobinj/turboyomi"
        const val API_BASE = "https://api.github.com"
    }
}
