# Subproyecto 2b: navegador integrado (Cloudflare, WebView y logins). Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un Chromium integrado, descargado bajo demanda, para que carguen las fuentes con Cloudflare, funcionen las extensiones que usan `WebView`, "Abrir en WebView" abra la página dentro de la app y los logins de las fuentes sobrevivan al reinicio.

**Architecture:**
- `desktop-core` descarga la build JCEF de JetBrains Runtime (`CefInstaller`) y la arranca (`CefRuntime`), los dos portados del `CEFManager` de Suwayomi. La publica en `CefHelper.cefApp`, de donde la toma el `WebView` de AndroidCompat, que ya existe.
- Un `CookieStore` persistente y cifrado con DPAPI sustituye al de memoria. `CefCookieSync` lo mantiene igual que el almacén de CEF.
- El `CloudflareInterceptor` de Mihon pasa a resolver desafíos en ese `WebView`.
- `desktop-app` añade el diálogo de descarga, una ventana WebView propia (CEF con ventana en un `SwingPanel`) y la sección de Ajustes.

**Tech Stack:**
- Kotlin 2.4.21, Compose Multiplatform 1.12.2.
- JCEF `144.0.15-g72717cf-chromium-144.0.7559.172-api-1.21-262-b37` (JetBrains, ya en el catálogo).
- JetBrains Runtime `jbr-release-25.0.4b508.27` (nativos).
- `jna-platform` 5.17.0 (DPAPI).
- OkHttp MockWebServer en los tests.

**Spec:** `docs/superpowers/specs/2026-10-21-subproyecto-2b-navegador-integrado-design.md`. Base: `master` en `4be1266e9`.

## Global Constraints

- Versiones fijas:

  | Componente | Versión |
  |---|---|
  | JCEF (API Java) | `144.0.15-g72717cf-chromium-144.0.7559.172-api-1.21-262-b37` |
  | JetBrains Runtime | `jbr-release-25.0.4b508.27` |
  | jna-platform | `5.17.0` |

  Nada más cambia de versión.
- Nativos: `https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4-windows-x64-b508.27.zip`, con su `.checksum` (SHA-512) en la misma URL más `.checksum`.
- Rutas:
  - instalación: `%LOCALAPPDATA%\<App>\cef` (`AppDirs.cef`);
  - caché de CEF: `%LOCALAPPDATA%\<App>\cache\cef` (`AppDirs.cefCache`);
  - cookies: `%LOCALAPPDATA%\<App>\cookies.bin` (`AppDirs.cookies`).
- El código portado de Suwayomi (MPL-2.0) lleva su cabecera MPL, igual que `extension-loader/.../vendor/*.kt`, y lo vigila `scripts/check-licenses.sh`.
- AndroidCompat se modifica solo con `// desktop: <motivo>` en la línea cambiada o justo encima.
- Lo nuevo de escritorio va en `mihon.desktop.core.*` y `mihon.desktop.app.*`.
- Nada de `android.*`/`androidx.*` en `desktop-app` fuera de lo que permite `scripts/check-no-android.sh`.
- Textos nuevos en `i18n/.../base/strings.xml` y `.../es/strings.xml`, en el bloque `<!-- Desktop -->`. `SpanishStringsTest` exige la traducción.
- Los tests con CEF real llevan `@Tag("cef")`. Corren con `./gradlew :desktop-core:cefTest`, nunca en `./gradlew test`.
- Nunca `git push` a `upstream`. Commits con el trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- Los tests `= runBlocking { }` declaran `: Unit`.
- `waitUntil` se llama con `timeoutMillis =` con nombre.
- Archivos con `\\` se escriben con la herramienta Write, nunca con heredoc de Bash.
- Antes de cada commit, `./gradlew spotlessApply` de los módulos tocados.

## Desviaciones del spec (aprobar antes de ejecutar)

1. **`.zip` en lugar de `.tar.gz`.** La release publica los dos, con 282 MB y SHA-512 cada uno. El `.zip` se extrae con `java.util.zip` y evita añadir commons-compress.
2. **Sin botón aparte "Instalar navegador integrado" en las pantallas de error.** Esas pantallas ya tienen "Abrir en WebView", que abre el diálogo de descarga si falta el navegador. El mensaje del error lo dice. Ajustes › Avanzado sí tiene "Instalar".
3. **"Ahora no" no se guarda.** El diálogo solo aparece cuando el usuario pide algo que necesita el navegador. Guardar un "no" solo ocultaría la descarga a quien la acaba de pedir.
4. **CEF se cierra desde un shutdown hook de la JVM** (como Suwayomi), no desde `ExitHooks`. Así también se cierra en el harness y en los tests, que no tienen ventana.
5. **Borrar el navegador mientras funciona:** Windows mantiene sus DLL abiertas, así que se borra en el siguiente arranque. El spec no lo trataba.
6. **Licencias:** "Licencias del navegador" abre `chrome://credits` en la ventana WebView.
7. **"Borrar cookies de este sitio" es un botón de la barra, no un menú.** Un popup de Compose se dibujaría debajo del navegador nativo.

## Review Focus

1. **Cerrar la app o cancelar durante la descarga de 282 MB:** no queda una instalación a medias, y el siguiente arranque la ve como no instalada. Tests en la Task 1 (descarga cortada, cancelar).
2. **Una página sin cookies:** su carga no espera al visitante de cookies de CEF, que solo responde cuando hay cookies. Test en la Task 3.
3. **Un archivo de cookies ilegible** (otro usuario de Windows, archivo dañado): la app arranca sin cookies y no falla. Test en la Task 2.
4. **Dos peticiones a la vez al mismo sitio con Cloudflare:** se resuelve un solo desafío en el navegador. Test en la Task 4.
5. **"Abrir en WebView" justo al arrancar, con CEF aún iniciándose:** se abre la ventana, que espera al navegador, en lugar de ofrecer la descarga. Test en la Task 5.

## Mapa de archivos

| Ruta | Responsabilidad |
|---|---|
| `desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefRelease.kt` | Versión y URL fijadas |
| `desktop-core/.../cef/CefInstaller.kt` (MPL) | Descargar, verificar, instalar y desinstalar |
| `desktop-core/.../cef/CefRuntime.kt` (MPL) | Arrancar CEF y publicarlo en `CefHelper.cefApp` |
| `desktop-core/.../cef/CefCookies.kt` | Conversión `HttpCookie` ⇄ `CefCookie` |
| `desktop-core/.../cef/CefCookieSync.kt` | Cookies entre OkHttp y CEF |
| `desktop-core/.../cef/BrowserSession.kt` | Navegador visible de la ventana WebView |
| `desktop-core/.../cookies/PersistentCookieStore.kt`, `Dpapi.kt` | Cookies persistentes cifradas |
| `desktop-core/src/testFixtures/kotlin/mihon/desktop/core/cef/FakeJbr.kt`, `ChallengeSite.kt` | Servidores falsos para los tests |
| `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/EmbeddedBrowserUnavailableException.kt` | "No instalado" o "no arrancó" |
| `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt`, `CloudflareInterceptor.kt` | Interceptores de Mihon sobre AndroidCompat |
| `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/util/system/WebViewUtil.kt` | La parte de `WebViewUtil` que usan los interceptores |
| `desktop-app/src/main/kotlin/mihon/desktop/app/browser/EmbeddedBrowser.kt`, `EmbeddedBrowserConsent.kt`, `WebViewWindow.kt` | Estado de la UI, diálogo de descarga y ventana WebView |

---

### Task 1: Instalador del navegador

**Files:**
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefRelease.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefInstaller.kt`
- Create: `desktop-core/src/testFixtures/kotlin/mihon/desktop/core/cef/FakeJbr.kt`
- Modify: `desktop-core/build.gradle.kts`
- Modify: `desktop-core/src/main/kotlin/mihon/desktop/core/AppDirs.kt`
- Modify: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopGraph.kt`, `DesktopBindings.kt`
- Modify: `scripts/check-licenses.sh`
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/cef/CefInstallerTest.kt`, `CefReleaseTest.kt`

**Interfaces:**
- Consumes: `AppDirs`; OkHttp; MockWebServer.
- Produces:
  - `object CefRelease { const val JBR_RELEASE; const val URL; const val JCEF_VERSION }`.
  - `class CefInstaller(installDir: Path, url: String = CefRelease.URL, expectedVersion: String = CefRelease.JCEF_VERSION, client: OkHttpClient = OkHttpClient())`, con:
    - `isInstalled(): Boolean`, `isRemovalPending(): Boolean` e `installedVersion(): String?`;
    - `suspend downloadSize(): Long?` y `suspend install(onProgress: (Float) -> Unit = {})`;
    - `uninstall(running: Boolean)` y `applyPendingRemoval()`.
  - `AppDirs.cef`, `AppDirs.cefCache` y `AppDirs.cookies`.
  - `DesktopGraph.cefInstaller`.
  - En los test fixtures: `class FakeJbr(version: String = CefRelease.JCEF_VERSION, extraEntry: String? = null) : Dispatcher`, con `zip`, `checksum`, `disconnect` y `slow`.

- [ ] **Step 1: Test fixtures y el servidor falso**

En `desktop-core/build.gradle.kts`, añadir el plugin `` `java-test-fixtures` `` al bloque `plugins` y, a `dependencies`:

```kotlin
    testFixturesImplementation(libs.okhttp.mockwebserver)
```

`desktop-core/src/testFixtures/kotlin/mihon/desktop/core/cef/FakeJbr.kt`:

```kotlin
package mihon.desktop.core.cef

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * JetBrains Runtime's JCEF zip in miniature, served like the real one: `/jbr.zip` (also to HEAD) and
 * `/jbr.zip.checksum` with its SHA-512. [extraEntry] adds an entry by name, such as one that climbs out of the folder.
 */
class FakeJbr(version: String = CefRelease.JCEF_VERSION, extraEntry: String? = null) : Dispatcher() {
    val zip: ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray = name.toByteArray()) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("jbr/bin/libcef.dll")
            put("jbr/bin/jcef_helper.exe")
            put("jbr/lib/icudtl.dat")
            // Random, so it doesn't compress: big enough for a slow download to be cancelled halfway
            put("jbr/lib/padding.bin", Random(1).nextBytes(64 * 1024))
            put("jbr/release", "IMPLEMENTOR=\"JetBrains s.r.o.\"\nJCEF_VERSION_DETAILED=\"$version\"\n".toByteArray())
            extraEntry?.let { put(it) }
        }
    }.toByteArray()

    @Volatile
    var checksum: String = MessageDigest.getInstance("SHA-512").digest(zip).joinToString("") { "%02x".format(it) }

    /** Drops the connection halfway through the archive. */
    @Volatile
    var disconnect = false

    /** Sends the archive at 1 KiB every 100 ms, so a test can cancel during the download. */
    @Volatile
    var slow = false

    override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
        "/jbr.zip" -> when {
            request.method == "HEAD" -> MockResponse().setHeader("Content-Length", zip.size)
            disconnect -> MockResponse().setBody(Buffer().write(zip))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            slow -> MockResponse().setBody(Buffer().write(zip)).throttleBody(1024, 100, TimeUnit.MILLISECONDS)
            else -> MockResponse().setBody(Buffer().write(zip))
        }
        "/jbr.zip.checksum" -> MockResponse().setBody("$checksum  jbr.zip\n")
        else -> MockResponse().setResponseCode(404)
    }
}
```

- [ ] **Step 2: Escribir los tests (fallan: no existen)**

`desktop-core/src/test/kotlin/mihon/desktop/core/cef/CefInstallerTest.kt`:

```kotlin
package mihon.desktop.core.cef

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalPathApi::class)
class CefInstallerTest {
    private val server = MockWebServer()
    private lateinit var root: Path

    @BeforeEach
    fun setUp() {
        server.start()
        root = createTempDirectory("cef José")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        root.deleteRecursively()
    }

    private fun installer(fake: FakeJbr): CefInstaller {
        server.dispatcher = fake
        return CefInstaller(root.resolve("cef"), url = server.url("/jbr.zip").toString())
    }

    // Whatever install left next to the install folder: its staging folder must be gone
    private fun leftovers() = root.listDirectoryEntries().map { it.name }.filter { it != "cef" }

    @Test
    fun `installs the browser, and then it counts as installed`(): Unit = runBlocking {
        val installer = installer(FakeJbr())
        val progress = mutableListOf<Float>()

        installer.install { progress += it }

        installer.isInstalled() shouldBe true
        root.resolve("cef/libcef.dll").exists() shouldBe true
        root.resolve("cef/icudtl.dat").exists() shouldBe true
        installer.installedVersion() shouldBe CefRelease.JCEF_VERSION
        progress.last() shouldBe 1f
        progress.zipWithNext().all { (a, b) -> b >= a } shouldBe true
        leftovers().shouldBeEmpty()
    }

    @Test
    fun `a download that doesn't match its checksum installs nothing`(): Unit = runBlocking {
        val installer = installer(FakeJbr().apply { checksum = "0".repeat(128) })

        shouldThrow<IOException> { installer.install() }

        installer.isInstalled() shouldBe false
        root.resolve("cef").exists() shouldBe false
        leftovers().shouldBeEmpty()
    }

    @Test
    fun `an entry that climbs out of the folder is refused`(): Unit = runBlocking {
        val installer = installer(FakeJbr(extraEntry = "../../evil.txt"))

        shouldThrow<IOException> { installer.install() }

        root.resolve("evil.txt").exists() shouldBe false
        installer.isInstalled() shouldBe false
    }

    @Test
    fun `a download cut halfway leaves it not installed`(): Unit = runBlocking {
        val installer = installer(FakeJbr().apply { disconnect = true })

        shouldThrow<IOException> { installer.install() }

        installer.isInstalled() shouldBe false
        leftovers().shouldBeEmpty()
    }

    @Test
    fun `a build of another JCEF version isn't installed`(): Unit = runBlocking {
        val installer = installer(FakeJbr(version = "143.0.1-gabcdef1-chromium-143.0.7499.1-api-1.20-1-b1"))

        shouldThrow<IOException> { installer.install() }

        installer.isInstalled() shouldBe false
    }

    @Test
    fun `an install whose release file declares another version doesn't count`() {
        root.resolve("cef").createDirectories().resolve("release")
            .writeText("JCEF_VERSION_DETAILED=\"143.0.1-gabcdef1-chromium-143.0.7499.1\"\n")

        installer(FakeJbr()).isInstalled() shouldBe false
    }

    @Test
    fun `cancelling during the download leaves nothing behind`(): Unit = runBlocking {
        val installer = installer(FakeJbr().apply { slow = true })
        val downloading = CompletableDeferred<Unit>()

        val job = launch(Dispatchers.IO) { installer.install { if (it > 0f) downloading.complete(Unit) } }
        withTimeout(10.seconds) { downloading.await() }
        job.cancelAndJoin()

        installer.isInstalled() shouldBe false
        leftovers().shouldBeEmpty()
    }

    @Test
    fun `removing it while it runs waits for the next start`(): Unit = runBlocking {
        val installer = installer(FakeJbr())
        installer.install()

        installer.uninstall(running = true)
        installer.isInstalled() shouldBe false
        installer.isRemovalPending() shouldBe true
        root.resolve("cef").exists() shouldBe true

        installer.applyPendingRemoval()
        root.resolve("cef").exists() shouldBe false
        installer.isRemovalPending() shouldBe false
    }

    @Test
    fun `removing it when it doesn't run deletes it at once`(): Unit = runBlocking {
        val installer = installer(FakeJbr())
        installer.install()

        installer.uninstall(running = false)

        root.resolve("cef").exists() shouldBe false
        installer.isRemovalPending() shouldBe false
    }

    @Test
    fun `the download size comes from the server`(): Unit = runBlocking {
        val fake = FakeJbr()
        installer(fake).downloadSize() shouldBe fake.zip.size.toLong()
    }
}
```

`desktop-core/src/test/kotlin/mihon/desktop/core/cef/CefReleaseTest.kt`:

```kotlin
package mihon.desktop.core.cef

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

class CefReleaseTest {
    @Test
    fun `the native build is the one of the jcef library the app compiles against`() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val jcef = Regex("""^jcef = "([^"]+)"""", RegexOption.MULTILINE).find(catalog)!!.groupValues[1]

        CefRelease.JCEF_VERSION shouldBe jcef
    }
}
```

- [ ] **Step 3: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.cef.*"`
Expected: FAIL de compilación: `Unresolved reference 'CefInstaller'` y `'CefRelease'`.

- [ ] **Step 4: Implementación**

`desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefRelease.kt`:

```kotlin
package mihon.desktop.core.cef

/** The embedded browser's build (spec 2b §2): JetBrains Runtime's JCEF, the release Suwayomi pins for this JCEF. */
object CefRelease {
    const val JBR_RELEASE = "jbr-release-25.0.4b508.27"
    const val URL = "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4-windows-x64-b508.27.zip"

    /** The jcef library in gradle/libs.versions.toml: the natives must be its build (CefReleaseTest checks it). */
    const val JCEF_VERSION = "144.0.15-g72717cf-chromium-144.0.7559.172-api-1.21-262-b37"
}
```

`desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefInstaller.kt`:

```kotlin
package mihon.desktop.core.cef

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

/**
 * The embedded browser's files (spec 2b §2, §4): JetBrains Runtime's JCEF build in [installDir], downloaded on demand.
 *
 * Ported from Suwayomi's CEFManager, with a fixed [url] instead of the GitHub API, the .zip read with java.util.zip
 * instead of the .tar.gz, a SHA-512 check, and an install that only exists once complete: it's built in a folder
 * next to [installDir] and renamed at the end.
 */
@OptIn(ExperimentalPathApi::class)
class CefInstaller(
    private val installDir: Path,
    private val url: String = CefRelease.URL,
    private val expectedVersion: String = CefRelease.JCEF_VERSION,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val removalMarker: Path get() = installDir.resolveSibling("${installDir.name}.remove")

    /** Whether the build the app expects is installed, and not waiting to be removed. */
    fun isInstalled(): Boolean =
        !isRemovalPending() && installedVersion()?.let { sameBuild(it, expectedVersion) } == true

    fun isRemovalPending(): Boolean = removalMarker.exists()

    /** The JCEF version the installed build declares; null when there's none. */
    fun installedVersion(): String? = releaseVersion(installDir)

    /** Bytes the download takes, for the consent dialog; null when the server doesn't say. */
    suspend fun downloadSize(): Long? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
            response.header("Content-Length")?.toLongOrNull()?.takeIf { response.isSuccessful }
        }
    }

    /** Downloads, checks and installs the build, reporting 0..1 to [onProgress]. Cancellable; nothing is left half-done. */
    suspend fun install(onProgress: (Float) -> Unit = {}): Unit = withContext(Dispatchers.IO) {
        val parent = installDir.toAbsolutePath().parent.createDirectories()
        val staging = createTempDirectory(parent, "${installDir.name}-install")
        try {
            val checksum = fetchChecksum()
            val archive = staging / "jbr.zip"
            if (!download(archive, onProgress).equals(checksum, ignoreCase = true)) {
                throw IOException("The browser download is damaged: its SHA-512 doesn't match")
            }
            val layout = flatten(extract(archive, staging / "extracted"), staging / "layout")
            val version = releaseVersion(layout)
            if (version == null || !sameBuild(version, expectedVersion)) {
                throw IOException("The downloaded browser is version $version; the app needs $expectedVersion")
            }
            installDir.deleteRecursively()
            layout.moveTo(installDir, StandardCopyOption.ATOMIC_MOVE)
            removalMarker.deleteIfExists()
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Removes the browser. While it runs Windows keeps its DLLs open, so then it goes at the next start. */
    fun uninstall(running: Boolean) {
        if (running) {
            if (!removalMarker.exists()) removalMarker.createFile()
        } else {
            installDir.deleteRecursively()
            removalMarker.deleteIfExists()
        }
    }

    /** At startup, before anything loads the browser: finishes a removal asked for while it ran. */
    fun applyPendingRemoval() {
        if (removalMarker.exists()) uninstall(running = false)
    }

    private fun fetchChecksum(): String =
        client.newCall(Request.Builder().url("$url.checksum").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} getting the browser's checksum")
            response.body.string().trim().substringBefore(' ')
        }

    /** Writes the archive to [target] and returns its SHA-512. */
    private suspend fun download(target: Path, onProgress: (Float) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-512")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading the browser")
            val total = response.body.contentLength()
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        }
        onProgress(1f)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extract(archive: Path, target: Path): Path {
        val root = target.createDirectories().toAbsolutePath().normalize()
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val file = root.resolve(entry.name).normalize()
                if (!file.startsWith(root)) throw IOException("The browser archive has an unsafe path: ${entry.name}")
                if (entry.isDirectory) {
                    file.createDirectories()
                } else {
                    file.parent.createDirectories()
                    file.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        return root
    }

    /** JBR's folders to the layout JCefAppConfig loads: bin's files and lib's contents side by side, plus `release`. */
    private fun flatten(extracted: Path, layout: Path): Path {
        val jbr = extracted.listDirectoryEntries().firstOrNull { (it / "lib").isDirectory() }
            ?: throw IOException("The browser archive doesn't have the expected folders")
        layout.createDirectories()
        (jbr / "lib").listDirectoryEntries().forEach { it.moveTo(layout / it.name) }
        (jbr / "bin").takeIf { it.isDirectory() }?.listDirectoryEntries()?.forEach { it.moveTo(layout / it.name) }
        (jbr / "release").takeIf { it.exists() }?.moveTo(layout / "release")
        return layout
    }
}

private const val BUFFER_SIZE = 1 shl 16

/** The JCEF version a `release` file declares (`JCEF_VERSION_DETAILED`), without its quotes. */
private fun releaseVersion(dir: Path): String? = runCatching {
    (dir / "release").readLines().firstNotNullOfOrNull { line ->
        line.substringAfter("JCEF_VERSION_DETAILED=", "").trim().trim('"').takeIf { it.isNotEmpty() }
    }
}.getOrNull()

/** The same native build: the part before `-chromium` (JCEF's version and commit), as Suwayomi compares them. */
private fun sameBuild(installed: String, expected: String) =
    installed.substringBefore("-chromium") == expected.substringBefore("-chromium")
```

En `AppDirs`, añadir junto a `logs`:

```kotlin
    /** The embedded browser (spec 2b §2): its files, its cache, and the app's cookies, which only this machine reads. */
    val cef: Path get() = local.resolve("cef")
    val cefCache: Path get() = cache.resolve("cef")
    val cookies: Path get() = local.resolve("cookies.bin")
```

En `DesktopGraph`, añadir `val cefInstaller: CefInstaller` (import `mihon.desktop.core.cef.CefInstaller`). En `DesktopBindings`, añadir:

```kotlin
    @Provides
    @SingleIn(AppScope::class)
    fun providesCefInstaller(appDirs: AppDirs): CefInstaller = CefInstaller(appDirs.cef)
```

En `scripts/check-licenses.sh`, después del bucle de `extension-loader`:

```bash
for file in desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefInstaller.kt \
  desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefRuntime.kt; do
  [[ -f "$file" ]] || continue
  grep -q "Mozilla Public" "$file" || { echo "missing MPL header: $file"; status=1; }
done
```

`CefRuntime.kt` llega en la Task 3; mientras no exista, el `continue` lo salta.

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.cef.*"`
Expected: PASS (10 + 1 tests).

Run: `bash scripts/check-licenses.sh`
Expected: código 0.

- [ ] **Step 6: Commit**

```bash
./gradlew :desktop-core:spotlessApply
git add desktop-core scripts/check-licenses.sh
git commit -m "feat(desktop-core): installer for the embedded browser (JetBrains Runtime JCEF)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Cookies persistentes y cifradas

**Files:**
- Modify: `gradle/libs.versions.toml`, `desktop-core/build.gradle.kts`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/cookies/Dpapi.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/cookies/PersistentCookieStore.kt`
- Modify: `desktop-core/src/main/kotlin/mihon/desktop/core/Platform.kt`
- Modify: `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/NetworkHelper.kt` (comentario)
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/cookies/PersistentCookieStoreTest.kt`
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/PlatformTest.kt` (un caso nuevo)

**Interfaces:**
- Consumes: `AppDirs.cookies` (Task 1).
- Produces:
  - `class PersistentCookieStore(file: Path, protect: (ByteArray) -> ByteArray = Dpapi::protect, unprotect: (ByteArray) -> ByteArray = Dpapi::unprotect, now: () -> Long = System::currentTimeMillis) : CookieStore`, con:
    - `all(): List<Pair<URI?, HttpCookie>>`;
    - `flush()`.
  - `object Dpapi { fun protect(ByteArray): ByteArray; fun unprotect(ByteArray): ByteArray }`.
  - Tras `Platform.start`, `CookieHandler.getDefault()` es un `java.net.CookieManager` sobre un `PersistentCookieStore`.

- [ ] **Step 1: Escribir los tests (fallan)**

`desktop-core/src/test/kotlin/mihon/desktop/core/cookies/PersistentCookieStoreTest.kt`:

```kotlin
package mihon.desktop.core.cookies

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.net.HttpCookie
import java.net.URI
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

class PersistentCookieStoreTest {
    private val file = createTempDirectory("cookies José").resolve("cookies.bin")
    private val site = URI("https://example.com/")

    private fun cookie(name: String, value: String = "valor", maxAge: Long = 3600) =
        HttpCookie(name, value).apply {
            path = "/"
            this.maxAge = maxAge
        }

    @Test
    fun `cookies survive to a new store on the same file`() {
        PersistentCookieStore(file).apply {
            add(site, cookie("sesion"))
            // A session cookie (no expiry) also keeps a login across restarts
            add(site, cookie("login", maxAge = -1))
            flush()
        }

        val names = PersistentCookieStore(file).get(URI("https://example.com/manga/1")).map { it.name }

        names shouldContain "sesion"
        names shouldContain "login"
    }

    @Test
    fun `the file doesn't hold the cookie's value in clear`() {
        PersistentCookieStore(file).apply {
            add(site, cookie("sesion", value = "secreto-de-José"))
            flush()
        }

        String(file.readBytes(), Charsets.ISO_8859_1) shouldNotContain "secreto"
    }

    @Test
    fun `expired cookies are dropped when the file is read`() {
        var now = 1_000_000L
        PersistentCookieStore(file, now = { now }).apply {
            add(site, cookie("corta", maxAge = 10))
            flush()
        }

        now += 20_000
        PersistentCookieStore(file, now = { now }).cookies.shouldBeEmpty()
    }

    @Test
    fun `an unreadable file starts with no cookies instead of failing`() {
        file.parent.toFile().mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        val store = PersistentCookieStore(file)
        store.cookies.shouldBeEmpty()

        // And it's replaced with a good one on the next change
        store.add(site, cookie("nueva"))
        store.flush()
        PersistentCookieStore(file).cookies.map { it.name } shouldBe listOf("nueva")
    }

    @Test
    fun `removing all cookies empties the file too`() {
        PersistentCookieStore(file).apply {
            add(site, cookie("sesion"))
            flush()
            removeAll()
            flush()
        }

        PersistentCookieStore(file).cookies.shouldBeEmpty()
    }

    @Test
    fun `all lists each cookie with the address it came from`() {
        val store = PersistentCookieStore(file)
        store.add(site, cookie("sesion"))

        store.all().map { (uri, cookie) -> uri to cookie.name } shouldBe listOf(site to "sesion")
    }
}
```

En `PlatformTest`, añadir:

```kotlin
    @Test
    fun `the app's cookies are kept in the persistent store`() {
        (CookieHandler.getDefault() as java.net.CookieManager).cookieStore
            .shouldBeInstanceOf<PersistentCookieStore>()
    }
```

Imports: `io.kotest.matchers.types.shouldBeInstanceOf`, `mihon.desktop.core.cookies.PersistentCookieStore` y `java.net.CookieHandler`. Si `PlatformTest` no arranca ya la plataforma en un `@BeforeAll`, el caso empieza con `Platform.start(AppDirs(root.resolve("data"), root.resolve("local")))` sobre un `createTempDirectory()`, como `WebViewUnavailableTest`.

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.cookies.*" --tests "mihon.desktop.core.PlatformTest"`
Expected: FAIL de compilación: `Unresolved reference 'PersistentCookieStore'`.

- [ ] **Step 3: Implementación**

En `gradle/libs.versions.toml`: en `[versions]`, `jna = "5.17.0"`; en `[libraries]`, `jna-platform = { module = "net.java.dev.jna:jna-platform", version.ref = "jna" }`. En `desktop-core/build.gradle.kts`, `implementation(libs.jna.platform)`.

`desktop-core/src/main/kotlin/mihon/desktop/core/cookies/Dpapi.kt`:

```kotlin
package mihon.desktop.core.cookies

import com.sun.jna.platform.win32.Crypt32Util

/** Windows' DPAPI: what it protects only the same Windows user can read (spec 2b §4, the cookie file). */
object Dpapi {
    fun protect(data: ByteArray): ByteArray = Crypt32Util.cryptProtectData(data)

    fun unprotect(data: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(data)
}
```

`desktop-core/src/main/kotlin/mihon/desktop/core/cookies/PersistentCookieStore.kt`:

```kotlin
package mihon.desktop.core.cookies

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.net.CookieManager
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * The app's cookies, kept across restarts in [file] (spec 2b §2): logins and Cloudflare clearances survive closing
 * the app. Lookups are the JDK's in-memory store; every change is written, encrypted with [protect], shortly after.
 */
class PersistentCookieStore(
    private val file: Path,
    private val protect: (ByteArray) -> ByteArray = Dpapi::protect,
    private val unprotect: (ByteArray) -> ByteArray = Dpapi::unprotect,
    private val now: () -> Long = System::currentTimeMillis,
) : CookieStore {
    private val memory: CookieStore = CookieManager().cookieStore
    private val entries = ConcurrentHashMap<Key, Entry>()
    private val writer = Executors.newSingleThreadScheduledExecutor { Thread(it, "cookie-store").apply { isDaemon = true } }
    private val saveScheduled = AtomicBoolean(false)

    init {
        load()
    }

    override fun add(uri: URI?, cookie: HttpCookie) {
        memory.add(uri, cookie)
        // A max age of 0 deletes it, as in the JDK's store
        if (cookie.maxAge == 0L) entries.remove(Key(cookie)) else entries[Key(cookie)] = Entry(uri, cookie, expiresAt(cookie))
        scheduleSave()
    }

    override fun get(uri: URI): List<HttpCookie> = memory.get(uri)

    override fun getCookies(): List<HttpCookie> = memory.cookies

    override fun getURIs(): List<URI> = memory.getURIs()

    override fun remove(uri: URI?, cookie: HttpCookie): Boolean = memory.remove(uri, cookie).also {
        entries.remove(Key(cookie))
        scheduleSave()
    }

    override fun removeAll(): Boolean = memory.removeAll().also {
        entries.clear()
        scheduleSave()
    }

    /** Every unexpired cookie with the address it came from, to copy them into the embedded browser. */
    fun all(): List<Pair<URI?, HttpCookie>> = live().map { it.uri to it.cookie }

    /** Writes pending changes now: on exit, and in tests. */
    fun flush() {
        saveScheduled.set(false)
        save()
    }

    private fun live() = entries.values.filter { it.expiresAt == null || it.expiresAt > now() }

    private fun expiresAt(cookie: HttpCookie): Long? = cookie.maxAge.takeIf { it > 0 }?.let { now() + it * 1000 }

    // Many changes in a row (a page setting a dozen cookies) are one write
    private fun scheduleSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            writer.schedule({ if (saveScheduled.compareAndSet(true, false)) save() }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    private fun save() {
        try {
            val bytes = protect(Json.encodeToString(live().map { it.toStored() }).toByteArray())
            file.parent.createDirectories()
            val temp = file.resolveSibling("${file.name}.tmp")
            temp.writeBytes(bytes)
            temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Couldn't save the cookies" }
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            Json.decodeFromString<List<Stored>>(String(unprotect(file.readBytes()))).forEach { stored ->
                if (stored.expiresAt != null && stored.expiresAt <= now()) return@forEach
                val uri = stored.uri?.let(::URI)
                val cookie = stored.toCookie()
                memory.add(uri, cookie)
                entries[Key(cookie)] = Entry(uri, cookie, stored.expiresAt)
            }
        } catch (e: Exception) {
            // Another Windows user's file, or a damaged one: no cookies is better than no app
            logcat(LogPriority.ERROR, e) { "Couldn't read the saved cookies; starting without them" }
        }
    }

    private data class Key(val name: String, val domain: String?, val path: String?) {
        constructor(cookie: HttpCookie) : this(cookie.name, cookie.domain?.lowercase(), cookie.path)
    }

    private class Entry(val uri: URI?, val cookie: HttpCookie, val expiresAt: Long?)

    @Serializable
    private data class Stored(
        val uri: String?,
        val name: String,
        val value: String,
        val domain: String?,
        val path: String?,
        val expiresAt: Long?,
        val secure: Boolean,
        val httpOnly: Boolean,
        val version: Int,
    )

    private fun Entry.toStored() = Stored(
        uri = uri?.toString(),
        name = cookie.name,
        value = cookie.value,
        domain = cookie.domain,
        path = cookie.path,
        expiresAt = expiresAt,
        secure = cookie.secure,
        httpOnly = cookie.isHttpOnly,
        version = cookie.version,
    )

    private fun Stored.toCookie() = HttpCookie(name, value).also {
        it.domain = domain
        it.path = path
        it.secure = secure
        it.isHttpOnly = httpOnly
        it.version = version
        it.maxAge = expiresAt?.let { at -> (at - now()) / 1000 } ?: -1
    }

    private companion object {
        const val SAVE_DELAY_MS = 500L
    }
}
```

En `Platform.start`, justo antes de `startKoin { ... }`:

```kotlin
        // Logins and Cloudflare clearances survive restarts (spec 2b §2). NetworkHelper keeps this handler
        val cookies = PersistentCookieStore(dirs.cookies)
        CookieHandler.setDefault(java.net.CookieManager(cookies, CookiePolicy.ACCEPT_ALL))
        Runtime.getRuntime().addShutdownHook(thread(start = false, name = "cookie-flush") { cookies.flush() })
```

Imports: `mihon.desktop.core.cookies.PersistentCookieStore`, `java.net.CookieHandler`, `java.net.CookiePolicy` y `kotlin.concurrent.thread`.

En `NetworkHelper` de escritorio, el comentario del `init` pasa a ser:

```kotlin
        // AndroidCompat's android.webkit.CookieManager stores cookies in the JVM-wide CookieHandler, which
        // Platform.start sets to the app's persistent store. This fallback only serves code that skips Platform.
```

Borrar también la palabra `ponytail` de ese comentario y de la KDoc de la clase si solo hablaba de las cookies en memoria.

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.cookies.*" --tests "mihon.desktop.core.PlatformTest"`
Expected: PASS.

Run: `./gradlew :desktop-core:test :desktop-app:test`
Expected: PASS. Las cookies de los tests van a sus carpetas temporales.

- [ ] **Step 5: Commit**

```bash
./gradlew :desktop-core:spotlessApply :core:common:spotlessApply
git add gradle/libs.versions.toml desktop-core core/common
git commit -m "feat(desktop-core): cookies kept across restarts, encrypted with DPAPI

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Arranque de CEF y cookies compartidas

**Files:**
- Create: `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/EmbeddedBrowserUnavailableException.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefRuntime.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefCookies.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefCookieSync.kt`
- Modify: `android-compat/src/main/java/xyz/nulldev/androidcompat/webkit/KcefWebViewProvider.kt`
- Modify: `desktop-core/src/main/kotlin/mihon/desktop/core/Platform.kt`
- Modify: `desktop-core/build.gradle.kts` (tarea `cefTest`)
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/cef/CefCookiesTest.kt`
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/WebViewUnavailableTest.kt` (actualizar)
- Test (`cef`): `desktop-core/src/test/kotlin/mihon/desktop/core/cef/RealCef.kt`, `CefRuntimeTest.kt`, `WebViewCookiesTest.kt`

**Interfaces:**
- Consumes: `CefInstaller`, `AppDirs.cef`, `AppDirs.cefCache` (Task 1); `PersistentCookieStore` (Task 2); `CefHelper` y `KcefWebViewProvider` (AndroidCompat).
- Produces:
  - `class EmbeddedBrowserUnavailableException(reason: Reason, cause: Throwable? = null) : IOException`, con `enum class Reason { NOT_INSTALLED, NOT_STARTED }`.
  - `object CefRuntime { val isRunning; fun markNotInstalled(); fun start(installDir: Path, cacheDir: Path); fun startAsync(installDir: Path, cacheDir: Path) }`.
  - `HttpCookie.toCefCookie(now)`, `CefCookie.toHttpCookie(now)`, `cookieUrl(uri, cookie)` y `CefCookie.url()`.
  - `object CefCookieSync : KcefWebViewProvider.InitBrowserHandler`, con:
    - `pushAll()`;
    - `pull(url: String, wait: Boolean = false)` y `pullAll()`;
    - `clear(url: String?)`.
  - `KcefWebViewProvider.InitBrowserHandler.onLoadEnd(url: String)`, con implementación vacía por defecto.
  - La tarea `./gradlew :desktop-core:cefTest`, que instala CEF en `build/cef` en la raíz del repo.
  - En los tests: `fun startWithRealCef(): DesktopGraph`.

- [ ] **Step 1: Escribir los tests sin CEF (fallan)**

`desktop-core/src/test/kotlin/mihon/desktop/core/cef/CefCookiesTest.kt`:

```kotlin
package mihon.desktop.core.cef

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.HttpCookie
import java.net.URI
import java.util.Date

class CefCookiesTest {
    private val now = Date(1_700_000_000_000)

    @Test
    fun `a domain cookie goes to CEF and back unchanged`() {
        val cookie = HttpCookie("sesion", "José").apply {
            domain = ".example.com"
            path = "/manga"
            secure = true
            isHttpOnly = true
            maxAge = 3600
        }

        val cef = cookie.toCefCookie(now)
        cef.domain shouldBe ".example.com"
        cef.hasExpires shouldBe true
        cef.expires shouldBe Date(now.time + 3_600_000)

        val back = cef.toHttpCookie(now)
        back.name shouldBe "sesion"
        back.value shouldBe "José"
        back.domain shouldBe ".example.com"
        back.path shouldBe "/manga"
        back.secure shouldBe true
        back.isHttpOnly shouldBe true
        back.maxAge shouldBe 3600
    }

    @Test
    fun `a host cookie with no expiry stays a host session cookie`() {
        val cef = HttpCookie("login", "si").apply { path = "/" }.toCefCookie(now)

        cef.domain shouldBe ""
        cef.hasExpires shouldBe false
        cef.toHttpCookie(now).maxAge shouldBe -1
    }

    @Test
    fun `the address CEF sets a cookie for comes from its domain, or the page it came from`() {
        val secure = HttpCookie("a", "1").apply {
            domain = ".example.com"
            secure = true
        }
        val host = HttpCookie("b", "2").apply { path = "/m" }

        cookieUrl(URI("http://example.com/x"), secure) shouldBe "https://example.com/"
        cookieUrl(URI("http://example.com/x"), host) shouldBe "http://example.com/m"
    }
}
```

En `WebViewUnavailableTest`, el comentario y las dos aserciones del test pasan a ser:

```kotlin
        // cubari and mangaup build a WebView on the main thread; without the embedded browser it used to wait forever
        // there, freezing the looper every other extension posts to
        val created = withTimeout(5_000) { onMain { WebView(graph.context) }.await() }
        created.isFailure shouldBe true
        created.exceptionOrNull()!!.stackTraceToString() shouldContain "EmbeddedBrowserUnavailableException"
```

En `desktop-core/build.gradle.kts`, añadir `testImplementation(libs.jcef)` a `dependencies`. Los tests construyen `CefCookie`, que no carga nativos.

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.cef.CefCookiesTest" --tests "mihon.desktop.core.WebViewUnavailableTest"`
Expected: FAIL de compilación: `Unresolved reference 'toCefCookie'`.

- [ ] **Step 3: Excepción, conversión, sincronización y arranque**

`core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/EmbeddedBrowserUnavailableException.kt`:

```kotlin
package eu.kanade.tachiyomi.network

import java.io.IOException

/**
 * The embedded browser that WebViews and Cloudflare challenges need isn't there or didn't start (spec 2b §3 A).
 * An IOException, so OkHttp hands it on to the screen that made the request.
 */
class EmbeddedBrowserUnavailableException(
    val reason: Reason,
    cause: Throwable? = null,
) : IOException(reason.description, cause) {
    enum class Reason(val description: String) {
        NOT_INSTALLED("The embedded browser isn't installed"),
        NOT_STARTED("The embedded browser didn't start"),
    }
}
```

`desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefCookies.kt`:

```kotlin
package mihon.desktop.core.cef

import org.cef.network.CefCookie
import java.net.HttpCookie
import java.net.URI
import java.util.Date

/** A cookie of the app's store as CEF takes it; one without a domain is a host cookie (CEF: an empty domain). */
fun HttpCookie.toCefCookie(now: Date = Date()): CefCookie {
    val expires = if (maxAge >= 0) Date(now.time + maxAge * 1000) else null
    return CefCookie(name, value, domain.orEmpty(), path ?: "/", secure, isHttpOnly, now, now, expires != null, expires)
}

/** A CEF cookie for the app's store; one without an expiry stays a session cookie. */
fun CefCookie.toHttpCookie(now: Date = Date()): HttpCookie = HttpCookie(name, value).also {
    it.domain = domain.takeIf(String::isNotEmpty)
    it.path = path
    it.secure = secure
    it.isHttpOnly = httponly
    it.maxAge = if (hasExpires && expires != null) maxOf(0L, (expires.time - now.time) / 1000) else -1L
}

/** The address CEF's setCookie wants for [cookie], which came from [uri]: its scheme, and its domain or host. */
fun cookieUrl(uri: URI?, cookie: HttpCookie): String {
    val scheme = if (cookie.secure) "https" else uri?.scheme ?: "https"
    val host = cookie.domain?.trimStart('.') ?: uri?.host.orEmpty()
    return "$scheme://$host${cookie.path ?: "/"}"
}

/** The address a CEF cookie belongs to, for the app's store (which matches cookies by domain). */
fun CefCookie.url(): URI = URI(if (secure) "https" else "http", domain.trimStart('.'), path.ifEmpty { "/" }, null)
```

`desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefCookieSync.kt`:

```kotlin
package mihon.desktop.core.cef

import mihon.desktop.core.cookies.PersistentCookieStore
import org.cef.network.CefCookieManager
import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider
import java.net.CookieHandler
import java.net.CookieManager
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The app's cookies (OkHttp's store) and the embedded browser's, kept the same (spec 2b §2): into CEF before a
 * browser loads, back after every page. AndroidCompat's WebView calls it as its [KcefWebViewProvider.InitBrowserHandler].
 */
object CefCookieSync : KcefWebViewProvider.InitBrowserHandler {
    private val store: PersistentCookieStore?
        get() = (CookieHandler.getDefault() as? CookieManager)?.cookieStore as? PersistentCookieStore

    private val cef: CefCookieManager? get() = if (CefRuntime.isRunning) CefCookieManager.getGlobalManager() else null

    override fun init(provider: KcefWebViewProvider) = pushAll()

    /** Before onPageFinished: a client checking cookies there (the Cloudflare interceptor) must see the page's. */
    override fun onLoadEnd(url: String) = pull(url, wait = true)

    /** Every cookie of the app into CEF. */
    fun pushAll() {
        val cef = cef ?: return
        store?.all()?.forEach { (uri, cookie) -> cef.setCookie(cookieUrl(uri, cookie), cookie.toCefCookie()) }
    }

    /**
     * CEF's cookies for [url] into the app's store. With [wait], for a short while: CEF only calls the visitor when
     * there are cookies, so on a page without them the wait runs out instead of holding the page.
     */
    fun pull(url: String, wait: Boolean = false) {
        val cef = cef ?: return
        val store = store ?: return
        val uri = runCatching { URI(url) }.getOrNull() ?: return
        val done = CountDownLatch(1)
        val visiting = cef.visitUrlCookies(url, true) { cookie, count, total, _ ->
            store.add(uri, cookie.toHttpCookie())
            if (count + 1 >= total) done.countDown()
            true
        }
        if (visiting && wait) done.await(PULL_WAIT_MS, TimeUnit.MILLISECONDS)
    }

    /** All of CEF's cookies into the app's store, when the WebView window closes. */
    fun pullAll() {
        val cef = cef ?: return
        val store = store ?: return
        cef.visitAllCookies { cookie, _, _, _ ->
            store.add(cookie.url(), cookie.toHttpCookie())
            true
        }
    }

    /** Deletes the cookies of [url]'s site, or all of them with null, in both stores ("Borrar cookies", spec 2b §4). */
    fun clear(url: String?) {
        val store = store
        if (url == null) {
            store?.removeAll()
            cef?.deleteCookies("", "")
        } else {
            val uri = runCatching { URI(url) }.getOrNull() ?: return
            store?.get(uri)?.forEach { store.remove(uri, it) }
            cef?.deleteCookies(url, "")
        }
    }

    // ponytail: a fixed short wait, since CEF has no "no cookies" callback; enough for one page's cookies
    private const val PULL_WAIT_MS = 300L
}
```

`desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefRuntime.kt`:

```kotlin
package mihon.desktop.core.cef

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.jetbrains.cef.JCefAppConfig
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException.Reason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import org.cef.CefApp
import org.cef.SystemBootstrap
import tachiyomi.core.common.util.system.logcat
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

/**
 * Starts the embedded browser (spec 2b §4) and publishes it in [CefHelper.cefApp], where AndroidCompat's WebView and
 * the WebView window wait for it. Ported from Suwayomi's CEFManager, Windows only.
 */
object CefRuntime {
    val isRunning: Boolean get() = CefHelper.cefApp.value.getOrNull() != null

    /** Until the browser runs, WebViews fail fast with this instead of waiting forever on the main looper. */
    fun markNotInstalled() {
        CefHelper.cefApp.value = Result.failure(EmbeddedBrowserUnavailableException(Reason.NOT_INSTALLED))
    }

    /** At app launch and after the download: on its own thread, it loads big DLLs. */
    fun startAsync(installDir: Path, cacheDir: Path) {
        thread(name = "cef-start", isDaemon = true) { start(installDir, cacheDir) }
    }

    @Synchronized
    fun start(installDir: Path, cacheDir: Path) {
        if (isRunning) return
        // Starting: WebViews created now wait for it instead of failing
        CefHelper.cefApp.value = Result.success(null)
        try {
            System.loadLibrary("jawt")
            val config = JCefAppConfig.getInstance(installDir.toAbsolutePath().toString(), false).apply {
                getAppArgsAsList().addAll(
                    listOf("--disable-gpu", "--off-screen-rendering-enabled", "--enable-widevine-cdm"),
                )
                getCefSettings().apply {
                    // AndroidCompat's WebView draws without a window; the WebView window still gets a native one
                    windowless_rendering_enabled = true
                    cache_path = cacheDir.toAbsolutePath().toString()
                }
            }
            // As Suwayomi: JCEF keeps a failed native load in a future nobody can read, so they're loaded here first
            listOf("chrome_elf", "libcef", "jcef").forEach { config.getLoader().loadLibrary(it) }
            CefApp.setIsRemoteEnabled(config.isRemoteEnabled())
            SystemBootstrap.setLoader(config.getLoader())
            CefApp.startup(config.getAppArgs())
            val app = CefApp.getInstance(config.getAppArgs(), config.getCefSettings(), config.getServerExe())
            CefHelper.cefApp.value = Result.success(app)
            runBlocking { withTimeout(30.seconds) { CefHelper.waitForInit().first() } }
            Runtime.getRuntime().addShutdownHook(thread(start = false, name = "cef-stop") { app.dispose() })
            logcat { "The embedded browser started" }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "The embedded browser didn't start" }
            CefHelper.cefApp.value = Result.failure(EmbeddedBrowserUnavailableException(Reason.NOT_STARTED, e))
        }
    }
}
```

En `KcefWebViewProvider.kt` de AndroidCompat:
- La interfaz pasa a ser:

```kotlin
    interface InitBrowserHandler {
        fun init(provider: KcefWebViewProvider): Unit

        // desktop: brings the page's cookies to the app's cookie store before the page counts as loaded
        fun onLoadEnd(url: String) {}
    }
```

- En `LoadHandler.onLoadEnd`, la primera línea dentro de `handler.post {` pasa a ser:

```kotlin
                // desktop: before onPageFinished, so a client checking cookies there (CloudflareInterceptor) sees them
                initHandler.onLoadEnd(url)
```

En `Platform.start`:
- `startKoin { modules(androidCompatModule(), configManagerModule()) }` pasa a ser:

```kotlin
        startKoin {
            modules(
                androidCompatModule(),
                configManagerModule(),
                // AndroidCompat's WebView asks Koin for this when it creates a browser: it brings in the app's cookies
                module { single<KcefWebViewProvider.InitBrowserHandler> { CefCookieSync } },
            )
        }
```

- El bloque que marca `CefHelper.cefApp` con `CefHelper.CefException(...)`, con sus dos líneas de comentario, pasa a ser:

```kotlin
        // Without this, a WebView waits forever for Chromium inside a runBlocking on the main looper, freezing it
        // for every extension; CefRuntime replaces it once the browser runs
        CefRuntime.markNotInstalled()
```

- Justo antes de `return graph`:

```kotlin
        // The embedded browser starts in the background when it's installed (spec 2b §2). A removal asked for while
        // it ran happens first, before anything loads its DLLs
        graph.cefInstaller.applyPendingRemoval()
        if (graph.cefInstaller.isInstalled()) CefRuntime.startAsync(dirs.cef, dirs.cefCache)
```

Imports de `Platform.kt`: `mihon.desktop.core.cef.CefCookieSync`, `mihon.desktop.core.cef.CefRuntime`, `org.koin.dsl.module` y `xyz.nulldev.androidcompat.webkit.KcefWebViewProvider`. Borrar el import de `CefHelper` si deja de usarse.

- [ ] **Step 4: Ejecutar los tests sin CEF y ver que pasan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.cef.CefCookiesTest" --tests "mihon.desktop.core.WebViewUnavailableTest"`
Expected: PASS (3 + 1 tests).

- [ ] **Step 5: La tarea `cefTest`**

En `desktop-core/build.gradle.kts`, sustituir `tasks.test { forkEvery = 1 }` por:

```kotlin
// Platform.start boots Koin and AndroidCompat's global config: once per JVM
tasks.test {
    forkEvery = 1
    // The real embedded browser is downloaded and started only by cefTest
    useJUnitPlatform { excludeTags("cef") }
}

// Tests against the real embedded browser (spec 2b §5). It's downloaded once into build/cef at the repo's root
val cefTest by tasks.registering(Test::class) {
    description = "Runs the tests that need the real embedded browser"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    forkEvery = 1
    useJUnitPlatform { includeTags("cef") }
    systemProperty("cef.dir", layout.settingsDirectory.dir("build/cef").asFile.absolutePath)
}
```

- [ ] **Step 6: Escribir los tests con CEF real**

`desktop-core/src/test/kotlin/mihon/desktop/core/cef/RealCef.kt`:

```kotlin
package mihon.desktop.core.cef

import kotlinx.coroutines.runBlocking
import mihon.desktop.core.AppDirs
import mihon.desktop.core.DesktopGraph
import mihon.desktop.core.Platform
import xyz.nulldev.androidcompat.webkit.CefHelper
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory

/** The platform with the real embedded browser, installed once into the folder cefTest passes as `cef.dir`. */
fun startWithRealCef(): DesktopGraph {
    val root = createTempDirectory("cef test José")
    val graph = Platform.start(AppDirs(root.resolve("data"), root.resolve("local")))
    val dir = Path(System.getProperty("cef.dir") ?: error("Run these with ./gradlew :desktop-core:cefTest"))
    val installer = CefInstaller(dir)
    if (!installer.isInstalled()) runBlocking { installer.install() }
    CefRuntime.start(dir, root.resolve("cef-cache"))
    check(CefRuntime.isRunning) {
        "The embedded browser didn't start: ${CefHelper.cefApp.value.exceptionOrNull()?.stackTraceToString()}"
    }
    return graph
}
```

`desktop-core/src/test/kotlin/mihon/desktop/core/cef/CefRuntimeTest.kt`:

```kotlin
package mihon.desktop.core.cef

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import xyz.nulldev.androidcompat.webkit.CefHelper
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

@Tag("cef")
class CefRuntimeTest {
    @Test
    fun `the embedded browser starts from the downloaded build`(): Unit = runBlocking {
        startWithRealCef()

        val installed = CefInstaller(Path(System.getProperty("cef.dir"))).installedVersion().shouldNotBeNull()
        installed.substringBefore("-chromium") shouldBe CefRelease.JCEF_VERSION.substringBefore("-chromium")
        withTimeout(30.seconds) { CefHelper.waitForInit().first() }.shouldNotBeNull()
    }
}
```

`desktop-core/src/test/kotlin/mihon/desktop/core/cef/WebViewCookiesTest.kt`:

```kotlin
package mihon.desktop.core.cef

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.core.DesktopGraph
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

@Tag("cef")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebViewCookiesTest {
    private val server = MockWebServer()
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        graph = startWithRealCef()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val script = if (request.path == "/galleta") "document.cookie = 'sesion=jose; path=/'" else ""
                return MockResponse().setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<html><body>José<script>$script</script></body></html>")
            }
        }
        server.start()
    }

    @AfterAll
    fun stop() = server.shutdown()

    /** Loads [url] in AndroidCompat's WebView, on the main looper as extensions do, until onPageFinished. */
    private fun load(url: String) {
        val finished = CompletableDeferred<Unit>()
        var webView: WebView? = null
        Handler(Looper.getMainLooper()).post {
            webView = WebView(graph.context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        finished.complete(Unit)
                    }
                }
                loadUrl(url)
            }
        }
        runBlocking { withTimeout(30.seconds) { finished.await() } }
        Handler(Looper.getMainLooper()).post { webView?.destroy() }
    }

    @Test
    fun `a cookie a page sets reaches the app's cookie jar`() {
        load(server.url("/galleta").toString())

        CookieManager.getInstance().getCookie(server.url("/").toString()).shouldNotBeNull() shouldContain "sesion=jose"
    }

    @Test
    fun `a page without cookies isn't held up waiting for them`() {
        measureTimeMillis { load(server.url("/nada").toString()) } shouldBeLessThan 10_000
    }
}
```

- [ ] **Step 7: Ejecutar `cefTest` (riesgo 1 del spec)**

Run: `./gradlew :desktop-core:cefTest > build/cef-test.txt; tail -30 build/cef-test.txt`
Expected: PASS (1 + 2 tests). La primera vez descarga 282 MB a `build/cef`.

Esta es la comprobación del riesgo 1 del spec: los nativos de JBR 25 tienen que cargar en el JDK de la app (21 en CI, el local en desarrollo).
- Si `CefRuntime.start` falla al cargar `jcef` o `libcef`, el mensaje de `startWithRealCef` lleva la causa.
- Plan B del spec: arrancar la app con el `java.exe` que trae la propia build JBR (`build/cef/` contiene su runtime) o cambiar el JDK de la app. Anotarlo como ruling con el error exacto.

- [ ] **Step 8: Suite y commit**

Run: `./gradlew :desktop-core:test :desktop-app:test`
Expected: PASS.

Run: `bash scripts/check-licenses.sh`
Expected: código 0. `CefRuntime.kt` lleva la cabecera MPL.

```bash
./gradlew :desktop-core:spotlessApply :core:common:spotlessApply :android-compat:spotlessApply
git add desktop-core core/common android-compat
git commit -m "feat(desktop-core): start the embedded browser and share cookies with it

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Cloudflare con el navegador integrado

**Files:**
- Create: `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/util/system/WebViewUtil.kt`
- Create: `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt`
- Modify (reemplazar el stub): `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt`
- Modify: `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/NetworkHelper.kt`
- Modify: `core/common/build.gradle.kts`
- Create: `desktop-core/src/testFixtures/kotlin/mihon/desktop/core/cef/ChallengeSite.kt`
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/CloudflareWithoutBrowserTest.kt`
- Test (`cef`): `desktop-core/src/test/kotlin/mihon/desktop/core/cef/CloudflareChallengeTest.kt`

**Interfaces:**
- Consumes: `EmbeddedBrowserUnavailableException` y `CefCookieSync.onLoadEnd` (Task 3); `CefHelper.cefApp`; `AndroidCookieJar`.
- Produces:
  - `CloudflareInterceptor(context, cookieManager: AndroidCookieJar, scope, defaultUserAgentProvider)`, el mismo nombre de clase que buscan las extensiones.
  - En los test fixtures: `class ChallengeSite(interactive: Boolean = false) : Dispatcher`, con `challenges` y `browserChallenges`.

- [ ] **Step 1: El sitio con desafío y el test sin navegador (falla)**

`desktop-core/src/testFixtures/kotlin/mihon/desktop/core/cef/ChallengeSite.kt`:

```kotlin
package mihon.desktop.core.cef

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.atomic.AtomicInteger

/**
 * A site behind a Cloudflare-like challenge: 403 with `cf-mitigated: challenge` until a request brings
 * `cf_clearance=ok`, which the challenge page sets by JavaScript. With [interactive] the page asks for a click instead,
 * telling the interceptor so the way Cloudflare's page does.
 */
class ChallengeSite(private val interactive: Boolean = false) : Dispatcher() {
    /** Challenge pages served: to OkHttp and to the browser. */
    val challenges = AtomicInteger()

    /** Challenge pages served to the embedded browser (Chromium sends `Sec-Fetch-Mode`; OkHttp doesn't). */
    val browserChallenges = AtomicInteger()

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.getHeader("Cookie")?.contains("cf_clearance=ok") == true) {
            return MockResponse().setBody("contenido de José")
        }
        challenges.incrementAndGet()
        if (request.getHeader("Sec-Fetch-Mode") != null) browserChallenges.incrementAndGet()
        val script = if (interactive) {
            """setTimeout(() => window.postMessage({ source: "cloudflare-challenge", event: "interactiveBegin" }, "*"), 1000)"""
        } else {
            """document.cookie = "cf_clearance=ok; path=/""""
        }
        return MockResponse().setResponseCode(403)
            .setHeader("cf-mitigated", "challenge")
            .setHeader("Server", "cloudflare")
            .setHeader("Content-Type", "text/html; charset=utf-8")
            .setBody("<html><body>Comprobando<script>$script</script></body></html>")
    }
}
```

`desktop-core/src/test/kotlin/mihon/desktop/core/CloudflareWithoutBrowserTest.kt`:

```kotlin
package mihon.desktop.core

import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.network.GET
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import mihon.desktop.core.cef.ChallengeSite
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.io.path.createTempDirectory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CloudflareWithoutBrowserTest {
    private val server = MockWebServer()
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        val root = createTempDirectory("cloudflare")
        graph = Platform.start(AppDirs(root.resolve("data"), root.resolve("local")))
        server.dispatcher = ChallengeSite()
        server.start()
    }

    @AfterAll
    fun stop() = server.shutdown()

    @Test
    fun `a challenge without the embedded browser says the browser is missing`() {
        val error = shouldThrow<EmbeddedBrowserUnavailableException> {
            graph.networkHelper.client.newCall(GET(server.url("/manga/1").toString())).execute()
        }

        error.reason shouldBe EmbeddedBrowserUnavailableException.Reason.NOT_INSTALLED
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.CloudflareWithoutBrowserTest"`
Expected: FAIL. El stub actual deja pasar el 403 y `execute()` devuelve la respuesta sin lanzar: `Expected exception ... EmbeddedBrowserUnavailableException but no exception was thrown`.

- [ ] **Step 3: Interceptores de escritorio**

En `core/common/build.gradle.kts`:
- Añadir a `dependencies`:

```kotlin
    // Types of CefHelper.cefApp, which the interceptors read; the jar comes at runtime with android-compat
    compileOnly(libs.jcef)
```

- El comentario de `androidOnly` pasa a decir:

```kotlin
            // Android-only, or with a desktop copy of the same relative path in src/desktop (NetworkHelper, the
            // WebView interceptors and WebViewUtil), so only src/main is filtered.
```

`core/common/src/desktop/kotlin/eu/kanade/tachiyomi/util/system/WebViewUtil.kt`:

```kotlin
package eu.kanade.tachiyomi.util.system

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import okhttp3.Headers
import java.util.Locale

// desktop: the part of Mihon's WebViewUtil that the interceptors use. Without androidx.webkit, setUserAgent sets the
// user agent only; AndroidCompat's WebView derives the rest

fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT

        // Handle popups properly
        setSupportMultipleWindows(true)

        // Allow zooming
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }

    CookieManager.getInstance().acceptThirdPartyCookies(this)
}

fun WebView.setUserAgent(userAgent: String) {
    settings.userAgentString = userAgent
}

fun Headers.toWebViewHeaders(): Map<String, String> {
    return this
        // Keeping unsafe header makes webview throw [net::ERR_INVALID_ARGUMENT]
        .filter { (name, value) ->
            isRequestHeaderSafe(name, value)
        }
        .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
        .mapValues { it.value.getOrNull(0).orEmpty() }
}

// Based on [IsRequestHeaderSafe] in
// https://source.chromium.org/chromium/chromium/src/+/main:services/network/public/cpp/header_util.cc
private fun isRequestHeaderSafe(_name: String, _value: String): Boolean {
    val name = _name.lowercase(Locale.ENGLISH)
    val value = _value.lowercase(Locale.ENGLISH)
    if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
    if (name == "connection" && value == "upgrade") return false
    return true
}
private val unsafeHeaderNames = listOf(
    "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive", "transfer-encoding", "set-cookie",
    // Renderer is killed if it doesn't match the request initiator
    "origin",
)
```

`core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt`:

```kotlin
package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.webkit.WebView
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.setUserAgent
import kotlinx.coroutines.CoroutineScope
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Desktop copy of Mihon's WebViewInterceptor, over AndroidCompat's WebView and the embedded browser (spec 2b §3 A). */
abstract class WebViewInterceptor(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    abstract fun shouldIntercept(response: Response): Boolean

    abstract fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!shouldIntercept(response)) {
            return response
        }

        if (!prepareWebView()) return response

        return intercept(chain, request, response)
    }

    /**
     * Returns whether a WebView can be used.
     *
     * desktop: when the embedded browser isn't installed or didn't start it throws instead of toasting, so the screen
     * that made the request says why and offers "Abrir en WebView", which installs it.
     */
    protected fun prepareWebView(): Boolean {
        CefHelper.cefApp.value.exceptionOrNull()?.let { failure ->
            throw failure as? EmbeddedBrowserUnavailableException
                ?: EmbeddedBrowserUnavailableException(EmbeddedBrowserUnavailableException.Reason.NOT_STARTED, failure)
        }
        return true
    }

    fun CountDownLatch.awaitFor30Seconds() {
        await(30, TimeUnit.SECONDS)
    }

    fun createWebView(request: Request): WebView {
        return WebView(context).apply {
            setDefaultSettings()
            // Avoid sending empty User-Agent, Chromium WebView will reset to default if empty
            setUserAgent(request.header("User-Agent") ?: defaultUserAgentProvider())
        }
    }
}
```

`core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt` (sustituye al stub entero):

```kotlin
package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.toWebViewHeaders
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Desktop copy of Mihon's CloudflareInterceptor (spec 2b §3 A): it solves the challenge in AndroidCompat's WebView over
 * the embedded browser. Extensions find it in NetworkHelper.client by class simple name, so the name stays.
 *
 * desktop: there's no androidx.webkit, so the bridge is the WebView's JavaScript interface (Mihon's fallback).
 * AndroidCompat doesn't report onReceivedHttpError, so the challenge counts as found: OkHttp already got it. There
 * are no WebView updates to suggest.
 */
class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    scope: CoroutineScope,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, scope, defaultUserAgentProvider) {

    // desktop: AndroidCompat's main looper, where its WebViews live (Mihon: ContextCompat.getMainExecutor)
    private val executor = Executor { Handler(Looper.getMainLooper()).post(it) }

    // Requests to a host share its lock while a challenge is solved under it alone, so concurrent requests wait for
    // one WebView instead of each opening their own
    private val hostLocks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    private val listenerScript = """
        addEventListener("message", ({data}) => {
            if (data?.source === "cloudflare-challenge") {
                mihon?.postMessage(data.event);
            }
        })
    """.trimIndent()

    override fun shouldIntercept(response: Response): Boolean {
        // Checking the cf-mitigated header is the official way to detect a Cloudflare challenge:
        // https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
        return response.header("cf-mitigated") == "challenge" && response.header("Server") in SERVER_CHECK
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val lock = hostLocks.computeIfAbsent(request.url.host) { ReentrantReadWriteLock() }

        val (response, clearance) = lock.read {
            val response = chain.proceed(request)
            if (!shouldIntercept(response)) return response
            response to clearance(request.url)
        }

        // write releases the read hold of a request made from within another to the same host, like an extension
        // refreshing a token, which would otherwise wait on itself
        lock.write {
            // Another request may have solved the challenge while this one waited
            if (isCleared(request.url, clearance)) {
                response.close()
            } else {
                if (!prepareWebView()) return response
                solve(request, response, clearance)
            }
        }

        return chain.proceed(request)
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        solve(request, response, clearance(request.url))
        return chain.proceed(request)
    }

    private fun clearance(url: HttpUrl): String? {
        return cookieManager.get(url).firstOrNull { it.name == "cf_clearance" }?.value
    }

    private fun isCleared(url: HttpUrl, previousClearance: String?): Boolean {
        val clearance = clearance(url)
        return !clearance.isNullOrBlank() && clearance != previousClearance
    }

    private fun solve(request: Request, response: Response, clearance: String?) {
        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            resolveWithWebView(request, clearance)
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    private fun resolveWithWebView(originalRequest: Request, originalClearance: String?) {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        var webview: WebView? = null
        var cloudflareBypassed = false
        var challengeFailed = false

        val origRequestUrl = originalRequest.url.toString()
        // A WebView can only load a page with GET, and loading the url of another method usually reaches an
        // endpoint that answers GET without a challenge to solve. The clearance covers the whole site, so it's
        // earned on the site's root page instead.
        val challengeUrl = if (originalRequest.method == "GET") {
            origRequestUrl
        } else {
            originalRequest.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
        }
        val headers = originalRequest.headers.toWebViewHeaders()

        executor.execute {
            val view = createWebView(originalRequest)
            webview = view

            view.addJavascriptInterface(
                object {
                    @Suppress("unused")
                    @JavascriptInterface
                    fun postMessage(event: String) {
                        when (event) {
                            // The challenge cannot be solved non-interactively, abort.
                            "interactiveBegin" -> latch.countDown()
                            "fail" -> {
                                challengeFailed = true
                                latch.countDown()
                            }
                        }
                    }
                },
                "mihon",
            )

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!challengeFailed && isCleared(originalRequest.url, originalClearance)) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == challengeUrl) view.evaluateJavascript(listenerScript, null)
                }
            }

            view.loadUrl(challengeUrl, headers)
        }

        latch.awaitFor30Seconds()

        executor.execute {
            webview?.run {
                removeJavascriptInterface("mihon")
                stopLoading()
                destroy()
            }
        }

        // Throw exception if we failed to bypass Cloudflare
        if (!cloudflareBypassed) {
            // A clearance from a failed challenge would only be rejected again
            cookieManager.remove(originalRequest.url, COOKIE_NAMES, 0)
            throw CloudflareBypassException()
        }
    }
}

private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

private class CloudflareBypassException : Exception()
```

`solve` añade una rama `catch (e: IOException) { throw e }` antes de la de `Exception`: así `EmbeddedBrowserUnavailableException`, que lanza `createWebView` cuando CEF falla durante el desafío, llega a la pantalla sin envolver.

En `NetworkHelper` de escritorio:
- El parámetro `@Suppress("UNUSED_PARAMETER") @AppCoroutineScope scope: CoroutineScope,` pasa a ser `@AppCoroutineScope private val scope: CoroutineScope,`.
- `.addInterceptor(CloudflareInterceptor())` pasa a ser `.addInterceptor(CloudflareInterceptor(context, cookieJar, scope, ::defaultUserAgentProvider))`.
- La línea `ponytail:` de la KDoc de la clase se borra.

- [ ] **Step 4: Ejecutar el test sin navegador y ver que pasa**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.CloudflareWithoutBrowserTest"`
Expected: PASS.

- [ ] **Step 5: Tests con CEF real**

`desktop-core/src/test/kotlin/mihon/desktop/core/cef/CloudflareChallengeTest.kt`:

```kotlin
package mihon.desktop.core.cef

import eu.kanade.tachiyomi.network.GET
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mihon.desktop.core.DesktopGraph
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import kotlin.system.measureTimeMillis

@Tag("cef")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CloudflareChallengeTest {
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        graph = startWithRealCef()
    }

    private fun site(site: ChallengeSite) = MockWebServer().apply {
        dispatcher = site
        start()
    }

    private fun fetch(server: MockWebServer, path: String): String =
        graph.networkHelper.client.newCall(GET(server.url(path).toString())).execute().use { it.body.string() }

    @Test
    fun `a challenge the page solves on its own is passed`() {
        val challenge = ChallengeSite()
        val server = site(challenge)

        fetch(server, "/manga/1") shouldBe "contenido de José"
        challenge.browserChallenges.get() shouldBe 1
        server.shutdown()
    }

    @Test
    fun `two requests to the same site at once solve one challenge`(): Unit = runBlocking {
        val challenge = ChallengeSite()
        val server = site(challenge)

        val bodies = listOf("/manga/1", "/manga/2").map { async(Dispatchers.IO) { fetch(server, it) } }.awaitAll()

        bodies shouldBe listOf("contenido de José", "contenido de José")
        challenge.browserChallenges.get() shouldBe 1
        server.shutdown()
    }

    @Test
    fun `a challenge that needs a click fails fast with Mihon's message`() {
        val server = site(ChallengeSite(interactive = true))

        val elapsed = measureTimeMillis {
            val error = shouldThrow<IOException> { fetch(server, "/manga/1") }
            error.message shouldBe graph.context.stringResource(MR.strings.information_cloudflare_bypass_failure)
        }

        // The page said so after 1 s: well before the interceptor's 30 s wait
        elapsed shouldBeLessThan 20_000
        server.shutdown()
    }
}
```

Run: `./gradlew :desktop-core:cefTest > build/cef-test.txt; tail -30 build/cef-test.txt`
Expected: PASS (los 3 de la Task 3 más 3).

Si falla el tercer caso, el puente JavaScript de AndroidCompat (`addJavascriptInterface` sobre su `CefMessageRouter`) no expone `mihon.postMessage` a la página. Comprobarlo con un log en `KcefWebViewProvider.addJavascriptInterface` y en su `MessageRouterHandler`. Si es así, corregir AndroidCompat con `// desktop:` y anotar el ruling.

- [ ] **Step 6: Suite y commit**

Run: `./gradlew :core:common:test :desktop-core:test :desktop-app:test`
Expected: PASS.

```bash
./gradlew :core:common:spotlessApply :desktop-core:spotlessApply
git add core/common desktop-core
git commit -m "feat(core): solve Cloudflare challenges in the embedded browser

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Diálogo de descarga y estado del navegador en la UI

**Files:**
- Modify: `desktop-app/build.gradle.kts`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/browser/EmbeddedBrowser.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/browser/EmbeddedBrowserConsent.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/AppContent.kt`
- Modify: `i18n/.../base/strings.xml`, `.../es/strings.xml`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/browser/EmbeddedBrowserTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/browser/EmbeddedBrowserConsentTest.kt`

**Interfaces:**
- Consumes: `CefInstaller` y `FakeJbr` (Task 1); `CefRuntime` y `EmbeddedBrowserUnavailableException` (Task 3); `formattedMessage`.
- Produces:
  - `data class WebViewRequest(val url: String, val userAgent: String? = null)` y `data class ConsentRequest(val then: WebViewRequest?)`.
  - `object EmbeddedBrowser`, con:
    - `window: WebViewRequest?` y `consent: ConsentRequest?` (estado Compose);
    - `open(context, url, headers: Headers? = null)` y `requestInstall()`;
    - `installed()`, `dismissConsent()` y `close()`.
  - `@Composable fun EmbeddedBrowserConsent(installer: CefInstaller, startBrowser: () -> Unit)`.

- [ ] **Step 1: Cadenas**

En el bloque `<!-- Desktop -->` de `base/strings.xml`, borrar `desktop_site_needs_browser` y añadir:

```xml
    <string name="desktop_cef_title">Built-in browser</string>
    <string name="desktop_cef_consent_message">Opening pages inside the app, getting past Cloudflare and signing in to sources need a Chromium browser from JetBrains (%1$s). It\'s downloaded once.</string>
    <string name="desktop_cef_download">Download</string>
    <string name="desktop_cef_not_now">Not now</string>
    <string name="desktop_cef_downloading">Downloading the browser…</string>
    <string name="desktop_cef_install_failed">Couldn\'t install the browser: %1$s</string>
    <string name="desktop_cef_not_installed">This site needs the built-in browser. Press “Open in WebView” to install it.</string>
    <string name="desktop_cef_not_started">The built-in browser couldn\'t start: %1$s</string>
    <string name="desktop_cef_status_installed">Installed</string>
    <string name="desktop_cef_status_starting">Starting…</string>
    <string name="desktop_cef_status_not_installed">Not installed</string>
    <string name="desktop_cef_status_failed">Installed, but it didn\'t start: %1$s</string>
    <string name="desktop_cef_status_remove_pending">It\'ll be removed the next time the app starts</string>
    <string name="desktop_cef_install">Install the built-in browser</string>
    <string name="desktop_cef_remove">Remove the built-in browser</string>
    <string name="desktop_cef_removed">Built-in browser removed</string>
    <string name="desktop_cef_licenses">Browser licenses</string>
    <string name="desktop_cef_clear_site_cookies">Clear this site\'s cookies</string>
```

En `es/strings.xml`, borrar `desktop_site_needs_browser` y añadir:

```xml
    <string name="desktop_cef_title">Navegador integrado</string>
    <string name="desktop_cef_consent_message">Para abrir páginas dentro de la app, pasar Cloudflare e iniciar sesión en las fuentes hace falta un navegador Chromium de JetBrains (%1$s). Se descarga una sola vez.</string>
    <string name="desktop_cef_download">Descargar</string>
    <string name="desktop_cef_not_now">Ahora no</string>
    <string name="desktop_cef_downloading">Descargando el navegador…</string>
    <string name="desktop_cef_install_failed">No se pudo instalar el navegador: %1$s</string>
    <string name="desktop_cef_not_installed">Este sitio necesita el navegador integrado. Pulsa «Abrir en WebView» para instalarlo.</string>
    <string name="desktop_cef_not_started">No se pudo iniciar el navegador integrado: %1$s</string>
    <string name="desktop_cef_status_installed">Instalado</string>
    <string name="desktop_cef_status_starting">Arrancando…</string>
    <string name="desktop_cef_status_not_installed">No instalado</string>
    <string name="desktop_cef_status_failed">Instalado, pero no arrancó: %1$s</string>
    <string name="desktop_cef_status_remove_pending">Se borrará la próxima vez que abras la app</string>
    <string name="desktop_cef_install">Instalar el navegador integrado</string>
    <string name="desktop_cef_remove">Borrar el navegador integrado</string>
    <string name="desktop_cef_removed">Navegador integrado borrado</string>
    <string name="desktop_cef_licenses">Licencias del navegador</string>
    <string name="desktop_cef_clear_site_cookies">Borrar las cookies de este sitio</string>
```

En `ExceptionFormatter.kt`, mientras la Task 6 no lo sustituya, borrar el bloque que usa `desktop_site_needs_browser` (las 4 líneas con su comentario), para que compile.

En `desktop-app/build.gradle.kts`, añadir a `dependencies`:

```kotlin
    // Types of CefHelper.cefApp and of the WebView window's browser; the jar comes at runtime with android-compat
    compileOnly(libs.jcef)
    testImplementation(testFixtures(projects.desktopCore))
```

- [ ] **Step 2: Escribir los tests (fallan)**

`desktop-app/src/test/kotlin/mihon/desktop/app/browser/EmbeddedBrowserTest.kt`:

```kotlin
package mihon.desktop.app.browser

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.core.cef.CefRuntime
import okhttp3.Headers.Companion.headersOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.nulldev.androidcompat.webkit.CefHelper

class EmbeddedBrowserTest {
    private val graph = startTestApp()

    @AfterEach
    fun reset() {
        CefRuntime.markNotInstalled()
        EmbeddedBrowser.close()
        EmbeddedBrowser.dismissConsent()
    }

    @Test
    fun `without the browser, WebView asks to download it and then opens the page`() {
        EmbeddedBrowser.open(graph.context, "https://example.org/manga")

        EmbeddedBrowser.window.shouldBeNull()
        EmbeddedBrowser.consent.shouldNotBeNull().then?.url shouldBe "https://example.org/manga"

        EmbeddedBrowser.installed()
        EmbeddedBrowser.window?.url shouldBe "https://example.org/manga"
        EmbeddedBrowser.consent.shouldBeNull()
    }

    @Test
    fun `while the browser is still starting the window opens and waits for it`() {
        // What CefRuntime publishes while it starts in the background at launch
        CefHelper.cefApp.value = Result.success(null)

        EmbeddedBrowser.open(graph.context, "https://example.org")

        EmbeddedBrowser.window.shouldNotBeNull()
        EmbeddedBrowser.consent.shouldBeNull()
    }

    @Test
    fun `the source's user agent goes with the page`() {
        EmbeddedBrowser.open(graph.context, "https://example.org", headersOf("User-Agent", "Agente de José"))

        EmbeddedBrowser.consent?.then?.userAgent shouldBe "Agente de José"
    }

    @Test
    fun `installing from the settings opens no page`() {
        EmbeddedBrowser.requestInstall()
        EmbeddedBrowser.installed()

        EmbeddedBrowser.window.shouldBeNull()
    }
}
```

`desktop-app/src/test/kotlin/mihon/desktop/app/browser/EmbeddedBrowserConsentTest.kt`:

```kotlin
package mihon.desktop.app.browser

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.desktop.core.cef.CefInstaller
import mihon.desktop.core.cef.FakeJbr
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalTestApi::class)
class EmbeddedBrowserConsentTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        server.shutdown()
        EmbeddedBrowser.dismissConsent()
        EmbeddedBrowser.close()
    }

    private fun ComposeUiTest.waitFor(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    /** The dialog over an installer that downloads from [fake], after asking for a page in WebView. */
    private fun ComposeUiTest.show(fake: FakeJbr, onStart: () -> Unit = {}): CefInstaller {
        val graph = startTestApp()
        server.dispatcher = fake
        server.start()
        val installer = CefInstaller(createTempDirectory("cef José").resolve("cef"), url = server.url("/jbr.zip").toString())
        setContent { AppProviders(graph) { EmbeddedBrowserConsent(installer, onStart) } }
        runOnIdle { EmbeddedBrowser.open(graph.context, "https://example.org/manga") }
        waitFor(str(MR.strings.desktop_cef_download))
        return installer
    }

    @Test
    fun `the dialog says how big the download is, and installing opens the page asked for`() = runComposeUiTest {
        var started = false
        val installer = show(FakeJbr()) { started = true }
        waitFor("MB")

        onNodeWithText(str(MR.strings.desktop_cef_download)).performClick()
        waitUntil(timeoutMillis = 15_000) { EmbeddedBrowser.window != null }

        installer.isInstalled() shouldBe true
        started shouldBe true
        EmbeddedBrowser.window?.url shouldBe "https://example.org/manga"
        EmbeddedBrowser.consent.shouldBeNull()
    }

    @Test
    fun `not now closes it without downloading anything`() = runComposeUiTest {
        val installer = show(FakeJbr())

        onNodeWithText(str(MR.strings.desktop_cef_not_now)).performClick()
        waitForIdle()

        EmbeddedBrowser.consent.shouldBeNull()
        EmbeddedBrowser.window.shouldBeNull()
        installer.isInstalled() shouldBe false
    }

    @Test
    fun `a failed download says why and offers to try again`() = runComposeUiTest {
        show(FakeJbr().apply { checksum = "0".repeat(128) })

        onNodeWithText(str(MR.strings.desktop_cef_download)).performClick()

        waitFor(str(MR.strings.desktop_cef_install_failed, "").trim())
        onNodeWithText(str(MR.strings.action_retry)).assertExists()
    }

    @Test
    fun `cancelling a download in progress installs nothing`() = runComposeUiTest {
        val installer = show(FakeJbr().apply { slow = true })

        onNodeWithText(str(MR.strings.desktop_cef_download)).performClick()
        waitFor(str(MR.strings.desktop_cef_downloading))
        onNodeWithText(str(MR.strings.action_cancel)).performClick()
        waitForIdle()

        EmbeddedBrowser.consent.shouldBeNull()
        installer.isInstalled() shouldBe false
    }
}
```

`str(MR.strings.desktop_cef_install_failed, "")` da "No se pudo instalar el navegador: "; con `trim()`, la espera busca esa parte fija del mensaje.

- [ ] **Step 3: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.browser.*"`
Expected: FAIL de compilación: `Unresolved reference 'EmbeddedBrowser'` y `'EmbeddedBrowserConsent'`.

- [ ] **Step 4: Implementación**

`desktop-app/src/main/kotlin/mihon/desktop/app/browser/EmbeddedBrowser.kt`:

```kotlin
package mihon.desktop.app.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.Headers
import xyz.nulldev.androidcompat.webkit.CefHelper

/** A page for the WebView window. [userAgent] is the source's, so the site sees the same browser as its requests. */
data class WebViewRequest(val url: String, val userAgent: String? = null)

/** The download dialog. [then] opens once the browser is installed; null when it's installed from the settings. */
data class ConsentRequest(val then: WebViewRequest?)

/** What the UI shows of the embedded browser (spec 2b §3 C, D): the WebView window and the download dialog. */
object EmbeddedBrowser {
    var window: WebViewRequest? by mutableStateOf(null)
        private set

    var consent: ConsentRequest? by mutableStateOf(null)
        private set

    /**
     * "Abrir en WebView": the window when the browser is there, even if it's still starting (the window waits for
     * it); the download dialog when it isn't installed; the reason when it didn't start.
     */
    fun open(context: Context, url: String, headers: Headers? = null) {
        val request = WebViewRequest(url, headers?.get("User-Agent"))
        val failure = CefHelper.cefApp.value.exceptionOrNull()
        when {
            failure == null -> window = request
            (failure as? EmbeddedBrowserUnavailableException)?.reason ==
                EmbeddedBrowserUnavailableException.Reason.NOT_INSTALLED -> consent = ConsentRequest(request)
            else -> context.toast(with(context) { failure.formattedMessage })
        }
    }

    /** From the settings: the download dialog, with no page to open after. */
    fun requestInstall() {
        consent = ConsentRequest(then = null)
    }

    /** The download finished: opens what was asked for. */
    fun installed() {
        consent?.then?.let { window = it }
        consent = null
    }

    fun dismissConsent() {
        consent = null
    }

    fun close() {
        window = null
    }
}
```

`desktop-app/src/main/kotlin/mihon/desktop/app/browser/EmbeddedBrowserConsent.kt`:

```kotlin
package mihon.desktop.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.desktop.core.cef.CefInstaller
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The download dialog (spec 2b §3 D): its real size, progress, cancel, and the error with a retry. Shown while
 * [EmbeddedBrowser.consent] is set; once installed, [startBrowser] starts it and the page asked for opens.
 */
@Composable
fun EmbeddedBrowserConsent(installer: CefInstaller, startBrowser: () -> Unit) {
    EmbeddedBrowser.consent ?: return
    val scope = rememberCoroutineScope()
    val size by produceState<Long?>(null) { value = runCatching { installer.downloadSize() }.getOrNull() }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun download() {
        error = null
        progress = 0f
        job = scope.launch {
            try {
                installer.install { progress = it }
                startBrowser()
                EmbeddedBrowser.installed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Couldn't install the embedded browser" }
                error = e.message ?: e.toString()
            } finally {
                progress = null
                job = null
            }
        }
    }

    AlertDialog(
        // Only the buttons close it: a click outside mustn't throw away a download in progress
        onDismissRequest = {},
        title = { Text(stringResource(MR.strings.desktop_cef_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val megabytes = size?.let { "%.0f MB".format(it / 1_000_000.0) } ?: "~280 MB"
                Text(stringResource(MR.strings.desktop_cef_consent_message, megabytes))
                progress?.let {
                    Text(stringResource(MR.strings.desktop_cef_downloading))
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                }
                error?.let {
                    Text(
                        stringResource(MR.strings.desktop_cef_install_failed, it),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (progress == null) {
                TextButton(onClick = ::download) {
                    Text(stringResource(if (error == null) MR.strings.desktop_cef_download else MR.strings.action_retry))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    job?.cancel()
                    EmbeddedBrowser.dismissConsent()
                },
            ) {
                Text(stringResource(if (progress == null) MR.strings.desktop_cef_not_now else MR.strings.action_cancel))
            }
        },
    )
}
```

En `AppContent.kt`, dentro del `Box`, después de `SnackbarHost(...)`:

```kotlin
                // The embedded browser's download dialog, over whatever screen asked for a WebView (spec 2b §3 D)
                EmbeddedBrowserConsent(graph.cefInstaller) {
                    CefRuntime.startAsync(graph.appDirs.cef, graph.appDirs.cefCache)
                }
```

Imports: `mihon.desktop.app.browser.EmbeddedBrowserConsent` y `mihon.desktop.core.cef.CefRuntime`.

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.browser.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS (4 + 4 + 1 tests).

- [ ] **Step 6: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add desktop-app i18n
git commit -m "feat(desktop-app): ask before downloading the embedded browser

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Ventana WebView, "Abrir en WebView" y mensajes de error

**Files:**
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/cef/BrowserSession.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/browser/WebViewWindow.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/presentation/util/ExceptionFormatter.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreen.kt`
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt`, `ReaderControls.kt`
- Test: `desktop-app/src/test/kotlin/eu/kanade/presentation/util/FormattedMessageTest.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/ReaderControlsTest.kt` (actualizar un caso)
- Test (`cef`): `desktop-core/src/test/kotlin/mihon/desktop/core/cef/BrowserSessionTest.kt`

**Interfaces:**
- Consumes:
  - `CefCookieSync` y `startWithRealCef` (Task 3);
  - `EmbeddedBrowser` y `WebViewRequest` (Task 5);
  - `AppProviders`, `MIN_WIDTH` y `MIN_HEIGHT` (plan 1).
- Produces:
  - `class BrowserSession(app: CefApp, url: String, userAgent: String?)`, con:
    - `browser`;
    - `title`, `currentUrl`, `canGoBack` y `canGoForward` (`StateFlow`);
    - `load(url)`, `back()`, `forward()`, `reload()`, `clearSiteCookies()` y `dispose()`.
  - `@Composable fun EmbeddedBrowserWindow(graph: AppGraph)`.
  - El parámetro nuevo `onOpenInWebView: (() -> Unit)?` de `ReaderControls`.

- [ ] **Step 1: Escribir los tests (fallan)**

`desktop-app/src/test/kotlin/eu/kanade/presentation/util/FormattedMessageTest.kt`:

```kotlin
package eu.kanade.presentation.util

import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException.Reason
import eu.kanade.tachiyomi.network.HttpException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import java.io.IOException

class FormattedMessageTest {
    private val context = startTestApp().context

    private fun Throwable.message() = with(context) { formattedMessage }

    @Test
    fun `a missing browser says how to install it, even wrapped by an extension`() {
        val missing = EmbeddedBrowserUnavailableException(Reason.NOT_INSTALLED)

        missing.message() shouldBe str(MR.strings.desktop_cef_not_installed)
        IOException("la extensión falló", missing).message() shouldBe str(MR.strings.desktop_cef_not_installed)
    }

    @Test
    fun `a browser that didn't start says why`() {
        EmbeddedBrowserUnavailableException(Reason.NOT_STARTED, UnsatisfiedLinkError("falta libcef.dll"))
            .message() shouldContain "falta libcef.dll"
    }

    @Test
    fun `a failed Cloudflare challenge shows Mihon's message, and a 403 is just a 403`() {
        IOException(str(MR.strings.information_cloudflare_bypass_failure)).message() shouldBe
            str(MR.strings.information_cloudflare_bypass_failure)
        HttpException(403).message() shouldBe str(MR.strings.exception_http, 403)
    }
}
```

`HttpException` es la de `eu.kanade.tachiyomi.network` que ya usa `ExceptionFormatter`. Si su constructor pide más argumentos, se usan los de su declaración.

En `ReaderControlsTest`, el caso `a chapter whose page list fails offers retry and opening it in the browser` pasa a ser:

```kotlin
    @Test
    fun `a chapter whose page list fails offers retry and opening it in WebView`() = runComposeUiTest {
        open(failPageList = true)
        waitFor("José")

        // Without the embedded browser, WebView asks to download it first and then opens the chapter
        onNodeWithText(str(MR.strings.action_open_in_web_view)).performClick()
        EmbeddedBrowser.consent?.then?.url.shouldNotBeNull() shouldContain "/c1"
        runOnIdle { EmbeddedBrowser.dismissConsent() }

        onNodeWithText(str(MR.strings.action_retry)).performClick()
        waitForTag("page 1 ready")
    }
```

Imports: `io.kotest.matchers.nulls.shouldNotBeNull`, `io.kotest.matchers.string.shouldContain` y `mihon.desktop.app.browser.EmbeddedBrowser`.

`desktop-core/src/test/kotlin/mihon/desktop/core/cef/BrowserSessionTest.kt`:

```kotlin
package mihon.desktop.core.cef

import android.webkit.CookieManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

@Tag("cef")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrowserSessionTest {
    private val server = MockWebServer()

    @BeforeAll
    fun start() {
        startWithRealCef()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(
                    when (request.path) {
                        "/otra" -> "<html><head><title>Otra</title></head></html>"
                        "/emergente" -> "<html><body><script>window.open('/otra')</script></body></html>"
                        else -> "<html><head><title>Hola José</title></head>" +
                            "<body><script>document.cookie = 'login=si; path=/'</script></body></html>"
                    },
                )
        }
        server.start()
    }

    @AfterAll
    fun stop() = server.shutdown()

    /** A session shown in a real window, as the WebView window shows it (spec 2b risk 2). */
    private fun <T> shown(path: String, userAgent: String? = null, block: suspend (BrowserSession) -> T): T = runBlocking {
        val app = CefHelper.waitForInit().first()
        val session = BrowserSession(app, server.url(path).toString(), userAgent)
        lateinit var frame: JFrame
        SwingUtilities.invokeAndWait {
            frame = JFrame().apply {
                add(session.browser.uiComponent)
                setSize(800, 600)
                isVisible = true
            }
        }
        try {
            block(session)
        } finally {
            session.dispose()
            SwingUtilities.invokeAndWait { frame.dispose() }
        }
    }

    @Test
    fun `the window's browser shows the page with the source's user agent and brings back its cookies`() =
        shown("/", userAgent = "Agente de José") { session ->
            withTimeout(30.seconds) { session.title.first { it == "Hola José" } }
            server.takeRequest(5, TimeUnit.SECONDS)?.getHeader("User-Agent") shouldBe "Agente de José"
            withTimeout(10.seconds) {
                while (CookieManager.getInstance().getCookie(server.url("/").toString())?.contains("login=si") != true) {
                    delay(100)
                }
            }
        }

    @Test
    fun `a page that opens a new window loads it in the same browser`() = shown("/emergente") { session ->
        withTimeout(30.seconds) { session.title.first { it == "Otra" } }
    }
}
```

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-app:test --tests "eu.kanade.presentation.util.FormattedMessageTest" --tests "mihon.desktop.app.reader.ReaderControlsTest"`
Expected:
- `FormattedMessageTest`: FAIL. El primer caso da "EmbeddedBrowserUnavailableException: The embedded browser isn't installed" en lugar del texto en español.
- El caso actualizado de `ReaderControlsTest`: FAIL. La pantalla de error todavía ofrece "Abrir en navegador".

- [ ] **Step 3: Sesión del navegador visible**

`desktop-core/src/main/kotlin/mihon/desktop/core/cef/BrowserSession.kt`:

```kotlin
package mihon.desktop.core.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter

/**
 * The browser of the WebView window (spec 2b §3 E): CEF with its own native window, the app's cookies in and out,
 * the source's user agent, popups in this same browser. It exposes nothing to the page's JavaScript: its client is
 * created directly, without AndroidCompat's bridge. Without a download handler CEF cancels downloads, and it ignores
 * external protocols.
 */
class BrowserSession(app: CefApp, url: String, private val userAgent: String?) {
    private val client: CefClient = app.createClient()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _currentUrl = MutableStateFlow(url)
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    val browser: CefBrowser

    init {
        CefCookieSync.pushAll()
        client.addDisplayHandler(
            object : CefDisplayHandlerAdapter() {
                override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
                    if (frame.isMain) _currentUrl.value = url
                }

                override fun onTitleChange(browser: CefBrowser, title: String) {
                    _title.value = title
                }
            },
        )
        client.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(
                    browser: CefBrowser,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean,
                ) {
                    _canGoBack.value = canGoBack
                    _canGoForward.value = canGoForward
                }

                override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                    // A login or a solved challenge reaches the app's requests without closing the window
                    if (frame.isMain) CefCookieSync.pull(frame.url)
                }
            },
        )
        client.addLifeSpanHandler(
            object : CefLifeSpanHandlerAdapter() {
                // window.open and target=_blank: in this same browser
                override fun onBeforePopup(
                    browser: CefBrowser,
                    frame: CefFrame,
                    targetUrl: String?,
                    targetFrameName: String?,
                ): Boolean {
                    targetUrl?.let(browser::loadURL)
                    return true
                }

                override fun onAfterCreated(browser: CefBrowser) {
                    val ua = userAgent
                    if (ua == null) {
                        browser.loadURL(url)
                    } else {
                        browser.devToolsClient
                            .executeDevToolsMethod("Emulation.setUserAgentOverride", Json.encodeToString(mapOf("userAgent" to ua)))
                            .whenComplete { _, _ -> browser.loadURL(url) }
                    }
                }
            },
        )
        // Blank first: the page loads once the user agent is set, when the native window exists (onAfterCreated)
        browser = client.createBrowser("about:blank", CefRendering.DEFAULT, false)
    }

    fun load(url: String) = browser.loadURL(url)

    fun back() = browser.goBack()

    fun forward() = browser.goForward()

    fun reload() = browser.reload()

    /** "Borrar las cookies de este sitio": in CEF and in the app, then the page again without them. */
    fun clearSiteCookies() {
        CefCookieSync.clear(currentUrl.value)
        browser.reload()
    }

    fun dispose() {
        CefCookieSync.pullAll()
        browser.close(true)
        client.dispose()
    }
}
```

Run: `./gradlew :desktop-core:cefTest > build/cef-test.txt; tail -30 build/cef-test.txt`
Expected: PASS, incluidos los 2 de `BrowserSessionTest`.

Esta es la comprobación del riesgo 2 del spec: la build JBR tiene que dibujar el navegador con ventana dentro de un componente AWT. Si `uiComponent` no se dibuja o `onAfterCreated` no llega:
- probar `createImmediately()` tras `createBrowser`, como hace AndroidCompat;
- si tampoco, el plan B del spec: la ventana WebView pasa a ser un `JFrame` Swing con una barra Swing.

Anotarlo como ruling.

- [ ] **Step 4: La ventana WebView**

`desktop-app/src/main/kotlin/mihon/desktop/app/browser/WebViewWindow.kt`:

```kotlin
package mihon.desktop.app.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import mihon.app.di.AppGraph
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.startup.MIN_HEIGHT
import mihon.desktop.app.startup.MIN_WIDTH
import mihon.desktop.core.AppDirs
import mihon.desktop.core.cef.BrowserSession
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowBack
import mihon.icons.materialsymbols.automirroredrounded.ArrowForward
import mihon.icons.materialsymbols.automirroredrounded.OpenInNew
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Refresh
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.awt.Dimension

/** The WebView window (spec 2b §3 E): one at a time, reused and brought to the front for the next page asked for. */
@Composable
fun EmbeddedBrowserWindow(graph: AppGraph) {
    val request = EmbeddedBrowser.window ?: return
    Window(
        onCloseRequest = EmbeddedBrowser::close,
        state = rememberWindowState(size = DpSize(1100.dp, 800.dp)),
        title = AppDirs.APP_NAME,
    ) {
        window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
        LaunchedEffect(request) { window.toFront() }
        AppProviders(graph) {
            Surface(Modifier.fillMaxSize()) { WebViewContent(request) }
        }
    }
}

@Composable
private fun WebViewContent(request: WebViewRequest) {
    val context = LocalContext.current
    // The window can open while the browser is still starting in the background
    val session by produceState<Result<BrowserSession>?>(null) {
        value = runCatching {
            val app = withContext(Dispatchers.IO) { CefHelper.waitForInit().first() }
            BrowserSession(app, request.url, request.userAgent)
        }
    }
    val current = session
    when {
        current == null -> LoadingScreen()
        current.isFailure -> EmptyScreen(message = with(context) { current.exceptionOrNull()!!.formattedMessage })
        else -> BrowserPane(current.getOrThrow(), request)
    }
}

@Composable
private fun BrowserPane(session: BrowserSession, request: WebViewRequest) {
    val context = LocalContext.current
    DisposableEffect(session) { onDispose { session.dispose() } }
    // Asked for another page while the window is open: it replaces the one shown
    var shown by remember { mutableStateOf(request) }
    LaunchedEffect(request) {
        if (request != shown) {
            shown = request
            session.load(request.url)
        }
    }
    val title by session.title.collectAsState()
    val url by session.currentUrl.collectAsState()
    val canGoBack by session.canGoBack.collectAsState()
    val canGoForward by session.canGoForward.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        title.ifBlank { url },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            },
            navigationIcon = {
                IconButton(onClick = EmbeddedBrowser::close) {
                    Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(MR.strings.action_close))
                }
            },
            // Buttons, not a menu: a Compose popup would be drawn under the native browser below
            actions = {
                IconButton(onClick = session::back, enabled = canGoBack) {
                    Icon(
                        MaterialSymbols.AutoMirroredRounded.ArrowBack,
                        contentDescription = stringResource(MR.strings.action_webview_back),
                    )
                }
                IconButton(onClick = session::forward, enabled = canGoForward) {
                    Icon(
                        MaterialSymbols.AutoMirroredRounded.ArrowForward,
                        contentDescription = stringResource(MR.strings.action_webview_forward),
                    )
                }
                IconButton(onClick = session::reload) {
                    Icon(MaterialSymbols.Rounded.Refresh, contentDescription = stringResource(MR.strings.action_webview_refresh))
                }
                IconButton(
                    onClick = {
                        session.clearSiteCookies()
                        context.toast(MR.strings.cookies_cleared)
                    },
                ) {
                    Icon(
                        MaterialSymbols.Rounded.Delete,
                        contentDescription = stringResource(MR.strings.desktop_cef_clear_site_cookies),
                    )
                }
                IconButton(onClick = { context.openInBrowser(url) }) {
                    Icon(
                        MaterialSymbols.AutoMirroredRounded.OpenInNew,
                        contentDescription = stringResource(MR.strings.action_open_in_browser),
                    )
                }
            },
        )
        // The native browser takes all the space under the bar: nothing of Compose draws over it
        SwingPanel(factory = { session.browser.uiComponent }, modifier = Modifier.fillMaxSize())
    }
}
```

En `Main.kt`, dentro del `CompositionLocalProvider(LocalWindowExceptionHandlerFactory ...)`, justo después del bloque `Window(...) { ... }` de la ventana principal:

```kotlin
            // The WebView window, while a page is open in it (spec 2b §3 E)
            EmbeddedBrowserWindow(graph)
```

Import: `mihon.desktop.app.browser.EmbeddedBrowserWindow`.

- [ ] **Step 5: Mensajes de error y "Abrir en WebView"**

`ExceptionFormatter.kt`. Al principio del `get()`, sustituyendo lo que quedó del bloque `desktop_site_needs_browser`:

```kotlin
        // desktop: the embedded browser missing or not started (spec 2b §3 A), however deep an extension wrapped it
        generateSequence(this) { it.cause }.filterIsInstance<EmbeddedBrowserUnavailableException>().firstOrNull()?.let {
            return when (it.reason) {
                EmbeddedBrowserUnavailableException.Reason.NOT_INSTALLED ->
                    context.stringResource(MR.strings.desktop_cef_not_installed)
                EmbeddedBrowserUnavailableException.Reason.NOT_STARTED ->
                    context.stringResource(MR.strings.desktop_cef_not_started, it.cause?.message ?: it.cause.toString())
            }
        }
```

Import: `eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException`.

`ExtensionsTab.kt`: `onOpenWebView` pasa a ser:

```kotlin
                onOpenWebView = { extension ->
                    extension.sources.getOrNull(0)?.let { EmbeddedBrowser.open(context, it.baseUrl) }
                },
```

`BrowseSourceScreen.kt` (`ui/browse/source/browse`): `onWebViewClick` pasa a ser:

```kotlin
        val onWebViewClick = f@{
            val httpSource = source as? HttpSource ?: return@f
            EmbeddedBrowser.open(context, httpSource.getHomeUrl(), httpSource.headers)
        }
```

`MangaScreen.kt` (`ui/manga`): `onWebViewClicked` pasa a ser:

```kotlin
            onWebViewClicked = {
                val url = getMangaUrl(viewModel.manga, viewModel.source)
                if (url != null) EmbeddedBrowser.open(context, url, (viewModel.source as? HttpSource)?.headers)
            }.takeIf { isHttpSource },
```

`ReaderControls.kt`:
- Añadir el parámetro `onOpenInWebView: (() -> Unit)?,` justo antes de `onOpenInBrowser`.
- En `ReaderTopBar(...)`, `onOpenInWebView = null,` pasa a ser `onOpenInWebView = onOpenInWebView,`.

`ReaderScreen.kt`:
- En la pantalla de error, la acción `EmptyScreenAction(MR.strings.action_open_in_browser, MaterialSymbols.Rounded.Public) { context.openInBrowser(it) }` pasa a ser:

```kotlin
                            EmptyScreenAction(MR.strings.action_open_in_web_view, MaterialSymbols.Rounded.Public) {
                                EmbeddedBrowser.open(context, it, (state.source as? HttpSource)?.headers)
                            }
```

- En `ReaderControls(...)`, añadir antes de `onOpenInBrowser`:

```kotlin
            onOpenInWebView = chapterUrl?.let { url ->
                { EmbeddedBrowser.open(context, url, (state.source as? HttpSource)?.headers) }
            },
```

En todos estos archivos: import `mihon.desktop.app.browser.EmbeddedBrowser` y, donde falte, `eu.kanade.tachiyomi.source.online.HttpSource`. Borrar los comentarios `// desktop: the system browser until the embedded WebView (subproject 2b)` y los imports de `openInBrowser` que dejen de usarse.

- [ ] **Step 6: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test`
Expected: PASS.

Run: `bash scripts/check-no-android.sh`
Expected: `No direct Android imports in desktop-app`.

- [ ] **Step 7: Comprobación manual**

`gradlew :desktop-app:run`:
1. Abrir un manga y pulsar el botón WebView: aparece el diálogo con ~282 MB.
2. Descargar. Al terminar, la ventana se abre con la página del manga.
3. Atrás, adelante y recargar funcionan.
4. Un enlace con `target=_blank` se abre en la misma ventana.
5. Cerrar la ventana y volver a abrirla desde otro manga: se reutiliza.

- [ ] **Step 8: Commit**

```bash
./gradlew :desktop-core:spotlessApply :desktop-app:spotlessApply
git add desktop-core desktop-app
git commit -m "feat(desktop-app): WebView window on the embedded browser, opened from every Open in WebView

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Ajustes del navegador integrado

**Files:**
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/AdvancedSettingsScreen.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/settings/SettingsTest.kt` (dos casos nuevos)

**Interfaces:**
- Consumes:
  - `CefInstaller`, `CefRuntime` y `CefCookieSync` (Tasks 1 y 3);
  - `EmbeddedBrowser` (Task 5);
  - `Preference.PreferenceGroup` y `Preference.PreferenceItem.TextPreference` (plan 1).
- Produces: el grupo "Navegador integrado" de Ajustes › Avanzado, con:
  - el estado;
  - "Instalar" o "Borrar";
  - "Licencias del navegador";
  - "Borrar cookies".

- [ ] **Step 1: Escribir los tests (fallan)**

En `SettingsTest`, añadir:

```kotlin
    @Test
    fun `the advanced settings offer to install the embedded browser`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_settings)).performClick()
        waitForText(str(MR.strings.pref_category_advanced))
        onNodeWithText(str(MR.strings.pref_category_advanced)).performClick()
        waitForText(str(MR.strings.desktop_cef_status_not_installed))
        onNodeWithText(str(MR.strings.desktop_cef_install)).performClick()

        // The same download dialog as WebView's
        waitForText(str(MR.strings.desktop_cef_download))
        runOnIdle { EmbeddedBrowser.dismissConsent() }
    }

    @Test
    fun `clearing cookies empties the app's cookie store`() = runComposeUiTest {
        val graph = startTestApp()
        val store = (CookieHandler.getDefault() as java.net.CookieManager).cookieStore
        store.add(URI("https://example.com/"), HttpCookie("sesion", "José").apply { path = "/" })
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_settings)).performClick()
        waitForText(str(MR.strings.pref_category_advanced))
        onNodeWithText(str(MR.strings.pref_category_advanced)).performClick()
        waitForText(str(MR.strings.pref_clear_cookies))
        onNodeWithText(str(MR.strings.pref_clear_cookies)).performClick()
        waitForIdle()

        store.cookies.shouldBeEmpty()
    }
```

Imports: `io.kotest.matchers.collections.shouldBeEmpty`, `mihon.desktop.app.browser.EmbeddedBrowser`, `java.net.CookieHandler`, `java.net.HttpCookie` y `java.net.URI`.

- [ ] **Step 2: Ejecutarlos y ver que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.settings.SettingsTest"`
Expected: FAIL. `ComposeTimeoutException` esperando "No instalado".

- [ ] **Step 3: Implementación**

`AdvancedSettingsScreen.kt`:
- La KDoc pasa a ser `/** Spec 2a §3 and 2b §2: the image cache, the logs folder and the embedded browser. */`.
- Al final de la lista que devuelve `getPreferences()`, añadir `embeddedBrowser(context),`.
- Añadir dentro del objeto:

```kotlin
    /** The embedded browser (spec 2b §2): its state, install or remove it, its licenses, and the app's cookies. */
    @Composable
    private fun embeddedBrowser(context: Context): Preference.PreferenceGroup {
        val installer = context.appGraph.cefInstaller
        val state by CefHelper.cefApp.collectAsState()
        var changed by remember { mutableIntStateOf(0) }
        val installed = remember(state, changed) { installer.isInstalled() }
        val removalPending = remember(state, changed) { installer.isRemovalPending() }
        val running = state.getOrNull() != null
        val status = when {
            removalPending -> stringResource(MR.strings.desktop_cef_status_remove_pending)
            !installed -> stringResource(MR.strings.desktop_cef_status_not_installed)
            running -> stringResource(MR.strings.desktop_cef_status_installed)
            state.isSuccess -> stringResource(MR.strings.desktop_cef_status_starting)
            else -> stringResource(
                MR.strings.desktop_cef_status_failed,
                state.exceptionOrNull()?.cause?.message ?: state.exceptionOrNull()?.message.orEmpty(),
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.desktop_cef_title),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(title = status),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.desktop_cef_install),
                    visible = !installed && !removalPending,
                    onClick = EmbeddedBrowser::requestInstall,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.desktop_cef_remove),
                    visible = installed,
                    onClick = {
                        installer.uninstall(running = CefRuntime.isRunning)
                        changed++
                        context.toast(
                            if (CefRuntime.isRunning) {
                                MR.strings.desktop_cef_status_remove_pending
                            } else {
                                MR.strings.desktop_cef_removed
                            },
                        )
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.desktop_cef_licenses),
                    visible = installed && running,
                    onClick = { EmbeddedBrowser.open(context, "chrome://credits") },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_cookies),
                    onClick = {
                        CefCookieSync.clear(url = null)
                        context.toast(MR.strings.cookies_cleared)
                    },
                ),
            ),
        )
    }
```

Imports:
- `android.content.Context`;
- `androidx.compose.runtime.collectAsState`, `getValue`, `mutableIntStateOf`, `remember` y `setValue`;
- `mihon.desktop.app.browser.EmbeddedBrowser`;
- `mihon.desktop.core.cef.CefCookieSync` y `mihon.desktop.core.cef.CefRuntime`;
- `xyz.nulldev.androidcompat.webkit.CefHelper`.

Si `getPreferences()` devuelve `List<Preference.PreferenceItem<...>>` en lugar de `List<Preference>`, se cambia su tipo de retorno a `List<Preference>`, que es el de `SearchableSettings`.

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.settings.SettingsTest" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew :desktop-app:spotlessApply
git add desktop-app
git commit -m "feat(desktop-app): embedded browser settings: status, install, remove, licenses and cookies

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Harness con CEF y CI

**Files:**
- Modify: `compat-harness/src/main/kotlin/mihon/desktop/harness/Main.kt`
- Modify: `.github/workflows/build.yml`, `.github/workflows/compat.yml`

**Interfaces:**
- Consumes: `CefInstaller` y `CefRuntime` (Tasks 1 y 3).
- Produces: la opción `--cef <carpeta>` del harness y el job `cefTest` de CI.

- [ ] **Step 1: Opción `--cef` del harness**

En `Main.kt`:
- El texto de uso pasa a ser `"Usage: --repo <index url> [--limit 100] [--threshold 0.8] [--out compat-report.md] [--packages file] [--cef dir]"`.
- Justo después de `val graph = Platform.start(...)`:

```kotlin
    // With the embedded browser, Cloudflare and WebView extensions are measured too (spec 2b §5). It's installed
    // into [dir] once and reused by later runs
    args.option("--cef")?.let { dir ->
        val installer = CefInstaller(Path(dir))
        if (!installer.isInstalled()) runBlocking { installer.install() }
        CefRuntime.start(Path(dir), home.resolve("cef-cache"))
        check(CefRuntime.isRunning) { "The embedded browser didn't start" }
    }
```

Imports: `mihon.desktop.core.cef.CefInstaller` y `mihon.desktop.core.cef.CefRuntime`.

- [ ] **Step 2: CI**

En `.github/workflows/build.yml`, después de `- run: bash scripts/check-no-android.sh`:

```yaml
      - uses: actions/cache@v4
        with:
          path: build/cef
          key: cef-jbr-release-25.0.4b508.27
      - run: ./gradlew :desktop-core:cefTest
```

En `.github/workflows/compat.yml`:
- Añadir el mismo paso `actions/cache@v4` antes de `- name: Run harness`.
- En `run:` del harness, añadir ` --cef $GITHUB_WORKSPACE/build/cef` al final de `--args="..."`, dentro de las comillas.

- [ ] **Step 3: Ejecutar el harness con CEF**

Run: `./gradlew :compat-harness:run --args="--repo https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb --limit 100 --threshold 0.8 --cef ../build/cef"`
Expected:
- Código 0 y pass rate ≥ 89,5 %.
- Pasan las 6 fuentes que daban 403 de Cloudflare, cubari y mangaup. La excepción son las que exijan un desafío interactivo.
- Anotar en el ledger la tasa y cada fuente de esa lista que siga fallando, con su motivo.

El directorio de trabajo de `:compat-harness:run` es `compat-harness/`, así que `../build/cef` es la misma carpeta que usa `cefTest`.

- [ ] **Step 4: Commit**

```bash
git add compat-harness .github/workflows
git commit -m "ci: test against the real embedded browser; the harness can run with it

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Verificación completa y prueba manual del subproyecto 2b

**Files:** ninguno nuevo. Se corrige lo que salga.

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: la verificación del spec 2b completo.

- [ ] **Step 1: Suite, CEF, formato, licencias e imports**

Run: `./gradlew test spotlessCheck > build/plan2b-verify.txt; tail -20 build/plan2b-verify.txt`
Expected: `BUILD SUCCESSFUL`. Pasan los 270 tests anteriores y los nuevos.

Run: `./gradlew :desktop-core:cefTest`
Expected: PASS.

Run: `bash scripts/check-licenses.sh` y `bash scripts/check-no-android.sh`
Expected: código 0 en ambos.

- [ ] **Step 2: Prueba manual del spec 2b (sección 5)**

Con `MANGAREADER_HOME` apuntando a una carpeta nueva, `gradlew :desktop-app:run`:
1. Primer "Abrir en WebView": diálogo con ~282 MB, descarga con progreso, y la ventana se abre.
2. Una fuente protegida por Cloudflare (una de las 6 del harness) carga sin intervenir.
3. Un desafío interactivo: el error dice "No se pudo saltar Cloudflare". "Abrir en WebView", resolverlo en la ventana, cerrarla y "Reintentar": funciona.
4. Iniciar sesión en la web de una fuente desde la ventana, cerrar la app y reabrirla: la sesión sigue. La fuente lo muestra al cargar contenido que exige sesión.
5. "Borrar cookies" en Ajustes › Avanzado cierra esa sesión.
6. "Borrar el navegador integrado" con la app abierta: aviso de que se borra en el siguiente arranque. Reiniciar: estado "No instalado", y el siguiente "Abrir en WebView" vuelve a ofrecer la descarga.
7. Cerrar la app con la ventana WebView abierta: se cierran las dos y no queda ningún proceso `jcef_helper.exe` en el Administrador de tareas.

Anotar en el ledger el resultado de cada paso.

- [ ] **Step 3: Commit (si hubo correcciones)**

```bash
git add -A
git commit -m "fix: <lo que corrigió la verificación>

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

## Después de este plan

Con este plan termina el subproyecto 2. Lo siguiente es el **subproyecto 3**: biblioteca, novedades, categorías y migración. Las categorías y la migración devuelven los diálogos que se quitaron del historial y del manga.
