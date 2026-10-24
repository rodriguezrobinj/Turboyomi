package mihon.desktop.app.track

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestBackups
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds

class CredentialsTest {
    private val graph: TestAppGraph = startTestApp()
    private val key = Preference.privateKey("pref_mangasync_password_3")

    private fun settingsFiles() = graph.appDirs.data.toFile().walk().filter { it.isFile }.map { it.readText() }.toList()

    @Test
    fun `a sealed preference keeps the value encrypted on disk and gives it back in clear`() {
        val sealed = SealedPreference(graph.preferenceStore.getString(key, ""))

        sealed.set("contraseña José")

        sealed.get() shouldBe "contraseña José"
        graph.preferenceStore.getString(key, "").get() shouldStartWith "dpapi:"
        // The write is async: wait until a later value is on disk, then read the files
        val marker = "flush-${System.nanoTime()}"
        graph.preferenceStore.getString("desktop_test_flush", "").set(marker)
        runBlocking { withTimeout(10.seconds) { while (settingsFiles().none { marker in it }) delay(100) } }
        settingsFiles().forEach { it shouldNotContain "contraseña José" }
        sealed.set("")
    }

    @Test
    fun `a value that can't be decrypted reads as empty, a signed-out session`() {
        graph.preferenceStore.getString(key, "").set("dpapi:bm8gZXMgZHBhcGk=")

        SealedPreference(graph.preferenceStore.getString(key, "")).get() shouldBe ""
    }

    @Test
    fun `a backup with private settings carries credentials in clear and restoring seals them again`(): Unit =
        runBlocking {
            SealedPreference(graph.preferenceStore.getString(key, "")).set("secreto")
            val file = File(createTempDirectory("Copias José").toFile(), "privado.tachibk")

            graph.backupCreatorFactory.create(isAutoBackup = false).backup(file, BackupOptions(privateSettings = true))
            val inBackup = graph.backupDecoder.decodeMetadata(file).backup.backupPreferences.single { it.key == key }
            inBackup.value shouldBe StringPreferenceValue("secreto")

            // As a backup from Android would bring it: in clear
            val android = File(file.parentFile, "android.tachibk")
            TestBackups.write(
                android,
                Backup(
                    emptyList(),
                    backupPreferences = listOf(BackupPreference(key, StringPreferenceValue("de Android"))),
                ),
            )
            graph.backupJob.restore(android, RestoreOptions()) shouldBe true
            graph.backupJob.awaitIdle()

            graph.preferenceStore.getString(key, "").get() shouldStartWith "dpapi:"
            SealedPreference(graph.preferenceStore.getString(key, "")).get() shouldBe "de Android"
            SealedPreference(graph.preferenceStore.getString(key, "")).set("")
        }

    @Test
    fun `only the trackers' usernames, passwords and tokens are credential keys`() {
        Credentials.isCredentialKey(Preference.privateKey("pref_mangasync_username_7")) shouldBe true
        Credentials.isCredentialKey(Preference.privateKey("pref_mangasync_password_3")) shouldBe true
        Credentials.isCredentialKey(Preference.privateKey("track_token_2")) shouldBe true
        Credentials.isCredentialKey(Preference.privateKey("pref_mangasync_displayname_3")) shouldBe false
        Credentials.isCredentialKey("pref_mangasync_password_3") shouldBe false
    }
}
