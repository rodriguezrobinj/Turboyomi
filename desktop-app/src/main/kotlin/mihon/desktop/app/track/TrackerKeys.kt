package mihon.desktop.app.track

import java.util.Properties

/**
 * The client ids (and secrets where a service needs one) registered for this app, from tracker-keys.properties: kept
 * out of git, packed as a resource at build time (spec 5b-2 §1). A service without its key isn't registered.
 */
object TrackerKeys {
    data class Keys(val clientId: String, val secret: String? = null)

    /** Tests' keys; tests also set mangareader.tracker-keys=none so a developer's own file never leaks in. */
    @Volatile
    internal var override: Map<String, Keys>? = null

    private val loaded: Map<String, Keys> by lazy {
        if (System.getProperty("mangareader.tracker-keys") == "none") return@lazy emptyMap()
        val properties = Properties()
        TrackerKeys::class.java.getResourceAsStream("/tracker-keys.properties")?.use { properties.load(it) }
        SERVICES.mapNotNull { service ->
            val id = properties.getProperty("$service.client_id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            service to Keys(id, properties.getProperty("$service.client_secret")?.takeIf { it.isNotBlank() })
        }.toMap()
    }

    private fun get(service: String): Keys? = (override ?: loaded)[service]

    val anilist get() = get("anilist")
    val myAnimeList get() = get("myanimelist")
    val shikimori get() = get("shikimori")
    val bangumi get() = get("bangumi")
    val comick get() = get("comick")
    val mangaBaka get() = get("mangabaka")
    val hikka get() = get("hikka")

    /** Runs [block] with test keys for every service; for tests, before the graph builds TrackerManager. */
    internal fun <T> withTestKeys(block: () -> T): T {
        override = mapOf(
            "anilist" to Keys("test-anilist"),
            "myanimelist" to Keys("test-mal"),
            "shikimori" to Keys("test-shikimori", "test-shikimori-secret"),
            "bangumi" to Keys("test-bangumi", "test-bangumi-secret"),
            "comick" to Keys("test-comick"),
            "mangabaka" to Keys("test-mangabaka"),
            "hikka" to Keys("test-hikka", "test-hikka-secret"),
        )
        return block()
    }

    private val SERVICES = listOf("anilist", "myanimelist", "shikimori", "bangumi", "comick", "mangabaka", "hikka")
}
