package mihon.desktop.extension

import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import mihon.desktop.extension.vendor.ChildFirstURLClassLoader
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream

class FakeSource : Source by mockk(relaxed = true) {
    override val id = 1L
    override val name = "Fake"
    override val lang = "en"
}

class ClassLoadingTest {
    private fun emptyClass(name: String): ByteArray = ClassWriter(0).run {
        visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null)
        visitEnd()
        toByteArray()
    }

    private fun jarWith(vararg classNames: String): Path {
        val jar = createTempFile(suffix = ".jar")
        JarOutputStream(jar.outputStream()).use { out ->
            classNames.forEach {
                out.putNextEntry(JarEntry(it.replace('.', '/') + ".class"))
                out.write(emptyClass(it))
                out.closeEntry()
            }
        }
        return jar
    }

    @Test
    fun `classes only in the extension load from the extension`() {
        ChildFirstURLClassLoader(arrayOf(jarWith("ext.only.Hello").toUri().toURL())).use { loader ->
            loader.loadClass("ext.only.Hello").classLoader shouldBe loader
        }
    }

    @Test
    fun `the app's copy of a shared library wins`() {
        ChildFirstURLClassLoader(arrayOf(jarWith("okhttp3.OkHttpClient").toUri().toURL())).use { loader ->
            loader.loadClass("okhttp3.OkHttpClient") shouldBe okhttp3.OkHttpClient::class.java
        }
    }

    @Test
    fun `instantiates sources by class name`() {
        val jar = jarWith("ext.only.Hello")
        SourceInstantiator.instantiate(jar, listOf(FakeSource::class.java.name)).map { it.name } shouldBe listOf("Fake")
        SourceInstantiator.release(jar)
    }

    @Test
    fun `apk assets are readable through the extension class loader`() {
        // Extensions read bundled assets (e.g. i18n files) from their own class loader, which on Android is the apk
        val jar = jarWith("ext.only.Hello")
        val apk = createTempFile(suffix = ".apk")
        JarOutputStream(apk.outputStream()).use { out ->
            out.putNextEntry(JarEntry("assets/i18n/messages_en.properties"))
            out.write("title=Hello".toByteArray())
            out.closeEntry()
        }

        val loader = SourceInstantiator.loaderFor(jar, apk)
        loader.getResourceAsStream("assets/i18n/messages_en.properties")!!.use { it.readBytes().decodeToString() } shouldBe
            "title=Hello"
        SourceInstantiator.release(jar)
    }
}
