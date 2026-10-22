# Subproyecto 3b: migración entre fuentes, Próximos e intervalo por manga. Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La app gana la migración de mangas entre fuentes, con todas las entradas de Mihon, la pestaña Migrar de Explorar, la pantalla Próximos con su calendario y el intervalo de actualización editable por manga.

**Architecture:**
- **Portado.** Los archivos de Mihon se copian con su paquete, como en 2a y 3a. Lo único nuevo es:
  - `WindowSize.kt` de `mihon.core.designsystem.utils`, que mide la ventana en lugar de la configuración Android;
  - los métodos que faltan en el stub de trackers.
- **Migración, en tres capas:**
  1. `MigrateMangaUseCase` copia los datos de un manga a otro.
  2. `MigrationListViewModel` busca coincidencias con `SmartSourceSearchEngine`.
  3. Las pantallas (config, lista, búsqueda manual, diálogo) llaman a esas dos piezas.
- **Próximos** lee `GetUpcomingManga`, que ya está en `domain`.
- **Intervalo por manga.** Reutiliza `SetIntervalDialog` y `MangaViewModel.setFetchInterval`, que ya están portados. Esta tarea solo los conecta y quita la condición `!isReleaseBuildType`.

**Tech Stack:**
- Kotlin 2.4.21 y JDK 21.
- Compose Multiplatform 1.12.2, Voyager y Metro 1.4.5 (`metrox` viewmodel).
- SQLDelight (BD de Mihon) y kotlinx-coroutines.
- `com.aallam.similarity:string-similarity-kotlin` 0.1.0 (nuevo en desktop-app) y `com.cheonjaeung.compose.grid` 2.9.0 (ya está).
- JUnit 5, kotest y `compose.uiTest`.

**Spec:** `docs/superpowers/specs/2026-10-21-subproyecto-3b-migracion-proximos-design.md`. Spec hermana: `docs/superpowers/specs/2026-10-21-subproyecto-3a-biblioteca-design.md`.

## Global Constraints

- **Portado de Mihon:**
  - Los archivos se copian de `app/src/main/java/<ruta>` a `desktop-app/src/main/kotlin/<ruta>`, con **el mismo paquete**.
  - Solo se cambia lo que dice el paso.
  - Cada cambio lleva un comentario `// desktop: <motivo>` en la línea modificada o justo encima.
- **Implementaciones nulas.**
  - Van en `desktop-app/src/stubs/kotlin/<paquete de Mihon>`, con la cabecera `// Null implementation until subproject <N>; replace with the Mihon port.`
  - Si el código portado necesita un miembro más, se añade ahí, devolviendo vacío.
- **Nada de `android.*` ni `androidx.*`** fuera de lo que permite `scripts/check-no-android.sh`.
  - Permitidos: `android.content.Context`, `android.widget.Toast`, `androidx.compose|lifecycle|paging|preference|navigationevent` y `androidx.activity.compose.BackHandler`.
  - `androidx.annotation.*` **no** está permitido.
- **Textos:**
  - se reutilizan las cadenas de Mihon (todas las de migración y Próximos existen ya en `i18n`);
  - `SpanishStringsTest` exige traducción española de cada `MR.strings.x` usado en `desktop-app/src/main` y `src/stubs`;
  - si falta alguna, se añade su `<string>` en `i18n/src/commonMain/moko-resources/es/strings.xml` con la misma clave de Mihon, junto a la inglesa equivalente.
- **Git:**
  - nunca `git push` al remoto `upstream` (mihonapp/mihon);
  - commits con el trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`;
  - el trabajo va en la rama `subproyecto-3b`, creada desde `master` con este plan ya commiteado.
- **Tests:** los que son `= runBlocking { }` declaran `: Unit`; si no, JUnit los salta.
- **Archivos con `\\`:** se escriben con la herramienta Write, no con heredoc de Bash.
- **Fuera de alcance** (spec §1):
  - borrar descargas al migrar (subproyecto 4);
  - migrar trackers de verdad y editar notas (subproyecto 5).

## Desviaciones del spec (aprobar antes de ejecutar)

1. **`HomeScreen.openTab(Browse)`** tras migrar desde `MigrateSourceSearchScreen`.
   - Mihon cambia a la pestaña Explorar antes de abrir la ficha del destino.
   - El `HomeScreen` del escritorio no tiene `openTab`. Se hace `popUntilRoot()` y se abre la ficha encima de la pestaña actual.
   - El usuario acaba en la misma ficha.

## Review Focus

1. **El destino ya está en la biblioteca, con su propia categoría.** Tras migrar con el flag de categorías, queda solo en las categorías del origen y sigue en la biblioteca. Test en la Task 1.
2. **El origen pertenece a una fuente desinstalada.** La migración funciona igual: el origen sale de la biblioteca y el destino entra. Test en la Task 1.
3. **El usuario marca como destino la misma fuente del origen** (por ejemplo, "Seleccionar todas"). Esa fuente no puede devolver el mismo manga como coincidencia: se salta y se usa la siguiente. Test en la Task 2.
4. **"Migrar todo" sin ninguna coincidencia.** No se migra nada, los mangas siguen en la biblioteca y la pantalla vuelve atrás. Test en la Task 2.
5. **Próximos con mangas completados o sin fecha.** Un manga completado o sin `nextUpdate` no sale aunque esté en la biblioteca. "Por defecto" en el intervalo vuelve al cálculo automático. Tests en las Tasks 5 y 6.

## Mapa de archivos

| Ruta (bajo `desktop-app/src/main/kotlin/` salvo indicación) | Responsabilidad |
|---|---|
| `mihon/domain/migration/usecases/MigrateMangaUseCase.kt` | Copiar capítulos, categorías, portada, notas y flags al destino (Mihon) |
| `mihon/feature/common/utils/MigrationFlag.kt` | `MigrationFlag.getLabel()` (Mihon) |
| `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/track/{Tracker,TrackerManager}.kt` | `EnhancedTracker` completo y `TrackerManager.trackers` vacíos |
| `mihon/feature/migration/list/**` | Lista de migración, ViewModel, búsqueda inteligente, diálogos (Mihon) |
| `mihon/feature/migration/config/**` | Configuración de la migración (Mihon) |
| `mihon/feature/migration/dialog/MigrateMangaDialog.kt` | Diálogo Migrar o Copiar de un manga (Mihon) |
| `eu/kanade/tachiyomi/ui/browse/migration/search/**`, `eu/kanade/presentation/browse/MigrateSearchScreen.kt` | Búsqueda manual del destino (Mihon) |
| `eu/kanade/tachiyomi/ui/browse/migration/{sources,manga}/**`, `eu/kanade/presentation/browse/MigrateSourceScreen.kt`, `eu/kanade/domain/source/interactor/GetSourcesWithFavoriteCount.kt`, `eu/kanade/presentation/manga/components/BaseMangaListItem.kt` | Pestaña Migrar de Explorar (Mihon) |
| `mihon/feature/upcoming/**` | Próximos (Mihon) |
| `mihon/core/designsystem/utils/WindowSize.kt` | `isMediumWidthWindow` / `isExpandedWidthWindow` según la ventana |
| `eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`, `ui/library/LibraryTab.kt`, `ui/history/HistoryTab.kt`, `ui/browse/source/browse/BrowseSourceScreen.kt`, `ui/updates/UpdatesTab.kt`, `mihon/desktop/app/main/BrowseTab.kt` | Ganchos "3b" conectados |
| `eu/kanade/presentation/manga/components/MangaDialogs.kt`, `eu/kanade/presentation/library/LibrarySettingsDialog.kt` | Intervalo fijo sin la condición de build |
| `desktop-app/src/test/kotlin/mihon/desktop/app/testing/{TestAppGraph,Strings,TestLibrary}.kt` | Accesores y ayudas de test |

---

### Task 1: `MigrateMangaUseCase`

**Files:**
- Modify: `desktop-app/build.gradle.kts` (añadir `implementation(libs.stringSimilarity)` tras `implementation(libs.reorderable)`; se usa en la Task 2, pero va aquí para que la rama compile de una vez)
- Modify: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/track/Tracker.kt`, `TrackerManager.kt`
- Copy: `mihon/domain/migration/usecases/MigrateMangaUseCase.kt`, `mihon/feature/common/utils/MigrationFlag.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/migration/MigrateMangaUseCaseTest.kt`

**Interfaces:**
- Consumes:
  - `UpdateMangaFromRemote.invoke(manga, fetchChapters = true): Result<RemoteMangaUpdate>`;
  - `CoverCache.setCustomCoverToCache(manga, InputStream)` y `getCustomCoverFile(id): File`;
  - `SourcePreferences.migrationFlags: Preference<Set<MigrationFlag>>`.
- Produces:
  - `MigrateMangaUseCase.invoke(current: Manga, target: Manga, replace: Boolean)` (suspend). `replace = true` migra y `false` copia;
  - `fun MigrationFlag.getLabel(): StringResource`;
  - en `TestAppGraph`: `migrateMangaUseCase`, `updateChapter: UpdateChapter`.

- [ ] **Step 0: Rama**

```bash
git checkout -b subproyecto-3b master
```

- [ ] **Step 1: Escribir los tests**

`desktop-app/src/test/kotlin/mihon/desktop/app/migration/MigrateMangaUseCaseTest.kt`:

```kotlin
package mihon.desktop.app.migration

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.domain.migration.models.MigrationFlag
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class MigrateMangaUseCaseTest {
    private val graph: TestAppGraph = startTestApp()

    /** A manga of [TestSources.fake] (five chapters once refreshed), not in the library yet. */
    private suspend fun target(sourceId: Long = TestSources.fake.id): Manga = graph.networkToLocalManga(
        Manga.create().copy(url = "/destino/${next.incrementAndGet()}", title = "Destino", source = sourceId),
    )

    private fun flags(vararg flags: MigrationFlag) = graph.sourcePreferences.migrationFlags.set(flags.toSet())

    @Test
    fun `chapters up to the last one read become read, and bookmarks and fetch dates carry over`(): Unit =
        runBlocking {
            flags(MigrationFlag.CHAPTER)
            val current = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
            val old = graph.getChaptersByMangaId.await(current.id).sortedBy { it.chapterNumber }
            graph.updateChapter.awaitAll(
                listOf(
                    ChapterUpdate(old[0].id) { read = true },
                    ChapterUpdate(old[1].id) {
                        read = true
                        bookmark = true
                    },
                ),
            )
            val target = target()

            graph.migrateMangaUseCase(current, target, replace = true)

            val migrated = graph.getChaptersByMangaId.await(target.id).sortedBy { it.chapterNumber }
            migrated.map { it.read } shouldContainExactly listOf(true, true, false, false, false)
            migrated.map { it.bookmark } shouldContainExactly listOf(false, true, false, false, false)
            migrated[1].dateFetch shouldBe old[1].dateFetch
        }

    @Test
    fun `categories and notes carry over only with their flag`(): Unit = runBlocking {
        val category = TestLibrary.category(graph)
        val current = TestLibrary.libraryManga(graph, category)
        graph.updateManga.await(MangaUpdate(current.id) { notes = "Mis notas" })
        val withNotes = graph.getManga.await(current.id)!!

        flags(MigrationFlag.CATEGORY, MigrationFlag.NOTES)
        val first = target()
        graph.migrateMangaUseCase(withNotes, first, replace = false)
        graph.getCategories.await(first.id).map { it.id } shouldContainExactly listOf(category)
        graph.getManga.await(first.id)!!.notes shouldBe "Mis notas"

        flags()
        val second = target()
        graph.migrateMangaUseCase(withNotes, second, replace = false)
        graph.getCategories.await(second.id).map { it.id } shouldContainExactly emptyList()
        graph.getManga.await(second.id)!!.notes shouldBe ""
    }

    @Test
    fun `a custom cover is copied to the target`(): Unit = runBlocking {
        flags(MigrationFlag.CUSTOM_COVER)
        val current = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        graph.coverCache.setCustomCoverToCache(current, "portada".byteInputStream())
        val target = target()

        graph.migrateMangaUseCase(current, target, replace = true)

        graph.coverCache.getCustomCoverFile(target.id).readText() shouldBe "portada"
    }

    @Test
    fun `migrating takes the origin out of the library, copying keeps both, and flags pass to the target`(): Unit =
        runBlocking {
            flags()
            val current = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
            graph.updateManga.await(
                MangaUpdate(current.id) {
                    chapterFlags = 0x100L
                    viewerFlags = 0x3L
                },
            )
            val origin = graph.getManga.await(current.id)!!

            val copied = target()
            graph.migrateMangaUseCase(origin, copied, replace = false)
            graph.getManga.await(origin.id)!!.favorite shouldBe true
            graph.getManga.await(copied.id)!!.favorite shouldBe true

            val migrated = target()
            graph.migrateMangaUseCase(origin, migrated, replace = true)
            graph.getManga.await(origin.id)!!.favorite shouldBe false
            graph.getManga.await(migrated.id)!!.let {
                it.favorite shouldBe true
                it.favoriteAt shouldBe origin.favoriteAt
                it.chapterFlags shouldBe 0x100L
                it.viewerFlags shouldBe 0x3L
            }
        }

    @Test
    fun `if refreshing the target fails, nothing changes`(): Unit = runBlocking {
        flags(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY)
        val current = TestLibrary.libraryManga(graph, TestLibrary.category(graph))
        val target = target(TestSources.updatable.id)
        TestSources.updatable.failing[target.url] = IOException("sin red")

        graph.migrateMangaUseCase(current, target, replace = true)

        graph.getManga.await(current.id)!!.favorite shouldBe true
        graph.getManga.await(target.id)!!.favorite shouldBe false
        graph.getCategories.await(target.id) shouldBe emptyList()
    }

    @Test
    fun `a target already in the library ends up only in the origin's categories`(): Unit = runBlocking {
        flags(MigrationFlag.CATEGORY)
        val originCategory = TestLibrary.category(graph)
        val current = TestLibrary.libraryManga(graph, originCategory)
        val target = TestLibrary.libraryManga(graph, TestLibrary.category(graph))

        graph.migrateMangaUseCase(current, target, replace = true)

        graph.getCategories.await(target.id).map { it.id } shouldContainExactly listOf(originCategory)
        graph.getManga.await(target.id)!!.favorite shouldBe true
    }

    @Test
    fun `an origin from an uninstalled source migrates all the same`(): Unit = runBlocking {
        flags(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY)
        val current = TestLibrary.libraryManga(graph, TestLibrary.category(graph), sourceId = 999L)
        val target = target()

        graph.migrateMangaUseCase(current, target, replace = true)

        graph.getManga.await(current.id)!!.favorite shouldBe false
        graph.getManga.await(target.id)!!.favorite shouldBe true
    }

    private companion object {
        val next = AtomicInteger()
    }
}
```

Añade a `TestAppGraph`, junto a los demás accesores e importando `mihon.domain.migration.usecases.MigrateMangaUseCase` y `tachiyomi.domain.chapter.interactor.UpdateChapter`:

```kotlin
    val migrateMangaUseCase: MigrateMangaUseCase
    val updateChapter: UpdateChapter
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.migration.MigrateMangaUseCaseTest"`
Expected: FAIL de compilación. No existe `mihon.domain.migration.usecases.MigrateMangaUseCase`.

- [ ] **Step 3: Stub de trackers**

En `Tracker.kt`, `EnhancedTracker` pasa a tener la forma de Mihon, que es la que usa `MigrateMangaUseCase`:

```kotlin
interface EnhancedTracker {
    fun accept(source: Source): Boolean

    fun isTrackFrom(track: Track, manga: Manga, source: Source?): Boolean = false

    fun migrateTrack(track: Track, manga: Manga, newSource: Source): Track? = null
}
```

Añade también `import tachiyomi.domain.manga.model.Manga`.

En `TrackerManager.kt`:

```kotlin
    val trackers: List<Tracker> = emptyList()
```

- [ ] **Step 4: Copiar y adaptar**

- `MigrationFlag.kt` (`mihon/feature/common/utils`): sin cambios.
- `MigrateMangaUseCase.kt`, en el `catch` final:

```kotlin
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            // desktop: Mihon drops the error silently; log it (spec 3b §1)
            logcat(LogPriority.ERROR, e) { "Migrating ${current.id} to ${target.id}" }
        }
```

  Con los imports `logcat.LogPriority` y `tachiyomi.core.common.util.system.logcat`.
- En `desktop-app/build.gradle.kts`, tras `implementation(libs.reorderable)`:

```kotlin
    implementation(libs.stringSimilarity)
```

- [ ] **Step 5: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.migration.MigrateMangaUseCaseTest"`
Expected: PASS, los 7.

- [ ] **Step 6: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): migrate-manga use case, with the tracker stub it needs

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Lista de migración y búsqueda inteligente

**Files:**
- Copy (de `app/src/main/java/` a `desktop-app/src/main/kotlin/`):
  - `mihon/feature/migration/list/MigrationListViewModel.kt`
  - `mihon/feature/migration/list/models/MigratingManga.kt`
  - `mihon/feature/migration/list/search/{BaseSmartSearchEngine,SmartSourceSearchEngine}.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/migration/MigrationListViewModelTest.kt`

**Interfaces:**
- Consumes: de la Task 1, `MigrateMangaUseCase`.
- Produces (Mihon, sin cambios de firma):
  - `MigrationListViewModel.Factory.create(mangaIds: Collection<Long>, extraSearchQuery: String?)`;
  - `state: StateFlow<State>`, con `items: List<MigratingManga>` y `finishedCount: Int`;
  - `navigateBackEvent: Flow<Unit>`;
  - `migrateMangas()`, `copyMangas()`, `migrateNow(id, replace)`, `useMangaForMigration(current, target, onMissingChapters)`;
  - `MigratingManga.searchResult: MutableStateFlow<SearchResult>`, con `Searching`, `NotFound` y `Success(manga, chapterCount, latestChapter, source)`;
  - en `TestAppGraph`: `migrationListViewModelFactory: MigrationListViewModel.Factory`.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.migration

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.FakeSource
import mihon.desktop.app.testing.FakeSourceManager
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.feature.migration.list.MigrationListViewModel
import mihon.feature.migration.list.models.MigratingManga.SearchResult
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class MigrationListViewModelTest {
    private val graph: TestAppGraph = startTestApp().also { graph ->
        (graph.sourceManager as FakeSourceManager).sources.update { sources ->
            sources + listOf(failing, destination, other).filter { it !in sources }
        }
    }

    /** A library manga of [TestSources.updatable] (or [sourceId]) with [title] and a url of its own. */
    private suspend fun libraryManga(title: String, sourceId: Long = TestSources.updatable.id, url: String? = null) =
        graph.networkToLocalManga(
            Manga.create().copy(url = url ?: "/lista/${next.incrementAndGet()}", title = title, source = sourceId),
        ).also { graph.updateManga.awaitUpdateFavorite(it.id, true) }

    private suspend fun searched(vararg mangas: Manga, sources: List<FakeSource>): MigrationListViewModel {
        graph.sourcePreferences.migrationSources.set(sources.map { it.id })
        val viewModel = graph.migrationListViewModelFactory.create(mangas.map { it.id }, null)
        withTimeout(20.seconds) {
            viewModel.state.first { it.items.size == mangas.size && it.finishedCount == mangas.size }
        }
        return viewModel
    }

    private fun MigrationListViewModel.result(manga: Manga) =
        state.value.items.single { it.manga.id == manga.id }.searchResult.value

    @Test
    fun `a source that fails is skipped and the next one finds the manga`(): Unit = runBlocking {
        val manga = libraryManga("Manga 7")
        failing.failNext = IOException("sin red")

        val viewModel = searched(manga, sources = listOf(failing, destination))

        val found = viewModel.result(manga).shouldBeInstanceOf<SearchResult.Success>()
        found.manga.source shouldBe destination.id
        found.manga.title shouldBe "Manga 7"
        found.chapterCount shouldBe 5
    }

    @Test
    fun `nothing similar in any source leaves it not found`(): Unit = runBlocking {
        val manga = libraryManga("Zzz sin coincidencias")

        val viewModel = searched(manga, sources = listOf(destination))

        viewModel.result(manga) shouldBe SearchResult.NotFound
    }

    @Test
    fun `the origin's own source never returns the same manga as its match`(): Unit = runBlocking {
        val manga = libraryManga("Manga 9", sourceId = destination.id, url = "/manga/9")

        val viewModel = searched(manga, sources = listOf(destination, other))

        viewModel.result(manga).shouldBeInstanceOf<SearchResult.Success>().manga.source shouldBe other.id
    }

    @Test
    fun `migrate all skips the not found ones`(): Unit = runBlocking {
        val found = libraryManga("Manga 8")
        val missing = libraryManga("Zzz tampoco existe")
        val viewModel = searched(found, missing, sources = listOf(destination))
        val target = viewModel.result(found).shouldBeInstanceOf<SearchResult.Success>().manga

        val back = async { viewModel.navigateBackEvent.first() }
        viewModel.migrateMangas()
        withTimeout(20.seconds) { back.await() }

        graph.getManga.await(found.id)!!.favorite shouldBe false
        graph.getManga.await(target.id)!!.favorite shouldBe true
        graph.getManga.await(missing.id)!!.favorite shouldBe true
    }

    @Test
    fun `migrate all with no match at all migrates nothing and goes back`(): Unit = runBlocking {
        val missing = libraryManga("Zzz ninguno")
        val viewModel = searched(missing, sources = listOf(destination))

        val back = async { viewModel.navigateBackEvent.first() }
        viewModel.migrateMangas()
        withTimeout(20.seconds) { back.await() }

        graph.getManga.await(missing.id)!!.favorite shouldBe true
    }

    private companion object {
        val next = AtomicInteger()
        val failing = FakeSource(id = 21L, name = "Falla")
        val destination = FakeSource(id = 22L, name = "Destino")
        val other = FakeSource(id = 23L, name = "Otra")
    }
}
```

Añade a `TestAppGraph` (import `mihon.feature.migration.list.MigrationListViewModel`):

```kotlin
    val migrationListViewModelFactory: MigrationListViewModel.Factory
```

Notas para quien implemente:
- `FakeSource.getSearchManga` devuelve los títulos que contienen la consulta. "Manga 7" da un único candidato con distancia 1.0, así que la coincidencia es exacta.
- `failNext` hace fallar la siguiente búsqueda de esa fuente. `MigrationListViewModel.searchSource` captura el error y devuelve `null`, y el bucle prueba la siguiente fuente.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.migration.MigrationListViewModelTest"`
Expected: FAIL de compilación. No existe `mihon.feature.migration.list.MigrationListViewModel`.

- [ ] **Step 3: Copiar y adaptar**

- `MigratingManga.kt`, `BaseSmartSearchEngine.kt` y `SmartSourceSearchEngine.kt`: sin cambios.
- `MigrationListViewModel.kt`: se quita `import androidx.annotation.FloatRange` y la anotación de `Dialog.Progress`:

```kotlin
        // desktop: no androidx.annotation on desktop; progress stays within 0..1
        data class Progress(val progress: Float) : Dialog
```

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.migration.*"`
Expected: PASS, los 12 (7 de la Task 1 y 5 de esta).

- [ ] **Step 5: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): migration list view model with the smart source search

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Pantallas de migración y sus entradas

**Files:**
- Copy (de `app/src/main/java/` a `desktop-app/src/main/kotlin/`):
  - `mihon/feature/migration/config/{MigrationConfigScreen,MigrationConfigScreenSheet}.kt`
  - `mihon/feature/migration/list/{MigrationListScreen,MigrationListScreenContent}.kt`
  - `mihon/feature/migration/list/components/{MigrationExitDialog,MigrationMangaDialog,MigrationProgressDialog}.kt`
  - `mihon/feature/migration/dialog/MigrateMangaDialog.kt`
  - `eu/kanade/tachiyomi/ui/browse/migration/search/{MigrateSearchScreen,MigrateSearchViewModel,MigrateSourceSearchScreen}.kt`
  - `eu/kanade/presentation/browse/MigrateSearchScreen.kt`
- Modify:
  - `eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`
  - `eu/kanade/tachiyomi/ui/library/LibraryTab.kt`
  - `eu/kanade/tachiyomi/ui/history/HistoryTab.kt`
  - `eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreen.kt`
  - `eu/kanade/presentation/manga/components/MangaBottomActionMenu.kt` y `eu/kanade/presentation/manga/DuplicateMangaDialog.kt`: solo se borran los comentarios "null until migration (3b)".
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/migration/MigrationScreensTest.kt`

**Interfaces:**
- Consumes:
  - de la Task 2, `MigrationListViewModel.Factory` y `MigratingManga`;
  - de la Task 1, `MigrateMangaUseCase` y `getLabel()`;
  - `SearchViewModel`, ya portado e idéntico al de Mihon;
  - `EmbeddedBrowser.open(context, url, headers)` (2b).
- Produces (Mihon): `MigrationConfigScreen(mangaIds: Collection<Long>)`, `MigrationConfigScreen(mangaId: Long)`, `MigrationListScreen(mangaIds, extraSearchQuery)`, `MigrateSearchScreen(mangaId)` y `Screen.MigrateMangaDialog(current, target, onClickTitle, onDismissRequest, onComplete)`.

- [ ] **Step 1: Escribir el test**

```kotlin
package mihon.desktop.app.migration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.main.Shortcut
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class MigrationScreensTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `Migrate in a library manga's menu opens the config, and Continue opens the manual search`() =
        runComposeUiTest {
            val graph = startTestApp()
            val manga = runBlocking { TestLibrary.libraryManga(graph, TestLibrary.category(graph)) }
            setContent {
                AppProviders(graph) {
                    Navigator(MangaScreen(manga.id)) { DefaultNavigatorScreenTransition(it) }
                }
            }

            waitForText(manga.title)
            onNodeWithContentDescription(str(MR.strings.action_menu_overflow_description)).performClick()
            onNodeWithText(str(MR.strings.action_migrate)).performClick()
            waitForText(str(MR.strings.migrationConfigScreen_continueButtonText))

            onNodeWithText(str(MR.strings.migrationConfigScreen_continueButtonText)).performClick()
            // One manga: straight to the global search over the chosen sources, with its title as the query
            waitForText(manga.title)
        }

    @Test
    fun `Migrate on a library selection opens the config`() = runComposeUiTest {
        val graph = startTestApp()
        val category = runBlocking { TestLibrary.category(graph) }
        val name = runBlocking { graph.getCategories.await().first { it.id == category }.name }
        val manga = runBlocking { TestLibrary.libraryManga(graph, category) }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitForText(name)
        onNodeWithText(name).performClick()
        waitForText(manga.title)
        runOnIdle { AppEvents.send(Shortcut.SelectAll) }
        // Without downloads (subproject 4) the bottom bar has no overflow: Migrate is its own icon button
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasContentDescription(str(MR.strings.migrate))).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithContentDescription(str(MR.strings.migrate)).performClick()
        waitForText(str(MR.strings.migrationConfigScreen_continueButtonText))
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.migration.MigrationScreensTest"`
Expected: FAIL, los dos.
- En la ficha, `onClickMigrate` es `null`, así que el menú no tiene "Migrar" y `onNodeWithText` no encuentra el nodo.
- En la biblioteca, `onMigrateClicked` es `null` y la barra no tiene el botón.

- [ ] **Step 3: Copiar y adaptar**

**Sin cambios:** `MigrationConfigScreen.kt`, `MigrationExitDialog.kt`, `MigrationMangaDialog.kt`, `MigrationProgressDialog.kt`, `MigrateMangaDialog.kt`, `MigrateSearchViewModel.kt` y los dos `MigrateSearchScreen.kt`.
- `MigrateMangaDialog` ya oculta "Borrar descargas": solo lo ofrece si `downloadManager.getDownloadCount(current) > 0`, y el stub devuelve 0.

**`MigrationConfigScreenSheet.kt`:** quita el `MigrationSheetSwitchItem` de `migrationConfigScreen_removeDownloadsTitle` y su `val removeDownloads`, con este comentario en su lugar:

```kotlin
                // desktop: removing downloads arrives with downloads (subproject 4)
```

**`MigrationListScreen.kt`:** sin cambios. `Toast` y `BackHandler` son los shims permitidos.

**`MigrationListScreenContent.kt`:** el cartel de "No encontrado" usa el mismo icono que `MangaCover` para una portada fallida:

```kotlin
                    Image(
                        // desktop: no Android drawables; the same error image MangaCover uses
                        painter = rememberVectorPainter(MaterialSymbols.Rounded.Image),
```

  - Imports: quita `eu.kanade.presentation.util.rememberResourceBitmapPainter` y `eu.kanade.tachiyomi.R`.
  - Añade `androidx.compose.ui.graphics.vector.rememberVectorPainter` y `mihon.icons.materialsymbols.rounded.Image`.

**`MigrateSourceSearchScreen.kt`:**
- `columns = viewModel.getColumnsPreference(), // desktop: no orientation`.
- `onWebViewClick`, como en `BrowseSourceScreen` del escritorio:

```kotlin
                onWebViewClick = {
                    // desktop: the embedded browser window instead of WebViewScreen (2b)
                    val httpSource = source as? HttpSource ?: return@BrowseSourceContent
                    EmbeddedBrowser.open(context, httpSource.getHomeUrl(), httpSource.headers)
                },
```

  Con `val context = LocalContext.current` al inicio de `Content()` y el import de `EmbeddedBrowser` que usa `BrowseSourceScreen.kt`.
- En `onComplete` del `MigrateMangaDialog` (desviación 1):

```kotlin
                    onComplete = {
                        scope.launch {
                            navigator.popUntilRoot()
                            // desktop: HomeScreen has no openTab; the target opens over the current tab
                            navigator.push(MangaScreen(dialog.target.id))
                        }
                    },
```

- Quita los imports de `WebViewScreen`, `HomeScreen` y `LocalConfiguration`.

**`ui/manga/MangaScreen.kt`** (la del escritorio). Cambia esto:

```kotlin
            // desktop: fetch interval and migration arrive in 3b, notes in subproject 5
            onEditFetchIntervalClicked = null,
            onMigrateClicked = null,
            onEditNotesClicked = null,
```

por esto:

```kotlin
            onEditFetchIntervalClicked = null, // desktop: wired in the interval task of 3b
            onMigrateClicked = {
                navigator.push(MigrationConfigScreen(successState.manga.id))
            }.takeIf { successState.manga.favorite },
            onEditNotesClicked = null, // desktop: notes arrive in subproject 5
```

  - En el diálogo de duplicado: `onMigrate = { viewModel.showMigrateDialog(it) },`.
  - En el `when (dialog)`, antes de `else -> {}`, va el bloque de Mihon:

```kotlin
            is MangaViewModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
```

  - El comentario `// desktop: no Migrate, TrackSheet or SetFetchInterval dialogs yet (3b and subproject 5)` pasa a decir `// desktop: no TrackSheet (subproject 5); SetFetchInterval comes with the interval task`.

**`LibraryTab.kt`:**

```kotlin
                    onMigrateClicked = {
                        val selection = state.selection
                        viewModel.clearSelection()
                        navigator.push(MigrationConfigScreen(selection))
                    },
```

**`HistoryTab.kt`:**
- En el diálogo de duplicado: `onMigrate = { viewModel.showMigrateDialog(dialog.manga, it) },`.
- El `else -> {}` y su comentario se sustituyen por el bloque `is HistoryViewModel.Dialog.Migrate -> { MigrateMangaDialog(...) }` de Mihon (igual que el de la ficha), seguido de `null -> {}`.

**`BrowseSourceScreen.kt`** (`ui/browse/source/browse`):
- En el diálogo de duplicado: `onMigrate = { viewModel.setDialog(BrowseSourceViewModel.Dialog.Migrate(dialog.manga, it)) },`.
- El comentario `// desktop: no Migrate dialog until 3b` se sustituye por el bloque `is BrowseSourceViewModel.Dialog.Migrate -> { MigrateMangaDialog(...) }` de Mihon.
- La rama `else -> {}` del final se queda, porque cubre `null`.

Imports en esos cuatro archivos: `mihon.feature.migration.config.MigrationConfigScreen` y `mihon.feature.migration.dialog.MigrateMangaDialog`, según los use cada uno.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.migration.*" --tests "mihon.desktop.app.SpanishStringsTest" --tests "mihon.desktop.app.history.*" --tests "mihon.desktop.app.library.*" --tests "mihon.desktop.app.browse.*"`
Expected: PASS.
- Si `SpanishStringsTest` lista claves sin traducir, añade sus `<string>` en `es/strings.xml` con la misma clave y vuelve a pasarlo.

Run: `bash scripts/check-no-android.sh`
Expected: `No direct Android imports in desktop-app`.

- [ ] **Step 5: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): migration config, list, manual search and dialog, from every entry point

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Pestaña Migrar de Explorar

**Files:**
- Copy (de `app/src/main/java/` a `desktop-app/src/main/kotlin/`):
  - `eu/kanade/domain/source/interactor/GetSourcesWithFavoriteCount.kt`
  - `eu/kanade/tachiyomi/ui/browse/migration/sources/{MigrateSourceTab,MigrateSourceViewModel}.kt`
  - `eu/kanade/tachiyomi/ui/browse/migration/manga/{MigrateMangaScreen,MigrateMangaViewModel}.kt`
  - `eu/kanade/presentation/browse/MigrateSourceScreen.kt`
  - `eu/kanade/presentation/manga/components/BaseMangaListItem.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/BrowseTab.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/migration/MigrateTabTest.kt`

**Interfaces:**
- Consumes:
  - de la Task 3, `MigrationConfigScreen(Collection<Long>)`;
  - `SourceRepository.getSourcesWithFavoriteCount()` (data) y `SetMigrateSorting` (ya portado).
- Produces: `@Composable fun Screen.migrateSourceTab(): TabContent` (Mihon).

- [ ] **Step 1: Escribir el test**

```kotlin
package mihon.desktop.app.migration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class MigrateTabTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `Browse has a Migrate tab listing the sources of the library, and a source lists its manga`() =
        runComposeUiTest {
            val graph = startTestApp()
            val manga = runBlocking { TestLibrary.libraryManga(graph, TestLibrary.category(graph)) }
            setContent { AppContent(graph, crash = null, onRestart = {}) }

            waitForText(str(MR.strings.browse))
            onAllNodesWithText(str(MR.strings.browse)).onFirst().performClick()
            waitForText(str(MR.strings.label_migration))
            onAllNodesWithText(str(MR.strings.label_migration)).onFirst().performClick()

            waitForText(TestSources.updatable.name)
            onAllNodesWithText(TestSources.updatable.name).onFirst().performClick()
            waitForText(manga.title)
        }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.migration.MigrateTabTest"`
Expected: FAIL. No hay pestaña con el texto `label_migration` y `waitUntil` agota el tiempo.

- [ ] **Step 3: Copiar y adaptar**

- **Sin cambios:** `GetSourcesWithFavoriteCount.kt`, `MigrateSourceViewModel.kt`, `MigrateMangaViewModel.kt`, `MigrateSourceTab.kt`, `BaseMangaListItem.kt` y `MigrateSourceScreen.kt` (`copyToClipboard` existe en `ContextExtensions.kt`).
- **`MigrateMangaScreen.kt`:** sin cambios. `BackHandler` y `toast` son los shims.
- **`BrowseTab.kt`** (escritorio):
  - la lista pasa a ser `listOf(sourcesTab(), extensionsTab(extensionsViewModel), migrateSourceTab())`, con `import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab`;
  - el KDoc pasa a `/** Mihon's BrowseTab with a static rail icon. */`.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.migration.*" --tests "mihon.desktop.app.main.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS. Las traducciones que falten se resuelven como en la Task 3.

- [ ] **Step 5: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): Migrate tab in Browse, by source and by manga

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Próximos

**Files:**
- Copy (de `app/src/main/java/` a `desktop-app/src/main/kotlin/`):
  - `mihon/feature/upcoming/{UpcomingScreen,UpcomingScreenContent,UpcomingViewModel,UpcomingUIModel}.kt`
  - `mihon/feature/upcoming/components/UpcomingItem.kt`
  - `mihon/feature/upcoming/components/calendar/{Calendar,CalendarDay,CalendarHeader,CalendarIndicator}.kt`
- Create: `desktop-app/src/main/kotlin/mihon/core/designsystem/utils/WindowSize.kt`
- Modify:
  - `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/updates/UpdatesTab.kt`
  - `desktop-app/src/main/kotlin/eu/kanade/presentation/updates/UpdatesScreen.kt` (solo el comentario de la línea 59)
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/{TestAppGraph,TestLibrary}.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/upcoming/UpcomingTest.kt`

**Interfaces:**
- Consumes: `GetUpcomingManga.subscribe(excluded, included): Flow<List<Manga>>` y `UpcomingPreferences` (domain).
- Produces:
  - `UpcomingScreen()` y `UpcomingViewModel(getUpcomingManga, getCategories, upcomingPreferences)`, cuyo `state: StateFlow<State>` lleva `items` y `events: Map<LocalDate, Int>` (Mihon);
  - `TestLibrary.ongoingManga(graph, category, nextUpdate: Instant?, status: Long = SManga.ONGOING.toLong()): Manga`;
  - en `TestAppGraph`: `getUpcomingManga` y `upcomingPreferences`.

- [ ] **Step 1: Ayuda de test**

En `TestLibrary`, con los imports `eu.kanade.tachiyomi.source.model.SManga`, `tachiyomi.domain.manga.model.MangaUpdate` y `kotlin.time.Instant`:

```kotlin
    /** A library manga with [status] and [nextUpdate], without chapters: what Upcoming reads. */
    suspend fun ongoingManga(
        graph: TestAppGraph,
        category: Long,
        nextUpdate: Instant?,
        status: Long = SManga.ONGOING.toLong(),
    ): Manga {
        val n = next.incrementAndGet()
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "/p/$n", title = "Próximo $n", source = TestSources.updatable.id, status = status),
        )
        graph.updateManga.awaitUpdateFavorite(manga.id, true)
        graph.setMangaCategories.await(manga.id, listOf(category))
        if (nextUpdate != null) graph.updateManga.await(MangaUpdate(manga.id) { this.nextUpdate = nextUpdate })
        return graph.getManga.await(manga.id)!!
    }
```

Añade a `TestAppGraph` (imports `mihon.domain.upcoming.interactor.GetUpcomingManga` y `tachiyomi.domain.upcoming.service.UpcomingPreferences`):

```kotlin
    val getUpcomingManga: GetUpcomingManga
    val upcomingPreferences: UpcomingPreferences
```

- [ ] **Step 2: Escribir los tests**

```kotlin
package mihon.desktop.app.upcoming

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.feature.upcoming.UpcomingUIModel
import mihon.feature.upcoming.UpcomingViewModel
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class UpcomingTest {
    @Test
    fun `upcoming groups the library by expected day, without completed or undated manga`(): Unit = runBlocking {
        val graph = startTestApp()
        val category = TestLibrary.category(graph)
        val now = Clock.System.now()
        val tomorrow = TestLibrary.ongoingManga(graph, category, now + 1.days)
        val later = TestLibrary.ongoingManga(graph, category, now + 3.days)
        val completed = TestLibrary.ongoingManga(graph, category, now + 1.days, status = SManga.COMPLETED.toLong())
        val undated = TestLibrary.ongoingManga(graph, category, nextUpdate = null)
        val viewModel = UpcomingViewModel(graph.getUpcomingManga, graph.getCategories, graph.upcomingPreferences)

        val state = withTimeout(10.seconds) {
            viewModel.state.first { s -> s.items.any { it is UpcomingUIModel.Item && it.manga.id == later.id } }
        }

        val ids = state.items.filterIsInstance<UpcomingUIModel.Item>().map { it.manga.id }
        ids shouldContain tomorrow.id
        ids shouldNotContain completed.id
        ids shouldNotContain undated.id
        val zone = TimeZone.currentSystemDefault()
        state.events.keys shouldContain (now + 1.days).toLocalDateTime(zone).date
        state.events.keys shouldContain (now + 3.days).toLocalDateTime(zone).date
        ids.indexOf(tomorrow.id) < ids.indexOf(later.id) shouldBe true
    }

    @Test
    fun `the calendar button in Updates opens Upcoming`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitUntil(15_000) { onAllNodes(hasText(str(MR.strings.label_recent_updates))).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText(str(MR.strings.label_recent_updates)).onFirst().performClick()
        waitUntil(15_000) {
            onAllNodes(androidx.compose.ui.test.hasContentDescription(str(MR.strings.action_view_upcoming)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithContentDescription(str(MR.strings.action_view_upcoming)).performClick()
        waitUntil(15_000) { onAllNodes(hasText(str(MR.strings.label_upcoming))).fetchSemanticsNodes().isNotEmpty() }
    }
}
```

Notas:
- La lista sale ordenada por `nextUpdate` (consulta de Mihon); por eso `tomorrow` va antes que `later`.
- El primer test crea su ViewModel fuera de Compose. `viewModelScope` usa `Dispatchers.Main`, que es el EDT de Swing tras `Platform.start` (memoria de 2a).

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.upcoming.UpcomingTest"`
Expected: FAIL de compilación. No existe `mihon.feature.upcoming.UpcomingViewModel`.

- [ ] **Step 4: `WindowSize.kt`**

`desktop-app/src/main/kotlin/mihon/core/designsystem/utils/WindowSize.kt`:

```kotlin
package mihon.core.designsystem.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// desktop: Android reads the device configuration; on desktop the window width decides, as in isTabletUi
@Composable
private fun windowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

@Composable
fun isMediumWidthWindow(): Boolean = windowWidth() > MediumWidthWindowSize

@Composable
fun isExpandedWidthWindow(): Boolean = windowWidth() > ExpandedWidthWindowSize

val MediumWidthWindowSize = 600.dp
val ExpandedWidthWindowSize = 840.dp
```

Se quita `@ReadOnlyComposable`: `LocalWindowInfo` no lo exige y `isTabletUi` tampoco lo lleva.

- [ ] **Step 5: Copiar y adaptar**

- **Sin cambios:** los 9 archivos de `mihon/feature/upcoming`.
- **`UpdatesTab.kt`:** `onCalendarClicked = { navigator.push(UpcomingScreen()) },`, con `import mihon.feature.upcoming.UpcomingScreen`.
- **`UpdatesScreen.kt`:** el comentario `// desktop: null until Upcoming (3b)` se quita. El parámetro sigue siendo nulable, que no estorba.

- [ ] **Step 6: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.upcoming.*" --tests "mihon.desktop.app.library.UpdatesTabTest" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS. Las traducciones que falten se resuelven como en la Task 3.

- [ ] **Step 7: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): Upcoming screen with its calendar, from the Updates tab

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Intervalo de actualización por manga

**Files:**
- Modify:
  - `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`
  - `desktop-app/src/main/kotlin/eu/kanade/presentation/manga/components/MangaDialogs.kt:112`
  - `desktop-app/src/main/kotlin/eu/kanade/presentation/library/LibrarySettingsDialog.kt:106-107`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/Strings.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/upcoming/MangaIntervalTest.kt`

**Interfaces:**
- Consumes:
  - `MangaViewModel.showSetFetchIntervalDialog()`, `setFetchInterval(manga, interval)`, `isUpdateIntervalEnabled` y `Dialog.SetFetchInterval(manga)`, todos ya portados;
  - `UpdateManga.awaitUpdateFetchInterval(manga)`;
  - de la Task 5: `TestLibrary.ongoingManga` y `UpcomingViewModel`.
- Produces: `fun plural(res: PluralsResource, count: Int, vararg args: Any): String` en `Strings.kt`.

- [ ] **Step 1: Ayuda `plural`**

En `Strings.kt`, con los imports `dev.icerock.moko.resources.PluralsResource` y `dev.icerock.moko.resources.desc.PluralFormatted`:

```kotlin
/** Like [str], for a plural: the text for [count], formatted with [args]. */
fun plural(res: PluralsResource, count: Int, vararg args: Any): String =
    StringDesc.PluralFormatted(res, count, *args).localized()
```

- [ ] **Step 2: Escribir los tests**

```kotlin
package mihon.desktop.app.upcoming

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.plural
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.feature.upcoming.UpcomingUIModel
import mihon.feature.upcoming.UpcomingViewModel
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class MangaIntervalTest {
    @Test
    fun `fixing 3 days gives an undated manga a next update in Upcoming, and Default goes back to automatic`(): Unit =
        runBlocking {
            val graph = startTestApp()
            val manga = TestLibrary.ongoingManga(graph, TestLibrary.category(graph), nextUpdate = null)

            // What MangaViewModel.setFetchInterval does for 3 days: custom intervals are negative
            graph.updateManga.awaitUpdateFetchInterval(manga.copy(fetchInterval = -3))
            val fixed = graph.getManga.await(manga.id)!!
            fixed.fetchInterval shouldBe -3
            fixed.nextUpdate.shouldNotBeNull()
            val viewModel = UpcomingViewModel(graph.getUpcomingManga, graph.getCategories, graph.upcomingPreferences)
            withTimeout(10.seconds) {
                viewModel.state.first { s -> s.items.any { it is UpcomingUIModel.Item && it.manga.id == manga.id } }
            }

            graph.updateManga.awaitUpdateFetchInterval(fixed.copy(fetchInterval = 0))
            graph.getManga.await(manga.id)!!.fetchInterval shouldBeGreaterThanOrEqual 0
        }

    /** The interval button's text, as MangaInfoHeader computes it from the stored next update. */
    private fun intervalLabel(graph: TestAppGraph, mangaId: Long): String {
        val next = runBlocking { graph.getManga.await(mangaId)!!.expectedNextUpdate }
        val days = next?.let { Clock.System.now().daysUntil(it, TimeZone.currentSystemDefault()) }
            ?: return str(MR.strings.not_applicable)
        return if (days <= 0) str(MR.strings.manga_interval_expected_update_soon) else plural(MR.plurals.day, days, days)
    }

    @Test
    fun `the interval button of a library manga opens the dialog with the day picker`() = runComposeUiTest {
        val graph = startTestApp()
        val manga = runBlocking { TestLibrary.libraryManga(graph, TestLibrary.category(graph)) }
        setContent {
            AppProviders(graph) { Navigator(MangaScreen(manga.id)) { DefaultNavigatorScreenTransition(it) } }
        }

        // Opening the manga refreshes it from the source, which may move its next update: read the label each time
        waitUntil(15_000) { onAllNodes(hasText(intervalLabel(graph, manga.id))).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText(intervalLabel(graph, manga.id)).onFirst().performClick()
        waitUntil(10_000) {
            onAllNodes(hasText(str(MR.strings.pref_library_update_smart_update))).fetchSemanticsNodes().isNotEmpty()
        }
        // The picker shows only when the dialog can change the interval (no release-build gate on desktop)
        onAllNodes(hasText(str(MR.strings.manga_interval_custom_amount))).fetchSemanticsNodes().size shouldBe 1
    }
}
```

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.upcoming.MangaIntervalTest"`
Expected:
- El primer test pasa: el intervalo ya está en `domain` y solo fija el comportamiento.
- El segundo FALLA: con `onEditFetchIntervalClicked = null` el clic no abre nada y `waitUntil` agota el tiempo.

- [ ] **Step 4: Conectar y quitar la condición de build**

**`ui/manga/MangaScreen.kt`:**

```kotlin
            onEditFetchIntervalClicked = viewModel::showSetFetchIntervalDialog.takeIf {
                successState.manga.favorite
            },
```

En el `when (dialog)`, antes de `else -> {}`, va el bloque de Mihon:

```kotlin
            is MangaViewModel.Dialog.SetFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> viewModel.setFetchInterval(dialog.manga, interval) }
                        .takeIf { viewModel.isUpdateIntervalEnabled },
                )
            }
```

- Import: `eu.kanade.presentation.manga.components.SetIntervalDialog`.
- El comentario del `else` pasa a ser `// desktop: no TrackSheet dialog until subproject 5`.

**`MangaDialogs.kt:112`:**

```kotlin
                // desktop: custom intervals in every build, not only debug ones (spec 3b §1)
                if (onValueChanged != null) {
```

Quita el import de `isReleaseBuildType` si se queda sin uso.

**`LibrarySettingsDialog.kt:106-107`:**

```kotlin
    // desktop: custom intervals in every build (spec 3b §1), so their filter shows too
    if (LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in autoUpdateMangaRestrictions) {
```

Quita también el `// TODO: re-enable…` de Mihon y el import de `isReleaseBuildType` si se queda sin uso.

- [ ] **Step 5: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.upcoming.*" --tests "mihon.desktop.app.browse.*" --tests "mihon.desktop.app.library.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): per-manga update interval from the manga screen, in every build

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Verificación completa y prueba manual del 3b

**Files:**
- Modify: ninguno, salvo los arreglos que salgan

- [ ] **Step 1: Suite completa y checks**

Run: `./gradlew test spotlessCheck`
Expected: BUILD SUCCESSFUL. Unos 360 tests: los 342 de antes y unos 18 nuevos.
- Si `spotlessCheck` falla, ejecuta `./gradlew spotlessApply` y revisa el diff.

Run: `bash scripts/check-no-android.sh` y `bash scripts/check-licenses.sh`
Expected: los dos OK.

- [ ] **Step 2: Ninguna referencia pendiente al 3b**

Run: `grep -rn "3b" desktop-app/src/main desktop-app/src/stubs`
Expected: solo los `// desktop:` que citan la spec 3b (`spec 3b §1`). Cualquier "until 3b", "arrives in 3b" o "comes with 3b" es un gancho sin conectar: se conecta o se explica.

- [ ] **Step 3: Commit de los arreglos (si los hay)**

```bash
git add -A desktop-app i18n
git commit -m "chore: tidy the last 3b notes

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Prueba manual (spec §5), al usuario**

Pasa al usuario esta lista para ejecutar con `./gradlew :desktop-app:run`:
1. **Migrar uno.** Ficha de un manga real de la biblioteca (fuente keiyoushi) › menú › Migrar. Elige otra fuente y pulsa Continuar. En la búsqueda global, elige el destino y pulsa Migrar. Comprueba que se conservan los capítulos leídos, la categoría y la portada personalizada.
2. **Migrar en lote.** En la Biblioteca, selecciona 3 mangas o más y pulsa Migrar › Continuar. En la hoja de opciones, pulsa Continuar. Uno debe quedar "No encontrado". Busca otro a mano con "Buscar manualmente". Pulsa "Migrar" y comprueba que el no encontrado sigue en la biblioteca.
3. **Copiar.** Repite el punto 1 con "Copiar": los dos mangas quedan en la biblioteca.
4. **Próximos.** En Novedades, pulsa el icono de calendario. Los mangas en emisión aparecen en sus días. Pulsa un día para saltar a su grupo y pulsa un manga para abrir su ficha.
5. **Intervalo.** En la ficha, pulsa el botón de intervalo (reloj de arena), elige 3 días y pulsa OK. En Próximos, el manga aparece en el día nuevo.
