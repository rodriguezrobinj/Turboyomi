# Subproyecto 3a: biblioteca, categorías, novedades y actualización periódica. Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La app gana las pestañas Biblioteca y Novedades, la pantalla Categorías, Ajustes › Biblioteca, una actualización periódica de la biblioteca con progreso dentro de la app, y un icono en la bandeja con "Cerrar a la bandeja" y notificaciones.

**Architecture:**
- **Planificador.** Un `JobScheduler` propio en desktop-core decide cuándo toca cada `PeriodicJob`. Usa `lastRun` en las preferencias y revisa al arrancar y cada 60 s.
- **Tarea de actualización.** `LibraryUpdateJob`, en desktop-app, es el cuerpo de `LibraryUpdateWorker` y `MetadataUpdateWorker` de Mihon sin WorkManager.
  - Expone su progreso como `StateFlow`.
  - Notifica a través de `TrayNotifier`: hacia la bandeja si existe; si no, como mensaje dentro de la app.
- **Pantallas.** Se copian de Mihon con su paquete, como en 2a.

**Tech Stack:**
- Kotlin 2.4.21 y JDK 21.
- Compose Multiplatform 1.12.2: `Tray`, `rememberTrayState` y `Notification` de `androidx.compose.ui.window`.
- Voyager 2.2.21-1.10.3 y Metro 1.4.5.
- SQLDelight (BD de Mihon) y kotlinx-coroutines.
- JUnit 5, kotest y `compose.uiTest`.

**Spec:** `docs/superpowers/specs/2026-10-21-subproyecto-3a-biblioteca-design.md`. Spec padre: `docs/superpowers/specs/2026-10-18-windows-port-design.md` (§5.1).

## Global Constraints

- **Portado de Mihon:**
  - Los archivos se copian de `app/src/main/java/<ruta>` a `desktop-app/src/main/kotlin/<ruta>`, con **el mismo paquete**.
  - Solo se cambia lo que dice el paso.
  - Cada cambio lleva un comentario `// desktop: <motivo>` en la línea modificada o justo encima.
- **Implementaciones nulas.**
  - Van en `desktop-app/src/stubs/kotlin/<paquete de Mihon>`, con la cabecera `// Null implementation until subproject <N>; replace with the Mihon port.`
  - Si el código portado necesita un método más, se añade ahí, devolviendo vacío.
- **Paquetes del código nuevo:** `mihon.desktop.app.*` en desktop-app y `mihon.desktop.core.*` en desktop-core.
- **Nada de `android.*` ni `androidx.*`** fuera de lo que permite `scripts/check-no-android.sh`.
- **Textos nuevos:**
  - van a `i18n/src/commonMain/moko-resources/base/strings.xml` (inglés) y `.../es/strings.xml` (español), al final del bloque `<!-- Desktop -->`, con prefijo `desktop_`;
  - `SpanishStringsTest` exige que todos tengan traducción;
  - se reutilizan las cadenas de Mihon siempre que existan.
- **Git:**
  - nunca `git push` al remoto `upstream` (mihonapp/mihon);
  - commits con el trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`;
  - el trabajo va en la rama `subproyecto-3a`, creada desde `master` con este plan ya commiteado.
- **Tests:** los que son `= runBlocking { }` declaran `: Unit`.
- **Archivos con `\\`:** se escriben con la herramienta Write, no con heredoc de Bash.
- **Fuera de alcance** (spec §1):
  - migración y Próximos, que van al 3b;
  - descargas, que van al 4;
  - trackers, que van al 5;
  - "Iniciar con Windows", que va al empaquetado.
  - Sus botones se ocultan pasando `null` o quitando la entrada, con `// desktop:`.

## Desviaciones del spec (aprobar antes de ejecutar)

1. **Texto de la notificación de capítulos nuevos.**
   - El spec pide "14 capítulos en 5 mangas". Esa frase necesitaría un plural anidado, con dos cuentas, que moko no tiene.
   - Se usa la cadena de Mihon `notification_chapters_generic` ("14 capítulos nuevos"), seguida de los 3 primeros títulos y de "y N más". El número de mangas se deduce de los títulos.
2. **Icono de la bandeja.** La app aún no tiene icono propio; llega con el empaquetado. Se usa el símbolo `CollectionsBookmark` teñido de azul (`#3B82F6`), para que se vea sobre una barra de tareas clara u oscura.
3. **"Editar categorías" en el menú de la biblioteca.** Mihon lo tiene en la pestaña Más, que no existe en el escritorio. Se añade como `OverflowAction` de `LibraryToolbar`, con la cadena de Mihon `action_edit_categories`, tal como dice el spec §3 B.

## Review Focus

1. **Cancelar o Salir a mitad de una actualización.** Lo ya guardado se queda, no sale ninguna notificación de error y la barra de progreso desaparece. Test en la Task 2.
2. **Un manga de una fuente desinstalada en la biblioteca.** Entra en los errores con el texto de Mihon, la tarea sigue con los demás y el archivo de errores lo nombra. Test en la Task 2.
3. **Añadir a la biblioteca con categorías creadas,** desde Explorar, desde el detalle o desde el historial. Hoy no se dibuja el diálogo y el manga no llega a añadirse. Tiene que aparecer `ChangeCategoryDialog`, y al confirmar el manga queda en su categoría. Test en la Task 3.
4. **Una actualización automática que toca justo al volver de una suspensión.** No se lanza en esa revisión, sino en la siguiente. Test en la Task 1.
5. **Pulsar "Actualizar" con una actualización ya en curso**, desde el menú, con F5 o desde la bandeja. No se lanza una segunda, y la pantalla muestra el mensaje de Mihon `update_already_running`. Test de `start` en la Task 2. El mensaje es el código de Mihon sin cambios, ligado a ese `Boolean`.

## Mapa de archivos

| Ruta | Responsabilidad |
|---|---|
| `desktop-core/src/main/kotlin/mihon/desktop/core/jobs/JobScheduler.kt` | `PeriodicJob` y `JobScheduler` |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt` | Actualización de capítulos y de metadatos, progreso, archivo de errores |
| `desktop-app/src/main/kotlin/mihon/desktop/app/tray/TrayNotifier.kt` | `Notice`, `TrayNotifier`, textos de las notificaciones |
| `desktop-app/src/main/kotlin/mihon/desktop/app/tray/TrayPreferences.kt` | `desktop_close_to_tray`, `desktop_tray_hint_shown` |
| `desktop-app/src/main/kotlin/mihon/desktop/app/tray/AppTray.kt` | Icono, menú y tooltip de la bandeja; `closeAction` |
| `desktop-app/src/main/kotlin/mihon/desktop/app/library/LibraryUpdateProgress.kt` | Barra de progreso de Biblioteca y Novedades |
| `desktop-app/src/main/kotlin/mihon/desktop/app/platform/SelectionClick.kt` | Clic, Mayús+clic en modo selección |
| `desktop-app/src/main/kotlin/mihon/desktop/app/platform/AppMessages.kt` | Mensajes con acción opcional |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/category/**`, `eu/kanade/presentation/category/**` | Categorías (Mihon) |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/library/**`, `eu/kanade/presentation/library/**`, `mihon/feature/library/QueryNodeExtensions.kt` | Biblioteca (Mihon) |
| `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/updates/**`, `eu/kanade/presentation/updates/**` | Novedades (Mihon) |
| `desktop-app/src/main/kotlin/eu/kanade/presentation/more/settings/screen/SettingsLibraryScreen.kt` | Ajustes › Biblioteca (Mihon) |
| `desktop-app/src/main/kotlin/mihon/desktop/app/main/{HomeScreen,AppContent,AppEvents,Shortcut}.kt` | Riel nuevo, atajos, abrir Novedades |
| `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt` | Planificador, bandeja, ocultar y mostrar ventana, salir |
| `desktop-app/src/test/kotlin/mihon/desktop/app/testing/UpdatableSource.kt` | Fuente de test con capítulos y fallos controlables |

---

### Task 1: Planificador de tareas periódicas

**Files:**
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/jobs/JobScheduler.kt`
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/jobs/JobSchedulerTest.kt`

**Interfaces:**
- Consumes: `tachiyomi.core.common.preference.Preference<Long>`; `InMemoryPreferenceStore` (core common), en tests.
- Produces:
  - `class PeriodicJob(name: String, interval: () -> Duration?, lastRun: Preference<Long>, run: suspend () -> Unit)`
  - `class JobScheduler(jobs: List<PeriodicJob>, now: () -> Long = System::currentTimeMillis)`, con:
    - `fun start(scope: CoroutineScope): Job`
    - `fun check(scope: CoroutineScope): List<String>`, que devuelve los nombres que lanzó
    - `companion val CHECK_EVERY = 1.minutes`, `val RESUME_GAP = 2.minutes`

- [ ] **Step 0: Rama**

```bash
git checkout -b subproyecto-3a master
```

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.core.jobs

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class JobSchedulerTest {
    private val store = InMemoryPreferenceStore()
    private var time = 100.hours.inWholeMilliseconds

    private fun job(
        interval: Duration? = 12.hours,
        lastRunKey: String = "last",
        run: suspend () -> Unit = {},
    ) = PeriodicJob("Update", { interval }, store.getLong(lastRunKey, 0L), run)

    @Test
    fun `a job whose interval has passed runs, and its last run is saved before it starts`(): Unit = runBlocking {
        val seenLastRun = CompletableDeferred<Long>()
        val job = job { seenLastRun.complete(store.getLong("last", 0L).get()) }
        val scheduler = JobScheduler(listOf(job)) { time }

        coroutineScope { scheduler.check(this) shouldContainExactly listOf("Update") }

        withTimeout(5.seconds) { seenLastRun.await() } shouldBe time
    }

    @Test
    fun `a job that ran less than an interval ago doesn't run`(): Unit = runBlocking {
        store.getLong("last", 0L).set(time - 11.hours.inWholeMilliseconds)
        val scheduler = JobScheduler(listOf(job())) { time }

        coroutineScope { scheduler.check(this).shouldBeEmpty() }
    }

    @Test
    fun `a job with no interval never runs`(): Unit = runBlocking {
        val scheduler = JobScheduler(listOf(job(interval = null))) { time }

        coroutineScope { scheduler.check(this).shouldBeEmpty() }
    }

    @Test
    fun `right after the computer wakes up it waits one more check, so the network is back`(): Unit = runBlocking {
        store.getLong("last", 0L).set(time - 1.hours.inWholeMilliseconds)
        val scheduler = JobScheduler(listOf(job())) { time }
        coroutineScope { scheduler.check(this).shouldBeEmpty() }

        // Asleep for 12 h: the job is due, but this check is the wake-up one
        time += 12.hours.inWholeMilliseconds
        coroutineScope { scheduler.check(this).shouldBeEmpty() }

        time += 1.minutes.inWholeMilliseconds
        coroutineScope { scheduler.check(this) shouldContainExactly listOf("Update") }
    }

    @Test
    fun `a job still running isn't launched again`(): Unit = runBlocking {
        val release = CompletableDeferred<Unit>()
        val scheduler = JobScheduler(listOf(job(interval = 1.minutes) { release.await() })) { time }

        coroutineScope {
            scheduler.check(this) shouldContainExactly listOf("Update")
            time += 1.minutes.inWholeMilliseconds
            scheduler.check(this).shouldBeEmpty()
            release.complete(Unit)
        }
    }

    @Test
    fun `a job that throws is logged and can run again later`(): Unit = runBlocking {
        val scheduler = JobScheduler(listOf(job(interval = 1.minutes) { error("boom") })) { time }

        coroutineScope { scheduler.check(this) shouldContainExactly listOf("Update") }
        time += 1.minutes.inWholeMilliseconds
        coroutineScope { scheduler.check(this) shouldContainExactly listOf("Update") }
    }
}
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.jobs.JobSchedulerTest"`
Expected: FAIL de compilación (`Unresolved reference 'PeriodicJob'`).

- [ ] **Step 3: Implementar**

```kotlin
package mihon.desktop.core.jobs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A task the scheduler runs every [interval] (spec 3a §3 E). A null interval means it's turned off. */
class PeriodicJob(
    val name: String,
    val interval: () -> Duration?,
    /** Epoch milliseconds of the last start; the scheduler saves it before running, like Mihon's update worker. */
    val lastRun: Preference<Long>,
    val run: suspend () -> Unit,
)

/**
 * In-process scheduler (parent spec §5.1): checks at start and every [CHECK_EVERY]. Jobs only run while the app is
 * open or in the tray; a missed run happens at the first check after the app comes back.
 */
class JobScheduler(
    private val jobs: List<PeriodicJob>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var lastCheck: Long? = null
    private val running: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            check(this)
            delay(CHECK_EVERY)
        }
    }

    /** One check: launches in [scope] every due job that isn't running yet. Returns the names it launched. */
    fun check(scope: CoroutineScope): List<String> {
        val time = now()
        val previous = lastCheck
        lastCheck = time
        // Back from sleep: the network takes a moment to return, so the due jobs wait for the next check
        if (previous != null && time - previous > RESUME_GAP.inWholeMilliseconds) return emptyList()

        val due = jobs.filter { job ->
            val interval = job.interval() ?: return@filter false
            job.name !in running && time - job.lastRun.get() >= interval.inWholeMilliseconds
        }
        due.forEach { job ->
            running += job.name
            job.lastRun.set(time)
            scope.launch {
                try {
                    job.run()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Job ${job.name} failed" }
                } finally {
                    running -= job.name
                }
            }
        }
        return due.map { it.name }
    }

    companion object {
        val CHECK_EVERY = 1.minutes
        val RESUME_GAP = 2.minutes
    }
}
```

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.jobs.JobSchedulerTest"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add desktop-core/src/main/kotlin/mihon/desktop/core/jobs desktop-core/src/test/kotlin/mihon/desktop/core/jobs
git commit -m "feat(desktop-core): periodic job scheduler with last-run tracking and a wake-up grace check

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Actualización de biblioteca y notificaciones

**Files:**
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/tray/TrayNotifier.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/AppMessages.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/AppContent.kt`, en la línea del `LaunchedEffect` de los mensajes
- Modify: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt`
- Modify: `i18n/.../base/strings.xml` y `i18n/.../es/strings.xml`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/UpdatableSource.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/FakeSource.kt` (`TestSources`), `TestAppGraph.kt` (`TestBindings` y accesores) y `TestLibrary.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/platform/AppMessagesTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/library/LibraryUpdateJobTest.kt`

**Interfaces:**
- Consumes:
  - de la Task 1: `PeriodicJob`;
  - de Mihon (ya portados): `UpdateMangaFromRemote`, `GetLibraryManga`, `GetManga`, `FetchInterval`, `LibraryPreferences`, `SourceManager`, `formattedMessage` (`eu.kanade.presentation.util`).
- Produces:
  - `LibraryUpdateJob` (`@SingleIn(AppScope)`), con:
    - `progress: StateFlow<LibraryUpdateJob.Progress?>`
    - `start(categoryId: Long? = null): Boolean`
    - `startMetadata(): Boolean`
    - `cancel()`
    - `suspend awaitIdle()`
    - `periodicJob(): PeriodicJob`
  - `data class Progress(val done: Int, val total: Int, val current: List<String>)`
  - `data class Notice(title: String, message: String, isError: Boolean = false, opensUpdates: Boolean = true)`
  - `object TrayNotifier`, con:
    - `var sink: ((Notice) -> Unit)?`
    - `var unread: Boolean`
    - `fun send(notice: Notice)`
  - `newChaptersNotice(context, updates)` y `errorsNotice(context, count)`
  - `data class AppMessage(text, actionLabel?, onAction?)`
  - `AppMessages.show(text, actionLabel, onAction)`
  - En `AppGraph`: `libraryPreferences`, `libraryUpdateJob`, `getCategories` y `resetCategoryFlags`

- [ ] **Step 1: Textos**

Al final del bloque `<!-- Desktop -->` de `base/strings.xml`:

```xml
    <string name="desktop_update_progress">Updating %1$d/%2$d · %3$s</string>
    <string name="desktop_and_more">and %1$d more</string>
    <string name="desktop_update_errors_hint">Open the app to see which ones</string>
    <string name="desktop_learn_more">Learn more</string>
```

En `es/strings.xml`, en el mismo bloque:

```xml
    <string name="desktop_update_progress">Actualizando %1$d/%2$d · %3$s</string>
    <string name="desktop_and_more">y %1$d más</string>
    <string name="desktop_update_errors_hint">Abre la app para ver cuáles</string>
    <string name="desktop_learn_more">Más información</string>
```

- [ ] **Step 2: Mensajes con acción**

`AppMessages.kt` queda así (se conserva el `install()` actual):

```kotlin
/** A snackbar message; with [actionLabel], the snackbar shows a button that runs [onAction]. */
data class AppMessage(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/** Short messages for the window's snackbar: Android toasts from extensions and from ported Mihon code. */
object AppMessages {
    private val _messages = MutableSharedFlow<AppMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<AppMessage> = _messages.asSharedFlow()

    fun install() {
        Toast.listener = java.util.function.Consumer { show(it.toString()) }
    }

    fun show(message: String) {
        if (message.isNotBlank()) _messages.tryEmit(AppMessage(message))
    }

    fun show(message: String, actionLabel: String, onAction: () -> Unit) {
        if (message.isNotBlank()) _messages.tryEmit(AppMessage(message, actionLabel, onAction))
    }
}
```

En `AppContent.kt`, se sustituye la línea `LaunchedEffect(Unit) { AppMessages.messages.collect { snackbar.showSnackbar(it) } }` por:

```kotlin
            LaunchedEffect(Unit) {
                AppMessages.messages.collect {
                    val result = snackbar.showSnackbar(it.text, actionLabel = it.actionLabel)
                    if (result == SnackbarResult.ActionPerformed) it.onAction?.invoke()
                }
            }
```

Hay que importar `androidx.compose.material3.SnackbarResult`. En `AppMessagesTest.kt`, las dos llamadas `AppMessages.messages.first()` pasan a ser `AppMessages.messages.first().text`.

- [ ] **Step 3: `TrayNotifier` y los textos**

`desktop-app/src/main/kotlin/mihon/desktop/app/tray/TrayNotifier.kt`:

```kotlin
package mihon.desktop.app.tray

import android.content.Context
import mihon.desktop.app.platform.AppMessages
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR

/** A notification (spec 3a §3 G). [opensUpdates]: clicking it opens the Updates tab. */
data class Notice(
    val title: String,
    val message: String,
    val isError: Boolean = false,
    val opensUpdates: Boolean = true,
)

/**
 * Where notifications go: the tray sets [sink] while its icon is up; without it they're in-app messages.
 * AWT hands every notification click to the tray icon's one action, so [unread] tells that action whether the last
 * notification asked to open the Updates tab.
 */
object TrayNotifier {
    @Volatile
    var sink: ((Notice) -> Unit)? = null

    @Volatile
    var unread: Boolean = false

    fun send(notice: Notice) {
        val tray = sink
        if (tray == null) {
            AppMessages.show("${notice.title}\n${notice.message}")
            return
        }
        if (notice.opensUpdates) unread = true
        tray(notice)
    }
}

/** "14 new chapters", then the first three titles and "and N more". */
fun newChaptersNotice(context: Context, updates: List<Pair<Manga, List<Chapter>>>): Notice {
    val chapters = updates.sumOf { it.second.size }
    val titles = updates.map { it.first.title }
    val lines = buildList {
        add(context.pluralStringResource(MR.plurals.notification_chapters_generic, chapters, chapters))
        addAll(titles.take(SHOWN_TITLES))
        if (titles.size > SHOWN_TITLES) add(context.stringResource(MR.strings.desktop_and_more, titles.size - SHOWN_TITLES))
    }
    return Notice(context.stringResource(MR.strings.notification_new_chapters), lines.joinToString("\n"))
}

fun errorsNotice(context: Context, count: Int): Notice = Notice(
    title = context.pluralStringResource(MR.plurals.notification_update_error, count, count),
    message = context.stringResource(MR.strings.desktop_update_errors_hint),
    isError = true,
)

private const val SHOWN_TITLES = 3
```

- [ ] **Step 4: Fuente de test controlable**

`desktop-app/src/test/kotlin/mihon/desktop/app/testing/UpdatableSource.kt`:

```kotlin
package mihon.desktop.app.testing

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * A source for library updates: [chapters] says how many chapters each manga url has now (3 if unset), [failing]
 * makes a manga's update throw, and [gate] holds every update until it's completed (for cancel and "already
 * running").
 */
class UpdatableSource : Source {
    override val id = 3L
    override val name = "Actualizable"
    override val lang = "es"

    val chapters: MutableMap<String, Int> = ConcurrentHashMap()
    val failing: MutableMap<String, Exception> = ConcurrentHashMap()

    @Volatile
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) = MangasPage(emptyList(), false)
    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        gate?.await()
        failing[manga.url]?.let { throw it }
        val count = this.chapters[manga.url] ?: 3
        return SMangaUpdate(
            manga,
            (count downTo 1).map { n ->
                SChapter.create().apply {
                    url = "${manga.url}/c$n"
                    name = "Capítulo $n"
                    chapter_number = n.toFloat()
                    date_upload = 1_700_000_000_000L + n
                }
            },
        )
    }
}
```

En `FakeSource.kt`, añade `val updatable = UpdatableSource()` a `object TestSources`.

En `TestAppGraph.kt`:
- `TestBindings.sourceManager()` pasa a `FakeSourceManager(TestSources.fake, TestSources.http, TestSources.updatable)`.
- Se añaden a `TestAppGraph` los accesores `val updateManga: UpdateManga`, `val setMangaCategories: SetMangaCategories`, `val createCategoryWithName: CreateCategoryWithName`, `val getLibraryManga: GetLibraryManga` y `val appScope: CoroutineScope`, este último anotado con `@AppCoroutineScope`, `mihon.core.metro.AppCoroutineScope`.

En `TestLibrary.kt`, añade:

```kotlin
    /**
     * A manga of [TestSources.updatable] (or of [sourceId], which may be an uninstalled source) in the library and in
     * [category], with its current chapters already synced.
     */
    suspend fun libraryManga(graph: TestAppGraph, category: Long, sourceId: Long = TestSources.updatable.id): Manga {
        val n = next.incrementAndGet()
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "/u/$n", title = "Manga José $n", source = sourceId),
        )
        if (sourceId == TestSources.updatable.id) {
            val chapters = TestSources.updatable.getMangaUpdate(manga.toSManga(), emptyList(), false, true).chapters
            graph.syncChaptersWithSource.await(chapters, manga, TestSources.updatable)
        }
        graph.updateManga.awaitUpdateFavorite(manga.id, true)
        graph.setMangaCategories.await(manga.id, listOf(category))
        return graph.getManga.await(manga.id)!!
    }

    /** A new category of its own, so each test updates only its manga. */
    suspend fun category(graph: TestAppGraph): Long {
        val name = "Categoría ${next.incrementAndGet()}"
        graph.createCategoryWithName.await(name)
        return graph.getCategories.await().first { it.name == name }.id
    }
```

Si `TestAppGraph` no expone `getManga`, añade `val getManga: GetManga`.

- [ ] **Step 5: Escribir los tests**

`desktop-app/src/test/kotlin/mihon/desktop/app/library/LibraryUpdateJobTest.kt`:

```kotlin
package mihon.desktop.app.library

import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import mihon.desktop.core.jobs.JobScheduler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

class LibraryUpdateJobTest {
    private lateinit var graph: TestAppGraph
    private lateinit var job: LibraryUpdateJob
    private val notices = CopyOnWriteArrayList<Notice>()

    @BeforeEach
    fun setUp() {
        graph = startTestApp()
        job = graph.libraryUpdateJob
        graph.libraryPreferences.autoUpdateMangaRestrictions.set(emptySet())
        TestSources.updatable.gate = null
        TestSources.updatable.failing.clear()
        TestSources.updatable.chapters.clear()
        TrayNotifier.sink = { notices += it }
    }

    @AfterEach
    fun tearDown() {
        TrayNotifier.sink = null
    }

    private suspend fun runUpdate(category: Long) {
        job.start(category) shouldBe true
        withTimeout(30.seconds) { job.awaitIdle() }
    }

    @Test
    fun `new chapters are saved, counted and notified`(): Unit = runBlocking {
        val category = TestLibrary.category(graph)
        val manga = TestLibrary.libraryManga(graph, category)
        val before = graph.libraryPreferences.newUpdatesCount.get()
        TestSources.updatable.chapters[manga.url] = 5

        runUpdate(category)

        graph.getChaptersByMangaId.await(manga.id).size shouldBe 5
        graph.libraryPreferences.newUpdatesCount.get() shouldBe before + 2
        notices.single().title shouldBe str(MR.strings.notification_new_chapters)
        notices.single().message shouldContain manga.title
        job.progress.value shouldBe null
    }

    @Test
    fun `only the requested category is updated`(): Unit = runBlocking {
        val mine = TestLibrary.category(graph)
        val other = TestLibrary.category(graph)
        val inMine = TestLibrary.libraryManga(graph, mine)
        val inOther = TestLibrary.libraryManga(graph, other)
        TestSources.updatable.chapters[inMine.url] = 4
        TestSources.updatable.chapters[inOther.url] = 4

        runUpdate(mine)

        graph.getChaptersByMangaId.await(inMine.id).size shouldBe 4
        graph.getChaptersByMangaId.await(inOther.id).size shouldBe 3
    }

    @Test
    fun `a manga with unread chapters is skipped when that restriction is on`(): Unit = runBlocking {
        graph.libraryPreferences.autoUpdateMangaRestrictions.set(setOf(LibraryPreferences.MANGA_HAS_UNREAD))
        val category = TestLibrary.category(graph)
        val manga = TestLibrary.libraryManga(graph, category)
        TestSources.updatable.chapters[manga.url] = 4

        runUpdate(category)

        graph.getChaptersByMangaId.await(manga.id).size shouldBe 3
    }

    @Test
    fun `failures, including an uninstalled source, are collected in the errors file and the rest still updates`():
        Unit = runBlocking {
        val category = TestLibrary.category(graph)
        val broken = TestLibrary.libraryManga(graph, category)
        val orphan = TestLibrary.libraryManga(graph, category, sourceId = 999L)
        val fine = TestLibrary.libraryManga(graph, category)
        TestSources.updatable.failing[broken.url] = IOException("Sin conexión con José")
        TestSources.updatable.chapters[fine.url] = 4

        runUpdate(category)

        graph.getChaptersByMangaId.await(fine.id).size shouldBe 4
        val errors = graph.appDirs.logs.resolve("update_errors.txt").readText()
        errors shouldContain broken.title
        errors shouldContain "Sin conexión con José"
        errors shouldContain orphan.title
        errors shouldContain str(MR.strings.loader_not_implemented_error)
        notices.filter { it.isError }.single().title shouldContain "2"
    }

    @Test
    fun `starting while one is running is refused`(): Unit = runBlocking {
        val category = TestLibrary.category(graph)
        TestLibrary.libraryManga(graph, category)
        val gate = CompletableDeferred<Unit>().also { TestSources.updatable.gate = it }

        job.start(category) shouldBe true
        job.start(category) shouldBe false
        job.startMetadata() shouldBe false

        gate.complete(Unit)
        withTimeout(30.seconds) { job.awaitIdle() }
        job.start(category) shouldBe true
        withTimeout(30.seconds) { job.awaitIdle() }
    }

    @Test
    fun `cancelling keeps what was saved, notifies nothing and clears the progress`(): Unit = runBlocking {
        val category = TestLibrary.category(graph)
        val manga = TestLibrary.libraryManga(graph, category)
        TestSources.updatable.chapters[manga.url] = 6
        TestSources.updatable.gate = CompletableDeferred()

        job.start(category) shouldBe true
        withTimeout(10.seconds) { job.progress.first { it?.current?.isNotEmpty() == true } }
        job.cancel()
        withTimeout(10.seconds) { job.awaitIdle() }

        job.progress.value shouldBe null
        notices.shouldBeEmpty()
        graph.getChaptersByMangaId.await(manga.id).size shouldBe 3
    }

    @Test
    fun `the scheduler runs the library update when its interval has passed`(): Unit = runBlocking {
        val category = TestLibrary.category(graph)
        val manga = TestLibrary.libraryManga(graph, category)
        TestSources.updatable.chapters[manga.url] = 4
        graph.libraryPreferences.autoUpdateInterval.set(12)
        graph.libraryPreferences.lastUpdatedTimestamp.set(0L)
        val scheduler = JobScheduler(listOf(job.periodicJob()))

        coroutineScope {
            scheduler.check(this) shouldContainExactly listOf("LibraryUpdate")
        }

        graph.getChaptersByMangaId.await(manga.id).size shouldBe 4
        graph.libraryPreferences.autoUpdateInterval.set(0)
    }
}
```

`str(...)` es el helper de `testing/Strings.kt`.

- [ ] **Step 6: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.library.LibraryUpdateJobTest"`
Expected: FAIL de compilación (`Unresolved reference 'LibraryUpdateJob'`).

- [ ] **Step 7: Implementar `LibraryUpdateJob`**

`desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt`:

```kotlin
package eu.kanade.tachiyomi.data.library

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.tray.TrayNotifier
import mihon.desktop.app.tray.errorsNotice
import mihon.desktop.app.tray.newChaptersNotice
import mihon.desktop.core.AppDirs
import mihon.desktop.core.jobs.PeriodicJob
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.SourceNotInstalledException
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import java.awt.Desktop
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createDirectories
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Mihon's LibraryUpdateWorker and MetadataUpdateWorker without WorkManager (spec 3a §3 F): one update at a time in the
 * app scope, [progress] for the in-app bar and the tray tooltip, notifications through [TrayNotifier].
 */
@Inject
@SingleIn(AppScope::class)
class LibraryUpdateJob(
    private val context: Context,
    private val appDirs: AppDirs,
    private val sourceManager: SourceManager,
    private val libraryPreferences: LibraryPreferences,
    private val getLibraryManga: GetLibraryManga,
    private val getManga: GetManga,
    private val fetchInterval: FetchInterval,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    /** [done] of [total] manga; [current] are the titles being fetched right now. */
    data class Progress(val done: Int, val total: Int, val current: List<String>)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    @Volatile
    private var running: Job? = null

    /** Updates the chapters of [categoryId]'s manga, or of the whole library. False if an update is already running. */
    fun start(categoryId: Long? = null): Boolean = launch { updateChapters(categoryId) }

    /** Mihon's "Refresh library metadata". Shares the slot with [start]. */
    fun startMetadata(): Boolean = launch { updateMetadata() }

    /** Stops at the next manga; what was already saved stays. */
    fun cancel() {
        running?.cancel()
    }

    suspend fun awaitIdle() {
        running?.join()
    }

    /** The automatic update, for the scheduler: [LibraryPreferences.autoUpdateInterval] hours, 0 = off. */
    fun periodicJob(): PeriodicJob = PeriodicJob(
        name = "LibraryUpdate",
        interval = { libraryPreferences.autoUpdateInterval.get().takeIf { it > 0 }?.hours },
        lastRun = libraryPreferences.lastUpdatedTimestamp,
        run = { if (start()) awaitIdle() },
    )

    private fun launch(block: suspend () -> Unit): Boolean = synchronized(this) {
        if (running?.isActive == true) return false
        running = scope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Library update failed" }
                AppMessages.show(context.stringResource(MR.strings.internal_error))
            } finally {
                _progress.value = null
            }
        }
        true
    }

    private suspend fun updateChapters(categoryId: Long?) {
        libraryPreferences.lastUpdatedTimestamp.set(Clock.System.now().toEpochMilliseconds())
        val timeZone = TimeZone.currentSystemDefault()
        val fetchWindow = fetchInterval.getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
        val queue = queue(categoryId, fetchWindow.endInclusive)
        warnIfQueueTooBig(queue)

        val newUpdates = CopyOnWriteArrayList<Pair<Manga, List<Chapter>>>()
        val failed = CopyOnWriteArrayList<Pair<Manga, String?>>()
        forEachWithProgress(queue) { manga ->
            // Don't continue to update if manga is not in library
            if (getManga.await(manga.id)?.favorite != true) return@forEachWithProgress
            try {
                val update = updateMangaFromRemote(
                    source = sourceManager.getOrStub(manga.source),
                    manga = manga,
                    fetchDetails = libraryPreferences.autoUpdateMetadata.get(),
                    fetchChapters = true,
                    fetchWindow = fetchWindow,
                ).getOrThrow()
                val newChapters = if (update.manga.favorite) update.newChapters else emptyList()
                if (newChapters.isNotEmpty()) {
                    // Sources update concurrently, and getAndSet is a separate read and write
                    synchronized(libraryPreferences.newUpdatesCount) {
                        libraryPreferences.newUpdatesCount.getAndSet { it + newChapters.size }
                    }
                    newUpdates += manga to newChapters.sortedByDescending { it.sourceOrder }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = when (e) {
                    is NoChaptersException -> context.stringResource(MR.strings.no_chapters_error)
                    // desktop: the same text the screens show, Cloudflare and the built-in browser included (spec 2b)
                    else -> with(context) { e.formattedMessage }
                }
                failed += manga to message
            }
        }

        if (newUpdates.isNotEmpty()) TrayNotifier.send(newChaptersNotice(context, newUpdates))
        if (failed.isNotEmpty()) {
            val file = writeErrorFile(failed)
            TrayNotifier.send(errorsNotice(context, failed.size))
            AppMessages.show(
                context.pluralStringResource(MR.plurals.notification_update_error, failed.size, failed.size),
                context.stringResource(MR.strings.action_open_log),
            ) { runCatching { Desktop.getDesktop().open(file) } }
        }
    }

    private suspend fun updateMetadata() {
        val queue = getLibraryManga.await()
        warnIfQueueTooBig(queue)
        forEachWithProgress(queue) { manga ->
            val source = sourceManager.get(manga.source) ?: return@forEachWithProgress
            // Errors are ignored and logged inside, like Mihon's MetadataUpdateWorker
            updateMangaFromRemote(source = source, manga = manga, fetchDetails = true)
        }
    }

    /** Mihon's addMangaToQueue: the requested category or the included and excluded ones, then the restrictions. */
    private suspend fun queue(categoryId: Long?, fetchWindowUpperBound: Instant): List<LibraryManga> {
        val libraryManga = getLibraryManga.await()
        val listToUpdate = if (categoryId != null) {
            libraryManga.filter { categoryId in it.categories }
        } else {
            val included = libraryPreferences.updateCategories.get().map { it.toLong() }
            val excluded = libraryPreferences.updateCategoriesExclude.get().map { it.toLong() }
            libraryManga.filter {
                (included.isEmpty() || it.categories.intersect(included).isNotEmpty()) &&
                    it.categories.intersect(excluded).isEmpty()
            }
        }
        val restrictions = libraryPreferences.autoUpdateMangaRestrictions.get()
        val skipped = mutableListOf<Pair<String, String>>()
        return listToUpdate
            .filter {
                val reason = when {
                    it.manga.updateStrategy == UpdateStrategy.ONLY_FETCH_ONCE && it.totalChapters > 0L ->
                        "not always update"
                    MANGA_NON_COMPLETED in restrictions && it.manga.status.toInt() == SManga.COMPLETED -> "completed"
                    MANGA_HAS_UNREAD in restrictions && it.unreadCount != 0L -> "not caught up"
                    MANGA_NON_READ in restrictions && it.totalChapters > 0L && !it.hasStarted -> "not started"
                    MANGA_OUTSIDE_RELEASE_PERIOD in restrictions &&
                        it.manga.nextUpdate.let { next -> next != null && next > fetchWindowUpperBound } ->
                        "not in release period"
                    else -> null
                }
                if (reason != null) skipped += reason to it.manga.title
                reason == null
            }
            .sortedBy { it.manga.title }
            .also {
                if (skipped.isNotEmpty()) {
                    logcat { skipped.groupBy({ it.first }, { it.second }).entries.joinToString { "${it.key}: ${it.value}" } }
                }
            }
    }

    /** Up to 5 sources at a time, one manga after another inside each, like Mihon. */
    private suspend fun forEachWithProgress(queue: List<LibraryManga>, block: suspend (Manga) -> Unit) {
        _progress.value = Progress(done = 0, total = queue.size, current = emptyList())
        val semaphore = Semaphore(5)
        coroutineScope {
            queue.groupBy { it.manga.source }.values
                .map { inSource ->
                    async {
                        semaphore.withPermit {
                            inSource.forEach { item ->
                                ensureActive()
                                val title = item.manga.title
                                _progress.update { it?.copy(current = it.current + title) }
                                block(item.manga)
                                ensureActive()
                                _progress.update { it?.copy(done = it.done + 1, current = it.current - title) }
                            }
                        }
                    }
                }
                .awaitAll()
        }
    }

    // desktop: an in-app message with a link instead of Mihon's warning notification
    private suspend fun warnIfQueueTooBig(queue: List<LibraryManga>) {
        val maxFromSource = queue.groupBy { it.manga.source }
            .filter { (sourceId, _) -> sourceManager.get(sourceId) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0
        if (maxFromSource <= MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD) return
        AppMessages.show(
            context.stringResource(MR.strings.notification_size_warning),
            context.stringResource(MR.strings.desktop_learn_more),
        ) { runCatching { Desktop.getDesktop().browse(java.net.URI(HELP_WARNING_URL)) } }
    }

    /** Mihon's error file format, in the logs folder (spec 3a §3 G). */
    private suspend fun writeErrorFile(errors: List<Pair<Manga, String?>>): File {
        val file = appDirs.logs.createDirectories().resolve("update_errors.txt").toFile()
        file.bufferedWriter().use { out ->
            out.write(context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n")
            // Error file format:
            // ! Error
            //   # Source
            //     - Manga
            errors.groupBy({ it.second }, { it.first }).forEach { (error, mangas) ->
                out.write("\n! ${error}\n")
                mangas.groupBy { it.source }.forEach { (srcId, inSource) ->
                    out.write("  # ${sourceManager.getOrStub(srcId)}\n")
                    inSource.forEach { out.write("    - ${it.title}\n") }
                }
            }
        }
        return file
    }

    private companion object {
        const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60
        const val ERROR_LOG_HELP_URL = "https://mihon.app/docs/guides/troubleshooting/"
        const val HELP_WARNING_URL =
            "https://mihon.app/docs/faq/library#why-am-i-warned-about-large-bulk-updates-and-downloads"
    }
}
```

Si una importación no resuelve (`getAndSet`, `UnmeteredSource`, `NoChaptersException`, `SourceNotInstalledException`), toma la ruta exacta de las importaciones de `app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateWorker.kt`.

Nota: `SourceNotInstalledException` no aparece en el `when`, porque `formattedMessage` ya devuelve `loader_not_implemented_error`.

- [ ] **Step 8: Accesores en `AppGraph`**

En `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt`, añade al interface (con sus importaciones):

```kotlin
    val libraryPreferences: LibraryPreferences
    val libraryUpdateJob: LibraryUpdateJob
    val getCategories: GetCategories
    val resetCategoryFlags: ResetCategoryFlags
```

`SettingsLibraryScreen` (Task 6) los usa a través de `context.appGraph`.

- [ ] **Step 9: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.library.LibraryUpdateJobTest" --tests "mihon.desktop.app.platform.AppMessagesTest"`
Expected: PASS (7 + los de `AppMessagesTest`).

- [ ] **Step 10: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): library update job with progress, error file and tray-or-in-app notifications

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Categorías y el diálogo de categorías al añadir

**Files:**
- Copy (de `app/src/main/java/` a `desktop-app/src/main/kotlin/`, misma ruta):
  - `eu/kanade/tachiyomi/ui/category/CategoryScreen.kt`
  - `eu/kanade/tachiyomi/ui/category/CategoryViewModel.kt`
  - `eu/kanade/presentation/category/CategoryScreen.kt`
  - `eu/kanade/presentation/category/CategoryExtensions.kt`
  - `eu/kanade/presentation/category/components/CategoryDialogs.kt`
  - `eu/kanade/presentation/category/components/CategoryListItem.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt` (diálogo `ChangeCategory` y `onEditCategoryClicked`)
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreen.kt` (diálogo `ChangeMangaCategory`)
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/history/HistoryTab.kt` (diálogo `ChangeCategory`)
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/library/CategoriesTest.kt`

**Interfaces:**
- Consumes: interactors de `tachiyomi.domain.category.interactor`, todos ya en `domain`.
- Produces:
  - `class CategoryScreen : Screen` (Mihon), con el `CategoryViewModel` de Mihon.
  - `ChangeCategoryDialog(initialSelection, onDismissRequest, onEditCategories, onConfirm)` (Mihon).

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class CategoriesTest {
    @Test
    fun `a category created on the categories screen is saved`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppProviders(graph) { Navigator(CategoryScreen()) } }

        onNodeWithText(str(MR.strings.action_add)).performClick()
        onNode(hasText(str(MR.strings.name))).performTextInput("Leyendo ñandú")
        onNodeWithText(str(MR.strings.action_ok)).performClick()

        waitUntil(10_000) { runBlocking { graph.getCategories.await() }.any { it.name == "Leyendo ñandú" } }
    }

    @Test
    fun `adding a manga to the library with categories asks which ones and saves the choice`() = runComposeUiTest {
        val graph = startTestApp()
        val category = runBlocking { TestLibrary.category(graph) }
        val name = runBlocking { graph.getCategories.await().first { it.id == category }.name }
        val (manga, _) = runBlocking { TestLibrary.httpManga(graph) }
        setContent { AppProviders(graph) { Navigator(MangaScreen(manga.id)) } }

        waitUntil(10_000) { onAllNodes(hasText(str(MR.strings.add_to_library))).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(str(MR.strings.add_to_library)).performClick()
        waitUntil(10_000) { onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(name).performClick()
        onNodeWithText(str(MR.strings.action_ok)).performClick()

        waitUntil(10_000) { runBlocking { graph.getManga.await(manga.id)!!.favorite } }
        runBlocking { graph.getCategories.await(manga.id) }.map { it.id } shouldContain category
    }
}
```

Etiquetas exactas:
- Botón de crear: `CategoryFloatingActionButton` usa `MR.strings.action_add`.
- Campo de nombre: `MR.strings.name`.
- Confirmar: `MR.strings.action_ok`.

Si alguna difiere, usa la que pongan los composables copiados y anótalo en el ledger. Si `GetCategories` no tiene `await(mangaId)`, usa el método de `GetCategories` que reciba un manga.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.library.CategoriesTest"`
Expected: FAIL de compilación (`Unresolved reference 'CategoryScreen'`).

- [ ] **Step 3: Copiar y adaptar las categorías**

Copia los 6 archivos de la lista. Adaptaciones:
- `ui/category/CategoryScreen.kt`:
  - si usa `LocalContext.current.toast(...)`, se queda: el shim existe;
  - si importa `eu.kanade.presentation.util.Screen`, se queda.
- `ui/category/CategoryViewModel.kt`: sin cambios. Usa `metroViewModel` o `@ContributesIntoMap @ViewModelKey`, igual que `HistoryViewModel`.
- `presentation/category/CategoryScreen.kt`:
  - el `ReorderableItem` u otro gesto de arrastre se queda si compila en JVM;
  - si depende de una librería solo-Android, se sustituyen el arrastre por las flechas que `CategoryListItem` ya trae (`onMoveUp`/`onMoveDown`), con `// desktop:`.
- `CategoryDialogs.kt`: sin cambios. Contiene `CategoryCreateDialog`, `CategoryRenameDialog`, `CategoryDeleteDialog`, `CategorySortAlphabeticallyDialog` y `ChangeCategoryDialog`.

- [ ] **Step 4: Dibujar el diálogo de categorías al añadir**

En `MangaScreen.kt` (desktop):

1. Cambia `onEditCategoryClicked = null,` por:

```kotlin
            onEditCategoryClicked = viewModel::showChangeCategoryDialog.takeIf { successState.manga.favorite },
```

y deja el comentario de esa zona como `// desktop: fetch interval and migration arrive in 3b, notes in subproject 5`.

2. En el `when (val dialog = successState.dialog)`, antes de `is MangaViewModel.Dialog.DeleteChapters`, añade el bloque de Mihon:

```kotlin
            is MangaViewModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        viewModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
```

y cambia el comentario del `else -> {}` final a `// desktop: no Migrate, TrackSheet or SetFetchInterval dialogs yet (3b and subproject 5)`.

En `BrowseSourceScreen.kt` (desktop), sustituye `// desktop: no ChangeMangaCategory dialog until categories (subproject 3)` por:

```kotlin
            is BrowseSourceViewModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        viewModel.changeMangaFavorite(dialog.manga)
                        viewModel.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
```

En `HistoryTab.kt` (desktop), cambia el comentario `// desktop: the category and migration dialogs come with subproject 3` y el `else -> {}` por:

```kotlin
            is HistoryViewModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        viewModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            // desktop: the migration dialog comes with 3b
            else -> {}
```

En los tres: importa `eu.kanade.presentation.category.components.ChangeCategoryDialog` y `eu.kanade.tachiyomi.ui.category.CategoryScreen`.

- [ ] **Step 5: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.library.CategoriesTest" --tests "mihon.desktop.app.browse.BrowseAndMangaTest" --tests "mihon.desktop.app.history.HistoryTabTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): categories screen, and adding to the library asks for categories like Mihon

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Pestaña Biblioteca, riel nuevo y atajos

**Files:**
- Copy (de `app/src/main/java/` a `desktop-app/src/main/kotlin/`):
  - `eu/kanade/tachiyomi/ui/library/LibraryTab.kt`
  - `eu/kanade/tachiyomi/ui/library/LibraryViewModel.kt`
  - `eu/kanade/tachiyomi/ui/library/LibrarySettingsViewModel.kt`
  - `eu/kanade/tachiyomi/ui/library/LibraryItem.kt`
  - `eu/kanade/presentation/library/DeleteLibraryMangaDialog.kt`
  - `eu/kanade/presentation/library/LibrarySettingsDialog.kt`
  - `eu/kanade/presentation/library/components/{GlobalSearchItem,LazyLibraryGrid,LibraryBadges,LibraryComfortableGrid,LibraryCompactGrid,LibraryContent,LibraryList,LibraryPager,LibraryTabs,LibraryToolbar}.kt` (`CommonMangaItem.kt` ya existe)
  - `mihon/feature/library/QueryNodeExtensions.kt`
  - `LibraryBottomActionMenu`, desde el archivo de Mihon donde esté (`grep -rn "fun LibraryBottomActionMenu" app/src/main/java`). Si vive en `MangaBottomActionMenu.kt`, que ya está portado, se copia solo esa función al archivo desktop existente.
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/SelectionClick.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/library/LibraryUpdateProgress.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/{HomeScreen,Shortcut,AppEvents}.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt` (`performSearch`)
- Modify: stubs (`TrackerManager`, `DownloadManager`) si el código copiado pide métodos que faltan
- Modify: tests que asumían Explorar como pestaña inicial
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/library/LibraryTabTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/platform/SelectionClickTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/main/ShortcutsTest.kt` (casos nuevos)

**Interfaces:**
- Consumes:
  - de la Task 2: `LibraryUpdateJob.start(categoryId)`, `.progress` y `.cancel()`;
  - de la Task 3: `CategoryScreen` y `ChangeCategoryDialog`.
- Produces:
  - `enum class SelectionClick { Open, Toggle, Range }`
  - `fun selectionClick(shift: Boolean, selecting: Boolean): SelectionClick`
  - `@Composable fun LibraryUpdateProgress(job: LibraryUpdateJob, modifier: Modifier = Modifier)`
  - `Shortcut.SelectAll`, `Shortcut.NextCategory`, `Shortcut.PreviousCategory`
  - `AppEvents.selectAll: SharedFlow<Unit>`, `AppEvents.categoryStep: SharedFlow<Int>`
  - `HomeScreen.search(query: String)`

- [ ] **Step 1: Escribir los tests de selección y atajos**

`SelectionClickTest.kt`:

```kotlin
package mihon.desktop.app.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SelectionClickTest {
    @Test
    fun `outside selection mode a click opens, even with shift`() {
        selectionClick(shift = false, selecting = false) shouldBe SelectionClick.Open
        selectionClick(shift = true, selecting = false) shouldBe SelectionClick.Open
    }

    @Test
    fun `in selection mode a click toggles and shift+click selects the range`() {
        selectionClick(shift = false, selecting = true) shouldBe SelectionClick.Toggle
        selectionClick(shift = true, selecting = true) shouldBe SelectionClick.Range
    }
}
```

En `ShortcutsTest.kt`, añade (con el estilo de los casos que ya tiene):

```kotlin
    @Test
    fun `library shortcuts`() {
        shortcutFor(Key.A, ctrl = true, alt = false, shift = false) shouldBe Shortcut.SelectAll
        shortcutFor(Key.Tab, ctrl = true, alt = false, shift = false) shouldBe Shortcut.NextCategory
        shortcutFor(Key.Tab, ctrl = true, alt = false, shift = true) shouldBe Shortcut.PreviousCategory
        shortcutFor(Key.A, ctrl = false, alt = false, shift = false) shouldBe null
    }
```

- [ ] **Step 2: Escribir el test de la pestaña**

`LibraryTabTest.kt`:

```kotlin
package mihon.desktop.app.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class LibraryTabTest {
    @Test
    fun `the app opens on the library, showing a manga in its category`() = runComposeUiTest {
        val graph = startTestApp()
        val category = runBlocking { TestLibrary.category(graph) }
        val name = runBlocking { graph.getCategories.await().first { it.id == category }.name }
        val manga = runBlocking { TestLibrary.libraryManga(graph, category) }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitUntil(10_000) { onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(name).performClick()
        waitUntil(10_000) { onAllNodes(hasText(manga.title)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `updating from the menu shows the progress, and a second update is refused`() = runComposeUiTest {
        val graph = startTestApp()
        val category = runBlocking { TestLibrary.category(graph) }
        runBlocking { TestLibrary.libraryManga(graph, category) }
        val gate = CompletableDeferred<Unit>().also { TestSources.updatable.gate = it }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitUntil(10_000) { onAllNodes(hasText(str(MR.strings.label_library))).fetchSemanticsNodes().isNotEmpty() }
        graph.libraryUpdateJob.start(category)
        waitUntil(10_000) { onAllNodes(hasText(str(MR.strings.action_cancel))).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText(str(MR.strings.action_cancel)).performClick()
        gate.complete(Unit)
        waitUntil(10_000) { onAllNodes(hasText(str(MR.strings.action_cancel))).fetchSemanticsNodes().isEmpty() }
        TestSources.updatable.gate = null
    }
}
```

El caso "un segundo intento sale como mensaje" lo cubre `start` en la Task 2. El snackbar usa la cadena de Mihon `update_already_running`, ya ligada en `LibraryTab`.

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.platform.SelectionClickTest" --tests "mihon.desktop.app.main.ShortcutsTest" --tests "mihon.desktop.app.library.LibraryTabTest"`
Expected: FAIL de compilación (`selectionClick`, `Shortcut.SelectAll` sin resolver).

- [ ] **Step 4: Selección y atajos**

`SelectionClick.kt`:

```kotlin
package mihon.desktop.app.platform

/** What a click on a list item does (spec 3a §3 A): Mihon's click, plus shift+click for a range while selecting. */
enum class SelectionClick { Open, Toggle, Range }

fun selectionClick(shift: Boolean, selecting: Boolean): SelectionClick = when {
    !selecting -> SelectionClick.Open
    shift -> SelectionClick.Range
    else -> SelectionClick.Toggle
}
```

En `Shortcut.kt`:
- añade `SelectAll(false)`, `NextCategory(true)` y `PreviousCategory(true)` al enum. `SelectAll` va después del componente enfocado para que el Ctrl+A de un campo de texto gane.
- en `shortcutFor`, antes de `else -> null`:

```kotlin
    key == Key.A && ctrl && !alt && !shift -> Shortcut.SelectAll
    key == Key.Tab && ctrl && !alt -> if (shift) Shortcut.PreviousCategory else Shortcut.NextCategory
```

En `AppEvents.kt`, añade:

```kotlin
    private val _selectAll = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val selectAll: SharedFlow<Unit> = _selectAll

    private val _categoryStep = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val categoryStep: SharedFlow<Int> = _categoryStep
```

y en `send`:

```kotlin
        Shortcut.SelectAll -> _selectAll.tryEmit(Unit)
        Shortcut.NextCategory -> _categoryStep.tryEmit(1)
        Shortcut.PreviousCategory -> _categoryStep.tryEmit(-1)
```

- [ ] **Step 5: Barra de progreso**

`desktop-app/src/main/kotlin/mihon/desktop/app/library/LibraryUpdateProgress.kt`:

```kotlin
package mihon.desktop.app.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** The running library update under the top bar, with Cancel (spec 3a §3 G). Nothing while none runs. */
@Composable
fun LibraryUpdateProgress(job: LibraryUpdateJob, modifier: Modifier = Modifier) {
    val progress by job.progress.collectAsState()
    val current = progress ?: return
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { if (current.total == 0) 0f else current.done.toFloat() / current.total },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    MR.strings.desktop_update_progress,
                    current.done,
                    current.total,
                    current.current.firstOrNull().orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = job::cancel) { Text(stringResource(MR.strings.action_cancel)) }
        }
    }
}
```

- [ ] **Step 6: Copiar y adaptar la biblioteca**

Copia los archivos de la lista. Adaptaciones, cada una con `// desktop:`:

**`LibraryTab.kt`**
- **Icono.** Igual que en `HistoryTab`: `icon = rememberVectorPainter(MaterialSymbols.Rounded.CollectionsBookmark)`, sin `AnimatedImageVector` ni `isSelected`.
- **`onReselect`** se queda: abre el diálogo de ajustes, como Mihon.
- **`onClickRefresh`**:
  - `val updateJob = remember { context.appGraph.libraryUpdateJob }`;
  - `val started = updateJob.start(category?.id)` en lugar de `LibraryUpdateWorker.startNow(...)`;
  - el resto del bloque, con los mensajes `update_already_running`, `updating_category` y `updating_library`, sin cambios.
- **`topBar`**: envuelve `LibraryToolbar(...)` en `Column { LibraryToolbar(...); LibraryUpdateProgress(updateJob) }`.
- **`LibraryToolbar`**: le pasa `onClickEditCategories = { navigator.push(CategoryScreen()) }` (ver `LibraryToolbar.kt` abajo).
- **`LibraryBottomActionMenu`**:
  - `onDownloadClicked = null`: las descargas llegan en el subproyecto 4;
  - `onMigrateClicked = null`: la migración llega en el 3b. Si el parámetro no es nulable en Mihon, hazlo nulable en la copia de `LibraryBottomActionMenu` y oculta su botón cuando sea `null`, como ya hace con `onDownloadClicked`.
- **`EmptyScreen`**: se queda con su acción de guía (abre la URL con `LocalUriHandler`, que funciona en escritorio).
- **`onContinueReadingClicked`**: `navigator.push(ReaderScreen(chapter.mangaId, chapter.id))` en lugar de `ReaderActivity`.
- **`DeleteLibraryMangaDialog`**: `containsLocalManga = false`. Sin fuente local hasta el subproyecto 4.
- **Se quitan:**
  - `HomeScreen.showBottomNav(...)`: el escritorio siempre tiene riel;
  - los `LaunchedEffect`/`ReportDrawnWhen` de `MainActivity.ready`.
- **Se añade** al `LaunchedEffect(Unit)` final:

```kotlin
            // desktop: F5 updates the current category, Ctrl+A selects all of it (spec 3a §3 A)
            launch { AppEvents.refresh.collect { onClickRefresh(viewModel.state.value.activeCategory) } }
            launch { AppEvents.selectAll.collect { viewModel.selectAll() } }
```

**`LibraryViewModel.kt`**
- Sin cambios de lógica.
- Si pide `trackerManager.getAll(ids)` o `downloadManager.getDownloadCounts(...)`, añade a los stubs:
  - `fun getAll(ids: Set<Long>): List<Tracker> = emptyList()` en `TrackerManager`;
  - `fun getDownloadCounts(mangas: List<Manga>): Map<Long, Int> = emptyMap()` en `DownloadManager`.
  - Copia la firma exacta de la llamada.
- Si pide otros miembros de `Tracker`, se añaden al interface stub con valores por defecto.

**`LibraryContent.kt`**
- `val windowInfo = LocalWindowInfo.current` al principio, y el `onClickManga` del `LibraryPager` pasa a ser:

```kotlin
                onClickManga = { category, manga ->
                    // desktop: shift+click selects the range while selecting (spec 3a §3 A)
                    when (selectionClick(windowInfo.keyboardModifiers.isShiftPressed, selection.isNotEmpty())) {
                        SelectionClick.Open -> onClickManga(manga.manga.id)
                        SelectionClick.Toggle -> onToggleSelection(category, manga)
                        SelectionClick.Range -> onToggleRangeSelection(category, manga)
                    }
                },
```

- Después de `LaunchedEffect(pagerState.currentPage)`:

```kotlin
        // desktop: Ctrl+Tab and Ctrl+Shift+Tab move between categories
        LaunchedEffect(pagerState, categories.size) {
            AppEvents.categoryStep.collect { step ->
                if (categories.isNotEmpty()) {
                    pagerState.animateScrollToPage((pagerState.currentPage + step).mod(categories.size))
                }
            }
        }
```

**`LibraryPager.kt`**: Mihon elige las columnas por orientación. El escritorio usa el ancho de la ventana:

```kotlin
// desktop: no device orientation; a window wider than tall uses the landscape columns
val isLandscape = LocalWindowInfo.current.containerSize.let { it.width > it.height }
```

Ese valor sustituye al `LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE` de Mihon.

**`LibraryToolbar.kt`**: añade el parámetro `onClickEditCategories: () -> Unit` y, después de la `OverflowAction` "abrir aleatorio":

```kotlin
                    // desktop: Mihon's More tab doesn't exist here (plan 3a, deviation 3)
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.action_edit_categories),
                        onClick = onClickEditCategories,
                    ),
```

**`LibrarySettingsDialog.kt`**, en la pestaña de filtros:
- Se quita el `TriStateItem` con `label = stringResource(MR.strings.label_downloaded)`, con `// desktop: downloads in subproject 4`.
- El bloque de trackers se deja: queda vacío porque `trackers` es una lista vacía.

**`CommonMangaItem.kt`**: ya portado; no se toca.

- [ ] **Step 7: Riel, búsqueda de biblioteca y pestaña inicial**

`HomeScreen.kt`:

```kotlin
object HomeScreen : Screen() {
    /** In rail order (Mihon's); the first one opens at start. Settings must stay last for [AppEvents.openSettings]. */
    val tabs: List<Tab> = listOf(LibraryTab, UpdatesTab, HistoryTab, BrowseTab, SettingsTab)

    private val librarySearch = Channel<String>()

    /** Mihon's HomeScreen.search: opens the library and searches it (from a manga's tag or title). */
    suspend fun search(query: String) = librarySearch.send(query)

    @Composable
    override fun Content() {
        val tabNavigator = LocalTabNavigator.current
        val context = LocalContext.current
        val libraryPreferences = remember { context.appGraph.libraryPreferences }
        val newUpdates by libraryPreferences.newUpdatesCount.collectAsState()
        val showNewUpdates by libraryPreferences.newShowUpdatesCount.collectAsState()
        LaunchedEffect(Unit) {
            librarySearch.receiveAsFlow().collect {
                tabNavigator.current = LibraryTab
                LibraryTab.search(it)
            }
        }
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                tabs.forEach { tab ->
                    val options = tab.options
                    NavigationRailItem(
                        selected = tabNavigator.current.key == tab.key,
                        onClick = { tabNavigator.current = tab },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == UpdatesTab && showNewUpdates && newUpdates > 0) {
                                        Badge { Text("$newUpdates") }
                                    }
                                },
                            ) { options.icon?.let { Icon(it, contentDescription = options.title) } }
                        },
                        label = { Text(options.title) },
                    )
                }
            }
            CurrentTab()
        }
    }
}
```

Notas sobre `HomeScreen.kt`:
- `appGraph` es la extensión `val Context.appGraph` de `mihon.app.di`, y `LocalContext` el shim de `presentation-core`.
- `collectAsState` viene de `tachiyomi.presentation.core.util`.
- `UpdatesTab` llega en la Task 5. Para que esta task compile sola, pon la lista sin `UpdatesTab` y la condición del badge en `false`; la Task 5 los añade.

En `MangaScreen.kt` (desktop), en `performSearch`, cambia el comentario `// desktop: Mihon's HomeScreen.search ...` por la rama de Mihon:

```kotlin
            is HomeScreen -> {
                navigator.pop()
                HomeScreen.search(query)
            }
```

e importa `mihon.desktop.app.main.HomeScreen`.

Los tests que daban por hecho que la app abre en Explorar empiezan pulsando el riel `str(MR.strings.browse)`, igual que `HistoryTabTest` pulsa su pestaña. Búscalos con `grep -rln "AppContent(graph" desktop-app/src/test`. Ejecuta la suite y arregla solo los que fallen por esto.

- [ ] **Step 8: Comprobar que pasan**

Run: `./gradlew :desktop-app:test`
Expected: PASS, toda la suite de desktop-app.

- [ ] **Step 9: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): library tab with categories, selection, F5, Ctrl+A and Ctrl+Tab; the app opens on it

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Pestaña Novedades

**Files:**
- Copy (de `app/src/main/java/` a `desktop-app/src/main/kotlin/`):
  - `eu/kanade/tachiyomi/ui/updates/UpdatesTab.kt`
  - `eu/kanade/tachiyomi/ui/updates/UpdatesViewModel.kt`
  - `eu/kanade/tachiyomi/ui/updates/UpdatesSettingsViewModel.kt`
  - `eu/kanade/presentation/updates/{UpdatesScreen,UpdatesUiItem,UpdatesFilterDialog}.kt`
  - `UpdatesDeleteConfirmationDialog.kt` no se copia: borrar descargas llega en el subproyecto 4.
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/HomeScreen.kt` (añadir `UpdatesTab` y la condición del badge)
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/library/UpdatesTabTest.kt`

**Interfaces:**
- Consumes:
  - de la Task 2: `LibraryUpdateJob.start()`;
  - de la Task 4: `LibraryUpdateProgress`, `selectionClick` y `AppEvents.refresh`/`selectAll`.
- Produces: `UpdatesTab` (Mihon).

- [ ] **Step 1: Escribir el test**

```kotlin
package mihon.desktop.app.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.reader.READER_TAG
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class UpdatesTabTest {
    @Test
    fun `a new chapter is listed, opening the tab clears the badge count, and clicking it opens the reader`() =
        runComposeUiTest {
            val graph = startTestApp()
            val category = runBlocking { TestLibrary.category(graph) }
            val manga = runBlocking { TestLibrary.libraryManga(graph, category) }
            TestSources.updatable.chapters[manga.url] = 4
            runBlocking {
                graph.libraryUpdateJob.start(category)
                kotlinx.coroutines.withTimeout(30.seconds) { graph.libraryUpdateJob.awaitIdle() }
            }
            setContent { AppContent(graph, crash = null, onRestart = {}) }

            onNodeWithText(str(MR.strings.label_recent_updates)).performClick()
            waitUntil(10_000) { onAllNodes(hasText("Capítulo 4")).fetchSemanticsNodes().isNotEmpty() }
            waitUntil(10_000) { graph.libraryPreferences.newUpdatesCount.get() == 0 }

            onAllNodesWithText("Capítulo 4").onFirst().performClick()
            waitUntil(15_000) { onAllNodes(hasTestTag(READER_TAG)).fetchSemanticsNodes().isNotEmpty() }
        }
}
```

Nota: `FakeHttpSource` no sirve aquí: el lector necesita páginas y `UpdatableSource` devuelve `emptyList()`.
- Si el lector muestra un error en lugar del tag con 0 páginas, el último paso comprueba que se navegó al `ReaderScreen` (el tag `READER_TAG` está en su raíz).
- Si no lo está, cambia `UpdatableSource.getPageList` para devolver una página de `TestSources.images` y apúntalo en el ledger.

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.library.UpdatesTabTest"`
Expected: FAIL. Sin `UpdatesTab` en el riel no aparece el texto `label_recent_updates`, y `waitUntil` agota el tiempo.

- [ ] **Step 3: Copiar y adaptar**

Adaptaciones, cada una con `// desktop:`:

**`UpdatesTab.kt`**
- **Icono:** `rememberVectorPainter(MaterialSymbols.Rounded.Autorenew)`.
- **`onReselect`**: se quita (la cola de descargas llega en el subproyecto 4).
- **`onOpenChapter`**: `navigator.push(ReaderScreen(it.update.mangaId, it.update.chapterId))`.
- **Parámetros nulos o sin acción:**
  - `onCalendarClicked = null`: Próximos llega en el 3b. Si el parámetro de `UpdateScreen` no es nulable, hazlo nulable en la copia y oculta su acción en `UpdatesAppBar` cuando sea `null`.
  - `onDownloadChapter = null` y `onMultiDeleteClicked = null`, igual: nulables en la copia, con la UI oculta cuando son `null`.
- **Diálogos:** se quita el caso `DeleteConfirmation` del `when (dialog)`.
- **Se quitan** `HomeScreen.showBottomNav` y `MainActivity.ready`.
- **Se añade** dentro del `LaunchedEffect(Unit)`:

```kotlin
            // desktop: F5 updates the whole library, Ctrl+A selects every update (spec 3a §3 C)
            launch { AppEvents.refresh.collect { viewModel.updateLibrary() } }
            launch { AppEvents.selectAll.collect { viewModel.toggleAllSelection(true) } }
```

**`UpdatesViewModel.kt`**
- Añade al constructor `private val libraryUpdateJob: LibraryUpdateJob` con `// desktop:`.
- `updateLibrary()` usa `libraryUpdateJob.start()` en lugar de `LibraryUpdateWorker.startNow(...)`. El evento `LibraryUpdateTriggered(started)` se queda.
- El resto, con descargas incluidas, compila contra los stubs.

**`UpdatesScreen.kt`**
- En `topBar`, debajo de `UpdatesAppBar(...)`: `LibraryUpdateProgress(remember { context.appGraph.libraryUpdateJob })`, dentro de una `Column`, con `val context = LocalContext.current`.
- En `UpdatesFilterDialog`, se quita el `TriStateItem` con `label = stringResource(MR.strings.label_downloaded)`, con `// desktop: downloads in subproject 4`.

**`UpdatesUiItem.kt`**
- Donde Mihon decide el clic con selección (`onClick = { if (selectionMode) onUpdateSelected(item, !item.selected, false) else onClickUpdate(item) }`, o la forma que tenga en el archivo), usa `selectionClick(LocalWindowInfo.current.keyboardModifiers.isShiftPressed, selectionMode)`:
  - `Open` = `onClickUpdate(item)`;
  - `Toggle` = `onUpdateSelected(item, !item.selected, false)`;
  - `Range` = `onUpdateSelected(item, true, true)`.
- Los gestos de deslizar (`SwipeableActionsBox` o equivalente) se quitan con `// desktop: no swipe gestures on desktop`, dejando el contenido del item.
- El indicador de descarga recibe `onDownloadChapter = null`, que lo oculta.

**`HomeScreen.kt`**: la lista pasa a ser `listOf(LibraryTab, UpdatesTab, HistoryTab, BrowseTab, SettingsTab)` y el badge usa `tab == UpdatesTab`.

- [ ] **Step 4: Comprobar que pasa**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.library.UpdatesTabTest" --tests "mihon.desktop.app.main.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): updates tab with filters, selection, F5 and the new-chapters badge

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Ajustes › Biblioteca, actualizar metadatos y arranque del planificador

**Files:**
- Copy: `eu/kanade/presentation/more/settings/screen/SettingsLibraryScreen.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/tray/TrayPreferences.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsTab.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/AdvancedSettingsScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt` (`trayPreferences`)
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt` (arranque del planificador)
- Modify: `i18n` (base y es)
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/settings/SettingsTest.kt` (casos nuevos)

**Interfaces:**
- Consumes:
  - de la Task 1: `JobScheduler`;
  - de la Task 2: `LibraryUpdateJob.periodicJob()` y `.startMetadata()`;
  - de la Task 3: `CategoryScreen`.
- Produces:
  - `class TrayPreferences(preferenceStore)`, con `closeToTray: Preference<Boolean>` (`desktop_close_to_tray`, false) y `hintShown: Preference<Boolean>` (`desktop_tray_hint_shown`, false)
  - `AppGraph.trayPreferences`

- [ ] **Step 1: Textos**

En `base/strings.xml`:

```xml
    <string name="desktop_close_to_tray">Close to the system tray</string>
    <string name="desktop_close_to_tray_summary">The app keeps running in the tray so the library updates with the window closed</string>
```

En `es/strings.xml`:

```xml
    <string name="desktop_close_to_tray">Cerrar a la bandeja del sistema</string>
    <string name="desktop_close_to_tray_summary">La app sigue en la bandeja y la biblioteca se actualiza con la ventana cerrada</string>
```

- [ ] **Step 2: Escribir los tests**

En `SettingsTest.kt`, con el estilo de los casos que ya tiene (Navigator sobre la pantalla y `AppProviders`):

```kotlin
    @Test
    fun `the library settings save the update interval where the scheduler reads it`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppProviders(graph) { Navigator(SettingsLibraryScreen) } }

        onNodeWithText(str(MR.strings.pref_library_update_interval)).performClick()
        onNodeWithText(str(MR.strings.update_12hour)).performClick()

        waitUntil(5_000) { graph.libraryPreferences.autoUpdateInterval.get() == 12 }
        graph.libraryUpdateJob.periodicJob().interval() shouldBe 12.hours
        graph.libraryPreferences.autoUpdateInterval.set(0)
    }

    @Test
    fun `close to tray is a library setting`() = runComposeUiTest {
        val graph = startTestApp()
        graph.trayPreferences.traySupported = true
        setContent { AppProviders(graph) { Navigator(SettingsLibraryScreen) } }

        onNodeWithText(str(MR.strings.desktop_close_to_tray)).performClick()

        waitUntil(5_000) { graph.trayPreferences.closeToTray.get() }
        graph.trayPreferences.closeToTray.set(false)
    }
```

Notas:
- El nombre exacto de la opción de 12 h es el que use Mihon en `SettingsLibraryScreen` (`MR.strings.update_12hour`).
- Si la fila no es visible sin desplazarse, usa `performScrollToNode(hasText(...))` sobre la lista, como ya hacen otros tests de ajustes.
- En el entorno de test `SystemTray.isSupported()` puede ser `false`. Por eso la condición de visibilidad del ajuste se lee de `TrayPreferences.traySupported` (ver Step 3), que el test fija a `true` antes de `setContent`, con `graph.trayPreferences.traySupported = true`.

- [ ] **Step 3: Implementar**

`TrayPreferences.kt`:

```kotlin
package mihon.desktop.app.tray

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.awt.SystemTray

@Inject
@SingleIn(AppScope::class)
class TrayPreferences(preferenceStore: PreferenceStore) {
    val closeToTray: Preference<Boolean> = preferenceStore.getBoolean("desktop_close_to_tray", false)

    /** The "still in the tray" notification is shown once. */
    val hintShown: Preference<Boolean> = preferenceStore.getBoolean(Preference.appStateKey("desktop_tray_hint_shown"), false)

    /** Without a tray (some Windows shells, headless tests) there's nothing to close to. Tests may set it. */
    @Volatile
    var traySupported: Boolean = runCatching { SystemTray.isSupported() }.getOrDefault(false)
}
```

Añade `val trayPreferences: TrayPreferences` a `AppGraph`.

Copia `SettingsLibraryScreen.kt`. Adaptaciones, con `// desktop:`:
- **Actualización periódica:**
  - Quita las dos llamadas `LibraryUpdateWorker.setupTask(context, ...)`. El planificador lee la preferencia en cada revisión y no necesita programar nada; el `onValueChanged` devuelve `true`.
  - Quita el `MultiSelectListPreference` de las restricciones de dispositivo (`autoUpdateDeviceRestrictions`).
  - Añade al final del grupo:

```kotlin
                // desktop: closing the window can leave the app in the tray (spec 3a §3 D)
                Preference.PreferenceItem.SwitchPreference(
                    preference = trayPreferences.closeToTray,
                    title = stringResource(MR.strings.desktop_close_to_tray),
                    subtitle = stringResource(MR.strings.desktop_close_to_tray_summary),
                    enabled = trayPreferences.traySupported,
                ),
```

  con `val trayPreferences = remember { context.appGraph.trayPreferences }` donde se leen las demás.
- **Se ocultan los gestos de deslizar.** Quita del `listOf(...)` de `getPreferences()` el grupo de acciones de deslizar de capítulos (`getChapterSwipeActionsGroup` o el nombre que tenga en el archivo). Si sus items están dentro de otro grupo, quita solo esos items.
- Lo demás (categorías, `ChangeCategoryDialog`/`CategoryScreen`, `resetCategoryFlags`, comportamiento) no cambia.

En `SettingsTab.kt`, añade como primera entrada:

```kotlin
            Triple(MR.strings.pref_category_library, MaterialSymbols.Rounded.CollectionsBookmark, SettingsLibraryScreen),
```

En `AdvancedSettingsScreen.kt`, junto a los demás items de datos (con el patrón de `TextPreference` que ya usa), añade:

```kotlin
                // desktop: Mihon's "Refresh library metadata", on the library update job (spec 3a §3 D)
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_refresh_library_covers),
                    onClick = {
                        val started = context.appGraph.libraryUpdateJob.startMetadata()
                        context.toast(if (started) MR.strings.updating_library else MR.strings.update_already_running)
                    },
                ),
```

Si `context.toast(StringResource)` no existe en el shim, usa `AppMessages.show(context.stringResource(...))`.

En `Main.kt`, justo después de `graph.mihonExtensionManager`:

```kotlin
    // Periodic jobs while the app is open or in the tray (parent spec §5.1); only the library update for now
    JobScheduler(listOf(graph.libraryUpdateJob.periodicJob())).start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
```

con `import mihon.desktop.core.jobs.JobScheduler`.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.settings.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): library settings, refresh metadata, and the scheduler runs the periodic update

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Bandeja, cerrar a la bandeja y notificaciones del sistema

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/tray/AppTray.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/AppEvents.kt` (`openUpdates`)
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/AppContent.kt` (recoger `openUpdates`)
- Modify: `i18n` (base y es)
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/tray/TrayTest.kt`

**Interfaces:**
- Consumes:
  - de la Task 2: `TrayNotifier`, `Notice` y `LibraryUpdateJob`;
  - de la Task 6: `TrayPreferences`.
- Produces:
  - `enum class CloseAction { Hide, Exit }`
  - `fun closeAction(traySupported: Boolean, closeToTray: Boolean): CloseAction`
  - `@Composable fun ApplicationScope.AppTray(graph: AppGraph, onOpen: () -> Unit, onExit: () -> Unit)`
  - `AppEvents.openUpdates()` y `AppEvents.updatesRequests: SharedFlow<Unit>`

- [ ] **Step 1: Textos**

En `base/strings.xml`:

```xml
    <string name="desktop_tray_open">Open</string>
    <string name="desktop_tray_exit">Exit</string>
    <string name="desktop_tray_hint">Still running in the tray; the library will keep updating</string>
```

En `es/strings.xml`:

```xml
    <string name="desktop_tray_open">Abrir</string>
    <string name="desktop_tray_exit">Salir</string>
    <string name="desktop_tray_hint">Sigue en la bandeja; la biblioteca se seguirá actualizando</string>
```

- [ ] **Step 2: Escribir los tests**

```kotlin
package mihon.desktop.app.tray

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class TrayTest {
    @Test
    fun `the close button hides to the tray only when it exists and the setting is on`() {
        closeAction(traySupported = true, closeToTray = true) shouldBe CloseAction.Hide
        closeAction(traySupported = true, closeToTray = false) shouldBe CloseAction.Exit
        closeAction(traySupported = false, closeToTray = true) shouldBe CloseAction.Exit
    }

    @Test
    fun `without a tray a notification becomes an in-app message`(): Unit = runBlocking {
        startTestApp()
        TrayNotifier.sink = null
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5.seconds) { AppMessages.messages.first() }
        }

        TrayNotifier.send(Notice("Capítulos nuevos", "Manga José"))

        received.await().text shouldBe "Capítulos nuevos\nManga José"
        TrayNotifier.unread shouldBe false
    }

    @Test
    fun `a tray notification marks the updates as unread until the window opens on them`() {
        val sent = mutableListOf<Notice>()
        TrayNotifier.sink = { sent += it }
        TrayNotifier.unread = false

        TrayNotifier.send(Notice("Capítulos nuevos", "Manga José"))

        sent.single().title shouldBe "Capítulos nuevos"
        TrayNotifier.unread shouldBe true
        TrayNotifier.sink = null
        TrayNotifier.unread = false
    }

    @Test
    fun `asking for the updates tab opens it`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }
        waitUntil(10_000) { onAllNodes(hasText(str(MR.strings.label_library))).fetchSemanticsNodes().isNotEmpty() }

        AppEvents.openUpdates()

        // The updates screen's title (Mihon's UpdatesAppBar) is the tab's label
        waitUntil(10_000) { onAllNodes(hasText(str(MR.strings.label_recent_updates))).fetchSemanticsNodes().size >= 2 }
    }
}
```

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.tray.TrayTest"`
Expected: FAIL de compilación (`closeAction`, `AppEvents.openUpdates` sin resolver).

- [ ] **Step 4: Implementar la bandeja**

`AppTray.kt`:

```kotlin
package mihon.desktop.app.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import mihon.app.di.AppGraph
import mihon.desktop.core.AppDirs
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CollectionsBookmark
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

enum class CloseAction { Hide, Exit }

/** What the window's close button does (spec 3a §3 G). */
fun closeAction(traySupported: Boolean, closeToTray: Boolean): CloseAction =
    if (traySupported && closeToTray) CloseAction.Hide else CloseAction.Exit

/**
 * The tray icon while the system has a tray: Open, Update library, Exit; double click (and any notification click,
 * AWT gives them the same action) opens the window. While it's up, [TrayNotifier] sends here.
 */
@Composable
fun ApplicationScope.AppTray(graph: AppGraph, onOpen: () -> Unit, onExit: () -> Unit) {
    if (!graph.trayPreferences.traySupported) return
    val context = graph.context
    val state = rememberTrayState()
    DisposableEffect(state) {
        TrayNotifier.sink = {
            state.sendNotification(
                Notification(it.title, it.message, if (it.isError) Notification.Type.Error else Notification.Type.Info),
            )
        }
        onDispose { TrayNotifier.sink = null }
    }
    val progress by graph.libraryUpdateJob.progress.collectAsState()
    val tooltip = progress?.let {
        context.stringResource(MR.strings.desktop_update_progress, it.done, it.total, it.current.firstOrNull().orEmpty())
    } ?: AppDirs.APP_NAME
    val vector = rememberVectorPainter(MaterialSymbols.Rounded.CollectionsBookmark)
    // ponytail: a tinted symbol until the app has its own icon (packaging), plan 3a deviation 2
    val icon = remember(vector) { TintedPainter(vector, TRAY_TINT) }
    Tray(
        icon = icon,
        state = state,
        tooltip = tooltip,
        onAction = onOpen,
        menu = {
            Item(context.stringResource(MR.strings.desktop_tray_open), onClick = onOpen)
            Item(context.stringResource(MR.strings.action_update_library), onClick = { graph.libraryUpdateJob.start() })
            Separator()
            Item(context.stringResource(MR.strings.desktop_tray_exit), onClick = onExit)
        },
    )
}

private val TRAY_TINT = Color(0xFF3B82F6)

/** Draws [inner] in one color, so a black symbol shows on light and dark taskbars. */
private class TintedPainter(private val inner: Painter, private val tint: Color) : Painter() {
    override val intrinsicSize: Size get() = inner.intrinsicSize

    override fun DrawScope.onDraw() {
        with(inner) { draw(size, colorFilter = ColorFilter.tint(tint)) }
    }
}
```

En `AppEvents.kt`:

```kotlin
    private val _updatesRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updatesRequests: SharedFlow<Unit> = _updatesRequests

    /** From a tray notification click: the Updates tab, over whatever screen is open. */
    fun openUpdates() {
        _updatesRequests.tryEmit(Unit)
    }
```

En `AppContent.kt`, en el `LaunchedEffect(navigator)`, junto a `globalSearch`:

```kotlin
                            launch {
                                AppEvents.updatesRequests.collect {
                                    navigator.popUntilRoot()
                                    tabNavigator.current = UpdatesTab
                                }
                            }
```

e importa `eu.kanade.tachiyomi.ui.updates.UpdatesTab`.

- [ ] **Step 5: Ventana oculta, mostrar y salir en `Main.kt`**

Dentro de `application { ... }`:

1. Después de `var closing by remember { mutableStateOf(false) }`:

```kotlin
        var windowVisible by remember { mutableStateOf(true) }
        // Exit from the close button (no tray) or the tray's Exit: the reader's last write first, and the update stops
        val exit = {
            if (!closing) {
                closing = true
                graph.libraryUpdateJob.cancel()
                exitScope.launch {
                    ExitHooks.runAll()
                    windowStore.save(windowControls.toSaved())
                    instance.close()
                    exitApplication()
                }
            }
        }
        val open = { bringToFront.tryEmit(Unit) }
```

2. `Window(` recibe `visible = windowVisible,` y su `onCloseRequest` pasa a ser:

```kotlin
                onCloseRequest = {
                    val trayPreferences = graph.trayPreferences
                    when (closeAction(trayPreferences.traySupported, trayPreferences.closeToTray.get())) {
                        CloseAction.Exit -> exit()
                        CloseAction.Hide -> {
                            windowVisible = false
                            if (!trayPreferences.hintShown.get()) {
                                trayPreferences.hintShown.set(true)
                                TrayNotifier.send(
                                    Notice(
                                        AppDirs.APP_NAME,
                                        graph.context.stringResource(MR.strings.desktop_tray_hint),
                                        opensUpdates = false,
                                    ),
                                )
                            }
                        }
                    }
                },
```

3. El `bringToFront.collect { ... }` existente empieza mostrando la ventana y, si había una notificación sin ver, abre Novedades:

```kotlin
                    bringToFront.collect {
                        windowVisible = true
                        windowState.isMinimized = false
                        // Windows doesn't let a background process take the foreground with toFront() alone
                        window.isAlwaysOnTop = true
                        window.toFront()
                        window.isAlwaysOnTop = false
                        if (TrayNotifier.unread) {
                            TrayNotifier.unread = false
                            AppEvents.openUpdates()
                        }
                    }
```

4. Después de `EmbeddedBrowserWindow(graph)`:

```kotlin
            // The tray icon, notifications and close to tray (spec 3a §3 G)
            AppTray(graph, onOpen = { open() }, onExit = exit)
```

Importa `mihon.desktop.app.tray.*`, `mihon.desktop.app.main.AppEvents` y `tachiyomi.core.common.i18n.stringResource`.

`AppGraph` ya expone `trayPreferences` (Task 6) y `libraryUpdateJob` (Task 2), y `DesktopAppGraph` extiende `AppGraph`.

- [ ] **Step 6: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.tray.TrayTest"`
Expected: PASS, 4 tests.

- [ ] **Step 7: Arranque real**

Run: `./gradlew :desktop-app:run` y ciérrala desde la bandeja, con Salir.
Expected: la ventana abre en Biblioteca y aparece el icono en la bandeja. Salir cierra el proceso y `logs` no tiene errores nuevos.

Esto es el arranque de comprobación; la prueba manual completa es la Task 8. Sin escritorio interactivo, se pasa a la Task 8 y se apunta en el ledger.

- [ ] **Step 8: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): tray icon with menu, close to tray, and new-chapter and error notifications

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Verificación completa y prueba manual del 3a

**Files:**
- Modify: ninguno, salvo los arreglos que salgan

- [ ] **Step 1: Suite completa y checks**

Run: `./gradlew test spotlessCheck`
Expected: BUILD SUCCESSFUL. Unos 340 tests: los 310 de antes más los nuevos.

Run: `bash scripts/check-no-android.sh` y `bash scripts/check-licenses.sh`
Expected: los dos OK.

- [ ] **Step 2: Ninguna referencia pendiente al subproyecto 3**

Run: `grep -rn "subproject 3" desktop-app/src desktop-core/src`
Expected: solo comentarios que apuntan a migración (ahora "3b"). Cambia a "3b" los que digan "subproject 3" y traten de migración; los demás deben haber desaparecido.

- [ ] **Step 3: Commit de los arreglos (si los hay)**

```bash
git add -A desktop-app desktop-core
git commit -m "chore: point the remaining migration notes at subproject 3b

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Prueba manual (spec §5), al usuario**

Pasa al usuario esta lista para ejecutar con `./gradlew :desktop-app:run`:
1. Ajustes › Biblioteca: activar "Cerrar a la bandeja". Cerrar con la X: la ventana se oculta y sale la notificación "Sigue en la bandeja". Volver con Abrir en la bandeja y también arrancando la app otra vez.
2. Con un manga de la biblioteca que tenga capítulos nuevos, "Actualizar biblioteca" desde la bandeja: sale la notificación "Nuevos capítulos encontrados".
3. Clic en la notificación: abre la ventana en Novedades.
4. Abrir un capítulo, y Salir desde la bandeja: la app se cierra y, al volver, el capítulo sigue donde se dejó.
5. F5 en Biblioteca (actualiza la categoría) y en Novedades (toda la biblioteca), con la barra de progreso y Cancelar.
