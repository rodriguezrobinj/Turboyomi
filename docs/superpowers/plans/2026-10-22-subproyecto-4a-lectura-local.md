# Subproyecto 4a: lectura local y almacenamiento. Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La app gana la fuente "Local" con la carpeta `local/` de Mihon, el lector abre capítulos locales (carpetas, ZIP/CBZ, RAR/CBR, 7Z/CB7, TAR/CBT y EPUB), y la ubicación de almacenamiento se elige en Ajustes.

**Architecture:**
- `core/archive`, `core-metadata` y `source-local` pasan a módulos JVM con el patrón de `core/common`:
  - el código de Mihon se queda en `src/main`;
  - lo que es solo de Android se excluye y tiene copia de escritorio en `src/desktop/kotlin`, con la misma ruta.
- `ArchiveReader` de escritorio lee con 7-Zip-JBinding, con la misma API que Mihon.
- `DesktopSourceManager` pone `LocalSource` primera. `StorageManager` de `domain` usa un `DesktopFolderProvider` que apunta a `AppDirs.storage` (`Documentos\MangaReader`).

**Tech Stack:**
- Kotlin 2.4.21 y JDK 21.
- Compose Multiplatform 1.12.2, Voyager y Metro 1.4.5.
- 7-Zip-JBinding 16.02-2.01 (`sevenzipjbinding` + `sevenzipjbinding-windows-amd64`), jsoup y xmlutil.
- JUnit 5, kotest y `compose.uiTest`.

**Spec:** `docs/superpowers/specs/2026-10-22-subproyecto-4a-lectura-local-design.md`. Spec padre: `docs/superpowers/specs/2026-10-18-windows-port-design.md` (§4.1, §4.3, §5.3).

## Global Constraints

- **Portado de Mihon:**
  - en desktop-app, los archivos se copian de `app/src/main/java/<ruta>` a `desktop-app/src/main/kotlin/<ruta>`, con el mismo paquete;
  - en los módulos `core/archive`, `core-metadata` y `source-local`, el código de Mihon se queda en `src/main`; lo que cambia va a `src/desktop/kotlin/<misma ruta>` y se excluye de `src/main` en su `build.gradle.kts`, como hace `core/common`;
  - cada cambio lleva un comentario `// desktop: <motivo>` en la línea modificada o justo encima.
- **Nada de `android.*` ni `androidx.*` en desktop-app** fuera de lo que permite `scripts/check-no-android.sh` (`android.content.Context`, `android.widget.Toast`, `androidx.compose|lifecycle|paging|preference|navigationevent`, `androidx.activity.compose.BackHandler`). Para URIs se usa `java.net.URI`.
- **Textos nuevos:**
  - van a `i18n/src/commonMain/moko-resources/base/strings.xml`, al final del bloque `<!-- Desktop -->` (tras `desktop_move_down`);
  - y a `.../es/strings.xml`, tras `<string name="desktop_move_down">Bajar</string>`;
  - prefijo `desktop_`. `SpanishStringsTest` exige la traducción.
- **Git:**
  - nunca `git push` al remoto `upstream` (mihonapp/mihon);
  - commits con el trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`;
  - rama `subproyecto-4a`, creada desde `master` con este plan commiteado.
- **Tests:** los que son `= runBlocking { }` declaran `: Unit`.
- **Archivos con `\\`:** se escriben con la herramienta Write, no con heredoc de Bash.
- **Fuera de alcance** (spec §1):
  - descargas, `ZipWriter`, `DownloadPageLoader`, el filtro "Descargados" y `getComicInfo` van a 4b;
  - copias de seguridad y caché van al subproyecto 5;
  - los nativos en el MSI van al empaquetado.
- **Muestras binarias de test:** van en `src/test/resources/archives/`. Se generan una vez con los comandos de la Task 1 y se commitean; los tests no llaman a herramientas externas.

## Desviaciones del spec (aprobar antes de ejecutar)

1. **Archivo dañado al listar.**
   - El spec §4 dice "se salta".
   - Mihon (`be44a81fb`, "Keep listing local chapters when one archive can't be read") mantiene el capítulo en la lista sin sus metadatos y sigue con los demás. Al abrirlo, el lector muestra el error.
   - Se queda el comportamiento de Mihon: el listado no se rompe y el capítulo dañado se ve. Los tests lo fijan así.
2. **Ruta en Ajustes.**
   - Mihon muestra "Todavía no has proporcionado ninguna carpeta" mientras se use la ruta por defecto.
   - Aquí siempre se muestra la ruta, como pide el spec §3 C, porque la carpeta por defecto ya funciona sin elegirla.
3. **Selector de carpetas.**
   - El `FileDialog` de AWT no elige carpetas en Windows.
   - Se usa `JFileChooser` en modo solo carpetas, con el aspecto de Swing.
4. **Piezas que el spec no nombra y hacen falta:**
   - `ImageUtil` de escritorio (`isImage` y `findImageType` por bytes mágicos): la de Mihon usa `ImageDecoder` de Android y está excluida desde el subproyecto 1. La usan la fuente local y los cargadores.
   - `EpubReader` de escritorio: el `resolveZipPath` de Mihon usa `File.canonicalPath`, que en Windows añade la letra de unidad.
   - `UniFile.uri` y `MangaCoverFetcher` aceptan URIs `file:` con espacios y tildes, para las portadas locales.
   - `AppDirs.storage`: con `MANGAREADER_HOME` (ejecuciones de desarrollo y tests) la carpeta base va a `<home>\storage` y no toca `Documentos`.
   - Se excluye `AndroidStorageFolderProvider` de `core/common`. También se contribuye a `AppScope` y chocaría con `DesktopFolderProvider`.

## Review Focus

1. **ZIPs creados en Windows con `\` en los nombres de entrada.** Se listan con `/`, como libarchive, y un EPUB así resuelve sus rutas. Test en la Task 1.
2. **Imágenes con extensión en mayúsculas** (`03.JPG`) dentro de un CBZ o de una carpeta. Cuentan como páginas, detectadas por sus bytes. Test en la Task 4.
3. **Una carpeta local borrada o renombrada después de añadir el manga a la biblioteca.** Al abrir un capítulo, el lector muestra "Capítulo no encontrado" y no se cuelga. Test en la Task 4.
4. **Cambiar la ubicación con la app abierta.** Se crean `local/`, `downloads/` y `autobackup/` en la nueva carpeta, y la fuente local lee de ella. Test en la Task 2.
5. **Nombres de carpeta con `#`, `%`, `&` y tildes.** La portada de la carpeta carga en la biblioteca (URI `file:` codificado). Test en la Task 5.

## Mapa de archivos

| Ruta | Responsabilidad |
|---|---|
| `core/archive/build.gradle.kts`, `core/archive/src/desktop/kotlin/mihon/core/archive/{ArchiveReader,EpubReader,UniFileExtensions}.kt` | Archivos comprimidos con 7-Zip-JBinding y EPUB con rutas sin letra de unidad |
| `core/archive/src/test/resources/archives/{paginas.7z,paginas.tar,rar4.rar,rar5.rar}` | Muestras |
| `core/common/src/desktop/kotlin/tachiyomi/core/common/util/system/ImageUtil.kt` | `isImage` y `findImageType` por bytes mágicos |
| `core-metadata/build.gradle.kts` | Módulo JVM (ComicInfo, MangaDetails) |
| `source-local/build.gradle.kts`, `source-local/src/desktop/kotlin/tachiyomi/source/local/LocalSource.kt` | Fuente local de Mihon con ComicInfo leído por xmlutil para JVM |
| `desktop-core/src/main/kotlin/mihon/desktop/core/{AppDirs,DesktopFolderProvider,DesktopSourceManager}.kt` | Carpeta base, `FolderProvider` y `LocalSource` primera |
| `compat-shims/src/main/kotlin/com/hippo/unifile/UniFile.kt` | `uri` como URI `file:` válida |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/reader/loader/{ChapterLoader,DirectoryPageLoader,ArchivePageLoader,EpubPageLoader}.kt` | Lectura de capítulos locales |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/coil/MangaCoverFetcher.kt` | Portadas `file:` |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/util/MangaExtensions.kt`, `.../ui/manga/MangaCoverViewModel.kt` | Portada personalizada de un manga local |
| `desktop-app/src/main/kotlin/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt`, `mihon/desktop/app/settings/SettingsTab.kt`, `mihon/desktop/app/platform/FileDialogs.kt` | Ajustes › Datos y almacenamiento |
| `desktop-app/src/test/kotlin/mihon/desktop/app/testing/LocalFixtures.kt` | PNG, CBZ, EPUB y CB7 de test |

---

### Task 1: `core/archive` sobre 7-Zip-JBinding

**Files:**
- Modify: `settings.gradle.kts` (añadir `include(":core:archive")` tras `include(":core:common")`)
- Modify: `gradle/libs.versions.toml`
- Modify: `core/archive/build.gradle.kts` (reescrito)
- Create: `core/archive/src/desktop/kotlin/mihon/core/archive/ArchiveReader.kt`, `EpubReader.kt`, `UniFileExtensions.kt`
- Create: `core/archive/src/test/resources/archives/paginas.7z`, `paginas.tar`, `rar4.rar`, `rar5.rar`
- Modify: `NOTICE`, `i18n/src/commonMain/moko-resources/{base,es}/strings.xml`
- Test: `core/archive/src/test/kotlin/mihon/core/archive/ArchiveReaderTest.kt`, `EpubReaderTest.kt`

**Interfaces:**
- Produces (API de Mihon, sin cambios de firma):
  - `class ArchiveReader(file: File) : Closeable`, con `fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T` y `fun getInputStream(entryName: String): InputStream?`;
  - `class ArchiveEntry(name: String, isFile: Boolean)`, el de `src/main`. El nombre siempre usa `/`;
  - `class EpubReader(reader: ArchiveReader)`, con `getImagesFromPages(): List<String>`, `getInputStream(entryName)`, `getPackageHref()` y `getPackageDocument(ref)`;
  - `fun UniFile.archiveReader(context: Context): ArchiveReader` y `fun UniFile.epubReader(context: Context): EpubReader`;
  - si no se puede abrir el archivo, `ArchiveReader(file)` lanza `IOException`. Si los nativos no cargan, el mensaje es `desktop_archive_reader_unavailable`;
  - cadena `MR.strings.desktop_archive_reader_unavailable`.

- [ ] **Step 0: Rama**

```bash
git checkout -b subproyecto-4a master
```

- [ ] **Step 1: Generar las muestras**

Las PNG son de 1×1 y sirven para que el contenido sea una imagen real.

```bash
mkdir -p core/archive/src/test/resources/archives
F=$(mktemp -d)
mkdir -p "$F/José"
python -I - "$F" <<'EOF'
import sys, struct, zlib
def png(r, g, b):
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    raw = b"\x00" + bytes([r, g, b])
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
d = sys.argv[1]
for name, color in (("01.png", (255, 0, 0)), ("02.png", (0, 255, 0)), ("10.png", (0, 0, 255))):
    open(f"{d}/José/{name}", "wb").write(png(*color))
open(f"{d}/ComicInfo.xml", "w", encoding="utf-8").write('<?xml version="1.0" encoding="utf-8"?><ComicInfo><Number>7.5</Number></ComicInfo>')
EOF
(cd "$F" && "/c/Program Files/7-Zip/7z.exe" a -t7z paginas.7z "José" ComicInfo.xml && "/c/Program Files/7-Zip/7z.exe" a -ttar paginas.tar "José" ComicInfo.xml)
cp "$F/paginas.7z" "$F/paginas.tar" core/archive/src/test/resources/archives/
```

Muestras RAR de la suite de tests de libarchive (BSD-2). Se bajan a una carpeta propia y se decodifican con un script fuera de ella:

```bash
R=$(mktemp -d)
for f in test_read_format_rar.rar.uu test_read_format_rar5_stored.rar.uu; do
  curl -sSfL -o "$R/$f" "https://raw.githubusercontent.com/libarchive/libarchive/master/libarchive/test/$f"
done
python -I - "$R" <<'EOF'
import sys, binascii
d = sys.argv[1]
for src, dst in (("test_read_format_rar.rar.uu", "rar4.rar"), ("test_read_format_rar5_stored.rar.uu", "rar5.rar")):
    out = bytearray()
    for line in open(f"{d}/{src}"):
        if line.startswith("begin") or line.strip() in ("end", "`", ""):
            continue
        out += binascii.a2b_uu(line)
    open(f"{d}/{dst}", "wb").write(out)
EOF
cp "$R/rar4.rar" "$R/rar5.rar" core/archive/src/test/resources/archives/
"/c/Program Files/7-Zip/7z.exe" l core/archive/src/test/resources/archives/rar4.rar
```

Expected:
- `rar4.rar` (336 bytes) lista `test.txt`, `testlink`, `testdir\test.txt` y las carpetas `testdir` y `testemptydir`.
- `rar5.rar` (109 bytes) contiene `helloworld.txt`.

- [ ] **Step 2: Escribir los tests**

`core/archive/src/test/kotlin/mihon/core/archive/ArchiveReaderTest.kt`:

```kotlin
package mihon.core.archive

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveReaderTest {
    @TempDir
    lateinit var dir: Path

    private val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private fun resource(name: String): File = File(javaClass.getResource("/archives/$name")!!.toURI())

    private fun zip(name: String, entries: Map<String, ByteArray>): File = dir.resolve(name).toFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (entry, bytes) ->
                zip.putNextEntry(ZipEntry(entry))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun ArchiveReader.files(): List<String> = useEntries { entries ->
        entries.filter { it.isFile }.map { it.name }.toList()
    }

    private fun ArchiveReader.bytes(name: String): ByteArray = getInputStream(name)!!.use { it.readBytes() }

    @Test
    fun `a zip lists its files with their folders, reads each one, and has no entry that isn't there`() {
        val file = zip(
            "José Pérez.cbz",
            mapOf("José/01.png" to "uno".toByteArray(), "José/02.png" to "dos".toByteArray()),
        )

        ArchiveReader(file).use { reader ->
            reader.files() shouldContainExactlyInAnyOrder listOf("José/01.png", "José/02.png")
            reader.bytes("José/02.png").decodeToString() shouldBe "dos"
            reader.getInputStream("José/99.png") shouldBe null
        }
    }

    @Test
    fun `entries stored with backslashes are named with slashes, as libarchive names them`() {
        val file = zip("windows.zip", mapOf("José\\03.png" to "tres".toByteArray()))

        ArchiveReader(file).use { reader ->
            reader.files() shouldContainExactlyInAnyOrder listOf("José/03.png")
            reader.bytes("José/03.png").decodeToString() shouldBe "tres"
        }
    }

    @Test
    fun `7z and tar samples list their pages and ComicInfo, with real images`() {
        listOf("paginas.7z", "paginas.tar").forEach { sample ->
            ArchiveReader(resource(sample)).use { reader ->
                reader.files() shouldContainExactlyInAnyOrder
                    listOf("José/01.png", "José/02.png", "José/10.png", "ComicInfo.xml")
                reader.bytes("José/10.png").take(4) shouldBe pngMagic.toList()
                reader.bytes("ComicInfo.xml").decodeToString() shouldContain "<Number>7.5</Number>"
            }
        }
    }

    @Test
    fun `a RAR4 sample lists files apart from folders and reads them`() {
        ArchiveReader(resource("rar4.rar")).use { reader ->
            reader.files() shouldContainAll listOf("test.txt", "testdir/test.txt")
            reader.files() shouldNotContain "testemptydir"
            reader.bytes("test.txt").decodeToString() shouldBe "test text document\r\n"
        }
    }

    @Test
    fun `a RAR5 sample reads its file`() {
        ArchiveReader(resource("rar5.rar")).use { reader ->
            reader.bytes("helloworld.txt").decodeToString() shouldBe "hello libarchive test suite!\n"
        }
    }

    @Test
    fun `a file that isn't an archive fails with an IOException instead of hanging`() {
        val garbage = dir.resolve("roto.cbz").toFile().apply { writeBytes(ByteArray(1024) { (it * 31).toByte() }) }

        shouldThrow<IOException> { ArchiveReader(garbage) }
    }

    @Test
    fun `pages read from several threads at once come out whole`() {
        ArchiveReader(resource("paginas.7z")).use { reader ->
            val names = listOf("José/01.png", "José/02.png", "José/10.png")
            val expected = names.associateWith { reader.bytes(it).toList() }
            val pool = Executors.newFixedThreadPool(8)
            try {
                val reads = (1..64).map { i ->
                    pool.submit(Callable { names[i % 3].let { it to reader.bytes(it).toList() } })
                }
                reads.forEach { read ->
                    val (name, bytes) = read.get()
                    bytes shouldBe expected.getValue(name)
                }
            } finally {
                pool.shutdown()
            }
        }
    }
}
```

`core/archive/src/test/kotlin/mihon/core/archive/EpubReaderTest.kt`:

```kotlin
package mihon.core.archive

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubReaderTest {
    @TempDir
    lateinit var dir: Path

    private fun epub(entries: Map<String, String>): File = dir.resolve("libro José.epub").toFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `the images of an epub come in spine order, resolved against each page's folder`() {
        val file = epub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to
                    """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
                "OEBPS/content.opf" to """
                    <package><manifest>
                    <item id="p2" href="Text/p2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="p1" href="Text/p1.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine><itemref idref="p1"/><itemref idref="p2"/></spine></package>
                """.trimIndent(),
                "OEBPS/Text/p1.xhtml" to """<html><body><img src="../Images/a.png"/></body></html>""",
                "OEBPS/Text/p2.xhtml" to """<html><body><svg><image xlink:href="../Images/b.png"/></svg></body></html>""",
                "OEBPS/Images/a.png" to "A",
                "OEBPS/Images/b.png" to "B",
            ),
        )

        EpubReader(ArchiveReader(file)).use { epub ->
            val images = epub.getImagesFromPages()
            images shouldContainExactly listOf("OEBPS/Images/a.png", "OEBPS/Images/b.png")
            epub.getInputStream(images[1])!!.use { it.readBytes().decodeToString() } shouldBe "B"
        }
    }
}
```

- [ ] **Step 3: El módulo en el build**

En `gradle/libs.versions.toml`:
- en `[versions]`: `sevenzipjbinding = "16.02-2.01"`;
- en `[libraries]`:

```toml
sevenzipjbinding = { module = "net.sf.sevenzipjbinding:sevenzipjbinding", version.ref = "sevenzipjbinding" }
sevenzipjbinding-windows = { module = "net.sf.sevenzipjbinding:sevenzipjbinding-windows-amd64", version.ref = "sevenzipjbinding" }
```

`core/archive/build.gradle.kts` entero:

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
}

sourceSets {
    main {
        kotlin {
            srcDir("src/desktop/kotlin")
            // Android-only (libarchive over mmap, SAF file descriptors), or with a desktop copy of the same relative
            // path in src/desktop. ZipWriter comes back with downloads (subproject 4b).
            // Local val: a script-level one would drag the script object into the configuration cache.
            val androidOnly = setOf(
                "mihon/core/archive/ArchiveInputStream.kt",
                "mihon/core/archive/ArchiveReader.kt",
                "mihon/core/archive/EpubReader.kt",
                "mihon/core/archive/UniFileExtensions.kt",
                "mihon/core/archive/ZipWriter.kt",
            )
            exclude { it.relativePath.pathString in androidOnly && "/src/main/" in it.file.invariantSeparatorsPath }
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.i18n)
    implementation(libs.jsoup)
    implementation(libs.sevenzipjbinding)
    runtimeOnly(libs.sevenzipjbinding.windows)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback)
}
```

Y en `settings.gradle.kts`, `include(":core:archive")` tras `include(":core:common")`.

- [ ] **Step 4: Comprobar que fallan**

Run: `./gradlew :core:archive:test`
Expected: FAIL de compilación. No existe `ArchiveReader(File)` (el de `src/main` está excluido y aún no hay copia de escritorio).

- [ ] **Step 5: Texto nuevo**

`base/strings.xml`, tras `desktop_move_down`:

```xml
    <string name="desktop_archive_reader_unavailable">The archive reader couldn\'t load</string>
```

`es/strings.xml`, tras `<string name="desktop_move_down">Bajar</string>`:

```xml
    <string name="desktop_archive_reader_unavailable">No se pudo cargar el lector de archivos</string>
```

- [ ] **Step 6: `ArchiveReader` de escritorio**

`core/archive/src/desktop/kotlin/mihon/core/archive/ArchiveReader.kt`:

```kotlin
package mihon.core.archive

import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.StringDesc
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import tachiyomi.i18n.MR
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Desktop copy of Mihon's ArchiveReader: the same API, read with 7-Zip (7-Zip-JBinding) instead of libarchive over
 * mmap. Entry names use '/' whatever the archive stored, as libarchive gives them. A 7-Zip archive isn't thread-safe,
 * so every extraction holds this reader's lock; an entry is extracted to memory only when it's asked for.
 */
class ArchiveReader(file: File) : Closeable {
    private val input = RandomAccessFile(file, "r")
    private val archive: IInArchive = try {
        SevenZip.openInArchive(null, RandomAccessFileInStream(input))
    } catch (e: SevenZipNativeInitializationException) {
        input.close()
        throw IOException(StringDesc.Resource(MR.strings.desktop_archive_reader_unavailable).localized(), e)
    } catch (e: LinkageError) {
        input.close()
        throw IOException(StringDesc.Resource(MR.strings.desktop_archive_reader_unavailable).localized(), e)
    } catch (e: SevenZipException) {
        input.close()
        throw IOException("Can't open ${file.name}: ${e.message}", e)
    }

    private val lock = Any()
    private val items: List<ISimpleInArchiveItem> = archive.simpleInterface.archiveItems.toList()
    private val entries: List<ArchiveEntry> = items.map { ArchiveEntry(it.path.replace('\\', '/'), !it.isFolder) }

    fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T = block(entries.asSequence())

    fun getInputStream(entryName: String): InputStream? {
        val index = entries.indexOfFirst { it.isFile && it.name == entryName }
        if (index < 0) return null
        val bytes = ByteArrayOutputStream()
        val result = synchronized(lock) {
            items[index].extractSlow { data ->
                bytes.write(data)
                data.size
            }
        }
        if (result != ExtractOperationResult.OK) throw IOException("Can't extract $entryName: $result")
        return ByteArrayInputStream(bytes.toByteArray())
    }

    override fun close() {
        synchronized(lock) {
            archive.close()
            input.close()
        }
    }
}
```

- [ ] **Step 7: `EpubReader` y `UniFileExtensions` de escritorio**

`core/archive/src/desktop/kotlin/mihon/core/archive/EpubReader.kt`:
- Copia de `core/archive/src/main/kotlin/mihon/core/archive/EpubReader.kt`.
- Solo cambia `resolveZipPath`. Quita el `import java.io.File` si queda sin uso.

```kotlin
    /**
     * Resolves a zip path from base and relative components and a path separator.
     */
    // desktop: Mihon resolves with File.canonicalPath, which adds the drive letter on Windows; plain path segments
    private fun resolveZipPath(basePath: String, relativePath: String): String {
        if (relativePath.startsWith(pathSeparator)) {
            // Path is absolute, so return as-is.
            return relativePath
        }

        val segments = ArrayDeque<String>()
        (basePath.split(pathSeparator) + relativePath.split(pathSeparator)).forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString(pathSeparator)
    }
```

`core/archive/src/desktop/kotlin/mihon/core/archive/UniFileExtensions.kt`:

```kotlin
package mihon.core.archive

import android.content.Context
import com.hippo.unifile.UniFile
import java.io.File

// desktop: no SAF file descriptors; on Windows an archive is always a file on disk

@Suppress("UnusedReceiverParameter")
fun UniFile.archiveReader(context: Context) = ArchiveReader(File(filePath ?: error("Not a file on disk: $uri")))

fun UniFile.epubReader(context: Context) = EpubReader(archiveReader(context))
```

- [ ] **Step 8: Licencias**

Al final de `NOTICE`:

```text

The archive reader uses 7-Zip-JBinding (https://sevenzipjbind.sourceforge.net/), which bundles 7-Zip by Igor
Pavlov, licensed under the GNU LGPL 2.1 with the unRAR restriction: its RAR code may be used only to read RAR
archives, never to create them or to recreate the RAR compression algorithm.

The RAR samples core/archive/src/test/resources/archives/rar4.rar and rar5.rar come from the libarchive test suite
(https://github.com/libarchive/libarchive), licensed under the BSD 2-Clause License.
```

- [ ] **Step 9: Comprobar que pasan**

Run: `./gradlew :core:archive:test`
Expected: PASS, 8 tests (7 de `ArchiveReaderTest` y 1 de `EpubReaderTest`).

Si 7-Zip-JBinding no carga en JDK 21 (riesgo del spec §6), **para** y avisa: la salida, junrar más Commons Compress sin RAR5, es una decisión del usuario.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml core/archive NOTICE i18n
git commit -m "feat(core-archive): read ZIP, RAR, 7z, TAR and EPUB on desktop with 7-Zip-JBinding

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Carpeta de almacenamiento

**Files:**
- Modify: `desktop-core/src/main/kotlin/mihon/desktop/core/AppDirs.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopFolderProvider.kt`
- Modify: `core/common/build.gradle.kts` (excluir `AndroidStorageFolderProvider.kt`)
- Modify: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt` (`storagePreferences`)
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt` (`storageManager`)
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/AppDirsTest.kt`, `desktop-app/src/test/kotlin/mihon/desktop/app/local/StorageTest.kt`

**Interfaces:**
- Produces:
  - `AppDirs(data: Path, local: Path, storage: Path = data.resolveSibling("storage"))`;
  - `AppDirs.fromEnvironment(env, documents: () -> Path = …)`;
  - `class DesktopFolderProvider(appDirs: AppDirs) : FolderProvider` (`@ContributesBinding(AppScope::class)`);
  - `AppGraph.storagePreferences: StoragePreferences`;
  - `TestAppGraph.storageManager: StorageManager`.

- [ ] **Step 1: Escribir los tests**

Añade a `AppDirsTest`:

```kotlin
    @Test
    fun `the storage folder is Documents, wherever Windows has it, plus the app name`() {
        val dirs = AppDirs.fromEnvironment(
            mapOf(
                "APPDATA" to "C:\\Users\\José Pérez\\AppData\\Roaming",
                "LOCALAPPDATA" to "C:\\Users\\José Pérez\\AppData\\Local",
            ),
            documents = { Path("C:\\Users\\José Pérez\\OneDrive\\Documentos") },
        )
        dirs.storage shouldBe Path("C:\\Users\\José Pérez\\OneDrive\\Documentos", AppDirs.APP_NAME)
    }

    @Test
    fun `with the home override the storage folder stays under the home folder`() {
        val dirs = AppDirs.fromEnvironment(mapOf("MANGAREADER_HOME" to "D:\\Portable"))
        dirs.storage shouldBe Path("D:\\Portable", "storage")
    }
```

`desktop-app/src/test/kotlin/mihon/desktop/app/local/StorageTest.kt`:

```kotlin
package mihon.desktop.app.local

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds

class StorageTest {
    private val graph = startTestApp()

    @Test
    fun `by default local and downloads live in the app's storage folder`() {
        File(graph.storageManager.getLocalSourceDirectory()!!.filePath!!) shouldBe graph.appDirs.storage.resolve("local").toFile()
        File(graph.storageManager.getDownloadsDirectory()!!.filePath!!) shouldBe graph.appDirs.storage.resolve("downloads").toFile()
    }

    @Test
    fun `choosing another folder creates local, downloads and autobackup in it and reads from it`(): Unit = runBlocking {
        val manager = graph.storageManager
        val other = createTempDirectory("Otra José Pérez").toFile()

        graph.storagePreferences.baseStorageDirectory.set(other.toURI().toString())

        withTimeout(5.seconds) {
            while (!listOf("local", "downloads", "autobackup").all { File(other, it).isDirectory }) delay(50)
        }
        File(manager.getLocalSourceDirectory()!!.filePath!!) shouldBe File(other, "local")
        graph.storagePreferences.baseStorageDirectory.set(graph.storagePreferences.baseStorageDirectory.defaultValue())
    }
}
```

Añade a `TestAppGraph` (import `tachiyomi.domain.storage.service.StorageManager`):

```kotlin
    val storageManager: StorageManager
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.AppDirsTest" :desktop-app:test --tests "mihon.desktop.app.local.StorageTest"`
Expected: FAIL de compilación. No existen `AppDirs.storage`, `documents =`, `storageManager` ni `storagePreferences`.

- [ ] **Step 3: Implementar**

`AppDirs.kt`:

```kotlin
/**
 * Where the app keeps its files: roaming data (synced with the Windows profile), local machine data, and the storage
 * folder the user sees (spec 4a §2): Mihon's local/, downloads/ and autobackup/, in Documents by default.
 */
data class AppDirs(val data: Path, val local: Path, val storage: Path = data.resolveSibling("storage")) {
```

- `createAll()` añade `storage` a su lista.
- `fromEnvironment` queda así (imports `javax.swing.filechooser.FileSystemView`):

```kotlin
        /** [documents] is the user's Documents folder, which OneDrive may have moved (FileSystemView follows it). */
        fun fromEnvironment(
            env: Map<String, String> = System.getenv(),
            documents: () -> Path = { FileSystemView.getFileSystemView().defaultDirectory.toPath() },
        ): AppDirs {
            env[HOME_ENV]?.let { home -> return AppDirs(Path(home, "data"), Path(home, "local")) }
            val roaming = env["APPDATA"] ?: error("APPDATA is not set")
            val local = env["LOCALAPPDATA"] ?: error("LOCALAPPDATA is not set")
            return AppDirs(Path(roaming, APP_NAME), Path(local, APP_NAME), documents().resolve(APP_NAME))
        }
```

`DesktopFolderProvider.kt`:

```kotlin
package mihon.desktop.core

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.storage.FolderProvider
import java.io.File

/** Mihon's default storage folder on Windows: [AppDirs.storage], as a file: uri StorageManager can parse. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopFolderProvider(private val appDirs: AppDirs) : FolderProvider {
    override fun directory(): File = appDirs.storage.toFile()

    override fun path(): String = directory().toURI().toString()
}
```

En `core/common/build.gradle.kts`, añade a `androidOnly`:

```kotlin
                // Its desktop counterpart is DesktopFolderProvider (desktop-core); both would bind FolderProvider
                "tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt",
```

En `AppGraph` (import `tachiyomi.domain.storage.service.StoragePreferences`):

```kotlin
    val storagePreferences: StoragePreferences
```

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.AppDirsTest" :desktop-app:test --tests "mihon.desktop.app.local.StorageTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/common desktop-core desktop-app
git commit -m "feat(desktop-core): a storage folder in Documents for Mihon's local, downloads and autobackup

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Fuente local

**Files:**
- Modify: `settings.gradle.kts` (añadir `include(":core-metadata")` e `include(":source-local")`)
- Modify: `core-metadata/build.gradle.kts`, `source-local/build.gradle.kts` (reescritos)
- Create: `source-local/src/desktop/kotlin/tachiyomi/source/local/LocalSource.kt`
- Create: `core/common/src/desktop/kotlin/tachiyomi/core/common/util/system/ImageUtil.kt`
- Modify: `desktop-core/build.gradle.kts`, `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopSourceManager.kt`
- Modify: `desktop-app/build.gradle.kts`
- Delete: `desktop-app/src/stubs/kotlin/tachiyomi/source/local/LocalSource.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt` (`TestBindings.sourceManager` y `localSource`)
- Create: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/LocalFixtures.kt`, `desktop-app/src/test/resources/archives/paginas.7z` (copia de la de la Task 1)
- Test: `core/common/src/test/kotlin/tachiyomi/core/common/util/system/ImageUtilTest.kt`, `desktop-app/src/test/kotlin/mihon/desktop/app/local/LocalSourceTest.kt`

**Interfaces:**
- Consumes:
  - de la Task 1: `archiveReader`, `epubReader` y `ArchiveReader`;
  - de la Task 2: `TestAppGraph.storageManager` y `AppDirs.storage`.
- Produces:
  - `tachiyomi.source.local.LocalSource` (Mihon): `ID = 0L`, `HELP_URL`, `getFormat(chapter: SChapter): Format`, y las extensiones `isLocal()`;
  - `ImageUtil.isImage(name, openStream)`, `ImageUtil.findImageType(...)`, `ImageUtil.ImageType`;
  - `TestAppGraph.localSource: LocalSource`;
  - `LocalFixtures.png(size: Int): ByteArray`, `zip(file, entries): File`, `epub(file, images: List<ByteArray>): File`, `cb7(file): File`, `localDir(graph): File`.

- [ ] **Step 1: Ayudas de test**

`cp core/archive/src/test/resources/archives/paginas.7z desktop-app/src/test/resources/archives/paginas.7z` (crea la carpeta).

`desktop-app/src/test/kotlin/mihon/desktop/app/testing/LocalFixtures.kt`:

```kotlin
package mihon.desktop.app.testing

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Pages and chapters for the local source's tests, in the test app's storage folder. */
object LocalFixtures {
    /** A real PNG; [size] tells pages apart. */
    fun png(size: Int = 4): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(size, size, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    fun zip(file: File, entries: Map<String, ByteArray>): File = file.also {
        ZipOutputStream(it.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /** An EPUB with one page per image, in order. */
    fun epub(file: File, images: List<ByteArray>): File {
        val pages = images.indices.associate { i ->
            "OEBPS/p$i.xhtml" to """<html><body><img src="img$i.png"/></body></html>""".toByteArray()
        }
        val opf = """
            <package><manifest>${images.indices.joinToString("") { i ->
            """<item id="p$i" href="p$i.xhtml" media-type="application/xhtml+xml"/>"""
        }}</manifest><spine>${images.indices.joinToString("") { i -> """<itemref idref="p$i"/>""" }}</spine></package>
        """.trimIndent()
        return zip(
            file,
            mapOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to
                    """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""
                        .toByteArray(),
                "OEBPS/content.opf" to opf.toByteArray(),
            ) + pages + images.withIndex().associate { (i, bytes) -> "OEBPS/img$i.png" to bytes },
        )
    }

    /** The 7z sample: José/01.png, 02.png and 10.png, and a ComicInfo.xml with chapter number 7.5. */
    fun cb7(file: File): File = file.also { target ->
        javaClass.getResourceAsStream("/archives/paginas.7z")!!.use { input -> target.outputStream().use(input::copyTo) }
    }

    /** The local/ folder of the test app's storage. */
    fun localDir(graph: TestAppGraph): File = File(graph.storageManager.getLocalSourceDirectory()!!.filePath!!)
}
```

En `TestAppGraph`:
- añade `val localSource: LocalSource`;
- cambia `TestBindings.sourceManager` por:

```kotlin
    @Provides
    @SingleIn(AppScope::class)
    fun sourceManager(local: LocalSource): SourceManager =
        FakeSourceManager(local, TestSources.fake, TestSources.http, TestSources.updatable)
```

  con `import tachiyomi.source.local.LocalSource`.

- [ ] **Step 2: Escribir los tests**

`core/common/src/test/kotlin/tachiyomi/core/common/util/system/ImageUtilTest.kt`:

```kotlin
package tachiyomi.core.common.util.system

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ImageUtilTest {
    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    @Test
    fun `a known extension is an image without opening it`() {
        ImageUtil.isImage("01.png") shouldBe true
        ImageUtil.isImage("nota.txt") shouldBe false
        ImageUtil.isImage(null) shouldBe false
    }

    @Test
    fun `an unknown or upper-case extension is told by its first bytes`() {
        ImageUtil.isImage("03.JPG") { bytes(0xFF, 0xD8, 0xFF, 0xE0).inputStream() } shouldBe true
        ImageUtil.isImage("03.JPG") { "hola".byteInputStream() } shouldBe false
    }

    @Test
    fun `the first bytes say which image type it is`() {
        ImageUtil.findImageType(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).inputStream()) shouldBe
            ImageUtil.ImageType.PNG
        ImageUtil.findImageType("GIF89a".byteInputStream()) shouldBe ImageUtil.ImageType.GIF
        ImageUtil.findImageType("RIFF0000WEBPVP8 ".byteInputStream()) shouldBe ImageUtil.ImageType.WEBP
        ImageUtil.findImageType((bytes(0, 0, 0, 0x1C) + "ftypavif".toByteArray()).inputStream()) shouldBe
            ImageUtil.ImageType.AVIF
        ImageUtil.findImageType(ByteArray(0).inputStream()) shouldBe null
    }
}
```

`desktop-app/src/test/kotlin/mihon/desktop/app/local/LocalSourceTest.kt`:

```kotlin
package mihon.desktop.app.local

import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.LocalFixtures
import mihon.desktop.app.testing.LocalFixtures.png
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import java.io.File

class LocalSourceTest {
    private val graph: TestAppGraph = startTestApp()
    private val source get() = graph.localSource

    private fun mangaDir(name: String): File = File(LocalFixtures.localDir(graph), name).apply { mkdirs() }

    private fun folderChapter(manga: File, name: String, pages: Int = 2): File =
        File(manga, name).apply { mkdirs() }.also { dir ->
            (1..pages).forEach { File(dir, "%02d.png".format(it)).writeBytes(png(it)) }
        }

    private suspend fun details(url: String): SManga =
        source.getMangaUpdate(SManga.create().apply { this.url = url; title = url }, emptyList(), true, false).manga

    @Test
    fun `lists the manga folders by name, without hidden folders or loose files, and searches by name`(): Unit =
        runBlocking {
            mangaDir("B José")
            mangaDir("a Pérez")
            mangaDir(".oculta")
            File(LocalFixtures.localDir(graph), "suelto.txt").writeText("x")

            val titles = source.getPopularManga(1).mangas.map { it.title }
            titles.filter { it in listOf("a Pérez", "B José") } shouldContainExactly listOf("a Pérez", "B José")
            titles shouldNotContain ".oculta"
            titles shouldNotContain "suelto.txt"

            source.getSearchManga(1, "pérez", source.getFilterList()).mangas.map { it.title } shouldContainExactly
                listOf("a Pérez")
        }

    @Test
    fun `details come from details json, which becomes a ComicInfo xml, or straight from ComicInfo xml`(): Unit =
        runBlocking {
            val json = mangaDir("Con JSON")
            File(json, "details.json").writeText(
                """{"title":"Título JSON","author":"Autora","genre":["Acción","Drama"],"status":1}""",
            )
            val fromJson = details("Con JSON")
            fromJson.title shouldBe "Título JSON"
            fromJson.author shouldBe "Autora"
            fromJson.genre shouldBe "Acción, Drama"
            File(json, "ComicInfo.xml").exists() shouldBe true

            val xml = mangaDir("Con XML")
            File(xml, "ComicInfo.xml").writeText(
                """<?xml version="1.0" encoding="utf-8"?>""" +
                    """<ComicInfo><Series>Serie XML</Series><Writer>Escritor</Writer><Summary>Resumen</Summary></ComicInfo>""",
            )
            val fromXml = details("Con XML")
            fromXml.title shouldBe "Serie XML"
            fromXml.author shouldBe "Escritor"
            fromXml.description shouldBe "Resumen"
        }

    @Test
    fun `cover jpg is the cover, and without one the first page of the first chapter becomes it`(): Unit =
        runBlocking {
            File(mangaDir("Con portada"), "cover.jpg").writeBytes(png(8))
            details("Con portada").thumbnail_url!! shouldEndWith "cover.jpg"

            val bare = mangaDir("Sin portada")
            folderChapter(bare, "Cap 1")
            val manga = SManga.create().apply { url = "Sin portada"; title = "Sin portada" }
            source.getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true)
            File(bare, "cover.jpg").readBytes().toList() shouldBe png(1).toList()
        }

    @Test
    fun `chapters come from folders, CBZ, CB7 and EPUB files, newest name first; a PDF is left out and a damaged CBZ still listed`(): Unit =
        runBlocking {
            val dir = mangaDir("Capítulos José")
            folderChapter(dir, "Cap 1")
            LocalFixtures.zip(File(dir, "Cap 2.cbz"), mapOf("01.png" to png(1)))
            LocalFixtures.cb7(File(dir, "Cap 3.cb7"))
            LocalFixtures.epub(File(dir, "Cap 4.epub"), listOf(png(1), png(2)))
            File(dir, "Cap 5.pdf").writeText("no")
            File(dir, "Cap 6.cbz").writeBytes(ByteArray(512) { (it * 7).toByte() })

            val manga = SManga.create().apply { url = "Capítulos José"; title = "Capítulos José" }
            val chapters = source.getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true).chapters

            chapters.map { it.name } shouldContainExactly listOf("Cap 6", "Cap 4", "Cap 3", "Cap 2", "Cap 1")
            chapters.single { it.name == "Cap 3" }.chapter_number shouldBe 7.5f // from its ComicInfo.xml, not its name
            chapters.single { it.name == "Cap 2" }.url shouldBe "Capítulos José/Cap 2.cbz"
        }
}
```

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :core:common:test --tests "tachiyomi.core.common.util.system.ImageUtilTest" :desktop-app:test --tests "mihon.desktop.app.local.LocalSourceTest"`
Expected: FAIL de compilación. No existen `ImageUtil` (está excluida de `core/common`), `TestAppGraph.localSource` ni el `LocalSource` real (desktop-app solo tiene el stub, sin `getMangaUpdate`).

- [ ] **Step 4: `ImageUtil` de escritorio**

`core/common/src/desktop/kotlin/tachiyomi/core/common/util/system/ImageUtil.kt`:

```kotlin
package tachiyomi.core.common.util.system

import java.io.InputStream

/**
 * Desktop copy of the parts of Mihon's ImageUtil that the local source and the page loaders use. Mihon tells image
 * types apart with Android's ImageDecoder; this reads the magic bytes. The rest arrives with what needs it.
 */
object ImageUtil {

    fun isImage(name: String?, openStream: (() -> InputStream)? = null): Boolean {
        if (name == null) return false

        val extension = name.substringAfterLast('.')
        return ImageType.entries.any { it.extension == extension } || openStream?.let { findImageType(it) } != null
    }

    fun findImageType(openStream: () -> InputStream): ImageType? {
        return openStream().use { findImageType(it) }
    }

    fun findImageType(stream: InputStream): ImageType? {
        val head = ByteArray(16)
        val read = stream.readNBytes(head, 0, head.size)
        fun at(offset: Int, vararg bytes: Int) =
            read >= offset + bytes.size && bytes.indices.all { head[offset + it] == bytes[it].toByte() }
        fun ascii(offset: Int, text: String) = at(offset, *text.map { it.code }.toIntArray())
        val brand = if (ascii(4, "ftyp") && read >= 12) String(head, 8, 4, Charsets.US_ASCII) else null

        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> ImageType.JPEG
            at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> ImageType.PNG
            ascii(0, "GIF8") -> ImageType.GIF
            ascii(0, "RIFF") && ascii(8, "WEBP") -> ImageType.WEBP
            brand == "avif" || brand == "avis" -> ImageType.AVIF
            brand in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1") -> ImageType.HEIF
            at(0, 0xFF, 0x0A) || at(0, 0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20, 0x0D, 0x0A, 0x87, 0x0A) ->
                ImageType.JXL
            at(0, 0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A, 0x87, 0x0A) -> ImageType.JP2
            else -> null
        }
    }

    fun getExtensionFromMimeType(mime: String?, openStream: () -> InputStream): String {
        val type = mime?.let { ImageType.entries.find { it.mime == mime } } ?: findImageType(openStream)
        return type?.extension ?: "jpg"
    }

    enum class ImageType(val mime: String, val extension: String) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),
        JXL("image/jxl", "jxl"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
        JP2("image/jp2", "jp2"),
        JPX("image/jpx", "jpx"),
    }
}
```

- [ ] **Step 5: `core-metadata` y `source-local` en JVM**

`core-metadata/build.gradle.kts` entero:

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.sourceApi)

    implementation(libs.bundles.serialization)
}
```

`source-local/build.gradle.kts` entero:

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.metro)
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

sourceSets {
    main {
        kotlin {
            srcDir("src/desktop/kotlin")
            // With a desktop copy of the same relative path in src/desktop (ComicInfo read without Android's parser).
            // Local val: a script-level one would drag the script object into the configuration cache.
            val androidOnly = setOf("tachiyomi/source/local/LocalSource.kt")
            exclude { it.relativePath.pathString in androidOnly && "/src/main/" in it.file.invariantSeparatorsPath }
        }
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.i18n)

    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.coreMetadata)
    implementation(projects.domain)

    implementation(libs.metro.runtime)

    implementation(libs.bundles.serialization)

    implementation(libs.injekt)
    implementation(libs.jsoup)

    implementation(libs.kotlinx.datetime)
}
```

En `settings.gradle.kts`, `include(":core-metadata")` e `include(":source-local")` tras `include(":core:archive")`.

`source-local/src/desktop/kotlin/tachiyomi/source/local/LocalSource.kt` es copia de `source-local/src/main/kotlin/tachiyomi/source/local/LocalSource.kt` con un solo cambio:
- quita los imports `nl.adaptivity.xmlutil.core.AndroidXmlReader` y `java.nio.charset.StandardCharsets`;
- `parseComicInfo` queda así:

```kotlin
    // desktop: xmlutil's AndroidXmlReader needs Android's XmlPullParser; the JVM decoder reads the same document
    private fun parseComicInfo(stream: InputStream): ComicInfo {
        return xml.decodeFromString(ComicInfo.serializer(), stream.use { it.readBytes().decodeToString() })
    }
```

- [ ] **Step 6: `LocalSource` en la app**

- `desktop-core/build.gradle.kts`: `api(projects.sourceLocal)` junto a los demás `api(...)`.
- `desktop-app/build.gradle.kts`: `implementation(projects.core.archive)` tras `implementation(projects.extensionLoader)`.
- `git rm desktop-app/src/stubs/kotlin/tachiyomi/source/local/LocalSource.kt`. El `LocalSource` real tiene el mismo `ID`, el mismo `HELP_URL` y las mismas extensiones `isLocal()`.
- `DesktopSourceManager`:

```kotlin
class DesktopSourceManager(
    extensionManager: DesktopExtensionManager,
    // desktop: the local source comes first, as in Mihon's AndroidSourceManager
    localSource: LocalSource,
    @AppCoroutineScope scope: CoroutineScope,
) : SourceManager {

    override val sources: StateFlow<List<Source>> = extensionManager.installed
        .map { list -> listOf<Source>(localSource) + list.filterIsInstance<Extension.Loaded>().flatMap { it.sources } }
        .stateIn(scope, SharingStarted.Eagerly, listOf(localSource))
```

  con `import tachiyomi.source.local.LocalSource`.

- [ ] **Step 7: Comprobar que pasan**

Run: `./gradlew :core:common:test --tests "tachiyomi.core.common.util.system.ImageUtilTest" :desktop-app:test --tests "mihon.desktop.app.local.*" --tests "mihon.desktop.app.browse.*"`
Expected: PASS.
- Si Metro se queja de dos bindings de `FolderProvider`, revisa la exclusión de la Task 2.
- Si se queja de un binding que falta para `LocalSourceFileSystem` o `LocalCoverManager`, comprueba que `source-local` aplica `libs.plugins.metro`.

Run: `bash scripts/check-no-android.sh`
Expected: `No direct Android imports in desktop-app`.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts core/common core-metadata source-local desktop-core desktop-app
git commit -m "feat(source-local): Mihon's local source on desktop, first in the source list

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Lectura de capítulos locales

**Files:**
- Copy (de `app/src/main/java/` a `desktop-app/src/main/kotlin/`): `eu/kanade/tachiyomi/ui/reader/loader/{DirectoryPageLoader,ArchivePageLoader,EpubPageLoader}.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/local/LocalReaderTest.kt`

**Interfaces:**
- Consumes:
  - de la Task 3: `LocalSource.getFormat`, `Format`, `LocalFixtures` y `TestAppGraph.localSource`;
  - de la Task 1: `archiveReader` y `epubReader`.
- Produces: `ChapterLoader` abre capítulos de `LocalSource`.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.local

import eu.kanade.domain.manga.model.toSManga
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.LocalFixtures
import mihon.desktop.app.testing.LocalFixtures.png
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import java.io.File
import kotlin.time.Duration.Companion.seconds

class LocalReaderTest {
    private val graph: TestAppGraph = startTestApp()

    /** A local manga folder built by [build], added to the database with its chapters, oldest first. */
    private suspend fun localManga(name: String, build: (File) -> Unit): Pair<Manga, List<Chapter>> {
        val dir = File(LocalFixtures.localDir(graph), name).apply { mkdirs() }
        build(dir)
        val manga = graph.networkToLocalManga(Manga.create().copy(url = name, title = name, source = LocalSource.ID))
        val update = graph.localSource.getMangaUpdate(manga.toSManga(), emptyList(), false, true)
        graph.syncChaptersWithSource.await(update.chapters, manga, graph.localSource)
        return manga to graph.getChaptersByMangaId.await(manga.id).sortedBy { it.sourceOrder }.reversed()
    }

    /** The page bytes of [chapter] as the reader loads them, or the reader's error. */
    private suspend fun open(manga: Manga, chapter: Chapter): Result<List<ByteArray>> {
        val viewModel = graph.readerViewModelFactory.create(manga.id, chapter.id)
        val state = withTimeout(15.seconds) { viewModel.state.first { it.viewerChapters != null || it.initError != null } }
        state.initError?.let { return Result.failure(it) }
        val pages = state.currentChapter.shouldNotBeNull().pages.shouldNotBeNull()
        return Result.success(pages.map { page -> page.stream!!().use { it.readBytes() } })
    }

    @Test
    fun `a folder, a CBZ and an EPUB chapter open with their pages in natural order, upper-case extensions included`(): Unit =
        runBlocking {
            val (manga, chapters) = localManga("Lectura José") { dir ->
                File(dir, "Cap 1").apply { mkdirs() }.also { cap ->
                    File(cap, "10.png").writeBytes(png(3))
                    File(cap, "2.png").writeBytes(png(2))
                    File(cap, "1.png").writeBytes(png(1))
                }
                LocalFixtures.zip(
                    File(dir, "Cap 2.cbz"),
                    mapOf("01.png" to png(1), "02.png" to png(2), "03.JPG" to png(3), "nota.txt" to "x".toByteArray()),
                )
                LocalFixtures.epub(File(dir, "Cap 3.epub"), listOf(png(1), png(2)))
            }
            val byName = chapters.associateBy { it.name }

            open(manga, byName.getValue("Cap 1")).getOrThrow().map { it.toList() } shouldBe
                listOf(png(1), png(2), png(3)).map { it.toList() }
            open(manga, byName.getValue("Cap 2")).getOrThrow().size shouldBe 3
            open(manga, byName.getValue("Cap 3")).getOrThrow().map { it.toList() } shouldBe
                listOf(png(1), png(2)).map { it.toList() }
        }

    @Test
    fun `a CB7 chapter opens with its pages`(): Unit = runBlocking {
        val (manga, chapters) = localManga("Siete José") { dir -> LocalFixtures.cb7(File(dir, "Cap 1.cb7")) }

        open(manga, chapters.single()).getOrThrow().size shouldBe 3
    }

    @Test
    fun `a damaged archive shows the reader's error instead of crashing`(): Unit = runBlocking {
        val (manga, chapters) = localManga("Roto José") { dir ->
            File(dir, "Cap 1.cbz").writeBytes(ByteArray(512) { (it * 7).toByte() })
        }

        open(manga, chapters.single()).isFailure shouldBe true
    }

    @Test
    fun `a manga folder deleted after it was added says the chapter wasn't found`(): Unit = runBlocking {
        val (manga, chapters) = localManga("Borrado José") { dir -> LocalFixtures.zip(File(dir, "Cap 1.cbz"), mapOf("01.png" to png())) }
        File(LocalFixtures.localDir(graph), "Borrado José").deleteRecursively()

        open(manga, chapters.single()).exceptionOrNull()?.message shouldBe str(MR.strings.chapter_not_found)
    }
}
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.local.LocalReaderTest"`
Expected: FAIL. `ChapterLoader` solo conoce `HttpSource`, así que todos los capítulos dan `loader_not_implemented_error`. El test del archivo dañado ya pasa, porque solo comprueba que hay error. El resto falla.

- [ ] **Step 3: Copiar los cargadores y la rama local**

- `DirectoryPageLoader.kt`, `ArchivePageLoader.kt` y `EpubPageLoader.kt`: copias sin cambios.
- `ChapterLoader.kt`:
  - el comentario de cabecera `desktop: only online sources for now; downloaded and local chapters come with subproject 4.` pasa a `desktop: online and local sources; downloaded chapters come with subproject 4b.`;
  - `getPageLoader` queda así:

```kotlin
    private fun getPageLoader(chapter: ReaderChapter): PageLoader = when (source) {
        is LocalSource -> when (val format = source.getFormat(chapter.chapter.toSChapter())) {
            is Format.Directory -> DirectoryPageLoader(format.file)
            is Format.Archive -> ArchivePageLoader(format.file.archiveReader(context))
            is Format.Epub -> EpubPageLoader(format.file.epubReader(context))
        }
        is HttpSource -> HttpPageLoader(chapter, source, chapterCache, appScope, pagesAhead)
        is StubSource -> error(context.stringResource(MR.strings.source_not_installed, source.toString()))
        else -> error(context.stringResource(MR.strings.loader_not_implemented_error))
    }
```

  con los imports `eu.kanade.domain.chapter.model.toSChapter`, `mihon.core.archive.archiveReader`, `mihon.core.archive.epubReader`, `tachiyomi.source.local.LocalSource` y `tachiyomi.source.local.io.Format`.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.local.*" --tests "eu.kanade.tachiyomi.ui.reader.*" --tests "mihon.desktop.app.reader.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): the reader opens local folders, archives and EPUBs

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Portadas locales

**Files:**
- Modify: `compat-shims/src/main/kotlin/com/hippo/unifile/UniFile.kt` (`uri`)
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/coil/MangaCoverFetcher.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/util/MangaExtensions.kt`, `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/manga/MangaCoverViewModel.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt` (`localCoverManager`)
- Test: `compat-shims/src/test/kotlin/com/hippo/unifile/UniFileTest.kt`, `desktop-app/src/test/kotlin/mihon/desktop/app/image/DesktopImageLoaderTest.kt`, `desktop-app/src/test/kotlin/mihon/desktop/app/local/LocalCoverTest.kt`

**Interfaces:**
- Consumes: de la Task 3, `LocalSource`, `LocalFixtures` y `TestAppGraph.localSource`.
- Produces:
  - `UniFile.uri` es `file:` con la ruta codificada (`File.toURI()`);
  - `MangaCoverFetcher` carga portadas `file:`;
  - `suspend fun Manga.editCover(coverManager: LocalCoverManager, stream: InputStream, updateManga: UpdateManga, coverCache: CoverCache)` (Mihon);
  - `TestAppGraph.localCoverManager: LocalCoverManager`.

- [ ] **Step 1: Escribir los tests**

En `UniFileTest`:

```kotlin
    @Test
    fun `its uri string parses back to the same file, spaces and accents included`() {
        val dir = UniFile.fromFile(createTempDirectory("Tomo #1 & 50% José").toFile())!!

        UniFile.fromUri(null, android.net.Uri.parse(dir.uri.toString()))!!.filePath shouldBe dir.filePath
    }
```

En `DesktopImageLoaderTest`:

```kotlin
    @Test
    fun `a local cover file with spaces, signs and accents in its path loads`(): Unit = runBlocking {
        val folder = kotlin.io.path.createTempDirectory("Tomo #1 & 50% José").toFile()
        val cover = java.io.File(folder, "cover.jpg").apply { writeBytes(png()) }

        val result = DesktopImageLoader.create(graph).execute(
            ImageRequest.Builder(PlatformContext.INSTANCE).data(
                MangaCover(
                    mangaId = 2,
                    sourceId = 0,
                    isMangaFavorite = true,
                    url = cover.toURI().toString(),
                    lastModified = null,
                ),
            ).build(),
        )

        result.shouldBeInstanceOf<SuccessResult>()
    }
```

`desktop-app/src/test/kotlin/mihon/desktop/app/local/LocalCoverTest.kt`:

```kotlin
package mihon.desktop.app.local

import eu.kanade.tachiyomi.util.editCover
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.LocalFixtures
import mihon.desktop.app.testing.LocalFixtures.png
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.LocalSource
import java.io.File

class LocalCoverTest {
    private val graph = startTestApp()

    @Test
    fun `editing a local manga's cover writes cover jpg in its folder, not in the cover cache`(): Unit = runBlocking {
        val folder = File(LocalFixtures.localDir(graph), "Portada José").apply { mkdirs() }
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "Portada José", title = "Portada José", source = LocalSource.ID),
        )

        manga.editCover(graph.localCoverManager, png(9).inputStream(), graph.updateManga, graph.coverCache)

        File(folder, "cover.jpg").readBytes().toList() shouldBe png(9).toList()
        graph.coverCache.getCustomCoverFile(manga.id).exists() shouldBe false
    }
}
```

Añade a `TestAppGraph` `val localCoverManager: LocalCoverManager` (import `tachiyomi.source.local.image.LocalCoverManager`).

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :compat-shims:test --tests "com.hippo.unifile.UniFileTest" :desktop-app:test --tests "mihon.desktop.app.image.DesktopImageLoaderTest" --tests "mihon.desktop.app.local.LocalCoverTest"`
Expected: FAIL.
- `LocalCoverTest` no compila: `editCover` no existe en escritorio.
- `DesktopImageLoaderTest`: `getResourceType` no reconoce `file:/C:/…` (solo `file://`) y no hay resultado.
- `UniFileTest`: falla si `Uri.fromFile(...).toString()` no vuelve a la misma ruta. Si ya pasa, apúntalo en el ledger: el test se queda como fijación.

- [ ] **Step 3: Implementar**

`UniFile.kt`:

```kotlin
    // desktop: java's file: uri (percent-encoded, forward slashes) parses back to the same path; Uri.fromFile's
    // string keeps Windows backslashes and doesn't
    val uri: Uri get() = Uri.parse(file.toURI().toString())
```

`MangaCoverFetcher.kt`:
- en `getResourceType`: `cover.startsWith("/") || cover.startsWith("file:") -> Type.File, // desktop: java's file:/C:/ uris too`;
- en el `when` de `fetch`:

```kotlin
            // desktop: a file: uri may be percent-encoded (spaces, accents, #); java.net.URI decodes it
            Type.File -> fileLoader(if (url.startsWith("file:")) File(URI(url)) else File(url))
```

  con `import java.net.URI`.

`MangaExtensions.kt`:
- quita el comentario `// desktop: editCover needs LocalCoverManager …`;
- añade el `editCover` de Mihon:

```kotlin
suspend fun Manga.editCover(
    coverManager: LocalCoverManager,
    stream: InputStream,
    updateManga: UpdateManga,
    coverCache: CoverCache,
) {
    if (isLocal()) {
        coverManager.update(toSManga(), stream)
        updateManga.awaitUpdateCoverLastModified(id)
    } else if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateManga.awaitUpdateCoverLastModified(id)
    }
}
```

  con los imports `eu.kanade.domain.manga.interactor.UpdateManga`, `eu.kanade.domain.manga.model.toSManga`, `tachiyomi.source.local.image.LocalCoverManager` y `java.io.InputStream`.

`MangaCoverViewModel.kt`:
- añade al constructor `private val localCoverManager: LocalCoverManager`;
- `editCover(file)` usa `file.inputStream().use { manga.editCover(localCoverManager, it, updateManga, coverCache) }` en lugar de `coverCache.setCustomCoverToCache` y `awaitUpdateCoverLastModified`;
- el comentario de cabecera añade "a local manga's cover goes to its folder (spec 4a §3 D)".

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :compat-shims:test :desktop-app:test --tests "mihon.desktop.app.image.*" --tests "mihon.desktop.app.local.*" --tests "mihon.desktop.app.browse.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add compat-shims desktop-app
git commit -m "feat(desktop-app): local covers load from any folder name, and editing one writes cover.jpg

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Ajustes › Datos y almacenamiento, y "Local" en Explorar

**Files:**
- Create: `desktop-app/src/main/kotlin/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsTab.kt`, `desktop-app/src/main/kotlin/mihon/desktop/app/platform/FileDialogs.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/presentation/browse/BrowseSourceScreen.kt:87`, `desktop-app/src/main/kotlin/eu/kanade/presentation/browse/components/BrowseSourceToolbar.kt:43` (solo comentarios)
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/local/LocalScreensTest.kt`

**Interfaces:**
- Consumes:
  - de la Task 2: `AppGraph.storagePreferences` y `AppDirs.storage`;
  - de la Task 3: `LocalSource` en el gestor de fuentes y `LocalFixtures.localDir`.
- Produces: `SettingsDataScreen` (objeto `SearchableSettings`) y `FileDialogs.chooseDirectory(title: String): File?`.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.local

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.LocalFixtures
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import java.io.File

@OptIn(ExperimentalTestApi::class)
class LocalScreensTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `Browse lists the local source, which lists the manga folders`() = runComposeUiTest {
        val graph = startTestApp()
        File(LocalFixtures.localDir(graph), "Explorar José").mkdirs()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitForText(str(MR.strings.browse))
        onAllNodesWithText(str(MR.strings.browse)).onFirst().performClick()
        waitForText(str(MR.strings.local_source))
        onAllNodesWithText(str(MR.strings.local_source)).onFirst().performClick()
        waitForText("Explorar José")
    }

    @Test
    fun `Settings › Data and storage shows the storage folder`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitForText(str(MR.strings.label_settings))
        onAllNodesWithText(str(MR.strings.label_settings)).onFirst().performClick()
        waitForText(str(MR.strings.label_data_storage))
        onAllNodesWithText(str(MR.strings.label_data_storage)).onFirst().performClick()
        waitForText(graph.appDirs.storage.toString())
    }
}
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.local.LocalScreensTest"`
Expected: el primer test PASA, porque la Task 3 ya puso "Local" en el gestor de fuentes; queda como fijación. El segundo FALLA: Ajustes no tiene `label_data_storage` y `waitUntil` agota el tiempo.

- [ ] **Step 3: Implementar**

`FileDialogs.kt` (imports `javax.swing.JFileChooser`):

```kotlin
    /** AWT's FileDialog can't pick folders on Windows, so this one is Swing's chooser in folders-only mode. */
    fun chooseDirectory(title: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
```

`SettingsDataScreen.kt`:

```kotlin
package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import mihon.app.di.appGraph
import mihon.desktop.app.platform.FileDialogs
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.io.File
import java.net.URI

/** Mihon's data and storage settings with only the storage location; backups, cache and export come with subproject 5. */
object SettingsDataScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_data_storage

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val storagePreferences = remember { context.appGraph.storagePreferences }
        val storageDir by storagePreferences.baseStorageDirectory.collectAsState()
        val chooserTitle = stringResource(MR.strings.pref_storage_location)
        // desktop: always the path, also for the default folder, which works without picking one (spec 4a §3 C)
        val path = remember(storageDir) { runCatching { File(URI(storageDir)).path }.getOrNull() }

        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_storage_location),
                subtitle = path ?: stringResource(MR.strings.invalid_location, storageDir),
                // desktop: a Windows folder chooser instead of Android's document tree picker
                onClick = {
                    FileDialogs.chooseDirectory(chooserTitle)?.let {
                        storagePreferences.baseStorageDirectory.set(it.toURI().toString())
                    }
                },
            ),
            Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.pref_storage_location_info)),
        )
    }
}
```

`SettingsTab.kt`: añade a `entries`, tras la entrada de `SettingsReaderScreen`:

```kotlin
            Triple(MR.strings.label_data_storage, MaterialSymbols.Rounded.Storage, SettingsDataScreen),
```

con los imports `eu.kanade.presentation.more.settings.screen.SettingsDataScreen` y `mihon.icons.materialsymbols.rounded.Storage`.

En `BrowseSourceScreen.kt:87` y en `BrowseSourceToolbar.kt:43` se borran los comentarios `desktop: LocalSource is only a stub until subproject 4`. El código ya usa `LocalSource.ID`, igual que Mihon.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.local.*" --tests "mihon.desktop.app.settings.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): Settings › Data and storage with the storage folder and its chooser

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Verificación completa y prueba manual del 4a

**Files:**
- Modify: ninguno, salvo los arreglos que salgan

- [ ] **Step 1: Suite completa y checks**

Run: `./gradlew test spotlessCheck`
Expected: BUILD SUCCESSFUL. Unos 390 tests: los 365 de antes y unos 25 nuevos.
- Si `spotlessCheck` falla, ejecuta `./gradlew spotlessApply`, revisa el diff y haz commit aparte.

Run: `bash scripts/check-no-android.sh` y `bash scripts/check-licenses.sh`
Expected: los dos OK.

- [ ] **Step 2: Ninguna referencia pendiente a la lectura local**

Run: `grep -rn "subproject 4" desktop-app/src desktop-core/src --include=*.kt`
Expected: solo notas de descargas ("4b" o "downloads"). Cambia a "subproject 4b" las que traten de descargas y aún digan "subproject 4". No debe quedar ninguna sobre la fuente local, `LocalCoverManager` ni las portadas locales.

- [ ] **Step 3: Arranque real**

Run: `./gradlew :desktop-app:run` con `MANGAREADER_HOME` apuntando a una carpeta temporal.
Expected:
- la app arranca y Explorar › Fuentes muestra "Fuente local";
- en el log no hay errores de 7-Zip ni de Metro.

- [ ] **Step 4: Commit de los arreglos (si los hay)**

```bash
git add -A desktop-app desktop-core
git commit -m "chore: point the remaining download notes at subproject 4b

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 5: Prueba manual (spec §5), al usuario**

Pasa al usuario esta lista para ejecutar con `./gradlew :desktop-app:run`:
1. Copiar una carpeta `local/` de Android a `Documentos\MangaReader\local`. En Explorar › Local se ven los mangas, con sus portadas y detalles.
2. Leer un CBR (RAR5), un CB7, un EPUB y una carpeta.
3. Añadir un manga local a la biblioteca, leer un capítulo hasta la mitad, cerrar la app y volver: el capítulo sigue en esa página.
4. Ajustes › Datos y almacenamiento › Ubicación: elegir otra carpeta. Se crean `local`, `downloads` y `autobackup` dentro.
5. En la ficha de un manga local, editar la portada: aparece `cover.jpg` en su carpeta.
