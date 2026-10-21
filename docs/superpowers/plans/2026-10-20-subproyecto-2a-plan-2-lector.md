# Subproyecto 2a, plan 2 de 2: lector, historial y ajustes del lector. Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Leer capítulos desde la red con un lector de escritorio completo: izquierda a derecha, derecha a izquierda, vertical, webtoon y doble página, con zoom, teclado, pantalla completa, progreso e historial. Añadir la pestaña Historial y los ajustes del lector.

**Architecture:** La carga de capítulos de Mihon se porta casi sin cambios: modelos, `PageLoader`, `HttpPageLoader`, `ChapterCache` y `ChapterLoader`. El `ReaderViewModel` se reescribe como un subconjunto del de Mihon, con el mismo nombre y los mismos métodos, pero sin descargas, seguimiento, orientación ni filtros de imagen. El visor (`mihon.desktop.app.reader`) es nuevo, en Compose:
- `HorizontalPager`/`VerticalPager` para los modos paginados y `LazyColumn` para webtoon.
- Decodificación con Skia, en tiles para las imágenes muy altas.
- Una caché de páginas decodificadas acotada.
- Zoom con `net.engawapg.lib:zoomable`.

La lógica que se puede probar sin UI es pura: teclas, zonas de clic, emparejamiento de doble página y tiles.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.12.2, material3 CMP 1.12.0-alpha03, Voyager 2.2.21-1.10.3, Metro 1.4.5, Skia (skiko, viene con CMP), `net.engawapg.lib:zoomable` 2.16.0, DiskLruCache 2.0.2, OkHttp MockWebServer en los tests.

**Spec:** `docs/superpowers/specs/2026-10-19-subproyecto-2a-ui-lector-design.md`, sección 4 (lector) y las partes de las secciones 2, 3, 5 y 6 que el plan 1 dejó pendientes: historial en el rail, ajustes del lector y tests del lector. Base: `master` en `3cddd7a7f`, con el plan 1 integrado.

## Global Constraints

- Versiones fijas:

  | Componente | Versión |
  |---|---|
  | Kotlin | `2.4.21` |
  | CMP | `1.12.2` |
  | material3 de CMP | `1.12.0-alpha03` |
  | Voyager | `2.2.21-1.10.3` |
  | Metro | `1.4.5` |
  | zoomable | `2.16.0` (publicado contra CMP 1.12.1 y Kotlin 2.4.20) |
  | DiskLruCache | `2.0.2` (ya está en el catálogo) |

  OkHttp, RxJava, Jsoup y kotlinx-serialization no cambian.
- Los archivos portados de Mihon se copian de `app/src/main/java/<ruta>` a `desktop-app/src/main/kotlin/<ruta>`, con **el mismo paquete**. Cada cambio lleva `// desktop: <motivo>` en la línea cambiada o justo encima.
- Lo nuevo de escritorio va en `mihon.desktop.app.*`.
- Nada de `android.*`/`androidx.*` fuera de lo que permite `scripts/check-no-android.sh`.
- Textos nuevos en `i18n/.../base/strings.xml` y `.../es/strings.xml`, en el bloque `<!-- Desktop -->`.
- `SpanishStringsTest` exige que toda cadena que use `desktop-app` exista en español. Si un archivo portado usa una cadena de Mihon sin traducir, se traduce en el bloque `<!-- Desktop: Mihon strings with no Spanish translation upstream yet ... -->` de `es/strings.xml`.
- Nunca `git push` a `upstream`. Commits con el trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- Los tests `= runBlocking { }` declaran `: Unit`.
- `waitUntil` se llama con `timeoutMillis =` con nombre: el primer parámetro posicional es la descripción.
- Archivos con `\\` se escriben con la herramienta Write, nunca con heredoc de Bash.
- Antes de cada commit, `./gradlew :desktop-app:spotlessApply`.

## Desviaciones del spec (aprobar antes de ejecutar)

1. **`ReaderViewModel` reescrito, no copiado.** El de Mihon tiene 1.149 líneas: unas 300 son la lógica que el spec pide (carga, progreso, historial, capítulos adyacentes y marcadores) y el resto, descargas, seguimiento, orientación, filtros de color, "usar como portada", compartir y el `SavedStateHandle` de Android. El de escritorio conserva el nombre, los métodos y los textos de Mihon para esa lógica.
2. **Progreso agrupado cada 500 ms**, como dice el spec. Mihon usa 2 s.
3. **Precarga:** se cargan las 4 páginas siguientes a la que se lee (ajustable de 1 a 10). No se precarga el capítulo siguiente en segundo plano. Mihon carga el capítulo entero en wifi; el spec pide 4 páginas configurables.
4. **Doble página:** usa la preferencia `dualPageView` de Mihon (Nunca, Siempre, Ventana ancha) en los modos izquierda a derecha y derecha a izquierda. "Desplazar 1" es un botón del panel rápido que vale para la sesión del lector.
5. **Zoom en los modos paginados.** En webtoon, el ancho se ajusta con el margen lateral, como en Mihon. Hacer zoom sobre una lista que se desplaza choca con el desplazamiento.
6. **Escala de imagen:** cuatro de las opciones de Mihon: ajustar a la pantalla, al ancho, al alto y tamaño original. Son los mismos valores de `imageScaleType`; las otras opciones se leen como "ajustar a la pantalla".
7. **Modo "vertical continuo" de Mihon** (webtoon con separación): se conserva, porque existe en `viewer_flags`. Es el visor webtoon con 16 dp entre páginas.
8. **Historial:** la pestaña de Mihon sin los diálogos de categorías ni de migración (subproyecto 3).
9. **Clic derecho sobre una página:** "Guardar" (diálogo de Windows) y "Copiar al portapapeles". Sin "Compartir" ni "Usar como portada".

## Review Focus

1. **Cerrar la ventana con el lector abierto justo después de pasar página:** la última página leída y el historial se guardan igualmente. Tests en las Tasks 2 (`flush`) y 5 (`ExitHooks`).
2. **Cambiar el modo de lectura a mitad de capítulo:** el lector sigue en la misma página. Test en la Task 7.
3. **Una página cuya imagen no se puede decodificar** (bytes corruptos o formato no soportado): esa página muestra el error con "Reintentar" y las demás funcionan. Tests en las Tasks 4 y 5.
4. **Pasar muchas páginas seguidas:** solo se guardan decodificadas las páginas de −2 a +4 de la actual. Test en la Task 4.
5. **Cerrar la app con el lector en pantalla completa:** la ventana abre con su tamaño normal la próxima vez. Test en la Task 5.

## Mapa de archivos

| Ruta (`desktop-app/src/main/kotlin/` salvo indicación) | Responsabilidad |
|---|---|
| `eu/kanade/tachiyomi/ui/reader/model/*` | Modelos de Mihon: páginas, capítulos y transiciones |
| `eu/kanade/tachiyomi/ui/reader/loader/*` | `PageLoader`, `HttpPageLoader` y `ChapterLoader` |
| `eu/kanade/tachiyomi/data/cache/ChapterCache.kt` | Caché de disco de listas de páginas e imágenes |
| `eu/kanade/tachiyomi/ui/reader/setting/*` | `ReaderPreferences` (sustituye al stub), `ReadingMode` |
| `eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt` | Estado del lector, progreso e historial |
| `mihon/desktop/app/reader/ReaderInput.kt`, `DualPage.kt`, `PageTiles.kt` | Lógica pura |
| `mihon/desktop/app/reader/image/*` | Decodificación con Skia y caché de páginas decodificadas |
| `mihon/desktop/app/reader/ReaderScreen.kt`, `viewer/*`, `ReaderControls.kt` | Pantalla, visores y controles |
| `mihon/desktop/app/platform/WindowControls.kt`, `ExitHooks.kt` | Pantalla completa y trabajo pendiente al cerrar |
| `eu/kanade/tachiyomi/ui/history/*`, `eu/kanade/presentation/history/*` | Historial |
| `mihon/desktop/app/settings/SettingsReaderScreen.kt` | Ajustes del lector |
| `desktop-app/src/test/kotlin/mihon/desktop/app/testing/FakeHttpSource.kt`, `TestLibrary.kt` | Fuente HTTP falsa sobre MockWebServer y datos de prueba |

---

### Task 1: Carga de capítulos: modelos, caché y `HttpPageLoader`

**Files:**
- Modify: `desktop-app/build.gradle.kts`
- Create (copias sin cambios): `eu/kanade/tachiyomi/ui/reader/model/ReaderPage.kt`, `ReaderChapter.kt`, `ChapterTransition.kt`, `ViewerChapters.kt`, `InsertPage.kt`, `DownloadStream.kt`; `eu/kanade/tachiyomi/util/storage/OkioExtensions.kt`; `eu/kanade/tachiyomi/util/chapter/ChapterRemoveDuplicates.kt`
- Create (copias con cambios): `eu/kanade/tachiyomi/ui/reader/loader/PageLoader.kt`, `HttpPageLoader.kt`; `eu/kanade/tachiyomi/data/cache/ChapterCache.kt`
- Create (reescrito): `eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt` (accesor `chapterCache`)
- Create: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/FakeHttpSource.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/FakeSource.kt` (`TestSources.http`)
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt` (`TestBindings` añade `TestSources.http`)
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/DesktopAppGraphTest.kt` (la lista de fuentes incluye la HTTP)
- Test: `desktop-app/src/test/kotlin/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoaderTest.kt`

**Interfaces:**
- Consumes: `HttpSource`, `Page` (`source-api`); `AppDirs`; `DiskUtil` (`core:common`).
- Produces:
  - Los modelos de Mihon `ReaderPage`, `ReaderChapter` (con `State.Wait/Loading/Error/Loaded`), `ChapterTransition.Prev/Next`, `ViewerChapters` e `InsertPage`.
  - `abstract class PageLoader { suspend fun getPages(); suspend fun loadPage(page); fun retryPage(page); fun recycle() }`.
  - `internal class HttpPageLoader(chapter: ReaderChapter, source: HttpSource, chapterCache: ChapterCache, appScope: CoroutineScope, pagesAhead: Int)`.
  - `class ChapterLoader(context: Context, chapterCache: ChapterCache, appScope: CoroutineScope, pagesAhead: Int, manga: Manga, source: Source) { suspend fun loadChapter(chapter: ReaderChapter) }`.
  - `ChapterCache(appDirs: AppDirs, json: Json)`, con la API de Mihon: `getPageListFromCache`, `putPageListToCache`, `isImageInCache`, `getImageFile`, `putImageToCache`, `clear()` y `readableSize`.
  - `AppGraph.chapterCache`.
  - En los tests:
    - `class FakeHttpSource(server: MockWebServer) : HttpSource`, con `failPageList: Exception?` y `images: FakeImages`, donde `images.failing` e `images.corrupt` son conjuntos de rutas.
    - `TestSources.http`.

- [ ] **Step 1: Fuente HTTP falsa para los tests**

`desktop-app/src/test/kotlin/mihon/desktop/app/testing/FakeHttpSource.kt`:

```kotlin
package mihon.desktop.app.testing

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/** Serves a 600×900 PNG for every `.png` path, except the [failing] (HTTP 500) and [corrupt] (not an image) ones. */
class FakeImages : Dispatcher() {
    val failing: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val corrupt: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val png: ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty()
        return when {
            path in failing -> MockResponse().setResponseCode(500)
            path in corrupt -> MockResponse().setHeader("Content-Type", "image/png").setBody("no es una imagen")
            path.endsWith(".png") -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png))
            else -> MockResponse().setResponseCode(404)
        }
    }
}

/**
 * An online source backed by [server]: every manga has three chapters (`<manga url>/c1`..`c3`), each with
 * [pageCount] pages at `<chapter url>/<index>.png`. [failPageList] makes the next page list throw.
 */
class FakeHttpSource(private val server: MockWebServer, val images: FakeImages) : HttpSource() {
    override val name = "Fake HTTP"
    override val lang = "es"
    override val supportsLatest = false
    override val baseUrl: String get() = server.url("/").toString().removeSuffix("/")

    @Volatile
    var pageCount = 5

    @Volatile
    var failPageList: Exception? = null

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        MangasPage(emptyList(), false)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga,
        if (!fetchChapters) {
            chapters
        } else {
            (3 downTo 1).map { n ->
                SChapter.create().apply {
                    url = "${manga.url}/c$n"
                    name = "Capítulo $n"
                    chapter_number = n.toFloat()
                    date_upload = 1_700_000_000_000L + n
                }
            }
        },
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        failPageList?.let {
            failPageList = null
            throw it
        }
        return (0 until pageCount).map { Page(it, imageUrl = "$baseUrl${chapter.url}/$it.png") }
    }
}
```

En `FakeSource.kt`, `TestSources` pasa a ser:

```kotlin
object TestSources {
    val fake = FakeSource()

    val images = FakeImages()
    private val server = MockWebServer().apply {
        dispatcher = images
        start()
    }

    /** One per test JVM (the test task forks per class), like [fake]. */
    val http = FakeHttpSource(server, images)
}
```

(import `okhttp3.mockwebserver.MockWebServer`).

En `TestAppGraph.kt`, `TestBindings.sourceManager()` pasa a ser `FakeSourceManager(TestSources.fake, TestSources.http)`.

En `DesktopAppGraphTest`, la primera aserción pasa a ser `graph.sourceManager.getAll() shouldBe listOf(TestSources.fake, TestSources.http)`.

- [ ] **Step 2: Escribir el test del cargador (falla: no existe)**

`desktop-app/src/test/kotlin/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoaderTest.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.chapter.model.Chapter
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpPageLoaderTest {
    private lateinit var graph: TestAppGraph
    private val scope = CoroutineScope(SupervisorJob())

    @BeforeAll
    fun start() {
        graph = startTestApp()
    }

    private suspend fun loaded(url: String, pages: Int): Pair<HttpPageLoader, List<ReaderPage>> {
        TestSources.http.pageCount = pages
        val chapter = ReaderChapter(Chapter.create().copy(id = url.hashCode().toLong(), url = url, name = url))
        val loader = HttpPageLoader(chapter, TestSources.http, graph.chapterCache, scope, pagesAhead = 4)
        val list = loader.getPages().onEach { it.chapter = chapter }
        chapter.state = ReaderChapter.State.Loaded(list)
        return loader to list
    }

    private suspend fun ReaderPage.awaitStatus(match: (Page.State) -> Boolean) =
        withTimeout(10.seconds) { statusFlow.first(match) }

    @Test
    fun `a page is downloaded into the chapter cache with the four pages after it`(): Unit = runBlocking {
        val (loader, pages) = loaded("/m/loader/c1", pages = 10)
        pages.size shouldBe 10

        loader.loadPage(pages[0])
        (0..4).forEach { pages[it].awaitStatus { s -> s == Page.State.Ready } }

        ImageIO.read(pages[0].stream.shouldNotBeNull().invoke()).width shouldBe 600
        // Past the window (index - 1 .. index + 4), nothing is downloaded
        (5..9).forEach { pages[it].status shouldBe Page.State.Queue }
    }

    @Test
    fun `a page that failed can be retried`(): Unit = runBlocking {
        val (loader, pages) = loaded("/m/loader/c2", pages = 3)
        TestSources.images.failing += "/m/loader/c2/0.png"

        loader.loadPage(pages[0])
        pages[0].awaitStatus { it is Page.State.Error }

        TestSources.images.failing -= "/m/loader/c2/0.png"
        loader.retryPage(pages[0])
        pages[0].awaitStatus { it == Page.State.Ready }
    }
}
```

- [ ] **Step 3: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoaderTest"`
Expected: FAIL de compilación: `Unresolved reference 'HttpPageLoader'` y `'chapterCache'`.

- [ ] **Step 4: Copiar los modelos y las utilidades**

Copiar sin cambios desde `app/src/main/java/`:
- `eu/kanade/tachiyomi/ui/reader/model/ReaderPage.kt`, `ReaderChapter.kt`, `ChapterTransition.kt`, `ViewerChapters.kt`, `InsertPage.kt` y `DownloadStream.kt`
- `eu/kanade/tachiyomi/util/storage/OkioExtensions.kt`
- `eu/kanade/tachiyomi/util/chapter/ChapterRemoveDuplicates.kt`

En `desktop-app/build.gradle.kts`, añadir a `dependencies`: `implementation(libs.diskLruCache)`.

- [ ] **Step 5: `PageLoader`, `HttpPageLoader` y `ChapterCache` con cambios**

`loader/PageLoader.kt`: copiar y quitar `import androidx.annotation.CallSuper` y la anotación `@CallSuper` de `recycle()`, poniendo encima `// desktop: no androidx.annotation (Android lint annotation)`.

`loader/HttpPageLoader.kt`: copiar con estos cambios:
- El parámetro `private val canLoadAhead: () -> Boolean,` pasa a ser `private val pagesAhead: Int, // desktop: N pages ahead, never the whole chapter (spec 2a §4)`.
- La línea `val window = if (canLoadAhead()) items.indices else (index - 1)..(index + LIMITED_PAGES_AHEAD)` pasa a ser `val window = (index - 1)..(index + pagesAhead)`.
- Borrar `private const val LIMITED_PAGES_AHEAD = 4`.

`data/cache/ChapterCache.kt`: copiar con estos cambios:
- El constructor pasa a ser `(private val appDirs: AppDirs, private val json: Json)`. Borrar `import android.content.Context` y añadir `import mihon.desktop.core.AppDirs`.
- `File(context.cacheDir, "chapter_disk_cache")` pasa a ser `appDirs.cache.resolve("chapters").toFile() // desktop: %LOCALAPPDATA%\<App>\cache\chapters (spec 2a §4)`.
- `readableSize` pasa a ser:

```kotlin
    // desktop: Android's Formatter.formatFileSize needs a Context
    val readableSize: String
        get() = "%.1f MB".format(realSize / 1_000_000.0)
```

  Borrar `import android.text.format.Formatter`.

- [ ] **Step 6: `ChapterLoader` de escritorio**

`loader/ChapterLoader.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR

/**
 * Loader used to retrieve the [PageLoader] for a given chapter.
 *
 * desktop: only online sources for now; downloaded and local chapters come with subproject 4.
 */
class ChapterLoader(
    private val context: Context,
    private val chapterCache: ChapterCache,
    private val appScope: CoroutineScope,
    private val pagesAhead: Int,
    private val manga: Manga,
    private val source: Source,
) {

    /** Assigns the chapter's page loader and loads its pages. Returns immediately if it's already loaded. */
    suspend fun loadChapter(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null) return

        chapter.state = ReaderChapter.State.Loading
        withContext(Dispatchers.IO) {
            logcat { "Loading pages for ${chapter.chapter.name} of ${manga.title}" }
            try {
                val loader = getPageLoader(chapter)
                chapter.pageLoader = loader

                val pages = loader.getPages().onEach { it.chapter = chapter }
                if (pages.isEmpty()) throw Exception(context.stringResource(MR.strings.page_list_empty_error))

                // If the chapter is partially read, start at the last page the user read
                if (!chapter.chapter.read) chapter.requestedPage = chapter.chapter.lastPageRead

                chapter.state = ReaderChapter.State.Loaded(pages)
            } catch (e: Throwable) {
                chapter.state = ReaderChapter.State.Error(e)
                throw e
            }
        }
    }

    private fun getPageLoader(chapter: ReaderChapter): PageLoader = when (source) {
        is HttpSource -> HttpPageLoader(chapter, source, chapterCache, appScope, pagesAhead)
        is StubSource -> error(context.stringResource(MR.strings.source_not_installed, source.toString()))
        else -> error(context.stringResource(MR.strings.loader_not_implemented_error))
    }
}
```

- [ ] **Step 7: Accesor del grafo**

En `mihon/app/di/AppGraph.kt`, añadir `val chapterCache: ChapterCache` (import `eu.kanade.tachiyomi.data.cache.ChapterCache`).

- [ ] **Step 8: Ejecutar el test y ver que pasa**

Run: `./gradlew :desktop-app:test --tests "eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoaderTest" --tests "mihon.desktop.app.DesktopAppGraphTest"`
Expected: PASS (2 + 3 tests).

- [ ] **Step 9: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add desktop-app
git commit -m "feat(desktop-app): Mihon's chapter loading with a disk cache for pages

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Preferencias del lector y `ReaderViewModel`

**Files:**
- Delete: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`
- Create (copias con cambios): `eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`, `ReadingMode.kt`; `eu/kanade/domain/manga/interactor/SetMangaViewerFlags.kt`
- Modify: `eu/kanade/domain/manga/model/Manga.kt` (vuelve `readingMode`)
- Create (reescrito): `eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestLibrary.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt` (accesores)
- Test: `desktop-app/src/test/kotlin/eu/kanade/tachiyomi/ui/reader/ReaderViewModelTest.kt`

**Interfaces:**
- Consumes: `ChapterLoader`, `ChapterCache` y los modelos (Task 1); `GetManga`, `GetChaptersByMangaId`, `UpdateChapter`, `UpsertHistory`, `GetHistory`, `NetworkToLocalManga`, `LibraryPreferences` (`domain`); `GetIncognitoState` y `SyncChaptersWithSource` (portados en el plan 1).
- Produces:
  - `ReaderPreferences`: la de Mihon, más `preloadPages: Preference<Int>` (clave `pref_reader_preload_pages`, por defecto 4).
  - `ReadingMode` (los seis valores de Mihon; sin `toViewer`).
  - `Manga.readingMode: Long`.
  - `SetMangaViewerFlags.awaitSetReadingMode(id, flag)`.
  - `class ReaderViewModel(@Assisted("manga") mangaId: Long, @Assisted("chapter") chapterId: Long, …)`, con:
    - `state: StateFlow<State>`. Sin canal de eventos: la pantalla reacciona a `state`.
    - `onPageSelected(page)`, `loadNextChapter()`, `loadPreviousChapter()`, `retryPage(page)` y `retryInit()`.
    - `flush()` (suspend) y `saveHistory()`.
    - `toggleChapterBookmark()`, `getMangaReadingMode(): ReadingMode`, `setMangaReadingMode(mode)`, `showMenus(visible)` y `getChapterUrl()`.
    - `readerPreferences`.
  - `ReaderViewModel.State(manga, source, initError, viewerChapters, bookmarked, isLoadingAdjacentChapter, currentPage, menuVisible)`, con `currentChapter` y `totalPages`.
  - En los tests:
    - `TestLibrary.httpManga(graph): Pair<Manga, List<Chapter>>`, que devuelve tres capítulos ordenados del 1 al 3.
    - `TestAppGraph.readerViewModelFactory`, `networkToLocalManga`, `syncChaptersWithSource` y `getHistory`.

- [ ] **Step 1: Datos de prueba y accesores**

En `TestAppGraph`, añadir (con sus imports):

```kotlin
    val readerViewModelFactory: ReaderViewModel.Factory
    val networkToLocalManga: NetworkToLocalManga
    val syncChaptersWithSource: SyncChaptersWithSource
    val getHistory: GetHistory
```

`desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestLibrary.kt`:

```kotlin
package mihon.desktop.app.testing

import eu.kanade.domain.manga.model.toSManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.util.concurrent.atomic.AtomicInteger

object TestLibrary {
    private val next = AtomicInteger()

    /** A new manga of [TestSources.http] in the database with its three chapters, oldest first. */
    suspend fun httpManga(graph: TestAppGraph): Pair<Manga, List<Chapter>> {
        val n = next.incrementAndGet()
        val source = TestSources.http
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "/m/$n", title = "Manga HTTP $n", source = source.id),
        )
        val chapters = source.getMangaUpdate(manga.toSManga(), emptyList(), fetchDetails = false, fetchChapters = true)
            .chapters
        graph.syncChaptersWithSource.await(chapters, manga, source)
        return manga to graph.getChaptersByMangaId.await(manga.id).sortedBy { it.chapterNumber }
    }
}
```

- [ ] **Step 2: Escribir el test del ViewModel (falla: no existe)**

`desktop-app/src/test/kotlin/eu/kanade/tachiyomi/ui/reader/ReaderViewModelTest.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.source.model.Page
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.chapter.model.Chapter
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderViewModelTest {
    private lateinit var graph: TestAppGraph

    @BeforeAll
    fun start() {
        graph = startTestApp()
    }

    private suspend fun reader(chapterIndex: Int = 0): Pair<ReaderViewModel, List<Chapter>> {
        TestSources.http.pageCount = 5
        val (manga, chapters) = TestLibrary.httpManga(graph)
        val viewModel = graph.readerViewModelFactory.create(manga.id, chapters[chapterIndex].id)
        withTimeout(10.seconds) { viewModel.state.first { it.viewerChapters != null || it.initError != null } }
        return viewModel to chapters
    }

    private val ReaderViewModel.pages get() = state.value.currentChapter.shouldNotBeNull().pages.shouldNotBeNull()

    private suspend fun stored(chapter: Chapter): Chapter =
        graph.getChaptersByMangaId.await(chapter.mangaId).single { it.id == chapter.id }

    private suspend fun eventually(check: suspend () -> Boolean) = withTimeout(5.seconds) {
        while (!check()) delay(50)
    }

    @Test
    fun `turning pages saves the last page read once the reader settles`(): Unit = runBlocking {
        val (viewModel, chapters) = reader()
        viewModel.onPageSelected(viewModel.pages[1])
        viewModel.onPageSelected(viewModel.pages[2])

        // Grouped: nothing written right away
        stored(chapters[0]).lastPageRead shouldBe 0
        eventually { stored(chapters[0]).lastPageRead == 2 }
    }

    @Test
    fun `the last page marks the chapter read`(): Unit = runBlocking {
        val (viewModel, chapters) = reader()
        viewModel.onPageSelected(viewModel.pages.last())
        eventually { stored(chapters[0]).read }
    }

    @Test
    fun `flush writes a page turn made just before the window closes, and the history`(): Unit = runBlocking {
        val (viewModel, chapters) = reader()
        viewModel.onPageSelected(viewModel.pages[3])
        viewModel.flush()

        stored(chapters[0]).lastPageRead shouldBe 3
        graph.getHistory.await(chapters[0].mangaId).map { it.chapterId } shouldContain chapters[0].id
    }

    @Test
    fun `next and previous chapter, updating the history on the way`(): Unit = runBlocking {
        val (viewModel, chapters) = reader()
        viewModel.loadNextChapter()
        viewModel.state.value.currentChapter.shouldNotBeNull().chapter.id shouldBe chapters[1].id
        graph.getHistory.await(chapters[0].mangaId).map { it.chapterId } shouldContain chapters[0].id

        viewModel.loadPreviousChapter()
        viewModel.state.value.currentChapter.shouldNotBeNull().chapter.id shouldBe chapters[0].id
    }

    @Test
    fun `a failed page can be retried`(): Unit = runBlocking {
        val (viewModel, chapters) = reader()
        val page = viewModel.pages[0]
        val path = "${chapters[0].url}/0.png"
        TestSources.images.failing += path
        page.chapter.pageLoader.shouldNotBeNull().loadPage(page)
        withTimeout(10.seconds) { page.statusFlow.first { it is Page.State.Error } }

        TestSources.images.failing -= path
        viewModel.retryPage(page)
        withTimeout(10.seconds) { page.statusFlow.first { it == Page.State.Ready } }
    }

    @Test
    fun `a chapter whose page list fails reports it and can retry`(): Unit = runBlocking {
        TestSources.http.failPageList = IOException("Sin páginas de José")
        val (viewModel, _) = reader()
        viewModel.state.value.initError.shouldNotBeNull().message.shouldNotBeNull() shouldContain "José"

        viewModel.retryInit()
        withTimeout(10.seconds) { viewModel.state.first { it.viewerChapters != null } }
    }
}
```

- [ ] **Step 3: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "eu.kanade.tachiyomi.ui.reader.ReaderViewModelTest"`
Expected: FAIL de compilación: `Unresolved reference 'ReaderViewModel'`.

- [ ] **Step 4: Preferencias, modos y flags**

Borrar `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`.

`setting/ReaderPreferences.kt`: copiar con estos cambios:
- Borrar `import android.os.Build`.
- En `ColorFilterMode`, quitar el `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)` y dejar su `addAll(...)` siempre, con `// desktop: no Android SDK levels`.
- Añadir después de `skipDupe`:

```kotlin
    // desktop: pages loaded ahead of the one being read (spec 2a §4: 4 by default, configurable)
    val preloadPages: Preference<Int> = preferenceStore.getInt("pref_reader_preload_pages", 4)
```

`setting/ReadingMode.kt`: copiar y borrar `fun toViewer(...)` y los imports que solo usaba (`ReaderActivity`, `Viewer`, los visores y `appGraph`), con `// desktop: the viewers are Compose (mihon.desktop.app.reader.viewer)` en su lugar.

`eu/kanade/domain/manga/interactor/SetMangaViewerFlags.kt`: copiar y borrar `awaitSetOrientation` y el import de `ReaderOrientation`, con `// desktop: no screen orientation`.

En `eu/kanade/domain/manga/model/Manga.kt`, sustituir la línea `// desktop: readingMode and readerOrientation come back with the reader (plan 2 of subproject 2a)` por:

```kotlin
// desktop: no readerOrientation (no screen orientation on desktop)
val Manga.readingMode: Long
    get() = viewerFlags and ReadingMode.MASK.toLong()
```

(import `eu.kanade.tachiyomi.ui.reader.setting.ReadingMode`).

- [ ] **Step 5: `ReaderViewModel` de escritorio**

`eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * desktop: the subset of Mihon's ReaderViewModel the desktop reader needs (spec 2a §4): loading, progress, history,
 * adjacent chapters, bookmarks and the per-manga reading mode. No downloads, trackers, orientation, crop borders,
 * colour filters, set-as-cover or Android's saved state; the reader screen passes the manga and chapter ids.
 */
@AssistedInject
class ReaderViewModel(
    @Assisted("manga") val mangaId: Long,
    @Assisted("chapter") initialChapterId: Long,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val context: Context,
    private val sourceManager: SourceManager,
    private val chapterCache: ChapterCache,
    val readerPreferences: ReaderPreferences,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val upsertHistory: UpsertHistory,
    private val updateChapter: UpdateChapter,
    private val setMangaViewerFlags: SetMangaViewerFlags,
    private val getIncognitoState: GetIncognitoState,
    private val libraryPreferences: LibraryPreferences,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted("manga") mangaId: Long, @Assisted("chapter") chapterId: Long): ReaderViewModel
    }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    val manga: Manga?
        get() = state.value.manga

    private var chapterId = initialChapterId
    private var loader: ChapterLoader? = null
    private var chapterReadStartTime: Instant? = null
    private var incognitoMode = false
    private var chapterList: List<ReaderChapter> = emptyList()
    private var unfilteredChapterList: List<Chapter>? = null

    // Serializes the progress writes, which outlive the reader, so they are applied in order
    private val progressWrites = Mutex()

    @Volatile
    private var pendingProgressChapter: ReaderChapter? = null
    private var pendingProgressSave: Job? = null

    init {
        viewModelScope.launch { init() }
    }

    override fun onCleared() {
        state.value.viewerChapters?.unref()
    }

    private suspend fun init() {
        withContext(Dispatchers.IO) {
            try {
                val manga = getManga.await(mangaId) ?: error("Requested manga of id $mangaId not found")
                val source = sourceManager.getOrStub(manga.source)
                incognitoMode = getIncognitoState.await(manga.source)
                mutableState.update { it.copy(manga = manga, source = source, initError = null) }

                val loader = ChapterLoader(
                    context,
                    chapterCache,
                    appScope,
                    readerPreferences.preloadPages.get(),
                    manga,
                    source,
                )
                this@ReaderViewModel.loader = loader
                chapterList = loadChapterList(manga)
                restartReadTimer()
                loadChapter(loader, chapterList.first { it.chapter.id == chapterId })
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(initError = e) }
            }
        }
    }

    /** After a failed start, for example a page list that didn't load: starts over. */
    fun retryInit() {
        mutableState.update { it.copy(initError = null) }
        viewModelScope.launch { init() }
    }

    private suspend fun getUnfilteredChapterList(manga: Manga): List<Chapter> =
        unfilteredChapterList ?: getChaptersByMangaId.await(manga.id, applyScanlatorFilter = false)
            .also { unfilteredChapterList = it }

    private suspend fun loadChapterList(manga: Manga): List<ReaderChapter> {
        val chapters = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true).let { filtered ->
            if (filtered.any { it.id == chapterId }) {
                filtered
            } else {
                filtered + getUnfilteredChapterList(manga).filter { it.id == chapterId }
            }
        }
        val selectedChapter = chapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val skipRead = readerPreferences.skipRead.get()
        val skipFiltered = readerPreferences.skipFiltered.get()
        val chaptersForReader = if (!skipRead && !skipFiltered) {
            chapters
        } else {
            chapters.filterNot { (skipRead && it.read) || (skipFiltered && isFilteredOut(manga, it)) }
                .let { if (it.any { c -> c.id == chapterId }) it else it + selectedChapter }
        }

        return chaptersForReader
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .let { if (readerPreferences.skipDupe.get()) it.removeDuplicates(selectedChapter) else it }
            .map(::ReaderChapter)
    }

    // desktop: Mihon's chapter filters without the downloaded one (downloads arrive in subproject 4)
    private fun isFilteredOut(manga: Manga, chapter: Chapter): Boolean =
        (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !chapter.read) ||
            (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && chapter.read) ||
            (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !chapter.bookmark) ||
            (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && chapter.bookmark)

    /** Loads [chapter] with [loader] and makes it the active one. Callers handle errors. */
    private suspend fun loadChapter(loader: ChapterLoader, chapter: ReaderChapter): ViewerChapters {
        loader.loadChapter(chapter)

        val chapterPos = chapterList.indexOf(chapter)
        val newChapters = ViewerChapters(
            chapter,
            chapterList.getOrNull(chapterPos - 1),
            chapterList.getOrNull(chapterPos + 1),
        )
        withContext(Dispatchers.Main) {
            mutableState.update {
                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                it.viewerChapters?.unref()
                it.copy(viewerChapters = newChapters, bookmarked = chapter.chapter.bookmark)
            }
        }
        chapterId = chapter.chapter.id
        return newChapters
    }

    /** The viewer settled on a page: saves the progress and, if it belongs to another chapter, makes that one active. */
    fun onPageSelected(page: ReaderPage) {
        // InsertPage doesn't change page progress
        if (page is InsertPage) return

        val selectedChapter = page.chapter
        if (selectedChapter.pages == null) return

        updateChapterProgress(selectedChapter, page)

        if (selectedChapter != state.value.currentChapter) {
            logcat { "Setting ${selectedChapter.chapter.url} as active" }
            loadNewChapter(selectedChapter)
        }
    }

    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return
        viewModelScope.launch(Dispatchers.IO) {
            updateHistory()
            restartReadTimer()
            try {
                loadChapter(loader, chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private suspend fun loadAdjacent(chapter: ReaderChapter) {
        val loader = loader ?: return
        mutableState.update { it.copy(isLoadingAdjacentChapter = true) }
        try {
            // Spec 2a §4: the history is updated when the chapter changes
            withContext(Dispatchers.Main.immediate) {
                saveChapterProgress()
                updateHistory()
                restartReadTimer()
            }
            withContext(Dispatchers.IO) { loadChapter(loader, chapter) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
        } finally {
            mutableState.update { it.copy(isLoadingAdjacentChapter = false) }
        }
    }

    suspend fun loadNextChapter() {
        val nextChapter = state.value.viewerChapters?.nextChapter ?: return
        loadAdjacent(nextChapter)
    }

    suspend fun loadPreviousChapter() {
        val prevChapter = state.value.viewerChapters?.prevChapter ?: return
        loadAdjacent(prevChapter)
    }

    /** Downloads a page that failed again. */
    fun retryPage(page: ReaderPage) {
        page.chapter.pageLoader?.retryPage(page)
    }

    /**
     * Saves the last page read and whether the chapter is read, unless incognito mode is on. Completing a chapter is
     * saved right away; other page changes once the reader settles (spec 2a §4: grouped every 500 ms).
     */
    private fun updateChapterProgress(readerChapter: ReaderChapter, page: Page) {
        val pageIndex = page.index
        mutableState.update { it.copy(currentPage = pageIndex + 1) }
        readerChapter.requestedPage = pageIndex

        if (incognitoMode || page.status is Page.State.Error) return

        val read = readerChapter.chapter.read || readerChapter.pages?.lastIndex == pageIndex
        // A page change in another chapter would replace that chapter's pending save
        if (pendingProgressChapter != readerChapter) saveChapterProgress()
        readerChapter.update { it.copy(lastPageRead = pageIndex, read = read) }
        pendingProgressSave?.cancel()

        if (readerChapter.pages?.lastIndex == pageIndex) {
            pendingProgressChapter = null
            writeChapterProgress(readerChapter, onComplete = true)
        } else {
            pendingProgressChapter = readerChapter
            pendingProgressSave = viewModelScope.launch {
                delay(PROGRESS_SAVE_DELAY)
                saveChapterProgress()
            }
        }
    }

    private fun saveChapterProgress() {
        val readerChapter = pendingProgressChapter ?: return
        pendingProgressChapter = null
        writeChapterProgress(readerChapter, onComplete = false)
    }

    /** Writes the progress of [readerChapter] on the app scope, so it's saved even when the reader is closing. */
    private fun writeChapterProgress(readerChapter: ReaderChapter, onComplete: Boolean) {
        // Immediate and through the mutex, which is fair, so the writes land in the order they were made
        appScope.launch(Dispatchers.Main.immediate) {
            progressWrites.withLock {
                if (onComplete) markDuplicatesRead(readerChapter)
                val chapter = readerChapter.chapter
                updateChapter.await(
                    ChapterUpdate(chapter.id) {
                        read = chapter.read
                        lastPageRead = chapter.lastPageRead
                    },
                )
            }
        }
    }

    private suspend fun markDuplicatesRead(readerChapter: ReaderChapter) {
        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        val manga = manga ?: return
        if (!markDuplicateAsRead) return

        val duplicateUnreadChapters = getUnfilteredChapterList(manga).mapNotNull { chapter ->
            if (!chapter.read && chapter.isRecognizedNumber && chapter.chapterNumber == readerChapter.chapter.chapterNumber) {
                ChapterUpdate(chapter.id) { read = true }
            } else {
                null
            }
        }
        updateChapter.awaitAll(duplicateUnreadChapters)
    }

    fun restartReadTimer() {
        chapterReadStartTime = Clock.System.now()
    }

    private suspend fun updateHistory() {
        val readerChapter = state.value.currentChapter ?: return
        if (incognitoMode) return
        val endTime = Clock.System.now()
        val sessionReadDuration = chapterReadStartTime?.let { (endTime - it).inWholeMilliseconds } ?: 0
        chapterReadStartTime = null
        upsertHistory.await(HistoryUpdate(readerChapter.chapter.id, endTime, sessionReadDuration))
    }

    /** Writes the pending progress and the history now and waits for both, for when the window closes. */
    suspend fun flush() {
        withContext(Dispatchers.Main.immediate) {
            saveChapterProgress()
            // Fair mutex: this waits for every progress write launched before it
            progressWrites.withLock { }
            updateHistory()
        }
    }

    /** Leaving the reader: the history is saved on the app scope, which outlives the reader. */
    fun saveHistory() {
        appScope.launch { flush() }
    }

    /** The URL of the chapter the reader is on or was opening, for "open in browser" when it failed to load. */
    fun getChapterUrl(): String? {
        val chapter = state.value.currentChapter?.chapter
            ?: chapterList.find { it.chapter.id == chapterId }?.chapter
            ?: return null
        val source = state.value.source as? HttpSource ?: return null
        return try {
            source.getChapterUrl(chapter.toSChapter())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    fun toggleChapterBookmark() {
        val readerChapter = state.value.currentChapter ?: return
        val bookmarked = !readerChapter.chapter.bookmark
        readerChapter.update { it.copy(bookmark = bookmarked) }
        // On Main so quick toggles are saved in order
        viewModelScope.launch {
            updateChapter.await(ChapterUpdate(readerChapter.chapter.id) { bookmark = bookmarked })
        }
        mutableState.update { it.copy(bookmarked = bookmarked) }
    }

    /** This manga's reading mode, or the default one from the settings. Never [ReadingMode.DEFAULT]. */
    fun getMangaReadingMode(): ReadingMode {
        val mangaMode = ReadingMode.fromPreference(manga?.readingMode?.toInt())
        if (mangaMode != ReadingMode.DEFAULT) return mangaMode
        return ReadingMode.fromPreference(readerPreferences.defaultReadingMode.get())
            .takeIf { it != ReadingMode.DEFAULT } ?: ReadingMode.RIGHT_TO_LEFT
    }

    private var readingModeJob: Job? = null

    fun setMangaReadingMode(readingMode: ReadingMode) {
        val manga = manga ?: return
        // Only the latest pick matters, and letting an older one finish last would restore its choice
        readingModeJob?.cancel()
        readingModeJob = viewModelScope.launch(Dispatchers.IO) {
            setMangaViewerFlags.awaitSetReadingMode(manga.id, readingMode.flagValue.toLong())
            val updatedManga = getManga.await(manga.id)
            // desktop: the reader screen rebuilds its viewer when the manga changes, at the chapter's requestedPage,
            // which updateChapterProgress keeps on the page being read (Mihon sends ReloadViewerChapters instead)
            mutableState.update { it.copy(manga = updatedManga) }
        }
    }

    fun showMenus(visible: Boolean) {
        mutableState.update { it.copy(menuVisible = visible) }
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val source: Source? = null,
        val initError: Throwable? = null,
        val viewerChapters: ViewerChapters? = null,
        val bookmarked: Boolean = false,
        val isLoadingAdjacentChapter: Boolean = false,
        val currentPage: Int = -1,
        val menuVisible: Boolean = false,
    ) {
        val currentChapter: ReaderChapter?
            get() = viewerChapters?.currChapter

        val totalPages: Int
            get() = currentChapter?.pages?.size ?: -1
    }
}

// desktop: spec 2a §4 groups the progress writes every 500 ms (Mihon: 2 s)
private val PROGRESS_SAVE_DELAY = 500.milliseconds
```

Si Metro rechaza dos `@Assisted` del mismo tipo con identificador, la alternativa es un único parámetro asistido `args: ReaderArgs`, una `data class ReaderArgs(val mangaId: Long, val chapterId: Long)` en el mismo archivo. Anotarlo como ruling.

- [ ] **Step 6: Ejecutar el test y ver que pasa**

Run: `./gradlew :desktop-app:test --tests "eu.kanade.tachiyomi.ui.reader.ReaderViewModelTest"`
Expected: PASS (6 tests).

Run: `./gradlew :desktop-app:test`
Expected: PASS. `MangaViewModel` sigue compilando con la `ReaderPreferences` completa, porque `skipFiltered` existe en ambas.

- [ ] **Step 7: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add desktop-app
git commit -m "feat(desktop-app): reader view model with progress, history and adjacent chapters

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Lógica pura del lector: teclas, clics, doble página y tiles

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderInput.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/DualPage.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/PageTiles.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/ReaderInputTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/DualPageTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/PageTilesTest.kt`

**Interfaces:**
- Consumes: `ReadingMode` (Task 2).
- Produces:
  - `enum class ReaderAction { NextPage, PreviousPage, ScrollDown, ScrollUp, FirstPage, LastPage, NextChapter, PreviousChapter, ToggleFullscreen, ToggleMenu }`.
  - `fun readerActionFor(key: Key, shift: Boolean, ctrl: Boolean, alt: Boolean, mode: ReadingMode): ReaderAction?`.
  - `fun tapActionFor(x: Float, width: Float, mode: ReadingMode): ReaderAction`.
  - `sealed interface Spread { val pages: List<Int> }`, con `Single(page)` y `Double(first, second)`, y `fun pairPages(count: Int, isWide: (Int) -> Boolean, shifted: Boolean): List<Spread>`.
  - `fun Spread.inDisplayOrder(rightToLeft: Boolean): List<Int>`.
  - `const val TILE_LIMIT = 8192`, `const val TILE_HEIGHT = 4096` y `fun tileRanges(height: Int): List<IntRange>`.

- [ ] **Step 1: Escribir los tests (fallan: no existen)**

`ReaderInputTest.kt`:

```kotlin
package mihon.desktop.app.reader

import androidx.compose.ui.input.key.Key
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderInputTest {
    private fun key(key: Key, mode: ReadingMode, shift: Boolean = false) =
        readerActionFor(key, shift = shift, ctrl = false, alt = false, mode = mode)

    @Test
    fun `arrows follow the reading direction`() {
        key(Key.DirectionRight, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.NextPage
        key(Key.DirectionLeft, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.PreviousPage
        key(Key.DirectionRight, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.PreviousPage
        key(Key.DirectionLeft, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.NextPage
    }

    @Test
    fun `up and down scroll in webtoon and turn pages in paged modes`() {
        key(Key.DirectionDown, ReadingMode.WEBTOON) shouldBe ReaderAction.ScrollDown
        key(Key.DirectionUp, ReadingMode.CONTINUOUS_VERTICAL) shouldBe ReaderAction.ScrollUp
        key(Key.DirectionDown, ReadingMode.VERTICAL) shouldBe ReaderAction.NextPage
        key(Key.DirectionUp, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.PreviousPage
    }

    @Test
    fun `space, page keys, home, end, chapters and fullscreen`() {
        key(Key.Spacebar, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.NextPage
        key(Key.Spacebar, ReadingMode.RIGHT_TO_LEFT, shift = true) shouldBe ReaderAction.PreviousPage
        key(Key.PageDown, ReadingMode.WEBTOON) shouldBe ReaderAction.NextPage
        key(Key.PageUp, ReadingMode.WEBTOON) shouldBe ReaderAction.PreviousPage
        key(Key.MoveHome, ReadingMode.VERTICAL) shouldBe ReaderAction.FirstPage
        key(Key.MoveEnd, ReadingMode.VERTICAL) shouldBe ReaderAction.LastPage
        key(Key.N, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.NextChapter
        key(Key.P, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.PreviousChapter
        key(Key.F11, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.ToggleFullscreen
    }

    @Test
    fun `window shortcuts stay with the window`() {
        readerActionFor(Key.DirectionLeft, shift = false, ctrl = false, alt = true, mode = ReadingMode.LEFT_TO_RIGHT) shouldBe
            null
        readerActionFor(Key.F, shift = false, ctrl = true, alt = false, mode = ReadingMode.LEFT_TO_RIGHT) shouldBe null
        key(Key.Escape, ReadingMode.LEFT_TO_RIGHT) shouldBe null
    }

    @Test
    fun `the side thirds turn pages in the reading direction and the middle toggles the controls`() {
        tapActionFor(50f, 900f, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.PreviousPage
        tapActionFor(850f, 900f, ReadingMode.LEFT_TO_RIGHT) shouldBe ReaderAction.NextPage
        tapActionFor(50f, 900f, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.NextPage
        tapActionFor(850f, 900f, ReadingMode.RIGHT_TO_LEFT) shouldBe ReaderAction.PreviousPage
        tapActionFor(450f, 900f, ReadingMode.WEBTOON) shouldBe ReaderAction.ToggleMenu
    }
}
```

`DualPageTest.kt`:

```kotlin
package mihon.desktop.app.reader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DualPageTest {
    private val portrait: (Int) -> Boolean = { false }

    @Test
    fun `an even number of pages pairs them all`() {
        pairPages(4, portrait, shifted = false) shouldBe listOf(Spread.Double(0, 1), Spread.Double(2, 3))
    }

    @Test
    fun `an odd number of pages leaves the last one alone`() {
        pairPages(5, portrait, shifted = false) shouldBe
            listOf(Spread.Double(0, 1), Spread.Double(2, 3), Spread.Single(4))
    }

    @Test
    fun `a page wider than tall shows alone and pairing restarts after it`() {
        pairPages(5, { it == 2 }, shifted = false) shouldBe
            listOf(Spread.Double(0, 1), Spread.Single(2), Spread.Double(3, 4))
        pairPages(4, { it == 1 }, shifted = false) shouldBe
            listOf(Spread.Single(0), Spread.Single(1), Spread.Double(2, 3))
    }

    @Test
    fun `shifting by one shows the first page alone and moves the pairs`() {
        pairPages(5, portrait, shifted = true) shouldBe
            listOf(Spread.Single(0), Spread.Double(1, 2), Spread.Double(3, 4))
    }

    @Test
    fun `right to left shows the first page of a pair on the right`() {
        Spread.Double(2, 3).inDisplayOrder(rightToLeft = false) shouldBe listOf(2, 3)
        Spread.Double(2, 3).inDisplayOrder(rightToLeft = true) shouldBe listOf(3, 2)
        Spread.Single(4).inDisplayOrder(rightToLeft = true) shouldBe listOf(4)
    }
}
```

`PageTilesTest.kt`:

```kotlin
package mihon.desktop.app.reader

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PageTilesTest {
    @Test
    fun `a page under the limit is one tile`() {
        tileRanges(1200) shouldBe listOf(0 until 1200)
        tileRanges(TILE_LIMIT) shouldBe listOf(0 until TILE_LIMIT)
    }

    @Test
    fun `a 30000 px page is cut in contiguous tiles of at most 4096 px that add up to its height`() {
        val tiles = tileRanges(30_000)
        tiles shouldHaveSize 8
        tiles.forEach { it.count() shouldBeLessThanOrEqual TILE_HEIGHT }
        tiles.sumOf { it.count() } shouldBe 30_000
        tiles.zipWithNext().forEach { (a, b) -> b.first shouldBe a.last + 1 }
        tiles.first().first shouldBe 0
        tiles.last().last shouldBe 29_999
    }
}
```

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.*"`
Expected: FAIL de compilación: `Unresolved reference 'readerActionFor'`, `'pairPages'` y `'tileRanges'`.

- [ ] **Step 3: Implementación**

`ReaderInput.kt`:

```kotlin
package mihon.desktop.app.reader

import androidx.compose.ui.input.key.Key
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode

enum class ReaderAction {
    NextPage,
    PreviousPage,
    ScrollDown,
    ScrollUp,
    FirstPage,
    LastPage,
    NextChapter,
    PreviousChapter,
    ToggleFullscreen,
    ToggleMenu,
}

/**
 * The reader's keys (spec 2a §4, "Entrada"). ← and → follow the reading direction; ↑ and ↓ scroll in webtoon and
 * turn pages otherwise. Keys with Ctrl or Alt are the window's (Alt+←, Ctrl+F); Esc is Compose's back key.
 */
fun readerActionFor(key: Key, shift: Boolean, ctrl: Boolean, alt: Boolean, mode: ReadingMode): ReaderAction? {
    if (ctrl || alt) return null
    val rightToLeft = mode == ReadingMode.RIGHT_TO_LEFT
    val webtoon = mode.type == ReadingMode.ViewerType.Webtoon
    return when (key) {
        Key.DirectionRight -> if (rightToLeft) ReaderAction.PreviousPage else ReaderAction.NextPage
        Key.DirectionLeft -> if (rightToLeft) ReaderAction.NextPage else ReaderAction.PreviousPage
        Key.DirectionDown -> if (webtoon) ReaderAction.ScrollDown else ReaderAction.NextPage
        Key.DirectionUp -> if (webtoon) ReaderAction.ScrollUp else ReaderAction.PreviousPage
        Key.Spacebar -> if (shift) ReaderAction.PreviousPage else ReaderAction.NextPage
        Key.PageDown -> ReaderAction.NextPage
        Key.PageUp -> ReaderAction.PreviousPage
        Key.MoveHome -> ReaderAction.FirstPage
        Key.MoveEnd -> ReaderAction.LastPage
        Key.N -> ReaderAction.NextChapter
        Key.P -> ReaderAction.PreviousChapter
        Key.F11 -> ReaderAction.ToggleFullscreen
        else -> null
    }
}

/** A click at [x] of [width]: the side thirds turn pages in the reading direction, the middle one shows the controls. */
fun tapActionFor(x: Float, width: Float, mode: ReadingMode): ReaderAction {
    val rightToLeft = mode == ReadingMode.RIGHT_TO_LEFT
    return when {
        x < width / 3 -> if (rightToLeft) ReaderAction.NextPage else ReaderAction.PreviousPage
        x > width * 2 / 3 -> if (rightToLeft) ReaderAction.PreviousPage else ReaderAction.NextPage
        else -> ReaderAction.ToggleMenu
    }
}
```

`DualPage.kt`:

```kotlin
package mihon.desktop.app.reader

/** One screen of the paged viewer: one page or two side by side, as indices into the chapter's pages. */
sealed interface Spread {
    val pages: List<Int>

    data class Single(val page: Int) : Spread {
        override val pages get() = listOf(page)
    }

    data class Double(val first: Int, val second: Int) : Spread {
        override val pages get() = listOf(first, second)
    }
}

/**
 * Pairs [count] pages two by two in reading order (spec 2a §4, "Doble página"). A page wider than tall (a double-page
 * scan) shows alone and pairing restarts after it. [shifted] ("desplazar 1") shows the first page alone, which moves
 * every pair by one when the chapter's spreads start on the wrong page.
 */
fun pairPages(count: Int, isWide: (Int) -> Boolean, shifted: Boolean): List<Spread> {
    val spreads = mutableListOf<Spread>()
    var i = 0
    if (shifted && count > 0) {
        spreads += Spread.Single(0)
        i = 1
    }
    while (i < count) {
        if (isWide(i) || i + 1 >= count || isWide(i + 1)) {
            spreads += Spread.Single(i)
            i++
        } else {
            spreads += Spread.Double(i, i + 1)
            i += 2
        }
    }
    return spreads
}

/** Left to right on screen: in right-to-left reading the first page of a pair goes on the right. */
fun Spread.inDisplayOrder(rightToLeft: Boolean): List<Int> = if (rightToLeft) pages.reversed() else pages
```

`PageTiles.kt`:

```kotlin
package mihon.desktop.app.reader

/** Taller than this, a page is decoded in strips: one texture that tall can exceed the GPU's limit. */
const val TILE_LIMIT = 8192

/** Height of each strip of a page taller than [TILE_LIMIT]. */
const val TILE_HEIGHT = 4096

/** The rows to decode a page of [height] in (spec 2a §4, "Imágenes"): all of them, or strips of at most 4096 px. */
fun tileRanges(height: Int): List<IntRange> =
    if (height <= TILE_LIMIT) {
        listOf(0 until height)
    } else {
        (0 until height step TILE_HEIGHT).map { top -> top until minOf(top + TILE_HEIGHT, height) }
    }
```

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.*"`
Expected: PASS (5 + 5 + 2 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add desktop-app
git commit -m "feat(desktop-app): reader keys, click zones, dual-page pairing and tiles

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Imágenes: decodificación con Skia y caché de páginas decodificadas

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/image/PageDecoder.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/image/DecodedPageCache.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/image/PageDecoderTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/image/DecodedPageCacheTest.kt`

**Interfaces:**
- Consumes: `tileRanges` (Task 3).
- Produces:
  - `sealed interface DecodedPage { val width: Int; val height: Int }`, con `Still(width, height, tiles: List<ImageBitmap>)` y `Animated(width, height, frames: List<ImageBitmap>, durationsMs: List<Int>)`.
  - `object PageDecoder { fun decode(bytes: ByteArray): DecodedPage }`, que lanza `UnreadableImageException` si Skia no puede leerla.
  - `data class PageKey(val chapterId: Long, val index: Int)`.
  - `class DecodedPageCache(behind: Int = 2, ahead: Int = 4)`, con `get(key)`, `put(key, page)`, `retainAround(chapterId, current): Set<PageKey>` y `size`.

- [ ] **Step 1: Escribir los tests (fallan)**

`PageDecoderTest.kt`:

```kotlin
package mihon.desktop.app.reader.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PageDecoderTest {
    private fun png(width: Int, height: Int, format: String = "png"): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, it)
    }.toByteArray()

    @Test
    fun `a normal page is one bitmap`() {
        val page = PageDecoder.decode(png(600, 900)).shouldBeInstanceOf<DecodedPage.Still>()
        page.width shouldBe 600
        page.height shouldBe 900
        page.tiles shouldHaveSize 1
    }

    @Test
    fun `a very tall page is decoded in strips that add up to its height`() {
        val page = PageDecoder.decode(png(400, 30_000)).shouldBeInstanceOf<DecodedPage.Still>()
        page.tiles shouldHaveSize 8
        page.tiles.sumOf { it.height } shouldBe 30_000
        page.tiles.forEach { it.width shouldBe 400 }
    }

    @Test
    fun `jpeg and bmp decode too`() {
        PageDecoder.decode(png(300, 400, "jpg")).height shouldBe 400
        PageDecoder.decode(png(300, 400, "bmp")).height shouldBe 400
    }

    @Test
    fun `bytes that aren't an image report an error instead of crashing`() {
        shouldThrow<UnreadableImageException> { PageDecoder.decode("no es una imagen".toByteArray()) }
    }
}
```

`DecodedPageCacheTest.kt`:

```kotlin
package mihon.desktop.app.reader.image

import androidx.compose.ui.graphics.ImageBitmap
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DecodedPageCacheTest {
    private val page = DecodedPage.Still(1, 1, listOf(ImageBitmap(1, 1)))

    @Test
    fun `only the pages from two before to four after the current one stay decoded`() {
        val cache = DecodedPageCache()
        (0..20).forEach { cache.put(PageKey(1, it), page) }
        cache.put(PageKey(2, 5), page)

        val dropped = cache.retainAround(chapterId = 1, current = 10)

        cache.size shouldBe 7
        (8..14).forEach { cache.get(PageKey(1, it)).shouldNotBeNull() }
        cache.get(PageKey(1, 7)).shouldBeNull()
        cache.get(PageKey(1, 15)).shouldBeNull()
        // Pages of another chapter go too
        cache.get(PageKey(2, 5)).shouldBeNull()
        dropped.size shouldBe 15
    }

    @Test
    fun `turning many pages keeps the cache bounded`() {
        val cache = DecodedPageCache()
        (0 until 200).forEach { current ->
            cache.put(PageKey(1, current), page)
            cache.retainAround(chapterId = 1, current = current)
        }
        cache.size shouldBe 3
    }
}
```

En el segundo caso se van guardando páginas solo hasta la actual, así que quedan las de −2 a 0: 3 páginas.

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.image.*"`
Expected: FAIL de compilación: `Unresolved reference 'PageDecoder'` y `'DecodedPageCache'`.

- [ ] **Step 3: Implementación**

`PageDecoder.kt`:

```kotlin
package mihon.desktop.app.reader.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import mihon.desktop.app.reader.tileRanges
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image

/** A page ready to draw: strips of a still image (one, unless it's very tall), or the frames of an animated GIF. */
sealed interface DecodedPage {
    val width: Int
    val height: Int

    class Still(override val width: Int, override val height: Int, val tiles: List<ImageBitmap>) : DecodedPage

    class Animated(
        override val width: Int,
        override val height: Int,
        val frames: List<ImageBitmap>,
        val durationsMs: List<Int>,
    ) : DecodedPage
}

class UnreadableImageException(cause: Throwable) : Exception("The image couldn't be decoded", cause)

/** Decodes page images with Skia (spec 2a §4): JPEG, PNG, WebP, GIF and BMP. */
object PageDecoder {
    fun decode(bytes: ByteArray): DecodedPage {
        val codec = try {
            Codec.makeFromData(Data.makeFromBytes(bytes))
        } catch (e: Exception) {
            throw UnreadableImageException(e)
        }
        codec.use {
            if (it.frameCount > 1) return animated(it)
        }
        val image = try {
            Image.makeFromEncoded(bytes)
        } catch (e: Exception) {
            throw UnreadableImageException(e)
        }
        return image.use(::still)
    }

    private fun still(image: Image): DecodedPage.Still {
        val full = Bitmap.makeFromImage(image)
        val tiles = tileRanges(full.height).map { rows ->
            if (rows.count() == full.height) {
                full
            } else {
                Bitmap().also { full.extractSubset(it, IRect.makeLTRB(0, rows.first, full.width, rows.last + 1)) }
            }
        }
        return DecodedPage.Still(full.width, full.height, tiles.map { it.asComposeImageBitmap() })
    }

    private fun animated(codec: Codec): DecodedPage.Animated {
        val frames = (0 until codec.frameCount).map { index ->
            Bitmap().also {
                it.allocPixels(codec.imageInfo)
                codec.readPixels(it, index)
            }.asComposeImageBitmap()
        }
        return DecodedPage.Animated(
            codec.width,
            codec.height,
            frames,
            codec.framesInfo.map { it.duration.coerceAtLeast(MIN_FRAME_MS) },
        )
    }

    // Browsers show 0 and 10 ms GIF frames at about 100 ms
    private const val MIN_FRAME_MS = 20
}
```

Si `Codec.makeFromData` devuelve `null` en lugar de lanzar una excepción con datos que no son una imagen, cambiar el `try` por `?: throw UnreadableImageException(IllegalArgumentException("Unknown image format"))`. Para verlo: `javap -cp <skiko-awt jar> org.jetbrains.skia.Codec$Companion`. Anotarlo como ruling.

`DecodedPageCache.kt`:

```kotlin
package mihon.desktop.app.reader.image

data class PageKey(val chapterId: Long, val index: Int)

/**
 * The decoded pages kept in memory (spec 2a §4): from [behind] before to [ahead] after the page being read. The
 * others stay in the chapter cache as bytes and are decoded again if the reader comes back to them.
 */
class DecodedPageCache(private val behind: Int = 2, private val ahead: Int = 4) {
    private val pages = HashMap<PageKey, DecodedPage>()

    val size: Int
        @Synchronized get() = pages.size

    @Synchronized
    fun get(key: PageKey): DecodedPage? = pages[key]

    @Synchronized
    fun put(key: PageKey, page: DecodedPage) {
        pages[key] = page
    }

    /** Drops every page outside the window around [current] of [chapterId]; returns the ones dropped. */
    @Synchronized
    fun retainAround(chapterId: Long, current: Int): Set<PageKey> {
        val window = (current - behind)..(current + ahead)
        val dropped = pages.keys.filterTo(HashSet()) { it.chapterId != chapterId || it.index !in window }
        dropped.forEach(pages::remove)
        return dropped
    }
}
```

`ponytail:` los bitmaps descartados los libera el recolector, porque Skia libera su memoria nativa al recogerlos. Si la prueba manual 5 (webtoon largo) muestra picos de memoria, hay que cerrarlos aquí de forma explícita.

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.image.*"`
Expected: PASS (4 + 2 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add desktop-app
git commit -m "feat(desktop-app): Skia page decoding in tiles and a bounded decoded-page cache

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Pantalla del lector, visor paginado, pantalla completa y apertura desde el manga

**Files:**
- Modify: `gradle/libs.versions.toml`, `desktop-app/build.gradle.kts` (zoomable)
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/WindowControls.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/ExitHooks.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderLook.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/ViewerItems.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/PageContent.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/PagerViewer.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt`
- Create (copia sin cambios): `eu/kanade/presentation/reader/ChapterTransition.kt`
- Delete: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderLauncher.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`
- Modify: `i18n/.../base/strings.xml`, `.../es/strings.xml` (borrar `desktop_reader_coming`)
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/platform/WindowControlsTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/platform/ExitHooksTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/ReaderScreenTest.kt`

**Interfaces:**
- Consumes:
  - `ReaderViewModel` (Task 2).
  - `readerActionFor`, `tapActionFor`, `pairPages` e `inDisplayOrder` (Task 3).
  - `PageDecoder`, `DecodedPageCache` y `PageKey` (Task 4).
  - `ConnectWindowBack`, `BackHandler` y `FileDialogs` (plan 1).
- Produces:
  - `class WindowControls(state: WindowState)`, con `isFullscreen`, `toggleFullscreen()`, `exitFullscreen()` y `placementToSave`; `val LocalWindowControls: ProvidableCompositionLocal<WindowControls?>`.
  - `object ExitHooks { fun add(hook); fun remove(hook); suspend fun runAll(timeout) }`.
  - `enum class ImageScale(prefValue, contentScale, label)` y `enum class ReaderBackground(prefValue, label)`, con `fromPreference`.
  - `sealed interface ViewerItem`, con `Pages(spread, pages)` y `Transition(transition)`, y `fun viewerItems(chapters: ViewerChapters, spreads: List<Spread>): List<ViewerItem>`.
  - `interface ViewerController { suspend fun perform(action: ReaderAction); suspend fun goToPage(index: Int) }`.
  - `@Composable fun PageContent(page, isCurrent, scale, cache, zoom: Boolean, onTap, onRetry, onSize, modifier)`.
  - `@Composable fun PagerViewer(...)`.
  - `class ReaderScreen(mangaId: Long, chapterId: Long) : Screen()` y `@Composable fun ReaderContent(viewModel, onClose)`.

- [ ] **Step 1: Escribir los tests de ventana y de cierre (fallan)**

`WindowControlsTest.kt`:

```kotlin
package mihon.desktop.app.platform

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WindowControlsTest {
    @Test
    fun `fullscreen toggles and goes back to the placement it came from`() {
        val controls = WindowControls(WindowState(placement = WindowPlacement.Maximized))
        controls.toggleFullscreen()
        controls.isFullscreen shouldBe true
        controls.toggleFullscreen()
        controls.isFullscreen shouldBe false
        controls.placementToSave shouldBe WindowPlacement.Maximized
    }

    @Test
    fun `closing in fullscreen saves the placement before it, never fullscreen`() {
        val controls = WindowControls(WindowState(placement = WindowPlacement.Floating))
        controls.toggleFullscreen()
        controls.placementToSave shouldBe WindowPlacement.Floating
    }
}
```

`ExitHooksTest.kt`:

```kotlin
package mihon.desktop.app.platform

import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class ExitHooksTest {
    @Test
    fun `every hook runs even when one fails, and a slow one can't hold the exit`(): Unit = runBlocking {
        val ran = mutableListOf<String>()
        val failing: suspend () -> Unit = { error("falla") }
        val working: suspend () -> Unit = { ran += "guardado" }
        val stuck: suspend () -> Unit = { delay(60_000) }
        listOf(failing, working, stuck).forEach(ExitHooks::add)

        val elapsed = measureTime { ExitHooks.runAll(timeout = 300.milliseconds) }

        ran shouldBe listOf("guardado")
        elapsed.inWholeMilliseconds shouldBeLessThan 5_000
        listOf(failing, working, stuck).forEach(ExitHooks::remove)
    }
}
```

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.platform.WindowControlsTest" --tests "mihon.desktop.app.platform.ExitHooksTest"`
Expected: FAIL de compilación: `Unresolved reference 'WindowControls'` y `'ExitHooks'`.

- [ ] **Step 3: `WindowControls` y `ExitHooks`**

`WindowControls.kt`:

```kotlin
package mihon.desktop.app.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

/** The window's real fullscreen (spec 2a §4: F11), for the reader. */
class WindowControls(private val state: WindowState) {
    private var placementBefore = state.placement

    val isFullscreen: Boolean
        get() = state.placement == WindowPlacement.Fullscreen

    fun toggleFullscreen() {
        if (isFullscreen) {
            state.placement = placementBefore
        } else {
            placementBefore = state.placement
            state.placement = WindowPlacement.Fullscreen
        }
    }

    fun exitFullscreen() {
        if (isFullscreen) toggleFullscreen()
    }

    /** What to remember when the window closes: never fullscreen, which only the reader turns on. */
    val placementToSave: WindowPlacement
        get() = if (isFullscreen) placementBefore else state.placement
}

/** Null outside a window (previews and tests that don't need fullscreen). */
val LocalWindowControls: ProvidableCompositionLocal<WindowControls?> = staticCompositionLocalOf { null }
```

`ExitHooks.kt`:

```kotlin
package mihon.desktop.app.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Work that must finish before the window closes, such as the reader's last progress write. */
object ExitHooks {
    private val hooks = CopyOnWriteArraySet<suspend () -> Unit>()

    fun add(hook: suspend () -> Unit) {
        hooks += hook
    }

    fun remove(hook: suspend () -> Unit) {
        hooks -= hook
    }

    /** Runs every hook; a failing one doesn't stop the others, and together they can't hold the exit past [timeout]. */
    suspend fun runAll(timeout: Duration = 2.seconds) {
        withTimeoutOrNull(timeout) {
            hooks.forEach { hook ->
                try {
                    hook()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Exit hook failed" }
                }
            }
        }
    }
}
```

En `Main.kt`, dentro de `application { }`:
- Después de `val windowState = rememberWindowState(...)`, añadir `val windowControls = remember { WindowControls(windowState) }` y `val exitScope = rememberCoroutineScope()`.
- `onCloseRequest` pasa a ser:

```kotlin
                onCloseRequest = {
                    // The reader's last progress write and history finish first (at most 2 s)
                    exitScope.launch {
                        ExitHooks.runAll()
                        windowStore.save(windowState.toSaved(windowControls.placementToSave))
                        instance.close()
                        exitApplication()
                    }
                },
```

- Envolver el `key(restarts) { AppContent(...) }` en `CompositionLocalProvider(LocalWindowControls provides windowControls) { ... }`.
- `WindowState.toSaved()` pasa a recibir `placement: WindowPlacement` y a usar `maximized = placement == WindowPlacement.Maximized`.

Imports: `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch`, `mihon.desktop.app.platform.ExitHooks`, `mihon.desktop.app.platform.LocalWindowControls`, `mihon.desktop.app.platform.WindowControls`.

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.platform.WindowControlsTest" --tests "mihon.desktop.app.platform.ExitHooksTest"`
Expected: PASS (2 + 1 tests).

- [ ] **Step 4: Escribir el test de la pantalla del lector (falla)**

`desktop-app/src/test/kotlin/mihon/desktop/app/reader/ReaderScreenTest.kt`:

```kotlin
package mihon.desktop.app.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.window.WindowState
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.main.ConnectWindowBack
import mihon.desktop.app.main.Shortcut
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.platform.WindowControls
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class ReaderScreenTest {
    private object Below : Screen() {
        @Composable
        override fun Content() {
            Text("pantalla anterior")
        }
    }

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.openReader(controls: WindowControls, corruptFirstPage: Boolean = false) {
        val graph = startTestApp()
        TestSources.http.pageCount = 5
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        if (corruptFirstPage) TestSources.images.corrupt += "${chapters[0].url}/0.png"
        // Left to right, so → is "next page"
        graph.readerPreferences.defaultReadingMode.set(ReadingMode.LEFT_TO_RIGHT.flagValue)
        setContent {
            AppProviders(graph) {
                CompositionLocalProvider(LocalWindowControls provides controls) {
                    ConnectWindowBack()
                    Navigator(listOf(Below, ReaderScreen(manga.id, chapters[0].id))) { DefaultNavigatorScreenTransition(it) }
                }
            }
        }
        waitForTag("page 1")
    }

    @Test
    fun `the right arrow turns to the next page`() = runComposeUiTest {
        openReader(WindowControls(WindowState()))
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
        waitForTag("page 2")
    }

    @Test
    fun `F11 toggles fullscreen, Esc leaves it first and then closes the reader`() = runComposeUiTest {
        val controls = WindowControls(WindowState())
        openReader(controls)

        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.F11) }
        waitForIdle()
        controls.isFullscreen shouldBe true

        // Esc reaches the reader as Compose's back event, which ConnectWindowBack forwards
        runOnIdle { AppEvents.send(Shortcut.Back) }
        waitForIdle()
        controls.isFullscreen shouldBe false
        onNodeWithTag(READER_TAG).assertExists()

        runOnIdle { AppEvents.send(Shortcut.Back) }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("pantalla anterior")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a page that can't be decoded shows the error with retry and the next one still reads`() = runComposeUiTest {
        openReader(WindowControls(WindowState()), corruptFirstPage = true)
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(str(MR.strings.decode_image_error))).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
        waitForTag("page 2 ready")
    }
}
```

Añadir a `TestAppGraph` el accesor `val readerPreferences: ReaderPreferences`, y a `AppGraph` también, porque los ajustes de la Task 9 lo usan.

- [ ] **Step 5: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.ReaderScreenTest"`
Expected: FAIL de compilación: `Unresolved reference 'ReaderScreen'` y `'READER_TAG'`.

- [ ] **Step 6: Dependencia de zoom y aspecto**

En `gradle/libs.versions.toml`, en `[versions]`, añadir `zoomable = "2.16.0"`; en `[libraries]`, añadir `zoomable = { module = "net.engawapg.lib:zoomable", version.ref = "zoomable" }`. En `desktop-app/build.gradle.kts`, añadir `implementation(libs.zoomable)`.

Comprobar la API antes de usarla:

Run: `./gradlew :desktop-app:dependencies --configuration runtimeClasspath | grep zoomable` y, con el jar resultante, `javap -cp <jar> net.engawapg.lib.zoomable.ZoomableKt net.engawapg.lib.zoomable.ZoomStateKt`
Expected:
- existen `Modifier.zoomable(zoomState, zoomEnabled, enableOneFingerZoom, scrollGesturePropagation, onTap, onDoubleTap, mouseWheelZoom)` y `rememberZoomState(maxScale, ...)`;
- existe `MouseWheelZoom.EnabledWithCtrlKey`.

Si el nombre de algún parámetro es distinto, usar el que tenga el jar y anotar el ruling. Si `mouseWheelZoom` no existe, Ctrl+rueda se implementa en `PageContent` con `Modifier.onPointerEvent(PointerEventType.Scroll)` cuando Ctrl está pulsado, llamando a `zoomState.changeScale(...)`.

`mihon/desktop/app/reader/ReaderLook.kt`:

```kotlin
package mihon.desktop.app.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

/** Mihon's imageScaleType values the desktop reader supports (spec deviation 6); the others read as fit screen. */
enum class ImageScale(val prefValue: Int, val contentScale: ContentScale, val label: StringResource) {
    FIT_SCREEN(1, ContentScale.Fit, MR.strings.scale_type_fit_screen),
    FIT_WIDTH(3, ContentScale.FillWidth, MR.strings.scale_type_fit_width),
    FIT_HEIGHT(4, ContentScale.FillHeight, MR.strings.scale_type_fit_height),
    ORIGINAL(5, ContentScale.None, MR.strings.scale_type_original_size),
    ;

    companion object {
        fun fromPreference(value: Int): ImageScale = entries.find { it.prefValue == value } ?: FIT_SCREEN
    }
}

/** Mihon's readerTheme values: the colour behind the pages. */
enum class ReaderBackground(val prefValue: Int, val label: StringResource) {
    WHITE(0, MR.strings.white_background),
    BLACK(1, MR.strings.black_background),
    GRAY(2, MR.strings.gray_background),
    AUTOMATIC(3, MR.strings.automatic_background),
    ;

    @Composable
    fun color(): Color = when (this) {
        WHITE -> Color.White
        BLACK -> Color.Black
        GRAY -> Color(0xFF202125)
        AUTOMATIC -> MaterialTheme.colorScheme.background
    }

    companion object {
        fun fromPreference(value: Int): ReaderBackground = entries.find { it.prefValue == value } ?: BLACK
    }
}
```

- [ ] **Step 7: Elementos del visor y contenido de una página**

`viewer/ViewerItems.kt`:

```kotlin
package mihon.desktop.app.reader.viewer

import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.reader.Spread

/** What the viewers show, in order: the way to the previous chapter, the pages (one or two per item), the way on. */
sealed interface ViewerItem {
    data class Pages(val spread: Spread, val pages: List<ReaderPage>) : ViewerItem

    data class Transition(val transition: ChapterTransition) : ViewerItem
}

fun viewerItems(chapters: ViewerChapters, spreads: List<Spread>): List<ViewerItem> {
    val current = chapters.currChapter
    val pages = current.pages.orEmpty()
    return buildList {
        add(ViewerItem.Transition(ChapterTransition.Prev(current, chapters.prevChapter)))
        spreads.forEach { spread -> add(ViewerItem.Pages(spread, spread.pages.map(pages::get))) }
        add(ViewerItem.Transition(ChapterTransition.Next(current, chapters.nextChapter)))
    }
}

/** The item showing page [index] of the current chapter, or the first page's when there's none. */
fun List<ViewerItem>.itemOfPage(index: Int): Int =
    indexOfFirst { it is ViewerItem.Pages && it.spread.pages.contains(index) }.takeIf { it >= 0 } ?: 1

/** Turns, jumps and scrolls for the viewer on screen; the reader screen sends the keys and clicks here. */
interface ViewerController {
    suspend fun perform(action: ReaderAction)

    suspend fun goToPage(index: Int)
}
```

`viewer/PageContent.kt`:

```kotlin
package mihon.desktop.app.reader.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mihon.desktop.app.reader.ImageScale
import mihon.desktop.app.reader.image.DecodedPage
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.image.PageDecoder
import mihon.desktop.app.reader.image.PageKey
import net.engawapg.lib.zoomable.MouseWheelZoom
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * One page: loads it, decodes it once it's downloaded and draws it. Failures stay on this page with a retry
 * (spec 2a §4, "Errores del lector"). With [zoom], double click and Ctrl+wheel zoom and dragging pans; the zoom
 * starts over whenever the page becomes the current one again.
 */
@Composable
fun PageContent(
    page: ReaderPage,
    isCurrent: Boolean,
    scale: ImageScale,
    cache: DecodedPageCache,
    zoom: Boolean,
    onTap: (x: Float, width: Float) -> Unit,
    onRetry: (ReaderPage) -> Unit,
    onSize: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status by page.statusFlow.collectAsStateWithLifecycle()
    LaunchedEffect(page) { page.chapter.pageLoader?.loadPage(page) }

    val key = PageKey(page.chapter.chapter.id, page.index)
    val decoded by produceState<Result<DecodedPage>?>(cache.get(key)?.let(Result.Companion::success), status) {
        if (value?.isSuccess == true || status != Page.State.Ready) return@produceState
        val stream = page.stream ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { PageDecoder.decode(stream().use { it.readBytes() }) }
        }.onSuccess {
            cache.put(key, it)
            onSize(IntSize(it.width, it.height))
        }
    }

    // The page's width, for the click zones of zoomable's onTap, which only gives the offset
    var width by remember { mutableIntStateOf(1) }
    val tapModifier = Modifier.pointerInput(page) {
        detectTapGestures { offset -> onTap(offset.x, size.width.toFloat()) }
    }

    val result = decoded
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .testTag(if (result?.isSuccess == true) "page ${page.number} ready" else "page ${page.number}"),
    ) {
        when {
            status is Page.State.Error || result?.isFailure == true -> PageError(page, onRetry, tapModifier)
            result == null -> PageLoading(page, tapModifier)
            else -> {
                // A new zoom state each time the page becomes current: the zoom starts over (spec 2a §4)
                val zoomState = key(page, isCurrent) { rememberZoomState(maxScale = 5f) }
                val pageModifier = if (zoom) {
                    Modifier.fillMaxSize().zoomable(
                        zoomState = zoomState,
                        onTap = { offset: Offset -> onTap(offset.x, width.toFloat()) },
                        mouseWheelZoom = MouseWheelZoom.EnabledWithCtrlKey,
                    )
                } else {
                    tapModifier.fillMaxWidth()
                }
                DecodedPageImage(result.getOrThrow(), if (zoom) scale.contentScale else ContentScale.FillWidth, pageModifier)
            }
        }
    }
}

@Composable
private fun PageLoading(page: ReaderPage, modifier: Modifier) {
    val progress by page.progressFlow.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (progress in 1..99) CircularProgressIndicator(progress = { progress / 100f }) else CircularProgressIndicator()
    }
}

@Composable
private fun PageError(page: ReaderPage, onRetry: (ReaderPage) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(MR.strings.decode_image_error), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { onRetry(page) }) { Text(stringResource(MR.strings.action_retry)) }
    }
}

@Composable
fun DecodedPageImage(page: DecodedPage, contentScale: ContentScale, modifier: Modifier) {
    when (page) {
        is DecodedPage.Still -> if (page.tiles.size == 1) {
            Image(page.tiles.single(), contentDescription = null, contentScale = contentScale, modifier = modifier)
        } else {
            // Very tall page: its strips stacked, each as wide as the page
            Column(modifier) {
                page.tiles.forEach {
                    Image(it, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        is DecodedPage.Animated -> {
            var frame by remember(page) { mutableIntStateOf(0) }
            LaunchedEffect(page) {
                while (true) {
                    delay(page.durationsMs[frame].toLong())
                    frame = (frame + 1) % page.frames.size
                }
            }
            Image(page.frames[frame], contentDescription = null, contentScale = contentScale, modifier = modifier)
        }
    }
}
```

- [ ] **Step 8: Visor paginado**

`viewer/PagerViewer.kt`:

```kotlin
package mihon.desktop.app.reader.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import eu.kanade.presentation.reader.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.launch
import mihon.desktop.app.reader.tapActionFor
import mihon.desktop.app.reader.ImageScale
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.reader.Spread
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.inDisplayOrder
import mihon.desktop.app.reader.pairPages

/**
 * The paged modes (spec 2a §4): left to right and right to left with a HorizontalPager (right to left reversed),
 * vertical with a VerticalPager. With [dual], pages pair two by two once their sizes are known.
 */
@Composable
fun PagerViewer(
    chapters: ViewerChapters,
    mode: ReadingMode,
    dual: Boolean,
    shifted: Boolean,
    scale: ImageScale,
    cache: DecodedPageCache,
    onPageSelected: (ReaderPage) -> Unit,
    onPastEnd: (forward: Boolean) -> Unit,
    onTap: (ReaderAction) -> Unit,
    onRetry: (ReaderPage) -> Unit,
    onController: (ViewerController) -> Unit,
) {
    val current = chapters.currChapter
    val pages = current.pages.orEmpty()
    val sizes = remember(current) { mutableStateMapOf<Int, IntSize>() }
    val spreads = if (dual && mode.direction == ReadingMode.Direction.Horizontal) {
        pairPages(pages.size, { sizes[it]?.let { s -> s.width > s.height } ?: false }, shifted)
    } else {
        pages.indices.map { Spread.Single(it) }
    }
    val items = remember(chapters, spreads) { viewerItems(chapters, spreads) }
    val latestItems by rememberUpdatedState(items)
    val state = rememberPagerState(initialPage = items.itemOfPage(current.requestedPage)) { latestItems.size }
    val rightToLeft = mode == ReadingMode.RIGHT_TO_LEFT

    // Pairs change as page sizes become known: stay on the page being read
    LaunchedEffect(items) {
        val target = items.itemOfPage(current.requestedPage)
        if (state.currentPage != target) state.scrollToPage(target)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect { index ->
            val item = latestItems.getOrNull(index) as? ViewerItem.Pages ?: return@collect
            // The last page of a pair, so reaching the end of the chapter marks it read
            onPageSelected(item.pages.last())
            cache.retainAround(current.chapter.id, item.pages.last().index)
        }
    }
    val controller = remember(state) { PagerController(state, { latestItems }, onPastEnd) }
    LaunchedEffect(controller) { onController(controller) }

    val scope = rememberCoroutineScope()
    val pagerModifier = Modifier.fillMaxSize().onPointerEvent(PointerEventType.Scroll) { event ->
        // The wheel turns pages; Ctrl+wheel is the page's zoom
        if (event.keyboardModifiers.isCtrlPressed) return@onPointerEvent
        val delta = event.changes.first().scrollDelta.y
        scope.launch { controller.perform(if (delta > 0) ReaderAction.NextPage else ReaderAction.PreviousPage) }
    }
    val pageOf: @Composable (Int) -> Unit = { index ->
        when (val item = items[index]) {
            is ViewerItem.Transition -> Box(
                Modifier.fillMaxSize().pointerInput(item) {
                    detectTapGestures { onTap(tapActionFor(it.x, size.width.toFloat(), mode)) }
                },
                contentAlignment = Alignment.Center,
            ) {
                ChapterTransition(item.transition, currChapterDownloaded = false, goingToChapterDownloaded = false)
            }
            is ViewerItem.Pages -> Row(Modifier.fillMaxSize()) {
                item.spread.inDisplayOrder(rightToLeft).forEach { pageIndex ->
                    val page = pages[pageIndex]
                    PageContent(
                        page = page,
                        isCurrent = state.settledPage == index,
                        scale = scale,
                        cache = cache,
                        zoom = true,
                        onTap = { x, width -> onTap(tapActionFor(x, width, mode)) },
                        onRetry = onRetry,
                        onSize = { sizes[pageIndex] = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (mode.direction == ReadingMode.Direction.Vertical) {
        VerticalPager(state = state, modifier = pagerModifier, key = { items[it].key() }) { pageOf(it) }
    } else {
        HorizontalPager(state = state, modifier = pagerModifier, reverseLayout = rightToLeft, key = { items[it].key() }) {
            pageOf(it)
        }
    }
}

private fun ViewerItem.key(): Any = when (this) {
    is ViewerItem.Pages -> spread
    is ViewerItem.Transition -> transition.javaClass.simpleName
}

private class PagerController(
    private val state: PagerState,
    private val items: () -> List<ViewerItem>,
    private val onPastEnd: (forward: Boolean) -> Unit,
) : ViewerController {
    override suspend fun perform(action: ReaderAction) {
        val last = items().lastIndex
        when (action) {
            ReaderAction.NextPage, ReaderAction.ScrollDown ->
                if (state.currentPage < last) state.animateScrollToPage(state.currentPage + 1) else onPastEnd(true)
            ReaderAction.PreviousPage, ReaderAction.ScrollUp ->
                if (state.currentPage > 0) state.animateScrollToPage(state.currentPage - 1) else onPastEnd(false)
            ReaderAction.FirstPage -> state.scrollToPage(1.coerceAtMost(last))
            ReaderAction.LastPage -> state.scrollToPage((last - 1).coerceAtLeast(0))
            else -> Unit
        }
    }

    override suspend fun goToPage(index: Int) {
        state.scrollToPage(items().itemOfPage(index))
    }
}
```

`eu/kanade/presentation/reader/ChapterTransition.kt`: copiarlo de Mihon sin cambios. Si alguno de sus `@Preview` no compila con CMP, borrar esas funciones de preview con `// desktop: Android-only preview annotation`.

- [ ] **Step 9: La pantalla del lector**

`mihon/desktop/app/reader/ReaderScreen.kt`:

```kotlin
package mihon.desktop.app.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.launch
import mihon.desktop.app.platform.ExitHooks
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.viewer.PagerViewer
import mihon.desktop.app.reader.viewer.ViewerController
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState

const val READER_TAG = "reader"

/** The reader takes the whole window (spec 2a §4, "Pantalla"): a screen pushed on top of everything else. */
class ReaderScreen(private val mangaId: Long, private val chapterId: Long) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val window = LocalWindowControls.current
        val viewModel = assistedMetroViewModel<ReaderViewModel, ReaderViewModel.Factory> { create(mangaId, chapterId) }

        // Esc: out of fullscreen first, then out of the reader
        BackHandler { if (window?.isFullscreen == true) window.exitFullscreen() else navigator.pop() }
        DisposableEffect(viewModel) {
            val flush: suspend () -> Unit = { viewModel.flush() }
            ExitHooks.add(flush)
            onDispose {
                ExitHooks.remove(flush)
                window?.exitFullscreen()
                viewModel.saveHistory()
            }
        }
        ReaderContent(viewModel, onClose = navigator::pop)
    }
}

@Composable
fun ReaderContent(viewModel: ReaderViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preferences = viewModel.readerPreferences
    val window = LocalWindowControls.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var controller by remember { mutableStateOf<ViewerController?>(null) }
    val cache = remember { DecodedPageCache() }
    val mode = remember(state.manga) { viewModel.getMangaReadingMode() }
    val scale by preferences.imageScaleType.collectAsState()
    val background by preferences.readerTheme.collectAsState()
    val dualView by preferences.dualPageView.collectAsState()
    var shifted by remember { mutableStateOf(false) }

    fun perform(action: ReaderAction) {
        when (action) {
            ReaderAction.ToggleFullscreen -> window?.toggleFullscreen()
            ReaderAction.ToggleMenu -> viewModel.showMenus(!state.menuVisible)
            ReaderAction.NextChapter -> scope.launch { viewModel.loadNextChapter() }
            ReaderAction.PreviousChapter -> scope.launch { viewModel.loadPreviousChapter() }
            else -> scope.launch { controller?.perform(action) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ReaderBackground.fromPreference(background).color())
            .testTag(READER_TAG)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val action = readerActionFor(event.key, event.isShiftPressed, event.isCtrlPressed, event.isAltPressed, mode)
                    ?: return@onKeyEvent false
                perform(action)
                true
            },
    ) {
        val chapters = state.viewerChapters
        when {
            state.initError != null -> Unit // Task 7: the error screen with retry and open in browser
            chapters == null -> LoadingScreen()
            else -> key(chapters.currChapter, mode) {
                PagerViewer(
                    chapters = chapters,
                    mode = mode,
                    dual = isDualPage(dualView),
                    shifted = shifted,
                    scale = ImageScale.fromPreference(scale),
                    cache = cache,
                    onPageSelected = viewModel::onPageSelected,
                    onPastEnd = { forward ->
                        scope.launch { if (forward) viewModel.loadNextChapter() else viewModel.loadPreviousChapter() }
                    },
                    onTap = ::perform,
                    onRetry = viewModel::retryPage,
                    onController = { controller = it },
                )
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Mihon's dualPageView: never, always, or when the window is wider than tall. */
@Composable
private fun isDualPage(view: ReaderPreferences.DualPageView): Boolean = when (view) {
    ReaderPreferences.DualPageView.NEVER -> false
    ReaderPreferences.DualPageView.ALWAYS -> true
    ReaderPreferences.DualPageView.WIDE -> LocalWindowInfo.current.containerSize.let { it.width > it.height }
}
```

- La rama `state.initError != null` queda vacía hasta la Task 7.

En la Task 6, el `when` elige `WebtoonViewer` cuando `mode.type == ReadingMode.ViewerType.Webtoon`.

- [ ] **Step 10: Abrir el lector desde el manga**

Borrar `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderLauncher.kt` y las cadenas `desktop_reader_coming` de `base/strings.xml` y `es/strings.xml`.

En `eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`:
- `onChapterClicked = { ReaderLauncher.open(context, it.mangaId, it.id) }, // desktop: reader in plan 2` pasa a ser `onChapterClicked = { navigator.push(ReaderScreen(it.mangaId, it.id)) },`.
- `onContinueReading = { continueReading(context, viewModel.getNextUnreadChapter()) },` pasa a ser `onContinueReading = { continueReading(navigator, viewModel.getNextUnreadChapter()) },`.
- `continueReading` pasa a ser:

```kotlin
    private fun continueReading(navigator: Navigator, unreadChapter: Chapter?) {
        // desktop: the reader is a screen, not an Activity
        if (unreadChapter != null) navigator.push(ReaderScreen(unreadChapter.mangaId, unreadChapter.id))
    }
```

- Cambiar el import de `ReaderLauncher` por `mihon.desktop.app.reader.ReaderScreen`.
- Borrar `import android.content.Context` si deja de usarse.

- [ ] **Step 11: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.ReaderScreenTest" --tests "mihon.desktop.app.platform.*"`
Expected: PASS (3 + 3 tests).

Run: `./gradlew :desktop-app:test` y `bash scripts/check-no-android.sh`
Expected: PASS y `No direct Android imports in desktop-app`.

- [ ] **Step 12: Comprobación manual**

`./gradlew :desktop-app:run` y abrir un capítulo de MangaDex:
- izquierda a derecha y derecha a izquierda pasan con ←, →, la rueda y un clic en los tercios;
- doble clic hace zoom y lo hace también Ctrl+rueda;
- F11 alterna la pantalla completa y Esc sale de ella;
- un segundo Esc cierra el lector y deja el progreso guardado (el capítulo aparece leído hasta esa página).

- [ ] **Step 13: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add gradle/libs.versions.toml desktop-app i18n
git commit -m "feat(desktop-app): reader screen with the paged viewer, zoom and fullscreen

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Webtoon y doble página

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/WebtoonViewer.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/WebtoonAndDualTest.kt`

**Interfaces:**
- Consumes: `ViewerItem`, `viewerItems`, `ViewerController` y `PageContent` (Task 5).
- Produces: `@Composable fun WebtoonViewer(chapters, gap: Boolean, sidePaddingPercent: Int, cache, onPageSelected, onReachedEnd, onTap, onRetry, onController)`.

- [ ] **Step 1: Escribir el test (falla)**

`WebtoonAndDualTest.kt` (mismo arranque que `ReaderScreenTest`, con el modo por defecto en `WEBTOON` o en `LEFT_TO_RIGHT` y `dualPageView = ALWAYS`):

```kotlin
package mihon.desktop.app.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.window.WindowState
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.main.ConnectWindowBack
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.platform.WindowControls
import mihon.desktop.app.reader.viewer.WEBTOON_TAG
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class WebtoonAndDualTest {
    private object Below : Screen() {
        @Composable
        override fun Content() {
            Text("pantalla anterior")
        }
    }

    private fun ComposeUiTest.open(mode: ReadingMode, dual: ReaderPreferences.DualPageView) {
        val graph = startTestApp()
        TestSources.http.pageCount = 6
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        graph.readerPreferences.defaultReadingMode.set(mode.flagValue)
        graph.readerPreferences.dualPageView.set(dual)
        setContent {
            AppProviders(graph) {
                CompositionLocalProvider(LocalWindowControls provides WindowControls(WindowState())) {
                    ConnectWindowBack()
                    Navigator(listOf(Below, ReaderScreen(manga.id, chapters[0].id))) { DefaultNavigatorScreenTransition(it) }
                }
            }
        }
    }

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `webtoon shows the pages one under another and the end key reaches the last one`() = runComposeUiTest {
        open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
        waitForTag("page 1 ready")
        onNodeWithTag(WEBTOON_TAG).assertExists()
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.MoveEnd) }
        waitForTag("page 6")
    }

    @Test
    fun `dual page shows two portrait pages side by side`() = runComposeUiTest {
        open(ReadingMode.LEFT_TO_RIGHT, ReaderPreferences.DualPageView.ALWAYS)
        waitForTag("page 1 ready")
        waitForTag("page 2 ready")
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
        waitForTag("page 3")
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.WebtoonAndDualTest"`
Expected: FAIL de compilación: `Unresolved reference 'WEBTOON_TAG'`. El caso de doble página ya pasaría con el `PagerViewer` de la Task 5, que empareja; se queda como test de regresión del emparejamiento en pantalla.

- [ ] **Step 3: El visor webtoon**

`viewer/WebtoonViewer.kt`:

```kotlin
package mihon.desktop.app.reader.viewer

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition as TransitionModel
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.flow.distinctUntilChanged
import mihon.desktop.app.reader.ImageScale
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.reader.Spread
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.tapActionFor

const val WEBTOON_TAG = "webtoon"

/**
 * Webtoon (spec 2a §4): pages one under another with no space between them ([gap]: Mihon's continuous vertical, 16
 * dp), [sidePaddingPercent] of the width on each side. Reaching the way to the next chapter loads it.
 */
@Composable
fun WebtoonViewer(
    chapters: ViewerChapters,
    gap: Boolean,
    sidePaddingPercent: Int,
    cache: DecodedPageCache,
    onPageSelected: (ReaderPage) -> Unit,
    onReachedEnd: () -> Unit,
    onTap: (ReaderAction) -> Unit,
    onRetry: (ReaderPage) -> Unit,
    onController: (ViewerController) -> Unit,
) {
    val current = chapters.currChapter
    val pages = current.pages.orEmpty()
    val items = remember(chapters) { viewerItems(chapters, pages.indices.map { Spread.Single(it) }) }
    val latestItems by rememberUpdatedState(items)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = items.itemOfPage(current.requestedPage))

    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            val item = latestItems.getOrNull(index) as? ViewerItem.Pages ?: return@collect
            onPageSelected(item.pages.single())
            cache.retainAround(current.chapter.id, item.pages.single().index)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            val item = latestItems.getOrNull(last ?: return@collect) as? ViewerItem.Transition ?: return@collect
            if (item.transition is TransitionModel.Next && item.transition.to != null) onReachedEnd()
        }
    }
    val controller = remember(state) { WebtoonController(state) { latestItems } }
    LaunchedEffect(controller) { onController(controller) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth * sidePaddingPercent / 100
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().testTag(WEBTOON_TAG),
            contentPadding = PaddingValues(horizontal = side),
            verticalArrangement = Arrangement.spacedBy(if (gap) 16.dp else 0.dp),
        ) {
            itemsIndexed(items) { _, item ->
                when (item) {
                    is ViewerItem.Transition -> Box(
                        Modifier.fillMaxWidth().heightIn(min = 240.dp).pointerInput(item) {
                            detectTapGestures { onTap(tapActionFor(it.x, size.width.toFloat(), ReadingMode.WEBTOON)) }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        ChapterTransition(item.transition, currChapterDownloaded = false, goingToChapterDownloaded = false)
                    }
                    is ViewerItem.Pages -> PageContent(
                        page = item.pages.single(),
                        isCurrent = false,
                        scale = ImageScale.FIT_WIDTH,
                        cache = cache,
                        // spec deviation 5: no zoom on a scrolling list
                        zoom = false,
                        onTap = { x, width -> onTap(tapActionFor(x, width, ReadingMode.WEBTOON)) },
                        onRetry = onRetry,
                        onSize = {},
                        modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp),
                    )
                }
            }
        }
    }
}

private class WebtoonController(
    private val state: LazyListState,
    private val items: () -> List<ViewerItem>,
) : ViewerController {
    override suspend fun perform(action: ReaderAction) {
        val viewport = state.layoutInfo.viewportSize.height.toFloat()
        when (action) {
            ReaderAction.NextPage -> state.animateScrollBy(viewport * 0.9f)
            ReaderAction.PreviousPage -> state.animateScrollBy(-viewport * 0.9f)
            ReaderAction.ScrollDown -> state.animateScrollBy(viewport * 0.15f)
            ReaderAction.ScrollUp -> state.animateScrollBy(-viewport * 0.15f)
            ReaderAction.FirstPage -> state.scrollToItem(1)
            ReaderAction.LastPage -> state.scrollToItem((items().lastIndex - 1).coerceAtLeast(0))
            else -> Unit
        }
    }

    override suspend fun goToPage(index: Int) {
        state.scrollToItem(items().itemOfPage(index))
    }
}
```

`heightIn(min = 400.dp)` evita que una página que todavía carga ocupe 0 px. Sin eso, la lista intentaría componer decenas de páginas vacías de golpe.

- [ ] **Step 4: Elegir el visor según el modo**

En `ReaderContent`, la rama `else -> key(chapters.currChapter, mode) { PagerViewer(...) }` pasa a ser:

```kotlin
            else -> key(chapters.currChapter, mode) {
                if (mode.type == ReadingMode.ViewerType.Webtoon) {
                    val sidePadding by preferences.webtoonSidePadding.collectAsState()
                    WebtoonViewer(
                        chapters = chapters,
                        gap = mode == ReadingMode.CONTINUOUS_VERTICAL,
                        sidePaddingPercent = sidePadding,
                        cache = cache,
                        onPageSelected = viewModel::onPageSelected,
                        onReachedEnd = { scope.launch { viewModel.loadNextChapter() } },
                        onTap = ::perform,
                        onRetry = viewModel::retryPage,
                        onController = { controller = it },
                    )
                } else {
                    PagerViewer(
                        chapters = chapters,
                        mode = mode,
                        dual = isDualPage(dualView),
                        shifted = shifted,
                        scale = ImageScale.fromPreference(scale),
                        cache = cache,
                        onPageSelected = viewModel::onPageSelected,
                        onPastEnd = { forward ->
                            scope.launch { if (forward) viewModel.loadNextChapter() else viewModel.loadPreviousChapter() }
                        },
                        onTap = ::perform,
                        onRetry = viewModel::retryPage,
                        onController = { controller = it },
                    )
                }
            }
```

Imports: `mihon.desktop.app.reader.viewer.WebtoonViewer`.

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add desktop-app
git commit -m "feat(desktop-app): webtoon viewer and dual pages

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Controles, panel rápido, errores del capítulo y clic derecho en una página

**Files:**
- Create (copias sin cambios): `eu/kanade/presentation/reader/appbars/ReaderTopBar.kt`, `eu/kanade/presentation/reader/components/ChapterNavigator.kt`, `eu/kanade/presentation/reader/ReaderPageIndicator.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderControls.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderQuickSettings.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/PageActions.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/PageContent.kt` (clic derecho)
- Modify: `i18n/.../base/strings.xml`, `.../es/strings.xml`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/ReaderControlsTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/PageActionsTest.kt`

**Interfaces:**
- Consumes: `ReaderContent` y `PageContent` (Task 5); `FileDialogs` y `copyToClipboard` (plan 1).
- Produces:
  - `@Composable fun ReaderControls(...)`.
  - `@Composable fun ReaderQuickSettings(...)`.
  - `object PageActions { fun toPng(bytes: ByteArray): ByteArray; fun copyImage(bytes: ByteArray); fun extensionOf(bytes: ByteArray): String }`.
  - Los parámetros nuevos `onSave: (ReaderPage) -> Unit` y `onCopy: (ReaderPage) -> Unit` de `PageContent`.

- [ ] **Step 1: Cadenas nuevas**

En el bloque `<!-- Desktop -->` de `base/strings.xml`:

```xml
    <string name="desktop_dual_page_shift">Shift by one page</string>
    <string name="desktop_reader_preload_pages">Pages loaded ahead</string>
    <string name="desktop_image_copied">Image copied</string>
```

Y en `es/strings.xml`:

```xml
    <string name="desktop_dual_page_shift">Desplazar una página</string>
    <string name="desktop_reader_preload_pages">Páginas que se cargan por adelantado</string>
    <string name="desktop_image_copied">Imagen copiada</string>
```

- [ ] **Step 2: Escribir los tests (fallan)**

`PageActionsTest.kt`:

```kotlin
package mihon.desktop.app.reader

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PageActionsTest {
    private fun image(format: String): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(30, 40, BufferedImage.TYPE_INT_RGB), format, it)
    }.toByteArray()

    @Test
    fun `any page image becomes a PNG the clipboard can take`() {
        ImageIO.read(PageActions.toPng(image("jpg")).inputStream()).shouldNotBeNull().height shouldBe 40
    }

    @Test
    fun `the saved file keeps the image's real format`() {
        PageActions.extensionOf(image("png")) shouldBe "png"
        PageActions.extensionOf(image("jpg")) shouldBe "jpg"
        PageActions.extensionOf("?".toByteArray()) shouldBe "png"
    }
}
```

`ReaderControlsTest.kt` (mismo arranque que `ReaderScreenTest`; se puede copiar su función `openReader` a este archivo):

```kotlin
package mihon.desktop.app.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.window.WindowState
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.main.ConnectWindowBack
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.platform.WindowControls
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import java.io.IOException

@OptIn(ExperimentalTestApi::class)
class ReaderControlsTest {
    private object Below : Screen() {
        @Composable
        override fun Content() {
            Text("pantalla anterior")
        }
    }

    private fun ComposeUiTest.open(failPageList: Boolean = false) {
        val graph = startTestApp()
        TestSources.http.pageCount = 5
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        graph.readerPreferences.defaultReadingMode.set(ReadingMode.LEFT_TO_RIGHT.flagValue)
        if (failPageList) TestSources.http.failPageList = IOException("Sin páginas de José")
        setContent {
            AppProviders(graph) {
                CompositionLocalProvider(LocalWindowControls provides WindowControls(WindowState())) {
                    ConnectWindowBack()
                    Navigator(listOf(Below, ReaderScreen(manga.id, chapters[0].id))) { DefaultNavigatorScreenTransition(it) }
                }
            }
        }
    }

    private fun ComposeUiTest.waitFor(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `a click in the middle shows the controls with the manga and chapter`() = runComposeUiTest {
        open()
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performMouseInput { click(center) }
        waitFor("Capítulo 1")
        onNodeWithText("Manga HTTP", substring = true).assertExists()
    }

    @Test
    fun `switching the reading mode keeps the page being read`() = runComposeUiTest {
        open()
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
        }
        waitForTag("page 3 ready")

        onNodeWithTag(READER_TAG).performMouseInput { click(center) }
        onNodeWithTag(QUICK_SETTINGS_TAG).performClick()
        onNodeWithText(str(MR.strings.vertical_viewer)).performClick()

        waitForTag("page 3 ready")
    }

    @Test
    fun `a chapter whose page list fails offers retry and opening it in the browser`() = runComposeUiTest {
        open(failPageList = true)
        waitFor("José")
        onNodeWithText(str(MR.strings.action_open_in_browser)).assertExists()
        onNodeWithText(str(MR.strings.action_retry)).performClick()
        waitForTag("page 1 ready")
    }
}
```

- [ ] **Step 3: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.ReaderControlsTest" --tests "mihon.desktop.app.reader.PageActionsTest"`
Expected: FAIL de compilación: `Unresolved reference 'PageActions'` y `'QUICK_SETTINGS_TAG'`.

- [ ] **Step 4: Acciones sobre una página**

`PageActions.kt`:

```kotlin
package mihon.desktop.app.reader

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** Right click on a page (spec 2a §4): save it as it came, or copy it as an image. */
object PageActions {
    /** The page as PNG: AWT can't read WebP, Skia can. */
    fun toPng(bytes: ByteArray): ByteArray = Image.makeFromEncoded(bytes).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encoding failed")
    }

    fun copyImage(bytes: ByteArray) {
        val image = ImageIO.read(toPng(bytes).inputStream()) ?: error("Not an image")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(image), null)
    }

    /** The extension to save the page with, from the file's signature. */
    fun extensionOf(bytes: ByteArray): String = when {
        bytes.startsWith(0xFF, 0xD8) -> "jpg"
        bytes.startsWith('G'.code, 'I'.code, 'F'.code) -> "gif"
        bytes.size > 12 && String(bytes, 8, 4) == "WEBP" -> "webp"
        bytes.startsWith('B'.code, 'M'.code) -> "bmp"
        else -> "png"
    }

    private fun ByteArray.startsWith(vararg values: Int) =
        size >= values.size && values.indices.all { this[it] == values[it].toByte() }

    private class ImageSelection(private val image: BufferedImage) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any =
            if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
    }
}
```

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.PageActionsTest"`
Expected: PASS (2 tests).

En `PageContent`, añadir los parámetros `onSave: (ReaderPage) -> Unit = {}` y `onCopy: (ReaderPage) -> Unit = {}`. Envolver la rama de la imagen decodificada en:

```kotlin
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem(saveLabel) { onSave(page) },
                            ContextMenuItem(copyLabel) { onCopy(page) },
                        )
                    },
                ) {
                    DecodedPageImage(
                        result.getOrThrow(),
                        if (zoom) scale.contentScale else ContentScale.FillWidth,
                        pageModifier,
                    )
                }
```

con `val saveLabel = stringResource(MR.strings.action_save)` y `val copyLabel = stringResource(MR.strings.action_copy_to_clipboard)` al principio de la función. Los imports son `androidx.compose.foundation.ContextMenuArea` y `ContextMenuItem`. `PagerViewer` y `WebtoonViewer` reciben `onSave` y `onCopy` y se los pasan a `PageContent`.

- [ ] **Step 5: Controles y panel rápido**

Copiar sin cambios `ReaderTopBar.kt`, `ChapterNavigator.kt` y `ReaderPageIndicator.kt` de `app/src/main/java/eu/kanade/presentation/reader/`. Si alguno de sus `@Preview` no compila, borrar esa función con `// desktop: Android-only preview annotation`.

`ReaderQuickSettings.kt`:

```kotlin
package mihon.desktop.app.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/** The reader's quick settings (spec 2a §4): mode for this manga, scale, background and dual pages. */
@Composable
fun ReaderQuickSettings(
    preferences: ReaderPreferences,
    mode: ReadingMode,
    shifted: Boolean,
    onModeChange: (ReadingMode) -> Unit,
    onShiftChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val scale by preferences.imageScaleType.collectAsState()
    val background by preferences.readerTheme.collectAsState()
    val dual by preferences.dualPageView.collectAsState()
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp, modifier = Modifier.widthIn(max = 560.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Group(MR.strings.pref_viewer_type) {
                    ReadingMode.entries.filter { it != ReadingMode.DEFAULT }.forEach {
                        Chip(it.stringRes, selected = it == mode) { onModeChange(it) }
                    }
                }
                Group(MR.strings.pref_image_scale_type) {
                    ImageScale.entries.forEach {
                        Chip(it.label, selected = it == ImageScale.fromPreference(scale)) { preferences.imageScaleType.set(it.prefValue) }
                    }
                }
                Group(MR.strings.pref_reader_theme) {
                    ReaderBackground.entries.forEach {
                        Chip(it.label, selected = it == ReaderBackground.fromPreference(background)) {
                            preferences.readerTheme.set(it.prefValue)
                        }
                    }
                }
                if (mode.direction == ReadingMode.Direction.Horizontal) {
                    Group(MR.strings.pref_dual_page_view) {
                        ReaderPreferences.DualPageView.entries.forEach {
                            Chip(it.titleRes, selected = it == dual) { preferences.dualPageView.set(it) }
                        }
                        if (dual != ReaderPreferences.DualPageView.NEVER) {
                            Chip(MR.strings.desktop_dual_page_shift, selected = shifted) { onShiftChange(!shifted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: StringResource, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Chip(label: StringResource, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}
```

`ReaderControls.kt`:

```kotlin
package mihon.desktop.app.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import eu.kanade.presentation.reader.ReaderPageIndicator
import eu.kanade.presentation.reader.appbars.ReaderTopBar
import eu.kanade.presentation.reader.components.ChapterNavigator
import eu.kanade.presentation.reader.components.ChapterNavigatorType
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Settings
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

const val QUICK_SETTINGS_TAG = "reader quick settings"

/** Top bar (manga and chapter), bottom bar (page slider, chapters, settings) and the page number (spec 2a §4). */
@Composable
fun ReaderControls(
    state: ReaderViewModel.State,
    mode: ReadingMode,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenInBrowser: (() -> Unit)?,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPageIndexChange: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val chapters = state.viewerChapters
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(state.menuVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopBar(
                mangaTitle = state.manga?.title,
                chapterTitle = state.currentChapter?.chapter?.name,
                navigateUp = onBack,
                bookmarked = state.bookmarked,
                onToggleBookmarked = onToggleBookmark,
                onOpenInWebView = null,
                onOpenInBrowser = onOpenInBrowser,
                onShare = null,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            )
        }
        AnimatedVisibility(state.menuVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth()) {
                ChapterNavigator(
                    type = if (mode == ReadingMode.RIGHT_TO_LEFT) ChapterNavigatorType.HORIZONTAL_RTL else ChapterNavigatorType.HORIZONTAL_LTR,
                    onNextChapter = onNextChapter,
                    enabledNext = chapters?.nextChapter != null,
                    onPreviousChapter = onPreviousChapter,
                    enabledPrevious = chapters?.prevChapter != null,
                    currentPage = state.currentPage.coerceAtLeast(1),
                    totalPages = state.totalPages.coerceAtLeast(1),
                    onPageIndexChange = onPageIndexChange,
                    onPageIndexChangeFinished = {},
                )
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag(QUICK_SETTINGS_TAG)) {
                        Icon(MaterialSymbols.Rounded.Settings, contentDescription = stringResource(MR.strings.action_settings))
                    }
                }
            }
        }
        if (!state.menuVisible && state.totalPages > 0) {
            ReaderPageIndicator(
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
```

Las firmas de `ReaderTopBar`, `ChapterNavigator` y `ReaderPageIndicator` se toman de los archivos copiados. Si `ReaderPageIndicator` o `ChapterNavigator` tienen otros nombres de parámetro en esta versión de Mihon, se usan los suyos. `ChapterNavigatorType.HORIZONTAL_LTR` y `HORIZONTAL_RTL` existen en `ChapterNavigator.kt`. Si `MR.strings.action_settings` no existe, se usa `MR.strings.label_settings`.

- [ ] **Step 6: La pantalla del lector usa los controles, el panel y la pantalla de error**

En `ReaderContent`:
- Añadir `var showSettings by remember { mutableStateOf(false) }` y `val context = LocalContext.current`.
- Ocultar los controles tras 3 s sin mover el ratón:

```kotlin
    var lastMouseMove by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.menuVisible, lastMouseMove) {
        if (state.menuVisible) {
            delay(3.seconds)
            viewModel.showMenus(false)
        }
    }
```

  y, en la cadena de modificadores del `Box` raíz, `.onPointerEvent(PointerEventType.Move) { lastMouseMove = System.nanoTime() }`.
- La rama `state.initError != null -> Unit` pasa a ser:

```kotlin
            state.initError != null -> {
                val error = state.initError!!
                val url = viewModel.getChapterUrl()
                EmptyScreen(
                    message = with(context) { error.formattedMessage },
                    actions = listOfNotNull(
                        EmptyScreenAction(MR.strings.action_retry, MaterialSymbols.Rounded.Refresh) { viewModel.retryInit() },
                        url?.let {
                            EmptyScreenAction(MR.strings.action_open_in_browser, MaterialSymbols.Rounded.Public) {
                                context.openInBrowser(it)
                            }
                        },
                    ),
                )
            }
```

  `getChapterUrl()` (Task 2) también da la URL cuando la carga falló, a partir de la lista de capítulos.
- Después del `when`, todavía dentro del `Box`:

```kotlin
        ReaderControls(
            state = state,
            mode = mode,
            onBack = onClose,
            onToggleBookmark = viewModel::toggleChapterBookmark,
            onOpenInBrowser = viewModel.getChapterUrl()?.let { url -> { context.openInBrowser(url) } },
            onPreviousChapter = { scope.launch { viewModel.loadPreviousChapter() } },
            onNextChapter = { scope.launch { viewModel.loadNextChapter() } },
            onPageIndexChange = { index -> scope.launch { controller?.goToPage(index) } },
            onOpenSettings = { showSettings = true },
        )
        if (showSettings) {
            ReaderQuickSettings(
                preferences = preferences,
                mode = mode,
                shifted = shifted,
                onModeChange = viewModel::setMangaReadingMode,
                onShiftChange = { shifted = it },
                onDismissRequest = { showSettings = false },
            )
        }
```

- Guardar y copiar una página. Se pasa a `PagerViewer` y a `WebtoonViewer`:

```kotlin
        onSave = { page -> savePage(context, state, page) },
        onCopy = { page ->
            scope.launch(Dispatchers.IO) {
                page.stream?.let { PageActions.copyImage(it().use { s -> s.readBytes() }) }
                context.toast(MR.strings.desktop_image_copied)
            }
        },
```

  con, en el mismo archivo:

```kotlin
private fun savePage(context: Context, state: ReaderViewModel.State, page: ReaderPage) {
    val bytes = page.stream?.invoke()?.use { it.readBytes() } ?: return
    val name = listOfNotNull(state.manga?.title, state.currentChapter?.chapter?.name, page.number.toString())
        .joinToString(" - ")
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
    val file = FileDialogs.saveFile(context.stringResource(MR.strings.action_save), "$name.${PageActions.extensionOf(bytes)}")
        ?: return
    file.writeBytes(bytes)
    context.toast(MR.strings.picture_saved)
}
```

  `ReaderScreen.kt` contiene `\\`: se escribe con la herramienta Write.

Imports: `android.content.Context`, `androidx.compose.ui.platform.LocalContext`, `androidx.compose.ui.input.pointer.PointerEventType`, `androidx.compose.ui.input.pointer.onPointerEvent`, `androidx.compose.runtime.mutableLongStateOf`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.Dispatchers`, `kotlin.time.Duration.Companion.seconds`, `eu.kanade.presentation.util.formattedMessage`, `eu.kanade.tachiyomi.util.system.openInBrowser`, `eu.kanade.tachiyomi.util.system.toast`, `eu.kanade.tachiyomi.ui.reader.model.ReaderPage`, `mihon.desktop.app.platform.FileDialogs`, `mihon.icons.materialsymbols.rounded.Public`, `mihon.icons.materialsymbols.rounded.Refresh`, `tachiyomi.core.common.i18n.stringResource`, `tachiyomi.i18n.MR`, `tachiyomi.presentation.core.screens.EmptyScreen` y `tachiyomi.presentation.core.screens.EmptyScreenAction`.

- [ ] **Step 7: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.*"`
Expected: PASS.

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS. Si lista cadenas de Mihon que usan los archivos copiados, traducirlas en el bloque de traducciones de `es/strings.xml`.

- [ ] **Step 8: Comprobación manual**

`./gradlew :desktop-app:run`, abrir un capítulo de MangaDex:
- un clic en el centro muestra las barras, que se ocultan solas a los 3 s;
- el deslizador salta de página;
- capítulo anterior y siguiente;
- desde el panel rápido, cambiar a webtoon y a doble página;
- clic derecho › Guardar escribe la imagen con su extensión, y Copiar la deja en el portapapeles (pegarla en Paint).

- [ ] **Step 9: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add desktop-app i18n
git commit -m "feat(desktop-app): reader controls, quick settings, chapter errors and page save/copy

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Historial

**Files:**
- Create (copias sin cambios): `eu/kanade/presentation/history/HistoryScreen.kt`, `HistoryviewModelStateProvider.kt`, `components/HistoryDialogs.kt`, `components/HistoryItem.kt`, `components/HistoryWithRelationsProvider.kt`; `eu/kanade/tachiyomi/ui/history/HistoryViewModel.kt`
- Create (copia con cambios): `eu/kanade/tachiyomi/ui/history/HistoryTab.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/HomeScreen.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt` (`upsertHistory`)
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/history/HistoryTabTest.kt`

**Interfaces:**
- Consumes: `ReaderScreen` (Task 5); `GetHistory`, `RemoveHistory`, `GetNextChapters` y `UpsertHistory` (`domain`).
- Produces: `object HistoryTab : Tab`. El rail queda `HomeScreen.tabs = listOf(BrowseTab, HistoryTab, SettingsTab)`.

- [ ] **Step 1: Escribir el test (falla)**

`HistoryTabTest.kt`:

```kotlin
package mihon.desktop.app.history

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.reader.READER_TAG
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.i18n.MR
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class HistoryTabTest {
    @Test
    fun `a read chapter is listed, resumes in the reader and can be removed`() = runComposeUiTest {
        val graph = startTestApp()
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        runBlocking { graph.upsertHistory.await(HistoryUpdate(chapters[0].id, Clock.System.now(), 60_000)) }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_recent_manga)).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(manga.title)).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithContentDescription(str(MR.strings.action_delete)).performClick()
        onAllNodesWithText(str(MR.strings.action_remove)).onLast().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(manga.title)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `clicking an entry continues reading in the reader`() = runComposeUiTest {
        val graph = startTestApp()
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        runBlocking { graph.upsertHistory.await(HistoryUpdate(chapters[0].id, Clock.System.now(), 60_000)) }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_recent_manga)).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(manga.title)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(manga.title).performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasTestTag(READER_TAG)).fetchSemanticsNodes().isNotEmpty() }
    }
}
```

Añadir a `TestAppGraph` el accesor `val upsertHistory: UpsertHistory`.

En `HistoryItem`, el clic en el título (la fila) llama a `onClickResume`, y el clic en la portada, a `onClickCover`. Si al copiar resulta que el clic en el título abre los detalles, el segundo test hace clic en el botón de continuar de la fila (su `contentDescription`, según `HistoryItem.kt`). Anotarlo.

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.history.HistoryTabTest"`
Expected: FAIL. No hay entrada "Historial" en el rail (`No node … label_recent_manga`).

- [ ] **Step 3: Portar el historial**

Copiar sin cambios los cinco archivos de `eu/kanade/presentation/history/` y `eu/kanade/tachiyomi/ui/history/HistoryViewModel.kt`. Si algún `@PreviewLightDark` o `@PreviewParameter` no compila con CMP, borrar esa función de preview y los proveedores que solo usaba, con `// desktop: Android-only preview annotation`.

`HistoryTab.kt`: copiar con estos cambios:
- `options`: borrar `isSelected` y el icono animado (`R.drawable.animated_history`, `animatedVectorResource` y `rememberAnimatedVectorPainter`). Poner `icon = rememberVectorPainter(MaterialSymbols.Rounded.Schedule), // desktop: no Android animated drawables`.
- En `Content()`:
  - Borrar las ramas `ChangeCategory` y `Migrate` del `when (dialog)` y añadir `else -> {}`.
  - En `DuplicateMangaDialog`, pasar `onMigrate = null`.
  - Borrar el `LaunchedEffect(state.list)` que usa `MainActivity`.
- `openChapter(context, chapter)` pasa a ser `openChapter(navigator, context, chapter)`, con el cuerpo:

```kotlin
    private suspend fun openChapter(navigator: Navigator, context: Context, chapter: Chapter?) {
        if (chapter != null) {
            // desktop: the reader is a screen, not an Activity
            navigator.push(ReaderScreen(chapter.mangaId, chapter.id))
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }
```

  Las dos llamadas que tiene pasan `navigator`.
- Borrar los imports de `R`, `ChangeCategoryDialog`, `CategoryScreen`, `MainActivity`, `ReaderActivity`, `MigrateMangaDialog`, `AnimatedImageVector`, `animatedVectorResource` y `rememberAnimatedVectorPainter`.
- Añadir `rememberVectorPainter`, `MaterialSymbols`, `mihon.icons.materialsymbols.rounded.Schedule` y `mihon.desktop.app.reader.ReaderScreen`.

En `HomeScreen.kt`: `val tabs: List<Tab> = listOf(BrowseTab, HistoryTab, SettingsTab)` (import `eu.kanade.tachiyomi.ui.history.HistoryTab`). El comentario de `tabs` dice que Ajustes debe quedar la última, y así se mantiene.

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.history.HistoryTabTest"` y `./gradlew :desktop-app:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add desktop-app i18n
git commit -m "feat(desktop-app): history tab that resumes in the reader

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Ajustes del lector y borrado de la caché de capítulos

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsReaderScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsTab.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/AdvancedSettingsScreen.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/settings/SettingsTest.kt` (un caso nuevo)

**Interfaces:**
- Consumes: `ReaderPreferences`, `ReadingMode`, `ImageScale`, `ReaderBackground`, `AppGraph.readerPreferences` y `AppGraph.chapterCache`; el modelo `Preference` del plan 1.
- Produces: `object SettingsReaderScreen : SearchableSettings`.

- [ ] **Step 1: Escribir el test (falla)**

En `SettingsTest`, añadir:

```kotlin
    @Test
    fun `the reader's default mode is chosen in the settings`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_settings)).performClick()
        waitForText(str(MR.strings.pref_category_reader))
        onNodeWithText(str(MR.strings.pref_category_reader)).performClick()
        waitForText(str(MR.strings.pref_viewer_type))
        onNodeWithText(str(MR.strings.pref_viewer_type)).performClick()
        onNodeWithText(str(MR.strings.webtoon_viewer)).performClick()
        waitForIdle()

        graph.readerPreferences.defaultReadingMode.get() shouldBe ReadingMode.WEBTOON.flagValue
    }
```

(imports `io.kotest.matchers.shouldBe` y `eu.kanade.tachiyomi.ui.reader.setting.ReadingMode`).

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.settings.SettingsTest"`
Expected: FAIL. No hay entrada "Lector" en Ajustes.

- [ ] **Step 3: Pantalla de ajustes del lector**

`SettingsReaderScreen.kt`:

```kotlin
package mihon.desktop.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import mihon.app.di.appGraph
import mihon.desktop.app.reader.ImageScale
import mihon.desktop.app.reader.ReaderBackground
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** The reader's defaults (spec 2a §3, Ajustes › Lector); the reader's quick panel changes the same preferences. */
object SettingsReaderScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val preferences = remember { context.appGraph.readerPreferences }
        return listOf(
            Preference.PreferenceItem.ListPreference(
                preference = preferences.defaultReadingMode,
                entries = ReadingMode.entries.filter { it != ReadingMode.DEFAULT }
                    .associate { it.flagValue to stringResource(it.stringRes) },
                title = stringResource(MR.strings.pref_viewer_type),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = preferences.imageScaleType,
                entries = ImageScale.entries.associate { it.prefValue to stringResource(it.label) },
                title = stringResource(MR.strings.pref_image_scale_type),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = preferences.readerTheme,
                entries = ReaderBackground.entries.associate { it.prefValue to stringResource(it.label) },
                title = stringResource(MR.strings.pref_reader_theme),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = preferences.dualPageView,
                entries = ReaderPreferences.DualPageView.entries.associateWith { stringResource(it.titleRes) },
                title = stringResource(MR.strings.pref_dual_page_view),
            ),
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.webtoonSidePadding,
                title = stringResource(MR.strings.pref_webtoon_side_padding),
                valueText = { "$it %" },
                valueRange = ReaderPreferences.WEBTOON_PADDING_MIN..ReaderPreferences.WEBTOON_PADDING_MAX,
            ),
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.preloadPages,
                title = stringResource(MR.strings.desktop_reader_preload_pages),
                valueRange = 1..10,
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.skipRead,
                title = stringResource(MR.strings.pref_skip_read_chapters),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.skipFiltered,
                title = stringResource(MR.strings.pref_skip_filtered_chapters),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.skipDupe,
                title = stringResource(MR.strings.pref_skip_dupe_chapters),
            ),
        )
    }
}
```

En `SettingsTab`, la lista `entries` pasa a ser:
1. Apariencia;
2. Explorar;
3. `Triple(MR.strings.pref_category_reader, MaterialSymbols.Rounded.Image, SettingsReaderScreen)`;
4. Avanzado.

Import `mihon.icons.materialsymbols.rounded.Image`.

En `AdvancedSettingsScreen`, la acción "Borrar caché de imágenes" también vacía la caché de capítulos. Antes del `context.toast(...)`, añadir `context.appGraph.chapterCache.clear() // spec 2a §3: Avanzado › borrar caché`.

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.settings.SettingsTest" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS (3 + 1 tests). Si `SpanishStringsTest` lista cadenas, traducirlas.

- [ ] **Step 5: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add desktop-app i18n
git commit -m "feat(desktop-app): reader settings and clearing the chapter cache

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Regresiones, harness y prueba manual del subproyecto 2a

**Files:** ninguno nuevo. Se corrige lo que salga.

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: la verificación del spec 2a completo.

- [ ] **Step 1: Suite, formato, licencias e imports**

Run: `./gradlew test spotlessCheck > build/plan2-verify.txt; tail -20 build/plan2-verify.txt`
Expected: `BUILD SUCCESSFUL`. Siguen pasando los 223 tests del plan 1 y pasan los nuevos.

Run: `bash scripts/check-licenses.sh` y `bash scripts/check-no-android.sh`
Expected: código 0 en ambos y `No direct Android imports in desktop-app`. El jar de zoomable es Apache-2.0: si el chequeo de licencias no lo reconoce, añadirlo a su lista de licencias permitidas como Apache-2.0.

- [ ] **Step 2: Harness**

Run: `./gradlew :compat-harness:run --args="--repo https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb --limit 100 --threshold 0.8"`
Expected: ≥ 80 %. La última medida fue 89,4 %. Este plan no toca la carga de extensiones.

- [ ] **Step 3: Prueba manual del spec 2a (sección 6)**

Con `MANGAREADER_HOME` apuntando a una carpeta nueva:
1. `gradlew :desktop-app:run`; añadir el repositorio de keiyoushi e instalar MangaDex.
2. Buscar, abrir un manga y leer un capítulo en cada modo: izquierda a derecha, derecha a izquierda, vertical, webtoon y doble página (desde el panel rápido).
3. Zoom con doble clic y con Ctrl+rueda; F11.
4. Cerrar la ventana a mitad de capítulo y reabrir. Comprobar que se conservan:
   - el progreso: el manga muestra el capítulo empezado y "Reanudar" abre esa página;
   - el historial: aparece en la pestaña Historial;
   - la ventana: misma posición, y no en pantalla completa aunque se cerrara en pantalla completa.
5. Abrir un webtoon con imágenes muy largas (en MangaDex, cualquier manhwa a color): sin cortes ni errores de textura. Mirar el uso de memoria en el Administrador de tareas mientras se baja por 3 capítulos: no debe crecer sin límite.
6. Lanzar una segunda instancia: enfoca la primera.

Anotar en el ledger el resultado de cada paso.

- [ ] **Step 4: Commit (si hubo correcciones)**

```bash
git add -A
git commit -m "fix(desktop-app): <lo que corrigió la verificación>

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

## Después de este plan

Con este plan termina el subproyecto 2a. Lo siguiente:
- **2b:** KCEF para Cloudflare, el WebView de las extensiones y los logins.
- **3:** biblioteca, novedades, categorías y migración. Las categorías y la migración devuelven los diálogos quitados del historial y del manga.
