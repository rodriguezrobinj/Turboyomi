package mihon.desktop.extension

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream

class ApkInspectorTest {
    @Test
    fun `reads application meta-data`() {
        val xml = """
            <manifest package="eu.kanade.tachiyomi.extension.en.example">
              <application android:label="Tachiyomi: Example">
                <meta-data android:name="tachiyomi.extension.class" android:value=".Example"/>
                <meta-data android:name="tachiyomix.extensionLib" android:value="1.4"/>
              </application>
            </manifest>
        """.trimIndent()

        ApkInspector.readMetaData(xml) shouldBe mapOf(
            "tachiyomi.extension.class" to ".Example",
            "tachiyomix.extensionLib" to "1.4",
        )
    }

    @Test
    fun `rejects manifests with a doctype`() {
        val xml = """<!DOCTYPE x [<!ENTITY e SYSTEM "file:///C:/Windows/win.ini">]><manifest><application/></manifest>"""
        shouldThrowAny { ApkInspector.readMetaData(xml) }
    }

    @Test
    fun `unsigned apks are rejected`() {
        val apk = createTempFile(suffix = ".apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(3, 0, 8, 0))
            zip.closeEntry()
        }
        shouldThrow<ApkSignatureException> { ApkInspector.verifiedSignatures(apk) }
    }

    @Test
    fun `truncated files are rejected`() {
        val apk = createTempFile(suffix = ".apk")
        apk.outputStream().use { it.write(byteArrayOf(0x50, 0x4B, 0x03)) }
        shouldThrow<ApkSignatureException> { ApkInspector.verifiedSignatures(apk) }
    }
}
