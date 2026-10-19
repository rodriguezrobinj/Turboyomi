package mihon.desktop.extension

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText

class ExtensionInstallerTest {
    private val infos = mutableMapOf<Path, ApkInfo>()

    private fun apk(versionCode: Long, signatures: List<String> = listOf("k")): Path =
        createTempFile(suffix = ".apk").also {
            it.writeText("apk $versionCode")
            infos[it] = ApkInfo("pkg.example", "Example", "1.4.$versionCode", versionCode, emptyMap(), signatures)
        }

    private fun installer(dir: Path, convert: (Path, Path) -> Unit = { _, jar -> jar.writeText("jar") }) =
        ExtensionInstaller(dir, inspect = { infos.getValue(it) }, convert = convert)

    @Test
    fun `installs apk and jar named by package and version`() {
        val dir = createTempDirectory("ext José")
        installer(dir).install(apk(3))
        dir.listDirectoryEntries().map { it.name }.sorted() shouldBe listOf("pkg.example-3.apk", "pkg.example-3.jar")
    }

    @Test
    fun `newest version wins and stale files are cleaned up`() {
        val dir = createTempDirectory("ext")
        val installer = installer(dir)
        installer.install(apk(3))
        // Simulates a jar Windows kept locked during the update: an older version is still on disk
        dir.resolve("pkg.example-2.apk").writeText("old")
        dir.resolve("pkg.example-2.jar").writeText("old")
        infos[dir.resolve("pkg.example-3.apk")] = infos.values.first { it.versionCode == 3L }

        installer.installed().map { it.info.versionCode } shouldContainExactly listOf(3L)
        dir.listDirectoryEntries().map { it.name }.sorted() shouldBe listOf("pkg.example-3.apk", "pkg.example-3.jar")
    }

    @Test
    fun `update signed with a different key is rejected and keeps the installed version`() {
        val dir = createTempDirectory("ext")
        val installer = installer(dir)
        installer.install(apk(3, listOf("k")))
        infos[dir.resolve("pkg.example-3.apk")] = infos.values.first { it.versionCode == 3L }

        shouldThrow<SignatureMismatchException> { installer.install(apk(4, listOf("evil"))) }
        dir.listDirectoryEntries().map { it.name }.sorted() shouldBe listOf("pkg.example-3.apk", "pkg.example-3.jar")
    }

    @Test
    fun `reinstalling the same version keeps its files`() {
        val dir = createTempDirectory("ext")
        val installer = installer(dir)
        installer.install(apk(3))
        infos[dir.resolve("pkg.example-3.apk")] = infos.values.first { it.versionCode == 3L }

        installer.install(apk(3))
        dir.listDirectoryEntries().map { it.name }.sorted() shouldBe listOf("pkg.example-3.apk", "pkg.example-3.jar")
    }

    @Test
    fun `failed conversion leaves no partial files`() {
        val dir = createTempDirectory("ext")
        shouldThrow<IllegalStateException> {
            installer(dir, convert = { _, jar -> jar.writeText("partial"); error("corrupt dex") }).install(apk(3))
        }
        dir.listDirectoryEntries() shouldBe emptyList()
    }
}
