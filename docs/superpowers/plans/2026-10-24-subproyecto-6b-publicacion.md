# Subproyecto 6b: publicación y auto-actualización — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turboyomi se publica en `rodriguezrobinj/turboyomi`. Cada tag `v*` produce una release, y la app avisa e instala la versión nueva con un clic.

**Architecture:**
- **App:** tres piezas en `mihon.desktop.app.update`.
  - `AppVersion` compara versiones.
  - `AppReleases` lee la última release de GitHub.
  - `AppUpdater` comprueba, descarga, verifica e instala; tiene un `StateFlow` que la UI pinta.
- **Instalación:** un PowerShell oculto espera a que la app salga y lanza `msiexec`, así el MSI no choca con archivos en uso.
- **CI:** tres workflows de GitHub Actions en `windows-latest`.

**Tech Stack:** Kotlin, Compose Desktop, OkHttp (`NetworkHelper`), kotlinx.serialization, Metro, GitHub Actions, `gh` CLI, JUnit 5 + kotest + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-10-24-subproyecto-6b-publicacion-design.md`

## Global Constraints

- Repo `rodriguezrobinj/turboyomi`, público, remote `origin`; nunca push a `upstream` (spec §1).
- Assets de la release: `Turboyomi-<v>.msi`, `Turboyomi-<v>.zip` y `SHA256SUMS` (spec §2).
- La app instalada es `%LOCALAPPDATA%\Programs\Turboyomi\Turboyomi.exe`; fuera de ahí, "Actualizar" abre la página de la release (spec §3 C).
- La comprobación automática es cada 24 h, con el interruptor activado por defecto, y está apagada en desarrollo (spec §2, §3 C).
- `msiexec /i "<msi>" /passive /norestart` (spec §3 B).
- Sin firma: el paso de firma se salta sin secretos (spec §1).
- El secreto `TRACKER_KEYS` es opcional (spec §4).
- Tests `= runBlocking {}` con `: Unit`; scripts de edición con Write; commits con `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Desviaciones respecto a la spec

1. **No se sustituye `ReleaseServiceImpl` de Mihon.** `AppReleases` es una clase propia de escritorio. La de Mihon sigue en el grafo sin uso. Evita un `replaces` de Metro sobre una clase de `data` y la API `Arguments` de Android (`isFoss`, `commitCount`).
2. **Sin los 30 s de espera al arrancar.** El `JobScheduler` comprueba al arrancar y cuando han pasado 24 h desde la última comprobación guardada, no en cada arranque. Así se respetan las 60 peticiones/h de GitHub, y un reinicio no vuelve a consultar.
3. **Licencias.** "Licencias" en Acerca de abre `LICENSE` y `NOTICE` en el repo. No hay aboutlibraries en escritorio.
4. **Tareas de publicación tras el merge.** La Task 5 (crear el repo, push y release) va después del merge local en `master`. Los arreglos que pida el CI van como commits en `master`.

## Review Focus

1. Una ruta del MSI con apóstrofo (`C:\Users\O'Brien\…`): el comando de PowerShell sigue siendo válido. Test en la Task 2.
2. `SHA256SUMS` con el formato `<hash> *<archivo>` (binario) o con mayúsculas: se reconoce. Test en la Task 2.
3. Un tag de release `v0.1.0-beta` o `nightly`: no ofrece actualización y no rompe. Test en la Task 1.
4. GitHub devuelve 403 por el límite de la API: la comprobación automática calla y la manual informa. Test en la Task 2.
5. "Actualizar" pulsado dos veces seguidas: una sola descarga. Test en la Task 2.

---

### Task 1: versiones y releases de GitHub

**Files:**
- Create:
  - `desktop-app/src/main/kotlin/mihon/desktop/app/update/AppVersion.kt`;
  - `desktop-app/src/main/kotlin/mihon/desktop/app/update/AppReleases.kt`.
- Test:
  - `desktop-app/src/test/kotlin/mihon/desktop/app/update/AppVersionTest.kt`;
  - `desktop-app/src/test/kotlin/mihon/desktop/app/update/AppReleasesTest.kt`.

**Interfaces:**
- Produces:
  - `AppVersion.isNewer(tag: String, current: String): Boolean`.
  - `data class AppRelease(val version: String, val notes: String, val page: String, val msiName: String, val msiUrl: String, val sumsUrl: String)`.
  - `AppReleases` (inyectable): `suspend fun latest(apiBase: String = AppReleases.API_BASE): AppRelease?`. Devuelve null sin `.msi` o sin `SHA256SUMS`, y lanza la excepción de OkHttp si falla la red o GitHub.
  - Constantes `AppReleases.REPO = "rodriguezrobinj/turboyomi"` y `AppReleases.API_BASE = "https://api.github.com"`.

- [ ] **Step 1: Escribir los tests**

`AppVersionTest.kt`:

```kotlin
package mihon.desktop.app.update

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppVersionTest {
    @Test
    fun `versions compare by number, not by text`() {
        AppVersion.isNewer("v0.10.0", "0.9.0") shouldBe true
        AppVersion.isNewer("v1.0.0", "0.99.99") shouldBe true
        AppVersion.isNewer("0.1.1", "0.1.0") shouldBe true
    }

    @Test
    fun `the same or an older version is no update`() {
        AppVersion.isNewer("v0.1.0", "0.1.0") shouldBe false
        AppVersion.isNewer("v0.0.9", "0.1.0") shouldBe false
    }

    @Test
    fun `a tag that isn't x_y_z is no update`() {
        AppVersion.isNewer("v0.2.0-beta", "0.1.0") shouldBe false
        AppVersion.isNewer("nightly", "0.1.0") shouldBe false
        AppVersion.isNewer("v1.2", "0.1.0") shouldBe false
    }
}
```

`AppReleasesTest.kt`:

```kotlin
package mihon.desktop.app.update

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.startTestApp
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class AppReleasesTest {
    private val server = MockWebServer().apply { start() }
    private val graph = startTestApp()
    private val releases = AppReleases(graph.networkHelper, graph.json)
    private val base = server.url("/").toString().trimEnd('/')

    @AfterEach
    fun stop() = server.close()

    private fun release(vararg assets: String) = """
        {"tag_name":"v0.2.0","body":"## Novedades\n- algo","html_url":"https://github.com/r/t/releases/tag/v0.2.0",
         "assets":[${assets.joinToString { """{"name":"$it","browser_download_url":"https://dl/$it"}""" }}]}
    """.trimIndent()

    @Test
    fun `the latest release gives its msi and its checksums`(): Unit = runBlocking {
        server.enqueue(MockResponse.Builder().body(release("Turboyomi-0.2.0.zip", "Turboyomi-0.2.0.msi", "SHA256SUMS")).build())

        val latest = releases.latest(base)!!

        server.takeRequest().target shouldBe "/repos/rodriguezrobinj/turboyomi/releases/latest"
        latest.version shouldBe "v0.2.0"
        latest.notes shouldBe "## Novedades\n- algo"
        latest.page shouldBe "https://github.com/r/t/releases/tag/v0.2.0"
        latest.msiName shouldBe "Turboyomi-0.2.0.msi"
        latest.msiUrl shouldBe "https://dl/Turboyomi-0.2.0.msi"
        latest.sumsUrl shouldBe "https://dl/SHA256SUMS"
    }

    @Test
    fun `a release without its msi or its checksums is no update`(): Unit = runBlocking {
        server.enqueue(MockResponse.Builder().body(release("Turboyomi-0.2.0.zip", "SHA256SUMS")).build())
        server.enqueue(MockResponse.Builder().body(release("Turboyomi-0.2.0.msi")).build())

        releases.latest(base).shouldBeNull()
        releases.latest(base).shouldBeNull()
    }

    @Test
    fun `GitHub's rate limit comes out as an error`(): Unit = runBlocking {
        server.enqueue(MockResponse.Builder().code(403).body("""{"message":"API rate limit exceeded"}""").build())

        shouldThrowAny { releases.latest(base) }
    }
}
```

Los imports de `MockWebServer`/`MockResponse` y la forma de `takeRequest().target` se ajustan a los que usan los tests existentes con `MockWebServer` (por ejemplo, `TrackLoginHandlerTest`).

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.update.*"`
Expected: FAIL de compilación (`AppVersion` y `AppReleases` no existen).

- [ ] **Step 3: Implementación**

`AppVersion.kt`:

```kotlin
package mihon.desktop.app.update

/** Release tags against the running version (spec 6b §2): plain x.y.z, with or without a "v"; anything else is ignored. */
object AppVersion {
    private val SEMVER = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    private fun parse(version: String): List<Int>? = SEMVER.find(version.trim())?.groupValues?.drop(1)?.map(String::toInt)

    fun isNewer(tag: String, current: String): Boolean {
        val new = parse(tag) ?: return false
        val old = parse(current) ?: return false
        return new.zip(old).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a > b } ?: false
    }
}
```

`AppReleases.kt`:

```kotlin
package mihon.desktop.app.update

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AppRelease(
    val version: String,
    val notes: String,
    val page: String,
    val msiName: String,
    val msiUrl: String,
    val sumsUrl: String,
)

/** The latest Turboyomi release on GitHub (spec 6b §2). Mihon's ReleaseServiceImpl picks assets by Android ABI. */
@Inject
class AppReleases(
    private val network: NetworkHelper,
    private val json: Json,
) {
    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tag: String,
        @SerialName("body") val body: String = "",
        @SerialName("html_url") val page: String,
        val assets: List<Asset>,
    )

    @Serializable
    private data class Asset(val name: String, @SerialName("browser_download_url") val url: String)

    /** Null when the release lacks its MSI or its checksums; network and GitHub errors are thrown. */
    suspend fun latest(apiBase: String = API_BASE): AppRelease? = withContext(Dispatchers.IO) {
        val release = with(json) {
            network.client.newCall(GET("$apiBase/repos/$REPO/releases/latest")).awaitSuccess().parseAs<GithubRelease>()
        }
        val msi = release.assets.firstOrNull { it.name.endsWith(".msi") } ?: return@withContext null
        val sums = release.assets.firstOrNull { it.name == "SHA256SUMS" } ?: return@withContext null
        AppRelease(release.tag, release.body, release.page, msi.name, msi.url, sums.url)
    }

    companion object {
        const val REPO = "rodriguezrobinj/turboyomi"
        const val API_BASE = "https://api.github.com"
    }
}
```

Si `json` no ignora claves desconocidas (`ignoreUnknownKeys`), el test lo mostrará; en ese caso se usa un `Json { ignoreUnknownKeys = true }` local y se apunta como ruling.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.update.*"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src
git commit -m "feat(desktop-app): read Turboyomi's latest GitHub release and compare versions

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `AppUpdater`: comprobar, descargar, verificar e instalar

**Files:**
- Create:
  - `desktop-app/src/main/kotlin/mihon/desktop/app/update/AppUpdater.kt`;
  - `desktop-app/src/main/kotlin/mihon/desktop/app/update/UpdateInstaller.kt`.
- Modify:
  - `desktop-app/src/main/kotlin/eu/kanade/domain/base/BasePreferences.kt` (dos preferencias);
  - `desktop-app/src/main/kotlin/mihon/app/di/AppGraph.kt` (accesor `appUpdater`).
- Test:
  - `desktop-app/src/test/kotlin/mihon/desktop/app/update/AppUpdaterTest.kt`;
  - `desktop-app/src/test/kotlin/mihon/desktop/app/update/UpdateInstallerTest.kt`.

**Interfaces:**
- Consumes (Task 1): `AppReleases.latest(apiBase)`, `AppRelease`, `AppVersion.isNewer`.
- Produces:
  - `sealed interface UpdateState { Idle; Available(release); Downloading(release, progress: Float?); Failed(release, message); Installing }`.
  - `AppUpdater` (`@SingleIn(AppScope)`):
    - `val state: StateFlow<UpdateState>`;
    - `suspend fun check(manual: Boolean)`;
    - `fun update(release: AppRelease)`, que descarga, verifica e instala en su scope, o abre la página si no está instalada;
    - `fun cancel()`, `fun dismiss()`;
    - `fun periodicJob(): PeriodicJob`;
    - seams `var apiBase: String`, `var install: UpdateInstaller`, `var openPage: (String) -> Unit`.
  - `UpdateInstaller`:
    - `fun isInstalled(): Boolean`, `fun isDevelopment(): Boolean`;
    - `fun command(msi: File, pid: Long): List<String>`;
    - `fun launch(command: List<String>)`.
  - Preferencias `BasePreferences.autoCheckAppUpdates: Preference<Boolean>` (true) y `lastAppUpdateCheck: Preference<Long>` (0).

- [ ] **Step 1: Escribir los tests**

`UpdateInstallerTest.kt`:

```kotlin
package mihon.desktop.app.update

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import java.util.Base64

class UpdateInstallerTest {
    private val localAppData = Path.of("C:\\Users\\José O'Brien\\AppData\\Local")

    private fun installer(exe: String?) = UpdateInstaller(exe = { exe?.let(Path::of) }, localAppData = { localAppData })

    @Test
    fun `only the exe in Programs Turboyomi is the installed app`() {
        installer("C:\\Users\\José O'Brien\\AppData\\Local\\Programs\\Turboyomi\\Turboyomi.exe").isInstalled() shouldBe true
        installer("c:\\users\\josé o'brien\\appdata\\local\\programs\\turboyomi\\TURBOYOMI.EXE").isInstalled() shouldBe true
        installer("D:\\Apps\\Turboyomi\\Turboyomi.exe").isInstalled() shouldBe false
        installer("C:\\Program Files\\Java\\jdk-23\\bin\\java.exe").isInstalled() shouldBe false
    }

    @Test
    fun `development is a java process, not the app's exe`() {
        installer("C:\\Program Files\\Java\\jdk-23\\bin\\java.exe").isDevelopment() shouldBe true
        installer(null).isDevelopment() shouldBe true
        installer("D:\\Apps\\Turboyomi\\Turboyomi.exe").isDevelopment() shouldBe false
    }

    @Test
    fun `the command waits for the app to exit, then runs msiexec on a path with spaces, accents and an apostrophe`() {
        val msi = File("C:\\Users\\José O'Brien\\AppData\\Local\\Turboyomi\\cache\\updates\\Turboyomi-0.2.0.msi")

        val command = installer(null).command(msi, pid = 4242)

        command.take(4) shouldBe listOf("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden")
        command[4] shouldBe "-EncodedCommand"
        String(Base64.getDecoder().decode(command[5]), Charsets.UTF_16LE) shouldBe
            "Wait-Process -Id 4242 -ErrorAction SilentlyContinue; " +
            "Start-Process -FilePath 'msiexec.exe' -ArgumentList '/i', " +
            "'\"C:\\Users\\José O''Brien\\AppData\\Local\\Turboyomi\\cache\\updates\\Turboyomi-0.2.0.msi\"', " +
            "'/passive', '/norestart'"
    }
}
```

`AppUpdaterTest.kt`:

```kotlin
package mihon.desktop.app.update

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.waitUntil
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.io.path.listDirectoryEntries

class AppUpdaterTest {
    private val server = MockWebServer().apply { start() }
    private val graph = startTestApp()
    private val updater = graph.appUpdater
    private val msiBytes = ByteArray(200_000) { (it % 251).toByte() }
    private val hash = MessageDigest.getInstance("SHA-256").digest(msiBytes).joinToString("") { "%02x".format(it) }
    private val launched = mutableListOf<List<String>>()
    private val opened = mutableListOf<String>()

    init {
        updater.apiBase = server.url("/").toString().trimEnd('/')
        updater.install = object : UpdateInstaller(exe = { null }, localAppData = { graph.appDirs.local }) {
            override fun isInstalled() = true
            override fun isDevelopment() = false
            override fun launch(command: List<String>) {
                launched += command
            }
        }
        updater.openPage = { opened += it }
    }

    @AfterEach
    fun stop() = server.close()

    private fun release() = AppRelease(
        version = "v9.0.0", notes = "notas", page = "https://github.com/x/releases/tag/v9.0.0",
        msiName = "Turboyomi-9.0.0.msi", msiUrl = server.url("/Turboyomi-9.0.0.msi").toString(),
        sumsUrl = server.url("/SHA256SUMS").toString(),
    )

    private fun enqueueDownload(sums: String) {
        server.enqueue(MockResponse.Builder().body(Buffer().write(msiBytes)).build())
        server.enqueue(MockResponse.Builder().body(sums).build())
    }

    private fun updates() = graph.appDirs.cache.resolve("updates")

    @Test
    fun `a newer release is offered`(): Unit = runBlocking {
        server.enqueue(
            MockResponse.Builder().body(
                """{"tag_name":"v9.0.0","body":"b","html_url":"p","assets":[
                {"name":"Turboyomi-9.0.0.msi","browser_download_url":"m"},{"name":"SHA256SUMS","browser_download_url":"s"}]}""",
            ).build(),
        )

        updater.check(manual = false)

        updater.state.value.shouldBeInstanceOf<UpdateState.Available>().release.version shouldBe "v9.0.0"
    }

    @Test
    fun `the automatic check says nothing when GitHub refuses, the manual one does`(): Unit = runBlocking {
        val messages = mutableListOf<String>()
        val collector = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            AppMessages.messages.collect { messages += it.text }
        }
        server.enqueue(MockResponse.Builder().code(403).build())
        server.enqueue(MockResponse.Builder().code(403).build())

        updater.check(manual = false)
        kotlinx.coroutines.delay(300)
        messages.shouldBeEmpty()
        updater.check(manual = true)
        waitUntil(timeoutMillis = 5_000) { messages.isNotEmpty() }
        collector.cancel()
        updater.state.value shouldBe UpdateState.Idle
    }

    @Test
    fun `a verified download hands the msi to msiexec and the app goes into Installing`(): Unit = runBlocking {
        // The binary form "<hash> *<file>" and upper case, as other tools write it
        enqueueDownload("${hash.uppercase()} *Turboyomi-9.0.0.msi\nffff  Turboyomi-9.0.0.zip\n")

        updater.update(release())

        waitUntil(timeoutMillis = 10_000) { updater.state.value == UpdateState.Installing }
        launched.single().first() shouldBe "powershell.exe"
        updates().resolve("Turboyomi-9.0.0.msi").toFile().readBytes().contentEquals(msiBytes) shouldBe true
    }

    @Test
    fun `a download that doesn't match its checksum is deleted and reported`(): Unit = runBlocking {
        enqueueDownload("${"0".repeat(64)}  Turboyomi-9.0.0.msi\n")

        updater.update(release())

        waitUntil(timeoutMillis = 10_000) { updater.state.value is UpdateState.Failed }
        launched.shouldBeEmpty()
        updates().listDirectoryEntries().shouldBeEmpty()
    }

    @Test
    fun `cancelling a download removes the partial file`(): Unit = runBlocking {
        server.enqueue(
            MockResponse.Builder().body(Buffer().write(msiBytes))
                .throttleBody(10_000, 1, java.util.concurrent.TimeUnit.SECONDS).build(),
        )

        updater.update(release())
        waitUntil(timeoutMillis = 5_000) { updater.state.value is UpdateState.Downloading }
        updater.cancel()

        waitUntil(timeoutMillis = 5_000) { updater.state.value is UpdateState.Available }
        waitUntil(timeoutMillis = 5_000) { updates().listDirectoryEntries().isEmpty() }
    }

    @Test
    fun `a second click while downloading starts no second download`(): Unit = runBlocking {
        enqueueDownload("$hash  Turboyomi-9.0.0.msi\n")

        updater.update(release())
        updater.update(release())

        waitUntil(timeoutMillis = 10_000) { updater.state.value == UpdateState.Installing }
        server.requestCount shouldBe 2 // one msi, one SHA256SUMS
        launched.size shouldBe 1
    }

    @Test
    fun `outside the installed app, updating opens the release page`(): Unit = runBlocking {
        updater.install = object : UpdateInstaller(exe = { null }, localAppData = { graph.appDirs.local }) {
            override fun isInstalled() = false
            override fun isDevelopment() = false
        }

        updater.update(release())

        opened shouldBe listOf("https://github.com/x/releases/tag/v9.0.0")
        server.requestCount shouldBe 0
    }

    @Test
    fun `the daily job follows the switch and stays off in development`() {
        graph.basePreferences.autoCheckAppUpdates.set(true)
        updater.periodicJob().interval() shouldBe kotlin.time.Duration.parse("24h")
        graph.basePreferences.autoCheckAppUpdates.set(false)
        updater.periodicJob().interval() shouldBe null
        graph.basePreferences.autoCheckAppUpdates.set(true)
        updater.install = object : UpdateInstaller(exe = { null }, localAppData = { graph.appDirs.local }) {
            override fun isDevelopment() = true
        }
        updater.periodicJob().interval() shouldBe null
    }
}
```

Los imports se ajustan al compilar: `launch` y `GlobalScope` como imports, y `waitUntil` y `MockWebServer` como en los tests de 5b-2. `startTestApp()` comparte el grafo en el JVM (`forkEvery = 1`), así que cada test deja el estado en `Idle` con `updater.dismiss()` en un `@AfterEach` si hace falta.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.update.*"`
Expected: FAIL de compilación (`AppUpdater`, `UpdateInstaller` y `appUpdater` no existen).

- [ ] **Step 3: Implementación**

`BasePreferences.kt`, junto a `skippedAppUpdate`:

```kotlin
    // desktop: Settings › About's switch and the daily check's last run (spec 6b §2)
    val autoCheckAppUpdates: Preference<Boolean> = preferenceStore.getBoolean("pref_auto_check_app_updates", true)

    val lastAppUpdateCheck: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("last_app_update_check"),
        0L,
    )
```

`UpdateInstaller.kt`:

```kotlin
package mihon.desktop.app.update

import java.io.File
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.Path

/**
 * Where the app runs from and how an MSI replaces it (spec 6b §3 B, C). msiexec runs once this process is gone: a
 * hidden PowerShell waits for it, so the installer never meets the app's files in use.
 */
open class UpdateInstaller(
    private val exe: () -> Path? = { ProcessHandle.current().info().command().orElse(null)?.let(::Path) },
    private val localAppData: () -> Path? = { System.getenv("LOCALAPPDATA")?.let(::Path) },
) {
    open fun isInstalled(): Boolean {
        val installed = localAppData()?.resolve("Programs")?.resolve("Turboyomi")?.resolve("Turboyomi.exe")
        // Windows paths compare without case
        return installed != null && exe()?.toString().equals(installed.toString(), ignoreCase = true)
    }

    /** Running from Gradle or an IDE: a java process instead of Turboyomi.exe. */
    open fun isDevelopment(): Boolean = !exe()?.fileName.toString().equals("Turboyomi.exe", ignoreCase = true)

    fun command(msi: File, pid: Long): List<String> {
        // A PowerShell string in single quotes: only the quote itself needs doubling
        val path = msi.path.replace("'", "''")
        val script = "Wait-Process -Id $pid -ErrorAction SilentlyContinue; " +
            "Start-Process -FilePath 'msiexec.exe' -ArgumentList '/i', '\"$path\"', '/passive', '/norestart'"
        // Encoded, so nothing in the path is read by a command line parser
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return listOf("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
    }

    open fun launch(command: List<String>) {
        ProcessBuilder(command).start()
    }
}
```

`AppUpdater.kt`:

```kotlin
package mihon.desktop.app.update

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopBuildInfo
import mihon.desktop.core.jobs.PeriodicJob
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlin.time.Duration.Companion.hours

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    data class Failed(val release: AppRelease, val message: String) : UpdateState
    data object Installing : UpdateState
}

/**
 * The app's own updates (spec 6b §2, §3): a daily or manual check, then download, SHA-256 against the release's
 * SHA256SUMS, and msiexec once the app is gone. The window shows [state]; Main exits on [UpdateState.Installing].
 */
@Inject
@SingleIn(AppScope::class)
class AppUpdater(
    private val context: Context,
    private val releases: AppReleases,
    private val network: NetworkHelper,
    private val appDirs: AppDirs,
    private val preferences: BasePreferences,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var download: Job? = null

    // Seams for tests, like BaseTracker.testInterceptor
    var apiBase: String = AppReleases.API_BASE
    var install: UpdateInstaller = UpdateInstaller()
    var openPage: (String) -> Unit = { runCatching { Desktop.getDesktop().browse(URI(it)) } }

    /** [manual]: Settings › About's button, which always reports back; the daily check stays quiet on errors. */
    suspend fun check(manual: Boolean) {
        val release = try {
            releases.latest(apiBase)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Couldn't check for app updates" }
            if (manual) AppMessages.show(e.message ?: e.toString())
            return
        }
        if (release != null && AppVersion.isNewer(release.version, DesktopBuildInfo.VERSION_NAME)) {
            _state.value = UpdateState.Available(release)
        } else {
            if (release == null) logcat(LogPriority.WARN) { "The latest release has no MSI or SHA256SUMS" }
            if (manual) AppMessages.show(context.stringResource(MR.strings.update_check_no_new_updates))
        }
    }

    fun update(release: AppRelease) {
        if (!install.isInstalled()) {
            openPage(release.page)
            _state.value = UpdateState.Idle
            return
        }
        synchronized(this) {
            if (download?.isActive == true) return
            _state.value = UpdateState.Downloading(release, null)
            download = scope.launch { downloadAndInstall(release) }
        }
    }

    fun cancel() {
        download?.cancel()
    }

    fun dismiss() {
        download?.cancel()
        _state.value = UpdateState.Idle
    }

    fun periodicJob(): PeriodicJob = PeriodicJob(
        name = "AppUpdateCheck",
        interval = { 24.hours.takeIf { preferences.autoCheckAppUpdates.get() && !install.isDevelopment() } },
        lastRun = preferences.lastAppUpdateCheck,
        run = { check(manual = false) },
    )

    private suspend fun downloadAndInstall(release: AppRelease) {
        val dir = appDirs.cache.resolve("updates").toFile().apply { mkdirs() }
        val part = File(dir, "${release.msiName}.part")
        val msi = File(dir, release.msiName)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            network.client.newCall(GET(release.msiUrl)).awaitSuccess().use { response ->
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            _state.value = UpdateState.Downloading(release, total?.let { done.toFloat() / it })
                        }
                    }
                }
            }
            val sums = network.client.newCall(GET(release.sumsUrl)).awaitSuccess().use { it.body.string() }
            val expected = expectedHash(sums, release.msiName)
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expected == null || !expected.equals(actual, ignoreCase = true)) {
                part.delete()
                _state.value = UpdateState.Failed(release, context.stringResource(MR.strings.desktop_update_corrupt))
                return
            }
            msi.delete()
            check(part.renameTo(msi)) { "Couldn't move the download into place" }
            withContext(Dispatchers.IO) { install.launch(install.command(msi, ProcessHandle.current().pid())) }
            _state.value = UpdateState.Installing
        } catch (e: CancellationException) {
            part.delete()
            _state.value = UpdateState.Available(release)
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "App update failed" }
            part.delete()
            _state.value = UpdateState.Failed(release, e.message ?: e.toString())
        }
    }

    companion object {
        /** "<hash>  <file>" or "<hash> *<file>", as sha256sum writes them in text and binary mode. */
        internal fun expectedHash(sums: String, file: String): String? = sums.lineSequence()
            .map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].removePrefix("*") == file }
            ?.first()
    }
}
```

Strings, en base y es, en la sección `desktop_` de 5b-2:

```xml
    <string name="desktop_update_corrupt">The download is damaged; try again</string>
    <string name="desktop_update_check_auto">Check for updates automatically</string>
    <string name="desktop_update_downloading">Downloading %1$s…</string>
    <string name="desktop_update_open_page">Open the release page</string>
```

```xml
    <string name="desktop_update_corrupt">La descarga está dañada; inténtalo de nuevo</string>
    <string name="desktop_update_check_auto">Buscar actualizaciones automáticamente</string>
    <string name="desktop_update_downloading">Descargando %1$s…</string>
    <string name="desktop_update_open_page">Abrir la página de la versión</string>
```

`AppGraph.kt` (desktop), junto a `backupJob`:

```kotlin
    val appUpdater: mihon.desktop.app.update.AppUpdater
```

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.update.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src i18n
git commit -m "feat(desktop-app): check, download, verify and install app updates

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: el diálogo, Acerca de y la conexión en `Main`

**Files:**
- Create:
  - `desktop-app/src/main/kotlin/mihon/desktop/app/update/UpdateDialog.kt`;
  - `desktop-app/src/main/kotlin/mihon/desktop/app/settings/AboutScreen.kt`.
- Modify:
  - `desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsTab.kt` (entrada Acerca de);
  - `desktop-app/src/main/kotlin/mihon/desktop/app/main/AppContent.kt` (diálogo);
  - `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt` (job y salida).
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/update/UpdateUiTest.kt`.

**Interfaces:**
- Consumes (Task 2): `AppUpdater.state`, `update`, `cancel`, `dismiss`, `check(manual = true)`, `periodicJob()`; `BasePreferences.autoCheckAppUpdates`.
- Produces:
  - `@Composable fun UpdateDialog(state: UpdateState, onUpdate: (AppRelease) -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit)`;
  - `object AboutScreen : SearchableSettings`.

- [ ] **Step 1: Escribir el test**

```kotlin
package mihon.desktop.app.update

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class UpdateUiTest {
    private val release = AppRelease("v9.0.0", "Arreglos varios", "https://p", "Turboyomi-9.0.0.msi", "https://m", "https://s")

    @Test
    fun `the dialog shows the version and its notes, and Download starts the update`() = runComposeUiTest {
        val graph = startTestApp()
        val updated = mutableListOf<AppRelease>()
        setContent {
            AppProviders(graph) {
                UpdateDialog(UpdateState.Available(release), onUpdate = { updated += it }, onCancel = {}, onDismiss = {})
            }
        }

        onNodeWithText("v9.0.0", substring = true).assertExists()
        onNodeWithText("Arreglos varios").assertExists()
        onNodeWithText(str(MR.strings.update_check_confirm)).performClick()
        updated shouldBe listOf(release)
    }

    @Test
    fun `a failed download says why and offers to try again`() = runComposeUiTest {
        val graph = startTestApp()
        val updated = mutableListOf<AppRelease>()
        setContent {
            AppProviders(graph) {
                UpdateDialog(UpdateState.Failed(release, "dañada"), onUpdate = { updated += it }, onCancel = {}, onDismiss = {})
            }
        }

        onNodeWithText("dañada", substring = true).assertExists()
        onNodeWithText(str(MR.strings.action_retry)).performClick()
        updated shouldBe listOf(release)
    }
}
```

Si `MR.strings.action_retry` no existe en Mihon, se usa la cadena que use Mihon para reintentar (`action_retry` está en `base/strings.xml` de Mihon; se comprueba con grep antes de escribir el test).

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.update.UpdateUiTest"`
Expected: FAIL de compilación (`UpdateDialog` no existe).

- [ ] **Step 3: Implementación**

`UpdateDialog.kt`:

```kotlin
package mihon.desktop.app.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MarkdownRender
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Mihon's NewUpdateScreen as a dialog (spec 6b §3 B): the notes, then download progress or what went wrong. */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onUpdate: (AppRelease) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Failed -> state.release
        UpdateState.Idle, UpdateState.Installing -> return
    }
    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) onDismiss() },
        title = { Text("${stringResource(MR.strings.update_check_notification_update_available)} ${release.version}") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                when (state) {
                    is UpdateState.Downloading -> {
                        Text(stringResource(MR.strings.desktop_update_downloading, release.msiName))
                        val progress = state.progress
                        if (progress == null) LinearProgressIndicator() else LinearProgressIndicator(progress = { progress })
                    }
                    is UpdateState.Failed -> Text(state.message)
                    else -> MarkdownRender(content = release.notes)
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Downloading -> Unit
                is UpdateState.Failed -> TextButton(onClick = { onUpdate(release) }) {
                    Text(stringResource(MR.strings.action_retry))
                }
                else -> TextButton(onClick = { onUpdate(release) }) {
                    Text(stringResource(MR.strings.update_check_confirm))
                }
            }
        },
        dismissButton = {
            if (state is UpdateState.Downloading) {
                TextButton(onClick = onCancel) { Text(stringResource(MR.strings.action_cancel)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_not_now)) }
            }
        },
    )
}
```

`AppContent.kt`, dentro del `Surface`, tras el `LaunchedEffect` de `AppMessages`:

```kotlin
            // desktop: the app's own updates (spec 6b §3 B), over whatever screen is open
            val update by graph.appUpdater.state.collectAsState()
            UpdateDialog(
                update,
                onUpdate = graph.appUpdater::update,
                onCancel = graph.appUpdater::cancel,
                onDismiss = graph.appUpdater::dismiss,
            )
```

`AboutScreen.kt`:

```kotlin
package mihon.desktop.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.desktop.app.update.AppReleases
import mihon.desktop.core.DesktopBuildInfo
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Mihon's About, cut to what the desktop has (spec 6b §2): version, update check, the repo and its licenses. */
object AboutScreen : SearchableSettings {
    private const val REPO_URL = "https://github.com/${AppReleases.REPO}"

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_about

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val graph = context.appGraph
        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.version),
                subtitle = "${DesktopBuildInfo.VERSION_NAME} (${DesktopBuildInfo.COMMIT_SHA})",
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.check_for_updates),
                onClick = { scope.launch { graph.appUpdater.check(manual = true) } },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = graph.basePreferences.autoCheckAppUpdates,
                title = stringResource(MR.strings.desktop_update_check_auto),
            ),
            Preference.PreferenceItem.TextPreference(
                title = "GitHub",
                subtitle = REPO_URL,
                onClick = { context.openInBrowser(REPO_URL) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.licenses),
                onClick = { context.openInBrowser("$REPO_URL/blob/master/NOTICE") },
            ),
        )
    }
}
```

Los nombres de `Preference.PreferenceItem` (`TextPreference`, `SwitchPreference` y sus parámetros) se comprueban contra `eu/kanade/presentation/more/settings/Preference.kt` del repo. Si `SwitchPreference` recibe `pref` en lugar de `preference`, se usa ese nombre.

`SettingsTab.kt`: una entrada más al final de la lista:

```kotlin
            Triple(MR.strings.pref_category_about, MaterialSymbols.Rounded.Info, AboutScreen),
```

más el import `mihon.icons.materialsymbols.rounded.Info`. Si el icono `Info` no existe en `icons/material-symbols`, se usa el icono que Mihon pone a Acerca de en `MoreScreen` y se apunta como ruling.

`Main.kt`:
- En la lista de `JobScheduler`, se añade `graph.appUpdater.periodicJob(),`.
- Dentro del contenido de `Window`, junto al `LaunchedEffect` de `bringToFront`:

```kotlin
                // desktop: msiexec waits for this process to end (spec 6b §3 B)
                LaunchedEffect(Unit) {
                    graph.appUpdater.state.first { it == UpdateState.Installing }
                    exit()
                }
```

más los imports `kotlinx.coroutines.flow.first` y `mihon.desktop.app.update.UpdateState`.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.update.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

Run: `./gradlew spotlessApply :desktop-app:compileKotlin`
Expected: compila.

- [ ] **Step 5: Commit**

```bash
git add desktop-app/src i18n
git commit -m "feat(desktop-app): the update dialog, Settings › About, and the daily update check

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: los workflows

**Files:**
- Modify: `.github/workflows/build.yml`, `.github/workflows/compat.yml`.
- Create: `.github/workflows/release.yml`, `.github/actions/setup-turboyomi/action.yml` (pasos compartidos).

**Interfaces:**
- Consumes (6a): `:desktop-app:packageMsi`, `:desktop-app:packageZip`, `-PharnessJava`, la búsqueda de WiX en `%LOCALAPPDATA%\Programs\WiX314\bin`.
- Produces:
  - el workflow `release` por tag `v*`;
  - el secreto opcional `TRACKER_KEYS`;
  - los secretos de firma opcionales `SIGNING_CERT_BASE64` y `SIGNING_CERT_PASSWORD`;
  - la variable `COMPAT_REPO_URL`.

- [ ] **Step 1: Acción compartida**

`.github/actions/setup-turboyomi/action.yml`:

```yaml
name: setup-turboyomi
description: JDK 23, Gradle, WiX 3.14 and the tracker keys, as the packaging tasks expect them (spec 6b §2)
inputs:
  tracker-keys:
    description: The tracker-keys.properties contents; empty builds without OAuth trackers
    required: false
    default: ""
runs:
  using: composite
  steps:
    - uses: actions/setup-java@v4
      with:
        distribution: temurin
        java-version: 23
    - uses: gradle/actions/setup-gradle@v4
    - name: WiX 3.14
      shell: bash
      run: |
        dir="$(cygpath "$LOCALAPPDATA")/Programs/WiX314/bin"
        mkdir -p "$dir"
        curl -sSL -o "$RUNNER_TEMP/wix.zip" https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip
        unzip -q -o "$RUNNER_TEMP/wix.zip" -d "$dir"
    - name: Tracker keys
      if: inputs.tracker-keys != ''
      shell: bash
      env:
        KEYS: ${{ inputs.tracker-keys }}
      run: printf '%s\n' "$KEYS" > tracker-keys.properties
```

- [ ] **Step 2: `build.yml`**

```yaml
name: build

on:
  push:
    branches: [master]
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: windows-latest
    timeout-minutes: 120
    defaults:
      run:
        shell: bash
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: ./.github/actions/setup-turboyomi
        with:
          tracker-keys: ${{ secrets.TRACKER_KEYS }}
      - run: bash scripts/check-licenses.sh
      - run: ./gradlew spotlessCheck test
      - run: bash scripts/check-no-android.sh
      - uses: actions/cache@v4
        with:
          path: build/cef
          key: cef-jbr-release-25.0.4b508.27
      - run: ./gradlew :desktop-core:cefTest
      - run: ./gradlew :desktop-app:packageMsi :desktop-app:packageZip
      - uses: actions/upload-artifact@v4
        with:
          name: turboyomi
          path: |
            desktop-app/build/packaging/*.msi
            desktop-app/build/packaging/*.zip
```

Los pasos que ya tenía `build.yml` después de `cefTest` se mantienen si los hay.

- [ ] **Step 3: `compat.yml`**

Se cambia:
- `java-version: 21` por la acción compartida;
- el paso `Run harness`, para empaquetar primero y usar el runtime empaquetado.

```yaml
      - uses: ./.github/actions/setup-turboyomi
      - run: ./gradlew :desktop-app:appImage
      - name: Run harness
        env:
          COMPAT_REPO_URL: ${{ vars.COMPAT_REPO_URL }}
        run: |
          java="$GITHUB_WORKSPACE/desktop-app/build/packaging/image/Turboyomi/runtime/bin/java.exe"
          ./gradlew :compat-harness:run "-PharnessJava=$java" --args="--repo $COMPAT_REPO_URL --limit 100 --threshold 0.8 --out $GITHUB_WORKSPACE/compat-report.md --cef $GITHUB_WORKSPACE/build/cef"
```

- [ ] **Step 4: `release.yml`**

```yaml
name: release

on:
  push:
    tags: ["v*"]

permissions:
  contents: write

jobs:
  release:
    runs-on: windows-latest
    timeout-minutes: 120
    defaults:
      run:
        shell: bash
    steps:
      - uses: actions/checkout@v4
        with:
          # The version comes from the tag (spec 6a §2)
          fetch-depth: 0
      - uses: ./.github/actions/setup-turboyomi
        with:
          tracker-keys: ${{ secrets.TRACKER_KEYS }}
      - run: ./gradlew spotlessCheck test
      - run: ./gradlew :desktop-app:packageMsi :desktop-app:packageZip
      - name: Sign
        # Skipped until there's a certificate (spec 6b §1)
        if: env.SIGNING_CERT_BASE64 != ''
        env:
          SIGNING_CERT_BASE64: ${{ secrets.SIGNING_CERT_BASE64 }}
          SIGNING_CERT_PASSWORD: ${{ secrets.SIGNING_CERT_PASSWORD }}
        run: |
          echo "$SIGNING_CERT_BASE64" | base64 -d > "$RUNNER_TEMP/cert.pfx"
          signtool="$(ls "/c/Program Files (x86)/Windows Kits/10/bin/"*/x64/signtool.exe | tail -1)"
          "$signtool" sign //f "$RUNNER_TEMP/cert.pfx" //p "$SIGNING_CERT_PASSWORD" //fd SHA256 //tr http://timestamp.digicert.com //td SHA256 desktop-app/build/packaging/*.msi
      - name: Checksums
        working-directory: desktop-app/build/packaging
        run: sha256sum Turboyomi-*.msi Turboyomi-*.zip > SHA256SUMS
      - name: Release
        env:
          GH_TOKEN: ${{ github.token }}
        working-directory: desktop-app/build/packaging
        run: |
          gh release create "$GITHUB_REF_NAME" --title "Turboyomi ${GITHUB_REF_NAME#v}" --generate-notes \
            Turboyomi-*.msi Turboyomi-*.zip SHA256SUMS
```

`sha256sum` escribe `<hash>  <archivo>`, el formato que lee `AppUpdater.expectedHash`.

- [ ] **Step 5: Validar la sintaxis**

Run: `python -c "import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]; print('ok')" .github/workflows/build.yml .github/workflows/compat.yml .github/workflows/release.yml .github/actions/setup-turboyomi/action.yml`
Expected: `ok`. Si falta PyYAML, se instala con `pip install pyyaml`. La validación real es en GitHub, en la Task 5.

- [ ] **Step 6: Commit**

```bash
git add .github
git commit -m "ci: JDK 23 and WiX on the runners, packages as artifacts, the release workflow

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: publicar (después del merge local en `master`)

**Files:** ninguno nuevo; si el CI pide arreglos, se hacen en `master` con su commit.

**Interfaces:**
- Consumes (Task 4): los workflows. Consumes (6a): `git describe --match "v*"`.

- [ ] **Step 1: El repo**

```bash
gh repo create rodriguezrobinj/turboyomi --public --description "Manga reader for Windows, based on Mihon"
git remote add origin https://github.com/rodriguezrobinj/turboyomi.git
git push -u origin master
gh variable set COMPAT_REPO_URL --repo rodriguezrobinj/turboyomi --body "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"
```

Si existe `tracker-keys.properties`, se ejecuta también `gh secret set TRACKER_KEYS --repo rodriguezrobinj/turboyomi < tracker-keys.properties`.

Expected: el repo existe, `master` está arriba y `git remote -v` muestra `origin` y `upstream`.

- [ ] **Step 2: `build.yml` en verde**

Run: `gh run watch --repo rodriguezrobinj/turboyomi $(gh run list --repo rodriguezrobinj/turboyomi --workflow build --limit 1 --json databaseId -q '.[0].databaseId') --exit-status`
Expected: verde, con el artefacto `turboyomi` (MSI y ZIP). Si falla, se lee el log con `gh run view --log-failed`, se arregla en `master` y se repite.

- [ ] **Step 3: La primera release**

```bash
git tag v0.1.0
git push origin v0.1.0
gh run watch --repo rodriguezrobinj/turboyomi $(gh run list --repo rodriguezrobinj/turboyomi --workflow release --limit 1 --json databaseId -q '.[0].databaseId') --exit-status
```

Expected:
- la release `v0.1.0` tiene `Turboyomi-0.1.0.msi`, `Turboyomi-0.1.0.zip` y `SHA256SUMS`;
- el paso `Sign` sale como saltado.

Run:

```bash
d=$(mktemp -d) && gh release download v0.1.0 --repo rodriguezrobinj/turboyomi --dir "$d" && (cd "$d" && sha256sum -c SHA256SUMS)
```

Expected: `Turboyomi-0.1.0.msi: OK` y `Turboyomi-0.1.0.zip: OK`.

Run: `curl -s https://api.github.com/repos/rodriguezrobinj/turboyomi/releases/latest | grep -o '"name": "[^"]*"'`
Expected: los tres assets, que es lo que lee `AppReleases`.

- [ ] **Step 4: `compat.yml` a mano**

Run: `gh workflow run compat --repo rodriguezrobinj/turboyomi`, y luego `gh run watch … --exit-status` sobre esa ejecución.
Expected: el informe en el resumen de la ejecución, con un porcentaje parecido al local (85 PASS de 100).

- [ ] **Step 5: Prueba manual (spec §5), para el usuario**

1. Instalar `Turboyomi-0.1.0.msi` desde la release.
2. Crear un tag `v0.1.1` (vale un commit vacío en `master`) y esperar a su release.
3. Abrir Turboyomi: avisa de la 0.1.1. "Descargar" descarga, la app se cierra, el MSI se instala y la biblioteca sigue.
4. Desde el ZIP descomprimido, "Descargar" abre la página de la release.
