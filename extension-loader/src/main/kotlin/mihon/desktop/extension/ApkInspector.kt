package mihon.desktop.extension

import com.android.apksig.ApkVerifier
import eu.kanade.tachiyomi.util.lang.Hash
import net.dongliu.apk.parser.ApkFile
import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

data class ApkInfo(
    val pkgName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val metaData: Map<String, String>,
    /** SHA-256 hex digests of the signer certificates, the same format as a store's signingKey. */
    val signatures: List<String>,
)

class ApkSignatureException(message: String) : Exception(message)

object ApkInspector {

    /** Verifies the signature first, so nothing is read from an apk that was tampered with. */
    fun inspect(apk: Path): ApkInfo {
        val signatures = verifiedSignatures(apk)
        ApkFile(apk.toFile()).use { parsed ->
            val meta = parsed.apkMeta
            return ApkInfo(
                pkgName = meta.packageName,
                label = meta.label.orEmpty(),
                versionName = meta.versionName.orEmpty(),
                versionCode = meta.versionCode ?: 0L,
                metaData = readMetaData(parsed.manifestXml),
                signatures = signatures,
            )
        }
    }

    fun verifiedSignatures(apk: Path): List<String> {
        val result = try {
            ApkVerifier.Builder(apk.toFile()).build().verify()
        } catch (e: Exception) {
            throw ApkSignatureException("Could not verify the apk signature: ${e.message}")
        }
        if (!result.isVerified) throw ApkSignatureException("Invalid apk signature: ${result.errors.joinToString()}")
        return result.signerCertificates.map { Hash.sha256(it.encoded) }
    }

    fun readMetaData(manifestXml: String): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // The manifest comes from a downloaded file: no DTDs, no external entities
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(manifestXml.byteInputStream())
        val application = doc.getElementsByTagName("application").item(0) as? Element ?: return emptyMap()
        val nodes = application.getElementsByTagName("meta-data")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .associate { it.getAttribute("android:name") to it.getAttribute("android:value") }
    }
}
