# Subproyecto 5a: backup y pantalla de datos — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `.tachibk` compatible con Mihon Android en los dos sentidos, backup manual y automático, y la pantalla Ajustes › Datos de Mihon completa.

**Architecture:** se copia `data/backup/` de Mihon con su paquete y se cambia solo lo de Android. Las rutas pasan de `Uri` a `java.io.File`. Los workers de WorkManager se sustituyen por un `BackupJob` en el scope de la app, que tiene un único hueco para crear o restaurar y se programa con el `JobScheduler` de 3a. Las notificaciones van por `TrayNotifier`, y el progreso de la restauración se muestra en la pantalla. Además, `JavaSharedPreferences` de AndroidCompat pasa a guardar el tipo de cada clave: sin eso, el backup de preferencias no es compatible.

**Tech Stack:** Kotlin/JVM, Compose Desktop, Metro, kotlinx-serialization-protobuf, okio (gzip), SQLDelight (módulo `data` de Mihon), JUnit 5 + kotest.

**Spec:** `docs/superpowers/specs/2026-10-23-subproyecto-5a-backup-datos-design.md`

## Global Constraints

- Ficheros de Mihon: se copian de `app/src/main/java/<ruta>` a `desktop-app/src/main/kotlin/<ruta>` con el mismo paquete. Cada cambio lleva un comentario `// desktop:`.
- `scripts/check-no-android.sh` debe pasar. De Android solo se permiten `android.content.Context`, `android.widget.Toast`, `androidx.compose|lifecycle|paging|preference|navigationevent` y `androidx.activity.compose.BackHandler`.
- Strings nuevos con prefijo `desktop_`, en el bloque `<!-- Desktop -->` de `base/strings.xml` y de `es/strings.xml`. `SpanishStringsTest` exige que todos los strings usados tengan traducción al español.
- Tests escritos como `= runBlocking {}` declaran `: Unit`, porque si no JUnit los salta.
- Ficheros con `\\` se escriben con Write o Edit, no con un heredoc de Bash.
- Commits con el trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`. Nunca se hace push a `upstream`.
- Los backups automáticos van a `<almacenamiento>\autobackup`, se conservan los 4 últimos y el intervalo por defecto es de 12 h (spec §1).
- El `.tachibk` es gzip + protobuf con los `@ProtoNumber` de Mihon, sin cambios en los modelos (spec §2).
- CSV en UTF-8 con BOM (spec §3 G).

## Desviaciones respecto a la spec

1. **Tipos de las preferencias (§3 D).**
   - `JavaSharedPreferences.getAll()` de AndroidCompat devuelve todos los valores como `String`, y los sets aparecen partidos en `clave.0`, `clave.size`… Con eso, `PreferenceBackupCreator` escribiría cada preferencia como `StringPreferenceValue`. Mihon Android las descartaría al restaurar, porque comprueba el tipo, y aquí no se restauraría ninguna que no sea texto.
   - Arreglo (Task 1): se guarda el tipo de cada clave junto al valor, y `getAll()` devuelve los valores con su tipo, como en Android.
2. **`storage_dir` (§3 D).** En Mihon ya es una clave de estado (`Preference.appStateKey("storage_dir")`), y las claves de estado nunca entran en el backup. Lo que añade el escritorio es que `PreferenceRestorer` también ignora las claves de estado al restaurar, por si un backup antiguo o manipulado las trae.
3. **Rutas (§2).** Se usa `java.io.File` en lugar de `UniFile`: todo son ficheros locales y `UniFile` no aporta nada aquí.
4. **Marca del programador (§3 B).** `JobScheduler` guarda `lastRun` antes de ejecutar el job. Si se le pasara `lastAutoBackupTimestamp`, cambiaría aunque el backup fallara. El job usa una marca propia (`desktop_last_auto_backup_attempt`, clave de estado), y `BackupCreator` sigue moviendo `lastAutoBackupTimestamp` solo si el backup sale bien.
5. **Rotación (§3 B).** Mihon borra los backups viejos antes de crear el nuevo, así que si falla pierde uno. Aquí se borran después de crear el nuevo con éxito, y se conservan igualmente 4.
6. **Exclusión (§3 E).** Durante una restauración esperan los jobs automáticos: el backup automático y la actualización periódica de la biblioteca. La actualización manual (botón o bandeja) sigue permitida, como en Mihon, porque es una decisión explícita del usuario.
7. **Disco lleno (§5).** El seam `DiskUtil.usableSpace` no afecta a la escritura del backup. El test de "nada a medias" provoca el fallo con un temporal que no se puede escribir.

## Review Focus

1. Restaurar el mismo backup dos veces no duplica mangas, capítulos ni historial (Mihon fusiona). Test en la Task 3.
2. Un backup de Android con una clave que aquí es de otro tipo (por ejemplo, un `Int` donde el escritorio guarda un `String`) no la sobrescribe ni rompe el arranque. Test en la Task 3.
3. Rutas con caracteres no ASCII (`José`) en el destino del backup y en el almacenamiento. Los tests usan carpetas temporales con acentos.
4. Una restauración grande (cientos de mangas) avanza, termina y mantiene la exclusión. Test en la Task 4 con 400 mangas.
5. Cerrar la app a mitad de una restauración deja una base de datos usable al volver. Sin test automático: va a la prueba manual (Task 8).

---

### Task 1: AndroidCompat guarda el tipo de cada preferencia

**Files:**
- Modify: `android-compat/src/main/java/xyz/nulldev/androidcompat/io/sharedprefs/JavaSharedPreferences.kt`
- Test: `android-compat/src/test/kotlin/AndroidCompatBootTest.kt`

**Interfaces:**
- Produces: `SharedPreferences.getAll()` devuelve `Int`, `Long`, `Float`, `Double`, `Boolean`, `String` o `Set<String>` según lo que se guardó, sin las claves internas. La Task 2 (`PreferenceBackupCreator`) y la Task 3 (`PreferenceRestorer`) dependen de esto.

- [ ] **Step 1: Escribir los tests**

En `AndroidCompatBootTest`, después del test de listeners (imports nuevos: `xyz.nulldev.androidcompat.io.sharedprefs.JavaSharedPreferences`, `xyz.nulldev.ts.config.ApplicationRootDir`, `java.util.Properties`, `kotlin.io.path.Path`, `kotlin.io.path.createParentDirectories`, `kotlin.io.path.outputStream`):

```kotlin
    @Test
    fun `getAll gives each value its type, as on Android, also after reloading from disk`() {
        val prefs = context.getSharedPreferences("typed_all", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("s", "12")
            .putInt("i", 12)
            .putLong("l", 12L)
            .putFloat("f", 1.5f)
            .putBoolean("b", true)
            .putStringSet("set", setOf("a", "b"))
            .commit()
        val expected = mapOf("s" to "12", "i" to 12, "l" to 12L, "f" to 1.5f, "b" to true, "set" to setOf("a", "b"))

        prefs.all shouldBe expected
        // A new instance reads the file the first one wrote
        JavaSharedPreferences("typed_all").all shouldBe expected
    }

    @Test
    fun `a removed key leaves no trace in getAll`() {
        val prefs = context.getSharedPreferences("typed_remove", Context.MODE_PRIVATE)
        prefs.edit().putInt("i", 1).putStringSet("set", setOf("a")).putString("kept", "x").commit()

        prefs.edit().remove("i").remove("set").commit()

        prefs.all shouldBe mapOf("kept" to "x")
    }

    @Test
    fun `a key saved before the types were kept still reads, as text`() {
        val file = Path(ApplicationRootDir, "settings", "legacy_untyped.xml")
        file.createParentDirectories()
        val old = Properties().apply { setProperty("old", "x") }
        file.outputStream().use { old.storeToXML(it, null) }

        JavaSharedPreferences("legacy_untyped").all shouldBe mapOf("old" to "x")
    }
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :android-compat:test --tests "AndroidCompatBootTest"`
Expected: FAIL. El primero falla porque `getAll` devuelve `"12"` en `i` y trae `set.0` y `set.size`. El segundo, porque quedan claves de los sets. El tercero pasa ya: comprueba la compatibilidad hacia atrás.

- [ ] **Step 3: Implementación**

En `JavaSharedPreferences.kt`:

1. Sustituir `getAll()` (línea 84, con su `// TODO`) por:

```kotlin
    // desktop: Android's getAll() gives each value its type, which a backup keeps (spec 5a §3 D). Properties only
    // stores text, so the type of each key is saved beside it, under TYPE_PREFIX
    override fun getAll(): MutableMap<String, *> {
        val keys = preferences.keys
        val types = keys
            .filter { it.startsWith(TYPE_PREFIX) }
            .associate { it.removePrefix(TYPE_PREFIX) to preferences.getString(it, "") }
        val setKeys = types.filterValues { it == TYPE_SET }.keys
        val all = mutableMapOf<String, Any?>()
        types.forEach { (key, type) ->
            all[key] = when (type) {
                TYPE_INT -> preferences.getInt(key, 0)
                TYPE_LONG -> preferences.getLong(key, 0L)
                TYPE_FLOAT -> preferences.getFloat(key, 0f)
                TYPE_DOUBLE -> preferences.getDouble(key, 0.0)
                TYPE_BOOLEAN -> preferences.getBoolean(key, false)
                TYPE_SET -> getStringSet(key, null)
                else -> preferences.getStringOrNull(key)
            }
        }
        // ponytail: keys saved before the types were kept read as text, as they did; a set from then shows as its parts
        keys
            .filter { key -> !key.startsWith(TYPE_PREFIX) && key !in types && setKeys.none { key.startsWith("$it.") } }
            .forEach { all[it] = preferences.getStringOrNull(it) }
        return all
    }
```

2. En `saveAction`, rama `is Action.Add`, justo antes de `if (shouldNotify) notify(action.key)`:

```kotlin
                // desktop: the type getAll() gives back
                preferences.putString(TYPE_PREFIX + action.key, typeOf(action.value))
```

3. En la rama `is Action.Remove`, después de `preferences.remove(action.key)`:

```kotlin
                preferences.remove(TYPE_PREFIX + action.key) // desktop: and its type
```

4. En el `companion object`, después de `logger`:

```kotlin
        // desktop: where getAll() finds each key's type (spec 5a §3 D)
        private const val TYPE_PREFIX = "__type."
        private const val TYPE_STRING = "string"
        private const val TYPE_INT = "int"
        private const val TYPE_LONG = "long"
        private const val TYPE_FLOAT = "float"
        private const val TYPE_DOUBLE = "double"
        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_SET = "set"

        private fun typeOf(value: Any): String = when (value) {
            is Set<*> -> TYPE_SET
            is Int -> TYPE_INT
            is Long -> TYPE_LONG
            is Float -> TYPE_FLOAT
            is Double -> TYPE_DOUBLE
            is Boolean -> TYPE_BOOLEAN
            else -> TYPE_STRING
        }
```

La cabecera MPL del fichero se queda como está.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :android-compat:test`
Expected: PASS, con los 6 tests de `AndroidCompatBootTest`.

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.*"`
Expected: PASS. `DownloadStore` y `DownloadPendingDeleter` leen `preferences.all` como texto, y siguen leyéndolo igual porque guardan strings.

- [ ] **Step 5: Commit**

```bash
git add android-compat
git commit -m "fix(android-compat): SharedPreferences.getAll returns each value with its type, as on Android

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: formato `.tachibk` y creación del backup

**Files:**
- Copy (sin cambios): `eu/kanade/tachiyomi/data/backup/models/*.kt`, `BackupStream.kt`, `create/BackupOptions.kt` y `create/creators/*.kt`.
- Copy and modify: `eu/kanade/tachiyomi/data/backup/BackupDecoder.kt`, `BackupFileValidator.kt` y `create/BackupCreator.kt`.
- Copy test: `app/src/test/java/eu/kanade/tachiyomi/data/backup/BackupStreamTest.kt` → `desktop-app/src/test/kotlin/eu/kanade/tachiyomi/data/backup/BackupStreamTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestBackups.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/backup/BackupCreatorTest.kt`

**Interfaces:**
- Consumes: de la Task 1, el `getAll()` con tipos.
- Produces:
  - `BackupDecoder.decodeMetadata(file: File): BackupDecoder.Metadata`.
  - `BackupDecoder.decodeEntries(file: File, include: (Int) -> Boolean = { true }): Flow<BackupManga>`.
  - `BackupFileValidator.validate(file: File): BackupFileValidator.Results`.
  - `BackupCreator.Factory.create(isAutoBackup: Boolean)` y `BackupCreator.backup(file: File, options: BackupOptions): String`. En el manual, `file` es el destino; en el automático, la carpeta. Devuelve la ruta escrita.
  - `BackupCreator.getFilename()`: `app.mangareader_yyyy-MM-dd_HH-mm.tachibk`.
  - `TestAppGraph`: `trackRepository`, `backupDecoder`, `backupCreatorFactory`, `getFavorites`.
  - `TestBackups.write(file: File, backup: Backup)`.

- [ ] **Step 1: Copiar los ficheros de Mihon**

```bash
A=app/src/main/java/eu/kanade/tachiyomi/data/backup
D=desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/backup
mkdir -p $D/models $D/create/creators desktop-app/src/test/kotlin/eu/kanade/tachiyomi/data/backup
cp $A/models/*.kt $D/models/
cp $A/BackupStream.kt $A/BackupDecoder.kt $A/BackupFileValidator.kt $D/
cp $A/create/BackupOptions.kt $A/create/BackupCreator.kt $D/create/
cp $A/create/creators/*.kt $D/create/creators/
cp app/src/test/java/eu/kanade/tachiyomi/data/backup/BackupStreamTest.kt desktop-app/src/test/kotlin/eu/kanade/tachiyomi/data/backup/
```

- [ ] **Step 2: Escribir los tests**

`TestBackups.kt`:

```kotlin
package mihon.desktop.app.testing

import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.sink
import java.io.File

/** Backups written straight from Mihon's models, as Mihon Android writes them. */
object TestBackups {
    fun write(file: File, backup: Backup) {
        file.outputStream().sink().gzip().buffer().use {
            it.write(ProtoBuf.encodeToByteArray(Backup.serializer(), backup))
        }
    }
}
```

En `TestAppGraph`, accessors nuevos (con sus imports): `val trackRepository: TrackRepository`, `val backupDecoder: BackupDecoder`, `val backupCreatorFactory: BackupCreator.Factory` y `val getFavorites: GetFavorites`.

`BackupCreatorTest.kt`:

```kotlin
package mihon.desktop.app.backup

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.track.model.Track
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.io.path.createTempDirectory
import kotlin.time.Instant

class BackupCreatorTest {
    private val graph: TestAppGraph = startTestApp()
    private val dir = createTempDirectory("Copias José").toFile()

    private suspend fun backup(name: String, options: BackupOptions = BackupOptions()): File {
        val file = File(dir, name)
        graph.backupCreatorFactory.create(isAutoBackup = false).backup(file, options) shouldBe file.path
        return file
    }

    @Test
    fun `a backup holds the library with its chapters, categories, history and tracks, and decodes back`(): Unit =
        runBlocking {
            val category = TestLibrary.category(graph)
            val categoryName = graph.getCategories.await().first { it.id == category }.name
            val manga = TestLibrary.libraryManga(graph, category)
            val chapter = graph.getChaptersByMangaId.await(manga.id).first()
            graph.updateChapter.await(ChapterUpdate(chapter.id) { read = true })
            graph.upsertHistory.await(HistoryUpdate(chapter.id, Instant.fromEpochMilliseconds(1_700_000_000_000), 60_000))
            graph.trackRepository.upsert(
                Track(
                    id = 0, mangaId = manga.id, trackerId = 2, remoteId = 7, libraryId = null, title = manga.title,
                    lastChapterRead = 1.0, totalChapters = 10, status = 1, score = 8.0,
                    remoteUrl = "https://example.org/7", startDate = null, finishDate = null, private = false,
                ),
            )

            val file = backup("completo.tachibk")

            val entry = graph.backupDecoder.decodeEntries(file).toList().single { it.url == manga.url }
            entry.title shouldBe manga.title
            entry.chapters.single { it.url == chapter.url }.read shouldBe true
            entry.history.single().readDuration shouldBe 60_000
            entry.tracking.single().trackingUrl shouldBe "https://example.org/7"
            val metadata = graph.backupDecoder.decodeMetadata(file).backup
            metadata.backupCategories.map { it.name } shouldContain categoryName
            metadata.backupSources.map { it.sourceId } shouldContain manga.source
        }

    @Test
    fun `the file is gzip and Mihon's whole-backup decoding reads it`(): Unit = runBlocking {
        TestLibrary.libraryManga(graph, TestLibrary.category(graph))

        val file = backup("formato.tachibk")

        val bytes = file.readBytes()
        (bytes[0].toInt() and 0xff) shouldBe 0x1f
        (bytes[1].toInt() and 0xff) shouldBe 0x8b
        val proto = GZIPInputStream(file.inputStream()).use { it.readBytes() }
        // The entries are Backup's field 1, length-delimited: tag 0x0a
        proto[0].toInt() shouldBe 0x0a
        ProtoBuf.decodeFromByteArray(Backup.serializer(), proto).backupManga.isNotEmpty() shouldBe true
    }

    @Test
    fun `settings keep their types, app state never travels, private keys only with their option`(): Unit =
        runBlocking {
            TestLibrary.libraryManga(graph, TestLibrary.category(graph))
            graph.backupPreferences.backupInterval.set(24)
            val token = Preference.privateKey("desktop_test_token")
            graph.preferenceStore.getString(token).set("secreto")

            val plain = graph.backupDecoder.decodeMetadata(backup("ajustes.tachibk")).backup.backupPreferences
            val withPrivate = graph.backupDecoder.decodeMetadata(
                backup("privados.tachibk", BackupOptions(privateSettings = true)),
            ).backup.backupPreferences

            plain.single { it.key == "backup_interval" }.value shouldBe IntPreferenceValue(24)
            plain.map { it.key } shouldNotContain Preference.appStateKey("storage_dir")
            plain.map { it.key } shouldNotContain token
            withPrivate.map { it.key } shouldContain token
            graph.backupPreferences.backupInterval.set(12)
        }

    @Test
    fun `a backup that can't be written fails and keeps the file it was replacing`(): Unit = runBlocking {
        TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        val target = File(dir, "existente.tachibk").apply { writeText("anterior") }
        // A folder where the temporary file goes: writing it fails
        File(dir, "existente.tachibk.tmp/bloqueo").apply { parentFile.mkdirs() }.writeText("x")

        shouldThrowAny { graph.backupCreatorFactory.create(isAutoBackup = false).backup(target, BackupOptions()) }

        target.readText() shouldBe "anterior"
    }
}
```

`graph.backupPreferences` y `graph.preferenceStore` se resuelven en este paso. `preferenceStore` ya está en `DesktopGraph`. `backupPreferences` se añade a `AppGraph` en la Task 3; para que este test compile, añadirlo ya aquí: `val backupPreferences: BackupPreferences` en `mihon.app.di.AppGraph`.

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.backup.BackupCreatorTest" --tests "eu.kanade.tachiyomi.data.backup.BackupStreamTest"`
Expected: FAIL de compilación. Los ficheros copiados usan `android.net.Uri`, `TrackerManager.get`, `BuildConfig` y `UniFile.fromUri`.

- [ ] **Step 4: Adaptar los ficheros copiados**

`BackupDecoder.kt`:
- Imports: quitar `android.net.Uri` y añadir `java.io.File`.
- `fun decodeMetadata(uri: Uri)` pasa a `fun decodeMetadata(file: File)`, y su `read(uri)` a `read(file)`.
- `fun decodeEntries(uri: Uri, …)` pasa a `fun decodeEntries(file: File, …)`, y su `read(uri)` a `read(file)`.
- `private inline fun <T> read(uri: Uri, …)` pasa a `private inline fun <T> read(file: File, …)`, y su primera línea a:

```kotlin
        // desktop: a local file instead of a content uri
        return file.inputStream().use { inputStream ->
```

`BackupFileValidator.kt`:
- Imports: quitar `android.net.Uri` y `eu.kanade.tachiyomi.data.track.TrackerManager`, y añadir `java.io.File`.
- Constructor: quitar `private val trackerManager: TrackerManager,`.
- `validate`:

```kotlin
    suspend fun validate(file: File): Results {
        val backup = try {
            // desktop: the entries are still read, so a broken one fails here, before anything is restored
            backupDecoder.decodeEntries(file).collect {}
            backupDecoder.decodeMetadata(file).backup
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
```

  Se mantiene el bloque `sources` / `missingSources`. Se borra el bloque `missingTrackers`, y el `return` queda así:

```kotlin
        // desktop: there are no trackers until subproject 5b, so none can be missing
        return Results(missingSources, missingTrackers = emptyList())
```

`create/BackupCreator.kt`:
- Imports: quitar `android.net.Uri`, `com.hippo.unifile.UniFile`, `eu.kanade.tachiyomi.BuildConfig` y `java.io.FileOutputStream`. Añadir `mihon.desktop.core.DesktopBuildInfo`, `java.io.File`, `java.nio.file.Files` y `java.nio.file.StandardCopyOption`.
- Sustituir `backup(…)` entero por:

```kotlin
    /**
     * desktop: [file] is the destination, or the folder for an automatic backup. The backup is written next to it
     * as a temporary file and only replaces it once complete and valid, so a failed backup never leaves a partial
     * .tachibk nor loses the one it was replacing (spec 5a §3 A). Older automatic backups go once the new one is in.
     */
    suspend fun backup(file: File, options: BackupOptions): String {
        var tmp: File? = null
        try {
            val destination = if (isAutoBackup) {
                file.mkdirs()
                File(file, getFilename())
            } else {
                file
            }
            if (destination.parentFile?.isDirectory != true) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }
            tmp = File(destination.path + ".tmp")

            val nonFavoriteManga = if (options.readEntries) mangaRepository.getReadMangaNotInLibrary() else emptyList()
            val backupManga = backupMangas(getFavorites.await() + nonFavoriteManga, options)

            tmp.outputStream().sink().gzip().buffer().use {
                val writer = BackupWriter(it, parser)
                val sourceIds = mutableSetOf<Long>()
                backupManga.collect { manga ->
                    writer.writeEntry(manga)
                    sourceIds += manga.source
                }
                writer.writeMetadata(
                    Backup(
                        backupManga = emptyList(),
                        backupCategories = backupCategories(options),
                        backupSources = backupSources(sourceIds),
                        backupPreferences = backupAppPreferences(options),
                        backupExtensionStores = backupExtensionStores(options),
                        backupSourcePreferences = backupSourcePreferences(options),
                    ),
                )
                if (writer.isEmpty) {
                    throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
                }
            }

            // Make sure it's a valid backup file
            backupFileValidator.validate(tmp)
            Files.move(tmp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)

            if (isAutoBackup) {
                file.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(MAX_AUTO_BACKUPS)
                    .forEach { it.delete() }
                backupPreferences.lastAutoBackupTimestamp.set(Clock.System.now().toEpochMilliseconds())
            }

            return destination.path
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            tmp?.takeIf { it.isFile }?.delete()
            throw e
        }
    }
```

- En el `companion object`, cambiar `BuildConfig.APPLICATION_ID` por `DesktopBuildInfo.APPLICATION_ID` (dos sitios), con `// desktop: the desktop app's id`.

En los demás copiados (`models/`, `BackupStream.kt`, `creators/`, `BackupOptions.kt`) no se cambia nada. Si alguno no compila por algo de Android, se aplica el cambio mínimo con `// desktop:` y se apunta como ruling.

- [ ] **Step 5: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.backup.BackupCreatorTest" --tests "eu.kanade.tachiyomi.data.backup.BackupStreamTest"`
Expected: PASS (4 + los de `BackupStreamTest`).

Run: `bash scripts/check-no-android.sh`
Expected: OK.

- [ ] **Step 6: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): Mihon's .tachibk format and backup creation

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: restaurar, notificador y `BackupJob`

**Files:**
- Copy (sin cambios): `restore/RestoreOptions.kt`, `restore/restorers/CategoriesRestorer.kt`, `ExtensionStoreRestorer.kt` y `MangaRestorer.kt`.
- Copy and modify: `restore/BackupRestorer.kt` y `restore/restorers/PreferenceRestorer.kt`.
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/backup/BackupNotifier.kt` y `BackupJob.kt`.
- Modify: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt`.
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/backup/BackupRestoreTest.kt`.

**Interfaces:**
- Consumes: de la Task 2, `BackupDecoder`, `BackupFileValidator`, `BackupCreator.Factory` y `TestBackups.write`.
- Produces:
  - `BackupNotifier`:
    - `restoreProgress: StateFlow<BackupNotifier.Progress?>`, con `Progress(content: String, done: Int, total: Int)`;
    - `showRestoreProgress(content, progress, maxAmount, sync)`, `showRestoreComplete(time, errorCount, path, file, sync)` y `showRestoreError(error)`;
    - `showBackupComplete(file: File)` y `showBackupError(error)`.
  - `BackupJob`:
    - `running: StateFlow<BackupJob.Operation?>`, con `enum Operation { Create, Restore }`;
    - `create(file: File, options: BackupOptions): Boolean` y `restore(file: File, options: RestoreOptions): Boolean`;
    - `awaitIdle()`, `awaitRestore()`, `createAutomatic()` y `periodicJob(): PeriodicJob`.
  - `AppGraph`: `backupJob`, `backupNotifier`, `backupPreferences` (este último ya desde la Task 2) y `getFavorites`.

- [ ] **Step 1: Copiar**

```bash
A=app/src/main/java/eu/kanade/tachiyomi/data/backup/restore
D=desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/backup/restore
mkdir -p $D/restorers
cp $A/RestoreOptions.kt $A/BackupRestorer.kt $D/
cp $A/restorers/*.kt $D/restorers/
```

- [ ] **Step 2: Escribir los tests**

`BackupRestoreTest.kt`:

```kotlin
package mihon.desktop.app.backup

import eu.kanade.tachiyomi.data.backup.BackupJob
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestBackups
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory

class BackupRestoreTest {
    private val graph: TestAppGraph = startTestApp()
    private val dir = createTempDirectory("Restaurar José").toFile()
    private val notices = CopyOnWriteArrayList<Notice>()

    @BeforeEach
    fun captureNotices() {
        TrayNotifier.sink = { notices += it }
    }

    @AfterEach
    fun releaseNotices() {
        TrayNotifier.sink = null
    }

    private fun manga(url: String, title: String, memo: ByteArray? = null) = BackupManga(
        source = TestSources.updatable.id,
        url = url,
        title = title,
        chapters = listOf(
            BackupChapter(url = "$url/c1", name = "Capítulo 1", read = true, chapterNumber = 1f),
            BackupChapter(url = "$url/c2", name = "Capítulo 2", chapterNumber = 2f),
        ),
        categories = listOf(50),
        history = listOf(BackupHistory(url = "$url/c1", lastRead = 1_700_000_000_000, readDuration = 30_000)),
        // Kept in the database before any tracker exists (spec 5a §3 F)
        tracking = listOf(BackupTracking(syncId = 2, libraryId = 0, trackingUrl = "https://example.org$url", title = title)),
    ).apply { if (memo != null) this.memo = memo }

    private suspend fun restore(backup: Backup, options: RestoreOptions = RestoreOptions()) {
        val file = File(dir, "copia-${System.nanoTime()}.tachibk").also { TestBackups.write(it, backup) }
        graph.backupJob.restore(file, options) shouldBe true
        graph.backupJob.awaitIdle()
    }

    @Test
    fun `a backup restores its manga with chapters, categories and history, and twice adds nothing`(): Unit =
        runBlocking {
            val backup = Backup(
                backupManga = listOf(manga("/restaurar/1", "Restaurado José")),
                backupCategories = listOf(BackupCategory(name = "Restaurada José", order = 50)),
            )

            restore(backup)
            restore(backup)

            val restored = graph.getMangaByUrlAndSourceId.await("/restaurar/1", TestSources.updatable.id)!!
            restored.favorite shouldBe true
            val chapters = graph.getChaptersByMangaId.await(restored.id)
            chapters.size shouldBe 2
            chapters.single { it.url == "/restaurar/1/c1" }.read shouldBe true
            graph.getHistory.await(restored.id).size shouldBe 1
            graph.trackRepository.getTracksByMangaId(restored.id).single().remoteUrl shouldBe
                "https://example.org/restaurar/1"
            val category = graph.getCategories.await().single { it.name == "Restaurada José" }
            graph.getCategories.await(restored.id).map { it.id } shouldBe listOf(category.id)
            graph.backupNotifier.restoreProgress.value shouldBe null
        }

    @Test
    fun `a corrupt file fails with an error and the library stays as it was`(): Unit = runBlocking {
        val before = graph.getFavorites.await().size
        val file = File(dir, "roto.tachibk").apply { writeText("esto no es un backup") }

        graph.backupJob.restore(file, RestoreOptions()) shouldBe true
        graph.backupJob.awaitIdle()

        graph.getFavorites.await().size shouldBe before
        notices.single().title shouldBe graph.context.stringResource(MR.strings.restoring_backup_error)
    }

    @Test
    fun `a manga with bad data goes to the error log and the others are restored`(): Unit = runBlocking {
        restore(
            Backup(
                backupManga = listOf(
                    manga("/restaurar/bien", "Bueno José"),
                    manga("/restaurar/mal", "Malo José", memo = "no es json".encodeToByteArray()),
                ),
            ),
        )

        graph.getMangaByUrlAndSourceId.await("/restaurar/bien", TestSources.updatable.id) shouldNotBe null
        graph.getMangaByUrlAndSourceId.await("/restaurar/mal", TestSources.updatable.id) shouldBe null
        graph.appDirs.logs.resolve("restore_errors.txt").toFile().readText() shouldContain "Malo José"
        notices.single().isError shouldBe true
    }

    @Test
    fun `restored settings keep their types, and app state and mistyped keys are left alone`(): Unit = runBlocking {
        val storageDir = graph.storagePreferences.baseStorageDirectory.get()
        graph.preferenceStore.getString("desktop_test_text").set("texto")

        restore(
            Backup(
                backupManga = emptyList(),
                backupPreferences = listOf(
                    BackupPreference("backup_interval", IntPreferenceValue(48)),
                    BackupPreference("desktop_test_flag", BooleanPreferenceValue(true)),
                    BackupPreference(
                        Preference.appStateKey("storage_dir"),
                        StringPreferenceValue("content://com.android.externalstorage.documents/tree/x"),
                    ),
                    // On this side the key holds text: an Int of the same name mustn't replace it
                    BackupPreference("desktop_test_text", IntPreferenceValue(3)),
                ),
            ),
        )

        graph.backupPreferences.backupInterval.get() shouldBe 48
        graph.preferenceStore.getBoolean("desktop_test_flag").get() shouldBe true
        graph.storagePreferences.baseStorageDirectory.get() shouldBe storageDir
        graph.preferenceStore.getString("desktop_test_text").get() shouldBe "texto"
        graph.backupPreferences.backupInterval.set(12)
    }

    @Test
    fun `one backup or restore at a time`(): Unit = runBlocking {
        val file = File(dir, "uno.tachibk")
        TestBackups.write(file, Backup(backupManga = (1..50).map { manga("/uno/$it", "Uno $it") }))

        graph.backupJob.restore(file, RestoreOptions()) shouldBe true
        graph.backupJob.running.value shouldBe BackupJob.Operation.Restore
        graph.backupJob.restore(file, RestoreOptions()) shouldBe false
        graph.backupJob.create(File(dir, "otro.tachibk"), BackupOptions()) shouldBe false
        graph.backupJob.awaitIdle()

        graph.backupJob.running.value shouldBe null
    }
}
```

`graph.context`, `graph.appDirs`, `graph.preferenceStore` y `graph.storagePreferences` ya existen en `DesktopGraph` y `AppGraph`. `graph.getHistory` (`GetHistory.await(mangaId)`) y `graph.getMangaByUrlAndSourceId` ya están en `TestAppGraph`. `getCategories.await(mangaId)` es la sobrecarga por manga de Mihon; si no existe, usar `graph.getCategories.await().filter { … }` con la tabla `manga_category`, y apuntarlo como ruling.

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.backup.BackupRestoreTest"`
Expected: FAIL de compilación (`BackupJob` no existe; los restorers usan `Uri`, `Log` y `LibraryUpdateWorker`).

- [ ] **Step 4: `BackupNotifier`**

```kotlin
package eu.kanade.tachiyomi.data.backup

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.awt.Desktop
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Desktop counterpart of Mihon's BackupNotifier (spec 5a §2): the restore's progress is a flow the Data screen shows;
 * finished backups and restores and their errors go to the tray, or to an in-app message without it.
 */
@Inject
@SingleIn(AppScope::class)
class BackupNotifier(private val context: Context) {
    data class Progress(val content: String, val done: Int, val total: Int)

    private val _restoreProgress = MutableStateFlow<Progress?>(null)
    val restoreProgress: StateFlow<Progress?> = _restoreProgress.asStateFlow()

    fun showBackupComplete(file: File) {
        send(Notice(context.stringResource(MR.strings.backup_created), file.path, opensUpdates = false))
    }

    fun showBackupError(error: String?) {
        send(
            Notice(
                context.stringResource(MR.strings.creating_backup_error),
                error.orEmpty(),
                isError = true,
                opensUpdates = false,
            ),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun showRestoreProgress(content: String = "", progress: Int = 0, maxAmount: Int = 100, sync: Boolean = false) {
        _restoreProgress.value = Progress(content, progress, maxAmount)
    }

    fun showRestoreError(error: String?) {
        _restoreProgress.value = null
        send(
            Notice(
                context.stringResource(MR.strings.restoring_backup_error),
                error.orEmpty(),
                isError = true,
                opensUpdates = false,
            ),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun showRestoreComplete(time: Long, errorCount: Int, path: String?, file: String?, sync: Boolean) {
        _restoreProgress.value = null
        val timeString = context.stringResource(
            MR.strings.restore_duration,
            time.milliseconds.inWholeMinutes,
            time.milliseconds.inWholeSeconds - (time.milliseconds.inWholeMinutes * 60),
        )
        val message = context.pluralStringResource(
            MR.plurals.restore_completed_message,
            errorCount,
            timeString,
            errorCount,
        )
        send(
            Notice(
                context.stringResource(MR.strings.restore_completed),
                message,
                isError = errorCount > 0,
                opensUpdates = false,
            ),
        )
        if (errorCount > 0 && !path.isNullOrEmpty() && !file.isNullOrEmpty()) {
            // desktop: the tray can't carry a button, so the log opens from an in-app message, as in 3a
            AppMessages.show(message, context.stringResource(MR.strings.action_show_errors)) {
                runCatching { Desktop.getDesktop().open(File(path, file)) }
            }
        }
    }

    private fun send(notice: Notice) = TrayNotifier.send(notice)
}
```

- [ ] **Step 5: Adaptar `BackupRestorer` y `PreferenceRestorer`**

`BackupRestorer.kt`:
- Imports: quitar `android.net.Uri` y `eu.kanade.tachiyomi.util.system.createFileInCacheDir`. Añadir `mihon.desktop.core.AppDirs` y `kotlin.io.path.createDirectories`.
- Constructor: añadir `private val appDirs: AppDirs,` después de `private val context: Context,`.
- `suspend fun restore(uri: Uri, …)` pasa a `restore(file: File, …)`, con `restoreFromFile(file, options)` dentro.
- `restoreFromFile(uri: Uri, …)` pasa a `restoreFromFile(file: File, …)`, con `backupDecoder.decodeMetadata(file)` y `restoreManga(file, …)` dentro.
- `CoroutineScope.restoreManga(uri: Uri, …)` pasa a `restoreManga(file: File, …)`. Las dos llamadas `backupDecoder.decodeEntries(uri…)` pasan a `decodeEntries(file…)`.
- En `writeErrorLog`, `val file = context.createFileInCacheDir("mihon_restore_error.txt")` pasa a:

```kotlin
                // desktop: in the logs folder, beside the library update's error file (spec 3a §3 G)
                val file = appDirs.logs.createDirectories().resolve("restore_errors.txt").toFile()
```

`restorers/PreferenceRestorer.kt`:
- Imports: quitar `android.content.Context`, `android.util.Log`, `eu.kanade.tachiyomi.data.library.LibraryUpdateWorker` y `eu.kanade.tachiyomi.data.backup.create.BackupCreateWorker`. Añadir `logcat.LogPriority`, `tachiyomi.core.common.util.system.logcat` y `tachiyomi.core.common.preference.Preference`.
- Constructor: quitar `private val context: Context,`.
- En `restoreApp`, sustituir las dos llamadas `…setupTask(context)` por:

```kotlin
        // desktop: the jobs read their intervals from the preferences, so there's nothing to reschedule
```

- En `restorePreferences`, primera línea dentro de `toRestore.forEach { (key, value) ->`:

```kotlin
            // desktop: app state (the storage folder among it) belongs to this install, never to a backup (spec 5a §3 D)
            if (Preference.isAppState(key)) return@forEach
```

- `Log.e("PreferenceRestorer", "Failed to restore preference <$key>", e)` pasa a:

```kotlin
                logcat(LogPriority.ERROR, e) { "Failed to restore preference <$key>" }
```

- [ ] **Step 6: `BackupJob`**

```kotlin
package eu.kanade.tachiyomi.data.backup

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.desktop.core.jobs.PeriodicJob
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import java.io.File
import kotlin.time.Duration.Companion.hours

/**
 * Desktop counterpart of Mihon's BackupCreateWorker and BackupRestoreWorker (spec 5a §2): one backup or restore at a
 * time on the app scope, which outlives the window while the app is in the tray.
 */
@Inject
@SingleIn(AppScope::class)
class BackupJob(
    private val context: Context,
    private val backupCreatorFactory: BackupCreator.Factory,
    private val backupRestorerFactory: BackupRestorer.Factory,
    private val notifier: BackupNotifier,
    private val storageManager: StorageManager,
    private val backupPreferences: BackupPreferences,
    preferenceStore: PreferenceStore,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    enum class Operation { Create, Restore }

    private val _running = MutableStateFlow<Operation?>(null)
    val running: StateFlow<Operation?> = _running.asStateFlow()

    @Volatile
    private var job: Job? = null

    // The scheduler marks a run before it starts; Mihon's lastAutoBackupTimestamp moves only on success (plan 5a, dev. 4)
    private val lastAutoBackupAttempt =
        preferenceStore.getLong(Preference.appStateKey("desktop_last_auto_backup_attempt"), 0L)

    /** Writes a backup to [file]. False if a backup or a restore is already running. */
    fun create(file: File, options: BackupOptions): Boolean = launch(Operation.Create) {
        try {
            val path = backupCreatorFactory.create(isAutoBackup = false).backup(file, options)
            notifier.showBackupComplete(File(path))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notifier.showBackupError(e.message)
        }
    } != null

    /** Restores [file]. False if a backup or a restore is already running. */
    fun restore(file: File, options: RestoreOptions): Boolean = launch(Operation.Restore) {
        try {
            backupRestorerFactory.create(notifier = notifier, isSync = false).restore(file, options)
        } catch (e: CancellationException) {
            notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_canceled))
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showRestoreError(e.message)
        }
    } != null

    suspend fun awaitIdle() {
        job?.join()
    }

    /** Returns once no restore is running: the automatic jobs hold back meanwhile (spec 5a §3 E). */
    suspend fun awaitRestore() {
        running.first { it != Operation.Restore }
    }

    /** The automatic backup, for the scheduler: [BackupPreferences.backupInterval] hours, 0 = off. */
    fun periodicJob(): PeriodicJob = PeriodicJob(
        name = "AutoBackup",
        interval = { backupPreferences.backupInterval.get().takeIf { it > 0 }?.hours },
        lastRun = lastAutoBackupAttempt,
        run = ::createAutomatic,
    )

    /** One automatic backup into the storage's autobackup folder, once whatever backup or restore is running ends. */
    suspend fun createAutomatic() {
        while (true) {
            running.first { it == null }
            val started = launch(Operation.Create) {
                try {
                    val dir = storageManager.getAutomaticBackupsDirectory()?.filePath?.let(::File)
                        ?: error(context.stringResource(MR.strings.create_backup_file_error))
                    backupCreatorFactory.create(isAutoBackup = true).backup(dir, BackupOptions())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    notifier.showBackupError(e.message)
                }
            } ?: continue
            started.join()
            return
        }
    }

    private fun launch(operation: Operation, block: suspend () -> Unit): Job? = synchronized(this) {
        if (_running.value != null) return null
        _running.value = operation
        scope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                _running.value = null
            }
        }.also { job = it }
    }
}
```

`AppGraph`: añadir `val backupJob: BackupJob`, `val backupNotifier: BackupNotifier` y `val getFavorites: GetFavorites`. Si `TestAppGraph` ya declara `getFavorites` (Task 2), se borra de allí, porque ahora lo hereda.

- [ ] **Step 7: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.backup.*" --tests "eu.kanade.tachiyomi.data.backup.*"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): restore Mihon backups, one backup job at a time with tray notices

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: backup automático y exclusión con la restauración

**Files:**
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/backup/AutoBackupTest.kt`

**Interfaces:**
- Consumes: de la Task 3, `BackupJob.createAutomatic()`, `periodicJob()`, `awaitRestore()`, `restore()` y `running`.
- Produces: `LibraryUpdateJob.periodicJob()` espera a que termine una restauración en curso. `Main` programa `backupJob.periodicJob()`.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.backup

import eu.kanade.tachiyomi.data.backup.BackupJob
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestBackups
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory

class AutoBackupTest {
    private val graph: TestAppGraph = startTestApp()

    private fun autobackupDir() = File(graph.storageManager.getAutomaticBackupsDirectory()!!.filePath!!)

    private fun backups() = autobackupDir().listFiles().orEmpty().map { it.name }.filter { it.endsWith(".tachibk") }

    @Test
    fun `an automatic backup goes to the autobackup folder and only the last 4 stay`(): Unit = runBlocking {
        TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        val old = (1..5).map { "app.mangareader_2020-01-0${it}_00-00.tachibk" }
        old.forEach { File(autobackupDir(), it).writeText("viejo") }

        graph.backupJob.createAutomatic()

        val kept = backups()
        kept.size shouldBe 4
        kept shouldContainAll old.takeLast(3)
        kept shouldNotContain old[0]
        graph.backupPreferences.lastAutoBackupTimestamp.get() shouldNotBe 0L
    }

    @Test
    fun `a failed automatic backup says so and keeps the last backup time`(): Unit = runBlocking {
        val notices = CopyOnWriteArrayList<Notice>()
        TrayNotifier.sink = { notices += it }
        val storage = graph.storagePreferences.baseStorageDirectory.get()
        // A storage folder whose autobackup is a file: the folder can't be created
        val broken = createTempDirectory("Almacén José").toFile().apply { resolve("autobackup").writeText("x") }
        graph.storagePreferences.baseStorageDirectory.set(broken.toURI().toString())
        val before = graph.backupPreferences.lastAutoBackupTimestamp.get()
        try {
            graph.backupJob.createAutomatic()
        } finally {
            graph.storagePreferences.baseStorageDirectory.set(storage)
            TrayNotifier.sink = null
        }

        graph.backupPreferences.lastAutoBackupTimestamp.get() shouldBe before
        notices.single().isError shouldBe true
    }

    @Test
    fun `the automatic jobs wait for a restore to end, and the backup then holds what it restored`(): Unit =
        runBlocking {
            val file = createTempDirectory("Grande José").toFile().resolve("grande.tachibk")
            val mangas = (1..400).map { n ->
                BackupManga(
                    source = TestSources.updatable.id,
                    url = "/grande/$n",
                    title = "Grande $n",
                    chapters = (1..20).map { BackupChapter(url = "/grande/$n/$it", name = "Capítulo $it") },
                )
            }
            TestBackups.write(file, Backup(backupManga = mangas))
            val lastUpdate = graph.libraryPreferences.lastUpdatedTimestamp.get()

            graph.backupJob.restore(file, RestoreOptions()) shouldBe true
            graph.backupJob.running.value shouldBe BackupJob.Operation.Restore
            val backup = async { graph.backupJob.periodicJob().run() }
            val update = async { graph.libraryUpdateJob.periodicJob().run() }
            delay(300)
            assumeTrue(graph.backupJob.running.value == BackupJob.Operation.Restore) { "the restore ended too soon" }
            graph.libraryPreferences.lastUpdatedTimestamp.get() shouldBe lastUpdate
            backup.await()
            update.await()

            val newest = autobackupDir().listFiles().orEmpty().filter { it.name.endsWith(".tachibk") }.maxBy { it.name }
            val urls = graph.backupDecoder.decodeEntries(newest).toList().map { it.url }
            urls shouldContainAll mangas.map { it.url }
        }
}
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.backup.AutoBackupTest"`
Expected:
- Los dos primeros pasan ya: la rotación y el error se hicieron en las Tasks 2 y 3. Si no pasan, se arregla antes de seguir.
- El tercero falla en `lastUpdatedTimestamp shouldBe lastUpdate`, porque la actualización periódica de la biblioteca no espera a la restauración.

- [ ] **Step 3: La actualización periódica espera**

En `LibraryUpdateJob.kt`:
- Constructor: añadir `private val backupJob: BackupJob,` después de `filterChaptersForDownload`. Import `eu.kanade.tachiyomi.data.backup.BackupJob`.
- En `periodicJob()`, `run = { if (start()) awaitIdle() },` pasa a:

```kotlin
        // desktop: never during a restore, which is rewriting the library (spec 5a §3 E)
        run = {
            backupJob.awaitRestore()
            if (start()) awaitIdle()
        },
```

- [ ] **Step 4: Programar el backup y limpiar la caché al arrancar**

En `Main.kt`:
- El comentario `// Periodic jobs while the app is open or in the tray (parent spec §5.1); only the library update for now` pasa a `// Periodic jobs while the app is open or in the tray (parent spec §5.1)`.
- La lista del `JobScheduler` pasa a `listOf(graph.libraryUpdateJob.periodicJob(), graph.backupJob.periodicJob()),`.
- Justo después de `graph.startStorage()`:

```kotlin
    // Mihon clears the chapter cache on launch when asked to (MainActivity)
    if (graph.libraryPreferences.autoClearChapterCache.get()) {
        CoroutineScope(Dispatchers.IO).launch { graph.chapterCache.clear() }
    }
```

  (imports `kotlinx.coroutines.launch` y `kotlinx.coroutines.CoroutineScope`, si faltan).

- [ ] **Step 5: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.backup.*" --tests "mihon.desktop.app.library.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): automatic backups, held back with the library update while a restore runs

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: almacenamiento, caché y CSV

**Files:**
- Modify: `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/util/storage/DiskUtil.kt` y su test `core/common/src/test/kotlin/…/DiskUtilTest.kt` (el de 4b).
- Copy and modify: `eu/kanade/presentation/more/settings/screen/data/StorageInfo.kt` y `eu/kanade/tachiyomi/data/export/LibraryExporter.kt`.
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/backup/LibraryExporterTest.kt`.

**Interfaces:**
- Produces:
  - `DiskUtil.getTotalStorageSpace(file: File): Long`, que devuelve el tamaño total del volumen.
  - `StorageInfo(modifier: Modifier)`, que muestra el disco de la carpeta de almacenamiento.
  - `formatFileSize(bytes: Long): String`, internal, en `StorageInfo.kt`.
  - `LibraryExporter.exportToCsv(file: File, favorites: List<Manga>, options: ExportOptions)`, suspend.

- [ ] **Step 1: Copiar**

```bash
mkdir -p desktop-app/src/main/kotlin/eu/kanade/presentation/more/settings/screen/data desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/export
cp app/src/main/java/eu/kanade/presentation/more/settings/screen/data/StorageInfo.kt desktop-app/src/main/kotlin/eu/kanade/presentation/more/settings/screen/data/
cp app/src/main/java/eu/kanade/tachiyomi/data/export/LibraryExporter.kt desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/export/
```

- [ ] **Step 2: Escribir los tests**

En `DiskUtilTest` (core/common):

```kotlin
    @Test
    fun `the total space is the volume's size`() {
        val dir = createTempDirectory("Disco José").toFile()
        DiskUtil.getTotalStorageSpace(dir) shouldBe dir.totalSpace
        (DiskUtil.getTotalStorageSpace(dir) > 0) shouldBe true
    }
```

`LibraryExporterTest.kt`:

```kotlin
package mihon.desktop.app.backup

import eu.kanade.presentation.more.settings.screen.data.formatFileSize
import eu.kanade.tachiyomi.data.export.LibraryExporter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import kotlin.io.path.createTempDirectory

class LibraryExporterTest {
    @Test
    fun `the CSV has a BOM for Excel and only the chosen columns`(): Unit = runBlocking {
        val file = createTempDirectory("Exportar José").toFile().resolve("biblioteca.csv")
        val favorites = listOf(
            Manga.create().copy(title = "Ñandú, el \"ave\"", author = "José Pérez", artist = "Ana"),
            Manga.create().copy(title = "Sin autor", author = null, artist = "Luis"),
        )

        LibraryExporter.exportToCsv(
            file,
            favorites,
            LibraryExporter.ExportOptions(includeTitle = true, includeAuthor = true, includeArtist = false),
        )

        val bytes = file.readBytes()
        bytes.take(3).map { it.toInt() and 0xff } shouldBe listOf(0xef, 0xbb, 0xbf)
        bytes.drop(3).toByteArray().decodeToString() shouldBe "\"Ñandú, el \"\"ave\"\"\",José Pérez\r\nSin autor,"
    }

    @Test
    fun `sizes read like Android's, in 1000-based units`() {
        formatFileSize(512) shouldBe "512 B"
        formatFileSize(1_500_000_000) shouldEndWith "GB"
    }
}
```

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :core:common:test --tests "*DiskUtilTest*" :desktop-app:test --tests "mihon.desktop.app.backup.LibraryExporterTest"`
Expected: FAIL. El de `DiskUtil` da -1, porque `StatFs` es el stub de Android. `LibraryExporter` no compila, porque usa `Uri` y `contentResolver`.

- [ ] **Step 4: Implementación**

`DiskUtil.getTotalStorageSpace` en la copia de escritorio:

```kotlin
    /**
     * Gets the total space for the disk that a file path points to, in bytes.
     * desktop: the size Windows reports for the volume, instead of Android's StatFs.
     */
    fun getTotalStorageSpace(file: File): Long {
        return try {
            file.totalSpace
        } catch (_: Exception) {
            -1L
        }
    }
```

`LibraryExporter.kt`:
- Imports: quitar `android.content.Context` y `android.net.Uri`, y añadir `java.io.File`.
- `exportToCsv`:

```kotlin
    // desktop: a local file, with a UTF-8 BOM so Excel on Windows doesn't read it as ANSI (spec 5a §3 G)
    suspend fun exportToCsv(
        file: File,
        favorites: List<Manga>,
        options: ExportOptions,
    ) {
        withContext(Dispatchers.IO) {
            file.outputStream().use { outputStream ->
                outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                outputStream.write(generateCsvData(favorites, options).toByteArray())
            }
        }
    }
```

`StorageInfo.kt`:
- Imports: quitar `android.text.format.Formatter`. Añadir `mihon.app.di.appGraph`, `java.net.URI` y `java.util.Locale`.
- El `StorageInfo(modifier)` público pasa a:

```kotlin
@Composable
fun StorageInfo(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // desktop: the disk of the storage folder (spec 5a §3 G), instead of Android's external storages
    val storages by produceState(emptyList<File>()) {
        value = withContext(Dispatchers.IO) {
            val dir = context.appGraph.storagePreferences.baseStorageDirectory.get()
            listOfNotNull(runCatching { File(URI(dir)) }.getOrNull())
        }
    }
```

  (el resto del cuerpo, con el `Column` y el `forEach`, se queda igual).
- En el `StorageInfo(file)` privado:
  - `Formatter.formatFileSize(context, available)` pasa a `formatFileSize(available)`, y lo mismo con `total`.
  - Se quita `val context = LocalContext.current` si deja de usarse.
- Al final del fichero:

```kotlin
// desktop: Android's Formatter.formatFileSize, with its 1000-based units, without a Context
internal fun formatFileSize(bytes: Long): String {
    val units = listOf("B", "kB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.getDefault(), "%.2f %s", value, units[unit])
}
```

- [ ] **Step 5: Comprobar que pasan**

Run: `./gradlew :core:common:test :desktop-app:test --tests "mihon.desktop.app.backup.LibraryExporterTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/common desktop-app
git commit -m "feat(desktop-app): storage usage, library export to CSV for Excel, real disk totals

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: pantallas de Datos, crear y restaurar

**Files:**
- Replace: `desktop-app/src/main/kotlin/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt`, con la copia de Mihon y sus cambios.
- Copy and modify: `eu/kanade/presentation/more/settings/screen/data/CreateBackupScreen.kt` y `RestoreBackupScreen.kt`.
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/FileDialogs.kt`.
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/backup/DataScreensTest.kt`.

**Interfaces:**
- Consumes:
  - Task 3: `BackupJob.create/restore/running`, `BackupNotifier.restoreProgress` y `BackupFileValidator.validate(File)`.
  - Task 5: `StorageInfo` y `LibraryExporter.exportToCsv(File, …)`.
- Produces:
  - `FileDialogs.openBackup(title: String): File?`.
  - `RestoreBackupScreen(path: String)`.

- [ ] **Step 1: Copiar**

```bash
S=app/src/main/java/eu/kanade/presentation/more/settings/screen
D=desktop-app/src/main/kotlin/eu/kanade/presentation/more/settings/screen
cp $S/SettingsDataScreen.kt $D/SettingsDataScreen.kt
cp $S/data/CreateBackupScreen.kt $S/data/RestoreBackupScreen.kt $D/data/
```

- [ ] **Step 2: Escribir los tests**

```kotlin
package mihon.desktop.app.backup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.source.model.Page
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestBackups
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalTestApi::class)
class DataScreensTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `Data and storage has Mihon's backup, storage and export sections, without the MIUI warning`() =
        runComposeUiTest {
            val graph = startTestApp()
            setContent { AppProviders(graph) { Navigator(SettingsDataScreen) { DefaultNavigatorScreenTransition(it) } } }

            listOf(
                MR.strings.pref_create_backup,
                MR.strings.pref_restore_backup,
                MR.strings.pref_backup_interval,
                MR.strings.pref_clear_chapter_cache,
                MR.strings.library_list,
            ).forEach { waitForText(str(it)) }
            onAllNodes(hasText(str(MR.strings.restore_miui_warning))).fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `a running restore shows its progress on the Data screen`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppProviders(graph) { Navigator(SettingsDataScreen) { DefaultNavigatorScreenTransition(it) } } }

        graph.backupNotifier.showRestoreProgress("Restaurado José", 3, 10)

        waitForText(str(MR.strings.restoring_backup))
        waitForText("Restaurado José")
        waitForText("3/10")
    }

    @Test
    fun `clearing the chapter cache empties it`() = runComposeUiTest {
        val graph = startTestApp()
        val (_, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        graph.chapterCache.putPageListToCache(chapters[0], listOf(Page(0, imageUrl = "https://example.org/0.png")))
        setContent { AppProviders(graph) { Navigator(SettingsDataScreen) { DefaultNavigatorScreenTransition(it) } } }

        waitForText(str(MR.strings.pref_clear_chapter_cache))
        onNodeWithText(str(MR.strings.pref_clear_chapter_cache)).performScrollTo().performClick()

        waitUntil(timeoutMillis = 10_000) { graph.chapterCache.getPageListFromCache(chapters[0]).isEmpty() }
    }

    @Test
    fun `the restore screen lists a backup's missing sources, and an invalid file's error`() = runComposeUiTest {
        val graph = startTestApp()
        val dir = createTempDirectory("Pantalla José").toFile()
        val valid = dir.resolve("valido.tachibk")
        TestBackups.write(
            valid,
            Backup(
                backupManga = listOf(BackupManga(source = 424242L, url = "/perdido", title = "Perdido")),
                backupSources = listOf(BackupSource(name = "Fuente Perdida José", sourceId = 424242L)),
            ),
        )
        val invalid = dir.resolve("invalido.tachibk").apply { writeText("nada") }
        var path by androidx.compose.runtime.mutableStateOf(valid.path)
        setContent {
            AppProviders(graph) {
                androidx.compose.runtime.key(path) { Navigator(RestoreBackupScreen(path)) }
            }
        }

        waitForText(str(MR.strings.backup_restore_missing_sources))
        waitForText("Fuente Perdida José")
        path = invalid.path
        waitForText(str(MR.strings.invalid_backup_file))
    }
}
```

`getPageListFromCache` lanza una excepción si la entrada no existe. En ese caso, la condición del `waitUntil` pasa a ser `runCatching { graph.chapterCache.getPageListFromCache(chapters[0]) }.isFailure`, y se apunta como ruling si hace falta.

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.backup.DataScreensTest"`
Expected: FAIL de compilación. Los ficheros copiados usan `ActivityResultContracts`, `Uri`, `Intent`, `DeviceUtil`, `workManager` y los Workers.

- [ ] **Step 4: `FileDialogs.openBackup`**

En `FileDialogs`, después de `openImage`:

```kotlin
    /** Windows' dialog ignores FilenameFilter, so the "*.tachibk" name is what filters there; the filter covers others. */
    fun openBackup(title: String): File? = show(title, FileDialog.LOAD) {
        file = "*.tachibk"
        setFilenameFilter { _, name -> name.endsWith(".tachibk", ignoreCase = true) }
    }
```

- [ ] **Step 5: Adaptar `SettingsDataScreen`**

Sobre la copia de Mihon:
- **Imports.**
  - Quitar `android.content.ActivityNotFoundException`, `android.content.Intent`, `android.net.Uri`, `androidx.activity.compose.ManagedActivityResultLauncher`, `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.core.net.toUri`, `com.hippo.unifile.UniFile`, `eu.kanade.tachiyomi.data.backup.create.BackupCreateWorker`, `eu.kanade.tachiyomi.data.backup.restore.BackupRestoreWorker`, `eu.kanade.tachiyomi.util.system.DeviceUtil` y `eu.kanade.tachiyomi.util.system.workManager`.
  - Añadir `eu.kanade.tachiyomi.data.backup.BackupJob`, `mihon.desktop.app.platform.FileDialogs`, `java.io.File`, `java.net.URI`, `androidx.compose.material3.LinearProgressIndicator` y `androidx.compose.ui.unit.dp`.
- **Ubicación de almacenamiento.**
  - Se borran `storageLocationPicker` y `storageLocationText`, que en Mihon solo usan el SAF.
  - `getStorageLocationPref` pasa a ser la versión de 4a: el `TextPreference` con la ruta, `FileDialogs.chooseDirectory` y sus dos comentarios `// desktop:`. Se copia literal del fichero actual antes de sustituirlo.
- **`getBackupAndRestoreGroup`.**
  1. Se borra `val chooseBackup = rememberLauncherForActivityResult(…) { … }` entero.
  2. Se añaden `val backupJob = remember { context.appGraph.backupJob }` y `val progress by remember { context.appGraph.backupNotifier.restoreProgress }.collectAsState()` (este con `androidx.compose.runtime.collectAsState`, alias si choca con el de `tachiyomi.presentation.core.util`).
  3. El `onCheckedChange` del botón Restaurar pasa a:

```kotlin
                                    onCheckedChange = {
                                        // desktop: Windows' open dialog instead of SAF, and no MIUI warning
                                        if (backupJob.running.value == BackupJob.Operation.Restore) {
                                            context.toast(MR.strings.restore_in_progress)
                                        } else {
                                            FileDialogs.openBackup(restoreTitle)?.let {
                                                navigator.push(RestoreBackupScreen(it.path))
                                            }
                                        }
                                    },
```

     con `val restoreTitle = stringResource(MR.strings.pref_restore_backup)` junto a los otros `remember`. `scope` se borra si deja de usarse.
  4. En el `ListPreference` de `backupInterval`, se quita el `onValueChanged`, con el comentario `// desktop: BackupJob reads the interval, so there's nothing to reschedule`.
  5. Después del `CustomPreference` de los botones, se añade el progreso:

```kotlin
                // desktop: the restore's progress, which Mihon shows in a notification (spec 5a §2)
                Preference.PreferenceItem.CustomPreference(title = stringResource(MR.strings.restoring_backup)) {
                    val current = progress ?: return@CustomPreference
                    Column(Modifier.padding(horizontal = PrefsHorizontalPadding)) {
                        Text(stringResource(MR.strings.restoring_backup))
                        Text("${current.content} (${current.done}/${current.total})")
                        LinearProgressIndicator(
                            progress = { if (current.total > 0) current.done / current.total.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                    }
                },
```

- **`getExportGroup`.**
  - Se borra `saveFileLauncher`.
  - En `onConfirm`, `saveFileLauncher.launch("mihon_library.csv")` pasa a:

```kotlin
                    // desktop: Windows' save dialog instead of SAF
                    FileDialogs.saveFile(exportTitle, "mihon_library.csv")?.let { file ->
                        scope.launch {
                            LibraryExporter.exportToCsv(file, favorites, options)
                            context.toast(MR.strings.library_exported)
                        }
                    }
```

    con `val exportTitle = stringResource(MR.strings.library_list)` junto a los otros `remember`.
- **El resto.** `getDataGroup`, `ColumnSelectionDialog`, `HELP_URL` y `AppBarAction` se quedan como en Mihon.

- [ ] **Step 6: Adaptar `CreateBackupScreen` y `RestoreBackupScreen`**

`CreateBackupScreen.kt`:
- **Imports.**
  - Quitar `android.content.ActivityNotFoundException`, `android.content.Intent`, `android.net.Uri`, `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `BackupCreateWorker`, `DeviceUtil` y `rememberCoroutineScope`.
  - Añadir `eu.kanade.tachiyomi.data.backup.BackupJob`, `mihon.desktop.app.platform.FileDialogs` y `java.io.File`.
- **`Content`.**
  - Se borran `chooseBackupDir` y `scope`. Se añade `val dialogTitle = stringResource(MR.strings.pref_create_backup)`.
  - `onClickAction` pasa a:

```kotlin
                onClickAction = {
                    // desktop: Windows' save dialog instead of SAF
                    val file = FileDialogs.saveFile(dialogTitle, BackupCreator.getFilename()) ?: return@LazyColumnWithAction
                    if (viewModel.createBackup(file)) {
                        navigator.pop()
                    } else {
                        context.toast(MR.strings.backup_in_progress)
                    }
                },
```

  - Se borra el bloque `if (DeviceUtil.isMiui …) { item { WarningBanner(…) } }`, con `// desktop: no MIUI warning`.
- **`CreateBackupViewModel`.**
  - El constructor pasa a `class CreateBackupViewModel(private val backupJob: BackupJob) : ViewModel()`.
  - `createBackup` pasa a:

```kotlin
    fun createBackup(file: File): Boolean = backupJob.create(file, state.value.options)
```

  - Se borra `import android.content.Context` si ya no se usa.

`RestoreBackupScreen.kt`:
- **Imports.**
  - Quitar `android.net.Uri`, `androidx.core.net.toUri`, `BackupRestoreWorker`, `DeviceUtil` y `workManager`.
  - Añadir `eu.kanade.tachiyomi.data.backup.BackupJob`, `java.io.File` e `eu.kanade.tachiyomi.util.system.toast`.
- **`class RestoreBackupScreen(private val uri: String)`.**
  - Pasa a `RestoreBackupScreen(private val path: String)`, y `create(uri = uri)` pasa a `create(path = path)`.
  - Se borra el bloque MIUI.
  - `onClickAction` pasa a:

```kotlin
                onClickAction = {
                    if (viewModel.startRestore()) navigator.pop() else context.toast(MR.strings.restore_in_progress)
                },
```

    con `val context = LocalContext.current`.
- **`RestoreBackupViewModel`.**
  - Constructor: `@Assisted private val path: String`, `backupFileValidator` y `private val backupJob: BackupJob` (sin `context`). `Factory.create(path: String)`.
  - `init`: `validate(File(path))`.
  - `startRestore()` pasa a `fun startRestore(): Boolean = backupJob.restore(File(path), state.value.options)`.
  - `validate(uri: Uri)` pasa a `validate(file: File)`, con `backupFileValidator.validate(file)` dentro.
  - Los `InvalidRestore(uri, …)` y `MissingRestoreComponents(uri, …)` pasan a recibir `file.path`, y sus clases cambian `val uri: Uri` por `val uri: String` (abajo, en el mismo fichero). `appendLine(error.uri.toString())` se queda igual.

- [ ] **Step 7: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.backup.*" --tests "mihon.desktop.app.settings.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

Run: `bash scripts/check-no-android.sh`
Expected: OK.

- [ ] **Step 8: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): Mihon's Data and storage screen with backup creation and restore

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: compatibilidad con un backup real de Mihon Android

**Files:**
- Add (lo pone el usuario): `desktop-app/src/test/resources/backup/mihon-android.tachibk`.
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/backup/MihonCompatibilityTest.kt`.

**Interfaces:**
- Consumes: de la Task 3, `BackupJob.restore` y `BackupDecoder`.

- [ ] **Step 1: El fixture**

Si `desktop-app/src/test/resources/backup/mihon-android.tachibk` no existe, **parar y pedírselo al usuario**: un backup exportado desde Mihon Android (Ajustes › Datos y almacenamiento › Crear backup), mejor con pocos mangas. Ninguna otra tarea depende de esta, pero la compatibilidad real solo se puede probar con ese fichero.

- [ ] **Step 2: Escribir el test**

```kotlin
package mihon.desktop.app.backup

import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import java.io.File

/** The real thing: a backup Mihon Android wrote (spec 5a §5). */
class MihonCompatibilityTest {
    private val graph: TestAppGraph = startTestApp()
    private val fixture = File("src/test/resources/backup/mihon-android.tachibk")

    @Test
    fun `a backup from Mihon Android restores its library, categories, read chapters and history`(): Unit =
        runBlocking {
            check(fixture.isFile) { "Export a backup from Mihon Android to $fixture (plan 5a, task 7)" }
            graph.backupFileValidator.validate(fixture)
            val entries = graph.backupDecoder.decodeEntries(fixture).toList()
            val metadata = graph.backupDecoder.decodeMetadata(fixture).backup

            graph.backupJob.restore(fixture, RestoreOptions()) shouldBe true
            graph.backupJob.awaitIdle()

            graph.getCategories.await().map { it.name } shouldContainAll metadata.backupCategories.map { it.name }
            entries.forEach { entry ->
                val manga = graph.getMangaByUrlAndSourceId.await(entry.url, entry.source)
                manga shouldNotBe null
                manga!!.favorite shouldBe entry.favorite
                val readUrls = graph.getChaptersByMangaId.await(manga.id).filter { it.read }.map { it.url }
                readUrls shouldContainAll entry.chapters.filter { it.read }.map { it.url }
                graph.getHistory.await(manga.id).size shouldBe entry.history.distinctBy { it.url }.size
            }
        }
}
```

`TestAppGraph` gana `val backupFileValidator: BackupFileValidator`.

- [ ] **Step 3: Ejecutarlo**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.backup.MihonCompatibilityTest"`
Expected: PASS. Este test valida un fichero que ya existe, así que no tiene fase roja. Si falla, es un fallo real de compatibilidad: se depura con superpowers:systematic-debugging hasta encontrar la causa, nunca se relaja el test.

- [ ] **Step 4: Commit**

```bash
git add desktop-app
git commit -m "test(desktop-app): a real Mihon Android backup restores

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: verificación completa y prueba manual

**Files:**
- Modify: ninguno, salvo los arreglos que salgan.

- [ ] **Step 1: Suite y checks**

Run: `./gradlew test spotlessCheck`
Expected: BUILD SUCCESSFUL, con unos 440 tests (los 418 de antes más los nuevos).

Run: `bash scripts/check-no-android.sh` y `bash scripts/check-licenses.sh`
Expected: los dos OK.

- [ ] **Step 2: Sin referencias pendientes**

Run: `grep -rn "subproject 5\b\|come with subproject 5\|backups, cache and export" desktop-app/src desktop-core/src --include=*.kt`
Expected: solo las de trackers (5b). Ninguna de backup, caché ni exportación.

- [ ] **Step 3: Arranque real**

Run: `./gradlew :desktop-app:run` con `MANGAREADER_HOME` en una carpeta temporal, durante 2 minutos.
Expected:
- El log no tiene errores de Metro ni de `AutoBackup`.
- Al primer chequeo del programador aparece un `.tachibk` en `<almacenamiento>\autobackup`.

- [ ] **Step 4: Commit de los arreglos (si hay)**

```bash
git add -A desktop-app desktop-core core android-compat
git commit -m "chore: tidy the last 5a notes

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 5: Prueba manual (spec §5), para el usuario**

1. Restaurar un backup completo del móvil y comprobar biblioteca, categorías, lo leído, historial y ajustes.
2. Crear un backup aquí, copiarlo al móvil y restaurarlo en Mihon Android.
3. Con el intervalo a 6 h, dejar la app en la bandeja y ver aparecer el backup en `autobackup`.
4. Exportar la biblioteca a CSV y abrirla en Excel: los acentos se ven bien.
5. Borrar la caché de capítulos y ver bajar el tamaño mostrado.
6. Cerrar la app a mitad de una restauración grande, volver a abrirla y comprobar que la biblioteca funciona. Restaurar de nuevo termina bien.
