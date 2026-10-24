# Subproyecto 5b-1: seguimiento sin OAuth, notas e incógnito — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Traer el seguimiento de Mihon (TrackerManager, vincular, sincronizar, reintentar sin red) con Kitsu, MangaUpdates, Komga, Kavita y Suwayomi, más las notas de manga y el modo incógnito global.

**Architecture:**
- Se copian `data/track/`, `domain/track/` y su UI de Mihon con el mismo paquete, cambiando solo lo de Android.
- Las credenciales pasan por `SealedPreference`, que las cifra con DPAPI al guardar y las descifra al leer. El backup las saca descifradas (solo con "privadas") y la restauración las vuelve a cifrar.
- `DelayedTrackingUpdateWorker` pasa a ser un `PeriodicJob`.
- `TrackerManager` recibe por multibinding de Metro los trackers que añaden los tests.
- Un seam en `BaseTracker` permite redirigir el HTTP de los trackers a un `MockWebServer`.

**Tech Stack:** Kotlin/JVM, Compose Desktop, Metro, Apollo Kotlin 5 (GraphQL de Kitsu), OkHttp, compose-rich-editor, JNA Crypt32 (DPAPI), JUnit 5 + kotest + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-10-23-subproyecto-5b1-seguimiento-design.md`

## Global Constraints

- Los ficheros de Mihon se copian de `app/src/main/java/<ruta>` a `desktop-app/src/main/kotlin/<ruta>` con el mismo paquete. Cada cambio lleva `// desktop:`.
- `scripts/check-no-android.sh` debe pasar. De Android solo se permiten `android.content.Context`, `android.widget.Toast`, `androidx.compose|lifecycle|paging|preference|navigationevent` y `androidx.activity.compose.BackHandler`. Por eso `androidx.annotation.*` (`@CallSuper`, `@DrawableRes`) y `android.content.SharedPreferences` se quitan.
- Strings nuevos con prefijo `desktop_`, en el bloque `<!-- Desktop -->` de `base/strings.xml` y `es/strings.xml`. `SpanishStringsTest` lo exige.
- Tests escritos como `= runBlocking {}` declaran `: Unit`.
- Los scripts de edición se escriben con la herramienta Write y se ejecutan con `PYTHONPATH=<workspace> python <script>`: los heredocs de Bash fallan con textos largos.
- Commits con `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`. Nunca se hace push a `upstream`.
- IDs de los trackers, los de Mihon: Kitsu 3, Komga 6, MangaUpdates 7, Kavita 8, Suwayomi 9 (spec §1).
- Las credenciales nunca se guardan en texto plano (spec §3 E).

## Desviaciones respecto a la spec

1. **Sesión caducada (§3 D, §4, §5).** En Mihon, `trackAuthExpired` solo lo usa MyAnimeList, que llega en 5b-2.
   - Kitsu renueva el token con su `refresh_token` (`KitsuInterceptor`).
   - Un error de MangaUpdates se muestra como aviso (`BaseTracker.updateRemote`, `context.toast`).
   - Se sigue a Mihon: el test de "401 marca la sesión caducada" pasa a ser "un 401 de MangaUpdates muestra el error y no rompe nada, y Kitsu renueva su token".
2. **Pruebas de Kitsu (§5).** La API de Kitsu es GraphQL, con consultas generadas por Apollo.
   - Contra `MockWebServer` se prueban el login (REST `oauth/token` y la consulta `KitsuGetCurrentAccount`), la contraseña incorrecta y la renovación del token.
   - Buscar, vincular y actualizar en Kitsu quedan para la prueba manual. MangaUpdates, que es REST, los cubre todos de forma automática.
3. **Logos.** `Tracker.getLogo()` deja de devolver un `@DrawableRes Int` y devuelve la ruta de un recurso del classpath (`drawable/brand_kitsu.xml`). `TrackLogoIcon` lo carga con `loadXmlImageVector`. Los XML vectoriales son los de Mihon.
4. **`TrackerManager`.** Recibe un `Set<Tracker>` adicional por multibinding de Metro, vacío en la app y con el tracker falso en los tests. Sin eso, el tracker falso de la spec (§5) no se podría registrar.

## Review Focus

1. Credenciales de un backup de Android restauradas en el escritorio: quedan cifradas y la sesión sigue activa. Test en la Task 1.
2. Un fallo de DPAPI al leer (otro perfil de Windows) se trata como sesión cerrada, sin crash. Test en la Task 1.
3. Un tracker falla mientras otro va bien: el que va bien se actualiza y el fallido queda en la cola de reintento. Test en la Task 2.
4. Un manga con varios trackers: terminar un capítulo los actualiza todos. Lo cubre el test con dos tracks de la Task 2.
5. Cerrar la app con cambios pendientes: siguen en `DelayedTrackingStore` y se envían en la sesión siguiente, porque el store es persistente. Prueba manual (Task 8).

---

### Task 1: credenciales cifradas

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/track/Credentials.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/backup/create/creators/PreferenceBackupCreator.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/backup/restore/restorers/PreferenceRestorer.kt`
- Modify: `desktop-app/build.gradle.kts`, si `Dpapi` (desktop-core) no es visible desde desktop-app.
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/track/CredentialsTest.kt`

**Interfaces:**
- Produces:
  - `object Credentials`: `fun seal(plain: String): String`, `fun unseal(stored: String): String` y `fun isCredentialKey(key: String): Boolean`.
  - `class SealedPreference(inner: Preference<String>) : Preference<String>`.
  - La Task 2 envuelve con `SealedPreference` las preferencias `trackUsername`, `trackPassword` y `trackToken` de `TrackPreferences`.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.track

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

    private fun settingsFiles() = graph.appDirs.data.toFile().walk().filter { it.isFile }.map { it.readText() }

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

            graph.backupCreatorFactory.create(isAutoBackup = false).backup(
                file,
                eu.kanade.tachiyomi.data.backup.create.BackupOptions(privateSettings = true),
            )
            val inBackup = graph.backupDecoder.decodeMetadata(file).backup.backupPreferences.single { it.key == key }
            inBackup.value shouldBe StringPreferenceValue("secreto")

            // As a backup from Android would bring it: in clear
            val android = File(file.parentFile, "android.tachibk")
            TestBackups.write(
                android,
                Backup(emptyList(), backupPreferences = listOf(BackupPreference(key, StringPreferenceValue("de Android")))),
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
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.CredentialsTest"`
Expected: FAIL de compilación (`SealedPreference` y `Credentials` no existen).

- [ ] **Step 3: Implementación**

`Credentials.kt`:

```kotlin
package mihon.desktop.app.track

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import logcat.LogPriority
import mihon.desktop.core.cookies.Dpapi
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import java.util.Base64

/**
 * The trackers' credentials, encrypted with DPAPI for this Windows user (parent spec §5.5): never stored in clear.
 * A backup carries them in clear, as Mihon does, since a DPAPI blob is useless on another machine (spec 5b-1 §3 E).
 */
object Credentials {
    private const val PREFIX = "dpapi:"
    private val KEY = Regex(
        "^" + Regex.escape(Preference.privateKey("")) + "(pref_mangasync_username_|pref_mangasync_password_|track_token_)\\d+$",
    )

    fun isCredentialKey(key: String): Boolean = KEY.matches(key)

    fun seal(plain: String): String =
        if (plain.isEmpty()) "" else PREFIX + Base64.getEncoder().encodeToString(Dpapi.protect(plain.encodeToByteArray()))

    /** The clear value: a sealed one decrypted, or one stored in clear (restored, or from before sealing) as it is. */
    fun unseal(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            Dpapi.unprotect(Base64.getDecoder().decode(stored.removePrefix(PREFIX))).decodeToString()
        } catch (e: Exception) {
            // Another Windows user or a reinstalled profile: the session is gone, sign in again (spec 5b-1 §4)
            logcat(LogPriority.WARN, e) { "A tracker credential couldn't be decrypted" }
            ""
        }
    }
}

/** [inner] with its value sealed by [Credentials]; one found in clear is sealed on the first read. */
class SealedPreference(private val inner: Preference<String>) : Preference<String> {
    override fun key(): String = inner.key()

    override fun get(): String {
        val stored = inner.get()
        if (stored.isNotEmpty() && !stored.startsWith("dpapi:")) inner.set(Credentials.seal(stored))
        return Credentials.unseal(stored)
    }

    override fun set(value: String) = inner.set(Credentials.seal(value))

    override fun isSet(): Boolean = inner.isSet()

    override fun delete() = inner.delete()

    override fun defaultValue(): String = inner.defaultValue()

    override fun changes(): Flow<String> = inner.changes().map(Credentials::unseal)

    override fun stateIn(scope: CoroutineScope): StateFlow<String> =
        changes().stateIn(scope, SharingStarted.Eagerly, get())
}
```

`PreferenceBackupCreator.kt`, en `toBackupPreferences`:

```kotlin
                    // desktop: credentials leave sealed for this machine, so a backup carries them in clear (spec 5b-1 §3 E)
                    is String -> BackupPreference(
                        key,
                        StringPreferenceValue(if (Credentials.isCredentialKey(key)) Credentials.unseal(value) else value),
                    )
```

`PreferenceRestorer.kt`, en la rama `is StringPreferenceValue`, que pasa a:

```kotlin
                    is StringPreferenceValue -> {
                        if (prefs[key] is String?) {
                            // desktop: credentials are only ever stored sealed (spec 5b-1 §3 E)
                            val restored = if (Credentials.isCredentialKey(key)) Credentials.seal(value.value) else value.value
                            preferenceStore.getString(key).set(restored)
                        }
                    }
```

Si `mihon.desktop.core.cookies.Dpapi` no se ve desde desktop-app, porque es `internal` o está en otro módulo sin dependencia, se hace público y se apunta como ruling.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.CredentialsTest" --tests "mihon.desktop.app.backup.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop-app desktop-core
git commit -m "feat(desktop-app): tracker credentials sealed with DPAPI, in clear only inside a private backup

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: núcleo de seguimiento con un tracker falso

**Files:**
- Copy:
  - `eu/kanade/tachiyomi/data/track/Tracker.kt`, `BaseTracker.kt`, `EnhancedTracker.kt`, `DeletableTracker.kt`, `TrackerManager.kt` y `model/TrackSearch.kt`;
  - `eu/kanade/tachiyomi/data/database/models/Track.kt` y `TrackImpl.kt`;
  - `eu/kanade/domain/track/interactor/*.kt`, `model/*.kt`, `service/TrackPreferences.kt` y `store/DelayedTrackingStore.kt`.
- Create:
  - `desktop-app/src/main/kotlin/eu/kanade/domain/track/service/DelayedTrackingJob.kt`;
  - `desktop-app/src/main/kotlin/mihon/desktop/app/track/TrackerBindings.kt`;
  - `desktop-app/src/test/kotlin/mihon/desktop/app/testing/FakeTracker.kt`.
- Delete: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/track/*` y `desktop-app/src/stubs/kotlin/eu/kanade/domain/track/*`.
- Modify:
  - `mihon/app/di/AppGraph.kt`;
  - `mihon/desktop/app/Main.kt`;
  - `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt` (`TestBindings`).
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/track/TrackingFlowTest.kt`

**Interfaces:**
- Consumes: de la Task 1, `SealedPreference`.
- Produces:
  - `TrackerManager(extra: Set<Tracker>)`, con `trackers`, `get(id)`, `loggedInTrackers()` y `kitsu`, `mangaUpdates`, `komga`, `kavita`, `suwayomi`. Las propiedades de esos trackers las rellenan las Tasks 3 y 4; en esta tarea su lista es vacía.
  - `DelayedTrackingJob.periodicJob(): PeriodicJob` y `suspend fun flush()`.
  - `TestTrackers.fake: FakeTracker`, con `id = 999`, `remote: MutableMap<Long, Double>`, `failing: Boolean` y `loggedIn`.
  - En `AppGraph`: `trackPreferences`, `trackerManager`, `addTracks`, `upsertTrack`, `trackChapter`, `delayedTrackingStore` y `delayedTrackingJob`.

- [ ] **Step 1: Copiar y borrar stubs**

```bash
A=app/src/main/java; D=desktop-app/src/main/kotlin
mkdir -p $D/eu/kanade/tachiyomi/data/track/model $D/eu/kanade/tachiyomi/data/database/models $D/eu/kanade/domain/track/{interactor,model,service,store}
cp $A/eu/kanade/tachiyomi/data/track/{Tracker,BaseTracker,EnhancedTracker,DeletableTracker,TrackerManager}.kt $D/eu/kanade/tachiyomi/data/track/
cp $A/eu/kanade/tachiyomi/data/track/model/TrackSearch.kt $D/eu/kanade/tachiyomi/data/track/model/
cp $A/eu/kanade/tachiyomi/data/database/models/{Track,TrackImpl}.kt $D/eu/kanade/tachiyomi/data/database/models/
cp $A/eu/kanade/domain/track/interactor/*.kt $D/eu/kanade/domain/track/interactor/
cp $A/eu/kanade/domain/track/model/*.kt $D/eu/kanade/domain/track/model/
cp $A/eu/kanade/domain/track/service/TrackPreferences.kt $D/eu/kanade/domain/track/service/
cp $A/eu/kanade/domain/track/store/DelayedTrackingStore.kt $D/eu/kanade/domain/track/store/
git rm -q -r desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/track desktop-app/src/stubs/kotlin/eu/kanade/domain/track
```

- [ ] **Step 2: Escribir el tracker falso y los tests**

`FakeTracker.kt`:

```kotlin
package mihon.desktop.app.testing

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import tachiyomi.domain.track.model.Track as DomainTrack

/** A tracker kept in memory: [remote] is what the "service" holds per remote id; [failing] plays a network outage. */
class FakeTracker(id: Long, name: String) : BaseTracker(id, name) {
    val remote = ConcurrentHashMap<Long, Double>()

    @Volatile
    var failing = false

    override fun getLogo() = "drawable/brand_kitsu.xml"

    override fun getStatusList(): List<Long> = listOf(READING, COMPLETED)

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        else -> null
    }

    override fun getReadingStatus() = READING

    override fun getRereadingStatus() = -1L

    override fun getCompletionStatus() = COMPLETED

    override fun getScoreList(): ImmutableList<String> = persistentListOf("0", "5", "10")

    override fun displayScore(track: DomainTrack) = track.score.toString()

    private fun network() {
        if (failing) throw IOException("sin red")
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        network()
        remote[track.remote_id] = track.last_chapter_read
        return track
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track = update(track)

    override suspend fun search(query: String): List<TrackSearch> {
        network()
        return listOf(TrackSearch.create(id).apply { remote_id = 42; title = "Resultado $query" })
    }

    override suspend fun refresh(track: Track): Track {
        network()
        track.last_chapter_read = remote[track.remote_id] ?: track.last_chapter_read
        return track
    }

    override suspend fun login(username: String, password: String) = saveCredentials(username, password)

    override suspend fun updateUserConfig() {}

    private companion object {
        const val READING = 1L
        const val COMPLETED = 2L
    }
}

object TestTrackers {
    val fake = FakeTracker(999L, "Falso José")
    val other = FakeTracker(998L, "Otro José")
}
```

`getScoreList` lleva el tipo de retorno que declare la interfaz `Tracker` copiada: `ImmutableList<String>` o `List<String>`. Se ajusta al compilar.

`TrackingFlowTest.kt`:

```kotlin
package mihon.desktop.app.track

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestTrackers
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track
import kotlin.time.Duration.Companion.seconds

class TrackingFlowTest {
    private val graph: TestAppGraph = startTestApp()
    private val fake = TestTrackers.fake
    private val other = TestTrackers.other

    @BeforeEach
    fun signIn(): Unit = runBlocking {
        fake.failing = false
        other.failing = false
        fake.login("josé", "clave")
        other.login("ana", "clave")
        graph.delayedTrackingStore.getItems().forEach { graph.delayedTrackingStore.remove(it.trackId) }
    }

    private suspend fun linked(manga: Manga, tracker: Long, remoteId: Long) {
        graph.trackRepository.upsert(
            Track(
                id = 0, mangaId = manga.id, trackerId = tracker, remoteId = remoteId, libraryId = null,
                title = manga.title, lastChapterRead = 0.0, totalChapters = 0, status = 1, score = 0.0,
                remoteUrl = "", startDate = null, finishDate = null, private = false,
            ),
        )
    }

    @Test
    fun `a read chapter goes up to every linked tracker`(): Unit = runBlocking {
        val manga = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        linked(manga, fake.id, 101)
        linked(manga, other.id, 102)

        graph.trackChapter.await(graph.context, manga.id, 2.0)

        fake.remote[101] shouldBe 2.0
        other.remote[102] shouldBe 2.0
        graph.trackRepository.getTracksByMangaId(manga.id).map { it.lastChapterRead }.toSet() shouldBe setOf(2.0)
    }

    @Test
    fun `offline the change waits in the queue, and the job sends it once the tracker answers`(): Unit = runBlocking {
        val manga = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        linked(manga, fake.id, 201)
        linked(manga, other.id, 202)
        fake.failing = true

        graph.trackChapter.await(graph.context, manga.id, 3.0)

        other.remote[202] shouldBe 3.0
        fake.remote[201] shouldBe null
        graph.delayedTrackingStore.getItems().size shouldBe 1

        fake.failing = false
        graph.delayedTrackingJob.flush()

        fake.remote[201] shouldBe 3.0
        graph.delayedTrackingStore.getItems().size shouldBe 0
    }

    @Test
    fun `a tracker without a session gets nothing`(): Unit = runBlocking {
        val manga = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        linked(manga, fake.id, 301)
        fake.logout()

        graph.trackChapter.await(graph.context, manga.id, 4.0)

        fake.remote[301] shouldBe null
    }

    @Test
    fun `a signed-in tracker's password isn't in the settings files`(): Unit = runBlocking {
        fake.login("josé", "clave secreta José")
        val files = { graph.appDirs.data.toFile().walk().filter { it.isFile }.map { it.readText() }.toList() }
        val marker = "flush-${System.nanoTime()}"
        graph.preferenceStore.getString("desktop_test_flush", "").set(marker)
        withTimeout(10.seconds) { while (files().none { marker in it }) delay(100) }

        files().forEach { it shouldNotContain "clave secreta José" }
    }
}
```

En `TestAppGraph`, accessors nuevos: `val trackRepository` (ya existe desde 5a), `val delayedTrackingStore: DelayedTrackingStore` y `val delayedTrackingJob: DelayedTrackingJob`, este último si no está en `AppGraph`. `TestBindings` gana:

```kotlin
    @Provides
    @IntoSet
    fun fakeTracker(): Tracker = TestTrackers.fake

    @Provides
    @IntoSet
    fun otherTracker(): Tracker = TestTrackers.other
```

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.TrackingFlowTest"`
Expected: FAIL de compilación. Los copiados usan `R`, `@CallSuper`, `@DrawableRes`, `WorkManager`, `androidx.core.content.edit` y los trackers de Mihon, y además falta `DelayedTrackingJob`.

- [ ] **Step 4: Adaptar lo copiado**

`Tracker.kt`:
- Quitar `@CallSuper`, `@DrawableRes` y sus imports.
- `fun getLogo(): Int` pasa a:

```kotlin
    // desktop: a classpath resource (Mihon's vector drawable), not an Android drawable id (plan 5b-1, dev. 3)
    fun getLogo(): String
```

`BaseTracker.kt`:
- Quitar `@CallSuper` e `import androidx.annotation.CallSuper`.
- `override val client` pasa a:

```kotlin
    // desktop: [testInterceptor] sends the trackers' requests to a test server (a seam, like DiskUtil.usableSpace)
    override val client: OkHttpClient
        get() = testInterceptor?.let { networkService.client.newBuilder().addInterceptor(it).build() }
            ?: networkService.client
```

- Al final de la clase:

```kotlin
    companion object {
        @Volatile
        internal var testInterceptor: Interceptor? = null
    }
```

  (import `okhttp3.Interceptor`).

`TrackerManager.kt` pasa a la versión de escritorio:

```kotlin
@Inject
@SingleIn(AppScope::class)
class TrackerManager(extra: Set<Tracker>) {
    // desktop: the trackers without OAuth (spec 5b-1); 5b-2 adds the rest with Mihon's ids. [extra] is what tests add

    val trackers: List<Tracker> = listOf<Tracker>() + extra

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    fun get(id: Long) = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>) = trackers.filter { it.id in ids }
}
```

Las Tasks 3 y 4 añaden `val kitsu = Kitsu(3L)` y los demás, y los ponen en `listOf(...)`. Si `combine` de una lista vacía no emite nada, la Task 2 deja `loggedInTrackersFlow()` así: `if (trackers.isEmpty()) flowOf(emptyList()) else combine(...)`.

`TrackerBindings.kt`:

```kotlin
package mihon.desktop.app.track

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import eu.kanade.tachiyomi.data.track.Tracker

/** The trackers outside TrackerManager's own list: none in the app, the fake ones in tests (plan 5b-1, dev. 4). */
@ContributesTo(AppScope::class)
interface TrackerBindings {
    @Multibinds(allowEmpty = true)
    fun extraTrackers(): Set<Tracker>
}
```

`TrackPreferences.kt`:
- `trackUsername`, `trackPassword` y `trackToken` devuelven `SealedPreference(preferenceStore.getString(...))`, con `// desktop: sealed with DPAPI (spec 5b-1 §3 E)`.
- Se quitan `anilistScoreType` y `mangabakaScoreType`, con `// desktop: with their trackers in 5b-2`.
- `kitsuScoreType` se queda cuando Kitsu exista (Task 3). En esta tarea se comenta, con `// desktop: with Kitsu (task 3)`.

`DelayedTrackingStore.kt`:
- `preferences.edit { … }` pasa a `preferences.edit().apply { … }.apply()`, con `// desktop: no androidx.core's edit {}`.
- Quitar `import androidx.core.content.edit`.

`TrackChapter.kt`:
- `if (setupJobOnFailure) { DelayedTrackingUpdateWorker.setupTask(context) }` pasa a:

```kotlin
                            // desktop: DelayedTrackingJob runs while there's something queued; nothing to schedule
```

- Los parámetros `context` y `setupJobOnFailure` se quedan, para no tocar a quien llama, con `@Suppress("UNUSED_PARAMETER")`.

`DelayedTrackingJob.kt`:

```kotlin
package eu.kanade.domain.track.service

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.store.DelayedTrackingStore
import logcat.LogPriority
import mihon.desktop.core.jobs.PeriodicJob
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import kotlin.time.Duration.Companion.minutes

/**
 * Desktop counterpart of Mihon's DelayedTrackingUpdateWorker (spec 5b-1 §3 C): while tracking updates are queued, it
 * sends them every few minutes, whenever the app is open or in the tray.
 */
@Inject
@SingleIn(AppScope::class)
class DelayedTrackingJob(
    private val context: Context,
    private val getTracks: GetTracks,
    private val trackChapter: TrackChapter,
    private val delayedTrackingStore: DelayedTrackingStore,
    preferenceStore: PreferenceStore,
) {
    private val lastRun = preferenceStore.getLong(Preference.appStateKey("desktop_last_delayed_tracking"), 0L)

    fun periodicJob(): PeriodicJob = PeriodicJob(
        name = "DelayedTracking",
        interval = { RETRY.takeIf { delayedTrackingStore.getItems().isNotEmpty() } },
        lastRun = lastRun,
        run = ::flush,
    )

    /** Mihon's doWork: each queued item goes up again; TrackChapter takes it off the queue once it lands. */
    suspend fun flush() {
        delayedTrackingStore.getItems()
            .mapNotNull {
                val track = getTracks.awaitOne(it.trackId)
                if (track == null) delayedTrackingStore.remove(it.trackId)
                track?.copy(lastChapterRead = it.lastChapterRead.toDouble())
            }
            .forEach { track ->
                logcat(LogPriority.DEBUG) { "Updating delayed track item: ${track.mangaId}, ${track.lastChapterRead}" }
                trackChapter.await(context, track.mangaId, track.lastChapterRead, setupJobOnFailure = false)
            }
    }

    private companion object {
        val RETRY = 5.minutes
    }
}
```

Cuidado: `TrackChapter` no sube si `chapterNumber <= track.lastChapterRead`. Si el `lastChapterRead` de la base de datos ya vale el número pendiente porque se guardó en local, el reintento no subiría nada. Mihon pasa la copia con el número de la cola a `trackChapter.await(…, track.lastChapterRead, …)`, y la comparación se hace contra la fila de la base de datos, que no se actualizó porque `upsertTrack` está después de `update`. El test "offline…" lo comprueba.

`AppGraph`: añadir `trackPreferences`, `trackerManager`, `addTracks`, `upsertTrack`, `trackChapter`, `delayedTrackingStore` y `delayedTrackingJob`. `BaseTracker` lee de él `trackPreferences`, `networkHelper`, `context`, `addTracks` y `upsertTrack`.

`Main.kt`: la lista del `JobScheduler` gana `graph.delayedTrackingJob.periodicJob()`.

En los demás copiados (interactors, `model/Track.kt`, `TrackImpl`, `TrackSearch`), solo se arreglan las importaciones que no compilen, con `// desktop:`. Si alguno usa algo de Android, se apunta como ruling.

- [ ] **Step 5: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.*" --tests "mihon.desktop.app.library.*" --tests "mihon.desktop.app.migration.*"`
Expected: PASS.

Run: `bash scripts/check-no-android.sh`
Expected: OK.

- [ ] **Step 6: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): Mihon's tracking core, queued updates retried by a desktop job

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Kitsu y MangaUpdates

**Files:**
- Copy: `eu/kanade/tachiyomi/data/track/kitsu/**` y `mangaupdates/**`, `app/src/main/java/mihon/graphql/kitsu/adapter/KitsuMapAdapter.kt`, `app/src/main/graphql/mihon/graphql/kitsu/*` (a `desktop-app/src/main/graphql/mihon/graphql/kitsu/`) y `app/src/main/res/drawable/brand_{kitsu,mangaupdates}.xml` (a `desktop-app/src/main/resources/drawable/`).
- Modify:
  - `desktop-app/build.gradle.kts`: plugin Apollo, `implementation(libs.apollo)`, `implementation(libs.apollo.adapters)` y el bloque `apollo { service("kitsu") { … } }`;
  - `TrackerManager.kt` y `TrackPreferences.kt` (`kitsuScoreType`).
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/track/KitsuMangaUpdatesTest.kt`

**Interfaces:**
- Consumes:
  - de la Task 2, `BaseTracker.testInterceptor` y `TrackerManager`;
  - de la Task 1, `SealedPreference`, a través de `TrackPreferences`.
- Produces: `trackerManager.kitsu` (id 3) y `trackerManager.mangaUpdates` (id 7).

- [ ] **Step 1: Copiar y configurar Apollo**

```bash
A=app/src/main/java/eu/kanade/tachiyomi/data/track; D=desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/track
cp -r $A/kitsu $A/mangaupdates $D/
mkdir -p desktop-app/src/main/kotlin/mihon/graphql/kitsu/adapter desktop-app/src/main/graphql/mihon/graphql/kitsu desktop-app/src/main/resources/drawable
cp app/src/main/java/mihon/graphql/kitsu/adapter/KitsuMapAdapter.kt desktop-app/src/main/kotlin/mihon/graphql/kitsu/adapter/
cp app/src/main/graphql/mihon/graphql/kitsu/* desktop-app/src/main/graphql/mihon/graphql/kitsu/
cp app/src/main/res/drawable/brand_kitsu.xml app/src/main/res/drawable/brand_mangaupdates.xml desktop-app/src/main/resources/drawable/
```

En `desktop-app/build.gradle.kts`:
- `plugins { … alias(libs.plugins.apollo) }`
- `dependencies { … implementation(libs.apollo); implementation(libs.apollo.adapters) }`
- El bloque, copiado del `service("kitsu")` de `app/build.gradle.kts` sin `introspection` (el esquema ya está en el repo) y sin `android.defaultConfig`:

```kotlin
apollo {
    // Mihon's Kitsu GraphQL service; AniList and Shikimori come with their trackers in 5b-2
    service("kitsu") {
        packageName.set("mihon.graphql.kitsu")
        srcDir(file("src/main/graphql/mihon/graphql/kitsu"))
        mapScalarToKotlinString("Date")
        mapScalar("ISO8601DateTime", "kotlin.time.Instant", "com.apollographql.adapter.core.KotlinInstantAdapter")
        mapScalar("Map", "kotlin.collections.Map<String, Any?>", "mihon.graphql.kitsu.adapter.KitsuMapAdapter")
    }
}
```

  Si el `service("kitsu")` de Mihon tiene más líneas después de `mapScalar("Map", …)`, se copian también.
- `bash scripts/check-licenses.sh`: si Apollo (MIT) o sus adaptadores no están en la lista, se añaden como en los subproyectos anteriores.

- [ ] **Step 2: Escribir los tests**

```kotlin
package mihon.desktop.app.track

import eu.kanade.tachiyomi.data.track.BaseTracker
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KitsuMangaUpdatesTest {
    private val graph: TestAppGraph = startTestApp()
    private val server = MockWebServer()
    private val responses = mutableMapOf<String, (RecordedRequest) -> MockResponse>()

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    @BeforeAll
    fun redirect() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.requestUrl!!.encodedPath}"
                return responses[key]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val local = server.url("/")
        // The trackers' real hosts go to the test server, path and query untouched
        BaseTracker.testInterceptor = Interceptor { chain ->
            val url = chain.request().url.newBuilder().scheme("http").host(local.host).port(local.port).build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }
    }

    @AfterAll
    fun stop() {
        BaseTracker.testInterceptor = null
        server.shutdown()
    }

    @Test
    fun `MangaUpdates signs in, searches, binds and updates, and its session token is sealed`(): Unit = runBlocking {
        val mu = graph.trackerManager.mangaUpdates
        responses["PUT /v1/account/login"] = { json("""{"context":{"session_token":"token-José","uid":77}}""") }
        responses["GET /v1/account/profile"] = { json("""{"username":"josé"}""") }
        responses["POST /v1/series/search"] = {
            json("""{"results":[{"record":{"series_id":5,"title":"Berserk","url":"https://www.mangaupdates.com/series/5"}}]}""")
        }

        mu.login("josé", "clave")

        mu.isLoggedIn shouldBe true
        mu.getDisplayUsername() shouldBe "josé"
        graph.preferenceStore.getString(
            tachiyomi.core.common.preference.Preference.privateKey("pref_mangasync_password_7"),
            "",
        ).get().startsWith("dpapi:") shouldBe true
        mu.search("Berserk").single().title shouldBe "Berserk"
        mu.logout()
    }

    @Test
    fun `a wrong MangaUpdates password fails and saves nothing`(): Unit = runBlocking {
        val mu = graph.trackerManager.mangaUpdates
        responses["PUT /v1/account/login"] = { json("""{"status":"exception","reason":"Bad credentials"}""", code = 401) }

        shouldThrowAny { mu.login("josé", "mala") }

        mu.isLoggedIn shouldBe false
    }

    @Test
    fun `Kitsu signs in with its OAuth password grant and reads the account`(): Unit = runBlocking {
        val kitsu = graph.trackerManager.kitsu
        responses["POST /api/oauth/token"] = {
            json("""{"access_token":"a","created_at":${System.currentTimeMillis() / 1000},"expires_in":3600,"refresh_token":"r"}""")
        }
        responses["POST /api/graphql"] = {
            json("""{"data":{"currentAccount":{"id":"9","ratingSystem":"ADVANCED","profile":{"name":"José"}}}}""")
        }

        kitsu.login("jose@example.org", "clave")

        kitsu.isLoggedIn shouldBe true
        kitsu.getDisplayUsername() shouldBe "José"
        kitsu.logout()
    }

    @Test
    fun `a wrong Kitsu password fails and saves nothing`(): Unit = runBlocking {
        val kitsu = graph.trackerManager.kitsu
        responses["POST /api/oauth/token"] = { json("""{"error":"invalid_grant"}""", code = 400) }

        shouldThrowAny { kitsu.login("jose@example.org", "mala") }

        kitsu.isLoggedIn shouldBe false
    }
}
```

`TestAppGraph` gana `val trackerManager: TrackerManager`, si no lo hereda de `AppGraph`.

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.KitsuMangaUpdatesTest"`
Expected: FAIL de compilación (`trackerManager.kitsu` y `mangaUpdates` no existen; los copiados usan `R`, `BuildConfig` e `injectLazy`).

- [ ] **Step 4: Adaptar**

- `Kitsu.kt` y `MangaUpdates.kt`:
  - `override fun getLogo() = R.drawable.brand_kitsu` pasa a `override fun getLogo() = "drawable/brand_kitsu.xml" // desktop: classpath resource`, y lo mismo para `brand_mangaupdates`;
  - quitar `import eu.kanade.tachiyomi.R`.
- `KitsuInterceptor.kt` y `MangaUpdatesInterceptor.kt`: `BuildConfig.VERSION_NAME` pasa a `DesktopBuildInfo.VERSION_NAME`, y `BuildConfig.APPLICATION_ID`, si aparece, a `DesktopBuildInfo.APPLICATION_ID`. Import `mihon.desktop.core.DesktopBuildInfo`.
- `injectLazy()` de `Json` se queda: el `DesktopInjektRegistrar` ya expone `Json`.
- `TrackerManager`: `val kitsu = Kitsu(3L)` y `val mangaUpdates = MangaUpdates(7L)`, y en la lista `listOf<Tracker>(kitsu, mangaUpdates)`. Se mantiene el orden de Mihon entre ellos.
- `TrackPreferences`: vuelve `kitsuScoreType`.

- [ ] **Step 5: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add desktop-app gradle scripts
git commit -m "feat(desktop-app): Kitsu and MangaUpdates trackers, Kitsu's GraphQL through Apollo

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: trackers enhanced (Komga, Kavita, Suwayomi)

**Files:**
- Copy: `eu/kanade/tachiyomi/data/track/komga/**`, `kavita/**` y `suwayomi/**`, y `app/src/main/res/drawable/brand_{komga,kavita,suwayomi}.xml` a `desktop-app/src/main/resources/drawable/`.
- Create: `desktop-app/src/test/kotlin/eu/kanade/tachiyomi/extension/all/komga/Komga.kt`, una fuente de test con el nombre de clase que Komga acepta.
- Modify: `TrackerManager.kt` y `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt` (`TestBindings.sourceManager`).
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/track/EnhancedTrackersTest.kt`

**Interfaces:**
- Consumes: de la Task 2, `AddTracks.bindEnhancedTrackers(manga, source)` y `TrackerManager`.
- Produces: `trackerManager.komga` (6), `kavita` (8) y `suwayomi` (9).

- [ ] **Step 1: Copiar**

```bash
A=app/src/main/java/eu/kanade/tachiyomi/data/track; D=desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/track
cp -r $A/komga $A/kavita $A/suwayomi $D/
cp app/src/main/res/drawable/brand_{komga,kavita,suwayomi}.xml desktop-app/src/main/resources/drawable/
```

- [ ] **Step 2: Escribir el test**

Fuente de test `eu/kanade/tachiyomi/extension/all/komga/Komga.kt`:

```kotlin
package eu.kanade.tachiyomi.extension.all.komga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.mockwebserver.MockWebServer

/** Stands for the Komga extension: the Komga tracker accepts a source by this class name. */
class Komga(private val server: MockWebServer) : HttpSource() {
    override val name = "Komga José"
    override val lang = "es"
    override val supportsLatest = false
    override val baseUrl: String get() = server.url("/").toString().removeSuffix("/")

    override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) = MangasPage(emptyList(), false)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = SMangaUpdate(manga, chapters)

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}
```

La firma de `HttpSource` se ajusta a la de `FakeHttpSource`, que es la que compila hoy. Se registra en `TestSources` (`val komga = Komga(komgaServer)`) y en `TestBindings.sourceManager`.

`EnhancedTrackersTest.kt`:

```kotlin
package mihon.desktop.app.track

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class EnhancedTrackersTest {
    private val graph: TestAppGraph = startTestApp()

    @Test
    fun `the three enhanced trackers are there with Mihon's ids, and need no sign-in`() {
        graph.trackerManager.get(6)!!.name shouldBe "Komga"
        graph.trackerManager.get(8)!!.name shouldBe "Kavita"
        graph.trackerManager.get(9)!!.name shouldBe "Suwayomi"
    }

    @Test
    fun `a Komga manga added to the library binds itself to the Komga tracker`(): Unit = runBlocking {
        val server = TestSources.komgaServer
        server.enqueueKomgaSeries(seriesId = "S1", readChapters = 3)
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "/api/v1/series/S1", title = "Serie José", source = TestSources.komga.id),
        )

        graph.addTracks.bindEnhancedTrackers(manga, TestSources.komga)

        val track = graph.trackRepository.getTracksByMangaId(manga.id).single()
        track.trackerId shouldBe 6L
    }
}
```

`TestSources.komgaServer` es un `MockWebServer` arrancado en `TestSources`. `enqueueKomgaSeries` es una extensión de test que encola las respuestas que pide `KomgaApi` al vincular: la serie y su progreso, con la forma de `KomgaModels.kt`. Se escribe en este paso, tras leer las rutas que llama `KomgaApi.getTrackSearch`.

- [ ] **Step 3: Comprobar que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.EnhancedTrackersTest"`
Expected: FAIL (`get(6)` es null y los copiados no compilan).

- [ ] **Step 4: Adaptar**

- `Komga.kt`, `Kavita.kt` y `Suwayomi.kt`: `getLogo()` devuelve `"drawable/brand_komga.xml"`, `"drawable/brand_kavita.xml"` y `"drawable/brand_suwayomi.xml"`, con `// desktop:`. Se quita `import eu.kanade.tachiyomi.R`.
- `KomgaApi.kt` y `KavitaInterceptor.kt`: `BuildConfig` pasa a `DesktopBuildInfo`.
- `SuwayomiApi.kt`: quitar `import android.content.SharedPreferences` y dejar que el tipo se infiera, con `// desktop:`.
- `TrackerManager`: `val komga = Komga(6L)`, `val kavita = Kavita(8L)` y `val suwayomi = Suwayomi(9L)`, y la lista en el orden de Mihon: `listOf<Tracker>(kitsu, komga, mangaUpdates, kavita, suwayomi) + extra`.

- [ ] **Step 5: Comprobar que pasa**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.*" --tests "mihon.desktop.app.migration.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): Komga, Kavita and Suwayomi trackers bind themselves to their sources

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: UI de seguimiento

**Files:**
- Copy:
  - `eu/kanade/presentation/track/*.kt` y `components/*.kt` (incluidos los `*PreviewProvider`);
  - `eu/kanade/tachiyomi/ui/manga/track/TrackInfoDialog.kt` y `TrackItem.kt`;
  - `eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt`.
- Modify:
  - `eu/kanade/presentation/more/settings/Preference.kt` y `PreferenceItem.kt` (vuelve `TrackerPreference`, con su widget);
  - `eu/kanade/tachiyomi/ui/manga/MangaScreen.kt` (`onTrackingClicked` y `Dialog.TrackSheet`);
  - `mihon/desktop/app/settings/SettingsTab.kt` (entrada Seguimiento);
  - `eu/kanade/tachiyomi/data/backup/BackupFileValidator.kt` (vuelven los "trackers sin sesión").
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/track/TrackingScreensTest.kt`

**Interfaces:**
- Consumes: de las Tasks 2, 3 y 4, `TrackerManager`, `Tracker.getLogo(): String` y `TestTrackers.fake`.
- Produces: `SettingsTrackingScreen` en Ajustes, y la hoja `TrackInfoDialogHomeScreen(mangaId, mangaTitle, sourceId)`.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.track

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestBackups
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.TestTrackers
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalTestApi::class)
class TrackingScreensTest {
    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 15_000) {
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `Settings › Tracking lists the trackers without OAuth and none with it`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppProviders(graph) { Navigator(SettingsTrackingScreen) { DefaultNavigatorScreenTransition(it) } } }

        listOf("Kitsu", "MangaUpdates", "Komga", "Kavita", "Suwayomi").forEach { waitForText(it) }
        listOf("AniList", "MyAnimeList", "Shikimori", "Bangumi").forEach {
            onAllNodes(hasText(it)).fetchSemanticsNodes().size shouldBe 0
        }
    }

    @Test
    fun `the manga screen's tracking button opens the sheet with the signed-in trackers`() = runComposeUiTest {
        val graph = startTestApp()
        runBlocking { TestTrackers.fake.login("josé", "clave") }
        val manga = runBlocking { TestLibrary.libraryManga(graph, TestLibrary.category(graph)) }
        setContent {
            AppProviders(graph) { Navigator(MangaScreen(manga.id)) { DefaultNavigatorScreenTransition(it) } }
        }

        waitUntil(15_000) {
            onAllNodes(androidx.compose.ui.test.hasContentDescription(str(MR.strings.manga_tracking_tab)))
                .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodes(hasText(str(MR.strings.manga_tracking_tab))).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodes(hasText(str(MR.strings.manga_tracking_tab))).fetchSemanticsNodes().firstOrNull()
            ?.let { onAllNodes(hasText(str(MR.strings.manga_tracking_tab)))[0].performClick() }
            ?: onNodeWithContentDescription(str(MR.strings.manga_tracking_tab)).performClick()
        waitForText("Falso José")
    }

    @Test
    fun `restoring tracks of a signed-out tracker warns about it`(): Unit = runBlocking {
        val graph = startTestApp()
        TestTrackers.fake.logout()
        val file = createTempDirectory("Avisos José").toFile().resolve("tracks.tachibk")
        TestBackups.write(
            file,
            Backup(
                listOf(
                    BackupManga(
                        source = TestSources.updatable.id,
                        url = "/t/1",
                        title = "Con track",
                        tracking = listOf(BackupTracking(syncId = 999, libraryId = 0, title = "x")),
                    ),
                ),
            ),
        )

        graph.backupFileValidator.validate(file).missingTrackers shouldBe listOf("Falso José")
    }
}
```

Se ajustan dos cosas a lo que muestre la cabecera de la ficha portada: el texto del botón de tracking (`MR.strings.manga_tracking_tab` o el que use `MangaInfoHeader`) y el nombre de la pantalla de manga (`MangaScreen(mangaId)`). Si cambian, se apunta como ruling.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.TrackingScreensTest"`
Expected: FAIL. `SettingsTrackingScreen` no existe, el botón de tracking es `null` y el validador devuelve `emptyList()`.

- [ ] **Step 3: Copiar y adaptar**

```bash
A=app/src/main/java/eu/kanade; D=desktop-app/src/main/kotlin/eu/kanade
mkdir -p $D/presentation/track/components $D/tachiyomi/ui/manga/track
cp $A/presentation/track/*.kt $D/presentation/track/
cp $A/presentation/track/components/*.kt $D/presentation/track/components/
cp $A/tachiyomi/ui/manga/track/*.kt $D/tachiyomi/ui/manga/track/
cp $A/presentation/more/settings/screen/SettingsTrackingScreen.kt $D/presentation/more/settings/screen/
```

- **`TrackLogoIcon.kt`:** `painterResource(id = tracker.getLogo())` pasa a:

```kotlin
    // desktop: Mihon's vector drawable from the classpath (plan 5b-1, dev. 3)
    val density = LocalDensity.current
    val logo = remember(tracker.getLogo(), density) {
        Thread.currentThread().contextClassLoader.getResourceAsStream(tracker.getLogo())!!.use {
            loadXmlImageVector(InputSource(it), density)
        }
    }
    val painter = rememberVectorPainter(logo)
```

  Con los imports `androidx.compose.ui.res.loadXmlImageVector`, `org.xml.sax.InputSource`, `androidx.compose.ui.platform.LocalDensity` y `androidx.compose.ui.graphics.vector.rememberVectorPainter`. Si `loadXmlImageVector` no existe en esta versión de CMP, se usa `androidx.compose.ui.res.painterResource(path)`, que acepta XML vectorial en desktop, y se apunta como ruling.
- **`TrackerSearch.kt`:** el `ClipData` de Android para copiar al portapapeles pasa a `ClipEntry` de AWT:

```kotlin
                        // desktop: an AWT transferable instead of Android's ClipData
                        clipboard.setClipEntry(ClipEntry(java.awt.datatransfer.StringSelection(text)))
```

  Se quita `import android.content.ClipData` y `toClipEntry`.
- **`TrackInfoDialog.kt`:** `copyToClipboard`, `openInBrowser` y `toast` ya existen en escritorio. Se mantienen los imports `eu.kanade.tachiyomi.util.system.*`. Solo se arregla lo que no compile, con `// desktop:`.
- **`SettingsTrackingScreen.kt`:**
  - Se borran los `trackerPreference` de los trackers OAuth (AniList, MyAnimeList, Shikimori, Bangumi, Comick, MangaBaka, Hikka) y sus imports `…Api`, con `// desktop: OAuth trackers arrive in 5b-2`.
  - Se quitan las opciones que leen `anilistScoreType` y `mangabakaScoreType`.
  - Se quedan Kitsu y MangaUpdates (diálogo de usuario y contraseña), los enhanced y las opciones generales (`autoUpdateTrack`, `autoUpdateTrackOnMarkRead`).
- **`Preference.kt` y `PreferenceItem.kt`:** vuelven el `TrackerPreference` de Mihon y su rama en `PreferenceItem`, en lugar de los comentarios `// desktop: no TrackerPreference until trackers`. El widget `TrackingPreferenceWidget` se copia de `app/src/main/java/eu/kanade/presentation/more/settings/widget/` si no está.
- **`ui/manga/MangaScreen.kt`:**
  - `onTrackingClicked = null, // desktop: trackers arrive in subproject 5` pasa a:

```kotlin
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    // desktop: Settings › Tracking directly, there's no SettingsScreen destination
                    navigator.push(SettingsTrackingScreen)
                } else {
                    viewModel.showTrackDialog()
                }
            },
```

  - `// desktop: no TrackSheet dialog until subproject 5` pasa a la rama de Mihon:

```kotlin
            MangaViewModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = TrackInfoDialogHomeScreen(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is TrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
```

    El `else -> {}` se queda si todavía quedan ramas sin cubrir.
- **`SettingsTab.kt`:** tras la entrada de Descargas, `Triple(MR.strings.pref_category_tracking, MaterialSymbols.Rounded.Sync, SettingsTrackingScreen)`, con el icono que use Mihon en `SettingsMainScreen` para Seguimiento.
- **`BackupFileValidator.kt`:** vuelve el bloque de Mihon que reúne los `syncId` y calcula `missingTrackers` con `trackerManager.get(it.toLong())` y `!it.isLoggedIn`. Los ids que no están en `TrackerManager` (los OAuth hasta 5b-2) se ignoran, como hace Mihon con `mapNotNull`. Se quita el comentario `// desktop: there are no trackers until subproject 5b`.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.*" --tests "mihon.desktop.app.backup.*" --tests "mihon.desktop.app.browse.*" --tests "mihon.desktop.app.settings.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

Run: `bash scripts/check-no-android.sh`
Expected: OK.

- [ ] **Step 5: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): Mihon's tracking sheet, Settings › Tracking and the restore warning for signed-out trackers

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: sincronizar desde el lector, e incógnito

**Files:**
- Modify:
  - `eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`;
  - `eu/kanade/presentation/library/components/LibraryToolbar.kt` y `eu/kanade/tachiyomi/ui/library/LibraryTab.kt`;
  - `mihon/desktop/app/tray/AppTray.kt`;
  - `mihon/desktop/app/main/AppContent.kt`.
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/track/ReaderTrackingTest.kt`

**Interfaces:**
- Consumes: de la Task 2, `TrackChapter`, `TrackPreferences.autoUpdateTrack` y `TestTrackers.fake`.
- Produces: `ReaderViewModel` sube el capítulo terminado, salvo en incógnito. Hay interruptores de incógnito en la Biblioteca y en la bandeja, y el banner lee `basePreferences.incognitoMode`.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.track

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestTrackers
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class ReaderTrackingTest {
    private val graph: TestAppGraph = startTestApp()

    private suspend fun linkedManga(remoteId: Long) = TestLibrary.httpManga(graph).also { (manga, _) ->
        TestTrackers.fake.login("josé", "clave")
        graph.trackRepository.upsert(
            Track(
                id = 0, mangaId = manga.id, trackerId = TestTrackers.fake.id, remoteId = remoteId, libraryId = null,
                title = manga.title, lastChapterRead = 0.0, totalChapters = 0, status = 1, score = 0.0,
                remoteUrl = "", startDate = null, finishDate = null, private = false,
            ),
        )
    }

    @Test
    fun `finishing a chapter in the reader updates the tracker, unless auto update is off or incognito is on`(): Unit =
        runBlocking {
            val (manga, chapters) = linkedManga(remoteId = 501)
            graph.readTo(manga, chapters[0], lastPage = true)
            withTimeout(10.seconds) { while (TestTrackers.fake.remote[501] != chapters[0].chapterNumber) delay(100) }

            graph.trackPreferences.autoUpdateTrack.set(false)
            graph.readTo(manga, chapters[1], lastPage = true)
            delay(1_000)
            TestTrackers.fake.remote[501] shouldBe chapters[0].chapterNumber
            graph.trackPreferences.autoUpdateTrack.set(true)

            graph.basePreferences.incognitoMode.set(true)
            graph.readTo(manga, chapters[2], lastPage = true)
            delay(1_000)
            TestTrackers.fake.remote[501] shouldBe chapters[0].chapterNumber
            graph.getHistory.await(manga.id).none { it.chapterId == chapters[2].id } shouldBe true
            graph.basePreferences.incognitoMode.set(false)
        }

    @Test
    fun `incognito from the library menu shows Mihon's banner`() = runComposeUiTest {
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        graph.basePreferences.incognitoMode.set(true)

        waitUntil(10_000) { onAllNodes(hasText(str(MR.strings.pref_incognito_mode))).fetchSemanticsNodes().isNotEmpty() }
        graph.basePreferences.incognitoMode.set(false)
    }
}
```

`graph.readTo(manga, chapter, lastPage = true)` es un helper de test que abre el `ReaderViewModel` del capítulo, va a la última página y llama a `saveHistory()`/`onCleared`, como hacen los tests del lector de 2a y 4b (`DownloadedReaderTest`). Si ya existe con otro nombre, se reutiliza; si no, se crea en `mihon/desktop/app/testing/` copiando el patrón de `DownloadedReaderTest`.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.ReaderTrackingTest"`
Expected: FAIL. El lector no llama a `TrackChapter` (el `withTimeout` se agota) y el banner no sale, porque `AppContent` pasa `incognitoMode = false`.

- [ ] **Step 3: Implementación**

`ReaderViewModel.kt`:
- Inyectar `private val trackChapter: TrackChapter` y `private val trackPreferences: TrackPreferences`.
- La función de Mihon:

```kotlin
    // desktop: Mihon's, in the desktop reader's progress writes (spec 5b-1 §3 B, G)
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (incognitoMode) return
        if (!trackPreferences.autoUpdateTrack.get()) return
        val manga = manga ?: return

        // Reading the last page and leaving right away is common, so it outlives the reader
        appScope.launch {
            trackChapter.await(context, manga.id, readerChapter.chapter.chapterNumber)
        }
    }
```

- En `writeChapterProgress`, dentro de `if (onComplete)`, después de `deleteChapterIfNeeded(readerChapter)`: `updateTrackChapterRead(readerChapter)`.
- Si el `ReaderViewModel` de escritorio no tiene `context`, se inyecta `Context` como en los demás ViewModels.

`LibraryToolbar.kt`, como `onClickDownloadedOnly` en 4b:
- nuevo parámetro `onClickIncognito: () -> Unit` en las dos funciones;
- un `AppBar.OverflowAction(title = stringResource(MR.strings.pref_incognito_mode), onClick = onClickIncognito)` tras el de "Solo descargados", con `// desktop: Mihon's More tab switch (spec 5b-1 §1)`.

`LibraryTab.kt`: `onClickIncognito = { context.appGraph.basePreferences.incognitoMode.toggle() }`.

`AppTray.kt`, antes del `Separator()`:

```kotlin
            // desktop: incognito without opening the window (spec 5b-1 §1)
            CheckboxItem(
                context.stringResource(MR.strings.pref_incognito_mode),
                checked = incognito,
                onCheckedChange = { graph.basePreferences.incognitoMode.set(it) },
            )
```

con `val incognito by remember { graph.basePreferences.incognitoMode.changes() }.collectAsState(graph.basePreferences.incognitoMode.get())` en el composable `AppTray`.

`AppContent.kt`:
- `incognitoMode = false` pasa a `incognitoMode = incognito`, con `val incognito by remember { graph.basePreferences.incognitoMode.changes() }.collectAsState(graph.basePreferences.incognitoMode.get())`.
- El comentario pasa a `// desktop: Mihon's banners over the whole window (MainActivity)`.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.*" --tests "mihon.desktop.app.reader.*" --tests "mihon.desktop.app.download.*" --tests "mihon.desktop.app.library.*" --tests "mihon.desktop.app.tray.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): the reader updates trackers on a finished chapter; incognito from the library and the tray

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: notas de manga

**Files:**
- Copy: `eu/kanade/presentation/manga/MangaNotesScreen.kt`, `eu/kanade/tachiyomi/ui/manga/notes/MangaNotesScreen.kt` y `eu/kanade/presentation/manga/components/MangaNotesTextArea.kt`.
- Modify:
  - `desktop-app/build.gradle.kts` (`implementation(libs.composeRichEditor)`);
  - `eu/kanade/tachiyomi/ui/manga/MangaScreen.kt` (`onEditNotesClicked`);
  - los comentarios `// desktop: null until notes (subproject 5)` de `MangaInfoHeader.kt`, `MangaToolbar.kt` y `presentation/manga/MangaScreen.kt`.
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/manga/MangaNotesTest.kt`

**Interfaces:**
- Consumes: el `UpdateMangaNotes` de Mihon (`tachiyomi.domain.manga.interactor`), si existe en domain; si no, se copia de `app/`.
- Produces: `ui.manga.notes.MangaNotesScreen(manga: Manga)`.

- [ ] **Step 1: Escribir el test**

```kotlin
package mihon.desktop.app.manga

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class MangaNotesTest {
    @Test
    fun `a note typed on the notes screen is saved to the manga`() = runComposeUiTest {
        val graph = startTestApp()
        val manga = runBlocking { TestLibrary.libraryManga(graph, TestLibrary.category(graph)) }
        setContent {
            AppProviders(graph) { Navigator(MangaNotesScreen(manga)) { DefaultNavigatorScreenTransition(it) } }
        }

        waitUntil(15_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction()).performTextInput("Nota de José")

        waitUntil(10_000) { runBlocking { graph.getManga.await(manga.id)!!.notes }.contains("Nota de José") }
        runBlocking { graph.getManga.await(manga.id)!!.notes } shouldContain "Nota de José"
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.manga.MangaNotesTest"`
Expected: FAIL de compilación (`ui.manga.notes.MangaNotesScreen` no existe).

- [ ] **Step 3: Copiar y conectar**

```bash
A=app/src/main/java/eu/kanade; D=desktop-app/src/main/kotlin/eu/kanade
mkdir -p $D/tachiyomi/ui/manga/notes
cp $A/presentation/manga/MangaNotesScreen.kt $D/presentation/manga/
cp $A/tachiyomi/ui/manga/notes/MangaNotesScreen.kt $D/tachiyomi/ui/manga/notes/
cp $A/presentation/manga/components/MangaNotesTextArea.kt $D/presentation/manga/components/
```

- `desktop-app/build.gradle.kts`: `implementation(libs.composeRichEditor)`. Si `check-licenses` la pide, se añade (Apache-2.0).
- `ui/manga/MangaScreen.kt`: `onEditNotesClicked = null, // desktop: notes arrive in subproject 5` pasa a `onEditNotesClicked = { navigator.push(MangaNotesScreen(manga = successState.manga)) },`.
- En `MangaInfoHeader.kt`, `MangaToolbar.kt` y `presentation/manga/MangaScreen.kt`, se quita el `// desktop: null until notes (subproject 5)` de los parámetros, porque ya no son null.
- Lo que no compile de los copiados (por ejemplo, el guardado al salir con `BackHandler`) se adapta con `// desktop:` y se apunta como ruling.

- [ ] **Step 4: Comprobar que pasa**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.manga.*" --tests "mihon.desktop.app.browse.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop-app gradle scripts
git commit -m "feat(desktop-app): Mihon's manga notes with the rich text editor

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: verificación completa y prueba manual

**Files:**
- Modify: ninguno, salvo los arreglos que salgan.

- [ ] **Step 1: Suite y checks**

Run: `./gradlew test spotlessCheck`
Expected: BUILD SUCCESSFUL, con unos 470 tests (los 450 de antes más los nuevos).

Run: `bash scripts/check-no-android.sh` y `bash scripts/check-licenses.sh`
Expected: los dos OK.

- [ ] **Step 2: Sin referencias pendientes**

Run: `grep -rn "subproject 5\b\|until trackers\|until notes\|trackers arrive\|notes arrive\|without incognito" desktop-app/src desktop-core/src --include=*.kt`
Expected: nada. Run: `ls desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/track`. Expected: no existe.

- [ ] **Step 3: Arranque real**

Run: `./gradlew :desktop-app:run` con `MANGAREADER_HOME` en una carpeta temporal, durante 2 minutos.
Expected: el log no tiene errores de Metro, de Apollo ni de `DelayedTracking`.

- [ ] **Step 4: Commit de los arreglos (si los hay)**

```bash
git add -A desktop-app desktop-core
git commit -m "chore: tidy the last 5b-1 notes

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 5: Prueba manual (spec §5), para el usuario**

1. Iniciar sesión en Kitsu o MangaUpdates con una cuenta real.
2. Vincular un manga desde la hoja (buscar y elegir), leer un capítulo y verlo actualizado en la web del tracker.
3. Con la red desconectada, leer un capítulo, reconectar y ver el cambio subido (unos minutos después).
4. Cerrar la app con un cambio pendiente, volver a abrirla y ver que se sube.
5. Con Komga, Kavita o Suwayomi: añadir un manga de esa fuente y verlo vinculado solo.
6. Escribir una nota con formato y verla en la ficha.
7. Activar incógnito desde la bandeja, leer y comprobar que no queda historial y que el banner se ve.
