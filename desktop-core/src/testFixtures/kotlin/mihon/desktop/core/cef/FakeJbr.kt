package mihon.desktop.core.cef

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * JetBrains Runtime's JCEF zip in miniature, served like the real one: `/jbr.zip` (also to HEAD) and
 * `/jbr.zip.checksum` with its SHA-512. [extraEntry] adds an entry by name, such as one that climbs out of the folder.
 */
class FakeJbr(version: String = CefRelease.JCEF_VERSION, extraEntry: String? = null) : Dispatcher() {
    val zip: ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray = name.toByteArray()) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("jbr/bin/libcef.dll")
            put("jbr/bin/jcef_helper.exe")
            put("jbr/lib/icudtl.dat")
            // Random, so it doesn't compress: big enough for a slow download to be cancelled halfway
            put("jbr/lib/padding.bin", Random(1).nextBytes(64 * 1024))
            put("jbr/release", "IMPLEMENTOR=\"JetBrains s.r.o.\"\nJCEF_VERSION_DETAILED=\"$version\"\n".toByteArray())
            extraEntry?.let { put(it) }
        }
    }.toByteArray()

    @Volatile
    var checksum: String = MessageDigest.getInstance("SHA-512").digest(zip).joinToString("") { "%02x".format(it) }

    /** Drops the connection halfway through the archive. */
    @Volatile
    var disconnect = false

    /** Sends the archive at 1 KiB every 100 ms, so a test can cancel during the download. */
    @Volatile
    var slow = false

    override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
        "/jbr.zip" -> when {
            request.method == "HEAD" -> MockResponse().setHeader("Content-Length", zip.size)
            disconnect -> MockResponse().setBody(Buffer().write(zip))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            slow -> MockResponse().setBody(Buffer().write(zip)).throttleBody(1024, 100, TimeUnit.MILLISECONDS)
            else -> MockResponse().setBody(Buffer().write(zip))
        }
        "/jbr.zip.checksum" -> MockResponse().setBody("$checksum  jbr.zip\n")
        else -> MockResponse().setResponseCode(404)
    }
}
