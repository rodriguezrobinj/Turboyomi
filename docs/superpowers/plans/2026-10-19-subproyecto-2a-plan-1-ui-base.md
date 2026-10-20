# Subproyecto 2a, plan 1 de 2: base de UI y pantallas de Mihon en escritorio. Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Abrir una ventana Compose Desktop con las pantallas de Mihon portadas: Explorar (fuentes y extensiones, con sus repositorios, iconos y filtro de contenido), navegar una fuente, búsqueda global, detalles del manga y Ajustes. La app se ejecuta con `gradlew :desktop-app:run`.

**Architecture:** Un módulo nuevo, `desktop-app`, contiene la ventana y es la hoja del grafo Metro (`DesktopAppGraph`). Los archivos de Mihon se copian del módulo `app` con el mismo paquete y la misma ruta relativa, y se cambia lo mínimo. Lo que Mihon resuelve con Android se cubre de tres formas:
- **Shims** con el nombre exacto de la API de Android o de Mihon: `LocalContext`, `BackHandler`, `toast`, `copyToClipboard`, `appGraph`…
- **Fachadas de escritorio** con la API de Mihon: `ExtensionManager`.
- **Implementaciones nulas** de los subsistemas que llegan en subproyectos posteriores: descargas y trackers en `src/stubs/kotlin`.

`presentation-core` y los iconos pasan a JVM con Compose Multiplatform. El lector y el historial son el plan 2.

**Tech Stack:** Kotlin 2.4.21, JDK 21, Compose Multiplatform 1.12.2 (artefactos `org.jetbrains.compose.*`), material3 CMP 1.12.0-alpha03, Voyager 2.2.21-1.10.3 (artefacto `-desktop`), Metro 1.4.5 + metrox-viewmodel(-compose) 1.4.5, lifecycle KMP de JetBrains 2.11.0, Coil 3.6.3, paging 3.5.3, JUnit 5 + kotest + `compose.uiTest`.

**Spec:** `docs/superpowers/specs/2026-10-19-subproyecto-2a-ui-lector-design.md` (secciones 1, 2, 3, 5 y 6; la sección 4, el lector, es el plan 2). Spec padre: `docs/superpowers/specs/2026-10-18-windows-port-design.md`.

## Global Constraints

- Versiones fijas:

  | Componente | Versión |
  |---|---|
  | Kotlin | `2.4.21` |
  | JDK | `21` |
  | CMP | `1.12.2` |
  | material3 y material3-adaptive-navigation-suite de CMP | `1.12.0-alpha03` |
  | lifecycle de JetBrains | `2.11.0` |
  | Voyager | `2.2.21-1.10.3` |
  | Metro | `1.4.5` |
  | Coil | `3.6.3` |

  OkHttp, RxJava, Jsoup y kotlinx-serialization no cambian: los fijan las extensiones.
- Los archivos portados de Mihon se copian de `app/src/main/java/<ruta>` a `desktop-app/src/main/kotlin/<ruta>`, con **el mismo paquete**. Solo se cambia lo que dice el paso. Cada cambio respecto a Mihon lleva un comentario `// desktop: <motivo>` en la línea modificada o justo encima, para facilitar futuros merges con upstream.
- Las implementaciones nulas de subsistemas de otros subproyectos van en `desktop-app/src/stubs/kotlin/<paquete de Mihon>`. Cada archivo empieza con `// Null implementation until subproject <N>; replace with the Mihon port.` Ningún código de producción nuevo depende de ellas, salvo el código portado que ya las usaba.
- El código nuevo de escritorio va en el paquete `mihon.desktop.app.*` (en `desktop-app`) o `mihon.desktop.*` (en los demás módulos). Los shims usan el paquete exacto de la API que reemplazan.
- Nada de `android.*` ni `androidx.*` (salvo `androidx.compose`, `androidx.lifecycle`, `androidx.paging` y los shims de `compat-shims` o AndroidCompat) en el código de `desktop-app`. La comprobación es el paso de verificación de la Task 13.
- Los textos nuevos visibles para el usuario van a `i18n/src/commonMain/moko-resources/base/strings.xml` (inglés) y `.../es/strings.xml` (español), en un bloque al final marcado `<!-- Desktop -->`.
- Nunca hacer `git push` al remoto `upstream` (mihonapp/mihon). No hay `origin` propio.
- Commits con el trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- Tests que son `= runBlocking { }` declaran `: Unit`; si no, JUnit los ignora.
- Archivos con `\\` se escriben con la herramienta Write, no con heredoc de Bash.

## Desviaciones del spec (aprobar antes de ejecutar)

1. **El subproyecto 2a se divide en dos planes.**
   - Este plan contiene la base de UI y las pantallas.
   - El plan 2 contiene el lector, el historial y los ajustes del lector, y se escribe cuando este esté integrado, sobre sus interfaces reales.
   - Hasta entonces, "Leer" y "Continuar" muestran el aviso "El lector llega en la próxima versión".
2. **Sin convention plugin nuevo y sin el plugin Gradle `org.jetbrains.compose`.** El spec preveía `mihonx.plugins.compose.desktop`, pero cada módulo con Compose solo necesita `alias(libs.plugins.kotlin.compose.compiler)` y sus dependencias. Para `run`, `desktop-app` usa el plugin `application` de Gradle. Los nativos de Skia llegan con `desktop-jvm-windows-x64`. El empaquetado (MSI) decidirá si añade el plugin de CMP, así se evita ahora su posible choque con Isolated Projects.
3. **Icono de extensión:** se lee del APK instalado al pedirlo (`ApkInspector.icon`) y Coil lo guarda en caché. No se extrae a un `<pkg>.png` al instalar. El resultado para el usuario es el mismo, sin archivos extra que limpiar.
4. **Filtro de contenido para adultos:** se usa el mecanismo que Mihon ya trae.
   - Ajuste `enabled_content_warnings` (SAFE, MIXED, NSFW) y `apply_content_warnings_to_installed`, en lugar de un único interruptor y del campo `isNsfw`, que el modelo actual ya no tiene.
   - Valor por defecto en escritorio: `{SAFE, MIXED}`, es decir, contenido para adultos oculto.
   - Las extensiones instaladas para adultos aparecen como "No cargadas (filtrada)", con el botón Desinstalar, y sus fuentes no se muestran.
5. **Estado vacío de Explorar:** se reutiliza el de Mihon (texto vacío y botón "Repositorios de extensiones", que lleva a la pantalla donde se añade la URL) en lugar de un botón nuevo "Añadir repositorio".
6. **Idioma:** la app sigue el idioma de Windows. El selector de idioma de la app se pospone, porque el de Mihon depende de `AppCompatDelegate`.
7. **Rail:** se usa `NavigationRail` de material3 directamente, no `NavigationSuiteScaffold`. En escritorio siempre es rail.
8. **Descargas y trackers:** implementaciones nulas con la API de Mihon (no hay descargas, no hay trackers con sesión iniciada). Los controles de descarga se ocultan pasando `null` a los parámetros que Mihon ya tiene nulos para las fuentes locales. El botón de seguimiento y las notas se ocultan haciendo nulos sus parámetros.

## Review Focus

1. **Dos pantallas iguales con distinto argumento** (dos mangas abiertos uno sobre otro): cada pantalla tiene su propio `ViewModel`, y al cerrar la de arriba la de abajo conserva su estado. Test en la Task 5.
2. **Esc con un diálogo o una búsqueda abiertos:** Esc cierra lo de más arriba (el último `BackHandler` habilitado), no la pantalla entera. Test en la Task 3.
3. **Extensión para adultos ya instalada al arrancar con el filtro por defecto:** no se cargan sus clases, aparece como filtrada y se puede desinstalar. Al activar NSFW en Ajustes, se carga sin reiniciar.
   - Tests del loader y de las preferencias en la Task 8.
   - Test de que el ajuste se guarda donde lo lee el loader en la Task 12.
   - La recarga sin reiniciar se comprueba a mano en la Task 12, porque no hay un APK de prueba para automatizarla (es el I-7 pendiente del subproyecto 1).
4. **Preferencia de fuente cuyo `OnPreferenceChangeListener` devuelve `false`:** el valor no se guarda. Test en la Task 10.
5. **Fallo de la red al buscar en una fuente** (la extensión lanza `IOException`): la pantalla muestra el error con "Reintentar" y no se cierra la app. Test en la Task 11.

## Mapa de archivos

| Ruta | Responsabilidad |
|---|---|
| `gradle/libs.versions.toml` | Versiones y artefactos de CMP, lifecycle de JetBrains y plugin `org.jetbrains.compose` |
| `settings.gradle.kts` | Incluye `:presentation-core`, `:icons:*` y `:desktop-app` |
| `presentation-core/**` | Componentes compartidos de Mihon, ahora en JVM. Shims `LocalContext` y `BackHandler` |
| `icons/*` | Iconos generados con Valkyrie, ahora en JVM |
| `android-compat/src/main/java/android/widget/Toast.java` | `Toast.show()` publica en un listener (modificación local documentada) |
| `desktop-core/.../DesktopGraph.kt`, `CoreGraph.kt`, `Platform.kt` | El grafo Metro pasa a los módulos hoja; `Platform.start` recibe la fábrica |
| `desktop-core/.../DesktopExtensionManager.kt` | Lista de disponibles, actualizaciones y filtro de content warning |
| `extension-loader/.../DesktopExtensionLoader.kt`, `ApkInspector.kt` | Motivo `Filtered` e icono del APK |
| `desktop-app/build.gradle.kts` | Módulo de la app (CMP, Metro) |
| `desktop-app/src/main/kotlin/mihon/desktop/app/**` | `Main.kt`, `DesktopAppGraph`, ventana, rail, ViewModels por pantalla, arranque, imágenes, ajustes de escritorio |
| `desktop-app/src/main/kotlin/eu/kanade/**`, `mihon/**`, `tachiyomi/**` | Archivos portados de Mihon y shims con nombres de Mihon |
| `desktop-app/src/stubs/kotlin/**` | Descargas y trackers nulos |
| `desktop-app/src/main/resources/logback-app.xml` | Log rotativo en `%LOCALAPPDATA%\<App>\logs` |
| `desktop-app/src/test/kotlin/**` | `TestAppGraph`, `FakeSource` y tests |

---
### Task 1: Compose Multiplatform en el build y esqueleto de `desktop-app`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Create: `desktop-app/build.gradle.kts`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/ComposeSmokeTest.kt`

**Interfaces:**
- Consumes: nada.
- Produces:
  - Alias de catálogo `libs.compose.*`, `libs.jetbrains.lifecycle.*` y `libs.compose.desktop.windows`.
  - Módulo `:desktop-app` con `application { mainClass = "mihon.desktop.app.MainKt" }`.

- [ ] **Step 1: Añadir versiones y artefactos al catálogo**

En `gradle/libs.versions.toml`, sección `[versions]`:

```toml
compose-multiplatform = "1.12.2"
compose-material3 = "1.12.0-alpha03"
jetbrains-lifecycle = "2.11.0"
```

Sección `[libraries]`:

```toml
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "compose-multiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose-multiplatform" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "compose-multiplatform" }
compose-ui-util = { module = "org.jetbrains.compose.ui:ui-util", version.ref = "compose-multiplatform" }
compose-ui-toolingPreview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "compose-multiplatform" }
compose-ui-test = { module = "org.jetbrains.compose.ui:ui-test", version.ref = "compose-multiplatform" }
compose-animation = { module = "org.jetbrains.compose.animation:animation", version.ref = "compose-multiplatform" }
compose-animationGraphics = { module = "org.jetbrains.compose.animation:animation-graphics", version.ref = "compose-multiplatform" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "compose-material3" }
compose-material3NavSuite = { module = "org.jetbrains.compose.material3:material3-adaptive-navigation-suite", version.ref = "compose-material3" }
compose-desktop-windows = { module = "org.jetbrains.compose.desktop:desktop-jvm-windows-x64", version.ref = "compose-multiplatform" }
jetbrains-lifecycle-viewmodelCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "jetbrains-lifecycle" }
jetbrains-lifecycle-runtimeCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "jetbrains-lifecycle" }
```

- [ ] **Step 2: Incluir el módulo**

En `settings.gradle.kts`, añadir al final de los `include`:

```kotlin
include(":desktop-app")
```

- [ ] **Step 3: Escribir el test de humo (falla: el módulo no compila todavía)**

`desktop-app/src/test/kotlin/mihon/desktop/app/ComposeSmokeTest.kt`:

```kotlin
package mihon.desktop.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ComposeSmokeTest {
    @Test
    fun `compose desktop renders material3 text`() = runComposeUiTest {
        setContent { MaterialTheme { Text("José Pérez") } }
        onNodeWithText("José Pérez").assertExists()
    }
}
```

- [ ] **Step 4: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.ComposeSmokeTest"`
Expected: FAIL: `Project with path ':desktop-app' could not be found` o un error de configuración (falta `build.gradle.kts`).

- [ ] **Step 5: Crear `desktop-app/build.gradle.kts`**

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.metro)
    application
}

application {
    mainClass = "mihon.desktop.app.MainKt"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    implementation(projects.desktopCore)

    implementation(libs.compose.desktop.windows)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    testImplementation(libs.bundles.test)
    testImplementation(libs.compose.ui.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Platform.start boots Koin and AndroidCompat's global config: once per JVM
tasks.test { forkEvery = 1 }
```

`desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt` (provisional; la Task 6 lo completa):

```kotlin
package mihon.desktop.app

import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import mihon.desktop.core.AppDirs

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = AppDirs.APP_NAME) {
        Text(AppDirs.APP_NAME)
    }
}
```

- [ ] **Step 6: Comprobar que el classpath no mezcla Compose de AndroidX con el de JetBrains**

Run: `./gradlew :desktop-app:dependencies --configuration testRuntimeClasspath > build/desktop-app-deps.txt; grep -E "androidx\.compose\.(ui|foundation|material3|runtime):[a-z-]+(-desktop|-jvmstubs)?:" build/desktop-app-deps.txt`
Expected: ninguna línea con `androidx.compose.ui`, `androidx.compose.foundation` ni `androidx.compose.material3`. Solo puede aparecer `androidx.compose.runtime:runtime-desktop` si es la misma clase que `org.jetbrains.compose.runtime:runtime-desktop` usa por debajo, y en ese caso no puede aparecer ninguna otra copia de `runtime`.

Si aparece alguna copia duplicada, añadir a `desktop-app/build.gradle.kts` (y en la Task 3 también a `presentation-core/build.gradle.kts`) la sustitución siguiente y anotarla como ruling:

```kotlin
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.compose.runtime:runtime"))
            .using(module("org.jetbrains.compose.runtime:runtime:${libs.versions.compose.multiplatform.get()}"))
    }
}
```

- [ ] **Step 7: Ejecutar el test y ver que pasa**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.ComposeSmokeTest"`
Expected: PASS (1 test).

- [ ] **Step 8: Comprobar que la ventana abre**

Run: `./gradlew :desktop-app:run` y cerrar la ventana.
Expected: aparece una ventana titulada `MangaReader`, y al cerrarla el proceso termina con código 0.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts desktop-app
git commit -m "build(desktop-app): Compose Multiplatform window module

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: El grafo Metro pasa a los módulos hoja

Metro solo agrega las contribuciones (`@ContributesBinding`, `@ContributesIntoMap`) que ve el módulo donde está el `@DependencyGraph`. Los ViewModels de `desktop-app` no los vería un grafo declarado en `desktop-core`, así que el grafo se declara en cada hoja: `CoreGraph` para el harness y los tests de `desktop-core`, y `DesktopAppGraph` para la app.

**Files:**
- Modify: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopGraph.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/CoreGraph.kt`
- Modify: `desktop-core/src/main/kotlin/mihon/desktop/core/Platform.kt`
- Modify: `gradle/libs.versions.toml` (nada nuevo; se usan `metrox-viewmodel` y `metrox-viewmodel-compose`, que ya existen)
- Modify: `desktop-app/build.gradle.kts`
- Create: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt`
- Create: `desktop-app/src/main/kotlin/mihon/app/di/AppGraphUtils.kt`
- Create: `desktop-app/src/main/kotlin/mihon/app/di/MihonViewModelFactory.kt` (copia de Mihon sin cambios)
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/DesktopApp.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/DesktopAppGraph.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/FakeSource.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/DesktopAppGraphTest.kt`

**Interfaces:**
- Consumes: `DesktopBindings`, `DesktopSourceManager`, `AppDirs`, `IsDebugBuild` (de `desktop-core` y `core:metro`).
- Produces:
  - `interface DesktopGraph`, que ya no lleva anotación y mantiene los mismos accesores.
  - `@DependencyGraph interface CoreGraph : DesktopGraph`.
  - `Platform.start(dirs, isDebugBuild = false): DesktopGraph`, sin cambios para el harness.
  - `fun <G : DesktopGraph> Platform.start(dirs: AppDirs, isDebugBuild: Boolean = false, createGraph: (Context, AppDirs, Boolean) -> G): G`.
  - `interface mihon.app.di.AppGraph : DesktopGraph, ViewModelGraph`. Las tareas siguientes le añaden accesores.
  - `val Context.appGraph: AppGraph`.
  - `object DesktopApp { lateinit var graph: AppGraph }`.
  - En los tests: `startTestApp(): TestAppGraph`, `FakeSource`, `FakeSourceManager` y `TestSources.fake`.

- [ ] **Step 1: Escribir la infraestructura de test y el test (falla: no existen `AppGraph` ni la sobrecarga de `Platform.start`)**

`desktop-app/src/test/kotlin/mihon/desktop/app/testing/FakeSource.kt`:

```kotlin
package mihon.desktop.app.testing

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager

/** In-memory source: 60 manga in pages of 25, five chapters each. [failNext] makes the next listing call throw. */
class FakeSource(
    override val id: Long = 1L,
    override val name: String = "Fake",
    override val lang: String = "en",
) : Source {
    override val supportsLatest: Boolean = true

    @Volatile
    var failNext: Exception? = null

    val mangas: List<SManga> = (1..60).map { i ->
        SManga.create().apply {
            url = "/manga/$i"
            title = "Manga $i"
            initialized = true
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = page(mangas, page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = page(mangas.reversed(), page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        page(mangas.filter { it.title.contains(query, ignoreCase = true) }, page)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (!fetchDetails) {
            manga
        } else {
            SManga.create().also {
                it.url = manga.url
                it.title = manga.title
                it.description = "Description of ${manga.title}"
                it.initialized = true
            }
        }
        val list = if (!fetchChapters) {
            chapters
        } else {
            (5 downTo 1).map { n ->
                SChapter.create().apply {
                    url = "${manga.url}/$n"
                    name = "Chapter $n"
                    chapter_number = n.toFloat()
                    date_upload = 1_700_000_000_000L + n
                }
            }
        }
        return SMangaUpdate(details, list)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    private fun page(list: List<SManga>, page: Int): MangasPage {
        failNext?.let {
            failNext = null
            throw it
        }
        return MangasPage(list.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE), page * PAGE_SIZE < list.size)
    }

    private companion object {
        const val PAGE_SIZE = 25
    }
}

class FakeSourceManager(vararg initial: Source) : SourceManager {
    override val sources = MutableStateFlow(initial.toList())

    override suspend fun get(sourceKey: Long): Source? = sources.value.find { it.id == sourceKey }

    override suspend fun getOrStub(sourceKey: Long): Source = get(sourceKey) ?: StubSource(sourceKey, "", "")

    override suspend fun getAll(): List<Source> = sources.value

    override suspend fun getOnlineSources(): List<HttpSource> = sources.value.filterIsInstance<HttpSource>()

    override suspend fun getStubSources(): List<StubSource> = emptyList()
}

object TestSources {
    val fake = FakeSource()
}
```

`desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt`:

```kotlin
package mihon.desktop.app.testing

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import mihon.app.di.AppGraph
import mihon.core.metro.IsDebugBuild
import mihon.desktop.app.DesktopApp
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopBindings
import mihon.desktop.core.DesktopSourceManager
import mihon.desktop.core.Platform
import tachiyomi.domain.source.service.SourceManager
import kotlin.io.path.createTempDirectory

/** The app graph with [TestSources.fake] as the only source, instead of the installed extensions. */
@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [DesktopBindings::class, TestBindings::class],
    excludes = [DesktopSourceManager::class],
)
interface TestAppGraph : AppGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
            @Provides appDirs: AppDirs,
            @Provides @IsDebugBuild isDebugBuild: Boolean,
        ): TestAppGraph
    }
}

@BindingContainer
object TestBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun sourceManager(): SourceManager = FakeSourceManager(TestSources.fake)
}

/** Boots the platform once for this JVM (the test task forks per class) in a folder with a non-ASCII path. */
fun startTestApp(): TestAppGraph {
    val root = createTempDirectory("José Pérez app")
    val graph = Platform.start(AppDirs(root.resolve("data"), root.resolve("local"))) { context, dirs, debug ->
        createGraphFactory<TestAppGraph.Factory>().create(context, dirs, debug)
    }
    DesktopApp.graph = graph
    return graph
}
```

`desktop-app/src/test/kotlin/mihon/desktop/app/DesktopAppGraphTest.kt`:

```kotlin
package mihon.desktop.app

import android.content.Context
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.runBlocking
import mihon.app.di.MihonViewModelFactory
import mihon.app.di.appGraph
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopAppGraphTest {
    private lateinit var graph: TestAppGraph

    @BeforeAll
    fun start() {
        graph = startTestApp()
    }

    @Test
    fun `the leaf graph replaces the source manager and keeps the platform bindings`(): Unit = runBlocking {
        graph.sourceManager.getAll() shouldBe listOf(TestSources.fake)
        graph.extensionStoreRepository.getAll() shouldBe emptyList()
    }

    @Test
    fun `ported code reaches the same graph through the context`() {
        Injekt.get<Context>().appGraph shouldBeSameInstanceAs graph
    }

    @Test
    fun `the view model factory is Mihon's, fed by this module's contributions`() {
        graph.metroViewModelFactory.shouldBeInstanceOf<MihonViewModelFactory>()
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.DesktopAppGraphTest"`
Expected: FAIL de compilación: `Unresolved reference 'AppGraph'` y `None of the following candidates is applicable` en `Platform.start`.

- [ ] **Step 3: `DesktopGraph` sin anotación y `CoreGraph`**

En `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopGraph.kt`:
- Quitar `@DependencyGraph(...)`, la interfaz anidada `Factory` y los imports que solo usaban (`DependencyGraph`, `Provides`, `IsDebugBuild`, `AppScope`).
- Poner encima de la interfaz:

```kotlin
/**
 * What every desktop graph exposes. The @DependencyGraph itself lives in the leaf module (CoreGraph here,
 * DesktopAppGraph in desktop-app), because Metro only merges the contributions the graph's module can see.
 */
```

`desktop-core/src/main/kotlin/mihon/desktop/core/CoreGraph.kt`:

```kotlin
package mihon.desktop.core

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import mihon.core.metro.IsDebugBuild

/** The platform without UI: the compatibility harness and desktop-core's own tests run on it. */
@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [DesktopBindings::class],
)
interface CoreGraph : DesktopGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
            @Provides appDirs: AppDirs,
            @Provides @IsDebugBuild isDebugBuild: Boolean,
        ): CoreGraph
    }
}
```

- [ ] **Step 4: `Platform.start` recibe la fábrica del grafo**

En `desktop-core/src/main/kotlin/mihon/desktop/core/Platform.kt`, sustituir la función `start` por estas dos (el cuerpo de arranque no cambia; solo cambia la línea que crea el grafo):

```kotlin
    /** Boots the platform with the UI-less [CoreGraph]. */
    fun start(dirs: AppDirs, isDebugBuild: Boolean = false): DesktopGraph =
        start(dirs, isDebugBuild) { context, appDirs, debug ->
            createGraphFactory<CoreGraph.Factory>().create(context, appDirs, debug)
        }

    /** Boots AndroidCompat, then the leaf module's Metro graph, then the Injekt view extensions use. Once per process. */
    fun <G : DesktopGraph> start(
        dirs: AppDirs,
        isDebugBuild: Boolean = false,
        createGraph: (Context, AppDirs, Boolean) -> G,
    ): G {
        dirs.createAll()
        // AndroidCompat resolves every path (settings/, android-compat/) from this root
        System.setProperty("suwayomi.tachidesk.config.server.rootDir", dirs.data.toString())

        startKoin { modules(androidCompatModule(), configManagerModule()) }
        startMainLooper()
        // Without this, a WebView waits forever for Chromium inside a runBlocking on the main looper, freezing it
        // for every extension. ponytail: fails fast until KCEF is initialized here (subproject 2b)
        CefHelper.cefApp.value = Result.failure(
            CefHelper.CefException("WebView is not available: the embedded browser (KCEF) is not set up yet"),
        )
        AndroidCompatInitializer().init()
        val application = DesktopApplication()
        AndroidCompat().startApp(application)

        // Like Mihon, the application is the Context everything gets; it delegates to AndroidCompat's CustomContext
        val graph = createGraph(application, dirs, isDebugBuild)
        Injekt = InjektScope(DesktopInjektRegistrar(application, graph))
        return graph
    }
```

Añadir `import android.content.Context`.

- [ ] **Step 5: `AppGraph`, `DesktopAppGraph`, `DesktopApp` y `appGraph` en `desktop-app`**

En `desktop-app/build.gradle.kts`, añadir a `dependencies`:

```kotlin
    implementation(projects.core.metro)
    implementation(libs.metro.runtime)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.injekt)
```

`desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt`:

```kotlin
package mihon.app.di

import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import mihon.desktop.core.DesktopGraph

/**
 * Desktop counterpart of Mihon's AppGraph: the accessors ported code reaches through `context.appGraph`.
 * The graph itself is DesktopAppGraph (tests: TestAppGraph).
 */
interface AppGraph : DesktopGraph, ViewModelGraph
```

`desktop-app/src/main/kotlin/mihon/app/di/AppGraphUtils.kt`:

```kotlin
package mihon.app.di

import android.content.Context
import mihon.desktop.app.DesktopApp

// desktop: Mihon casts applicationContext to GraphProvider; AndroidCompat's context isn't the Application
@Suppress("UnusedReceiverParameter")
val Context.appGraph: AppGraph get() = DesktopApp.graph
```

`desktop-app/src/main/kotlin/mihon/app/di/MihonViewModelFactory.kt`: copiar `app/src/main/java/mihon/app/di/MihonViewModelFactory.kt` sin cambios.

`desktop-app/src/main/kotlin/mihon/desktop/app/DesktopApp.kt`:

```kotlin
package mihon.desktop.app

import mihon.app.di.AppGraph

/** The running app's graph, for code ported from Mihon that reaches it through `context.appGraph`. */
object DesktopApp {
    lateinit var graph: AppGraph
}
```

`desktop-app/src/main/kotlin/mihon/desktop/app/DesktopAppGraph.kt`:

```kotlin
package mihon.desktop.app

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import mihon.app.di.AppGraph
import mihon.core.metro.IsDebugBuild
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopBindings

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [DesktopBindings::class],
)
interface DesktopAppGraph : AppGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
            @Provides appDirs: AppDirs,
            @Provides @IsDebugBuild isDebugBuild: Boolean,
        ): DesktopAppGraph
    }
}
```

- [ ] **Step 6: Ejecutar los tests de los tres módulos afectados**

Run: `./gradlew :desktop-core:test :compat-harness:test :desktop-app:test`
Expected: PASS. Los tests anteriores de `desktop-core` y `compat-harness` siguen en verde, y `DesktopAppGraphTest` pasa (3 tests).

Si Metro rechaza `excludes` con una clase `@ContributesBinding` de otro módulo, hay que usar el otro mecanismo de Metro para sustituir un binding. Anotarlo como ruling.
- Quitar `excludes` y `TestBindings`.
- Declarar en los tests:

```kotlin
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [DesktopSourceManager::class])
class FakeSourceManagerBinding : SourceManager by FakeSourceManager(TestSources.fake)
```

- [ ] **Step 7: Commit**

```bash
git add desktop-core desktop-app
git commit -m "refactor(desktop): Metro graph lives in each leaf module

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `presentation-core` y los iconos en JVM, con los shims `LocalContext` y `BackHandler`

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `presentation-core/build.gradle.kts`
- Delete: `presentation-core/src/main/AndroidManifest.xml`, `presentation-core/src/main/res/` (colores XML que solo usaban las Views de Android)
- Modify: `presentation-core/src/main/java/tachiyomi/presentation/core/i18n/Localize.kt` (sin cambios de código: ahora resuelve `LocalContext` con el shim)
- Modify: `presentation-core/src/main/java/tachiyomi/presentation/core/components/VerticalFastScroller.kt`
- Modify: `presentation-core/src/main/java/tachiyomi/presentation/core/util/Scrollbar.kt`
- Create: `presentation-core/src/main/java/androidx/compose/ui/platform/LocalContext.kt`
- Create: `presentation-core/src/main/java/androidx/activity/compose/BackHandler.kt`
- Modify: `icons/custom-material-symbols/build.gradle.kts`, `icons/material-symbols/build.gradle.kts`, `icons/simple-icons/build.gradle.kts`
- Test: `presentation-core/src/test/kotlin/androidx/activity/compose/BackHandlerTest.kt`

**Interfaces:**
- Consumes: `libs.compose.*` (Task 1).
- Produces:
  - `val LocalContext: ProvidableCompositionLocal<android.content.Context>`, en el paquete `androidx.compose.ui.platform`.
  - `@Composable fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)`, en el paquete `androidx.activity.compose`.
  - `object BackPressDispatcher { fun dispatch(): Boolean }`. Llama al último handler habilitado y devuelve `false` si no hay ninguno.
  - Módulos `:presentation-core`, `:icons:material-symbols`, `:icons:custom-material-symbols` y `:icons:simple-icons` en JVM.

- [ ] **Step 1: Incluir los módulos**

En `settings.gradle.kts`:

```kotlin
include(":presentation-core")
include(":icons:material-symbols")
include(":icons:custom-material-symbols")
include(":icons:simple-icons")
```

- [ ] **Step 2: Escribir el test del despachador de "atrás" (falla: no existe)**

`presentation-core/src/test/kotlin/androidx/activity/compose/BackHandlerTest.kt`:

```kotlin
package androidx.activity.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class BackHandlerTest {
    @Test
    fun `back reaches the innermost enabled handler, then the outer one`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        var searchOpen by mutableStateOf(true)
        setContent {
            BackHandler { calls += "screen" }
            BackHandler(enabled = searchOpen) {
                calls += "search"
                searchOpen = false
            }
        }

        BackPressDispatcher.dispatch() shouldBe true
        waitForIdle()
        BackPressDispatcher.dispatch() shouldBe true

        calls shouldBe listOf("search", "screen")
    }

    @Test
    fun `nothing handles back once the handlers leave the composition`() = runComposeUiTest {
        var shown by mutableStateOf(true)
        setContent { if (shown) BackHandler { } }
        shown = false
        waitForIdle()

        BackPressDispatcher.dispatch() shouldBe false
    }
}
```

- [ ] **Step 3: Pasar `presentation-core` a JVM**

`presentation-core/build.gradle.kts` completo:

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    api(projects.core.common)
    api(projects.i18n)
    implementation(projects.icons.materialSymbols)

    api(libs.compose.runtime)
    api(libs.compose.foundation)
    api(libs.compose.ui)
    api(libs.compose.material3)
    api(libs.compose.material3NavSuite)
    implementation(libs.compose.animation)
    implementation(libs.compose.animationGraphics)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.toolingPreview)

    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.compose)

    testImplementation(libs.bundles.test)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.compose.desktop.windows)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

Comprobar que nada usa los colores XML antes de borrarlos:

Run: `grep -rn "R\.color\|R\.drawable\|import tachiyomi.presentation.core.R" presentation-core/src app/src/main/java/eu/kanade/presentation | head`
Expected: ninguna línea en `presentation-core/src`. Las de `app/` no importan, porque `app` no se compila.

Borrar `presentation-core/src/main/AndroidManifest.xml` y `presentation-core/src/main/res/`.

- [ ] **Step 4: Shims `LocalContext` y `BackHandler`**

`presentation-core/src/main/java/androidx/compose/ui/platform/LocalContext.kt`:

```kotlin
package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

// desktop: Compose Multiplatform has no LocalContext; the window provides AndroidCompat's application context
val LocalContext = staticCompositionLocalOf<Context> { error("LocalContext is provided by the app window") }
```

`presentation-core/src/main/java/androidx/activity/compose/BackHandler.kt`:

```kotlin
package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Desktop stand-in for AndroidX's OnBackPressedDispatcher: the window sends Esc and Alt+Left here. Like on
 * Android, the handler registered last that is enabled wins.
 */
object BackPressDispatcher {
    internal class Entry(var enabled: Boolean, val onBack: () -> Unit)

    private val entries = mutableListOf<Entry>()

    internal fun add(entry: Entry) {
        entries += entry
    }

    internal fun remove(entry: Entry) {
        entries -= entry
    }

    /** Runs the innermost enabled handler; false when none would handle it. */
    fun dispatch(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }
}

// desktop: same signature as androidx.activity.compose.BackHandler
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val entry = remember { BackPressDispatcher.Entry(enabled) { currentOnBack() } }
    SideEffect { entry.enabled = enabled }
    DisposableEffect(entry) {
        BackPressDispatcher.add(entry)
        onDispose { BackPressDispatcher.remove(entry) }
    }
}
```

- [ ] **Step 5: Quitar `android.view.ViewConfiguration`**

En los stubs de Android, `ViewConfiguration` lanza `Stub!`. Se sustituyen sus valores por las constantes por defecto de Android.

En `presentation-core/src/main/java/tachiyomi/presentation/core/util/Scrollbar.kt`:
- Borrar `import android.view.ViewConfiguration` y `import androidx.compose.ui.platform.LocalContext`.
- Cambiar `val context = LocalContext.current` (línea 237) y `val thickness = remember { ViewConfiguration.get(context).scaledScrollBarSize.toFloat() }` por:

```kotlin
    // desktop: Android's default scrollbar size (4dp); ViewConfiguration is a stub here
    val thickness = with(LocalDensity.current) { 4.dp.toPx() }
```

(añadir `import androidx.compose.ui.platform.LocalDensity` si falta).

- Cambiar `ViewConfiguration.getScrollDefaultDelay().toLong()` por `300L // desktop: Android's default scroll delay`.
- Cambiar `ViewConfiguration.getScrollBarFadeDuration()` por `250 // desktop: Android's default fade duration`.

En `presentation-core/src/main/java/tachiyomi/presentation/core/components/VerticalFastScroller.kt`:
- Borrar `import android.view.ViewConfiguration`.
- Cambiar `ViewConfiguration.getScrollBarFadeDuration()` por `250 // desktop: Android's default fade duration`.

- [ ] **Step 6: Iconos en JVM**

En los tres `icons/*/build.gradle.kts`:
- Cambiar `alias(mihonx.plugins.android.library)` por `alias(mihonx.plugins.kotlin.jvm)`.
- Cambiar `alias(mihonx.plugins.compose)` por `alias(libs.plugins.kotlin.compose.compiler)`.
- Borrar el bloque `android { namespace = ... }`.
- Cambiar `api(libs.androidx.compose.ui)` por `api(libs.compose.ui)`.
- Solo en `icons/material-symbols/build.gradle.kts`: borrar el `import mihon.gradle.tasks.GenerateVectorDrawablesTask` y el bloque `androidComponents { ... }` entero, que generaba drawables para widgets de Android.

Si el plugin Valkyrie no genera código en un módulo `kotlin("jvm")`, el error será `Unresolved reference 'MaterialSymbols'` al compilar `presentation-core`. En ese caso, añadir `targetSourceSet = "main"` (ya está) y comprobar con `./gradlew :icons:material-symbols:tasks --all | grep -i valkyrie` el nombre de su tarea de generación. Hacer que `compileKotlin` dependa de ella con `tasks.named("compileKotlin") { dependsOn("<tarea>") }`, y anotar el ruling.

- [ ] **Step 7: Compilar y ejecutar el test**

Run: `./gradlew :presentation-core:test`
Expected: PASS (2 tests de `BackHandlerTest`). La compilación de `presentation-core` no da errores.

Si un componente de `presentation-core` usa una API de material3 que no existe en `1.12.0-alpha03`, el error será `Unresolved reference`. Sustituirla por su equivalente disponible en la misma clase o archivo de material3, marcar el cambio con `// desktop:` y anotar el ruling.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts presentation-core icons
git commit -m "feat(presentation-core): build for the JVM with Compose Multiplatform

LocalContext and BackHandler get desktop stand-ins with AndroidX's names.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---
### Task 4: Shims de plataforma, toasts al snackbar y subsistemas nulos

**Files:**
- Modify: `android-compat/src/main/java/android/widget/Toast.java`
- Modify: `android-compat/README.md` (lista de modificaciones locales)
- Modify: `desktop-app/build.gradle.kts`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/AppMessages.kt`
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/util/system/ContextExtensions.kt`
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/util/system/DeviceUtilExtensions.kt`
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/util/system/BuildConfig.kt`
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/util/system/NetworkStateExtensions.kt`
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/util/system/LocaleHelper.kt` (portado)
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/download/model/Download.kt` (copia de Mihon sin cambios)
- Create: `desktop-app/src/main/kotlin/eu/kanade/domain/track/model/AutoTrackState.kt` (copia de Mihon sin cambios)
- Create: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/download/DownloadManager.kt`
- Create: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/download/DownloadCache.kt`
- Create: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/download/DownloadProvider.kt`
- Create: `desktop-app/src/stubs/kotlin/eu/kanade/domain/download/interactor/DeleteDownload.kt`
- Create: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/track/Tracker.kt`
- Create: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/track/TrackerManager.kt`
- Create: `desktop-app/src/stubs/kotlin/eu/kanade/domain/track/interactor/TrackInteractors.kt`
- Create: `desktop-app/src/stubs/kotlin/eu/kanade/domain/track/service/TrackPreferences.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/platform/AppMessagesTest.kt`

**Interfaces:**
- Consumes: `Toast` de AndroidCompat; `DeviceUtil` y `truncateCenter` de `core:common`.
- Produces:
  - `Toast.listener: java.util.function.Consumer<CharSequence>?`, campo estático.
  - `object AppMessages { val messages: SharedFlow<String>; fun install() }`.
  - Shims en `eu.kanade.tachiyomi.util.system`:
    - `Context.copyToClipboard(label, content)` y `Context.openInBrowser(url, forceDefaultBrowser)`.
    - `Context.isPackageInstalled(pkg) = false` y `Context.isOnline() = true`.
    - `isReleaseBuildType = true` y `DeviceUtil.isDynamicColorAvailable = false`.
    - `LocaleHelper`.
  - Clases nulas con la API de Mihon:
    - `DownloadManager`, `DownloadCache`, `DownloadProvider` y `DeleteDownload`.
    - `Tracker`, `EnhancedTracker` y `TrackerManager`.
    - `TrackChapter`, `RefreshTracks` y `AddTracks`.
    - `TrackPreferences`, con `autoUpdateTrackOnMarkRead`.
  - Modelos reales `Download` y `AutoTrackState`.

- [ ] **Step 1: Escribir el test (falla: no existe `AppMessages`)**

`desktop-app/src/test/kotlin/mihon/desktop/app/platform/AppMessagesTest.kt`:

```kotlin
package mihon.desktop.app.platform

import android.content.Context
import android.widget.Toast
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.toast
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppMessagesTest {
    @BeforeAll
    fun start() {
        startTestApp()
        AppMessages.install()
    }

    @Test
    fun `an extension's toast reaches the window's messages`(): Unit = runBlocking {
        val received = async { withTimeout(5.seconds) { AppMessages.messages.first() } }
        Toast.makeText(Injekt.get<Context>(), "Inicia sesión en José Pérez", Toast.LENGTH_SHORT).show()
        received.await() shouldBe "Inicia sesión en José Pérez"
    }

    @Test
    fun `ported code's context toast reaches them too`(): Unit = runBlocking {
        val received = async { withTimeout(5.seconds) { AppMessages.messages.first() } }
        Injekt.get<Context>().toast("hola")
        received.await() shouldBe "hola"
    }

    @Test
    fun `locale helper names multi-language sources without android`() {
        LocaleHelper.getLocalizedDisplayName("es").isNotBlank() shouldBe true
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.platform.AppMessagesTest"`
Expected: FAIL de compilación: `Unresolved reference 'AppMessages'` y `'LocaleHelper'`.

- [ ] **Step 3: `Toast.show()` publica en un listener**

En `android-compat/src/main/java/android/widget/Toast.java`, añadir dentro de la clase, después de las constantes:

```java
    /** Local modification: the desktop app shows toasts in its window; null prints them like upstream. */
    public static volatile java.util.function.Consumer<CharSequence> listener;
```

Y cambiar `show()` por:

```java
    public void show() {
        java.util.function.Consumer<CharSequence> current = listener;
        if (current != null) {
            current.accept(text);
        } else {
            System.out.printf("made a Toast: \"%s\"\n", text.toString());
        }
    }
```

En `android-compat/README.md`, añadir a la lista de modificaciones locales:

```markdown
- `android/widget/Toast.java`: `show()` hands the text to the static `listener` when one is set (the desktop app shows it in its window).
```

- [ ] **Step 4: `AppMessages`**

`desktop-app/src/main/kotlin/mihon/desktop/app/platform/AppMessages.kt`:

```kotlin
package mihon.desktop.app.platform

import android.widget.Toast
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Short messages for the window's snackbar: Android toasts from extensions and from ported Mihon code. */
object AppMessages {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun install() {
        Toast.listener = java.util.function.Consumer { show(it.toString()) }
    }

    fun show(message: String) {
        if (message.isNotBlank()) _messages.tryEmit(message)
    }
}
```

- [ ] **Step 5: Shims de `eu.kanade.tachiyomi.util.system`**

`ContextExtensions.kt`:

```kotlin
package eu.kanade.tachiyomi.util.system

import android.content.Context
import eu.kanade.tachiyomi.util.lang.truncateCenter
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

// Desktop versions of the Context helpers in Mihon's ContextExtensions.kt that ported screens call.

@Suppress("UNUSED_PARAMETER")
fun Context.copyToClipboard(label: String, content: String) {
    if (content.isBlank()) return
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(content), null)
        toast(stringResource(MR.strings.copied_to_clipboard, content.truncateCenter(50)))
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e)
        toast(MR.strings.clipboard_copy_error)
    }
}

@Suppress("UNUSED_PARAMETER")
fun Context.openInBrowser(url: String, forceDefaultBrowser: Boolean = false) {
    try {
        Desktop.getDesktop().browse(URI(url))
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e)
        toast(e.message)
    }
}

// desktop: extensions are never system packages, so the private uninstall path always applies
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun Context.isPackageInstalled(packageName: String): Boolean = false
```

`DeviceUtilExtensions.kt`:

```kotlin
package eu.kanade.tachiyomi.util.system

// desktop: Monet (wallpaper colors) is Android-only
@Suppress("UnusedReceiverParameter")
val DeviceUtil.isDynamicColorAvailable: Boolean get() = false
```

`BuildConfig.kt`:

```kotlin
package eu.kanade.tachiyomi.util.system

// desktop: there is no debug build type yet
val isReleaseBuildType: Boolean = true
```

`NetworkStateExtensions.kt`:

```kotlin
package eu.kanade.tachiyomi.util.system

import android.content.Context

// desktop: no connectivity manager; a failed request reports the real error instead
@Suppress("UnusedReceiverParameter")
fun Context.isOnline(): Boolean = true
```

`LocaleHelper.kt`: copiar `app/src/main/java/eu/kanade/tachiyomi/util/system/LocaleHelper.kt` con estos cambios:
- Borrar `import androidx.core.os.LocaleListCompat` y `import eu.kanade.tachiyomi.ui.browse.source.SourcesViewModel`.
- En `getSourceDisplayName`, cambiar `SourcesViewModel.LAST_USED_KEY` por `"last_used"` y `SourcesViewModel.PINNED_KEY` por `"pinned"`. Encima del `when`, poner el comentario `// desktop: SourcesViewModel's LAST_USED_KEY and PINNED_KEY, inlined so this file has no UI dependency`.
- En `getLocalizedDisplayName`, cambiar `"" -> LocaleListCompat.getAdjustedDefault()[0]` por `"" -> Locale.getDefault() // desktop: no LocaleListCompat`.

- [ ] **Step 6: Modelos reales y subsistemas nulos**

En `desktop-app/build.gradle.kts`, añadir antes de `dependencies`:

```kotlin
sourceSets {
    main {
        // Null implementations of subsystems later subprojects port (downloads: 4, trackers: 5)
        kotlin.srcDir("src/stubs/kotlin")
    }
}
```

Y en `dependencies`, añadir `implementation(libs.kotlinx.coroutines.core)`.

Copiar sin cambios:
- `app/src/main/java/eu/kanade/tachiyomi/data/download/model/Download.kt`, en `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/download/model/Download.kt`.
- `app/src/main/java/eu/kanade/domain/track/model/AutoTrackState.kt`, en la ruta equivalente.

`desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/download/DownloadManager.kt`:

```kotlin
// Null implementation until subproject 4; replace with the Mihon port.
package eu.kanade.tachiyomi.data.download

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

@Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
@Inject
@SingleIn(AppScope::class)
class DownloadManager {
    val queueState: StateFlow<List<Download>> = MutableStateFlow(emptyList())

    fun statusFlow(): Flow<Download> = emptyFlow()

    fun progressFlow(): Flow<Download> = emptyFlow()

    fun getDownloadCount(manga: Manga): Int = 0

    fun getDownloadedChapterIds(chapters: List<Chapter>, manga: Manga): Set<Long> = emptySet()

    fun getQueuedDownloadOrNull(chapterId: Long): Download? = null

    fun getQueuedDownloadsByChapterId(): Map<Long, Download> = emptyMap()

    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean = false

    fun startDownloads() {}

    suspend fun startDownloadNow(chapterId: Long) {}

    suspend fun downloadChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean = true) {}

    suspend fun cancelQueuedDownloads(downloads: List<Download>) {}

    suspend fun deleteChapters(chapters: List<Chapter>, manga: Manga, source: Source) {}

    suspend fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true) {}

    suspend fun renameManga(manga: Manga, newTitle: String) {}

    suspend fun renameChapter(source: Source, manga: Manga, oldChapter: Chapter, newChapter: Chapter) {}
}
```

`DownloadCache.kt`:

```kotlin
// Null implementation until subproject 4; replace with the Mihon port.
package eu.kanade.tachiyomi.data.download

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Suppress("UNUSED_PARAMETER")
@Inject
@SingleIn(AppScope::class)
class DownloadCache {
    val changes: Flow<Unit> = flowOf(Unit)

    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean = false
}
```

`DownloadProvider.kt`:

```kotlin
// Null implementation until subproject 4; replace with the Mihon port.
package eu.kanade.tachiyomi.data.download

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.domain.chapter.model.Chapter

@Suppress("UNUSED_PARAMETER")
@Inject
@SingleIn(AppScope::class)
class DownloadProvider {
    fun isChapterDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean = false
}
```

`desktop-app/src/stubs/kotlin/eu/kanade/domain/download/interactor/DeleteDownload.kt`:

```kotlin
// Null implementation until subproject 4; replace with the Mihon port.
package eu.kanade.domain.download.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

@Suppress("UNUSED_PARAMETER")
@Inject
class DeleteDownload {
    suspend fun awaitAll(manga: Manga, vararg chapters: Chapter) {}
}
```

`desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/data/track/Tracker.kt`:

```kotlin
// Null implementation until subproject 5; replace with the Mihon port.
package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.source.Source

interface Tracker {
    val id: Long
}

interface EnhancedTracker {
    fun accept(source: Source): Boolean
}
```

`TrackerManager.kt`:

```kotlin
// Null implementation until subproject 5; replace with the Mihon port.
package eu.kanade.tachiyomi.data.track

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Inject
@SingleIn(AppScope::class)
class TrackerManager {
    fun loggedInTrackers(): List<Tracker> = emptyList()

    fun loggedInTrackersFlow(): Flow<List<Tracker>> = flowOf(emptyList())
}
```

`desktop-app/src/stubs/kotlin/eu/kanade/domain/track/interactor/TrackInteractors.kt`:

```kotlin
// Null implementation until subproject 5; replace with the Mihon port.
package eu.kanade.domain.track.interactor

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.model.Manga

@Suppress("UNUSED_PARAMETER")
@Inject
class TrackChapter {
    suspend fun await(context: Context, mangaId: Long, chapterNumber: Double, setupJobOnFailure: Boolean = true) {}
}

@Suppress("UNUSED_PARAMETER")
@Inject
class RefreshTracks {
    suspend fun await(mangaId: Long): List<Pair<Tracker?, Throwable>> = emptyList()
}

@Suppress("UNUSED_PARAMETER")
@Inject
class AddTracks {
    suspend fun bindEnhancedTrackers(manga: Manga, source: Source) {}
}
```

`desktop-app/src/stubs/kotlin/eu/kanade/domain/track/service/TrackPreferences.kt`:

```kotlin
// Null implementation until subproject 5; replace with the Mihon port (same keys).
package eu.kanade.domain.track.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.track.model.AutoTrackState
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

@Inject
@SingleIn(AppScope::class)
class TrackPreferences(preferenceStore: PreferenceStore) {
    val autoUpdateTrackOnMarkRead: Preference<AutoTrackState> = preferenceStore.getEnum(
        "pref_auto_update_manga_on_mark_read",
        AutoTrackState.ALWAYS,
    )
}
```

- [ ] **Step 7: Ejecutar el test y ver que pasa**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.platform.AppMessagesTest" :android-compat:compileJava`
Expected: PASS (3 tests).

- [ ] **Step 8: Commit**

```bash
git add android-compat desktop-app
git commit -m "feat(desktop-app): platform shims, toasts to the window and null downloads/trackers

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Ventana: tema, navegación, ViewModel por pantalla, rail y atajos

**Files:**
- Modify: `desktop-app/build.gradle.kts`
- Modify: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt` (accesor `uiPreferences`)
- Create (portados de Mihon, mismo paquete):
  - `eu/kanade/domain/ui/UiPreferences.kt`
  - `eu/kanade/domain/ui/model/AppTheme.kt`
  - `eu/kanade/domain/ui/model/ThemeMode.kt`
  - `eu/kanade/domain/ui/model/TabletUiMode.kt`
  - `eu/kanade/domain/ui/model/TimeFormat.kt`
  - `eu/kanade/presentation/theme/TachiyomiTheme.kt`
  - `eu/kanade/presentation/theme/colorscheme/*.kt`, todos salvo `MonetColorScheme.kt`
  - `eu/kanade/presentation/util/Navigator.kt`
  - `eu/kanade/presentation/components/AppBar.kt`
  - `eu/kanade/presentation/components/DropdownMenu.kt`
  - `eu/kanade/presentation/components/TabbedScreen.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/navigation/ScreenViewModelStores.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/main/AppEvents.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/main/Shortcuts.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/main/HomeScreen.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/main/AppContent.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/main/CrashScreen.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/navigation/ScreenViewModelStoresTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/main/ShortcutsTest.kt`

**Interfaces:**
- Consumes: `BackPressDispatcher` y `LocalContext` (Task 3), `AppMessages` (Task 4), `DesktopApp.graph` y `AppGraph` (Task 2).
- Produces:
  - `class ScreenViewModelStores { fun ownerFor(key: String): ViewModelStoreOwner; fun retainOnly(keys: Set<String>) }`.
  - `@Composable fun ProvideScreenViewModelStore(navigator: Navigator, screen: Screen, content: @Composable () -> Unit)`.
  - `enum class Shortcut { Back, GlobalSearch, Refresh, Settings }` y `fun shortcutFor(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean): Shortcut?`.
  - `object AppEvents { val refresh: SharedFlow<Unit>; val globalSearch: SharedFlow<Unit>; val openSettings: SharedFlow<Unit>; fun send(shortcut: Shortcut) }`.
  - `HomeScreen`: pantalla raíz con rail. Su lista `HomeScreen.tabs` la amplían las tareas 9, 11 y 12.
  - `@Composable fun AppProviders(graph: AppGraph, content: @Composable () -> Unit)`: `LocalContext`, `LocalMetroViewModelFactory`, el `LocalViewModelStoreOwner` raíz y el tema. Los tests de UI lo reutilizan.
- `@Composable fun AppContent(graph: AppGraph, crash: Throwable?, onRestart: () -> Unit)`: el contenido completo de la ventana (navegador, snackbar o pantalla de error).

- [ ] **Step 1: Escribir los tests (fallan: no existen las clases)**

`desktop-app/src/test/kotlin/mihon/desktop/app/navigation/ScreenViewModelStoresTest.kt`:

```kotlin
package mihon.desktop.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class ScreenViewModelStoresTest {
    class Counter(val id: Int) : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private fun counterIn(stores: ScreenViewModelStores, key: String, id: Int): Counter {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = Counter(id) as T
        }
        return ViewModelProvider.create(stores.ownerFor(key), factory)[Counter::class]
    }

    @Test
    fun `two screens of the same class get their own view model`() {
        val stores = ScreenViewModelStores()
        val first = counterIn(stores, "manga-1", 1)
        val second = counterIn(stores, "manga-2", 2)

        first shouldNotBeSameInstanceAs second
        counterIn(stores, "manga-1", 99) shouldBeSameInstanceAs first
    }

    @Test
    fun `a screen that leaves the stack clears its view model and keeps the others`() {
        val stores = ScreenViewModelStores()
        val first = counterIn(stores, "manga-1", 1)
        val second = counterIn(stores, "manga-2", 2)

        stores.retainOnly(setOf("manga-1"))

        second.cleared shouldBe true
        first.cleared shouldBe false
        counterIn(stores, "manga-1", 99).id shouldBe 1
    }
}
```

`desktop-app/src/test/kotlin/mihon/desktop/app/main/ShortcutsTest.kt`:

```kotlin
package mihon.desktop.app.main

import androidx.compose.ui.input.key.Key
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShortcutsTest {
    @Test
    fun `desktop shortcuts from the spec`() {
        shortcutFor(Key.Escape, ctrl = false, alt = false, shift = false) shouldBe Shortcut.Back
        shortcutFor(Key.DirectionLeft, ctrl = false, alt = true, shift = false) shouldBe Shortcut.Back
        shortcutFor(Key.F, ctrl = true, alt = false, shift = false) shouldBe Shortcut.GlobalSearch
        shortcutFor(Key.F5, ctrl = false, alt = false, shift = false) shouldBe Shortcut.Refresh
        shortcutFor(Key.Comma, ctrl = true, alt = false, shift = false) shouldBe Shortcut.Settings
    }

    @Test
    fun `plain keys stay with the focused field`() {
        shortcutFor(Key.F, ctrl = false, alt = false, shift = false) shouldBe null
        shortcutFor(Key.DirectionLeft, ctrl = false, alt = false, shift = false) shouldBe null
        shortcutFor(Key.Escape, ctrl = true, alt = false, shift = false) shouldBe null
    }
}
```

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.navigation.*" --tests "mihon.desktop.app.main.ShortcutsTest"`
Expected: FAIL de compilación: `Unresolved reference 'ScreenViewModelStores'` y `'shortcutFor'`.

- [ ] **Step 3: Dependencias**

En `desktop-app/build.gradle.kts`, añadir a `dependencies`:

```kotlin
    implementation(projects.presentationCore)
    implementation(projects.icons.materialSymbols)
    implementation(projects.icons.customMaterialSymbols)
    implementation(libs.compose.animation)
    implementation(libs.compose.animationGraphics)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.toolingPreview)
    implementation(libs.compose.material3NavSuite)
    implementation(libs.jetbrains.lifecycle.viewmodelCompose)
    implementation(libs.jetbrains.lifecycle.runtimeCompose)
    implementation(libs.bundles.voyager)
    implementation(libs.composeMaterialMotion)
    implementation(libs.kotlinx.datetime)
```

- [ ] **Step 4: `ScreenViewModelStores`**

```kotlin
package mihon.desktop.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator

/**
 * On Android, Voyager gives every screen its own ViewModelStore; on desktop it doesn't, so two MangaScreens would
 * share one MangaViewModel. Each screen key gets a store here, cleared when the screen leaves the stack.
 */
class ScreenViewModelStores {
    private val stores = HashMap<String, ViewModelStore>()

    fun ownerFor(key: String): ViewModelStoreOwner {
        val store = stores.getOrPut(key) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    fun retainOnly(keys: Set<String>) {
        val gone = stores.keys - keys
        gone.forEach { stores.remove(it)?.clear() }
    }
}

@Composable
fun ProvideScreenViewModelStore(navigator: Navigator, screen: Screen, content: @Composable () -> Unit) {
    val stores = remember(navigator) { ScreenViewModelStores() }
    LaunchedEffect(navigator.items) { stores.retainOnly(navigator.items.mapTo(HashSet()) { it.key }) }
    CompositionLocalProvider(LocalViewModelStoreOwner provides stores.ownerFor(screen.key), content = content)
}
```

- [ ] **Step 5: Atajos y eventos**

`Shortcuts.kt`:

```kotlin
package mihon.desktop.app.main

import androidx.compose.ui.input.key.Key

enum class Shortcut { Back, GlobalSearch, Refresh, Settings }

/** The window-wide shortcuts (spec 2a, section 3). Anything else stays with the focused component. */
fun shortcutFor(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean): Shortcut? = when {
    key == Key.Escape && !ctrl && !alt && !shift -> Shortcut.Back
    key == Key.DirectionLeft && alt && !ctrl -> Shortcut.Back
    key == Key.F && ctrl && !alt -> Shortcut.GlobalSearch
    key == Key.F5 && !ctrl && !alt -> Shortcut.Refresh
    key == Key.Comma && ctrl && !alt -> Shortcut.Settings
    else -> null
}
```

`AppEvents.kt`:

```kotlin
package mihon.desktop.app.main

import androidx.activity.compose.BackPressDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Window shortcuts the visible screen reacts to; only the screen on top is composed, so only it collects. */
object AppEvents {
    private val _refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refresh: SharedFlow<Unit> = _refresh

    private val _globalSearch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val globalSearch: SharedFlow<Unit> = _globalSearch

    private val _openSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openSettings: SharedFlow<Unit> = _openSettings

    /** True when something handled it, so the key event stops there. */
    fun send(shortcut: Shortcut): Boolean = when (shortcut) {
        Shortcut.Back -> BackPressDispatcher.dispatch()
        Shortcut.Refresh -> _refresh.tryEmit(Unit)
        Shortcut.GlobalSearch -> _globalSearch.tryEmit(Unit)
        Shortcut.Settings -> _openSettings.tryEmit(Unit)
    }
}
```

- [ ] **Step 6: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.navigation.*" --tests "mihon.desktop.app.main.ShortcutsTest"`
Expected: PASS (4 tests).

- [ ] **Step 7: Portar tema, preferencias de UI y componentes**

Copiar los archivos de la lista "Create (portados de Mihon)" desde `app/src/main/java/` a `desktop-app/src/main/kotlin/`, con estos cambios:

- `UiPreferences.kt`: en `timeFormat(...)`, cambiar `val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)` por `val pattern = if (use24Hours) "HH:mm" else "h:mm a" // desktop: android.text.format.DateFormat is a stub`. Borrar la variable `skeleton` y el `import android.text.format.DateFormat`.
- `ThemeMode.kt`: borrar la función `setAppCompatDelegateThemeMode` y su import. Desktop aplica el modo en `TachiyomiTheme`.
- `TachiyomiTheme.kt`:
  - Borrar `import android.content.Context` y todo lo relativo a `MonetColorScheme`.
  - `getThemeColorScheme` pierde el parámetro `context` y usa `colorSchemes.getOrDefault(appTheme, TachiyomiColorScheme)` siempre, con `overrideDarkSurfaceContainers = true`.
  - `TachiyomiTheme` lee las tres preferencias como estado, para que un cambio en Ajustes repinte la ventana sin recrearla (Mihon recrea la Activity):

```kotlin
@Composable
fun TachiyomiTheme(
    appTheme: AppTheme? = null,
    amoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    // desktop: preferences read as state; Mihon recreates the Activity and applies ThemeMode via AppCompatDelegate
    val uiPreferences = remember { DesktopApp.graph.uiPreferences }
    val savedTheme by uiPreferences.appTheme.collectAsState()
    val savedAmoled by uiPreferences.themeDarkAmoled.collectAsState()
    val themeMode by uiPreferences.themeMode.collectAsState()
    BaseTachiyomiTheme(
        appTheme = appTheme ?: savedTheme,
        isAmoled = amoled ?: savedAmoled,
        isDark = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        },
        content = content,
    )
}
```

    `BaseTachiyomiTheme` recibe `isDark: Boolean` como parámetro en lugar de llamar a `isSystemInDarkTheme()`. Imports: `androidx.compose.runtime.getValue`, `tachiyomi.presentation.core.util.collectAsState`, `eu.kanade.domain.ui.model.ThemeMode` y `mihon.desktop.app.DesktopApp`. Borrar `TachiyomiPreviewTheme`, que deja de usarse, y `import mihon.app.di.appGraph`.
- `Navigator.kt`: en `ScreenTransition`, el `content` por defecto pasa a envolver cada pantalla en su almacén de ViewModels:

```kotlin
    content: ScreenTransitionContent = { ProvideScreenViewModelStore(navigator, it) { it.Content() } },
```

    (import `mihon.desktop.app.navigation.ProvideScreenViewModelStore`).
- `AppBar.kt`, `DropdownMenu.kt` y `TabbedScreen.kt`: sin cambios. Si alguna API de material3 no existe en `1.12.0-alpha03`, usar la equivalente disponible y marcarla con `// desktop:`.

En `AppGraph.kt`, añadir el accesor:

```kotlin
    val uiPreferences: UiPreferences
```

- [ ] **Step 8: Pantalla raíz, contenido de la ventana y pantalla de error**

`HomeScreen.kt`:

```kotlin
package mihon.desktop.app.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab

/** Root screen: the navigation rail and the current tab (spec 2a, section 2: Explorar and Ajustes in plan 1). */
object HomeScreen : Screen() {
    /** In rail order; the first one opens at start. Settings must stay last for [AppEvents.openSettings]. */
    val tabs: List<Tab> = listOf(ExploreTabPlaceholder)

    @Composable
    override fun Content() {
        TabNavigator(tabs.first()) { tabNavigator ->
            LaunchedEffect(Unit) { AppEvents.openSettings.collect { tabNavigator.current = tabs.last() } }
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    tabs.forEach { tab ->
                        val options = tab.options
                        NavigationRailItem(
                            selected = tabNavigator.current.key == tab.key,
                            onClick = { tabNavigator.current = tab },
                            icon = { options.icon?.let { Icon(it, contentDescription = options.title) } },
                            label = { Text(options.title) },
                        )
                    }
                }
                CurrentTab()
            }
        }
    }
}
```

Y en el mismo archivo, la pestaña provisional que la Task 9 sustituye por `BrowseTab`:

```kotlin
private data object ExploreTabPlaceholder : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = stringResource(MR.strings.browse),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Explore),
        )

    @Composable
    override fun Content() {
        EmptyScreen(MR.strings.empty_screen)
    }
}
```

(imports: `cafe.adriel.voyager.navigator.tab.TabOptions`, `androidx.compose.ui.graphics.vector.rememberVectorPainter`, `mihon.icons.materialsymbols.MaterialSymbols`, `mihon.icons.materialsymbols.rounded.Explore`, `tachiyomi.i18n.MR`, `tachiyomi.presentation.core.i18n.stringResource` y `tachiyomi.presentation.core.screens.EmptyScreen`).

`CrashScreen.kt`:

```kotlin
package mihon.desktop.app.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Shown instead of the window content when composing it threw; [onRestart] composes it again from scratch. */
@Composable
fun CrashScreen(error: Throwable, onRestart: () -> Unit) {
    val context = LocalContext.current
    val trace = error.stackTraceToString()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(MR.strings.crash_screen_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = trace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { context.copyToClipboard("crash", trace) }) {
                Text(stringResource(MR.strings.action_copy_to_clipboard))
            }
            Button(onClick = onRestart) { Text(stringResource(MR.strings.crash_screen_restart_application)) }
        }
    }
}
```

(Las tres cadenas, `crash_screen_title`, `crash_screen_restart_application` y `action_copy_to_clipboard`, ya existen en `i18n`).

`AppContent.kt`:

```kotlin
package mihon.desktop.app.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cafe.adriel.voyager.navigator.Navigator
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import mihon.app.di.AppGraph
import mihon.desktop.app.platform.AppMessages

/** What every screen needs around it: context, view model factory and root store, theme. Tests reuse it. */
@Composable
fun AppProviders(graph: AppGraph, content: @Composable () -> Unit) {
    val rootViewModels = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    CompositionLocalProvider(
        LocalContext provides graph.context,
        LocalMetroViewModelFactory provides graph.metroViewModelFactory,
        LocalViewModelStoreOwner provides rootViewModels,
    ) {
        TachiyomiTheme(content = content)
    }
}

/** Everything inside the window: the crash screen if composing threw, else the navigator and the snackbar. */
@Composable
fun AppContent(graph: AppGraph, crash: Throwable?, onRestart: () -> Unit) {
    AppProviders(graph) {
        if (crash != null) {
            CrashScreen(crash, onRestart)
            return@AppProviders
        }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { AppMessages.messages.collect { snackbar.showSnackbar(it) } }
        Box(Modifier.fillMaxSize()) {
            Navigator(HomeScreen) { DefaultNavigatorScreenTransition(it) }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
```

Borrar de este archivo los imports que no se usen (`key`, `mutableIntStateOf`, `setValue`).

- [ ] **Step 9: La ventana usa `AppContent`, atajos y captura de errores de composición**

Sustituir `Main.kt` por la versión de la Task 6 cuando se haga. Por ahora:

```kotlin
package mihon.desktop.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import dev.zacsweers.metro.createGraphFactory
import logcat.LogPriority
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.main.shortcutFor
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.core.AppDirs
import mihon.desktop.core.Platform
import tachiyomi.core.common.util.system.logcat

fun main() {
    val graph = Platform.start(AppDirs.fromEnvironment()) { context, dirs, debug ->
        createGraphFactory<DesktopAppGraph.Factory>().create(context, dirs, debug)
    }
    DesktopApp.graph = graph
    AppMessages.install()

    application {
        var crash by remember { mutableStateOf<Throwable?>(null) }
        var restarts by remember { mutableIntStateOf(0) }
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides WindowExceptionHandlerFactory {
                WindowExceptionHandler { error ->
                    logcat(LogPriority.ERROR, error) { "Window content crashed" }
                    crash = error
                }
            },
        ) {
            Window(
                onCloseRequest = ::exitApplication,
                title = AppDirs.APP_NAME,
                onPreviewKeyEvent = { event ->
                    event.type == KeyEventType.KeyDown &&
                        shortcutFor(event.key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
                            ?.let(AppEvents::send) == true
                },
            ) {
                key(restarts) {
                    AppContent(graph, crash) {
                        crash = null
                        restarts++
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 10: Compilar, ejecutar los tests del módulo y probar la ventana**

Run: `./gradlew :desktop-app:test`
Expected: PASS, con todos los tests del módulo hasta ahora.

Run: `./gradlew :desktop-app:run`
Expected: la ventana muestra el rail con "Explorar" y el contenido vacío de Mihon. Pulsar Esc no hace nada (no hay a dónde volver). Cerrar la ventana termina el proceso.

Con Windows en modo oscuro (Configuración › Personalización › Colores › Oscuro), la ventana tiene que salir oscura, porque el tema es "Sistema" por defecto.

Si sale clara, `isSystemInDarkTheme()` no detecta el modo de Windows (riesgo de la sección 7 del spec). En ese caso, crear `desktop-app/src/main/kotlin/mihon/desktop/app/platform/WindowsTheme.kt`, usar `WindowsTheme.isDark()` en lugar de `isSystemInDarkTheme()` en la rama `ThemeMode.SYSTEM` de `TachiyomiTheme`, y anotar el ruling:

```kotlin
package mihon.desktop.app.platform

/** Reads Windows' app color mode from the registry; light if it can't be read. */
object WindowsTheme {
    fun isDark(): Boolean = runCatching {
        val process = ProcessBuilder(
            "reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        // "AppsUseLightTheme    REG_DWORD    0x0" means dark
        Regex("AppsUseLightTheme\\s+REG_DWORD\\s+0x0\\b").containsMatchIn(output)
    }.getOrDefault(false)
}
```

(escribirlo con la herramienta Write, porque lleva `\\`).

- [ ] **Step 11: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): window shell with Mihon's theme, rail, shortcuts and per-screen view models

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Arranque: instancia única, error de arranque, estado de la ventana y logs

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/startup/WindowStateStore.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/startup/Startup.kt`
- Create: `desktop-app/src/main/resources/logback-app.xml`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/startup/WindowStateStoreTest.kt`

**Interfaces:**
- Consumes: `SingleInstance.acquire(lockFile, args)` y `SingleInstance.Result` (`desktop-core`); `PreferenceStore`; `AppDirs`.
- Produces:
  - `data class SavedWindow(val x: Int, val y: Int, val width: Int, val height: Int, val maximized: Boolean)`.
  - `fun placeOnScreens(saved: SavedWindow?, screens: List<java.awt.Rectangle>): SavedWindow`, una función pura.
  - `class WindowStateStore(store: PreferenceStore) { fun load(): SavedWindow?; fun save(window: SavedWindow) }`.
  - `object Startup { fun configureLogging(dirs: AppDirs); fun fatal(message: String, logs: Path): Nothing }`.

- [ ] **Step 1: Escribir el test (falla: no existe)**

`desktop-app/src/test/kotlin/mihon/desktop/app/startup/WindowStateStoreTest.kt`:

```kotlin
package mihon.desktop.app.startup

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.Rectangle

class WindowStateStoreTest {
    private val primary = Rectangle(0, 0, 1920, 1080)
    private val secondary = Rectangle(1920, 0, 2560, 1440)

    @Test
    fun `a window that fits on a screen keeps its place`() {
        val saved = SavedWindow(x = 2000, y = 100, width = 1400, height = 900, maximized = true)
        placeOnScreens(saved, listOf(primary, secondary)) shouldBe saved
    }

    @Test
    fun `a window on a screen that was unplugged moves to the primary one`() {
        val saved = SavedWindow(x = 2000, y = 100, width = 1400, height = 900, maximized = false)
        placeOnScreens(saved, listOf(primary)) shouldBe SavedWindow(320, 140, 1280, 800, false)
    }

    @Test
    fun `nothing saved opens centered at the default size`() {
        placeOnScreens(null, listOf(primary)) shouldBe SavedWindow(320, 140, 1280, 800, false)
    }

    @Test
    fun `a window smaller than the minimum grows to it`() {
        val saved = SavedWindow(x = 10, y = 10, width = 300, height = 200, maximized = false)
        placeOnScreens(saved, listOf(primary)) shouldBe SavedWindow(10, 10, 800, 600, false)
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.startup.WindowStateStoreTest"`
Expected: FAIL de compilación: `Unresolved reference 'SavedWindow'`.

- [ ] **Step 3: `WindowStateStore`**

```kotlin
package mihon.desktop.app.startup

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.awt.Rectangle

data class SavedWindow(val x: Int, val y: Int, val width: Int, val height: Int, val maximized: Boolean)

const val MIN_WIDTH = 800
const val MIN_HEIGHT = 600
private const val DEFAULT_WIDTH = 1280
private const val DEFAULT_HEIGHT = 800

/**
 * Where the window opens: where it was if its top-left corner is still on a screen, else centered on the
 * first (primary) screen at the default size. Never below the minimum size.
 */
fun placeOnScreens(saved: SavedWindow?, screens: List<Rectangle>): SavedWindow {
    val onScreen = saved?.takeIf { s -> screens.any { it.contains(s.x, s.y) } }
    if (onScreen != null) {
        return onScreen.copy(width = onScreen.width.coerceAtLeast(MIN_WIDTH), height = onScreen.height.coerceAtLeast(MIN_HEIGHT))
    }
    val primary = screens.first()
    return SavedWindow(
        x = primary.x + (primary.width - DEFAULT_WIDTH) / 2,
        y = primary.y + (primary.height - DEFAULT_HEIGHT) / 2,
        width = DEFAULT_WIDTH,
        height = DEFAULT_HEIGHT,
        maximized = false,
    )
}

/** The window's last bounds, kept in the app preferences as "x,y,width,height,maximized". */
class WindowStateStore(store: PreferenceStore) {
    private val pref = store.getString(Preference.appStateKey("window_bounds"), "")

    fun load(): SavedWindow? {
        val parts = pref.get().split(',')
        if (parts.size != 5) return null
        val (x, y, w, h) = parts.take(4).map { it.toIntOrNull() ?: return null }
        return SavedWindow(x, y, w, h, parts[4].toBooleanStrictOrNull() ?: return null)
    }

    fun save(window: SavedWindow) {
        pref.set("${window.x},${window.y},${window.width},${window.height},${window.maximized}")
    }
}
```

- [ ] **Step 4: Ejecutar el test y ver que pasa**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.startup.WindowStateStoreTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Logs y errores fatales de arranque**

`desktop-app/src/main/resources/logback-app.xml` (escribirlo con la herramienta Write). No se llama `logback.xml` porque `desktop-core` ya trae uno de solo consola, que siguen usando el harness y los tests; dos `logback.xml` en el classpath harían que logback eligiera uno al azar:

```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${mangareader.logs}/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${mangareader.logs}/app.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{24} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="FILE" />
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

`Startup.kt`:

```kotlin
package mihon.desktop.app.startup

import logcat.LogPriority
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.core.AppDirs
import tachiyomi.core.common.util.system.logcat
import java.nio.file.Path
import javax.swing.JOptionPane
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

object Startup {
    /** Must run before the first logger is created: points logback at the app config and its log folder. */
    fun configureLogging(dirs: AppDirs) {
        dirs.logs.createDirectories()
        System.setProperty("mangareader.logs", dirs.logs.toString())
        System.setProperty("logback.configurationFile", "logback-app.xml")
        // A failed view model coroutine ends here too (Dispatchers.Main is the Swing thread): log it and tell the
        // user, the screen stays up
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            logcat(LogPriority.ERROR, error) { "Uncaught exception in ${thread.name}" }
            AppMessages.show("Error: ${error.message ?: error::class.simpleName}")
        }
    }

    /** The app can't start: says why and where the log is, then exits with 1. Never leaves a windowless process. */
    fun fatal(message: String, logs: Path): Nothing {
        JOptionPane.showMessageDialog(null, "$message\n\nLog: $logs", AppDirs.APP_NAME, JOptionPane.ERROR_MESSAGE)
        exitProcess(1)
    }
}
```

- [ ] **Step 6: `main` completo**

Sustituir en `Main.kt` la función `main` y el bloque `Window` por esta versión. El resto del archivo (contenido de la ventana, `AppContent`) se queda como en la Task 5:

```kotlin
fun main(args: Array<String>) {
    val dirs = AppDirs.fromEnvironment()
    Startup.configureLogging(dirs)

    val instance = when (val result = SingleInstance.acquire(dirs.lockFile, args.toList())) {
        is SingleInstance.Result.Primary -> result.instance
        SingleInstance.Result.Forwarded -> exitProcess(0)
        // No Context exists yet, so the string is resolved with moko directly
        SingleInstance.Result.Unreachable -> Startup.fatal(
            StringDesc.Resource(MR.strings.desktop_instance_unreachable).localized(),
            dirs.logs,
        )
    }
    // A second launch forwards its arguments here; accept() blocks, so it runs on IO, never on the UI thread
    val bringToFront = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    instance.listen(CoroutineScope(SupervisorJob() + Dispatchers.IO)) { bringToFront.tryEmit(Unit) }

    val graph = try {
        Platform.start(dirs) { context, appDirs, debug ->
            createGraphFactory<DesktopAppGraph.Factory>().create(context, appDirs, debug)
        }
    } catch (e: Throwable) {
        logcat(LogPriority.ERROR, e) { "Startup failed" }
        Startup.fatal(e.message ?: e.toString(), dirs.logs)
    }
    DesktopApp.graph = graph
    AppMessages.install()
    val windowStore = WindowStateStore(graph.preferenceStore)
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
    val initial = placeOnScreens(windowStore.load(), screens)

    application {
        val windowState = rememberWindowState(
            placement = if (initial.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = WindowPosition(initial.x.dp, initial.y.dp),
            size = DpSize(initial.width.dp, initial.height.dp),
        )
        var crash by remember { mutableStateOf<Throwable?>(null) }
        var restarts by remember { mutableIntStateOf(0) }
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides WindowExceptionHandlerFactory {
                WindowExceptionHandler { error ->
                    logcat(LogPriority.ERROR, error) { "Window content crashed" }
                    crash = error
                }
            },
        ) {
            Window(
                state = windowState,
                onCloseRequest = {
                    windowStore.save(windowState.toSaved())
                    instance.close()
                    exitApplication()
                },
                title = AppDirs.APP_NAME,
                onPreviewKeyEvent = { event ->
                    event.type == KeyEventType.KeyDown &&
                        shortcutFor(event.key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
                            ?.let(AppEvents::send) == true
                },
            ) {
                window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
                LaunchedEffect(Unit) { bringToFront.collect { window.toFront() } }
                key(restarts) {
                    AppContent(graph, crash) {
                        crash = null
                        restarts++
                    }
                }
            }
        }
    }
}

private fun WindowState.toSaved() = SavedWindow(
    x = position.x.value.toInt(),
    y = position.y.value.toInt(),
    width = size.width.value.toInt(),
    height = size.height.value.toInt(),
    maximized = placement == WindowPlacement.Maximized,
)
```

Imports nuevos:
- `java.awt.Dimension` y `java.awt.GraphicsEnvironment`.
- `kotlin.system.exitProcess`.
- `androidx.compose.ui.unit.DpSize` y `androidx.compose.ui.unit.dp`.
- `androidx.compose.ui.window.WindowPlacement`, `WindowPosition`, `WindowState` y `rememberWindowState`.
- `androidx.compose.runtime.LaunchedEffect`.
- `kotlinx.coroutines.CoroutineScope`, `SupervisorJob`, `Dispatchers` y `flow.MutableSharedFlow`.
- `mihon.desktop.core.SingleInstance`.
- `mihon.desktop.app.startup.*`.
- `dev.icerock.moko.resources.desc.Resource`, `dev.icerock.moko.resources.desc.StringDesc` y `tachiyomi.i18n.MR`.

Añadir la cadena al final de `i18n/src/commonMain/moko-resources/base/strings.xml`:

```xml
    <!-- Desktop -->
    <string name="desktop_instance_unreachable">Another window of the app is already open but isn\'t responding. Close it from the Task Manager and try again.</string>
```

Y en `.../es/strings.xml`:

```xml
    <!-- Desktop -->
    <string name="desktop_instance_unreachable">Ya hay otra ventana de la app abierta, pero no responde. Ciérrala desde el Administrador de tareas e inténtalo de nuevo.</string>
```

- [ ] **Step 7: Comprobación manual del arranque**

1. Run: `./gradlew :desktop-app:run`. Mover y redimensionar la ventana, y cerrarla. Volver a ejecutar.
   Expected: abre en la misma posición y con el mismo tamaño.
2. Con la app abierta, ejecutar `./gradlew :desktop-app:run` en otra terminal.
   Expected: el segundo proceso termina enseguida y la primera ventana pasa al frente.
3. Abrir `%LOCALAPPDATA%\MangaReader\logs\app.log`.
   Expected: existe y contiene líneas de arranque.

- [ ] **Step 8: Ejecutar los tests del módulo**

Run: `./gradlew :desktop-app:test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): single instance, startup errors, window bounds and rolling logs

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---
### Task 7: Imágenes: Coil, portadas e iconos de extensión

**Files:**
- Modify: `extension-loader/src/main/kotlin/mihon/desktop/extension/ApkInspector.kt`
- Modify: `extension-loader/src/main/kotlin/mihon/desktop/extension/ExtensionInstaller.kt`
- Modify: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopExtensionManager.kt`
- Modify: `desktop-app/build.gradle.kts`
- Modify: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt` (accesor `coverCache`)
- Create (portados, mismo paquete):
  - `eu/kanade/tachiyomi/data/cache/CoverCache.kt`
  - `eu/kanade/tachiyomi/data/coil/MangaCoverFetcher.kt`
  - `eu/kanade/tachiyomi/data/coil/MangaCoverKeyer.kt`
  - `eu/kanade/tachiyomi/data/coil/BufferedSourceFetcher.kt`
  - `eu/kanade/tachiyomi/data/coil/CoverRequestInterceptor.kt`
  - `eu/kanade/tachiyomi/data/coil/DeDupeConcurrentRequests.kt`
  - `eu/kanade/tachiyomi/data/coil/Utils.kt`
  - `eu/kanade/domain/manga/model/Manga.kt` (lo necesita `MangaKeyer`: `hasCustomCover`)
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/coil/ExtensionIconFetcher.kt` (versión de escritorio, con los mismos nombres de modelo)
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/image/DesktopImageLoader.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt` (instala el `ImageLoader`)
- Test: `extension-loader/src/test/kotlin/mihon/desktop/extension/ApkIconTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/image/DesktopImageLoaderTest.kt`

**Interfaces:**
- Consumes: `DesktopExtensionManager`, `NetworkHelper.client`, `SourceManager`, `AppDirs`.
- Produces:
  - `ApkInspector.icon(apk: Path): ByteArray?` y `ApkInspector.pickIcon(icons: List<Icon>): ByteArray?`.
  - `ExtensionInstaller.apkFor(pkgName: String, versionCode: Long): Path`.
  - `DesktopExtensionManager.installedApk(pkgName: String): Path?`.
  - `data class InstalledExtensionIcon(pkgName, versionCode, density)` y `data class InstalledSourceIcon(sourceId)`: mismos nombres y campos que Mihon, que usa `BrowseIcons.kt` (Task 9).
  - `object DesktopImageLoader { fun create(graph: AppGraph): ImageLoader; fun install(graph: AppGraph) }`.
  - `CoverCache(scope, appDirs)`.

- [ ] **Step 1: Escribir los tests (fallan)**

`extension-loader/src/test/kotlin/mihon/desktop/extension/ApkIconTest.kt`:

```kotlin
package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import net.dongliu.apk.parser.bean.Icon
import org.junit.jupiter.api.Test

class ApkIconTest {
    @Test
    fun `the densest bitmap launcher icon wins`() {
        val icons = listOf(
            Icon("res/mipmap-mdpi-v4/ic_launcher.png", 160, byteArrayOf(1)),
            Icon("res/mipmap-xxxhdpi-v4/ic_launcher.png", 640, byteArrayOf(4)),
            Icon("res/mipmap-anydpi-v26/ic_launcher.xml", 65534, byteArrayOf(9)),
            Icon("res/mipmap-xhdpi-v4/ic_launcher.png", 320, null),
        )
        ApkInspector.pickIcon(icons)?.toList() shouldBe listOf<Byte>(4)
    }

    @Test
    fun `an apk with only an adaptive icon has no bitmap to show`() {
        ApkInspector.pickIcon(listOf(Icon("res/mipmap-anydpi-v26/ic_launcher.xml", 65534, byteArrayOf(9)))) shouldBe null
    }
}
```

`desktop-app/src/test/kotlin/mihon/desktop/app/image/DesktopImageLoaderTest.kt`:

```kotlin
package mihon.desktop.app.image

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.manga.model.MangaCover
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopImageLoaderTest {
    private lateinit var graph: TestAppGraph

    @BeforeAll
    fun start() {
        graph = startTestApp()
    }

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(4, 6, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    @Test
    fun `a manga cover from a source is downloaded and decoded`(): Unit = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png())))
        server.start()

        val cover = MangaCover(
            mangaId = 1,
            sourceId = 1,
            isMangaFavorite = false,
            url = server.url("/cover.png").toString(),
            lastModified = 0,
        )
        val result = DesktopImageLoader.create(graph).execute(
            ImageRequest.Builder(PlatformContext.INSTANCE).data(cover).build(),
        )

        result.shouldBeInstanceOf<SuccessResult>()
        server.shutdown()
    }
}
```

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :extension-loader:test --tests "*ApkIconTest" :desktop-app:test --tests "*DesktopImageLoaderTest"`
Expected: FAIL de compilación: `Unresolved reference 'pickIcon'` y `'DesktopImageLoader'`.

- [ ] **Step 3: Icono del APK y ruta del APK instalado**

En `ApkInspector.kt`, añadir:

```kotlin
    /** The launcher icon with the highest density, read from an installed (already verified) apk. */
    fun icon(apk: Path): ByteArray? = ApkFile(apk.toFile()).use { pickIcon(it.iconFiles) }

    /** Bitmaps only: an adaptive icon is XML that only Android can draw. */
    fun pickIcon(icons: List<Icon>): ByteArray? = icons
        .filter { it.data != null && !it.path.endsWith(".xml") }
        .maxByOrNull { it.density }
        ?.data
```

(import `net.dongliu.apk.parser.bean.Icon`).

En `ExtensionInstaller.kt`, añadir y usar en `install` para construir `apk` (`val apk = apkFor(info.pkgName, info.versionCode)` en lugar de `dir.resolve("$base.apk")`, manteniendo `base` para los demás nombres):

```kotlin
    /** Where the apk of this version lives once installed. */
    fun apkFor(pkgName: String, versionCode: Long): Path = dir.resolve("$pkgName-$versionCode.apk")
```

Comprobar antes que `base` vale exactamente `"$pkgName-$versionCode"` en `install` (ver la KDoc de la clase: `<pkg>-<versionCode>.apk`). Si no coincide, ajustar `apkFor` al formato real, no al revés.

En `DesktopExtensionManager.kt`, añadir:

```kotlin
    /** The apk of the installed version of [pkgName], for reading its icon. */
    fun installedApk(pkgName: String): Path? = installed.value
        .find { it.pkgName == pkgName }
        ?.let { installer.apkFor(it.pkgName, it.versionCode) }
```

- [ ] **Step 4: Portar portadas y caché**

En `desktop-app/build.gradle.kts`, añadir a `dependencies`:

```kotlin
    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(projects.extensionLoader)
    testImplementation(libs.okhttp.mockwebserver)
```

Copiar los archivos portados de la lista, con estos cambios:

- `CoverCache.kt`:
  - El constructor recibe `private val appDirs: AppDirs` en lugar de `private val context: Context`; quitar el import de `Context`.
  - `getCacheDir(dir)` pasa a ser:

```kotlin
    // desktop: library covers persist next to the other local data, not in a cache Windows may clear
    private fun getCacheDir(dir: String): File = appDirs.local.resolve(dir).toFile().also { it.mkdirs() }
```

- `MangaCoverFetcher.kt`:
  - Borrar `import androidx.core.net.toUri` y la función `fileUriLoader`.
  - La rama `Type.URI -> fileUriLoader(url)` pasa a ser `Type.URI -> error("content:// covers only exist on Android") // desktop`.
- `Manga.kt` (`eu.kanade.domain.manga.model`):
  - Borrar `import android.content.Context` y las funciones que reciben `Context`.
  - Borrar `import mihon.app.di.appGraph` si solo lo usaban esas funciones.
  - Si `ReadingMode` o `ReaderOrientation` aparecen en funciones de este archivo, borrar esas funciones. Vuelven en el plan 2 junto con el lector.
  - Borrar `editCover(...)`, que recibe `LocalCoverManager` de `source-local` (subproyecto 4). La versión de escritorio de `MangaCoverViewModel` (Task 11) guarda la portada personalizada con `CoverCache` directamente.
  - Mantener `hasCustomCover`, `toSManga`, `copyFrom` y lo demás que no dependa de Android.
- `BufferedSourceFetcher.kt`, `CoverRequestInterceptor.kt`, `DeDupeConcurrentRequests.kt`, `MangaCoverKeyer.kt` y `Utils.kt`: sin cambios.

En `AppGraph.kt`, añadir `val coverCache: CoverCache`.

- [ ] **Step 5: Iconos de extensión en escritorio**

`desktop-app/src/main/kotlin/eu/kanade/tachiyomi/data/coil/ExtensionIconFetcher.kt`:

```kotlin
package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.extension.model.Extension
import mihon.desktop.core.DesktopExtensionManager
import mihon.desktop.extension.ApkInspector
import okio.Buffer
import okio.FileSystem

/** The icon of an installed extension. [density] is ignored on desktop: the densest bitmap is always used. */
data class InstalledExtensionIcon(
    val pkgName: String,
    val versionCode: Long,
    val density: Int,
)

/** The icon of the installed extension that provides the source with [sourceId]. */
data class InstalledSourceIcon(val sourceId: Long)

// desktop: reads the launcher icon from the installed apk instead of Android's PackageManager
class ExtensionIconFetcher(private val getPkgName: suspend () -> String?, private val manager: DesktopExtensionManager) :
    Fetcher {
    override suspend fun fetch(): FetchResult {
        val pkgName = getPkgName() ?: error("No installed extension")
        val apk = manager.installedApk(pkgName) ?: error("Extension $pkgName isn't installed")
        val bytes = ApkInspector.icon(apk) ?: error("Extension $pkgName has no bitmap icon")
        return SourceFetchResult(
            source = ImageSource(source = Buffer().write(bytes), fileSystem = FileSystem.SYSTEM),
            mimeType = "image/png",
            dataSource = DataSource.DISK,
        )
    }

    class ExtensionFactory(private val manager: DesktopExtensionManager) : Fetcher.Factory<InstalledExtensionIcon> {
        override fun create(data: InstalledExtensionIcon, options: Options, imageLoader: ImageLoader): Fetcher =
            ExtensionIconFetcher({ data.pkgName }, manager)
    }

    class SourceFactory(private val manager: DesktopExtensionManager) : Fetcher.Factory<InstalledSourceIcon> {
        override fun create(data: InstalledSourceIcon, options: Options, imageLoader: ImageLoader): Fetcher =
            ExtensionIconFetcher(
                getPkgName = {
                    manager.installed.value
                        .filterIsInstance<Extension.Loaded>()
                        .find { ext -> ext.sources.any { it.id == data.sourceId } }
                        ?.pkgName
                },
                manager = manager,
            )
    }
}

class InstalledExtensionIconKeyer : Keyer<InstalledExtensionIcon> {
    override fun key(data: InstalledExtensionIcon, options: Options): String =
        "extension;${data.pkgName};${data.versionCode}"
}

class InstalledSourceIconKeyer : Keyer<InstalledSourceIcon> {
    override fun key(data: InstalledSourceIcon, options: Options): String = "source;${data.sourceId}"
}
```

- [ ] **Step 6: `DesktopImageLoader`**

```kotlin
package mihon.desktop.app.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import eu.kanade.tachiyomi.data.coil.BufferedSourceFetcher
import eu.kanade.tachiyomi.data.coil.CoverRequestInterceptor
import eu.kanade.tachiyomi.data.coil.ExtensionIconFetcher
import eu.kanade.tachiyomi.data.coil.InstalledExtensionIconKeyer
import eu.kanade.tachiyomi.data.coil.InstalledSourceIconKeyer
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer
import eu.kanade.tachiyomi.data.coil.MangaKeyer
import kotlinx.coroutines.Dispatchers
import mihon.app.di.AppGraph
import okio.Path.Companion.toOkioPath

/** Mihon's App.newImageLoader for desktop: same fetchers and keyers, Skia decoding, caches under AppDirs. */
object DesktopImageLoader {
    fun create(graph: AppGraph): ImageLoader {
        val callFactory = lazy { graph.networkHelper.client }
        return ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { callFactory.value }))
                add(BufferedSourceFetcher.Factory())
                add(MangaCoverFetcher.MangaCoverFactory(callFactory, graph.coverCache, graph.sourceManager))
                add(MangaCoverFetcher.MangaFactory(callFactory, graph.coverCache, graph.sourceManager))
                add(ExtensionIconFetcher.ExtensionFactory(graph.extensionManager))
                add(ExtensionIconFetcher.SourceFactory(graph.extensionManager))
                add(MangaCoverKeyer(graph.coverCache))
                add(MangaKeyer())
                add(InstalledExtensionIconKeyer())
                add(InstalledSourceIconKeyer())
                add(CoverRequestInterceptor())
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(PlatformContext.INSTANCE, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(graph.appDirs.cache.resolve("covers").toOkioPath())
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            // Coil spawns a new thread for every image load by default
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
            .build()
    }

    fun install(graph: AppGraph) {
        SingletonImageLoader.setSafe { create(graph) }
    }
}
```

`graph.extensionManager` es aquí el `DesktopExtensionManager` de `DesktopGraph`. La fachada de Mihon de la Task 8 se expone con otro nombre, `mihonExtensionManager`.

En `Main.kt`, justo después de `AppMessages.install()`, añadir `DesktopImageLoader.install(graph)`.

- [ ] **Step 7: Ejecutar los tests y ver que pasan**

Run: `./gradlew :extension-loader:test :desktop-core:test :desktop-app:test`
Expected: PASS. Pasan `ApkIconTest` (2), `DesktopImageLoaderTest` (1) y los tests anteriores.

- [ ] **Step 8: Commit**

```bash
git add extension-loader desktop-core desktop-app
git commit -m "feat(desktop-app): Coil image loading for covers and extension icons

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: `ExtensionManager` de escritorio y filtro de content warning

`ExtensionManager` es una fachada con el nombre y la API que usan los ViewModels de Mihon, construida sobre `DesktopExtensionManager`. El filtro por content warning se aplica en el loader, antes de instanciar clases, como en Mihon.

**Files:**
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/ContentWarningPrefs.kt`
- Modify: `extension-loader/src/main/kotlin/mihon/desktop/extension/DesktopExtensionLoader.kt`
- Modify: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopExtensionManager.kt`
- Create (portados, mismo paquete):
  - `eu/kanade/domain/source/service/SourcePreferences.kt`
  - `eu/kanade/domain/source/interactor/SetMigrateSorting.kt`
  - `mihon/domain/migration/models/MigrationFlag.kt`
  - `eu/kanade/tachiyomi/extension/model/InstallStep.kt`
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/extension/ExtensionManager.kt` (fachada)
- Modify: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt`
- Modify: `compat-harness/src/main/kotlin/mihon/desktop/harness/Main.kt`
- Test: `extension-loader/src/test/kotlin/mihon/desktop/extension/DesktopExtensionLoaderTest.kt` (un caso nuevo)
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/ContentWarningFilterTest.kt`
- Test: `desktop-app/src/test/kotlin/eu/kanade/tachiyomi/extension/ExtensionManagerTest.kt`

**Interfaces:**
- Consumes:
  - `DesktopExtensionManager.installed`, `available()`, `install`, `uninstall` y `trust`.
  - `Extension.Installed.findUpdate`/`findListing` (`domain`).
- Produces:
  - `object ContentWarningPrefs`, con `ENABLED_KEY`, `APPLY_TO_INSTALLED_KEY`, `DEFAULT_ENABLED = setOf(SAFE, MIXED)`, `enabled(store)` y `applyToInstalled(store)`.
  - `DesktopExtensionLoader.load(installed, stores, isAllowed: (ContentWarning) -> Boolean = { true })`.
  - `DesktopExtensionManager`, ahora con el parámetro `preferenceStore` y recarga automática cuando cambian los ajustes de content warning.
  - `class ExtensionManager`, la fachada, con:
    - `loadedExtensionsFlow`, `notLoadedExtensionsFlow` y `availableExtensionsFlow`.
    - `getLoadedExtensions()`, `getExtensionPackage(sourceId)`, `getExtensionPackageAsFlow(sourceId)` y `getSourceData(id)`.
    - `findAvailableExtensions()`.
    - `installExtension(ext): Flow<InstallStep>`, `updateExtension(ext): Flow<InstallStep>` y `cancelInstallUpdateExtension(ext)`.
    - `uninstallExtension(ext)` y `trust(ext)`.
  - `AppGraph`:
    - `val extensionManager` sigue siendo el de `DesktopGraph`, el `DesktopExtensionManager`.
    - Nuevo accesor `val mihonExtensionManager: ExtensionManager` para el código nuevo.
    - Nuevos accesores `val sourcePreferences: SourcePreferences` y `val getExtensionStoreCountAsFlow`.

- [ ] **Step 1: Escribir los tests (fallan)**

En `DesktopExtensionLoaderTest.kt`, añadir:

```kotlin
    @Test
    fun `a content warning the user didn't allow is filtered before any class loads`() {
        var instantiated = false
        val filtering = DesktopExtensionLoader(ExtensionTrust(InMemoryPreferenceStore())) { _, _ ->
            instantiated = true
            emptyList()
        }
        val ext = filtering.load(
            installed(mapOf("tachiyomi.extension.class" to "x", "tachiyomix.contentWarning" to "2")),
            listOf(store),
            isAllowed = { it != ContentWarning.NSFW },
        )
        ext.shouldBeInstanceOf<Extension.NotLoaded>().reason shouldBe Extension.NotLoaded.Reason.Filtered
        instantiated shouldBe false
    }
```

(import `mihon.domain.extension.model.ContentWarning`).

`desktop-core/src/test/kotlin/mihon/desktop/core/ContentWarningFilterTest.kt`:

```kotlin
package mihon.desktop.core

import io.kotest.matchers.shouldBe
import mihon.domain.extension.model.ContentWarning
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class ContentWarningFilterTest {
    @Test
    fun `adult content is hidden by default on desktop`() {
        val store = InMemoryPreferenceStore()
        ContentWarningPrefs.isAllowed(store, ContentWarning.SAFE) shouldBe true
        ContentWarningPrefs.isAllowed(store, ContentWarning.MIXED) shouldBe true
        ContentWarningPrefs.isAllowed(store, ContentWarning.NSFW) shouldBe false
    }

    @Test
    fun `installed extensions load regardless when the filter doesn't apply to them`() {
        val store = InMemoryPreferenceStore()
        ContentWarningPrefs.applyToInstalled(store).set(false)
        ContentWarningPrefs.isAllowed(store, ContentWarning.NSFW) shouldBe true
    }

    @Test
    fun `allowing NSFW in the settings lets it load`() {
        val store = InMemoryPreferenceStore()
        ContentWarningPrefs.enabled(store).set(ContentWarning.entries.toSet())
        ContentWarningPrefs.isAllowed(store, ContentWarning.NSFW) shouldBe true
    }
}
```

`desktop-app/src/test/kotlin/eu/kanade/tachiyomi/extension/ExtensionManagerTest.kt`:

```kotlin
package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtensionManagerTest {
    private lateinit var graph: TestAppGraph

    @BeforeAll
    fun start() {
        graph = startTestApp()
    }

    private fun available(apkUrl: String) = Extension.Available(
        name = "Example", pkgName = "pkg.example", versionName = "1.4.1", versionCode = 1, libVersion = 1.4,
        lang = "en", contentWarning = ContentWarning.SAFE, sources = emptyList(), apkUrl = apkUrl, iconUrl = "",
        store = ExtensionStore("u", "Repo", "R", "key", ExtensionStore.Contact("w", null), false, null),
    )

    @Test
    fun `a failed install reports its steps and ends in error instead of throwing`(): Unit = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("PK\u0003 truncated"))
        server.start()

        val steps = graph.mihonExtensionManager.installExtension(available(server.url("/ext.apk").toString())).toList()

        steps shouldBe listOf(InstallStep.Pending, InstallStep.Downloading, InstallStep.Error)
        server.shutdown()
    }

    @Test
    fun `an extension with an update is flagged against the store listing`() {
        val installed = Extension.Loaded(
            name = "Example", pkgName = "pkg.example", versionName = "1.4.1", versionCode = 1, libVersion = 1.4,
            lang = "en", contentWarning = ContentWarning.SAFE, isShared = false, signatures = listOf("key"),
            pkgFactory = null, sources = emptyList(),
        )
        val newer = available("").copy(versionCode = 2)

        ExtensionManager.withStatus(installed, listOf(newer)).hasUpdate shouldBe true
        ExtensionManager.withStatus(installed, listOf(newer)).let { (it as Extension.Loaded).isObsolete } shouldBe false
        ExtensionManager.withStatus(installed, emptyList()) shouldBe installed
    }
}
```

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :extension-loader:test --tests "*DesktopExtensionLoaderTest" :desktop-core:test --tests "*ContentWarningFilterTest" :desktop-app:test --tests "*ExtensionManagerTest"`
Expected: FAIL de compilación: `No parameter with name 'isAllowed'`, `Unresolved reference 'ContentWarningPrefs'` y `'mihonExtensionManager'`.

- [ ] **Step 3: El loader filtra por content warning**

En `DesktopExtensionLoader.load`, añadir el parámetro `isAllowed: (ContentWarning) -> Boolean = { true }`. Justo después de la comprobación de confianza (`if (!trust.isTrusted(...)) return notLoaded(Untrusted...)`), añadir:

```kotlin
        // Same place as Mihon's loader: after trust, before any of the extension's classes are touched
        if (!isAllowed(contentWarning)) return notLoaded(Extension.NotLoaded.Reason.Filtered, libVersion)
```

(import `mihon.domain.extension.model.ContentWarning`).

- [ ] **Step 4: `ContentWarningPrefs` y el manager la usa**

`desktop-core/src/main/kotlin/mihon/desktop/core/ContentWarningPrefs.kt`:

```kotlin
package mihon.desktop.core

import mihon.domain.extension.model.ContentWarning
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnumSet

/** Mihon's content warning preferences (same keys as its SourcePreferences), with the desktop default. */
object ContentWarningPrefs {
    const val ENABLED_KEY = "enabled_content_warnings"
    const val APPLY_TO_INSTALLED_KEY = "apply_content_warnings_to_installed"

    /** Spec 2a: adult content stays hidden until the user allows it in the settings. */
    val DEFAULT_ENABLED: Set<ContentWarning> = setOf(ContentWarning.SAFE, ContentWarning.MIXED)

    fun enabled(store: PreferenceStore): Preference<Set<ContentWarning>> = store.getEnumSet(ENABLED_KEY, DEFAULT_ENABLED)

    fun applyToInstalled(store: PreferenceStore): Preference<Boolean> = store.getBoolean(APPLY_TO_INSTALLED_KEY, true)

    fun isAllowed(store: PreferenceStore, warning: ContentWarning): Boolean =
        !applyToInstalled(store).get() || warning in enabled(store).get()
}
```

En `DesktopExtensionManager`:
- Añadir al constructor `private val preferenceStore: PreferenceStore` y `@AppCoroutineScope scope: CoroutineScope`.
- En las dos llamadas a `loader.load(...)` (en `reloadLocked` e `installLocked`), pasar `isAllowed = { ContentWarningPrefs.isAllowed(preferenceStore, it) }`.
- Añadir un bloque `init`:

```kotlin
    init {
        // Allowing NSFW in the settings loads those extensions right away, and disallowing it unloads them
        scope.launch {
            merge(
                ContentWarningPrefs.enabled(preferenceStore).changes().distinctUntilChanged().drop(1).map {},
                ContentWarningPrefs.applyToInstalled(preferenceStore).changes().distinctUntilChanged().drop(1).map {},
            ).collectLatest { reload() }
        }
    }
```

(imports: `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.flow.merge`, `kotlinx.coroutines.flow.distinctUntilChanged`, `kotlinx.coroutines.flow.drop`, `kotlinx.coroutines.flow.map`, `kotlinx.coroutines.flow.collectLatest`, `mihon.core.metro.AppCoroutineScope`, `tachiyomi.core.common.preference.PreferenceStore`).

- [ ] **Step 5: Portar `SourcePreferences` y dependencias, e `InstallStep`**

Copiar los cuatro archivos portados. Cambios en `SourcePreferences.kt`:

```kotlin
    // desktop: shared with DesktopExtensionManager, which filters with the same preferences
    val enabledContentWarnings: Preference<Set<ContentWarning>> = ContentWarningPrefs.enabled(preferenceStore)

    val applyContentWarningsToInstalled: Preference<Boolean> = ContentWarningPrefs.applyToInstalled(preferenceStore)
```

(sustituyen a las dos definiciones originales; import `mihon.desktop.core.ContentWarningPrefs`). El resto del archivo queda igual.

`SetMigrateSorting.kt`, `MigrationFlag.kt` e `InstallStep.kt`: sin cambios.

- [ ] **Step 6: La fachada `ExtensionManager`**

`desktop-app/src/main/kotlin/eu/kanade/tachiyomi/extension/ExtensionManager.kt`:

```kotlin
package eu.kanade.tachiyomi.extension

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.desktop.core.DesktopExtensionManager
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import java.util.concurrent.ConcurrentHashMap

/**
 * Mihon's ExtensionManager API over [DesktopExtensionManager], so its extension screens and view models run
 * unchanged: install progress as [InstallStep]s, update and obsolete flags derived from the store listings.
 */
@Inject
@SingleIn(AppScope::class)
class ExtensionManager(
    private val manager: DesktopExtensionManager,
    private val preferences: SourcePreferences,
    private val context: Context,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    // Every store's listing, since more than one store can list the same extension
    private val availableListings = MutableStateFlow(emptyList<Extension.Available>())

    private val installedWithStatus: StateFlow<List<Extension.Installed>> =
        combine(manager.installed, availableListings) { installed, available ->
            installed.map { withStatus(it, available) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val loadedExtensionsFlow: Flow<List<Extension.Loaded>> =
        installedWithStatus.map { it.filterIsInstance<Extension.Loaded>() }

    val notLoadedExtensionsFlow: Flow<List<Extension.NotLoaded>> =
        installedWithStatus.map { it.filterIsInstance<Extension.NotLoaded>() }

    // Stores sharing a signing key serve the same apks, so only the newest of their listings is shown
    val availableExtensionsFlow: StateFlow<List<Extension.Available>> = availableListings
        .map { extensions ->
            extensions.groupBy { it.pkgName to it.store.signingKey }.values.map { listings ->
                listings.maxWith(compareBy<Extension.Available> { it.versionCode }.thenBy { it.libVersion })
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val installJobs = ConcurrentHashMap<String, Job>()

    init {
        scope.launch {
            manager.reload()
            findAvailableExtensions()
        }
        scope.launch {
            installedWithStatus.collect { list -> preferences.extensionUpdatesCount.set(list.count { it.hasUpdate }) }
        }
    }

    suspend fun getLoadedExtensions(): List<Extension.Loaded> = loadedExtensionsFlow.first()

    suspend fun getExtensionPackage(sourceId: Long): String? =
        getLoadedExtensions().find { ext -> ext.sources.any { it.id == sourceId } }?.pkgName

    fun getExtensionPackageAsFlow(sourceId: Long): Flow<String?> =
        loadedExtensionsFlow.map { list -> list.find { ext -> ext.sources.any { it.id == sourceId } }?.pkgName }

    fun getSourceData(id: Long): StubSource? = availableListings.value
        .asSequence()
        .flatMap { it.sources }
        .find { it.id == id }
        ?.toStubSource()

    suspend fun findAvailableExtensions() {
        availableListings.value = try {
            manager.available()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            context.toast(MR.strings.extension_api_error)
            return
        }
    }

    fun installExtension(extension: Extension.Available): Flow<InstallStep> = flow {
        installJobs[extension.pkgName] = currentCoroutineContext().job
        try {
            emit(InstallStep.Pending)
            // Downloading and installing are one call on desktop; the list shows it as downloading
            emit(InstallStep.Downloading)
            manager.install(extension)
            emit(InstallStep.Installed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install ${extension.pkgName}" }
            context.toast(e.message)
            emit(InstallStep.Error)
        } finally {
            installJobs.remove(extension.pkgName)
        }
    }

    fun updateExtension(extension: Extension.Installed): Flow<InstallStep> {
        val update = extension.findUpdate(availableListings.value) ?: return emptyFlow()
        return installExtension(update)
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        installJobs.remove(extension.pkgName)?.cancel()
    }

    fun uninstallExtension(extension: Extension.Installed) {
        scope.launch(Dispatchers.IO) { manager.uninstall(extension.pkgName) }
    }

    fun trust(extension: Extension.NotLoaded) {
        scope.launch(Dispatchers.IO) { manager.trust(extension) }
    }

    companion object {
        /** What the listings say about an installed extension; with no listing at all, nothing is derived. */
        fun withStatus(extension: Extension.Installed, available: List<Extension.Available>): Extension.Installed {
            if (available.isEmpty()) return extension
            val hasUpdate = extension.findUpdate(available) != null
            return when (extension) {
                is Extension.Loaded -> extension.copy(
                    hasUpdate = hasUpdate,
                    isObsolete = extension.findListing(available) == null,
                )
                is Extension.NotLoaded -> extension.copy(hasUpdate = hasUpdate)
            }
        }
    }
}
```

`Extension.Installed` es un `sealed interface` con solo `Loaded` y `NotLoaded`, así que el `when` es exhaustivo.

En `AppGraph.kt`, añadir:

```kotlin
    val mihonExtensionManager: ExtensionManager
    val sourcePreferences: SourcePreferences
    val getExtensionStoreCountAsFlow: GetExtensionStoreCountAsFlow
```

(imports `eu.kanade.tachiyomi.extension.ExtensionManager`, `eu.kanade.domain.source.service.SourcePreferences`, `mihon.domain.extension.interactor.GetExtensionStoreCountAsFlow`).

El código portado que use `context.appGraph.extensionManager` esperando la fachada (busca: `grep -rn "appGraph.extensionManager" desktop-app/src`) se cambia a `appGraph.mihonExtensionManager`, con `// desktop:`.

La fachada carga las extensiones instaladas y los listados de los repositorios al construirse. Metro la construye la primera vez que alguien la pide, y la pestaña Fuentes no la pide (lee `SourceManager`). Por eso `Main.kt` la pide al arrancar. Justo después de `DesktopImageLoader.install(graph)`:

```kotlin
    // Loads the installed extensions (their sources feed SourceManager) and the store listings at startup
    graph.mihonExtensionManager
```

- [ ] **Step 7: El harness mide compatibilidad, no la política de contenido**

Con el filtro por defecto, las extensiones 18+ del harness quedarían como `NotLoaded(Filtered)` y contarían como `LOAD_FAILED`. En `compat-harness/src/main/kotlin/mihon/desktop/harness/Main.kt`, justo después de `val graph = Platform.start(...)`:

```kotlin
    // The harness measures whether extensions run, so every content warning is allowed here
    ContentWarningPrefs.enabled(graph.preferenceStore).set(ContentWarning.entries.toSet())
```

(imports `mihon.desktop.core.ContentWarningPrefs` y `mihon.domain.extension.model.ContentWarning`).

- [ ] **Step 8: Ejecutar los tests y ver que pasan**

Run: `./gradlew :extension-loader:test :desktop-core:test :desktop-app:test :compat-harness:test`
Expected: PASS. Pasan el caso nuevo del loader, `ContentWarningFilterTest` (3) y `ExtensionManagerTest` (2), y los tests anteriores siguen en verde.

- [ ] **Step 9: Commit**

```bash
git add extension-loader desktop-core desktop-app compat-harness
git commit -m "feat(desktop): Mihon's ExtensionManager API and content warning filtering

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---
### Task 9: Explorar › Extensiones, repositorios y filtro

**Files:**
- Create (portados desde `app/src/main/java/`, mismo paquete, en `desktop-app/src/main/kotlin/`):
  - `eu/kanade/domain/base/BasePreferences.kt`
  - `eu/kanade/domain/extension/interactor/GetExtensionLanguages.kt`
  - `eu/kanade/domain/extension/interactor/GetExtensionSources.kt`
  - `eu/kanade/domain/extension/interactor/GetExtensionsByType.kt`
  - `eu/kanade/domain/extension/model/Extensions.kt`
  - `eu/kanade/domain/source/interactor/ToggleIncognito.kt`
  - `eu/kanade/domain/source/interactor/ToggleLanguage.kt`
  - `eu/kanade/domain/source/interactor/ToggleSource.kt`
  - `eu/kanade/domain/source/interactor/GetIncognitoState.kt`
  - `eu/kanade/presentation/browse/ExtensionDetailsScreen.kt`
  - `eu/kanade/presentation/browse/ExtensionFilterScreen.kt`
  - `eu/kanade/presentation/browse/ExtensionsScreen.kt`
  - `eu/kanade/presentation/browse/components/BaseBrowseItem.kt`
  - `eu/kanade/presentation/browse/components/BaseSourceItem.kt`
  - `eu/kanade/presentation/browse/components/BrowseIcons.kt`
  - `eu/kanade/presentation/browse/components/ContentWarningLabel.kt`
  - `eu/kanade/presentation/browse/components/ExtensionPill.kt`
  - `eu/kanade/presentation/components/Banners.kt`
  - `eu/kanade/presentation/category/components/CategoryFloatingActionButton.kt`
  - `eu/kanade/presentation/more/settings/Preference.kt`
  - `eu/kanade/presentation/more/settings/PreferenceItem.kt`
  - `eu/kanade/presentation/more/settings/widget/BasePreferenceWidget.kt`
  - `eu/kanade/presentation/more/settings/widget/EditTextPreferenceWidget.kt`
  - `eu/kanade/presentation/more/settings/widget/InfoWidget.kt`
  - `eu/kanade/presentation/more/settings/widget/ListPreferenceWidget.kt`
  - `eu/kanade/presentation/more/settings/widget/MultiSelectListPreferenceWidget.kt`
  - `eu/kanade/presentation/more/settings/widget/SwitchPreferenceWidget.kt`
  - `eu/kanade/presentation/more/settings/widget/TextPreferenceWidget.kt`
  - `eu/kanade/presentation/more/settings/widget/TriStateListDialog.kt`
  - `eu/kanade/presentation/more/settings/screen/browse/ExtensionStoresScreen.kt`
  - `eu/kanade/presentation/more/settings/screen/browse/ExtensionStoresViewModel.kt`
  - `eu/kanade/presentation/more/settings/screen/browse/components/ExtensionStoresContent.kt`
  - `eu/kanade/presentation/more/settings/screen/browse/components/ExtensionStoresDialogs.kt`
  - `eu/kanade/presentation/more/settings/screen/browse/components/ExtensionStoresScreen.kt`
  - `eu/kanade/tachiyomi/ui/browse/extension/ExtensionFilterScreen.kt`
  - `eu/kanade/tachiyomi/ui/browse/extension/ExtensionFilterViewModel.kt`
  - `eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt`
  - `eu/kanade/tachiyomi/ui/browse/extension/ExtensionsViewModel.kt`
  - `eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreen.kt`
  - `eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsViewModel.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/main/BrowseTab.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/HomeScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt` (accesor `basePreferences`)
- Create: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/Strings.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/main/ExploreEmptyStateTest.kt`

**Interfaces:**
- Consumes:
  - `ExtensionManager`, la fachada (Task 8), por `InstallStep`, `SourcePreferences` y `AppGraph`.
  - `ProvideScreenViewModelStore`, `AppEvents` y `HomeScreen.tabs` (Task 5).
  - `InstalledExtensionIcon` y `InstalledSourceIcon` (Task 7).
  - Los shims de la Task 4.
- Produces:
  - `object BrowseTab : Tab`, con las pestañas Fuentes y Extensiones. La Task 11 añade Fuentes.
  - `ExtensionDetailsScreen(pkgName)`, que llama a `SourcePreferencesScreen(sourceId)` (Task 10).
  - `ExtensionStoresScreen()`.
  - El modelo de ajustes `Preference` / `PreferenceItem` de Mihon, sin `TrackerPreference`.
  - En los tests: `fun str(res: StringResource, vararg args: Any): String`.

**Criterio de recorte de esta tarea:**
- Los ViewModels usan la fachada `eu.kanade.tachiyomi.extension.ExtensionManager`, que se llama igual que la clase de Mihon, así que sus imports no cambian.
- Lo de Android sin equivalente útil en escritorio se borra: el permiso de instalar paquetes, "Información de la app" y el selector de instalador.
- El WebView pasa a ser abrir el navegador del sistema.

- [ ] **Step 1: Escribir el test (falla: no existe `BrowseTab`)**

`desktop-app/src/test/kotlin/mihon/desktop/app/testing/Strings.kt`:

```kotlin
package mihon.desktop.app.testing

import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc

/** The text the UI shows for [res] in this JVM's locale, so tests don't depend on the machine's language. */
fun str(res: StringResource, vararg args: Any): String =
    if (args.isEmpty()) StringDesc.Resource(res).localized() else StringDesc.ResourceFormatted(res, *args).localized()
```

`desktop-app/src/test/kotlin/mihon/desktop/app/main/ExploreEmptyStateTest.kt`:

```kotlin
package mihon.desktop.app.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class ExploreEmptyStateTest {
    @Test
    fun `with no extension stores the extensions tab points to adding one`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_extensions)).performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(androidx.compose.ui.test.hasText(str(MR.strings.extensionStores))).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(str(MR.strings.extensionStores)).performClick()

        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(androidx.compose.ui.test.hasText(str(MR.strings.extensionStoresScreen_emptyLabel)))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
```

(moko expone `extensionStoresScreen.emptyLabel` como `extensionStoresScreen_emptyLabel`).

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.main.ExploreEmptyStateTest"`
Expected: FAIL. El test no encuentra el texto de la pestaña Extensiones: la pestaña de Explorar es todavía la provisional.

- [ ] **Step 3: Copiar los archivos portados y aplicar los cambios**

Copiar todos los archivos de la lista. Cambios por archivo (cada uno con `// desktop:`):

- `BasePreferences.kt`:
  - Borrar la propiedad `extensionInstaller`, el `enum class ExtensionInstaller` y sus imports.
  - Mantener el parámetro `context` (lo inyecta Metro).
- `ExtensionsViewModel.kt`:
  - Borrar el parámetro `basePreferences` y el campo `installer` de `State`.
  - En el `combine` de `state`, borrar `basePreferences.extensionInstaller.changes()` y el argumento `installer`.
- `ExtensionsScreen.kt`:
  - Borrar `rememberRequestPackageInstallsPermissionState`, la variable `installGranted` y el `item` del `WarningBanner` de permisos.
  - Borrar los imports de `launchRequestPackageInstallsPermission` y `rememberRequestPackageInstallsPermissionState`.
  - El parámetro `onOpenWebView` se mantiene: `ExtensionsTab` le pasa la apertura en el navegador.
- `ExtensionsTab.kt`:
  - `onOpenWebView` abre `it.baseUrl` con `context.openInBrowser(it.baseUrl)` en lugar de `navigator.push(WebViewScreen(...))`.
  - Borrar el import de `WebViewScreen`.
  - Añadir `import eu.kanade.tachiyomi.util.system.openInBrowser`.
- `ExtensionDetailsScreen.kt` (presentation):
  - Borrar la acción "Información de la app" (el bloque que crea `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)`) y los imports `android.content.Intent`, `android.net.Uri` y `android.provider.Settings`.
  - Cambiar `DisplayMetrics.DENSITY_XXXHIGH` por `640` y borrar `import android.util.DisplayMetrics`.
- `BrowseIcons.kt`:
  - Cambiar `DisplayMetrics.DENSITY_DEFAULT` por `160` y borrar su import.
  - Los recursos de Android pasan a iconos vectoriales de `icons`:
    - `painterResource(R.mipmap.ic_local_source)` → `rememberVectorPainter(MaterialSymbols.RoundedFilled.Folder)`.
    - `painterResource(R.mipmap.ic_default_source)` → `rememberVectorPainter(MaterialSymbols.RoundedFilled.Extension)`.
    - `rememberResourceBitmapPainter(id = R.drawable.cover_error)` → `rememberVectorPainter(MaterialSymbols.Rounded.Image)`.
  - Borrar los imports de `R`, `painterResource` y `rememberResourceBitmapPainter`.
- `Preference.kt`: borrar `data class TrackerPreference` y `import eu.kanade.tachiyomi.data.track.Tracker`.
- `PreferenceItem.kt`: borrar la rama `is Preference.PreferenceItem.TrackerPreference -> { ... }` y sus imports.
- `ExtensionDetailsViewModel.kt`, `ExtensionFilterViewModel.kt`, `GetExtensionsByType.kt`, `GetExtensionLanguages.kt` y `GetIncognitoState.kt`: sin cambios. Usan la fachada `ExtensionManager`, que tiene el mismo nombre.
- Los archivos de repositorios (`ExtensionStores*`) y `CategoryFloatingActionButton.kt`: sin cambios. Usan los shims `copyToClipboard` y `openInBrowser`.
- Los demás: sin cambios.

Si al compilar aparece una referencia a un archivo de Mihon que no está en la lista, aplicar este criterio y anotar la decisión:
- Si es un componente de Compose sin Android, copiarlo tal cual.
- Si pertenece a descargas, trackers, categorías, migración o WebView, borrar su uso.

En `AppGraph.kt`, añadir `val basePreferences: BasePreferences`.

- [ ] **Step 4: `BrowseTab` de escritorio y su entrada en el rail**

`desktop-app/src/main/kotlin/mihon/desktop/app/main/BrowseTab.kt`:

```kotlin
package mihon.desktop.app.main

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Explore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Mihon's BrowseTab without migration (subproject 3) and with a static rail icon. */
data object BrowseTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = stringResource(MR.strings.browse),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Explore),
        )

    @Composable
    override fun Content() {
        // Hoisted for the extensions tab's search bar
        val extensionsViewModel = metroViewModel<ExtensionsViewModel>()
        val extensionsSearchQuery by extensionsViewModel.searchQuery.collectAsStateWithLifecycle()

        val tabs = listOf(
            extensionsTab(extensionsViewModel),
        )
        val state = rememberPagerState { tabs.size }

        LaunchedEffect(Unit) { AppEvents.refresh.collect { extensionsViewModel.findAvailableExtensions() } }

        TabbedScreen(
            titleRes = MR.strings.browse,
            tabs = tabs,
            state = state,
            searchQuery = extensionsSearchQuery,
            onChangeSearchQuery = extensionsViewModel::search,
        )
    }
}
```

En `HomeScreen.kt`:
- Cambiar `val tabs: List<Tab> = listOf(ExploreTabPlaceholder)` por `listOf(BrowseTab)`.
- Borrar `ExploreTabPlaceholder` y los imports que solo usaba.

Si `TabbedScreen` no expone una pestaña cuando solo hay una (`tabs.size == 1`), el test fallará al no encontrar el texto "Extensiones". En ese caso, el test hace clic directamente en el botón del estado vacío, sin el clic en la pestaña. Anotar el ruling. La Task 11 añade la segunda pestaña.

- [ ] **Step 5: Ejecutar el test y ver que pasa**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.main.ExploreEmptyStateTest"`
Expected: PASS.

- [ ] **Step 6: Comprobación manual con un repositorio real**

1. Run: `./gradlew :desktop-app:run`.
2. Explorar › Extensiones › "Repositorios de extensiones" › añadir `https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb`.
3. Volver a Extensiones.

Expected:
- Aparece la lista de disponibles, agrupada por idioma, con sus iconos.
- No aparece ninguna con la etiqueta 18+, porque NSFW está desactivado por defecto.
- Instalar MangaDex: muestra "Descargando" y termina en Instaladas con su icono.
- Abrir sus detalles: aparecen las fuentes por idioma.
- F5 refresca la lista.

- [ ] **Step 7: Ejecutar todos los tests del módulo**

Run: `./gradlew :desktop-app:test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): browse tab with Mihon's extensions screens and stores

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Ajustes de cada fuente en Compose

El `SourcePreferencesScreen` de Mihon es un `PreferenceFragmentCompat` (Views de Android). Se reescribe en Compose sobre los modelos `androidx.preference` de AndroidCompat. Es el mismo enfoque que Suwayomi: el `PreferenceScreen` guarda las preferencias que la extensión añade, cada una lee su valor de los `SharedPreferences` de la fuente y lo guarda solo si el `OnPreferenceChangeListener` de la extensión lo acepta.

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/source/SourcePreferenceModel.kt`
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/browse/extension/details/SourcePreferencesScreen.kt` (reescrito, mismo nombre de clase que Mihon)
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/source/SourcePreferenceModelTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/source/SourcePreferencesScreenTest.kt`

**Interfaces:**
- Consumes: `ConfigurableSource`, `sourcePreferences()` (`source-api`); `androidx.preference.*` (AndroidCompat); los widgets de Mihon (Task 9); `AppBar`.
- Produces:
  - `object SourcePreferenceModel`, con:
    - `fun load(source: ConfigurableSource, context: Context): List<Preference>`.
    - `fun set(pref: Preference, value: Any): Boolean`.
    - `fun summaryOf(pref: Preference): String?`.
  - `class SourcePreferencesScreen(sourceId: Long) : Screen()`.

- [ ] **Step 1: Escribir los tests (fallan)**

`desktop-app/src/test/kotlin/mihon/desktop/app/source/SourcePreferenceModelTest.kt`:

```kotlin
package mihon.desktop.app.source

import android.content.Context
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.sourcePreferences
import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A source with one preference of each kind extensions use, like a real extension's setupPreferenceScreen.
 * Each test passes its own [id]: the preferences persist per source id for the whole JVM.
 */
class ConfigurableFakeSource(override val id: Long) : ConfigurableSource {
    override val name = "Configurable"
    override val lang = "es"
    override val supportsLatest = false
    var lastQuality: Any? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(
            ListPreference(screen.context).apply {
                key = "quality"
                title = "Calidad"
                summary = "%s"
                entries = arrayOf<CharSequence>("Alta", "Baja")
                entryValues = arrayOf<CharSequence>("high", "low")
                setDefaultValue("high")
                setOnPreferenceChangeListener { _, new ->
                    lastQuality = new
                    true
                }
            },
        )
        screen.addPreference(
            SwitchPreferenceCompat(screen.context).apply {
                key = "split"
                title = "Dividir páginas"
                setDefaultValue(false)
            },
        )
        screen.addPreference(
            CheckBoxPreference(screen.context).apply {
                key = "locked"
                title = "No se puede cambiar"
                setDefaultValue(false)
                setOnPreferenceChangeListener { _, _ -> false }
            },
        )
        screen.addPreference(
            EditTextPreference(screen.context).apply {
                key = "domain"
                title = "Dominio"
                setDefaultValue("ejemplo.com")
            },
        )
        screen.addPreference(
            MultiSelectListPreference(screen.context).apply {
                key = "langs"
                title = "Idiomas"
                entries = arrayOf<CharSequence>("Español", "English")
                entryValues = arrayOf<CharSequence>("es", "en")
                setDefaultValue(setOf("es"))
            },
        )
    }

    override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
    override suspend fun getLatestUpdates(page: Int) = MangasPage(emptyList(), false)
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) = MangasPage(emptyList(), false)
    override suspend fun getMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean) =
        SMangaUpdate(manga, chapters)
    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourcePreferenceModelTest {
    @BeforeAll
    fun start() {
        startTestApp()
    }

    private fun prefs(source: ConfigurableFakeSource) =
        SourcePreferenceModel.load(source, Injekt.get<Context>()).associateBy { it.key }

    @Test
    fun `every kind of preference is read with its default`() {
        val prefs = prefs(ConfigurableFakeSource(id = 77))
        prefs.keys shouldBe setOf("quality", "split", "locked", "domain", "langs")
        prefs.getValue("quality").currentValue shouldBe "high"
        prefs.getValue("split").currentValue shouldBe false
        prefs.getValue("domain").currentValue shouldBe "ejemplo.com"
        prefs.getValue("langs").currentValue shouldBe setOf("es")
    }

    @Test
    fun `an accepted change is saved where the extension reads it and the listener sees it`() {
        val source = ConfigurableFakeSource(id = 78)
        SourcePreferenceModel.set(prefs(source).getValue("quality"), "low") shouldBe true
        source.sourcePreferences().getString("quality", null) shouldBe "low"
        source.lastQuality shouldBe "low"
        SourcePreferenceModel.summaryOf(prefs(source).getValue("quality")) shouldBe "Baja"
    }

    @Test
    fun `a change the extension's listener rejects is not saved`() {
        val source = ConfigurableFakeSource(id = 79)
        SourcePreferenceModel.set(prefs(source).getValue("locked"), true) shouldBe false
        source.sourcePreferences().getBoolean("locked", false) shouldBe false
    }
}
```

`desktop-app/src/test/kotlin/mihon/desktop/app/source/SourcePreferencesScreenTest.kt`:

```kotlin
package mihon.desktop.app.source

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import eu.kanade.tachiyomi.source.sourcePreferences
import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class SourcePreferencesScreenTest {
    @Test
    fun `each preference is drawn and a switch saves its value`() = runComposeUiTest {
        val graph = startTestApp()
        val source = ConfigurableFakeSource(id = 80)
        setContent {
            CompositionLocalProvider(LocalContext provides graph.context) {
                SourcePreferencesContent(source = source, navigateUp = {})
            }
        }
        waitUntil(5_000) { onAllNodes(hasText("Dividir páginas")).fetchSemanticsNodes().isNotEmpty() }
        listOf("Calidad", "Alta", "Dominio", "Idiomas", "No se puede cambiar").forEach {
            onNodeWithText(it, substring = true).assertExists()
        }

        onNodeWithText("Dividir páginas").performClick()
        waitForIdle()

        source.sourcePreferences().getBoolean("split", false) shouldBe true
    }
}
```

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.source.*"`
Expected: FAIL de compilación: `Unresolved reference 'SourcePreferenceModel'` y `'SourcePreferencesContent'`.

- [ ] **Step 3: `SourcePreferenceModel`**

```kotlin
package mihon.desktop.app.source

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.sourcePreferences

/**
 * An extension's settings, built as Suwayomi builds them: AndroidCompat's androidx.preference models, each one
 * reading and writing the source's own SharedPreferences, where the extension reads them back.
 */
object SourcePreferenceModel {
    fun load(source: ConfigurableSource, context: Context): List<Preference> {
        val screen = PreferenceScreen(context)
        // Set before setup: addPreference hands the screen's SharedPreferences to each preference
        screen.sharedPreferences = source.sourcePreferences()
        source.setupPreferenceScreen(screen)
        return screen.preferences
    }

    /** Saves [value] only when the extension's change listener accepts it, like androidx.preference does. */
    fun set(pref: Preference, value: Any): Boolean {
        if (!pref.callChangeListener(value)) return false
        pref.saveNewValue(value)
        return true
    }

    /** The summary, with a list's "%s" replaced by the chosen entry as on Android. */
    fun summaryOf(pref: Preference): String? {
        val summary = pref.summary?.toString() ?: return null
        if (pref !is ListPreference || "%s" !in summary) return summary
        val index = pref.findIndexOfValue(pref.currentValue as? String)
        val entry = pref.entries?.getOrNull(index)?.toString().orEmpty()
        return summary.replace("%s", entry)
    }
}
```

Si `ListPreference.findIndexOfValue(String)` de AndroidCompat no acepta `null`, usar `pref.entryValues.indexOfFirst { it.toString() == pref.currentValue }`.

- [ ] **Step 4: `SourcePreferencesScreen` en Compose**

`desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/browse/extension/details/SourcePreferencesScreen.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.browse.extension.details

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.MultiSelectListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.ConfigurableSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import mihon.desktop.app.source.SourcePreferenceModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

// desktop: Mihon's version hosts a PreferenceFragmentCompat (Android Views); this one draws the same preferences
class SourcePreferencesScreen(val sourceId: Long) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val source by produceState<ConfigurableSource?>(null) {
            value = context.appGraph.sourceManager.get(sourceId) as? ConfigurableSource
        }
        when (val current = source) {
            null -> EmptyScreen(MR.strings.empty_screen)
            else -> SourcePreferencesContent(current, navigator::pop)
        }
    }
}

@Composable
fun SourcePreferencesContent(source: ConfigurableSource, navigateUp: () -> Unit) {
    val context = LocalContext.current
    // Extensions may change other preferences from a listener, so every change reloads the whole screen
    var version by remember { mutableIntStateOf(0) }
    val preferences by produceState<List<Preference>?>(null, version) {
        value = withContext(Dispatchers.IO) { SourcePreferenceModel.load(source, context) }
    }
    val onChange: (Preference, Any) -> Unit = { pref, value ->
        if (SourcePreferenceModel.set(pref, value)) version++
    }

    Scaffold(
        topBar = { AppBar(title = source.name, navigateUp = navigateUp, scrollBehavior = it) },
    ) { padding ->
        val list = preferences
        if (list == null) {
            LoadingScreen()
            return@Scaffold
        }
        LazyColumn(contentPadding = padding) {
            items(list.filter { it.visible }, key = { it.key ?: it.title.toString() }) { pref ->
                key(version) { SourcePreference(pref, onChange) }
            }
        }
    }
}

@Composable
private fun SourcePreference(pref: Preference, onChange: (Preference, Any) -> Unit) {
    val title = pref.title?.toString().orEmpty()
    val summary = SourcePreferenceModel.summaryOf(pref)
    when (pref) {
        is TwoStatePreference -> SwitchPreferenceWidget(
            title = title,
            subtitle = summary,
            checked = pref.currentValue as Boolean,
            onCheckedChanged = { onChange(pref, it) },
        )
        is ListPreference -> ListPreferenceWidget(
            value = pref.currentValue as String,
            title = title,
            subtitle = summary,
            icon = null,
            entries = pref.entryValues.map { it.toString() }.zip(pref.entries.map { it.toString() }).toMap(),
            onValueChange = { onChange(pref, it) },
        )
        is MultiSelectListPreference -> @Suppress("UNCHECKED_CAST") MultiSelectListPreferenceWidget(
            values = pref.currentValue as Set<String>,
            title = title,
            subtitle = summary,
            icon = null,
            entries = pref.entryValues.map { it.toString() }.zip(pref.entries.map { it.toString() }).toMap(),
            onValuesChange = { onChange(pref, it) },
        )
        is EditTextPreference -> EditTextPreferenceWidget(
            title = title,
            subtitle = summary,
            icon = null,
            value = pref.currentValue as String,
            onConfirm = {
                onChange(pref, it)
                true
            },
        )
        else -> TextPreferenceWidget(title = title, subtitle = summary)
    }
}
```

`TwoStatePreference` cubre `SwitchPreferenceCompat` y `CheckBoxPreference`, así que el import de `CheckBoxPreference` sobra: borrarlo. La clase `Preference` de AndroidCompat expone `getVisible()`, que en Kotlin es `visible`. Si el compilador pide `getVisible()`, usarlo así.

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.source.*"`
Expected: PASS (3 + 1 tests).

- [ ] **Step 6: Comprobación manual**

Con MangaDex instalado (Task 9): Explorar › Extensiones › MangaDex › icono de ajustes de una fuente.
Expected: aparecen sus ajustes (calidad de imagen, idiomas, etc.). Cambiar uno, salir y volver: el valor se mantiene.

- [ ] **Step 7: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): source settings drawn in Compose from the extension's preference screen

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---
### Task 11: Fuentes, navegar una fuente, búsqueda global y detalles del manga

Estas pantallas se referencian entre sí: la fuente abre el manga, y el manga busca por género en la fuente o en la búsqueda global. Por eso se portan juntas.

**Files:**
- Create (portados desde `app/src/main/java/`, mismo paquete, en `desktop-app/src/main/kotlin/`):
  - Núcleo y dominio:
    - `eu/kanade/core/preference/PreferenceMutableState.kt`
    - `eu/kanade/core/util/CollectionUtils.kt`
    - `eu/kanade/domain/chapter/interactor/GetAvailableScanlators.kt`
    - `eu/kanade/domain/chapter/interactor/SetReadStatus.kt`
    - `eu/kanade/domain/chapter/interactor/SyncChaptersWithSource.kt`
    - `eu/kanade/domain/chapter/model/Chapter.kt`
    - `eu/kanade/domain/chapter/model/ChapterFilter.kt`
    - `eu/kanade/domain/manga/interactor/GetExcludedScanlators.kt`
    - `eu/kanade/domain/manga/interactor/SetExcludedScanlators.kt`
    - `eu/kanade/domain/manga/interactor/UpdateManga.kt`
    - `eu/kanade/domain/source/interactor/GetEnabledSources.kt`
    - `eu/kanade/domain/source/interactor/GetLanguagesWithSources.kt`
    - `eu/kanade/domain/source/interactor/ToggleSourcePin.kt`
    - `eu/kanade/tachiyomi/source/SourceExtensions.kt`
    - `eu/kanade/tachiyomi/util/MangaExtensions.kt`
    - `eu/kanade/tachiyomi/util/chapter/ChapterGetNextUnread.kt`
    - `eu/kanade/tachiyomi/util/lang/DateExtensions.kt`
    - `mihon/domain/source/interactor/UpdateMangaFromRemote.kt`
    - `mihon/domain/source/models/RemoteMangaUpdate.kt`
  - Presentación:
    - `eu/kanade/presentation/browse/BrowseSourceScreen.kt`
    - `eu/kanade/presentation/browse/GlobalSearchScreen.kt`
    - `eu/kanade/presentation/browse/SourcesFilterScreen.kt`
    - `eu/kanade/presentation/browse/SourcesScreen.kt`
    - `eu/kanade/presentation/browse/components/BrowseBadges.kt`
    - `eu/kanade/presentation/browse/components/BrowseSourceComfortableGrid.kt`
    - `eu/kanade/presentation/browse/components/BrowseSourceCompactGrid.kt`
    - `eu/kanade/presentation/browse/components/BrowseSourceDialogs.kt`
    - `eu/kanade/presentation/browse/components/BrowseSourceList.kt`
    - `eu/kanade/presentation/browse/components/BrowseSourceLoadingItem.kt`
    - `eu/kanade/presentation/browse/components/BrowseSourceToolbar.kt`
    - `eu/kanade/presentation/browse/components/GlobalSearchCardRow.kt`
    - `eu/kanade/presentation/browse/components/GlobalSearchResultItems.kt`
    - `eu/kanade/presentation/browse/components/GlobalSearchToolbar.kt`
    - `eu/kanade/presentation/components/AdaptiveSheet.kt`
    - `eu/kanade/presentation/components/DateText.kt`
    - `eu/kanade/presentation/components/DownloadDropdownMenu.kt`
    - `eu/kanade/presentation/components/TabbedDialog.kt`
    - `eu/kanade/presentation/library/components/CommonMangaItem.kt`
    - `eu/kanade/presentation/manga/ChapterSettingsDialog.kt`
    - `eu/kanade/presentation/manga/DuplicateMangaDialog.kt`
    - `eu/kanade/presentation/manga/MangaScreen.kt`
    - `eu/kanade/presentation/manga/MangaScreenConstants.kt`
    - `eu/kanade/presentation/manga/components/`: `ChapterDownloadIndicator.kt`, `ChapterHeader.kt`, `DotSeparatorText.kt`, `MangaBottomActionMenu.kt`, `MangaChapterListItem.kt`, `MangaCover.kt`, `MangaCoverDialog.kt`, `MangaDialogs.kt`, `MangaInfoHeader.kt`, `MangaNotesDisplay.kt`, `MangaNotesSection.kt`, `MangaToolbar.kt`, `MarkdownRender.kt`, `MissingChapterCountListItem.kt` y `ScanlatorFilterDialog.kt`
    - `eu/kanade/presentation/util/ChapterNumberFormatter.kt`
    - `eu/kanade/presentation/util/ExceptionFormatter.kt`
    - `eu/kanade/presentation/util/WindowSize.kt`
  - Pantallas y ViewModels:
    - `eu/kanade/tachiyomi/ui/browse/source/SourcesFilterScreen.kt`
    - `eu/kanade/tachiyomi/ui/browse/source/SourcesFilterViewModel.kt`
    - `eu/kanade/tachiyomi/ui/browse/source/SourcesTab.kt`
    - `eu/kanade/tachiyomi/ui/browse/source/SourcesViewModel.kt`
    - `eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreen.kt`
    - `eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceViewModel.kt`
    - `eu/kanade/tachiyomi/ui/browse/source/browse/SourceFilterDialog.kt`
    - `eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreen.kt`
    - `eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchViewModel.kt`
    - `eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchViewModel.kt`
    - `eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`
    - `eu/kanade/tachiyomi/ui/manga/MangaViewModel.kt`
- Create: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/manga/MangaCoverViewModel.kt` (reescrito, mismo nombre y `Factory`)
- Create: `desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt` (solo `skipFiltered`; el plan 2 lo sustituye por el port completo)
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/FileDialogs.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/PointerModifiers.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderLauncher.kt` (provisional; el plan 2 lo completa)
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/BrowseTab.kt` (añade Fuentes)
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/HomeScreen.kt` (Ctrl+F abre la búsqueda global)
- Modify: `desktop-app/build.gradle.kts` (`libs.bundles.markdown`, `libs.swipe`, `libs.composeGrid`, `libs.androidx.paging.compose`)
- Modify: `i18n/src/commonMain/moko-resources/base/strings.xml` y `.../es/strings.xml`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/testing/TestAppGraph.kt` (dos accesores para el test)
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/browse/BrowseAndMangaTest.kt`

**Interfaces:**
- Consumes:
  - `AppProviders`, `ProvideScreenViewModelStore` y `AppEvents` (Task 5).
  - `TestSources.fake` y `FakeSource.failNext` (Task 2).
  - Los stubs de descargas y trackers (Task 4) y la fachada `ExtensionManager` (Task 8).
- Produces:
  - `BrowseSourceScreen(sourceId: Long, listingQuery: String?)`, `GlobalSearchScreen(searchQuery: String = "")` y `MangaScreen(mangaId: Long, fromSource: Boolean = false)`: los constructores de Mihon.
  - `object ReaderLauncher { fun open(context: Context, mangaId: Long, chapterId: Long?) }`. En este plan muestra un aviso; el plan 2 lo reemplaza.
  - `fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier`.
  - `object FileDialogs { fun saveFile(title: String, defaultName: String): File?; fun openImage(title: String): File? }`.

- [ ] **Step 1: Escribir el test (falla: no existen `BrowseSourceScreen` ni `AppProviders`)**

`desktop-app/src/test/kotlin/mihon/desktop/app/browse/BrowseAndMangaTest.kt`:

```kotlin
package mihon.desktop.app.browse

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import java.io.IOException

@OptIn(ExperimentalTestApi::class)
class BrowseAndMangaTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `browsing a source, opening a manga saves it with its chapters, and a chapter is marked read`() =
        runComposeUiTest {
            val graph = startTestApp()
            setContent {
                AppProviders(graph) {
                    Navigator(BrowseSourceScreen(TestSources.fake.id, null)) { DefaultNavigatorScreenTransition(it) }
                }
            }

            waitForText("Manga 1")
            onNodeWithText("Manga 1").performClick()
            waitForText("Chapter 5")
            waitForText("Description of Manga 1")

            val manga = runBlocking { graph.getMangaByUrlAndSourceId.await("/manga/1", TestSources.fake.id) }!!
            runBlocking { graph.getChaptersByMangaId.await(manga.id) }.size shouldBe 5

            onNodeWithText("Chapter 3").performMouseInput { rightClick() }
            onNodeWithText(str(MR.strings.action_mark_as_read)).performClick()
            waitUntil(10_000) {
                runBlocking { graph.getChaptersByMangaId.await(manga.id) }.single { it.name == "Chapter 3" }.read
            }
        }

    @Test
    fun `a source that fails to load shows the error with retry instead of crashing`() = runComposeUiTest {
        val graph = startTestApp()
        TestSources.fake.failNext = IOException("Sin conexión con José Pérez")
        setContent {
            AppProviders(graph) {
                Navigator(BrowseSourceScreen(TestSources.fake.id, null)) { DefaultNavigatorScreenTransition(it) }
            }
        }

        waitForText("Sin conexión con José Pérez")
        onNodeWithText(str(MR.strings.action_retry)).performClick()
        waitForText("Manga 1")
    }
}
```

En `TestAppGraph`, añadir los accesores que usa el test:

```kotlin
    val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId
    val getChaptersByMangaId: GetChaptersByMangaId
```

(imports `tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId` y `tachiyomi.domain.chapter.interactor.GetChaptersByMangaId`; sus firmas son `await(url: String, sourceId: Long): Manga?` y `await(mangaId: Long, applyScanlatorFilter: Boolean = false): List<Chapter>`).

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.browse.BrowseAndMangaTest"`
Expected: FAIL de compilación: `Unresolved reference 'BrowseSourceScreen'`.

- [ ] **Step 3: Piezas nuevas de escritorio**

`desktop-app/src/main/kotlin/mihon/desktop/app/platform/PointerModifiers.kt`:

```kotlin
package mihon.desktop.app.platform

import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton

/** Right click does what a long press does on Android (select, add to library). */
fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier =
    this.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onClick)
```

`desktop-app/src/main/kotlin/mihon/desktop/app/platform/FileDialogs.kt`:

```kotlin
package mihon.desktop.app.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The native Windows file dialogs. */
object FileDialogs {
    fun saveFile(title: String, defaultName: String): File? = show(title, FileDialog.SAVE) { file = defaultName }

    fun openImage(title: String): File? = show(title, FileDialog.LOAD) {
        setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS }
    }

    private fun show(title: String, mode: Int, setup: FileDialog.() -> Unit): File? {
        val dialog = FileDialog(null as Frame?, title, mode).apply(setup)
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
}
```

`desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderLauncher.kt`:

```kotlin
package mihon.desktop.app.reader

import android.content.Context
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR

/** Opens the reader. ponytail: only a notice until the reader exists (subproject 2a, plan 2). */
object ReaderLauncher {
    @Suppress("UNUSED_PARAMETER")
    fun open(context: Context, mangaId: Long, chapterId: Long?) {
        context.toast(MR.strings.desktop_reader_coming)
    }
}
```

`desktop-app/src/stubs/kotlin/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`:

```kotlin
// Null implementation until subproject 2a plan 2 ports the reader; same key and default as Mihon.
package eu.kanade.tachiyomi.ui.reader.setting

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class ReaderPreferences(preferenceStore: PreferenceStore) {
    val skipFiltered: Preference<Boolean> = preferenceStore.getBoolean("skip_filtered", true)
}
```

Cadenas nuevas (bloque `<!-- Desktop -->` de `base/strings.xml` y `es/strings.xml`):

```xml
    <string name="desktop_reader_coming">The reader arrives in the next version</string>
    <string name="desktop_site_needs_browser">This site needs a browser to load (coming soon)</string>
```

```xml
    <string name="desktop_reader_coming">El lector llega en la próxima versión</string>
    <string name="desktop_site_needs_browser">Este sitio requiere navegador (disponible pronto)</string>
```

- [ ] **Step 4: Copiar los archivos portados y aplicar los cambios**

En `desktop-app/build.gradle.kts`, añadir:

```kotlin
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.compose)
    implementation(libs.bundles.markdown)
    implementation(libs.swipe)
    implementation(libs.composeGrid)
```

Copiar todos los archivos de la lista. Cambios por archivo (cada uno con `// desktop:`):

- `SourceExtensions.kt`: sin cambios. `Injekt.get<Context>().appGraph` funciona con el shim.
- `DateExtensions.kt`: sin cambios. `Context` existe en los stubs y `stringResource(Context, …)` está en `core:common`.
- `WindowSize.kt`: `isTabletUi()` pasa a depender del ancho de la ventana (dos paneles a partir de 840 dp, el "expanded" de Material):

```kotlin
// desktop: Android reads the device configuration; on desktop the window width decides (two panes from 840dp)
@Composable
fun isTabletUi(): Boolean {
    val info = LocalWindowInfo.current
    return with(LocalDensity.current) { info.containerSize.width.toDp() } >= 840.dp
}
```

  Borrar `@ReadOnlyComposable`, `LocalConfiguration` y el import de `eu.kanade.tachiyomi.util.system.isTabletUi`. Imports: `androidx.compose.ui.platform.LocalWindowInfo`, `LocalDensity`, `androidx.compose.ui.unit.dp`.
- `ExceptionFormatter.kt`:
  - Si la excepción es `HttpException` con código 403 o 503, devolver `context.stringResource(MR.strings.desktop_site_needs_browser)`.
  - Añadir esa rama antes de la de "sin conexión". Es el mensaje que la sección 5 del spec pide para Cloudflare.
- `CommonMangaItem.kt`: en las dos cadenas de modificadores con `.combinedClickable(onClick = onClick, onLongClick = onLongClick)` (líneas ~302 y ~346), añadir a continuación `.onSecondaryClick(onLongClick)`.
- `BaseBrowseItem.kt` (ya portado en la Task 9): igual, `.onSecondaryClick(onLongClick)` tras su `combinedClickable`.
- `MangaChapterListItem.kt`: sin cambios.
- `MangaScreen.kt` (presentation):
  - Hacer nulos los parámetros `onTrackingClicked: (() -> Unit)?` y `onEditNotesClicked: (() -> Unit)?` en las tres funciones que los declaran, y propagarlo a `MangaInfoHeader.kt` y `MangaToolbar.kt`. Donde se dibuja el botón de seguimiento (`MangaActionButton` con `onClick = onTrackingClicked`), envolverlo en `if (onTrackingClicked != null)`. La sección de notas (`MangaNotesSection`) y la opción de menú "Editar notas" se dibujan solo si `onEditNotesClicked != null`.
  - En `sharedChapterItems`, tres cambios:
    - Añadir los parámetros `onMultiMarkAsReadClicked: (List<Chapter>, Boolean) -> Unit`, `onMultiBookmarkClicked: (List<Chapter>, Boolean) -> Unit` y `onMarkPreviousAsReadClicked: (Chapter) -> Unit`, y pasarlos desde los dos sitios que lo llaman.
    - Envolver `MangaChapterListItem(...)` en un menú contextual:

```kotlin
                // desktop: right click opens what Android's swipe and long-press selection offer
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem(
                                if (item.chapter.read) labelUnread else labelRead,
                            ) { onMultiMarkAsReadClicked(listOf(item.chapter), !item.chapter.read) },
                            ContextMenuItem(
                                if (item.chapter.bookmark) labelUnbookmark else labelBookmark,
                            ) { onMultiBookmarkClicked(listOf(item.chapter), !item.chapter.bookmark) },
                            ContextMenuItem(labelPreviousRead) { onMarkPreviousAsReadClicked(item.chapter) },
                        )
                    },
                ) {
                    MangaChapterListItem(
                        // the same arguments as in Mihon; only onClick changes (next point)
                    )
                }
```

  La llamada a `MangaChapterListItem` que ya existe se mueve dentro de `ContextMenuArea` tal cual. Solo cambia su `onClick`, como dice el punto siguiente.

      Antes del `when (item)`, en el mismo ámbito composable, leer las etiquetas con `stringResource`:
      - `labelRead` = `MR.strings.action_mark_as_read`
      - `labelUnread` = `MR.strings.action_mark_as_unread`
      - `labelBookmark` = `MR.strings.action_bookmark`
      - `labelUnbookmark` = `MR.strings.action_remove_bookmark`
      - `labelPreviousRead` = `MR.strings.action_mark_previous_as_read`

      Comprobar con `grep` el nombre exacto de cada cadena en `strings.xml` (por ejemplo, `action_mark_previous_as_read`) y usar el existente.
    - El `onClick` del capítulo respeta Ctrl y Shift, como la selección en un explorador de archivos:

```kotlin
                    onClick = {
                        // desktop: Ctrl+click toggles one chapter, Shift+click selects the range from the last one
                        val keys = windowInfo.keyboardModifiers
                        when {
                            keys.isShiftPressed -> onChapterSelected(item, true, true)
                            keys.isCtrlPressed -> onChapterSelected(item, !item.selected, false)
                            else -> onChapterItemClick(
                                chapterItem = item,
                                isAnyChapterSelected = isAnyChapterSelected,
                                onToggleSelection = { onChapterSelected(item, !item.selected, false) },
                                onChapterClicked = onChapterClicked,
                            )
                        }
                    },
```

      con `val windowInfo = LocalWindowInfo.current` junto a `val haptic`.
- `MangaInfoHeader.kt` y `MangaToolbar.kt`: solo el cambio de nulabilidad descrito arriba.
- `MangaCoverDialog.kt`:
  - Sustituir el contenido del `Scaffold` (`{ contentPadding -> ... }`, con las ramas `useNewRenderer` y `AndroidView`) por:

```kotlin
        ) { contentPadding ->
            // desktop: Coil's AsyncImage instead of Android's ImageViewer / SubsamplingScaleImageView
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .clickableNoIndication(onClick = onDismissRequest),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = manga,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
```

  - Borrar `useNewRenderer`, `view`, el parámetro `decorFitsSystemWindows` de `DialogProperties` y los imports de Android, `ImageDecoder`, `ReaderPageImageView`, `ImageViewer` y `appGraph`.
  - `onShareClick` pasa a ser `(() -> Unit)?`; si es nulo, no se dibuja su `IconButton`.
- `DuplicateMangaDialog.kt`: `onMigrate` pasa a ser nulo en las dos funciones que lo declaran (líneas ~86 y ~178). Si es nulo, no se dibuja el botón de migrar.
- `ChapterGetNextUnread.kt`, `SyncChaptersWithSource.kt`, `SetReadStatus.kt`, `ChapterFilter.kt`, `UpdateMangaFromRemote.kt`: sin cambios. Usan los stubs de descargas.
- `BrowseSourceViewModel.kt`:
  - `getColumnsPreference()` pierde el parámetro `orientation` y usa siempre `libraryPreferences.landscapeColumns`, porque en escritorio la ventana es apaisada.
  - Borrar `import android.content.res.Configuration`.
  - `addTracks` queda (es el stub).
- `BrowseSourceScreen.kt` (ui):
  - `getColumnsPreference(LocalConfiguration.current.orientation)` pasa a ser `getColumnsPreference()`.
  - La acción de WebView abre el navegador: `context.openInBrowser(url)` en lugar de `navigator.push(WebViewScreen(...))`.
  - En el `when (dialog)`, borrar las ramas `Migrate` y `ChangeMangaCategory` y añadir `else -> {}`.
  - En `DuplicateMangaDialog(...)`, pasar `onMigrate = null`.
  - Borrar los imports de `ChangeCategoryDialog`, `CategoryScreen`, `WebViewScreen` y `MigrateMangaDialog`.
  - Añadir `LaunchedEffect(Unit) { AppEvents.refresh.collect { /* el mismo refresco que el botón de la barra */ } }`. Buscar en el archivo la llamada que hace el botón de refrescar o el `PullRefresh` (por ejemplo, `mangaList.refresh()`) y usar esa.
- `MangaScreen.kt` (ui), los argumentos de `MangaScreen(...)`:
  - `onChapterClicked = { ReaderLauncher.open(context, it.mangaId, it.id) }`, y lo mismo en `continueReading`. Borrar `openChapter` y el import de `ReaderActivity`.
  - `onDownloadChapter = null`, `onDownloadActionClicked = null`, `downloadProgressProvider = { 0 }`.
  - `onWebViewClicked = { getMangaUrl(viewModel.manga, viewModel.source)?.let(context::openInBrowser) }.takeIf { isHttpSource }`. Borrar `openMangaInWebView`.
  - `onTrackingClicked = null`, `onEditCategoryClicked = null`, `onEditFetchIntervalClicked = null`, `onMigrateClicked = null` y `onEditNotesClicked = null`.
  - `onShareClicked = null`. Borrar `shareManga`.
  - En `when (dialog)`, borrar las ramas `ChangeCategory`, `Migrate`, `TrackSheet` y `SetFetchInterval` y añadir `else -> {}`. En `DuplicateManga`, `onMigrate = null`.
  - La rama `FullCover` pasa a usar la versión de escritorio:

```kotlin
            MangaViewModel.Dialog.FullCover -> {
                val vm = assistedMetroViewModel<MangaCoverViewModel, MangaCoverViewModel.Factory> {
                    create(mangaId = mangaId)
                }
                val manga by vm.state.collectAsStateWithLifecycle()
                val coverTitle = stringResource(MR.strings.action_save)
                manga?.let { current ->
                    MangaCoverDialog(
                        manga = current,
                        snackbarHostState = vm.snackbarHostState,
                        isCustomCover = remember(current) { current.hasCustomCover() },
                        onShareClick = null,
                        onSaveClick = {
                            FileDialogs.saveFile(coverTitle, "${current.title}.png")?.let(vm::saveCover)
                        },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> FileDialogs.openImage(coverTitle)?.let(vm::editCover)
                                EditCoverAction.DELETE -> vm.deleteCustomCover()
                                EditCoverAction.ADD_TO_LIBRARY -> vm.addToLibraryWarning()
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } ?: LoadingScreen()
            }
```

  - En `performSearch`, borrar la rama `is HomeScreen -> ...` y su import.
  - Al principio de `Content()`, añadir `LaunchedEffect(Unit) { AppEvents.refresh.collect { viewModel.fetchAllFromSource() } }`.
  - Borrar los imports que ya no se usan: `rememberLauncherForActivityResult`, `ActivityResultContracts`, `toUri`, `toShareIntent`, `CategoryScreen`, `ChangeCategoryDialog`, `HomeScreen`, `MangaNotesScreen`, `TrackInfoDialogHomeScreen`, `ReaderActivity`, `SettingsScreen`, `WebViewScreen`, `MigrationConfigScreen` y `MigrateMangaDialog`.
- `MangaViewModel.kt`: sin cambios. Compila con los stubs: `DownloadManager`, `DownloadCache`, `TrackerManager`, `TrackChapter`, `RefreshTracks`, `AddTracks`, `TrackPreferences` y `ReaderPreferences`. Si el compilador pide un miembro de un stub que no existe, añadirlo al stub con la misma firma que en la clase de Mihon y un cuerpo nulo (`emptyFlow()`, `false`, `0`, `emptyList()`, `Unit`).
- `SourcesTab.kt`, `SourcesViewModel.kt`, `SourcesFilterScreen.kt`, `SourcesFilterViewModel.kt` y `GlobalSearch*`: sin cambios.

Si el compilador pide un accesor de `context.appGraph` que no existe, añadirlo a `AppGraph` con el mismo nombre y tipo que en el `AppGraph` de Mihon (`app/src/main/java/mihon/app/di/AppGraph.kt`). Por ejemplo, `libraryPreferences: LibraryPreferences`.

- [ ] **Step 5: `MangaCoverViewModel` de escritorio**

`desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/manga/MangaCoverViewModel.kt`:

```kotlin
package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import java.io.File
import kotlin.time.Duration.Companion.seconds

// desktop: Mihon's version saves through MediaStore and shares through Intents; this one uses Windows files
@AssistedInject
class MangaCoverViewModel(
    @Assisted private val mangaId: Long,
    private val getManga: GetManga,
    private val coverCache: CoverCache,
    private val updateManga: UpdateManga,
    private val context: Context,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): MangaCoverViewModel
    }

    val snackbarHostState = SnackbarHostState()

    val state: StateFlow<Manga?> = getManga.subscribe(mangaId)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    /** Saves the cover as PNG, whatever format the source served. */
    fun saveCover(target: File) {
        viewModelScope.launch {
            try {
                val png = encodeCover()
                withContext(Dispatchers.IO) { target.writeBytes(png) }
                notify(MR.strings.cover_saved)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                notify(MR.strings.error_saving_cover)
            }
        }
    }

    fun editCover(file: File) {
        val manga = state.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                file.inputStream().use { coverCache.setCustomCoverToCache(manga, it) }
                updateManga.awaitUpdateCoverLastModified(mangaId)
                notify(MR.strings.cover_updated)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                notify(MR.strings.notification_cover_update_failed)
            }
        }
    }

    fun deleteCustomCover() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                coverCache.deleteCustomCover(mangaId)
                updateManga.awaitUpdateCoverLastModified(mangaId)
                notify(MR.strings.cover_updated)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                notify(MR.strings.notification_cover_update_failed)
            }
        }
    }

    fun addToLibraryWarning() {
        viewModelScope.launch { notify(MR.strings.notification_first_add_to_library) }
    }

    private suspend fun encodeCover(): ByteArray {
        val manga = state.value ?: error("The manga isn't loaded")
        val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(manga).size(Size.ORIGINAL).build()
        val result = SingletonImageLoader.get(PlatformContext.INSTANCE).execute(request) as? SuccessResult
            ?: error("The cover couldn't be loaded")
        return withContext(Dispatchers.Default) {
            Image.makeFromBitmap(result.image.toBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encoding failed")
        }
    }

    private suspend fun notify(message: StringResource) {
        snackbarHostState.showSnackbar(context.stringResource(message), withDismissAction = true)
    }
}
```

- [ ] **Step 6: Pestaña Fuentes y Ctrl+F**

En `BrowseTab.kt`, la lista de pestañas pasa a ser:

```kotlin
        val tabs = listOf(
            sourcesTab(),
            extensionsTab(extensionsViewModel),
        )
```

(import `eu.kanade.tachiyomi.ui.browse.source.sourcesTab`).

En `HomeScreen.Content()`, junto al `LaunchedEffect` de ajustes:

```kotlin
            val navigator = LocalNavigator.currentOrThrow
            LaunchedEffect(Unit) { AppEvents.globalSearch.collect { navigator.push(GlobalSearchScreen()) } }
```

Como `HomeScreen` es la raíz, este `LaunchedEffect` vive mientras la app esté abierta. Ctrl+F abre la búsqueda global desde cualquier pantalla. Imports: `cafe.adriel.voyager.navigator.LocalNavigator`, `cafe.adriel.voyager.navigator.currentOrThrow`, `eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen`.

- [ ] **Step 7: Ejecutar el test y ver que pasa**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.browse.BrowseAndMangaTest"`
Expected: PASS (2 tests).

Si el clic derecho no abre el menú en el entorno de test, el error será que no encuentra el texto "Marcar como leído". En ese caso, comprobar en `ContextMenuArea` que el `MangaChapterListItem` recibe el evento (no debe consumir el botón secundario), y no saltarse el paso del test.

- [ ] **Step 8: Ejecutar todos los tests del módulo y probar a mano**

Run: `./gradlew :desktop-app:test`
Expected: PASS.

Prueba manual con MangaDex instalado (Task 9):
1. Explorar › Fuentes › MangaDex: populares, recientes y búsqueda con filtros.
2. Clic derecho sobre un manga: lo añade a la biblioteca (aparece el distintivo).
3. Abrir un manga con la ventana ancha: dos paneles. Estrechar la ventana: una columna.
4. Ctrl+clic y Shift+clic seleccionan capítulos. Clic derecho: el menú marca como leído.
5. Clic en un capítulo: aviso "El lector llega en la próxima versión".
6. F5 refresca. Ctrl+F abre la búsqueda global. Esc vuelve atrás.
7. Portada › Guardar: aparece el diálogo de Windows y se escribe un PNG.

- [ ] **Step 9: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): sources, browsing, global search and manga details from Mihon

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---
### Task 12: Ajustes: apariencia, explorar y avanzado

**Files:**
- Create (portados, mismo paquete):
  - `eu/kanade/presentation/more/settings/PreferenceScaffold.kt`
  - `eu/kanade/presentation/more/settings/PreferenceScreen.kt`
  - `eu/kanade/presentation/more/settings/screen/SearchableSettings.kt`
  - `eu/kanade/presentation/more/settings/screen/SettingsAppearanceScreen.kt`
  - `eu/kanade/presentation/more/settings/screen/SettingsBrowseScreen.kt`
  - `eu/kanade/presentation/more/settings/widget/AppThemeModePreferenceWidget.kt`
  - `eu/kanade/presentation/more/settings/widget/AppThemePreferenceWidget.kt`
  - `eu/kanade/presentation/more/settings/widget/PreferenceGroupHeader.kt`
- Create: `desktop-app/src/main/kotlin/eu/kanade/presentation/util/TwentyFourHourSystem.kt` (versión de escritorio, misma función)
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsTab.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/AdvancedSettingsScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/HomeScreen.kt`
- Modify: `i18n/.../base/strings.xml` y `.../es/strings.xml`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/settings/SettingsTest.kt`

**Interfaces:**
- Consumes:
  - `Preference` / `PreferenceItem` y los widgets (Task 9).
  - `UiPreferences` (Task 5), `SourcePreferences` y `ContentWarningPrefs` (Task 8).
  - `AppProviders` y `HomeScreen.tabs` (Tasks 5 y 11).
  - `AppDirs` y `coil3.SingletonImageLoader`.
- Produces:
  - `object SettingsTab : Tab`, la última pestaña del rail.
  - `object AdvancedSettingsScreen : SearchableSettings`.
  - La lista del rail queda `HomeScreen.tabs = listOf(BrowseTab, SettingsTab)`. El plan 2 añade Historial.

- [ ] **Step 1: Escribir el test (falla)**

`desktop-app/src/test/kotlin/mihon/desktop/app/settings/SettingsTest.kt`:

```kotlin
package mihon.desktop.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.collections.shouldContain
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.desktop.core.ContentWarningPrefs
import mihon.domain.extension.model.ContentWarning
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class SettingsTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `allowing 18+ content in the browse settings is saved where the extension loader reads it`() =
        runComposeUiTest {
            val graph = startTestApp()
            setContent { AppContent(graph, crash = null, onRestart = {}) }

            onNodeWithText(str(MR.strings.label_settings)).performClick()
            waitForText(str(MR.strings.browse))
            onNodeWithText(str(MR.strings.browse)).performClick()
            waitForText(str(MR.strings.pref_allowed_content_warnings))
            onNodeWithText(str(MR.strings.pref_allowed_content_warnings)).performClick()
            onNodeWithText(str(MR.strings.ext_content_warning_nsfw)).performClick()
            onNodeWithText(str(MR.strings.action_ok)).performClick()
            waitForIdle()

            ContentWarningPrefs.enabled(graph.preferenceStore).get() shouldContain ContentWarning.NSFW
        }

    @Test
    fun `the appearance and advanced settings open`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_settings)).performClick()
        onNodeWithText(str(MR.strings.pref_category_appearance)).performClick()
        waitForText(str(MR.strings.pref_app_theme))
    }
}
```

`str(MR.strings.browse)` aparece dos veces en pantalla: en el rail y en la lista de ajustes. Si `onNodeWithText` falla por haber más de un nodo, usar `onAllNodesWithText(...).onLast()` para la entrada de ajustes, y anotarlo.

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.settings.SettingsTest"`
Expected: FAIL: no hay entrada "Ajustes" en el rail (`No node found ... label_settings`) o la compilación falla por `SettingsTab`.

- [ ] **Step 3: Portar el andamiaje de ajustes y las dos pantallas**

Copiar los archivos de la lista, con estos cambios:

- `SettingsAppearanceScreen.kt`:
  - Borrar la llamada `setAppCompatDelegateThemeMode(it)` y su import. El tema reacciona a la preferencia (Task 5).
  - En el interruptor de negro puro, `onValueChanged` pasa a devolver solo `true`: borrar el `ActivityCompat.recreate`.
  - Borrar las entradas "Idioma de la app" (`AppLanguageScreen`) y "Modo de interfaz de tableta" (`tabletUiMode`), con sus imports. Ver la desviación 6.
  - Cambiar `val locale = LocalLocale.current` por `val locale = remember { Locale.getDefault() }` y `locale.platformLocale` por `locale`. Imports: `java.util.Locale`; borrar `LocalLocale`.
  - Borrar `import android.app.Activity` y `androidx.core.app.ActivityCompat`.
- `AppThemePreferenceWidget.kt`: borrar la línea `(context as? Activity)?.let { ActivityCompat.recreate(it) }` y sus imports. El tema se recompone solo.
- `SettingsBrowseScreen.kt`:
  - Los dos `onValueChanged` que llaman a `authenticate(...)` se borran (no hay bloqueo biométrico en escritorio), así que el cambio se guarda directamente.
  - Borrar los imports de `FragmentActivity` y `AuthenticatorUtil.authenticate`.
- `PreferenceScaffold.kt`, `PreferenceScreen.kt`, `SearchableSettings.kt`, `AppThemeModePreferenceWidget.kt` y `PreferenceGroupHeader.kt`: sin cambios.

`desktop-app/src/main/kotlin/eu/kanade/presentation/util/TwentyFourHourSystem.kt`:

```kotlin
package eu.kanade.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.DateFormat
import java.text.SimpleDateFormat

// desktop: Android asks the system setting; here the locale's short time pattern tells if it has AM/PM
@Composable
fun rememberIs24HourSystem(): Boolean = remember {
    (DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)?.toPattern()?.contains('a') != true
}
```

- [ ] **Step 4: Pantalla avanzada y pestaña de ajustes**

`AdvancedSettingsScreen.kt`:

```kotlin
package mihon.desktop.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import coil3.PlatformContext
import coil3.SingletonImageLoader
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.awt.Desktop

/** Spec 2a, section 3: clear the image cache and open the logs folder. */
object AdvancedSettingsScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.desktop_clear_image_cache),
                onClick = {
                    val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                    context.toast(MR.strings.desktop_image_cache_cleared)
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.desktop_open_logs_folder),
                onClick = { Desktop.getDesktop().open(context.appGraph.appDirs.logs.toFile()) },
            ),
        )
    }
}
```

`SettingsTab.kt`:

```kotlin
package mihon.desktop.app.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Tab
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Code
import mihon.icons.materialsymbols.rounded.Explore
import mihon.icons.materialsymbols.rounded.Palette
import mihon.icons.materialsymbols.rounded.Settings
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** The settings rail entry: the desktop subset of Mihon's settings categories. */
data object SettingsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 9u,
            title = stringResource(MR.strings.label_settings),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Settings),
        )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val entries = listOf(
            Triple(MR.strings.pref_category_appearance, MaterialSymbols.Rounded.Palette, SettingsAppearanceScreen),
            Triple(MR.strings.browse, MaterialSymbols.Rounded.Explore, SettingsBrowseScreen),
            Triple(MR.strings.pref_category_advanced, MaterialSymbols.Rounded.Code, AdvancedSettingsScreen),
        )
        Scaffold(topBar = { AppBar(title = stringResource(MR.strings.label_settings), scrollBehavior = it) }) { padding ->
            LazyColumn(contentPadding = padding) {
                items(entries) { (title, icon, screen) ->
                    TextPreferenceWidget(
                        title = stringResource(title),
                        icon = icon,
                        onPreferenceClick = { navigator.push(screen) },
                    )
                }
            }
        }
    }
}
```

Se usa `LocalNavigator.currentOrThrow` igual que `MoreTab` de Mihon (`app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt`), que empuja sus pantallas de ajustes desde una pestaña. El test del Step 1 comprueba que la pantalla de ajustes se abre.

En `HomeScreen.kt`: `val tabs: List<Tab> = listOf(BrowseTab, SettingsTab)` (import `mihon.desktop.app.settings.SettingsTab`).

Cadenas nuevas (bloque `<!-- Desktop -->`):

```xml
    <string name="desktop_clear_image_cache">Clear image cache</string>
    <string name="desktop_image_cache_cleared">Image cache cleared</string>
    <string name="desktop_open_logs_folder">Open logs folder</string>
```

```xml
    <string name="desktop_clear_image_cache">Borrar caché de imágenes</string>
    <string name="desktop_image_cache_cleared">Caché de imágenes borrada</string>
    <string name="desktop_open_logs_folder">Abrir carpeta de logs</string>
```

- [ ] **Step 5: Ejecutar el test y ver que pasa**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.settings.SettingsTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Comprobación manual**

1. Ajustes › Apariencia: cambiar a oscuro y luego a otro tema de color. La ventana cambia al momento.
2. Ajustes › Explorar: activar 18+. En Extensiones aparecen las disponibles con 18+, y una extensión 18+ ya instalada pasa de "No cargada (filtrada)" a cargada sin reiniciar.
3. Ajustes › Avanzado › Abrir carpeta de logs: se abre el Explorador de Windows en esa carpeta.
4. Ctrl+, abre Ajustes desde cualquier pestaña.

- [ ] **Step 7: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): appearance, browse and advanced settings

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: CI, regresiones y verificación del plan

**Files:**
- Modify: `.github/workflows/build.yml`
- Modify: `scripts/check-licenses.sh` (solo si marca falsos positivos con `desktop-app`)
- Create: `scripts/check-no-android.sh`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: CI que compila y prueba `desktop-app`, y una comprobación de que no se usa Android directamente.

- [ ] **Step 1: Comprobación de imports de Android**

`scripts/check-no-android.sh`:

```bash
#!/usr/bin/env bash
# desktop-app may reach Android APIs only through compat-shims or AndroidCompat (spec 2a, section 6).
# Allowed: Context and the androidx.preference models (AndroidCompat), Toast (AndroidCompat), widget-free androidx.
set -euo pipefail
cd "$(dirname "$0")/.."
bad=$(grep -rhoE '^import android(x)?\.[A-Za-z0-9_.]+' desktop-app/src/main desktop-app/src/stubs \
    | sort -u \
    | grep -vE '^import android\.(content\.Context|widget\.Toast)$' \
    | grep -vE '^import androidx\.(compose|lifecycle|paging|preference|activity\.compose\.BackHandler)' \
    || true)
if [ -n "$bad" ]; then
    echo "Android imports outside the allowed shims:"
    echo "$bad"
    exit 1
fi
echo "No direct Android imports in desktop-app"
```

Run: `bash scripts/check-no-android.sh`
Expected: `No direct Android imports in desktop-app`. Si lista alguno, quitarlo del archivo que lo importa (es un resto de un port), no ampliar la lista de permitidos.

- [ ] **Step 2: CI**

En `.github/workflows/build.yml`, en el job que ejecuta los tests:
- Añadir `:desktop-app:test` y `:presentation-core:test` al comando de Gradle.
- Añadir un paso con `bash scripts/check-no-android.sh`.

Mantener `permissions: contents: read` y el `timeout-minutes` actual. Si el job pasa de 60 minutos, subirlo a 75 y anotarlo.

- [ ] **Step 3: Suite completa, harness, licencias y formato**

Run: `./gradlew test spotlessCheck > build/plan1-verify.txt; tail -40 build/plan1-verify.txt`
Expected: `BUILD SUCCESSFUL`. Siguen en verde los 173 tests anteriores y los nuevos de este plan.

Run: `bash scripts/check-licenses.sh`
Expected: termina con código 0.

Run: `./gradlew :compat-harness:run --args="--repo https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb --limit 100 --threshold 0.8"`
Expected: tasa de compatibilidad ≥ 80 % (la última medida fue 89,4 %) y ningún resultado con el motivo `Filtered`. Este plan no toca la carga de clases, así que una bajada apunta a una regresión del refactor del grafo (Task 2) o del filtro (Task 8, Step 7).

- [ ] **Step 4: Prueba manual del plan 1, de principio a fin**

Con `%APPDATA%\MangaReader` y `%LOCALAPPDATA%\MangaReader` borrados (primera ejecución):
1. `gradlew :desktop-app:run`. Explorar › Extensiones muestra el estado vacío con "Repositorios de extensiones".
2. Añadir el repo de keiyoushi e instalar MangaDex. Aparece con su icono.
3. Fuentes › MangaDex › buscar › abrir un manga. Detalles con portada, descripción y capítulos; dos paneles en ventana ancha.
4. Clic en un capítulo: aviso de que el lector llega en la próxima versión.
5. Cerrar y reabrir: misma posición de ventana, el manga sigue en la BD (aparece como añadido si se añadió), los ajustes se conservan.
6. Segunda instancia: enfoca la primera.

- [ ] **Step 5: Commit**

```bash
git add .github scripts
git commit -m "ci: test the desktop app and check it reaches Android only through the shims

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

## Después de este plan

Plan 2 del subproyecto 2a (se escribe sobre el código resultante):
- El lector de la sección 4 del spec. `ReaderLauncher` deja de mostrar el aviso.
- El historial y su entrada en el rail.
- Los ajustes del lector. `ReaderPreferences` pasa de stub a port completo.
