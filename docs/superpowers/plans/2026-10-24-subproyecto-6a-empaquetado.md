# Subproyecto 6a: empaquetado local (Turboyomi) — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La app pasa a llamarse Turboyomi, migra los datos de MangaReader y se distribuye en un ZIP y un MSI por usuario, con su propio Java recortado.

**Architecture:**
- **Renombrado:** el nombre y los identificadores cambian en sus constantes. `LegacyMigration`, en desktop-core, mueve las carpetas antes de que arranque nada.
- **Versión y metadatos:** la versión sale de `git describe` en Gradle, que genera `DesktopBuildInfo`.
- **Icono:** un SVG en los recursos sirve a la ventana y a la bandeja. Una herramienta con Skia lo rasteriza a `.ico` para el exe y el MSI.
- **Empaquetado:** `jlink` recorta el runtime y `jpackage` hace la imagen (ZIP) y el MSI. El MSI lleva un `main.wxs` propio que registra el protocolo.

**Tech Stack:** Gradle (Kotlin DSL), JDK 23 (`jdeps`, `jlink`, `jpackage`), WiX 3.14, Skia (Skiko, ya en la app) para el icono, JUnit 5 + kotest.

**Spec:** `docs/superpowers/specs/2026-10-24-subproyecto-6a-empaquetado-design.md`

## Global Constraints

- Nombre: Turboyomi.
- Id `app.turboyomi`.
- Carpetas `%APPDATA%\Turboyomi` y `%LOCALAPPDATA%\Turboyomi`.
- Almacenamiento por defecto `Documentos\Turboyomi`.
- Protocolos `turboyomi://` y `app.turboyomi://` (Comick).
- Variable de desarrollo `TURBOYOMI_HOME` (spec §1, §3 A).
- MSI por usuario en `%LOCALAPPDATA%\Programs\Turboyomi`, sin UAC, con `UpgradeCode` fijo (spec §3 B).
- Runtime con, como mínimo: `java.desktop`, `java.net.http`, `java.sql`, `jdk.crypto.ec`, `jdk.unsupported`, `jdk.charsets`, `jdk.localedata`, `java.naming` y `java.management` (spec §2).
- Sin ProGuard (spec padre §6).
- Los datos nunca se borran al desinstalar ni en una migración fallida (spec §3).
- Tests `= runBlocking {}` con `: Unit`; scripts de edición con Write y `PYTHONPATH=<workspace> python`.
- Commits con `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`; nunca push a `upstream`.

## Desviaciones respecto a la spec

1. **Prueba de la versión (§5).** El parseo de `git describe` vive en el build script de desktop-core, porque Gradle lo necesita para `jpackage`. Ningún test de JUnit puede llamarlo.
   - Los tests comprueban lo generado en este repo: sin tags `v*` sale `0.1.0` y el commit es el de `git rev-parse`.
   - El caso con tag se comprueba en la Task 4 con un tag temporal `v9.9.9`, que se borra después.
   - Solo cuentan los tags `v*`: el repo tiene `upstream-base` y no debe tomarse como versión.
2. **Icono de la ventana y la bandeja.** Usan el SVG directamente (`painterResource("turboyomi.svg")`, que Compose Desktop ya rasteriza). El `.ico` solo lo necesitan el exe y el MSI.
   El SVG vive en `desktop-app/src/main/resources/turboyomi.svg`, no en `desktop-app/packaging/`, para que la app lo
   cargue del classpath.
3. **`VERSION_CODE`.** La spec dice código 1 para `0.1.0`. El plan usa `mayor·10000 + menor·100 + parche` (100 para
   `0.1.0`), que ordena las versiones; nadie lee el código fuera del instalador.
4. **`--smoke` y la instancia única.** `--smoke` arranca con datos temporales y antes de `SingleInstance`, para no chocar con una copia abierta de la app.

## Review Focus

1. Una carpeta de MangaReader con la base de datos abierta por otra copia de la app: la migración se deshace y avisa, sin dejar datos repartidos. Test en la Task 1.
2. `Documentos` en OneDrive (`C:\Users\…\OneDrive\Documentos`): la ruta por defecto vieja se reconoce y se cambia. Test en la Task 1.
3. Un `storage_dir` guardado con otra forma de URI (`file:/C:/…` frente a `file:///C:/…`): se compara como ruta, no como texto. Test en la Task 1.
4. Una ruta de instalación con espacios o acentos (`C:\Users\José Pérez\AppData\Local\Programs\Turboyomi`): el exe arranca y el protocolo registrado lleva comillas. Lo cubre el arranque desde el MSI de la Task 4.
5. Instalar un MSI encima de otro de la misma versión: Windows Installer lo trata como reparación y no duplica nada. Comprobación en la Task 4.

---

### Task 1: el nombre Turboyomi y la migración de MangaReader

**Files:**
- Create:
  - `desktop-core/src/main/kotlin/mihon/desktop/core/LegacyMigration.kt`;
  - `desktop-core/src/test/kotlin/mihon/desktop/core/LegacyMigrationTest.kt`.
- Modify:
  - `desktop-core/src/main/kotlin/mihon/desktop/core/AppDirs.kt` (`APP_NAME`, `HOME_ENV`);
  - `desktop-core/src/main/kotlin/mihon/desktop/core/Platform.kt` (`APPLICATION_ID`);
  - `desktop-app/src/main/kotlin/mihon/desktop/app/track/TrackLoginHandler.kt` (`SCHEMES`);
  - las 4 APIs con redirect (`ShikimoriApi`, `BangumiApi`, `ComickApi`, `MangaBakaApi`);
  - `tracker-keys.example.properties`;
  - `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt`;
  - los tests que nombran `mangareader` / `MangaReader` (`ProtocolRegistrarTest`, `LoginUrlsTest`, `TrackLoginHandlerTest`, `OAuthTrackersTest`, `AutoBackupTest`, `FileDialogsTest`).
- Test: `LegacyMigrationTest`, más los existentes renombrados.

**Interfaces:**
- Produces: `LegacyMigration.run(env: Map<String, String>, documents: () -> Path): LegacyMigration.Result`, con `enum Result { NotNeeded, Migrated, Failed }`. `Main` muestra el aviso si da `Failed`.

- [ ] **Step 1: Escribir los tests**

`LegacyMigrationTest.kt`:

```kotlin
package mihon.desktop.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

class LegacyMigrationTest {
    private val root = createTempDirectory("José Pérez migración")
    private val roaming = root.resolve("Roaming").createDirectories()
    private val local = root.resolve("Local").createDirectories()
    private val documents = root.resolve("OneDrive").resolve("Documentos").createDirectories()
    private val env = mapOf("APPDATA" to roaming.toString(), "LOCALAPPDATA" to local.toString())

    private fun oldSettings(storageDir: String?) {
        val settings = roaming.resolve("MangaReader/settings").createDirectories()
        val properties = Properties().apply {
            setProperty("library_update_interval", "12")
            if (storageDir != null) setProperty("__APP_STATE_storage_dir", storageDir)
        }
        settings.resolve("app.mangareader_preferences.xml").outputStream().use { properties.storeToXML(it, null) }
    }

    private fun newSettings(): Properties = Properties().apply {
        roaming.resolve("Turboyomi/settings/app.turboyomi_preferences.xml").inputStream().use { loadFromXML(it) }
    }

    @Test
    fun `MangaReader's folders and settings move to Turboyomi, and a default storage folder follows`() {
        oldSettings(storageDir = documents.resolve("MangaReader").toUri().toString())
        roaming.resolve("MangaReader/db").createDirectories().resolve("tachiyomi.db").writeText("db")
        local.resolve("MangaReader/cef").createDirectories().resolve("x").writeText("cef")
        documents.resolve("MangaReader/downloads").createDirectories().resolve("c.cbz").writeText("cbz")

        LegacyMigration.run(env) { documents } shouldBe LegacyMigration.Result.Migrated

        roaming.resolve("Turboyomi/db/tachiyomi.db").readText() shouldBe "db"
        local.resolve("Turboyomi/cef/x").readText() shouldBe "cef"
        documents.resolve("Turboyomi/downloads/c.cbz").readText() shouldBe "cbz"
        roaming.resolve("MangaReader").exists() shouldBe false
        newSettings().getProperty("library_update_interval") shouldBe "12"
        Path.of(java.net.URI(newSettings().getProperty("__APP_STATE_storage_dir"))) shouldBe documents.resolve("Turboyomi")
    }

    @Test
    fun `a storage folder of the user's choosing stays where it is`() {
        val elsewhere = root.resolve("D Mangas").createDirectories()
        oldSettings(storageDir = elsewhere.toUri().toString())

        LegacyMigration.run(env) { documents } shouldBe LegacyMigration.Result.Migrated

        Path.of(java.net.URI(newSettings().getProperty("__APP_STATE_storage_dir"))) shouldBe elsewhere
        documents.resolve("Turboyomi").exists() shouldBe false
    }

    @Test
    fun `a storage dir written in another uri form is still recognized as the default`() {
        // file:/C:/... as File.toURI writes it, against file:///C:/... from Path.toUri
        oldSettings(storageDir = documents.resolve("MangaReader").toFile().toURI().toString())
        documents.resolve("MangaReader").createDirectories()

        LegacyMigration.run(env) { documents } shouldBe LegacyMigration.Result.Migrated

        documents.resolve("Turboyomi").exists() shouldBe true
    }

    @Test
    fun `nothing moves once Turboyomi has its own folder`() {
        oldSettings(storageDir = null)
        roaming.resolve("Turboyomi").createDirectories()

        LegacyMigration.run(env) { documents } shouldBe LegacyMigration.Result.NotNeeded

        roaming.resolve("MangaReader").exists() shouldBe true
    }

    @Test
    fun `a folder in use puts everything back as it was`() {
        oldSettings(storageDir = null)
        val db = local.resolve("MangaReader/db").createDirectories().resolve("open.db").apply { writeText("x") }
        // RandomAccessFile opens without FILE_SHARE_DELETE, so Windows refuses to move the folder holding it
        java.io.RandomAccessFile(db.toFile(), "rw").use {
            LegacyMigration.run(env) { documents } shouldBe LegacyMigration.Result.Failed
        }

        roaming.resolve("MangaReader/settings/app.mangareader_preferences.xml").exists() shouldBe true
        roaming.resolve("Turboyomi").exists() shouldBe false
        local.resolve("MangaReader/db/open.db").exists() shouldBe true
    }

    @Test
    fun `the development home skips the migration`() {
        oldSettings(storageDir = null)

        LegacyMigration.run(env + ("TURBOYOMI_HOME" to root.resolve("dev").toString())) { documents } shouldBe
            LegacyMigration.Result.NotNeeded
    }
}
```

`AppDirsTest`: los nombres esperados `MangaReader` pasan a `Turboyomi`, y `MANGAREADER_HOME` a `TURBOYOMI_HOME`. Los tests de protocolo, login y backup que nombran `mangareader://`, `app.mangareader://`, `app.mangareader_…` o `MangaReader.exe` pasan a `turboyomi://`, `app.turboyomi://`, `app.turboyomi_…` y `Turboyomi.exe`, también en `Software\TurboyomiTest-…`. Se hace con un script de reemplazo, revisando el diff.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.LegacyMigrationTest" --tests "mihon.desktop.core.AppDirsTest" :desktop-app:test --tests "mihon.desktop.app.track.*"`
Expected: FAIL. `LegacyMigration` no existe, y los nombres siguen siendo los viejos.

- [ ] **Step 3: Implementación**

`AppDirs.kt`: `APP_NAME = "Turboyomi"` y `HOME_ENV = "TURBOYOMI_HOME"`. El comentario "Provisional product name…" pasa a "The product name; the only place it is spelled out", y el KDoc de `createStorageIfChosen` pasa a decir "an empty Turboyomi folder". Se añade:

```kotlin
        /** The development name, for LegacyMigration (spec 6a §3 A). */
        const val LEGACY_APP_NAME = "MangaReader"
```

`Platform.kt`: `APPLICATION_ID = "app.turboyomi"`. Sigue en `DesktopBuildInfo`, que la Task 2 genera.

`TrackLoginHandler.SCHEMES` pasa a `setOf("turboyomi", "app.turboyomi")`. En las APIs, `"mangareader://<x>-auth"` pasa a `"turboyomi://<x>-auth"` y `"app.mangareader://comick-auth"` a `"app.turboyomi://comick-auth"`. `ProtocolRegistrar` escribe `"URL:Turboyomi"` como valor por defecto. En `tracker-keys.example.properties`, los redirects pasan a `turboyomi://<service>-auth` y `app.turboyomi://comick-auth`.

`LegacyMigration.kt`:

```kotlin
package mihon.desktop.core

import logcat.LogPriority
import logcat.logcat
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import javax.swing.filechooser.FileSystemView
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.io.path.outputStream

/**
 * Brings the development build's data ("MangaReader") over to Turboyomi on the first start (spec 6a §3 A): its two
 * app folders, its settings file, and the storage folder when it is still the default one. All or nothing: a move
 * that fails puts back what had moved, and the app starts fresh with a warning.
 */
object LegacyMigration {
    enum class Result { NotNeeded, Migrated, Failed }

    private const val STORAGE_KEY = "__APP_STATE_storage_dir"

    fun run(
        env: Map<String, String> = System.getenv(),
        documents: () -> Path = { FileSystemView.getFileSystemView().defaultDirectory.toPath() },
    ): Result {
        if (env[AppDirs.HOME_ENV] != null) return Result.NotNeeded
        val roaming = env["APPDATA"]?.let(::Path) ?: return Result.NotNeeded
        val local = env["LOCALAPPDATA"]?.let(::Path) ?: return Result.NotNeeded
        val oldRoaming = roaming.resolve(AppDirs.LEGACY_APP_NAME)
        val newRoaming = roaming.resolve(AppDirs.APP_NAME)
        if (!oldRoaming.exists() || newRoaming.exists()) return Result.NotNeeded

        val oldSettings = oldRoaming.resolve("settings").resolve("app.mangareader_preferences.xml")
        val properties = Properties().apply {
            if (oldSettings.exists()) oldSettings.inputStream().use { loadFromXML(it) }
        }
        val oldStorage = documents().resolve(AppDirs.LEGACY_APP_NAME)
        val newStorage = documents().resolve(AppDirs.APP_NAME)
        val storedStorage = properties.getProperty(STORAGE_KEY)?.let { runCatching { Path.of(URI(it)) }.getOrNull() }
        val storageIsDefault = storedStorage == null || storedStorage == oldStorage

        val moves = buildList {
            add(oldRoaming to newRoaming)
            local.resolve(AppDirs.LEGACY_APP_NAME).takeIf { it.exists() }?.let { add(it to local.resolve(AppDirs.APP_NAME)) }
            if (storageIsDefault && oldStorage.exists() && !newStorage.exists()) add(oldStorage to newStorage)
        }
        val done = mutableListOf<Pair<Path, Path>>()
        return try {
            moves.forEach { (from, to) ->
                Files.move(from, to)
                done += from to to
            }
            val settings = newRoaming.resolve("settings")
            val moved = settings.resolve("app.mangareader_preferences.xml")
            if (moved.exists()) {
                if (storedStorage == oldStorage) properties.setProperty(STORAGE_KEY, newStorage.toUri().toString())
                settings.resolve("app.turboyomi_preferences.xml").outputStream().use { properties.storeToXML(it, null) }
                Files.delete(moved)
            }
            Result.Migrated
        } catch (e: Exception) {
            logcat("LegacyMigration", LogPriority.ERROR) { "Couldn't bring MangaReader's data over: $e" }
            // Put back what had moved, newest first, so nothing ends up split between the two names
            done.asReversed().forEach { (from, to) -> runCatching { Files.move(to, from) } }
            Result.Failed
        }
    }
}
```

`Files.move` de una carpeta sin `REPLACE_EXISTING` falla si el destino existe, y no copia entre volúmenes distintos. En ese caso falla y se deshace todo, sin copias a medias.

`Main.kt`, al principio de `main`, antes de `AppDirs.fromEnvironment()`:

```kotlin
    // The development build's data comes over before anything opens it (spec 6a §3 A)
    val migration = LegacyMigration.run()
```

Tras `AppMessages.install()`:

```kotlin
    if (migration == LegacyMigration.Result.Failed) {
        AppMessages.show(graph.context.stringResource(MR.strings.desktop_legacy_migration_failed))
    }
```

Strings, en base y es:

```xml
    <string name="desktop_legacy_migration_failed">Couldn\'t bring over MangaReader\'s data; close any other copy of the app and restart it</string>
```

```xml
    <string name="desktop_legacy_migration_failed">No se pudieron traer los datos de MangaReader; cierra otras copias de la app y reiníciala</string>
```

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-core:test :desktop-app:test --tests "mihon.desktop.app.track.*" --tests "mihon.desktop.app.backup.*" --tests "mihon.desktop.app.platform.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

Run: `grep -rn "mangareader\|MangaReader\|MANGAREADER" desktop-app/src desktop-core/src --include=*.kt`
Expected: solo `LEGACY_APP_NAME`, `LegacyMigration` (`app.mangareader_preferences.xml`) y su test.

- [ ] **Step 5: Commit**

```bash
git add desktop-app desktop-core i18n tracker-keys.example.properties
git commit -m "feat: the app is Turboyomi, and MangaReader's data comes over on the first start

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: versión generada e icono

**Files:**
- Create:
  - `desktop-app/src/main/resources/turboyomi.svg`;
  - `desktop-app/src/main/kotlin/mihon/desktop/app/packaging/IconRenderer.kt`;
  - `desktop-app/src/test/kotlin/mihon/desktop/app/packaging/IconRendererTest.kt`;
  - `desktop-core/src/test/kotlin/mihon/desktop/core/DesktopBuildInfoTest.kt`.
- Modify:
  - `desktop-core/build.gradle.kts` (tarea `generateBuildInfo`);
  - `desktop-core/src/main/kotlin/mihon/desktop/core/Platform.kt` (sin el `object DesktopBuildInfo` escrito a mano);
  - `desktop-app/build.gradle.kts` (tarea `iconIco`);
  - `mihon/desktop/app/Main.kt` (icono de la ventana);
  - `mihon/desktop/app/tray/AppTray.kt` (icono de la bandeja, sin `TintedPainter`).

**Interfaces:**
- Produces:
  - `DesktopBuildInfo` (generado): `VERSION_NAME: String`, `VERSION_CODE: Int`, `COMMIT_SHA: String` y `APPLICATION_ID: String`.
  - `IconRenderer.ico(svg: ByteArray, sizes: List<Int>): ByteArray`.
  - `desktop-app/build/packaging/turboyomi.ico`, que consume la Task 3.

- [ ] **Step 1: Escribir los tests**

`DesktopBuildInfoTest.kt`:

```kotlin
package mihon.desktop.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DesktopBuildInfoTest {
    private fun git(vararg args: String) =
        ProcessBuilder("git", *args).redirectErrorStream(true).start().inputStream.bufferedReader().readText().trim()

    @Test
    fun `the version comes from a v tag, 0_1_0 without one, and the commit is the checked out one`() {
        val tag = git("describe", "--tags", "--match", "v*", "--abbrev=0")
        val expected = Regex("""^v(\d+\.\d+\.\d+)$""").find(tag)?.groupValues?.get(1) ?: "0.1.0"

        DesktopBuildInfo.VERSION_NAME shouldBe expected
        DesktopBuildInfo.COMMIT_SHA shouldBe git("rev-parse", "--short", "HEAD")
        DesktopBuildInfo.APPLICATION_ID shouldBe "app.turboyomi"
    }
}
```

`IconRendererTest.kt`:

```kotlin
package mihon.desktop.app.packaging

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

class IconRendererTest {
    @Test
    fun `the ico holds the six sizes, each a png of its size`() {
        val svg = javaClass.getResourceAsStream("/turboyomi.svg")!!.readBytes()
        val sizes = listOf(16, 24, 32, 48, 64, 256)

        val ico = IconRenderer.ico(svg, sizes)

        val header = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN)
        header.getShort(2).toInt() shouldBe 1 // icon
        header.getShort(4).toInt() shouldBe sizes.size
        sizes.forEachIndexed { i, size ->
            val entry = 6 + i * 16
            val length = header.getInt(entry + 8)
            val offset = header.getInt(entry + 12)
            val image = ImageIO.read(ico.copyOfRange(offset, offset + length).inputStream())
            image.width shouldBe size
            image.height shouldBe size
        }
    }
}
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.DesktopBuildInfoTest" :desktop-app:test --tests "mihon.desktop.app.packaging.*"`
Expected: FAIL. `COMMIT_SHA` no existe, y no hay ni `IconRenderer` ni SVG.

- [ ] **Step 3: Implementación**

`desktop-core/build.gradle.kts`:

```kotlin
// DesktopBuildInfo from git (spec 6a §2): the version of the last v* tag (Mihon's upstream-base tag doesn't count),
// 0.1.0 without one; the code orders versions for the installer
val gitTag = providers.exec {
    commandLine("git", "describe", "--tags", "--match", "v*", "--abbrev=0")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }
val gitCommit = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }

val generateBuildInfo by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildinfo")
    inputs.property("tag", gitTag)
    inputs.property("commit", gitCommit)
    outputs.dir(out)
    doLast {
        val version = Regex("""^v(\d+)\.(\d+)\.(\d+)$""").find(gitTag.get())?.groupValues?.drop(1)
            ?.map(String::toInt) ?: listOf(0, 1, 0)
        val file = out.get().file("mihon/desktop/core/DesktopBuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package mihon.desktop.core
            |
            |/** Generated by :desktop-core:generateBuildInfo from git (spec 6a §2). */
            |object DesktopBuildInfo {
            |    const val VERSION_NAME = "${version.joinToString(".")}"
            |    const val VERSION_CODE = ${version[0] * 10_000 + version[1] * 100 + version[2]}
            |    const val COMMIT_SHA = "${gitCommit.get()}"
            |    const val APPLICATION_ID = "app.turboyomi"
            |}
            |""".trimMargin(),
        )
    }
}
kotlin.sourceSets.main { kotlin.srcDir(generateBuildInfo) }
```

Para `0.1.0`, `VERSION_CODE` vale 100 y no 1 (desviación 3).

`Platform.kt`: se borra el `object DesktopBuildInfo { … }` escrito a mano, con su `ponytail`.

`turboyomi.svg` (256×256, un libro abierto azul con un rayo amarillo):

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">
  <rect width="256" height="256" rx="56" fill="#1E293B"/>
  <path d="M40 70 Q84 54 124 74 V202 Q84 182 40 198 Z" fill="#3B82F6"/>
  <path d="M216 70 Q172 54 132 74 V202 Q172 182 216 198 Z" fill="#60A5FA"/>
  <path d="M146 40 L98 140 H130 L112 220 L170 112 H138 L158 40 Z" fill="#FACC15" stroke="#1E293B" stroke-width="6"
        stroke-linejoin="round"/>
</svg>
```

`IconRenderer.kt`:

```kotlin
package mihon.desktop.app.packaging

import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Turns the app's SVG into a Windows .ico with PNG images (spec 6a §2); the build runs it for the exe and the MSI. */
object IconRenderer {
    fun ico(svg: ByteArray, sizes: List<Int>): ByteArray {
        val pngs = sizes.map { png(svg, it) }
        val header = ByteBuffer.allocate(6 + 16 * sizes.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(sizes.size.toShort())
        var offset = header.capacity()
        sizes.zip(pngs).forEach { (size, png) ->
            // 256 is written as 0 in the one-byte width and height
            header.put((size % 256).toByte()).put((size % 256).toByte()).put(0).put(0)
            header.putShort(1).putShort(32).putInt(png.size).putInt(offset)
            offset += png.size
        }
        return ByteArrayOutputStream().apply {
            write(header.array())
            pngs.forEach(::write)
        }.toByteArray()
    }

    private fun png(svg: ByteArray, size: Int): ByteArray {
        val dom = SVGDOM(Data.makeFromBytes(svg))
        dom.setContainerSize(size.toFloat(), size.toFloat())
        val surface = Surface.makeRasterN32Premul(size, size)
        dom.render(surface.canvas)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val (svg, out) = args
        File(out).apply { parentFile.mkdirs() }.writeBytes(ico(File(svg).readBytes(), listOf(16, 24, 32, 48, 64, 256)))
    }
}
```

Si `SVGDOM.render` no escala al tamaño del contenedor, porque el SVG no lleva `width` ni `height`, se dibuja con `canvas.scale(size / 256f, size / 256f)` y `setContainerSize(256f, 256f)`. Se apunta como ruling.

`desktop-app/build.gradle.kts`:

```kotlin
// The exe's and the MSI's icon, from the app's SVG (spec 6a §2)
val iconIco by tasks.registering(JavaExec::class) {
    val svg = layout.projectDirectory.file("src/main/resources/turboyomi.svg")
    val ico = layout.buildDirectory.file("packaging/turboyomi.ico")
    inputs.file(svg)
    outputs.file(ico)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "mihon.desktop.app.packaging.IconRenderer"
    args(svg.asFile.path, ico.get().asFile.path)
}
```

`Main.kt`: `Window(…, icon = painterResource("turboyomi.svg"), …)`, con `// desktop: the app's own icon (spec 6a §2)` y el import `androidx.compose.ui.res.painterResource`.

`AppTray.kt`: el icono pasa a `val icon = painterResource("turboyomi.svg")`. Se borran `TintedPainter`, `TRAY_TINT`, el `rememberVectorPainter` del símbolo y su `ponytail`.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-core:test :desktop-app:test --tests "mihon.desktop.app.packaging.*" --tests "mihon.desktop.app.tray.*" :desktop-app:iconIco`
Expected:
- PASS;
- `desktop-app/build/packaging/turboyomi.ico` existe;
- el explorador de Windows lo muestra con el icono.

- [ ] **Step 5: Commit**

```bash
git add desktop-core desktop-app
git commit -m "feat: version and commit from git, Turboyomi's icon for the window, the tray and the exe

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: runtime recortado, `--smoke` y el ZIP

**Files:**
- Create:
  - `desktop-app/src/main/kotlin/mihon/desktop/app/startup/Smoke.kt`;
  - `desktop-app/src/test/kotlin/mihon/desktop/app/startup/SmokeTest.kt`.
- Modify:
  - `mihon/desktop/app/Main.kt` (`--smoke`);
  - `desktop-app/build.gradle.kts` (tareas `jlinkRuntime`, `appImage`, `packageZip`);
  - `compat-harness/build.gradle.kts` (propiedad `harnessJava`).

**Interfaces:**
- Consumes: de la Task 2, `iconIco` y `DesktopBuildInfo.VERSION_NAME`.
- Produces:
  - `Smoke.run(home: Path): Boolean`;
  - las tareas `:desktop-app:appImage` (sale en `build/packaging/image/Turboyomi`) y `:desktop-app:packageZip` (sale en `build/packaging/Turboyomi-<version>.zip`);
  - `:desktop-app:jlinkRuntime` (sale en `build/packaging/runtime`).

- [ ] **Step 1: Escribir el test**

```kotlin
package mihon.desktop.app.startup

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class SmokeTest {
    @Test
    fun `the smoke check builds the whole app graph on fresh data and passes`() {
        Smoke.run(createTempDirectory("Humo José")) shouldBe true
    }
}
```

- [ ] **Step 2: Comprobar que falla**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.startup.SmokeTest"`
Expected: FAIL de compilación (`Smoke` no existe).

- [ ] **Step 3: Implementación**

`Smoke.kt`:

```kotlin
package mihon.desktop.app.startup

import dev.zacsweers.metro.createGraphFactory
import mihon.desktop.app.DesktopAppGraph
import mihon.desktop.app.local.startStorage
import mihon.desktop.core.AppDirs
import mihon.desktop.core.Platform
import java.nio.file.Path

/**
 * `Turboyomi.exe --smoke` (spec 6a §2): builds the app graph on fresh data in [home] with no window and reports
 * whether it worked, which proves the packaged runtime has the modules the app itself needs.
 */
object Smoke {
    fun run(home: Path): Boolean = runCatching {
        val graph = Platform.start(AppDirs(home.resolve("data"), home.resolve("local"))) { context, dirs, debug ->
            createGraphFactory<DesktopAppGraph.Factory>().create(context, dirs, debug)
        }
        graph.startStorage()
        graph.libraryPreferences.autoUpdateInterval.get()
        true
    }.getOrElse {
        it.printStackTrace()
        false
    }
}
```

Los imports de `DesktopAppGraph` y `startStorage` se ajustan a los que usa `Main`.

`Main.kt`, primera línea de `main`, antes de la migración:

```kotlin
    // Packaging's check (spec 6a §2): fresh data, no window, no single-instance lock
    if ("--smoke" in args) {
        val ok = Smoke.run(kotlin.io.path.createTempDirectory("turboyomi-smoke"))
        println(if (ok) "OK" else "FAILED")
        exitProcess(if (ok) 0 else 1)
    }
```

`desktop-app/build.gradle.kts`, las tareas de empaquetado:

```kotlin
// Packaging (spec 6a §2): the app's jars, a runtime cut down with jlink, and jpackage's image of them
val packagingDir = layout.buildDirectory.dir("packaging")
val appJars by tasks.registering(Sync::class) {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(packagingDir.map { it.dir("input") })
}

val javaHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(23) }
    .map { it.metadata.installationPath.asFile }

val jlinkRuntime by tasks.registering(Exec::class) {
    dependsOn(appJars)
    val runtime = packagingDir.map { it.dir("runtime") }
    outputs.dir(runtime)
    doFirst {
        runtime.get().asFile.deleteRecursively()
        val jars = packagingDir.get().dir("input").asFile.listFiles()!!.filter { it.extension == "jar" }
        val deps = ProcessBuilder(
            javaHome.get().resolve("bin/jdeps").path, "--ignore-missing-deps", "--multi-release", "23",
            "--print-module-deps", "--class-path", jars.joinToString(";"), jars.first { it.name.startsWith("desktop-app") }.path,
        ).redirectErrorStream(true).start().inputStream.bufferedReader().readText().trim().lines().last()
        // What extensions load on top of the app's own needs (spec 6a §2)
        val extra = "java.desktop,java.net.http,java.sql,jdk.crypto.ec,jdk.unsupported,jdk.charsets,jdk.localedata," +
            "java.naming,java.management"
        commandLine(
            javaHome.get().resolve("bin/jlink").path, "--add-modules", "$deps,$extra", "--strip-debug",
            "--no-header-files", "--no-man-pages", "--output", runtime.get().asFile.path,
        )
    }
}

val appImage by tasks.registering(Exec::class) {
    dependsOn(appJars, jlinkRuntime, iconIco)
    val image = packagingDir.map { it.dir("image") }
    outputs.dir(image)
    doFirst {
        image.get().asFile.deleteRecursively()
        commandLine(
            javaHome.get().resolve("bin/jpackage").path, "--type", "app-image",
            "--name", "Turboyomi", "--app-version", mihon.desktop.core.DesktopBuildInfo.VERSION_NAME,
            "--input", packagingDir.get().dir("input").asFile.path,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", "mihon.desktop.app.MainKt",
            "--runtime-image", packagingDir.get().dir("runtime").asFile.path,
            "--icon", packagingDir.get().file("turboyomi.ico").asFile.path,
            "--java-options", "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
            "--java-options", "--add-exports=java.desktop/sun.awt.windows=ALL-UNNAMED",
            "--dest", image.get().asFile.path,
        )
    }
}

val packageZip by tasks.registering(Zip::class) {
    dependsOn(appImage)
    from(packagingDir.map { it.dir("image") })
    archiveFileName = "Turboyomi-${appVersion}.zip"
    destinationDirectory = packagingDir
}
```

`DesktopBuildInfo` no se ve desde el build script, porque es código generado del proyecto. Por eso la versión se calcula también aquí, con la misma regla de la Task 2:

```kotlin
// The same rule as :desktop-core:generateBuildInfo: the last v* tag, 0.1.0 without one
val appVersion: String = providers.exec {
    commandLine("git", "describe", "--tags", "--match", "v*", "--abbrev=0")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().let { Regex("""^v(\d+\.\d+\.\d+)$""").find(it)?.groupValues?.get(1) ?: "0.1.0" }
```

En `appImage`, `--app-version` usa `appVersion`. La duplicación de la regla se apunta como ruling, porque son dos líneas: si una cambia, la otra también.

`compat-harness/build.gradle.kts`:

```kotlin
// The harness on the packaged runtime (spec 6a §3 C): -PharnessJava=<image>/runtime/bin/java.exe
providers.gradleProperty("harnessJava").orNull?.let { java -> tasks.named<JavaExec>("run") { executable = java } }
```

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.startup.SmokeTest"`
Expected: PASS.

Run: `./gradlew :desktop-app:packageZip`
Expected:
- existe `desktop-app/build/packaging/image/Turboyomi/Turboyomi.exe`;
- existe `desktop-app/build/packaging/Turboyomi-0.1.0.zip`.

Run: `desktop-app/build/packaging/image/Turboyomi/runtime/bin/java.exe --list-modules`
Expected: incluye los 9 módulos de Global Constraints.

Run: `desktop-app/build/packaging/image/Turboyomi/Turboyomi.exe --smoke`
Expected: imprime `OK` y sale con 0. Un exe de ventana de `jpackage` puede no tener consola: si no imprime nada, se comprueba solo el código de salida con `echo $?` y se apunta como ruling.

Run: `./gradlew :compat-harness:run -PharnessJava=$PWD/desktop-app/build/packaging/image/Turboyomi/runtime/bin/java.exe --args="--repo https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json --limit 20 --threshold 0"`
Expected: el informe sale sin errores de clase o módulo que falte (`NoClassDefFoundError`, `ClassNotFoundException` de `java.*`/`javax.*`). Los fallos de red o de sitio no cuentan. La URL del repo es la de las ejecuciones anteriores del harness (memoria, 1.1).

- [ ] **Step 5: Commit**

```bash
git add desktop-app compat-harness
git commit -m "feat(desktop-app): a jlink runtime, the app image and its zip, and the --smoke check

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: el MSI con el protocolo y la verificación completa

**Files:**
- Create: `desktop-app/packaging/msi/main.wxs`, a partir del de `jpackage` más el protocolo.
- Modify: `desktop-app/build.gradle.kts` (tarea `packageMsi`).

**Interfaces:**
- Consumes: de la Task 3, `appJars`, `jlinkRuntime`, `iconIco` y `appVersion`.
- Produces: `desktop-app/build/packaging/Turboyomi-<version>.msi`.

- [ ] **Step 1: WiX**

Run: `winget install --id WiXToolset.WiXToolset --version 3.14.1 --accept-package-agreements --accept-source-agreements`
Expected: instalado. `candle.exe -?` responde. Si el PATH no lo recoge, se añade `C:\Program Files (x86)\WiX Toolset v3.14\bin` a la variable de entorno de la tarea `packageMsi`.

- [ ] **Step 2: El `main.wxs` con el protocolo**

Se saca el de `jpackage` con una ejecución con `--temp` (`jpackage --type msi … --temp build/packaging/msi-temp --verbose`), copiando `build/packaging/msi-temp/config/main.wxs` a `desktop-app/packaging/msi/main.wxs`. Dentro del `<Product>`, se añade:

```xml
    <!-- desktop: turboyomi:// and app.turboyomi:// for OAuth redirects, removed on uninstall (spec 6a §3 B) -->
    <DirectoryRef Id="TARGETDIR">
      <Component Id="TurboyomiProtocol" Guid="5F0C2D4E-8B1A-4C7E-9E3D-6A2B1F0C7D11">
        <RegistryKey Root="HKCU" Key="Software\Classes\turboyomi">
          <RegistryValue Type="string" Value="URL:Turboyomi" KeyPath="yes"/>
          <RegistryValue Name="URL Protocol" Type="string" Value=""/>
          <RegistryKey Key="shell\open\command">
            <RegistryValue Type="string" Value="&quot;[INSTALLDIR]Turboyomi.exe&quot; &quot;%1&quot;"/>
          </RegistryKey>
        </RegistryKey>
        <RegistryKey Root="HKCU" Key="Software\Classes\app.turboyomi">
          <RegistryValue Type="string" Value="URL:Turboyomi"/>
          <RegistryValue Name="URL Protocol" Type="string" Value=""/>
          <RegistryKey Key="shell\open\command">
            <RegistryValue Type="string" Value="&quot;[INSTALLDIR]Turboyomi.exe&quot; &quot;%1&quot;"/>
          </RegistryKey>
        </RegistryKey>
      </Component>
    </DirectoryRef>
```

y un `<ComponentRef Id="TurboyomiProtocol"/>` dentro del `<Feature>` que ya tiene el `main.wxs`. Los nombres `TARGETDIR`, `INSTALLDIR` y el `Id` del `Feature` son los del fichero sacado: si `jpackage` los llama de otra forma, se usan los suyos y se apunta como ruling.

- [ ] **Step 3: La tarea `packageMsi`**

```kotlin
// The per-user MSI (spec 6a §3 B); WiX 3.14 must be on this machine (winget install WiXToolset.WiXToolset)
val packageMsi by tasks.registering(Exec::class) {
    dependsOn(appJars, jlinkRuntime, iconIco)
    val out = packagingDir
    doFirst {
        packagingDir.get().asFile.listFiles()?.filter { it.extension == "msi" }?.forEach { it.delete() }
        commandLine(
            javaHome.get().resolve("bin/jpackage").path, "--type", "msi",
            "--name", "Turboyomi", "--app-version", appVersion, "--vendor", "Turboyomi",
            "--input", packagingDir.get().dir("input").asFile.path,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", "mihon.desktop.app.MainKt",
            "--runtime-image", packagingDir.get().dir("runtime").asFile.path,
            "--icon", packagingDir.get().file("turboyomi.ico").asFile.path,
            "--java-options", "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
            "--java-options", "--add-exports=java.desktop/sun.awt.windows=ALL-UNNAMED",
            "--win-per-user-install", "--win-menu", "--win-shortcut",
            // Fixed so a newer MSI replaces the installed one (spec 6a §3 B)
            "--win-upgrade-uuid", "7C3E9A52-41D8-4B6F-A0E2-9D5B3C8F1A64",
            "--resource-dir", layout.projectDirectory.dir("packaging/msi").asFile.path,
            "--dest", out.get().asFile.path,
        )
    }
}
```

`jpackage` nombra el MSI `Turboyomi-<version>.msi`.

- [ ] **Step 4: Verificación del MSI**

Run: `./gradlew :desktop-app:packageMsi`
Expected: existe `desktop-app/build/packaging/Turboyomi-0.1.0.msi`.

Run (PowerShell):

```powershell
$msi = (Resolve-Path desktop-app\build\packaging\Turboyomi-0.1.0.msi).Path
Start-Process msiexec -ArgumentList "/i `"$msi`" /qn" -Wait
Test-Path "$env:LOCALAPPDATA\Programs\Turboyomi\Turboyomi.exe"
(Get-ItemProperty 'HKCU:\Software\Classes\turboyomi\shell\open\command').'(default)'
Start-Process msiexec -ArgumentList "/i `"$msi`" /qn" -Wait   # the same version again: a repair, nothing doubled
Start-Process msiexec -ArgumentList "/x `"$msi`" /qn" -Wait
Test-Path "$env:LOCALAPPDATA\Programs\Turboyomi"
Test-Path 'HKCU:\Software\Classes\turboyomi'
Test-Path "$env:APPDATA\Turboyomi"
```

Expected, en orden:
- `True`;
- el comando con `"…\Programs\Turboyomi\Turboyomi.exe" "%1"`;
- tras desinstalar: `False` y `False`;
- y `True` para los datos, si la app llegó a arrancar (el MSI no los toca).

Al arrancar, la app reescribe la clave del protocolo, y el MSI la borra al desinstalar igual. La instalación se hace en el perfil real del usuario; la desinstalación final la deja limpia.

- [ ] **Step 5: La versión con tag**

Run:

```bash
git tag v9.9.9 && ./gradlew :desktop-core:generateBuildInfo -q && grep VERSION_NAME desktop-core/build/generated/buildinfo/mihon/desktop/core/DesktopBuildInfo.kt; git tag -d v9.9.9 && ./gradlew :desktop-core:generateBuildInfo -q
```

Expected:
- `VERSION_NAME = "9.9.9"` con el tag;
- el tag borrado, y la regeneración vuelve a `0.1.0` (desviación 1).

- [ ] **Step 6: Suite y checks**

Run: `./gradlew test spotlessCheck`, `bash scripts/check-no-android.sh` y `bash scripts/check-licenses.sh`
Expected: todo OK, con unos 500 tests.

Run: `grep -rn "mangareader\|MangaReader\|MANGAREADER" --include=*.kt --include=*.kts --include=*.properties desktop-app desktop-core compat-harness tracker-keys.example.properties`
Expected: solo la migración y su test.

- [ ] **Step 7: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): the per-user MSI, registering turboyomi:// and removing it on uninstall

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 8: Prueba manual (spec §5), para el usuario**

1. Instalar `Turboyomi-0.1.0.msi` con doble clic, abrir desde el menú Inicio y ver la biblioteca migrada desde MangaReader.
2. Crear un tag `v0.1.1`, rehacer el MSI, instalarlo encima y comprobar que no se pierde nada.
3. Desinstalar desde Programas y características y comprobar que los datos quedan.
4. Descomprimir `Turboyomi-0.1.0.zip` en otra carpeta y abrir `Turboyomi.exe`.
