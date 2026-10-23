# Subproyecto 4b: descargas. Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La app descarga capítulos con el motor de Mihon (cola con concurrencia por fuente, carpeta o CBZ con `ComicInfo.xml`), los lee sin red, y tiene la cola en el riel, Ajustes › Descargas y todos los ganchos de Mihon.

**Architecture:**
- Se copian con su paquete los archivos de `eu/kanade/tachiyomi/data/download/` de Mihon. Lo que es de Android se cambia en la copia, marcado con `// desktop:`:
  - `DownloadWorker` pasa a ser un `DownloadJob` de corrutinas;
  - `DownloadNotifier` es una versión de escritorio sobre `TrayNotifier`;
  - `ZipWriter` usa `java.util.zip`;
  - `AtomicFile` se cambia por un movimiento atómico de NIO.
- La cola de Mihon es de Views (`RecyclerView`). Aquí es una pantalla Compose nueva en `mihon.desktop.app.download`, como entrada del riel.
- El lector y la actualización de biblioteca del escritorio son reescrituras de 2a y 3a. Se les añaden las piezas de descargas siguiendo a Mihon.

**Tech Stack:**
- Kotlin 2.4.21 y JDK 21.
- Compose Multiplatform 1.12.2, Voyager, Metro 1.4.5 y `sh.calvin.reorderable`.
- OkHttp con `MockWebServer` en los tests, y kotlinx-serialization (ProtoBuf y XML).
- JUnit 5, kotest y `compose.uiTest`.

**Spec:** `docs/superpowers/specs/2026-10-22-subproyecto-4b-descargas-design.md`. Spec padre: `docs/superpowers/specs/2026-10-18-windows-port-design.md` (§5.1, §5.2).

## Global Constraints

- **Portado de Mihon:**
  - los archivos se copian de `app/src/main/java/<ruta>` a `desktop-app/src/main/kotlin/<ruta>` (o, en los módulos, a `src/desktop/kotlin` con exclusión de `src/main`, como en 4a), con el mismo paquete;
  - cada cambio lleva `// desktop: <motivo>`.
- **Nada de `android.*` ni `androidx.*` en desktop-app**, salvo lo que permite `scripts/check-no-android.sh`:
  - permitidos: `android.content.Context`, `android.widget.Toast`, `androidx.compose|lifecycle|paging|preference|navigationevent` y `androidx.activity.compose.BackHandler`;
  - `androidx.core.content.edit` no está permitido: se usa `edit().…apply()`;
  - en lugar de `android.net.Uri` se usa `java.net.URI`;
  - en lugar de `android.util.AtomicFile`, `Files.move(…, ATOMIC_MOVE)`.
- **Textos nuevos:**
  - en `base/strings.xml`, tras `desktop_archive_reader_unavailable`;
  - en `es/strings.xml`, tras su traducción;
  - prefijo `desktop_`. `SpanishStringsTest` exige la traducción;
  - se reutilizan las cadenas de Mihon siempre que existan.
- **Git:**
  - nunca `git push` al remoto `upstream`;
  - trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`;
  - rama `subproyecto-4b` desde `master`, con este plan commiteado.
- **Tests:** los que son `= runBlocking { }` declaran `: Unit`. Las líneas de más de 120 caracteres se parten antes del commit (`spotlessCheck`). Los nombres de test no llevan `;`.
- **Archivos con `\\`:** se escriben con la herramienta Write.
- **Fuera de alcance** (spec §1):
  - notificaciones con botones;
  - trackers (subproyecto 5);
  - reintento automático;
  - "Solo con Wi-Fi" y "Dividir imágenes altas", que se ocultan.

## Desviaciones del spec (aprobar antes de ejecutar)

1. **"Solo descargados".**
   - El spec §3 D dice que filtra Biblioteca, Novedades, Historial y la lista de capítulos.
   - En Mihon (`downloadedOnly`) solo filtra la Biblioteca (`globalFilterDownloaded`) y la lista de capítulos (`Manga.downloadedFilter`), y el diálogo de filtros lo muestra fijo.
   - Se sigue a Mihon. Además, el banner "Solo descargados" de Mihon (`AppStateBanners`, ya portado y sin usar) se muestra arriba de la ventana mientras está activo, con el aviso "Comprobando descargas" mientras la caché se renueva.
2. **Avisos de error de descarga.**
   - En Android cada aviso reemplaza al anterior (mismo id de notificación); en la bandeja de Windows se apilan.
   - El notificador de escritorio manda como mucho un aviso de error cada 5 s.
3. **Espacio libre en los tests.** `DiskUtil` de escritorio lee el espacio con una función (`DiskUtil.usableSpace`, por defecto `File::getUsableSpace`) que el test de "espacio insuficiente" sustituye. Sin ella no se puede simular un disco lleno.
4. **Tests anteriores que asumían que no había descargas.** Se actualizan:
   - `BrowseAndMangaTest` ya no espera cero botones de descarga;
   - `MigrationScreensTest` abre el menú desbordado de la barra de selección para llegar a "Migrar";
   - los tests de `LibraryTabTest` y `UpdatesTabTest` que comprobaban que el filtro "Descargados" se ignoraba pasan a comprobar que filtra.

## Review Focus

1. **Cerrar la app con un capítulo a medias.** Al volver, la cola está en pausa con ese capítulo, y al reanudar no se vuelven a bajar las páginas de `_tmp`. Test en la Task 3.
2. **Un manga o capítulo con un nombre reservado de Windows o caracteres prohibidos** (`CON`, `Tomo: 1?`). Se descarga y el lector lo encuentra. Test en la Task 2.
3. **Borrar un capítulo descargado mientras se lee.** "Borrar tras leer" no borra el capítulo abierto hasta salir del lector. Test en la Task 4.
4. **Varias fuentes en la cola.** Cada una baja en paralelo sin pasar de su límite, y un error en una no para a las otras. Test en la Task 3.
5. **"Solo descargados" activo con la biblioteca entera sin descargas.** La biblioteca queda vacía con el banner visible, no rota. Test en la Task 6.

## Mapa de archivos

| Ruta | Responsabilidad |
|---|---|
| `core/archive/src/desktop/kotlin/mihon/core/archive/ZipWriter.kt` | CBZ con `java.util.zip` (STORED) |
| `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/util/storage/DiskUtil.kt` | Nombres reservados y espacio libre |
| `desktop-app/src/main/kotlin/eu/kanade/domain/manga/model/Manga.kt` | `getComicInfo` y "Solo descargados" en el filtro de capítulos |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/download/{DownloadProvider,DownloadCache,DownloadStore,DownloadPendingDeleter}.kt` | Rutas, caché de descargas, cola guardada, borrados pendientes (Mihon) |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/download/{Downloader,DownloadManager}.kt` | Motor y fachada (Mihon) |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/download/{DownloadJob,DownloadNotifier}.kt` | Ejecución en el scope de la app y avisos (escritorio) |
| `desktop-app/src/main/kotlin/eu/kanade/domain/download/interactor/DeleteDownload.kt` | Mihon |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/reader/loader/DownloadPageLoader.kt` | Lector de descargados (Mihon) |
| `desktop-app/src/main/kotlin/mihon/desktop/app/download/{DownloadsTab,DownloadQueueViewModel}.kt` | Riel › Descargas |
| `desktop-app/src/main/kotlin/eu/kanade/presentation/more/settings/screen/SettingsDownloadScreen.kt` | Ajustes › Descargas (Mihon sin Wi-Fi ni imágenes altas) |
| `desktop-app/src/main/kotlin/eu/kanade/presentation/updates/UpdatesDeleteConfirmationDialog.kt` | Mihon |
| `desktop-app/src/test/kotlin/mihon/desktop/app/download/*` | Tests |

---

### Task 1: CBZ, nombres de Windows, espacio libre y `ComicInfo`

**Files:**
- Create: `core/archive/src/desktop/kotlin/mihon/core/archive/ZipWriter.kt`
- Modify: `core/archive/build.gradle.kts` (quitar `ZipWriter.kt` del comentario de exclusión; sigue excluido de `src/main`)
- Modify: `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/util/storage/DiskUtil.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/domain/manga/model/Manga.kt`
- Test: `core/archive/src/test/kotlin/mihon/core/archive/ZipWriterTest.kt`, `core/common/src/test/kotlin/eu/kanade/tachiyomi/util/storage/DiskUtilTest.kt`

**Interfaces:**
- Produces:
  - `class ZipWriter(context: Context, file: UniFile) : Closeable`, con `fun write(file: UniFile)` (API de Mihon);
  - `DiskUtil.buildValidFilename(...)` añade `_` a los nombres reservados;
  - `@Volatile var DiskUtil.usableSpace: (File) -> Long`;
  - `DiskUtil.getAvailableStorageSpace(UniFile): Long` (`-1` si no se sabe);
  - `fun getComicInfo(manga, chapter, urls, categories, sourceName): ComicInfo` (Mihon).

- [ ] **Step 0: Rama**

```bash
git checkout -b subproyecto-4b master
```

- [ ] **Step 1: Escribir los tests**

`core/archive/src/test/kotlin/mihon/core/archive/ZipWriterTest.kt`:

```kotlin
package mihon.core.archive

import com.hippo.unifile.UniFile
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ZipWriterTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `a CBZ written from a chapter folder is read back by the archive reader`() {
        val chapter = dir.resolve("Capítulo José_tmp").toFile().apply { mkdirs() }
        chapter.resolve("001.png").writeText("uno")
        chapter.resolve("002.png").writeText("dos")
        chapter.resolve("ComicInfo.xml").writeText("<ComicInfo/>")
        val cbz = dir.resolve("Capítulo José.cbz").toFile().apply { createNewFile() }

        ZipWriter(null, UniFile.fromFile(cbz)!!).use { writer ->
            chapter.listFiles()!!.forEach { writer.write(UniFile.fromFile(it)!!) }
        }

        ArchiveReader(cbz).use { reader ->
            reader.useEntries { entries -> entries.map { it.name }.toList() } shouldContainExactlyInAnyOrder
                listOf("001.png", "002.png", "ComicInfo.xml")
            reader.getInputStream("002.png")!!.use { it.readBytes().decodeToString() } shouldBe "dos"
        }
    }
}
```

`core/common/src/test/kotlin/eu/kanade/tachiyomi/util/storage/DiskUtilTest.kt`:

```kotlin
package eu.kanade.tachiyomi.util.storage

import com.hippo.unifile.UniFile
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DiskUtilTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `Windows reserved names get an underscore, with or without an extension and in any case`() {
        DiskUtil.buildValidFilename("CON") shouldBe "CON_"
        DiskUtil.buildValidFilename("com1") shouldBe "com1_"
        DiskUtil.buildValidFilename("lpt9.txt") shouldBe "lpt9_.txt"
        DiskUtil.buildValidFilename("CONSOLA") shouldBe "CONSOLA"
    }

    @Test
    fun `forbidden characters and a trailing dot or space are replaced or dropped, as in Mihon`() {
        DiskUtil.buildValidFilename("Tomo: 1?") shouldBe "Tomo_ 1_"
        DiskUtil.buildValidFilename("José. ") shouldBe "José"
    }

    @Test
    fun `the free space is what Windows reports for the folder's volume`() {
        val folder = UniFile.fromFile(dir.toFile())!!
        DiskUtil.getAvailableStorageSpace(folder) shouldBe dir.toFile().usableSpace

        val previous = DiskUtil.usableSpace
        try {
            DiskUtil.usableSpace = { 42L }
            DiskUtil.getAvailableStorageSpace(folder) shouldBe 42L
        } finally {
            DiskUtil.usableSpace = previous
        }
    }
}
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :core:archive:test --tests "mihon.core.archive.ZipWriterTest" :core:common:test --tests "eu.kanade.tachiyomi.util.storage.DiskUtilTest"`
Expected: FAIL de compilación. No existen `ZipWriter` (excluido de `src/main`) ni `DiskUtil.usableSpace`.

- [ ] **Step 3: `ZipWriter` de escritorio**

`core/archive/src/desktop/kotlin/mihon/core/archive/ZipWriter.kt`:

```kotlin
package mihon.core.archive

import android.content.Context
import com.hippo.unifile.UniFile
import java.io.Closeable
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Desktop copy of Mihon's ZipWriter: the same API, written with java.util.zip instead of libarchive. Entries are
 * STORED, as Mihon writes them: the pages are already compressed images.
 */
class ZipWriter(@Suppress("UNUSED_PARAMETER") context: Context?, file: UniFile) : Closeable {
    private val zip = ZipOutputStream(file.openOutputStream())

    fun write(file: UniFile) {
        val bytes = file.openInputStream().use { it.readBytes() }
        val entry = ZipEntry(file.name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
            time = file.lastModified()
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    override fun close() {
        zip.close()
    }
}
```

En `core/archive/build.gradle.kts`, el comentario de exclusión pasa a decir `// path in src/desktop.` (quita "ZipWriter comes back with downloads (subproject 4b).").

- [ ] **Step 4: `DiskUtil` de escritorio**

En `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/util/storage/DiskUtil.kt`:
- las dos funciones `getAvailableStorageSpace` se sustituyen por:

```kotlin
    /**
     * desktop: the free space Windows reports for the volume (spec 4b §2), instead of Android's StatFs. A variable so
     * a test can play a full disk.
     */
    @Volatile
    var usableSpace: (File) -> Long = File::getUsableSpace

    /**
     * Gets the available space for the disk that a file path points to, in bytes.
     */
    fun getAvailableStorageSpace(file: File): Long {
        return try {
            usableSpace(file)
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Gets the available space for the disk that a file path points to, in bytes.
     */
    fun getAvailableStorageSpace(f: UniFile): Long {
        return f.filePath?.let { getAvailableStorageSpace(File(it)) } ?: -1L
    }
```

- en `buildValidFilename`, `return truncateToLength(sb.toString(), maxBytes)` pasa a ser:

```kotlin
        val valid = truncateToLength(sb.toString(), maxBytes)
        // desktop: Windows reserves these names, with or without an extension (spec 4b §2)
        val reserved = WINDOWS_RESERVED_NAME.matchEntire(valid) ?: return valid
        return reserved.groupValues[1] + "_" + reserved.groupValues[2]
```

- y en el objeto, junto a `MAX_FILE_NAME_BYTES`:

```kotlin
    private val WINDOWS_RESERVED_NAME = Regex("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$", RegexOption.IGNORE_CASE)
```

- se quitan los imports `android.os.StatFs` y `android.os.Environment` si quedan sin uso.

- [ ] **Step 5: `getComicInfo` y "Solo descargados" en `Manga.kt`**

En `desktop-app/src/main/kotlin/eu/kanade/domain/manga/model/Manga.kt`:
- la línea `// desktop: getComicInfo comes back with downloads (subproject 4b)` se sustituye por la función `getComicInfo` de `app/src/main/java/eu/kanade/domain/manga/model/Manga.kt`, copiada sin cambios, con sus imports (`kotlinx.datetime.TimeZone`, `kotlinx.datetime.number`, `kotlinx.datetime.toLocalDateTime`, `tachiyomi.core.metadata.comicinfo.ComicInfo`, `tachiyomi.core.metadata.comicinfo.ComicInfoPublishingStatus`, `tachiyomi.domain.chapter.model.Chapter`);
- en `downloadedFilter`, `// desktop: no "downloaded only" mode until downloads (subproject 4b)` pasa a ser la línea de Mihon:

```kotlin
        if (Injekt.get<Context>().appGraph.basePreferences.downloadedOnly.get()) return TriState.ENABLED_IS
```

  con los imports que use (`android.content.Context`, `uy.kohesive.injekt.Injekt`, `uy.kohesive.injekt.api.get`, `mihon.app.di.appGraph`) si aún no están.

`desktop-app/build.gradle.kts`: añade `implementation(projects.coreMetadata)` tras `implementation(projects.core.archive)` si `ComicInfo` no resuelve.

- [ ] **Step 6: Comprobar que pasan**

Run: `./gradlew :core:archive:test :core:common:test --tests "eu.kanade.tachiyomi.util.storage.DiskUtilTest" :desktop-app:compileKotlin`
Expected: PASS y BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add core/archive core/common desktop-app
git commit -m "feat: CBZ writing, Windows reserved filenames and free space for downloads

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Rutas, caché, cola guardada y borrados pendientes

**Files:**
- Copy (de `app/src/main/java/` a `desktop-app/src/main/kotlin/`): `eu/kanade/tachiyomi/data/download/{DownloadProvider,DownloadCache,DownloadStore,DownloadPendingDeleter}.kt`
- Delete: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/download/{DownloadProvider,DownloadCache}.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/download/DownloadProviderTest.kt`, `DownloadCacheTest.kt`

**Interfaces:**
- Consumes: de la Task 1, `DiskUtil.buildValidFilename`; de 4a, `StorageManager.getDownloadsDirectory()`.
- Produces (Mihon):
  - `DownloadProvider.getMangaDir(title, source): Result<UniFile>`, `findChapterDir(name, scanlator, url, mangaTitle, source): UniFile?`, `getSourceDirName`, `getMangaDirName` y `getChapterDirName`;
  - `DownloadCache.isChapterDownloaded(name, scanlator, url, mangaTitle, sourceId, skipCache): Boolean`, `invalidateCache()`, `changes: Flow<Unit>` e `isInitializing: StateFlow<Boolean>`;
  - `DownloadStore.restore()`, `DownloadPendingDeleter`;
  - en `TestAppGraph`: `downloadProvider` y `downloadCache`.

- [ ] **Step 1: Escribir los tests**

`DownloadProviderTest.kt`:

```kotlin
package mihon.desktop.app.download

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import java.io.File

class DownloadProviderTest {
    private val graph: TestAppGraph = startTestApp()
    private val provider get() = graph.downloadProvider
    private val source = TestSources.http

    private fun downloads(): File = File(graph.storageManager.getDownloadsDirectory()!!.filePath!!)

    @Test
    fun `reserved and forbidden names are sanitized, and the chapter is found again`(): Unit = runBlocking {
        provider.getMangaDirName("CON") shouldBe "CON_"
        provider.getChapterDirName("Tomo: 1?", null, "/c1") shouldStartWith "Tomo_ 1_"

        val mangaDir = provider.getMangaDir("CON", source).getOrThrow()
        mangaDir.createDirectory(provider.getChapterDirName("Tomo: 1?", null, "/c1"))

        provider.findChapterDir("Tomo: 1?", null, "/c1", "CON", source).shouldNotBeNull()
    }

    @Test
    fun `a path longer than 260 characters is written and found`(): Unit = runBlocking {
        val title = "José " + "x".repeat(150)
        val chapter = "Capítulo " + "y".repeat(150)
        val dir = provider.getMangaDir(title, source).getOrThrow().createDirectory(
            provider.getChapterDirName(chapter, null, "/largo"),
        )!!
        File(dir.filePath!!, "001.png").writeText("p")
        File(dir.filePath!!).absolutePath.length > 260 shouldBe true

        provider.findChapterDir(chapter, null, "/largo", title, source).shouldNotBeNull()
    }

    @Test
    fun `a downloads folder copied from Android, with Mihon's layout, is recognized`(): Unit = runBlocking {
        val chapterDir = File(
            downloads(),
            "${provider.getSourceDirName(source)}/Copiado de Android/" +
                provider.getChapterDirName("Capítulo 1", null, "/android/c1"),
        ).apply { mkdirs() }
        File(chapterDir, "001.png").writeText("p")

        provider.findChapterDir("Capítulo 1", null, "/android/c1", "Copiado de Android", source).shouldNotBeNull()
    }
}
```

`DownloadCacheTest.kt`:

```kotlin
package mihon.desktop.app.download

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.seconds

class DownloadCacheTest {
    private val graph: TestAppGraph = startTestApp()

    private fun isDownloaded(chapter: String, manga: String) = graph.downloadCache.isChapterDownloaded(
        chapter,
        null,
        "/cache/$chapter",
        manga,
        TestSources.http.id,
        skipCache = false,
    )

    @Test
    fun `chapters on disk are seen as downloaded, and stop being so when deleted outside the app`(): Unit =
        runBlocking {
            val provider = graph.downloadProvider
            val mangaDir = File(provider.getMangaDir("Caché José", TestSources.http).getOrThrow().filePath!!)
            val chapterDir = File(mangaDir, provider.getChapterDirName("Capítulo 1", null, "/cache/Capítulo 1"))
            chapterDir.mkdirs()
            File(chapterDir, "001.png").writeText("p")

            graph.downloadCache.invalidateCache()
            withTimeout(10.seconds) { while (!isDownloaded("Capítulo 1", "Caché José")) delay(100) }

            chapterDir.deleteRecursively()
            graph.downloadCache.invalidateCache()
            withTimeout(10.seconds) { while (isDownloaded("Capítulo 1", "Caché José")) delay(100) }
            isDownloaded("Capítulo 1", "Caché José") shouldBe false
        }
}
```

Añade a `TestAppGraph` (imports `eu.kanade.tachiyomi.data.download.DownloadProvider` y `DownloadCache`):

```kotlin
    val downloadProvider: DownloadProvider
    val downloadCache: DownloadCache
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.*"`
Expected: FAIL de compilación. Los stubs no tienen `getMangaDir`, `findChapterDir` ni `invalidateCache`.

- [ ] **Step 3: Copiar y adaptar**

`git rm desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/download/DownloadProvider.kt desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/download/DownloadCache.kt`. Luego copia los cuatro archivos y adapta así:

**`DownloadProvider.kt`**
- `val isDocumentTree = mangaDir.uri.scheme == ContentResolver.SCHEME_CONTENT` pasa a ser `val isDocumentTree = false // desktop: no SAF document trees`.
- Quita `import android.content.ContentResolver`.

**`DownloadStore.kt` y `DownloadPendingDeleter.kt`**
- Cada `preferences.edit { … }` pasa a ser `preferences.edit().apply { … }.apply()`, con `// desktop: no androidx.core's edit {}` en la primera.
- Las llamadas de dentro (`putString`, `remove`, `clear`) no cambian.
- Quita `import androidx.core.content.edit`.

**`DownloadCache.kt`**
- Imports: quita `android.util.AtomicFile`, `androidx.core.net.toUri`, `androidx.core.util.writeBytes` y `uy.kohesive.injekt.Injekt`; añade `java.net.URI`, `java.nio.file.Files` y `java.nio.file.StandardCopyOption`.
- El archivo de la caché:

```kotlin
    // desktop: a plain file, written through a temp file and an atomic move instead of Android's AtomicFile
    private val diskCacheFile: File
        get() = File(context.cacheDir, "dl_index_cache_v3")
```

- `diskCacheFile.baseFile.exists()` pasa a ser `diskCacheFile.exists()`, y `diskCacheFile.openRead().use {` pasa a ser `diskCacheFile.inputStream().use {`.
- `diskCacheFile.writeBytes(bytes)` pasa a ser:

```kotlin
                val tmp = File(diskCacheFile.path + ".tmp").apply { writeBytes(bytes) }
                Files.move(
                    tmp.toPath(),
                    diskCacheFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
```

- En `UniFileAsStringSerializer.deserialize`:

```kotlin
            // desktop: the uri is a java file: uri (UniFile.uri, spec 4a); one that no longer parses is dropped
            runCatching { UniFile.fromFile(File(URI(decoder.decodeString()))) }.getOrNull()
```

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.*"`
Expected: PASS, 4 tests. El resto de desktop-app compila: `DownloadManager` sigue siendo el stub, y sus llamadas a `DownloadCache` usan la API real, que tiene esos métodos.

Run: `bash scripts/check-no-android.sh`
Expected: OK.

- [ ] **Step 5: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): Mihon's download paths, cache, saved queue and pending deletions

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Motor de descargas

**Files:**
- Copy: `eu/kanade/tachiyomi/data/download/{Downloader,DownloadManager}.kt`, `eu/kanade/domain/download/interactor/DeleteDownload.kt`
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/download/{DownloadJob,DownloadNotifier}.kt`
- Delete: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/download/DownloadManager.kt`, `desktop-app/src/stubs/kotlin/eu/kanade/domain/download/interactor/DeleteDownload.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt` (`downloadManager`, `downloadPreferences`, `downloadCache`), `i18n/.../{base,es}/strings.xml`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/download/DownloaderTest.kt`

**Interfaces:**
- Consumes: de la Task 2, `DownloadProvider`, `DownloadCache` y `DownloadStore`; de la Task 1, `ZipWriter`, `DiskUtil.usableSpace` y `getComicInfo`.
- Produces:
  - `DownloadManager` (Mihon): `queueState: StateFlow<List<Download>>`, `isDownloaderRunning: StateFlow<Boolean>`, `startDownloads()`, `pauseDownloads()`, `clearQueue()`, `downloadChapters(manga, chapters, autoStart = true)`, `cancelQueuedDownloads(downloads)`, `reorderQueue(downloads)`, `deleteChapters(chapters, manga, source)`, `isChapterDownloaded(name, scanlator, url, mangaTitle, sourceId)`, `enqueueChaptersToDelete(chapters, manga)`, `deletePendingChapters()`, `buildPageList(source, manga, chapter)`, `getQueuedDownloadOrNull(chapterId)` y `addDownloadsToStartOfQueue(downloads)`;
  - `class DownloadJob(downloader, scope)`, con `isRunning: StateFlow<Boolean>` y `start()`;
  - `DownloadNotifier(context)` con la API de Mihon, salvo `onWarning(reason, timeout, mangaId)`, que no recibe `PendingIntent`;
  - `AppGraph.downloadManager`, `downloadPreferences` y `downloadCache`;
  - cadena `desktop_downloads_finished`.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.download

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.util.storage.DiskUtil
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.core.archive.ArchiveReader
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.io.File
import kotlin.time.Duration.Companion.seconds

class DownloaderTest {
    private val graph: TestAppGraph = startTestApp()
    private val source = TestSources.http

    private suspend fun awaitQueue(done: (List<Download>) -> Boolean) = withTimeout(60.seconds) {
        while (!done(graph.downloadManager.queueState.value)) delay(100)
    }

    private fun chapterFile(manga: Manga, chapter: Chapter, cbz: Boolean): File {
        val provider = graph.downloadProvider
        val mangaDir = File(graph.storageManager.getDownloadsDirectory()!!.filePath!!)
            .resolve(provider.getSourceDirName(source)).resolve(provider.getMangaDirName(manga.title))
        val name = provider.getChapterDirName(chapter.name, chapter.scanlator, chapter.url)
        return mangaDir.resolve(if (cbz) "$name.cbz" else name)
    }

    @Test
    fun `a chapter downloads as a CBZ with its ComicInfo and its pages in order`(): Unit = runBlocking {
        graph.downloadPreferences.saveChaptersAsCBZ.set(true)
        source.pageCount = 3
        val (manga, chapters) = TestLibrary.httpManga(graph)

        graph.downloadManager.downloadChapters(manga, listOf(chapters[0]))
        awaitQueue { it.isEmpty() }

        ArchiveReader(chapterFile(manga, chapters[0], cbz = true)).use { reader ->
            val names = reader.useEntries { e -> e.map { it.name }.toList() }.sorted()
            names shouldContainExactly listOf("001.png", "002.png", "003.png", "ComicInfo.xml")
            reader.getInputStream("ComicInfo.xml")!!.use { it.readBytes().decodeToString() } shouldContain manga.title
        }
        graph.downloadManager.isChapterDownloaded(
            chapters[0].name,
            chapters[0].scanlator,
            chapters[0].url,
            manga.title,
            manga.source,
        ) shouldBe true
    }

    @Test
    fun `with CBZ off a chapter downloads as a folder`(): Unit = runBlocking {
        graph.downloadPreferences.saveChaptersAsCBZ.set(false)
        source.pageCount = 2
        val (manga, chapters) = TestLibrary.httpManga(graph)

        graph.downloadManager.downloadChapters(manga, listOf(chapters[1]))
        awaitQueue { it.isEmpty() }

        chapterFile(manga, chapters[1], cbz = false).list()!!.sorted() shouldContainExactly
            listOf("001.png", "002.png", "ComicInfo.xml")
        graph.downloadPreferences.saveChaptersAsCBZ.set(true)
    }

    @Test
    fun `a page that fails three times leaves its chapter in error and the queue goes on`(): Unit = runBlocking {
        source.pageCount = 2
        val (manga, chapters) = TestLibrary.httpManga(graph)
        source.images.failing += "${chapters[0].url}/1.png"

        graph.downloadManager.downloadChapters(manga, chapters.take(2))
        awaitQueue { queue -> queue.size == 1 && queue.single().status == Download.State.ERROR }

        graph.downloadManager.queueState.value.single().chapter.id shouldBe chapters[0].id
        chapterFile(manga, chapters[1], cbz = true).exists() shouldBe true
        graph.downloadManager.clearQueue()
        source.images.failing.clear()
    }

    @Test
    fun `with too little free space the chapter fails with Mihon's message and others are not lost`(): Unit =
        runBlocking {
            val (manga, chapters) = TestLibrary.httpManga(graph)
            val previous = DiskUtil.usableSpace
            try {
                DiskUtil.usableSpace = { 1L }
                graph.downloadManager.downloadChapters(manga, listOf(chapters[2]))
                awaitQueue { queue -> queue.singleOrNull()?.status == Download.State.ERROR }
            } finally {
                DiskUtil.usableSpace = previous
            }
            chapterFile(manga, chapters[2], cbz = true).exists() shouldBe false
            graph.downloadManager.clearQueue()
        }

    @Test
    fun `queued without starting, the queue is saved, restored paused, and resuming reuses the pages in _tmp`(): Unit =
        runBlocking {
            source.pageCount = 3
            val (manga, chapters) = TestLibrary.httpManga(graph)
            graph.downloadManager.downloadChapters(manga, listOf(chapters[0]), autoStart = false)

            graph.downloadStore.restore().map { it.chapter.id } shouldContainExactly listOf(chapters[0].id)
            graph.downloadManager.isDownloaderRunning.value shouldBe false

            // A page left in _tmp by a run that was cut short
            val tmp = File(chapterFile(manga, chapters[0], cbz = false).path + "_tmp").apply { mkdirs() }
            File(tmp, "001.png").writeBytes(source.images.png())
            val served = source.images.served.get()

            graph.downloadManager.startDownloads()
            awaitQueue { it.isEmpty() }
            (source.images.served.get() - served) shouldBe 2
        }

    @Test
    fun `two sources download side by side, and an error in one doesn't stop the other`(): Unit = runBlocking {
        source.pageCount = 2
        val (httpManga, httpChapters) = TestLibrary.httpManga(graph)
        val (otherManga, otherChapters) = TestLibrary.httpManga(graph, TestSources.http2)
        TestSources.http2.failPageList = IllegalStateException("caída")

        graph.downloadManager.downloadChapters(otherManga, listOf(otherChapters[0]))
        graph.downloadManager.downloadChapters(httpManga, listOf(httpChapters[0]))
        awaitQueue { queue -> queue.all { it.status == Download.State.ERROR } }

        chapterFile(httpManga, httpChapters[0], cbz = true).exists() shouldBe true
        graph.downloadManager.queueState.value.map { it.chapter.id } shouldContainExactly listOf(otherChapters[0].id)
        graph.downloadManager.clearQueue()
    }
}
```

Ayudas de test que hacen falta, en `FakeHttpSource.kt` y `FakeSource.kt` (`TestSources`):
- en `FakeImages`: `val served = AtomicInteger()` (incrementado en cada respuesta `.png` correcta) y `fun png(): ByteArray = png` (público);
- en `FakeHttpSource`: un parámetro `id` en el constructor con el valor por defecto actual, para tener una segunda fuente. `HttpSource.id` se deriva del nombre: dale también un `name` distinto;
- en `TestSources`: `val http2 = FakeHttpSource(server, images, name = "Fake HTTP 2")`, añadida a `TestBindings.sourceManager`;
- en `TestLibrary.httpManga`: un parámetro `source: FakeHttpSource = TestSources.http`;
- en `TestAppGraph`: `downloadStore: DownloadStore` y `downloadPreferences` (este ya está en `AppGraph` tras el Step 3).

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.DownloaderTest"`
Expected: FAIL. El stub de `DownloadManager` no descarga nada y `awaitQueue` agota el tiempo; o la compilación falla por `downloadStore`/`downloadPreferences`.

- [ ] **Step 3: Texto, `DownloadJob` y `DownloadNotifier`**

`base/strings.xml` tras `desktop_archive_reader_unavailable`:

```xml
    <string name="desktop_downloads_finished">Downloads finished</string>
```

`es/strings.xml` tras su traducción:

```xml
    <string name="desktop_downloads_finished">Descargas terminadas</string>
```

`DownloadJob.kt`:

```kotlin
package eu.kanade.tachiyomi.data.download

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import kotlin.time.Duration.Companion.milliseconds

/**
 * Desktop counterpart of Mihon's DownloadWorker (spec 4b §2): runs the downloader on the app scope, which outlives
 * the window while the app is in the tray, and says whether it's running.
 */
@Inject
@SingleIn(AppScope::class)
class DownloadJob(
    private val downloader: Downloader,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    private val running = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = running.asStateFlow()

    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!downloader.start()) return@launch
            running.value = true
            try {
                while (downloader.isRunning) delay(POLL)
            } finally {
                running.value = false
            }
        }
    }

    private companion object {
        val POLL = 500.milliseconds
    }
}
```

`DownloadNotifier.kt`:

```kotlin
package eu.kanade.tachiyomi.data.download

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.model.Download
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

/**
 * Desktop counterpart of Mihon's DownloadNotifier (spec 4b §2): the queue shows the progress; the tray, or an in-app
 * message without it, tells of a finished queue, an error and a warning. Errors come at most every 5 s: the tray stacks
 * them, where Android replaces one notification.
 */
@Inject
@SingleIn(AppScope::class)
class DownloadNotifier(private val context: Context) {
    @Volatile
    private var downloaded = false

    @Volatile
    private var lastError = 0L

    private val title get() = context.stringResource(MR.strings.download_notifier_downloader_title)

    fun onProgressChange(download: Download) {
        if (download.status == Download.State.DOWNLOADING) downloaded = true
    }

    fun dismissProgress() {}

    fun onPaused() {
        downloaded = false
    }

    fun onComplete() {
        if (!downloaded) return
        downloaded = false
        TrayNotifier.send(
            Notice(title, context.stringResource(MR.strings.desktop_downloads_finished), opensUpdates = false),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun onWarning(reason: String, timeout: Long? = null, mangaId: Long? = null) {
        TrayNotifier.send(Notice(title, reason, opensUpdates = false))
    }

    @Suppress("UNUSED_PARAMETER")
    fun onError(error: String? = null, chapter: String? = null, mangaTitle: String? = null, mangaId: Long? = null) {
        val now = System.currentTimeMillis()
        if (now - lastError < ERROR_INTERVAL_MS) return
        lastError = now
        TrayNotifier.send(
            Notice(
                title = mangaTitle?.plus(": $chapter") ?: title,
                message = error ?: context.stringResource(MR.strings.download_notifier_unknown_error),
                isError = true,
                opensUpdates = false,
            ),
        )
    }

    private companion object {
        const val ERROR_INTERVAL_MS = 5_000L
    }
}
```

- [ ] **Step 4: Copiar `Downloader`, `DownloadManager` y `DeleteDownload`**

`git rm` los stubs de `DownloadManager` y `DeleteDownload`, y copia los tres archivos. Adaptaciones:

**`Downloader.kt`**
- Imports: quita `eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier` y `eu.kanade.tachiyomi.data.notification.NotificationHandler`.
- El aviso de cola grande:

```kotlin
                    // desktop: no clickable help link in a tray notification
                    notifier.onWarning(
                        context.stringResource(
                            MR.strings.download_queue_size_warning,
                            context.stringResource(MR.strings.app_name),
                        ),
                        WARNING_NOTIF_TIMEOUT_MS,
                    )
```

- `splitTallImageIfNeeded` y su llamada se quitan. En el lugar de la llamada:

```kotlin
            // desktop: tall images aren't split (spec 4b §1): the desktop reader tiles them when it decodes
```

  Quita también `import java.util.Locale` si queda sin uso.

**`DownloadManager.kt`**
- Constructor: añade `private val downloadJob: DownloadJob,` tras `pendingDeleter`.
- `isDownloaderRunning`:

```kotlin
    // desktop: the DownloadJob instead of WorkManager's DownloadWorker
    val isDownloaderRunning: StateFlow<Boolean>
        get() = downloadJob.isRunning
```

- `startDownloads()`:

```kotlin
    fun startDownloads() {
        if (downloader.isRunning) return

        downloadJob.start() // desktop: instead of DownloadWorker.start(context)
    }
```

- Import `kotlinx.coroutines.flow.StateFlow`.

**`DeleteDownload.kt`:** sin cambios.

**`AppGraph.kt`** (imports `eu.kanade.tachiyomi.data.download.DownloadManager`, `DownloadCache`, `tachiyomi.domain.download.service.DownloadPreferences`):

```kotlin
    val downloadManager: DownloadManager
    val downloadPreferences: DownloadPreferences
    val downloadCache: DownloadCache
```

Quita `downloadCache` de `TestAppGraph`, que ya lo hereda.

- [ ] **Step 5: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.*"`
Expected: PASS. El test de la página que falla tarda unos 15 s, por los reintentos de Mihon (2, 4 y 8 s).

Run: `./gradlew :desktop-app:compileKotlin` y `bash scripts/check-no-android.sh`
Expected: OK. Si alguna llamada de la app usaba un método que el stub tenía y el real no (`statusFlow`, `progressFlow`, `renameChapter`), el real los tiene: revisa la firma.

- [ ] **Step 6: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): Mihon's downloader on the app scope, with tray notices

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Lectura de descargados, borrar tras leer y descargar por adelantado

**Files:**
- Copy: `eu/kanade/tachiyomi/ui/reader/loader/DownloadPageLoader.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt`, `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/download/DownloadedReaderTest.kt`

**Interfaces:**
- Consumes: de la Task 3, `DownloadManager`, `DownloadProvider` y `DownloadPreferences`.
- Produces: `ChapterLoader(context, chapterCache, appScope, pagesAhead, manga, source, downloadManager, downloadProvider)`.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.download

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Duration.Companion.seconds

class DownloadedReaderTest {
    private val graph: TestAppGraph = startTestApp()

    private suspend fun downloaded(manga: Manga, chapters: List<Chapter>) {
        graph.downloadManager.downloadChapters(manga, chapters)
        withTimeout(60.seconds) { while (graph.downloadManager.queueState.value.isNotEmpty()) delay(100) }
    }

    private fun isDownloaded(manga: Manga, chapter: Chapter) = graph.downloadManager.isChapterDownloaded(
        chapter.name,
        chapter.scanlator,
        chapter.url,
        manga.title,
        manga.source,
    )

    private suspend fun open(manga: Manga, chapter: Chapter) =
        graph.readerViewModelFactory.create(manga.id, chapter.id).also { viewModel ->
            withTimeout(15.seconds) {
                viewModel.state.first { it.viewerChapters != null || it.initError != null }
            }
            viewModel.state.value.initError shouldBe null
        }

    @Test
    fun `a downloaded chapter opens from the disk, with the server down`(): Unit = runBlocking {
        TestSources.http.pageCount = 3
        val (manga, chapters) = TestLibrary.httpManga(graph)
        downloaded(manga, listOf(chapters[0]))

        TestSources.http.images.failing += (0 until 3).map { "${chapters[0].url}/$it.png" }
        try {
            val pages = open(manga, chapters[0]).state.value.currentChapter.shouldNotBeNull().pages.shouldNotBeNull()
            pages.size shouldBe 3
            pages.all { page -> page.stream!!().use { it.readBytes().isNotEmpty() } } shouldBe true
        } finally {
            TestSources.http.images.failing.clear()
        }
    }

    @Test
    fun `remove after read deletes the read chapter only once the reader is left`(): Unit = runBlocking {
        TestSources.http.pageCount = 2
        graph.downloadPreferences.removeAfterReadSlots.set(0)
        val (manga, chapters) = TestLibrary.httpManga(graph)
        downloaded(manga, listOf(chapters[0]))

        val viewModel = open(manga, chapters[0])
        val pages = viewModel.state.value.currentChapter!!.pages!!
        viewModel.onPageSelected(pages.last())
        delay(1_000)
        isDownloaded(manga, chapters[0]) shouldBe true

        viewModel.saveHistory()
        withTimeout(10.seconds) { while (isDownloaded(manga, chapters[0])) delay(100) }
        graph.downloadPreferences.removeAfterReadSlots.set(-1)
    }

    @Test
    fun `reading a downloaded chapter queues the next ones when the following one is downloaded too`(): Unit =
        runBlocking {
            TestSources.http.pageCount = 4
            graph.downloadPreferences.autoDownloadWhileReading.set(2)
            val (manga, chapters) = TestLibrary.httpManga(graph)
            downloaded(manga, chapters.take(2))

            val viewModel = open(manga, chapters[0])
            viewModel.onPageSelected(viewModel.state.value.currentChapter!!.pages!![2])

            withTimeout(60.seconds) { while (!isDownloaded(manga, chapters[2])) delay(200) }
            graph.downloadPreferences.autoDownloadWhileReading.set(0)
        }
}
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.DownloadedReaderTest"`
Expected: FAIL, los tres.
- El primero: el lector baja las páginas por HTTP y fallan, así que sale `initError` o páginas con error.
- El segundo: no se borra nada.
- El tercero: no se encola nada.

- [ ] **Step 3: Copiar `DownloadPageLoader`**

Copia el archivo. En `getPagesFromDirectory`:

```kotlin
            ReaderPage(page.index, page.url, page.imageUrl) {
                // desktop: the page uri is a java file: uri (UniFile.uri, spec 4a)
                File(URI(page.uri.toString())).inputStream()
            }.apply {
```

Quita `import android.net.Uri`; añade `java.io.File` y `java.net.URI`.

- [ ] **Step 4: Rama de descargados en `ChapterLoader`**

- Constructor: añade al final `private val downloadManager: DownloadManager, private val downloadProvider: DownloadProvider,`.
- El comentario de cabecera pasa a `desktop: Mihon's loaders for downloaded, local and online chapters.`.
- `getPageLoader` pasa a ser `suspend` y queda así:

```kotlin
    private suspend fun getPageLoader(chapter: ReaderChapter): PageLoader {
        val dbChapter = chapter.chapter
        val isDownloaded = downloadManager.isChapterDownloadedOnDisk(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            manga.title,
            source,
        )
        return when {
            isDownloaded -> DownloadPageLoader(chapter, manga, source, downloadManager, downloadProvider)
            source is LocalSource -> when (val format = source.getFormat(chapter.chapter.toSChapter())) {
                is Format.Directory -> DirectoryPageLoader(format.file)
                is Format.Archive -> ArchivePageLoader(format.file.archiveReader(context))
                is Format.Epub -> EpubPageLoader(format.file.epubReader(context))
            }
            source is HttpSource -> HttpPageLoader(chapter, source, chapterCache, appScope, pagesAhead)
            source is StubSource -> error(context.stringResource(MR.strings.source_not_installed, source.toString()))
            else -> error(context.stringResource(MR.strings.loader_not_implemented_error))
        }
    }
```

  con los imports `eu.kanade.tachiyomi.data.download.DownloadManager` y `DownloadProvider`.

- [ ] **Step 5: El lector**

En `ReaderViewModel.kt`:
- **Constructor:** añade `private val downloadManager: DownloadManager,`, `private val downloadProvider: DownloadProvider,`, `private val downloadPreferences: DownloadPreferences,` y `private val getNextChapters: GetNextChapters,` (`mihon.domain.chapter.interactor.GetNextChapters`; si no existe en ese paquete, búscalo con `grep -rn "class GetNextChapters" domain/src`).
- **En `init`:** el `ChapterLoader(...)` recibe `downloadManager, downloadProvider` al final.
- **`isFilteredOut`:** el comentario `// desktop: Mihon's chapter filters without the downloaded one (downloads arrive in subproject 4b)` y el cuerpo pasan a:

```kotlin
    // desktop: Mihon's chapter filters, as a function of the desktop reader's chapter list
    private fun isFilteredOut(manga: Manga, chapter: Chapter): Boolean =
        (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !chapter.read) ||
            (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && chapter.read) ||
            (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !chapter.bookmark) ||
            (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && chapter.bookmark) ||
            (manga.downloadedFilter != TriState.DISABLED && isDownloadedFilteredOut(manga, chapter))

    private fun isDownloadedFilteredOut(manga: Manga, chapter: Chapter): Boolean {
        val downloaded = manga.isLocal() ||
            downloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, manga.title, manga.source)
        return when (manga.downloadedFilter) {
            TriState.ENABLED_IS -> !downloaded
            TriState.ENABLED_NOT -> downloaded
            TriState.DISABLED -> false
        }
    }
```

  Imports: `eu.kanade.domain.manga.model.downloadedFilter`, `tachiyomi.core.common.preference.TriState` y `tachiyomi.source.local.isLocal`.

- **`onPageSelected`**, al final:

```kotlin
        // Mihon: once a quarter of the chapter is read, the next downloaded chapters are queued ahead
        val pages = selectedChapter.pages ?: return
        if (page.number.toDouble() / pages.size > 0.25) downloadNextChapters()
```

- **`writeChapterProgress`:** dentro del `withLock`, antes de `if (onComplete) markDuplicatesRead(readerChapter)`:

```kotlin
                if (onComplete) deleteChapterIfNeeded(readerChapter)
```

- **`saveHistory()`** (al salir del lector) llama además a `deletePendingChapters()`.
- **Funciones nuevas**, copiadas de `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt` con estos ajustes:
  - `downloadNextChapters()` (líneas 566-601 de Mihon): `getCurrentChapter()` pasa a ser `state.value.currentChapter`, y `manga` es la propiedad del ViewModel de escritorio;
  - `deleteChapterIfNeeded(currentChapter)` (614-642): sin `chapterToDownload`, y `getUnfilteredChapterList()` pasa a ser `getUnfilteredChapterList(manga)` con `val manga = manga ?: return`;
  - `enqueueDeleteChapters(chapters)` (1087-1090);
  - `deletePendingChapters()` (1096-1102), con `progressWrites.withLock {}` igual que Mihon.

  Encima de cada una va `// desktop: Mihon's, ported for downloads (spec 4b §3 C)`. Lo de `chapterToDownload` y `cancelQueuedDownloads` de Mihon (sacar de la cola el capítulo que se abre) queda fuera; se apunta en el ledger.

- [ ] **Step 6: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.*" --tests "eu.kanade.tachiyomi.ui.reader.*" --tests "mihon.desktop.app.reader.*" --tests "mihon.desktop.app.local.*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): the reader opens downloads offline, removes after reading and downloads ahead

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Riel › Descargas

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/download/DownloadQueueViewModel.kt`, `DownloadsTab.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/HomeScreen.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/download/DownloadsTabTest.kt`

**Interfaces:**
- Consumes: de la Task 3, `DownloadManager`.
- Produces: `data object DownloadsTab : Tab`, y en `HomeScreen.tabs`: `LibraryTab, UpdatesTab, DownloadsTab, HistoryTab, BrowseTab, SettingsTab`.

- [ ] **Step 1: Escribir el test**

```kotlin
package mihon.desktop.app.download

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class DownloadsTabTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun `the rail shows Downloads with the queue count, and the queue pauses and resumes`() = runComposeUiTest {
        val graph = startTestApp()
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        runBlocking { graph.downloadManager.downloadChapters(manga, chapters, autoStart = false) }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitFor(hasText(str(MR.strings.pref_category_downloads)))
        waitFor(hasText("3"))
        onAllNodesWithText(str(MR.strings.pref_category_downloads)).onFirst().performClick()
        waitFor(hasText(manga.title))
        waitFor(hasText(chapters[0].name))

        onNodeWithContentDescription(str(MR.strings.action_resume)).performClick()
        waitUntil(15_000) { graph.downloadManager.isDownloaderRunning.value }
        waitFor(hasContentDescription(str(MR.strings.action_pause)))
        onNodeWithContentDescription(str(MR.strings.action_pause)).performClick()
        waitUntil(15_000) { !graph.downloadManager.isDownloaderRunning.value }

        runBlocking { graph.downloadManager.clearQueue() }
        graph.downloadManager.queueState.value.size shouldBe 0
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.DownloadsTabTest"`
Expected: FAIL. El riel no tiene la entrada `pref_category_downloads` y `waitUntil` agota el tiempo.

- [ ] **Step 3: ViewModel**

```kotlin
package mihon.desktop.app.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The download queue (spec 4b §3 B): Mihon's DownloadQueueViewModel without its RecyclerView adapter. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class DownloadQueueViewModel(private val downloadManager: DownloadManager) : ViewModel() {

    /** The queue grouped by source, in queue order. */
    val state: StateFlow<List<Pair<HttpSource, List<Download>>>> = downloadManager.queueState
        .map { queue -> queue.groupBy { it.source }.toList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isDownloaderRunning: StateFlow<Boolean> = downloadManager.isDownloaderRunning

    fun startDownloads() = downloadManager.startDownloads()

    fun pauseDownloads() {
        viewModelScope.launch { downloadManager.pauseDownloads() }
    }

    fun clearQueue() {
        viewModelScope.launch { downloadManager.clearQueue() }
    }

    fun cancel(downloads: List<Download>) {
        viewModelScope.launch { downloadManager.cancelQueuedDownloads(downloads) }
    }

    /** A drag ended: [download] goes to [index] of the whole queue. */
    fun move(download: Download, index: Int) {
        val queue = downloadManager.queueState.value.toMutableList()
        queue.remove(download)
        queue.add(index.coerceIn(0, queue.size), download)
        reorder(queue)
    }

    fun moveToTop(download: Download, series: Boolean) = moveTo(download, series, top = true)

    fun moveToBottom(download: Download, series: Boolean) = moveTo(download, series, top = false)

    /** Mihon's sort: each source keeps its place, and its chapters are sorted by [selector]. */
    fun <R : Comparable<R>> sort(selector: (Download) -> R, descending: Boolean) {
        reorder(
            state.value.flatMap { (_, downloads) ->
                if (descending) downloads.sortedByDescending(selector) else downloads.sortedBy(selector)
            },
        )
    }

    private fun moveTo(download: Download, series: Boolean, top: Boolean) {
        val queue = downloadManager.queueState.value
        val (moved, others) = queue.partition {
            if (series) it.manga.id == download.manga.id else it == download
        }
        reorder(if (top) moved + others else others + moved)
    }

    private fun reorder(downloads: List<Download>) {
        viewModelScope.launch { downloadManager.reorderQueue(downloads) }
    }
}
```

- [ ] **Step 4: Pantalla**

`DownloadsTab.kt`:

```kotlin
package mihon.desktop.app.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.download.model.Download
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import mihon.icons.materialsymbols.rounded.Download as DownloadIcon
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.rounded.MoreVert
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

/** Rail › Downloads (spec 4b §3 B): the queue, grouped by source, with drag to reorder. */
data object DownloadsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 2u,
            title = stringResource(MR.strings.pref_category_downloads),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.DownloadIcon),
        )

    @Composable
    override fun Content() {
        val viewModel = metroViewModel<DownloadQueueViewModel>()
        val groups by viewModel.state.collectAsStateWithLifecycle()
        val running by viewModel.isDownloaderRunning.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_download_queue),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (groups.isNotEmpty()) {
                            AppBarActions(
                                listOf(
                                    AppBar.OverflowAction(stringResource(MR.strings.action_order_by_upload_date)) {
                                        viewModel.sort({ it.chapter.dateUpload }, descending = false)
                                    },
                                    AppBar.OverflowAction(stringResource(MR.strings.action_order_by_chapter_number)) {
                                        viewModel.sort({ it.chapter.chapterNumber }, descending = false)
                                    },
                                    AppBar.OverflowAction(stringResource(MR.strings.action_cancel_all)) {
                                        viewModel.clearQueue()
                                    },
                                ),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                if (groups.isNotEmpty()) {
                    val label = stringResource(if (running) MR.strings.action_pause else MR.strings.action_resume)
                    ExtendedFloatingActionButton(
                        text = { Text(label) },
                        icon = {
                            Icon(
                                if (running) MaterialSymbols.RoundedFilled.Pause else MaterialSymbols.RoundedFilled.PlayArrow,
                                contentDescription = label,
                            )
                        },
                        onClick = { if (running) viewModel.pauseDownloads() else viewModel.startDownloads() },
                    )
                }
            },
        ) { contentPadding ->
            if (groups.isEmpty()) {
                EmptyScreen(MR.strings.information_no_downloads, modifier = Modifier.padding(contentPadding))
                return@Scaffold
            }
            val queue = groups.flatMap { it.second }
            val listState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                val moved = queue.firstOrNull { it.chapter.id == from.key } ?: return@rememberReorderableLazyListState
                val target = queue.indexOfFirst { it.chapter.id == to.key }
                if (target >= 0) viewModel.move(moved, target)
            }
            LazyColumn(state = listState, contentPadding = contentPadding) {
                groups.forEach { (source, downloads) ->
                    item(key = "source-${source.id}") {
                        Text(
                            text = "${source.name} (${downloads.size})",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(MaterialTheme.padding.medium),
                        )
                    }
                    items(downloads, key = { it.chapter.id }) { download ->
                        ReorderableItem(reorderState, key = download.chapter.id) {
                            DownloadRow(
                                download = download,
                                dragHandle = {
                                    Icon(
                                        MaterialSymbols.Rounded.DragHandle,
                                        contentDescription = null,
                                        modifier = Modifier.draggableHandle(),
                                    )
                                },
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(download: Download, dragHandle: @Composable () -> Unit, viewModel: DownloadQueueViewModel) {
    val progress by download.progressFlow.collectAsStateWithLifecycle(0)
    val status by download.statusFlow.collectAsStateWithLifecycle()
    val pages by download.pagesFlow.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dragHandle()
        Column(Modifier.weight(1f).padding(horizontal = MaterialTheme.padding.small)) {
            Text(download.manga.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                download.chapter.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (status == Download.State.ERROR) {
                Text(stringResource(MR.strings.download_notifier_title_error), color = MaterialTheme.colorScheme.error)
            } else {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                pages?.let { Text("${download.downloadedImages}/${it.size}", style = MaterialTheme.typography.bodySmall) }
            }
        }
        IconButton(onClick = { menu = true }) {
            Icon(MaterialSymbols.Rounded.MoreVert, contentDescription = stringResource(MR.strings.action_menu_overflow_description))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(
                MR.strings.action_move_to_top to { viewModel.moveToTop(download, series = false) },
                MR.strings.action_move_to_top_all_for_series to { viewModel.moveToTop(download, series = true) },
                MR.strings.action_move_to_bottom to { viewModel.moveToBottom(download, series = false) },
                MR.strings.action_move_to_bottom_all_for_series to { viewModel.moveToBottom(download, series = true) },
                MR.strings.action_cancel to { viewModel.cancel(listOf(download)) },
                MR.strings.cancel_all_for_series to {
                    viewModel.cancel(viewModel.state.value.flatMap { it.second }.filter { it.manga.id == download.manga.id })
                },
            ).forEach { (title, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(title)) },
                    onClick = {
                        menu = false
                        action()
                    },
                )
            }
        }
    }
}
```

Notas para quien implemente:
- Si el `rememberReorderableLazyListState` de la versión instalada pide `(lazyListState, contentPadding)`, pásale también `contentPadding`, como en `MigrationConfigScreen`.
- `draggableHandle` es una extensión de `ReorderableCollectionItemScope`: el `dragHandle` se compone dentro del lambda de `ReorderableItem`.
- Si alguna línea pasa de 120 caracteres, pártela.

- [ ] **Step 5: Riel y badge**

En `HomeScreen.kt`:
- `tabs` pasa a ser `listOf(LibraryTab, UpdatesTab, DownloadsTab, HistoryTab, BrowseTab, SettingsTab)`.
- El badge, además del de Novedades:

```kotlin
                                    // desktop: the queue's size on the Downloads entry (spec 4b §2)
                                    if (tab == DownloadsTab && queueSize > 0) Badge { Text("$queueSize") }
```

  con `val queueSize by remember { context.appGraph.downloadManager.queueState.map { it.size } }.collectAsState(0)` junto a `newUpdates`.
- **Volver a pulsar Novedades** (Mihon `onReselect`): en el `onClick` del item:

```kotlin
                        onClick = {
                            // desktop: Mihon's Updates onReselect opens the download queue
                            tabNavigator.current = if (tab == UpdatesTab && tabNavigator.current == UpdatesTab) {
                                DownloadsTab
                            } else {
                                tab
                            }
                        },
```

- En `UpdatesTab.kt`, el comentario `// desktop: no onReselect, the download queue arrives in subproject 4b` pasa a `// desktop: reselecting Updates opens Downloads, in HomeScreen's rail`.

- [ ] **Step 6: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.*" --tests "mihon.desktop.app.main.*"`
Expected: PASS. Si algún test de `main` esperaba el orden anterior del riel (Ajustes último), sigue igual: Ajustes sigue siendo el último.

- [ ] **Step 7: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): Downloads in the rail, with the queue, its count and drag to reorder

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Ganchos de la ficha, Biblioteca, Novedades y "Solo descargados"

**Files:**
- Copy: `eu/kanade/presentation/updates/UpdatesDeleteConfirmationDialog.kt`
- Modify:
  - `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`
  - `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/library/LibraryTab.kt`, `LibraryViewModel.kt`
  - `desktop-app/src/main/kotlin/eu/kanade/presentation/library/LibrarySettingsDialog.kt`, `components/LibraryToolbar.kt`
  - `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/updates/UpdatesTab.kt`, `UpdatesViewModel.kt`
  - `desktop-app/src/main/kotlin/eu/kanade/presentation/updates/{UpdatesFilterDialog,UpdatesScreen,UpdatesUiItem}.kt` (comentarios)
  - `desktop-app/src/main/kotlin/mihon/desktop/app/main/AppContent.kt` (banner)
- Modify tests: `BrowseAndMangaTest.kt`, `MigrationScreensTest.kt`, `LibraryTabTest.kt`, `UpdatesTabTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/download/DownloadHooksTest.kt`

**Interfaces:**
- Consumes: de la Task 3, `DownloadManager` y `AppGraph.downloadCache`.
- Produces: ganchos activos; nada nuevo para otras tareas.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.download

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class DownloadHooksTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 20_000) { onAllNodes(hasText(text), true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `a chapter's download button in the manga screen downloads it`() = runComposeUiTest {
        val graph = startTestApp()
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        setContent { AppProviders(graph) { Navigator(MangaScreen(manga.id)) { DefaultNavigatorScreenTransition(it) } } }

        waitForText(chapters[0].name)
        waitUntil(10_000) {
            onAllNodes(hasContentDescription(str(MR.strings.manga_download))).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithContentDescription(str(MR.strings.manga_download)).onFirst().performClick()
        waitUntil(60_000) {
            chapters.any {
                graph.downloadManager.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source)
            }
        }
    }

    @Test
    fun `downloaded only leaves the library with the manga that have downloads, under its banner`() =
        runComposeUiTest {
            val graph = startTestApp()
            val category = runBlocking { TestLibrary.category(graph) }
            val name = runBlocking { graph.getCategories.await().first { it.id == category }.name }
            val (kept, keptChapters) = runBlocking { TestLibrary.httpManga(graph) }
            val (hidden, _) = runBlocking { TestLibrary.httpManga(graph) }
            runBlocking {
                listOf(kept, hidden).forEach {
                    graph.updateManga.awaitUpdateFavorite(it.id, true)
                    graph.setMangaCategories.await(it.id, listOf(category))
                }
                graph.downloadManager.downloadChapters(kept, listOf(keptChapters[0]))
                withTimeout(60.seconds) { while (graph.downloadManager.queueState.value.isNotEmpty()) delay(100) }
            }
            setContent { AppContent(graph, crash = null, onRestart = {}) }

            waitForText(name)
            onNodeWithText(name).performClick()
            waitForText(hidden.title)
            onNodeWithContentDescription(str(MR.strings.action_menu_overflow_description)).performClick()
            onNodeWithText(str(MR.strings.label_downloaded_only)).performClick()

            waitUntil(15_000) { onAllNodesWithText(hidden.title).fetchSemanticsNodes().isEmpty() }
            waitForText(kept.title)
            waitForText(str(MR.strings.label_downloaded_only))
            graph.basePreferences.downloadedOnly.set(false)
        }
}
```

Actualiza los tests que asumían que no había descargas (desviación 4):
- **`BrowseAndMangaTest`:** quita el comentario `// Downloads arrive in subproject 4b…` y la aserción `... shouldBe 0` de `manga_download`. `FakeSource` no es `HttpSource`, pero Mihon muestra el botón igualmente.
- **`MigrationScreensTest`** (selección en la biblioteca): con descargas, la barra inferior lleva "Descargar", así que "Migrar" pasa al menú desbordado. Tras `AppEvents.send(Shortcut.SelectAll)`:
  - pulsa el último nodo con `contentDescription == str(MR.strings.action_menu_overflow_description)`;
  - luego pulsa `onNodeWithText(str(MR.strings.migrate))`;
  - el comentario pasa a `// With downloads the bottom bar overflows: Migrate is in its menu`.
- **`LibraryTabTest`:** `a stored downloaded filter doesn't hide the library while there are no downloads` pasa a `the downloaded filter hides a manga without downloads`. Con el filtro en `ENABLED_IS`, el título no aparece: `waitUntil { onAllNodes(hasText(manga.title)).fetchSemanticsNodes().isEmpty() }` tras abrir la categoría. El `finally` restaura `DISABLED`.
- **`UpdatesTabTest`:** lo mismo con `updatesPreferences.filterDownloaded`. La actualización no descargada no se lista.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.DownloadHooksTest" --tests "mihon.desktop.app.library.*"`
Expected: FAIL.
- La ficha no tiene botón de descarga.
- El menú de la Biblioteca no tiene "Solo descargados".
- Los filtros "Descargados" se ignoran.

- [ ] **Step 3: Restaurar los ganchos de Mihon**

Cada `null`/`// desktop: … subproject 4b` vuelve a ser la línea de Mihon de `app/src/main/java/<mismo archivo>`:

**`ui/manga/MangaScreen.kt`:**

```kotlin
            onDownloadChapter = viewModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            downloadProgressProvider = viewModel::getDownloadProgress,
```

```kotlin
            onDownloadActionClicked = viewModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
```

  con el import `eu.kanade.tachiyomi.source.isLocalOrStub` si falta. `MangaViewModel` ya tiene `runChapterDownloadActions`, `getDownloadProgress` y `runDownloadAction`; si alguno lo cambió 2a, restaura su cuerpo de Mihon.

**`ui/library/LibraryTab.kt`:**

```kotlin
                    onDownloadClicked = viewModel::performDownloadAction
                        .takeIf { state.selectedManga.fastAll { !it.isLocal() } },
```

**`ui/library/LibraryViewModel.kt`:** las dos líneas de Mihon en lugar del bloque `// desktop: no downloads until subproject 4b…`:

```kotlin
                globalFilterDownloaded = it[5] as Boolean,
                filterDownloaded = it[6] as TriState,
```

**`presentation/library/LibrarySettingsDialog.kt`:** el `TriStateItem` de Mihon `label_downloaded`, con `downloadedOnly`, en lugar del comentario. Si `viewModel.preferences` no existe en el `LibrarySettingsViewModel` de escritorio, añádelo como en Mihon (`val preferences: BasePreferences`).

**`presentation/library/components/LibraryToolbar.kt`:** el `listOf(...)` del menú desbordado gana, tras "Editar categorías":

```kotlin
                    // desktop: Mihon's More tab switch for downloaded-only mode, which desktop has no tab for
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.label_downloaded_only),
                        onClick = onClickDownloadedOnly,
                    ),
```

  - con el parámetro `onClickDownloadedOnly: () -> Unit` en `LibraryToolbar` y donde se llame;
  - en `LibraryTab`, `onClickDownloadedOnly = { graph.basePreferences.downloadedOnly.toggle() }`, con `context.appGraph` e `import tachiyomi.core.common.preference.toggle`.

**`ui/updates/UpdatesTab.kt`:**
- `onDownloadChapter = viewModel::downloadChapters,` y `onMultiDeleteClicked = viewModel::showConfirmDeleteChapters,`;
- el `when (state.dialog)` vuelve a tener la rama de Mihon:

```kotlin
            is UpdatesViewModel.Dialog.DeleteConfirmation -> {
                UpdatesDeleteConfirmationDialog(
                    onDismissRequest = onDismissDialog,
                    onConfirm = { viewModel.deleteChapters(dialog.toDelete) },
                )
            }
```

  con `when (val dialog = state.dialog)` y la rama `null -> {}`. Copia `UpdatesDeleteConfirmationDialog.kt` sin cambios.

**`ui/updates/UpdatesViewModel.kt`:** `filterDownloaded = it[0] as TriState,` en lugar del bloque `// desktop: no downloads until subproject 4b…`.

**`presentation/updates/UpdatesFilterDialog.kt`:** el `TriStateItem` de Mihon `label_downloaded` en lugar del comentario.

**`presentation/updates/UpdatesScreen.kt` y `UpdatesUiItem.kt`:** quita los comentarios `desktop: null until subproject 4b` y `desktop: no download indicator until downloads (subproject 4b)`. Los parámetros siguen siendo nulables, que no estorba.

**`mihon/desktop/app/main/AppContent.kt`:** el contenido del `Surface` pasa a ser una `Column`:

```kotlin
                val downloadedOnly by remember { graph.basePreferences.downloadedOnly.changes() }
                    .collectAsState(graph.basePreferences.downloadedOnly.get())
                val indexing by graph.downloadCache.isInitializing.collectAsState()
                Column(Modifier.fillMaxSize()) {
                    // desktop: Mihon's banners over the whole window (MainActivity), without incognito (subproject 5)
                    AppStateBanners(downloadedOnlyMode = downloadedOnly, incognitoMode = false, indexing = indexing)
                    Box(Modifier.weight(1f)) { /* the existing Box(Modifier.fillMaxSize()) { … } content */ }
                }
```

  El `Box(Modifier.fillMaxSize())` existente pasa a `Box(Modifier.weight(1f).fillMaxWidth())`, con su contenido intacto. Los imports `androidx.compose.foundation.layout.Column`, `eu.kanade.presentation.components.AppStateBanners` y `collectAsState`.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.*" --tests "mihon.desktop.app.library.*" --tests "mihon.desktop.app.browse.*" --tests "mihon.desktop.app.migration.*" --tests "mihon.desktop.app.main.*"`
Expected: PASS.

Run: `bash scripts/check-no-android.sh`
Expected: OK.

- [ ] **Step 5: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): download from the manga, library and updates screens, and downloaded-only mode

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Descarga automática, migración y Ajustes › Descargas

**Files:**
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/feature/migration/config/MigrationConfigScreenSheet.kt`
- Copy: `eu/kanade/presentation/more/settings/screen/SettingsDownloadScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsTab.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/download/AutoDownloadTest.kt`

**Interfaces:**
- Consumes: de la Task 3, `DownloadManager` y `AppGraph.downloadPreferences`; `FilterChaptersForDownload` (domain, Mihon).
- Produces: `SettingsDownloadScreen` en la lista de Ajustes.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.download

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class AutoDownloadTest {
    @Test
    fun `with download new chapters on, a library update downloads the new chapters`(): Unit = runBlocking {
        val graph = startTestApp()
        graph.downloadPreferences.downloadNewChapters.set(true)
        graph.libraryPreferences.autoUpdateMangaRestrictions.set(emptySet())
        TestSources.http.pageCount = 2
        val category = TestLibrary.category(graph)
        // A library manga whose three chapters aren't in the database yet: all three are new to the update
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "/auto/1", title = "Automática José", source = TestSources.http.id),
        )
        graph.updateManga.awaitUpdateFavorite(manga.id, true)
        graph.setMangaCategories.await(manga.id, listOf(category))

        graph.libraryUpdateJob.start(category) shouldBe true
        withTimeout(30.seconds) { graph.libraryUpdateJob.awaitIdle() }

        val chapters = graph.getChaptersByMangaId.await(manga.id)
        withTimeout(60.seconds) {
            while (!chapters.all { graph.downloadManager.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source) }) {
                delay(200)
            }
        }
        graph.downloadPreferences.downloadNewChapters.set(false)
    }

    @Test
    fun `Settings › Downloads has Mihon's options, without Wi-Fi or tall image splitting`() = runComposeUiTest {
        val graph = startTestApp()
        setContent {
            AppProviders(graph) { Navigator(SettingsDownloadScreen) { DefaultNavigatorScreenTransition(it) } }
        }

        waitUntil(15_000) {
            onAllNodes(hasText(str(MR.strings.save_chapter_as_cbz))).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodes(hasText(str(MR.strings.connected_to_wifi))).fetchSemanticsNodes().size shouldBe 0
        onAllNodes(hasText(str(MR.strings.split_tall_images))).fetchSemanticsNodes().size shouldBe 0
    }
}
```

Imports extra de ese test: `cafe.adriel.voyager.navigator.Navigator`, `eu.kanade.presentation.util.DefaultNavigatorScreenTransition`, `eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen` y `mihon.desktop.app.main.AppProviders` (sobran `AppContent`, `onAllNodesWithText`, `onFirst` y `performClick`).

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.AutoDownloadTest"`
Expected: FAIL.
- La actualización no encola descargas y el `while` agota el tiempo.
- `SettingsDownloadScreen` no existe y la compilación falla.

- [ ] **Step 3: Descarga automática**

En `LibraryUpdateJob.kt`:
- Constructor: `private val downloadManager: DownloadManager, private val filterChaptersForDownload: FilterChaptersForDownload,` (`mihon.domain.chapter.interactor.FilterChaptersForDownload`).
- En `updateChapters`, dentro de `if (newChapters.isNotEmpty()) {`, como en `LibraryUpdateWorker` de Mihon:

```kotlin
                    // desktop: Mihon's automatic download of new chapters (spec 4b §2)
                    val toDownload = filterChaptersForDownload.await(manga, newChapters)
                    if (toDownload.isNotEmpty()) {
                        downloadManager.downloadChapters(manga, toDownload, autoStart = false)
                        hasDownloads = true
                    }
```

  con `@Volatile var hasDownloads = false` local antes del bucle. Tras el bucle:

```kotlin
        if (hasDownloads) downloadManager.startDownloads()
```

  (`hasDownloads` es local y la escribe un solo hilo a la vez; si `forEachWithProgress` es concurrente, usa `AtomicBoolean`.)

- [ ] **Step 4: Migración**

En `MigrationConfigScreenSheet.kt`, el comentario `// desktop: removing downloads arrives with downloads (subproject 4b)` vuelve a ser el `MigrationSheetSwitchItem` de Mihon (`val removeDownloads = MigrationFlag.REMOVE_DOWNLOAD in migrationFlags` y el switch `migrationConfigScreen_removeDownloadsTitle`). En la copia de escritorio usa `headlineContent`, como los otros `ListItem` de 3b.

- [ ] **Step 5: Ajustes › Descargas**

Copia `SettingsDownloadScreen.kt` y quita los `SwitchPreference` de `downloadOnlyOverWifi` y `splitTallImages`, con `// desktop: no metered networks; tall images aren't split (spec 4b §1)`.

En `SettingsTab.kt`, tras la entrada del lector:

```kotlin
            Triple(MR.strings.pref_category_downloads, MaterialSymbols.Rounded.Download, SettingsDownloadScreen),
```

con los imports `eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen` y `mihon.icons.materialsymbols.rounded.Download`.

- [ ] **Step 6: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.download.*" --tests "mihon.desktop.app.library.*" --tests "mihon.desktop.app.migration.*" --tests "mihon.desktop.app.settings.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): automatic downloads of new chapters, remove downloads on migrating, Settings › Downloads

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Verificación completa y prueba manual del 4b

**Files:**
- Modify: ninguno, salvo los arreglos que salgan

- [ ] **Step 1: Suite completa y checks**

Run: `./gradlew test spotlessCheck`
Expected: BUILD SUCCESSFUL. Unos 415 tests: los 394 de antes más los nuevos.

Run: `bash scripts/check-no-android.sh` y `bash scripts/check-licenses.sh`
Expected: los dos OK.

- [ ] **Step 2: Sin referencias pendientes**

Run: `grep -rn "subproject 4b\|subproject 4\b" desktop-app/src desktop-core/src --include=*.kt`
Expected: nada. Los stubs de descargas ya no existen (`ls desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/download` falla).

- [ ] **Step 3: Arranque real**

Run: `./gradlew :desktop-app:run` con `MANGAREADER_HOME` apuntando a una carpeta temporal, durante 2 minutos.
Expected: en el log no hay errores de Metro, de la caché de descargas ni de la cola.

- [ ] **Step 4: Commit de los arreglos (si los hay)**

```bash
git add -A desktop-app desktop-core core
git commit -m "chore: tidy the last 4b notes

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 5: Prueba manual (spec §5), al usuario**

Pasa al usuario esta lista para ejecutar con `./gradlew :desktop-app:run`:
1. Descargar 3 capítulos de una fuente real como CBZ, y otro como carpeta.
2. Leerlos con la red desconectada.
3. Cerrar la app con la cola a medias, volver, ir a Descargas y reanudar.
4. Copiar una carpeta `downloads/` de Android a `Documentos\MangaReader\downloads` y ver sus capítulos como descargados.
5. Con "Descargar capítulos nuevos" activo, actualizar la biblioteca: los nuevos se descargan.
6. Con "Borrar tras leer" activo, leer un capítulo descargado y salir del lector: se borra el que toca.
