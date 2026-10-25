package mihon.desktop.app.update

/** Release tags against the running version (spec 6b §2): plain x.y.z, with or without a "v"; anything else is ignored. */
object AppVersion {
    private val SEMVER = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    private fun parse(version: String): List<Int>? =
        SEMVER.find(version.trim())?.groupValues?.drop(1)?.map(String::toInt)

    fun isNewer(tag: String, current: String): Boolean {
        val new = parse(tag) ?: return false
        val old = parse(current) ?: return false
        return new.zip(old).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a > b } ?: false
    }
}
