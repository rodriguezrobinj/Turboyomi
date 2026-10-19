# Subproyecto 1: Plataforma base + compatibilidad con extensiones. Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compilar el núcleo de Mihon (`source-api`, `core`, `domain`, `data`, `i18n`) como librerías JVM para Windows, cargar extensiones APK de Mihon mediante dex2jar y AndroidCompat, y medir la compatibilidad con un harness CLI ejecutado en CI.

**Architecture:** El repo es un fork de Mihon. Los módulos Android se convierten a `kotlin("jvm")` y compilan contra los stubs de Android de Suwayomi (AndroidCompat, MPL-2.0, vendorizado). Unos pocos shims Apache reemplazan las librerías solo-Android (`logcat`, `unifile`, `androidx.core`). `extension-loader` contiene la mecánica pura: inspección y firma de APK, dex2jar, ClassLoader, confianza y carga. `desktop-core` es la raíz de composición: rutas, instancia única, grafo Metro, Injekt y gestor de extensiones. `compat-harness` es un CLI que instala las N primeras extensiones de un repo, llama a `getPopularManga(1)` y publica el % de compatibilidad. Este subproyecto no incluye UI; la UI llega en el subproyecto 2.

**Tech Stack:** Kotlin 2.4.21, JDK 21, Gradle (build-logic de Mihon), Metro 1.4.5, Injekt 1.16.1, Koin 4.2.2 (solo dentro de AndroidCompat), SQLDelight 2.4.1 + `sqldelight-androidx-driver` + `androidx.sqlite:sqlite-bundled` (JVM), OkHttp 5.5.0, dex2jar `de.femtopedia.dex2jar` 2.4.38, `apksig` 9.4.1, `apk-parser` 2.6.10, JUnit 5 + kotest-assertions + mockk.

**Spec:** `docs/superpowers/specs/2026-10-18-windows-port-design.md` (secciones 2 y 3; 4.1 en la parte de almacenamiento e instancia única; 6 en la parte de CI).

## Global Constraints

- JDK 21: `java = "21"` en `gradle/mihon.versions.toml`.
- Versiones fijadas por las extensiones: OkHttp `5.5.0`, RxJava `1.3.8`, Jsoup `1.23.2`, kotlinx-serialization `1.11.0`.
- Versiones de lib de extensión soportadas: `SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)`.
- Contrato binario: no se renombra ningún paquete `eu.kanade.tachiyomi.*` ni cambia ninguna firma pública de `source-api` ni de `eu.kanade.tachiyomi.network.*`.
- Huella de firma: SHA-256 en hex minúsculas del certificado DER (`Hash.sha256(cert.encoded)`), igual que Mihon y que el campo `signingKey` de los repos.
- Rutas: datos en `%APPDATA%\<App>` y datos locales en `%LOCALAPPDATA%\<App>`, con `<App>` = `AppDirs.APP_NAME = "MangaReader"` (provisional, definido en un único sitio). La variable de entorno `MANGAREADER_HOME` sustituye ambas rutas (tests, modo portable).
- La app no trae repositorios de extensiones por defecto.
- Licencias:
  - Todo lo copiado de Suwayomi conserva su cabecera MPL-2.0 y vive en `android-compat/` o en `extension-loader/src/main/kotlin/mihon/desktop/extension/vendor/`.
  - El resto es Apache-2.0.
- Código nuevo en el paquete `mihon.desktop.*`. Shims en el paquete exacto de la librería que reemplazan.
- Estilo: ktlint vía spotless (`mihonx.plugins.spotless`) en todos los módulos excepto `android-compat` (código vendorizado sin reformatear).

## Desviaciones del spec (aprobar antes de ejecutar)

1. **Módulos `kotlin("jvm")` en lugar de KMP** (salvo `i18n`, que ya es KMP y pasa a tener un único target `jvm()`). Con un solo target, KMP no aporta nada todavía, y pasar luego de `jvm` a KMP es mecánico.
2. **Driver SQL**: se mantiene `AndroidxSqliteDriver` + `BundledSQLiteDriver`, que soportan JVM, en lugar de `JdbcSqliteDriver`. El código de Mihon solo cambia en el proveedor del driver.
3. **SharedPreferences**: AndroidCompat las guarda como Properties XML en `%APPDATA%\<App>\settings\<nombre>.xml`, no como JSON en `prefs\`.
4. **ClassLoader**: se usa `ChildFirstURLClassLoader` de Suwayomi (primero el classpath de la app, luego el JAR de la extensión). Cumple el objetivo de la lista explícita del spec sin mantener una lista.
5. **Cloudflare/KCEF en tiempo de ejecución pasa al subproyecto 2**, porque necesita ventana. En este subproyecto `CloudflareInterceptor` no se compila. El harness clasifica esos fallos como `BLOCKED` y los excluye del denominador.
6. **El icono de la extensión pasa al subproyecto 2**, donde está la UI que lo usa.
7. **"100 más usadas"**: el índice del repo no trae datos de popularidad. El harness usa `compat-harness/extensions.txt` (un paquete por línea) si existe; si no, toma las 100 primeras con `lang` `all` o `en`, ordenadas por `pkgName`.
8. **Filtro por content warning (`Reason.Filtered`)**: pasa al subproyecto 2, porque necesita la UI de ajustes.

## Review Focus

1. **Rutas no ASCII o con espacios** (usuario `José Pérez`): AppDirs, BD, settings y extensiones deben funcionar. Test en la Task 8 y en la Task 13.
2. **APK descargado corrupto o truncado**: el install falla con error claro y no deja `.jar` ni `.tmp` a medias. Test en la Task 11.
3. **Actualización con firma distinta a la instalada**: se rechaza y la versión instalada queda intacta. Test en la Task 11.
4. **Versiones viejas en disco** (el JAR anterior quedó bloqueado por Windows al actualizar): solo se carga la versión más reciente y las viejas se borran en el siguiente arranque. Test en la Task 11.
5. **Fuente que se cuelga** en `getPopularManga`: el harness corta por timeout y la clasifica como `TIMEOUT` sin colgar CI. Test en la Task 14.

## Mapa de archivos

| Ruta | Responsabilidad |
|---|---|
| `gradle/build-logic/src/main/kotlin/PluginKotlinJvm.kt` | Convención `mihon.plugins.kotlin.jvm` |
| `gradle/build-logic/src/main/kotlin/PluginKotlinMultiplatform.kt` | Convención KMP con target `jvm()` |
| `settings.gradle.kts` | Módulos del subproyecto 1 y repos Maven |
| `android-compat/**` | AndroidCompat de Suwayomi vendorizado (MPL-2.0) |
| `compat-shims/**` | `logcat`, `com.hippo.unifile`, `androidx.core.*`, `androidx.preference.PreferenceManager` |
| `source-api`, `core/common`, `core/metro`, `domain`, `data`, `i18n` | Módulos de Mihon compilados para JVM |
| `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/NetworkHelper.kt` | `NetworkHelper` de escritorio (sin Cloudflare) |
| `extension-loader/**` | `ApkInspector`, `ExtensionManifestParser`, `Dex2Jar`, `vendor/*`, `SourceInstantiator`, `ExtensionTrust`, `DesktopExtensionLoader`, `ExtensionInstaller` |
| `desktop-core/**` | `AppDirs`, `SingleInstance`, `DesktopBindings`, `DesktopGraph`, `DesktopInjektRegistrar`, `Platform`, `AppInfo`, `DesktopExtensionManager`, `DesktopSourceManager` |
| `compat-harness/**` | CLI de compatibilidad e informe |
| `.github/workflows/build.yml`, `.github/workflows/compat.yml`, `scripts/check-licenses.sh` | CI |

---

### Task 1: Importar el historial de Mihon (base del fork)

**Files:**
- Modify: el repo completo (merge de `upstream/main`)
- Create: `NOTICE`
- Modify: `.gitignore`
- Delete: `app/google-services.json`

**Interfaces:**
- Consumes: nada
- Produces: árbol de Mihon en la raíz del repo; tag `upstream-base`

- [ ] **Step 1: Traer el historial de upstream**

```bash
cd C:/Users/HP/OneDrive/Documentos/Proyectos/manga-reader-windows
git remote add upstream https://github.com/mihonapp/mihon.git
git fetch upstream main
git merge upstream/main --allow-unrelated-histories -m "chore: import Mihon upstream history"
git tag upstream-base upstream/main
```

Expected: el merge termina sin conflictos (`docs/superpowers/` no existe en upstream).

- [ ] **Step 2: Verificar el árbol**

Run: `ls settings.gradle.kts source-api domain data && git log --oneline -3`
Expected: los archivos existen y el log muestra el commit de merge sobre el commit del spec.

- [ ] **Step 3: Quitar la configuración de Firebase y añadir NOTICE**

```bash
git rm app/google-services.json
echo "app/google-services.json" >> .gitignore
```

Crear `NOTICE`:

```text
MangaReader (provisional name)

This product is a fork of Mihon (https://github.com/mihonapp/mihon),
Copyright (c) the Mihon Open Source Project, licensed under the Apache License 2.0.

The directory android-compat/ and the files under
extension-loader/src/main/kotlin/mihon/desktop/extension/vendor/ are derived from
Suwayomi-Server (https://github.com/Suwayomi/Suwayomi-Server) at commit
e2ce828abb81f0f5ce1a3fb86194a2746b519c85 and are licensed under the
Mozilla Public License 2.0 (see android-compat/LICENSE).
```

- [ ] **Step 4: Commit**

```bash
git add NOTICE .gitignore
git commit -m "chore: add NOTICE and drop Firebase config from the fork"
```

---

### Task 2: Base de build JVM (build-logic, settings, i18n)

**Files:**
- Create: `gradle/build-logic/src/main/kotlin/PluginKotlinJvm.kt`
- Modify: `gradle/build-logic/build.gradle.kts` (bloque `gradlePlugin`)
- Modify: `gradle/build-logic/src/main/kotlin/PluginKotlinMultiplatform.kt`
- Modify: `gradle/mihon.versions.toml`
- Modify: `settings.gradle.kts`
- Modify: `i18n/build.gradle.kts`
- Test: `i18n/src/jvmTest/kotlin/tachiyomi/i18n/MRSmokeTest.kt`

**Interfaces:**
- Consumes: nada
- Produces: plugin `alias(mihonx.plugins.kotlin.jvm)`; módulo `:i18n` con target JVM que expone `tachiyomi.i18n.MR`

- [ ] **Step 1: Escribir el test que falla**

`i18n/src/jvmTest/kotlin/tachiyomi/i18n/MRSmokeTest.kt`:

```kotlin
package tachiyomi.i18n

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale

class MRSmokeTest {
    @Test
    fun `strings resolve on the JVM`() {
        MR.strings.app_name.localized(Locale.ENGLISH) shouldBe "Mihon"
    }
}
```

- [ ] **Step 2: Añadir el plugin JVM a build-logic**

`gradle/build-logic/src/main/kotlin/PluginKotlinJvm.kt`:

```kotlin
import mihon.gradle.configurations.configureKotlin
import mihon.gradle.extensions.alias
import mihon.gradle.extensions.configureTest
import mihon.gradle.extensions.libs
import mihon.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("UNUSED")
class PluginKotlinJvm : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.kotlin.jvm)
        }

        configureKotlin()
        configureTest()
    }
}
```

En `gradle/build-logic/build.gradle.kts`, dentro de `gradlePlugin { plugins { ... } }`, añadir:

```kotlin
        register("kotlin-jvm") {
            id = mihonx.plugins.kotlin.jvm.get().pluginId
            implementationClass = "PluginKotlinJvm"
        }
```

En `gradle/mihon.versions.toml`, cambiar `java = "17"` por `java = "21"` y añadir en `[plugins]`:

```toml
kotlin-jvm = { id = "mihon.plugins.kotlin.jvm" }
```

- [ ] **Step 3: Pasar el plugin KMP a un único target `jvm()`**

Reemplazar el contenido de `gradle/build-logic/src/main/kotlin/PluginKotlinMultiplatform.kt` por:

```kotlin
import mihon.gradle.configurations.configureKotlin
import mihon.gradle.extensions.alias
import mihon.gradle.extensions.configureTest
import mihon.gradle.extensions.libs
import mihon.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("UNUSED")
class PluginKotlinMultiplatform : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.kotlin.multiplatform)
        }

        configureKotlin()
        configureTest()

        extensions.configure<KotlinMultiplatformExtension> {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            applyDefaultHierarchyTemplate()

            jvm()
        }
    }
}
```

- [ ] **Step 4: Ajustar `i18n/build.gradle.kts`**

Reemplazar el archivo por:

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.moko.resources)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    dependencies {
        api(libs.moko.resources)
    }

    sourceSets {
        jvmTest.dependencies {
            implementation(libs.bundles.test)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

multiplatformResources {
    resourcesPackage.set("tachiyomi.i18n")
}
```

- [ ] **Step 5: Reducir `settings.gradle.kts` a los módulos del subproyecto 1**

Reemplazar el bloque `include(...)` y `rootProject.name`, y añadir repos, para que el archivo quede así:

```kotlin
pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("mihonx") {
            from(files("gradle/mihon.versions.toml"))
        }
    }

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
        // android.jar stubs used by android-compat
        maven(url = "https://github.com/Suwayomi/Suwayomi-Server/raw/android-jar/")
        // JCEF (compile dependency of android-compat's webkit classes)
        maven(url = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        maven(url = "https://jogamp.org/deployment/maven")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "MangaReader"
include(":i18n")
```

El resto de módulos se añaden en las tasks que los portan. Los directorios de Mihon que no se incluyen (`app`, `presentation-*`, `icons`, `core:archive`, `core-metadata`, `source-local`, `telemetry`, `baseline-profile`) se quedan en el repo como referencia para los subproyectos siguientes.

- [ ] **Step 6: Ejecutar el test**

Run: `./gradlew :i18n:jvmTest`
Expected: `MRSmokeTest > strings resolve on the JVM() PASSED` y `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add gradle settings.gradle.kts i18n
git commit -m "build: JVM conventions and i18n on a jvm target"
```

---

### Task 3: Vendorizar AndroidCompat (MPL-2.0)

**Files:**
- Create: `android-compat/**` (copia de `AndroidCompat/` de Suwayomi en `e2ce828abb81f0f5ce1a3fb86194a2746b519c85`)
- Create: `android-compat/config/**` (copia de `AndroidCompat/Config/`)
- Create: `android-compat/LICENSE`, `android-compat/README.md`
- Replace: `android-compat/build.gradle.kts`, `android-compat/config/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`, `settings.gradle.kts`
- Test: `android-compat/src/test/kotlin/AndroidCompatBootTest.kt`

**Interfaces:**
- Consumes: nada
- Produces:
  - `xyz.nulldev.androidcompat.androidCompatModule(): org.koin.core.module.Module`
  - `xyz.nulldev.ts.config.configManagerModule(): Module`
  - `xyz.nulldev.androidcompat.AndroidCompatInitializer().init()`
  - `xyz.nulldev.androidcompat.AndroidCompat().startApp(application: android.app.Application)`
  - System property de raíz: `"suwayomi.tachidesk.config.server.rootDir"`
  - Clases `android.*`, `androidx.preference.*` y `app.cash.quickjs.QuickJs` en el classpath (`api`)

- [ ] **Step 1: Copiar el código fijado a un commit**

```bash
cd "$TEMP" && rm -rf suwa-vendor
git clone --filter=blob:none --sparse https://github.com/Suwayomi/Suwayomi-Server.git suwa-vendor
cd suwa-vendor && git checkout e2ce828abb81f0f5ce1a3fb86194a2746b519c85 && git sparse-checkout set AndroidCompat
cd C:/Users/HP/OneDrive/Documentos/Proyectos/manga-reader-windows
mkdir -p android-compat/config
cp -r "$TEMP/suwa-vendor/AndroidCompat/src" android-compat/
cp -r "$TEMP/suwa-vendor/AndroidCompat/Config/src" android-compat/config/
curl -s https://www.mozilla.org/media/MPL/2.0/index.txt -o android-compat/LICENSE
```

`android-compat/README.md`:

```markdown
# android-compat

Vendored from Suwayomi-Server `AndroidCompat/` and `AndroidCompat/Config/` at commit
`e2ce828abb81f0f5ce1a3fb86194a2746b519c85`. Licensed under MPL-2.0 (see `LICENSE`).

Modifications to these files must stay under MPL-2.0 and be listed below.

## Local modifications

- `build.gradle.kts` files rewritten for this repository's version catalog.
```

- [ ] **Step 2: Añadir las dependencias al catálogo**

En `gradle/libs.versions.toml`, en `[versions]`:

```toml
dex2jar = "2.4.38"
jcef = "144.0.15-g72717cf-chromium-144.0.7559.172-api-1.21-262-b37"
koin = "4.2.2"
polyglot = "25.4.4.1.1"
settings = "1.3.0"
twelvemonkeys = "3.15.3"
```

En `[libraries]`:

```toml
android-stubs = { module = "com.github.Suwayomi:android-jar", version = "1.0.0" }
apk-parser = { module = "net.dongliu:apk-parser", version = "2.6.10" }
apksig = { module = "com.android.tools.build:apksig", version = "9.4.1" }
appdirs = { module = "ca.gosyer:kotlin-multiplatform-appdirs", version = "2.0.0" }
axml = { module = "com.github.Aliucord:axml", version = "ed26565eb0" }
config = { module = "com.typesafe:config", version = "1.4.9" }
config4k = { module = "io.github.config4k:config4k", version = "0.7.0" }
dex2jar-tools = { module = "de.femtopedia.dex2jar:dex-tools", version.ref = "dex2jar" }
dex2jar-translator = { module = "de.femtopedia.dex2jar:dex-translator", version.ref = "dex2jar" }
icu4j = { module = "com.ibm.icu:icu4j", version = "78.3" }
imageio-webp = { module = "com.github.usefulness:webp-imageio", version = "0.11.0" }
jackson-annotations = { module = "com.fasterxml.jackson.core:jackson-annotations", version = "2.22" }
jcef = { module = "org.jetbrains.intellij.deps.jcef:jcef", version.ref = "jcef" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
kotlin-logging = { module = "io.github.oshai:kotlin-logging-jvm", version = "8.0.4" }
kotlinx-coroutines-jdk8 = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8", version.ref = "kotlinx-coroutines" }
logback = { module = "ch.qos.logback:logback-classic", version = "1.6.5" }
polyglot-core = { module = "org.graalvm.polyglot:polyglot", version.ref = "polyglot" }
polyglot-graaljs = { module = "org.graalvm.polyglot:js-community", version.ref = "polyglot" }
settings-core = { module = "com.russhwolf:multiplatform-settings-jvm", version.ref = "settings" }
settings-serialization = { module = "com.russhwolf:multiplatform-settings-serialization-jvm", version.ref = "settings" }
slf4j-api = { module = "org.slf4j:slf4j-api", version = "2.0.20" }
twelvemonkeys-common-image = { module = "com.twelvemonkeys.common:common-image", version.ref = "twelvemonkeys" }
twelvemonkeys-common-io = { module = "com.twelvemonkeys.common:common-io", version.ref = "twelvemonkeys" }
twelvemonkeys-common-lang = { module = "com.twelvemonkeys.common:common-lang", version.ref = "twelvemonkeys" }
twelvemonkeys-imageio-core = { module = "com.twelvemonkeys.imageio:imageio-core", version.ref = "twelvemonkeys" }
twelvemonkeys-imageio-jpeg = { module = "com.twelvemonkeys.imageio:imageio-jpeg", version.ref = "twelvemonkeys" }
twelvemonkeys-imageio-metadata = { module = "com.twelvemonkeys.imageio:imageio-metadata", version.ref = "twelvemonkeys" }
twelvemonkeys-imageio-webp = { module = "com.twelvemonkeys.imageio:imageio-webp", version.ref = "twelvemonkeys" }
xmlpull = { module = "xmlpull:xmlpull", version = "1.1.3.4a" }
```

En `[bundles]`:

```toml
androidcompat = ["kotlin-reflect", "kotlinx-coroutines-core", "kotlinx-coroutines-jdk8", "kotlinx-serialization-json", "kotlinx-serialization-jsonOkio", "kotlinx-serialization-protobuf", "koin-core", "slf4j-api", "logback", "kotlin-logging", "appdirs", "rxJava", "jsoup", "config", "config4k", "dex2jar-translator", "dex2jar-tools", "apk-parser", "axml", "jackson-annotations", "jcef", "okhttp-core"]
polyglot = ["polyglot-core", "polyglot-graaljs"]
settings = ["settings-core", "settings-serialization"]
twelvemonkeys = ["twelvemonkeys-common-lang", "twelvemonkeys-common-io", "twelvemonkeys-common-image", "twelvemonkeys-imageio-core", "twelvemonkeys-imageio-metadata", "twelvemonkeys-imageio-jpeg", "twelvemonkeys-imageio-webp"]
```

- [ ] **Step 3: Escribir los build files**

`android-compat/config/build.gradle.kts`:

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.bundles.androidcompat)
}
```

`android-compat/build.gradle.kts`:

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

sourceSets {
    main {
        java.srcDir("src/main/java")
    }
}

dependencies {
    api(libs.android.stubs)
    api(projects.androidCompat.config)
    implementation(libs.bundles.androidcompat)
    compileOnly(libs.xmlpull)
    compileOnly(libs.apksig)
    compileOnly(libs.androidx.annotation)
    implementation(libs.bundles.polyglot)
    implementation(libs.bundles.settings)
    implementation(libs.icu4j)
    implementation(libs.bundles.twelvemonkeys)
    implementation(libs.imageio.webp)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

Añadir a `settings.gradle.kts`:

```kotlin
include(":android-compat")
include(":android-compat:config")
```

- [ ] **Step 4: Escribir el test de arranque**

`android-compat/src/test/kotlin/AndroidCompatBootTest.kt`:

```kotlin
import android.app.Application
import android.content.Context
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatformTools
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.androidcompat.androidCompatModule
import xyz.nulldev.ts.config.configManagerModule
import kotlin.io.path.createTempDirectory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AndroidCompatBootTest {
    private lateinit var context: Context

    @BeforeAll
    fun boot() {
        val root = createTempDirectory("José Pérez compat")
        System.setProperty("suwayomi.tachidesk.config.server.rootDir", root.toString())
        startKoin { modules(androidCompatModule(), configManagerModule()) }
        AndroidCompatInitializer().init()
        AndroidCompat().startApp(object : Application() {})
        context = KoinPlatformTools.defaultContext().get().get()
    }

    @AfterAll
    fun shutdown() = stopKoin()

    @Test
    fun `shared preferences round-trip to disk`() {
        val prefs = context.getSharedPreferences("source_123", Context.MODE_PRIVATE)
        prefs.edit().putString("quality", "high").commit()

        context.getSharedPreferences("source_123", Context.MODE_PRIVATE)
            .getString("quality", null) shouldBe "high"
    }
}
```

- [ ] **Step 5: Ejecutar el test**

Run: `./gradlew :android-compat:test`
Expected: `AndroidCompatBootTest > shared preferences round-trip to disk() PASSED`.

Si la compilación falla por una dependencia que falta en `bundles.androidcompat`, compara con el bundle `shared` de `gradle/libs.versions.toml` de Suwayomi en el mismo commit, añade la entrada que falta al catálogo y anótala en `android-compat/README.md`.

- [ ] **Step 6: Commit**

```bash
git add android-compat gradle/libs.versions.toml settings.gradle.kts
git commit -m "feat(android-compat): vendor Suwayomi AndroidCompat (MPL-2.0)"
```

---

### Task 4: `compat-shims`, sustitutos JVM de librerías solo-Android

**Files:**
- Create: `compat-shims/build.gradle.kts`
- Create: `compat-shims/src/main/kotlin/logcat/Logcat.kt`
- Create: `compat-shims/src/main/kotlin/com/hippo/unifile/UniFile.kt`
- Create: `compat-shims/src/main/kotlin/androidx/core/content/ContentShims.kt`
- Create: `compat-shims/src/main/kotlin/androidx/core/text/HtmlShims.kt`
- Create: `compat-shims/src/main/kotlin/androidx/preference/PreferenceManager.kt`
- Modify: `settings.gradle.kts`
- Test: `compat-shims/src/test/kotlin/com/hippo/unifile/UniFileTest.kt`
- Test: `compat-shims/src/test/kotlin/logcat/LogcatTest.kt`

**Interfaces:**
- Consumes: `android.content.Context`, `android.content.SharedPreferences`, `android.net.Uri` y `android.text.Html` (de `:android-compat`)
- Produces (mismos FQN que las librerías originales):
  - `logcat.LogPriority`, `Any.logcat(priority, tag, message)`, `logcat(tag, priority, message)`, `Throwable.asLog()`
  - `com.hippo.unifile.UniFile` con `fromFile(File?)` y `fromUri(Context?, Uri?)`
  - `androidx.core.content.edit`, `Context.getSystemService<T>()`, `ContextCompat.getExternalFilesDirs`
  - `androidx.core.text.parseAsHtml`
  - `androidx.preference.PreferenceManager.getDefaultSharedPreferences(Context)`

- [ ] **Step 1: Escribir los tests que fallan**

`compat-shims/src/test/kotlin/com/hippo/unifile/UniFileTest.kt`:

```kotlin
package com.hippo.unifile

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class UniFileTest {
    @Test
    fun `creates finds and lists children`() {
        val root = UniFile.fromFile(createTempDirectory("uni José").toFile())!!
        val dir = root.createDirectory("Manga A")!!
        val file = dir.createFile("cover.jpg")!!
        file.openOutputStream().use { it.write(byteArrayOf(1, 2, 3)) }

        root.findFile("Manga A")!!.isDirectory shouldBe true
        dir.listFiles()!!.map { it.name } shouldBe listOf("cover.jpg")
        file.length() shouldBe 3L
        file.openInputStream().use { it.readBytes() } shouldBe byteArrayOf(1, 2, 3)
        root.findFile("missing") shouldBe null
    }

    @Test
    fun `fromUri resolves file uris only`() {
        val dir = createTempDirectory("uni").toFile()
        UniFile.fromUri(null, android.net.Uri.fromFile(dir)) shouldNotBe null
        UniFile.fromUri(null, android.net.Uri.parse("content://x/y")) shouldBe null
    }

    @Test
    fun `delete removes directories recursively`() {
        val root = UniFile.fromFile(createTempDirectory("uni").toFile())!!
        val dir = root.createDirectory("a")!!
        dir.createFile("b")
        dir.delete() shouldBe true
        dir.exists() shouldBe false
    }
}
```

`compat-shims/src/test/kotlin/logcat/LogcatTest.kt`:

```kotlin
package logcat

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class LogcatTest {
    @Test
    fun `asLog includes the stack trace`() {
        IllegalStateException("boom").asLog() shouldContain "IllegalStateException: boom"
    }

    @Test
    fun `logging never throws`() {
        logcat(LogPriority.ERROR) { "message" }
        logcat("tag", LogPriority.DEBUG) { "message" }
    }
}
```

- [ ] **Step 2: Crear el módulo**

`compat-shims/build.gradle.kts`:

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
}

dependencies {
    api(projects.androidCompat)
    api(libs.slf4j.api)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback)
}
```

Añadir `include(":compat-shims")` a `settings.gradle.kts`.

- [ ] **Step 3: Implementar `logcat` sobre SLF4J**

`compat-shims/src/main/kotlin/logcat/Logcat.kt`:

```kotlin
package logcat

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter

enum class LogPriority(val priorityInt: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
    ASSERT(7),
}

inline fun Any.logcat(
    priority: LogPriority = LogPriority.DEBUG,
    tag: String? = null,
    message: () -> String,
) {
    val logger = LoggerFactory.getLogger(tag ?: outerClassSimpleName(this))
    if (logger.isEnabled(priority)) logger.write(priority, message())
}

inline fun logcat(
    tag: String,
    priority: LogPriority = LogPriority.DEBUG,
    message: () -> String,
) {
    val logger = LoggerFactory.getLogger(tag)
    if (logger.isEnabled(priority)) logger.write(priority, message())
}

fun Throwable.asLog(): String {
    val writer = StringWriter(256)
    PrintWriter(writer, false).use { printStackTrace(it) }
    return writer.toString()
}

@PublishedApi
internal fun outerClassSimpleName(any: Any): String =
    any.javaClass.name.substringBefore('$').substringAfterLast('.')

@PublishedApi
internal fun Logger.isEnabled(priority: LogPriority): Boolean = when (priority) {
    LogPriority.VERBOSE -> isTraceEnabled
    LogPriority.DEBUG -> isDebugEnabled
    LogPriority.INFO -> isInfoEnabled
    LogPriority.WARN -> isWarnEnabled
    LogPriority.ERROR, LogPriority.ASSERT -> isErrorEnabled
}

@PublishedApi
internal fun Logger.write(priority: LogPriority, message: String) = when (priority) {
    LogPriority.VERBOSE -> trace(message)
    LogPriority.DEBUG -> debug(message)
    LogPriority.INFO -> info(message)
    LogPriority.WARN -> warn(message)
    LogPriority.ERROR, LogPriority.ASSERT -> error(message)
}
```

- [ ] **Step 4: Implementar `UniFile` sobre `java.io.File`**

`compat-shims/src/main/kotlin/com/hippo/unifile/UniFile.kt`:

```kotlin
package com.hippo.unifile

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Desktop replacement for the Android-only UniFile library: same API surface Mihon uses, backed by [File].
 * Only `file:` uris exist on Windows, so SAF document uris resolve to null.
 */
class UniFile private constructor(private val file: File) {

    val name: String? get() = file.name
    val uri: Uri get() = Uri.fromFile(file)
    val filePath: String? get() = file.absolutePath
    val parentFile: UniFile? get() = file.parentFile?.let(::UniFile)
    val isDirectory: Boolean get() = file.isDirectory
    val isFile: Boolean get() = file.isFile
    val type: String? get() = null

    fun exists(): Boolean = file.exists()
    fun canRead(): Boolean = file.canRead()
    fun canWrite(): Boolean = file.canWrite()
    fun length(): Long = file.length()
    fun lastModified(): Long = file.lastModified()

    fun listFiles(): Array<UniFile>? = file.listFiles()?.map(::UniFile)?.toTypedArray()

    fun listFiles(filter: (UniFile) -> Boolean): Array<UniFile>? = listFiles()?.filter(filter)?.toTypedArray()

    fun findFile(displayName: String): UniFile? = File(file, displayName).takeIf { it.exists() }?.let(::UniFile)

    fun subFile(displayName: String): UniFile = UniFile(File(file, displayName))

    fun createDirectory(displayName: String?): UniFile? {
        val dir = File(file, displayName ?: return null)
        return if (dir.isDirectory || dir.mkdirs()) UniFile(dir) else null
    }

    fun createFile(displayName: String?): UniFile? {
        val child = File(file, displayName ?: return null)
        return if (child.isFile || child.createNewFile()) UniFile(child) else null
    }

    fun ensureDir(): Boolean = file.isDirectory || file.mkdirs()

    fun ensureFile(): Boolean = file.isFile || (file.parentFile?.let { it.isDirectory || it.mkdirs() } != false && file.createNewFile())

    fun delete(): Boolean = file.deleteRecursively()

    fun renameTo(displayName: String): Boolean = file.renameTo(File(file.parentFile, displayName))

    fun openInputStream(): InputStream = file.inputStream()

    fun openOutputStream(): OutputStream = file.outputStream()

    fun openOutputStream(append: Boolean): OutputStream = FileOutputStream(file, append)

    override fun equals(other: Any?): Boolean = other is UniFile && other.file == file

    override fun hashCode(): Int = file.hashCode()

    override fun toString(): String = file.toString()

    companion object {
        @JvmStatic
        fun fromFile(file: File?): UniFile? = file?.let(::UniFile)

        @JvmStatic
        fun fromUri(context: Context?, uri: Uri?): UniFile? =
            uri?.takeIf { it.scheme == "file" }?.path?.let { UniFile(File(it)) }
    }
}
```

- [ ] **Step 5: Implementar los shims de `androidx`**

`compat-shims/src/main/kotlin/androidx/core/content/ContentShims.kt`:

```kotlin
package androidx.core.content

import android.content.Context
import android.content.SharedPreferences
import java.io.File

inline fun SharedPreferences.edit(commit: Boolean = false, action: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    editor.action()
    if (commit) editor.commit() else editor.apply()
}

inline fun <reified T : Any> Context.getSystemService(): T? = getSystemService(T::class.java)

object ContextCompat {
    @JvmStatic
    fun getExternalFilesDirs(context: Context, type: String?): Array<File?> = context.getExternalFilesDirs(type)
}
```

`compat-shims/src/main/kotlin/androidx/core/text/HtmlShims.kt`:

```kotlin
package androidx.core.text

import android.text.Html
import android.text.Spanned

fun String.parseAsHtml(flags: Int = Html.FROM_HTML_MODE_LEGACY): Spanned = Html.fromHtml(this, flags)
```

`compat-shims/src/main/kotlin/androidx/preference/PreferenceManager.kt`:

```kotlin
package androidx.preference

import android.content.Context
import android.content.SharedPreferences

object PreferenceManager {
    @JvmStatic
    fun getDefaultSharedPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
}
```

- [ ] **Step 6: Ejecutar los tests**

Run: `./gradlew :compat-shims:test`
Expected: los 5 tests en `PASSED`.

Si la compilación falla en `Html.fromHtml(String, Int)` o en `Context.getSystemService(Class)` porque la clase de AndroidCompat no tiene esa sobrecarga, usa la sobrecarga que sí exista en `android-compat/src/main/java/android/...` y anota el cambio en un comentario de una línea.

- [ ] **Step 7: Commit**

```bash
git add compat-shims settings.gradle.kts
git commit -m "feat(compat-shims): JVM stand-ins for logcat, unifile and androidx helpers"
```

---

### Task 5: `core:metro` y `core:common` en JVM con un `NetworkHelper` de escritorio

**Files:**
- Modify: `core/metro/build.gradle.kts`
- Modify: `core/common/build.gradle.kts`
- Create: `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/NetworkHelper.kt`
- Modify: `settings.gradle.kts`
- Test: `core/common/src/test/java/eu/kanade/tachiyomi/network/NetworkHelperTest.kt`
- Test existente: `core/common/src/test/java/tachiyomi/core/common/util/system/TallImageSplitCalculatorTest.kt`

**Interfaces:**
- Consumes: `:android-compat`, `:compat-shims`, `:i18n`
- Produces: módulo `:core:metro` (`AppCoroutineScope`, `IsDebugBuild`) sin cambios de código
- Produces: `eu.kanade.tachiyomi.network.NetworkHelper(scope: CoroutineScope, context: Context, preferences: NetworkPreferences)` con:
  - `cookieJar: AndroidCookieJar`
  - `client: OkHttpClient`
  - `cloudflareClient: OkHttpClient` (deprecated)
  - `defaultUserAgentProvider(): String`
  - Además: `JavaScriptEngine`, `AndroidPreferenceStore`, `NetworkPreferences`, `Hash` y `HttpException` sin cambios.

Archivos de Mihon excluidos en este subproyecto, por depender de `androidx.webkit` o de decodificadores nativos:
- `eu/kanade/tachiyomi/network/NetworkHelper.kt` (sustituido por la versión desktop)
- `eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt`
- `eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt`
- `eu/kanade/tachiyomi/util/system/WebViewUtil.kt`
- `tachiyomi/core/common/util/system/ImageUtil.kt`

- [ ] **Step 1: Escribir el test que falla**

`core/common/src/test/java/eu/kanade/tachiyomi/network/NetworkHelperTest.kt`:

```kotlin
package eu.kanade.tachiyomi.network

import android.content.Context
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import kotlin.io.path.createTempDirectory

class NetworkHelperTest {
    private fun helper(): NetworkHelper {
        val context = mockk<Context> { every { cacheDir } returns createTempDirectory("net").toFile() }
        val preferences = NetworkPreferences(InMemoryPreferenceStore(), isDebugBuild = false)
        return NetworkHelper(CoroutineScope(Dispatchers.IO), context, preferences)
    }

    @Test
    fun `client sends the configured user agent`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()

        val network = helper()
        network.client.newCall(GET(server.url("/").toString())).execute().use { it.code shouldBe 200 }

        server.takeRequest().getHeader("User-Agent") shouldBe network.defaultUserAgentProvider()
        server.shutdown()
    }

    @Test
    fun `cloudflare client is the regular client`() {
        val network = helper()
        @Suppress("DEPRECATION")
        network.cloudflareClient shouldBe network.client
        network.client.interceptors.map { it::class.simpleName } shouldContain "UserAgentInterceptor"
    }
}
```

- [ ] **Step 2: Reescribir `core/metro/build.gradle.kts` y `core/common/build.gradle.kts`**

`core/metro/build.gradle.kts`:

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.metro)
}

dependencies {
    implementation(projects.androidCompat)
    implementation(libs.metro.runtime)
}
```

`core/common/build.gradle.kts`:

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

sourceSets {
    main {
        kotlin {
            srcDir("src/desktop/kotlin")
            // Android-only until the desktop WebView (subproject 2) and image pipeline (subproject 2/4) exist
            exclude(
                "eu/kanade/tachiyomi/network/NetworkHelper.kt",
                "eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt",
                "eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt",
                "eu/kanade/tachiyomi/util/system/WebViewUtil.kt",
                "tachiyomi/core/common/util/system/ImageUtil.kt",
            )
        }
    }
}

dependencies {
    implementation(projects.core.metro)
    implementation(projects.i18n)
    api(projects.androidCompat)
    api(projects.compatShims)

    api(libs.rxJava)
    api(libs.okhttp.core)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.dnsOverHttps)
    api(libs.okio)
    api(libs.apollo)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.jsonOkio)

    implementation(libs.jsoup)
    implementation(libs.natural.comparator)
    implementation(libs.metro.runtime)

    testImplementation(libs.bundles.test)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

Añadir al catálogo, en `[libraries]`:

```toml
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
```

Añadir a `settings.gradle.kts`:

```kotlin
include(":core:metro")
include(":core:common")
```

- [ ] **Step 3: Crear el `NetworkHelper` de escritorio**

`core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/NetworkHelper.kt`. Es una copia del original sin `CloudflareInterceptor`:

```kotlin
package eu.kanade.tachiyomi.network

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import kotlinx.coroutines.CoroutineScope
import mihon.core.metro.AppCoroutineScope
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Desktop copy of Mihon's NetworkHelper. Same public API, which extensions link against.
 * ponytail: no Cloudflare interceptor until the desktop WebView exists (subproject 2).
 */
@Inject
@SingleIn(AppScope::class)
class NetworkHelper(
    @Suppress("UNUSED_PARAMETER") @AppCoroutineScope scope: CoroutineScope,
    private val context: Context,
    private val preferences: NetworkPreferences,
) {

    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30.seconds)
            .readTimeout(30.seconds)
            .callTimeout(2.minutes)
            .cache(
                Cache(
                    directory = File(context.cacheDir, "network_cache"),
                    maxSize = 5L * 1024 * 1024, // 5 MiB
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))

        if (preferences.verboseLogging.get()) {
            builder.addNetworkInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS },
            )
        }

        when (preferences.dohProvider.get()) {
            PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
            PREF_DOH_GOOGLE -> builder.dohGoogle()
            PREF_DOH_ADGUARD -> builder.dohAdGuard()
            PREF_DOH_QUAD9 -> builder.dohQuad9()
            PREF_DOH_ALIDNS -> builder.dohAliDNS()
            PREF_DOH_DNSPOD -> builder.dohDNSPod()
            PREF_DOH_360 -> builder.doh360()
            PREF_DOH_QUAD101 -> builder.dohQuad101()
            PREF_DOH_MULLVAD -> builder.dohMullvad()
            PREF_DOH_CONTROLD -> builder.dohControlD()
            PREF_DOH_NJALLA -> builder.dohNajalla()
            PREF_DOH_SHECAN -> builder.dohShecan()
            else -> builder
        }.build()
    }

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient get() = client

    fun defaultUserAgentProvider() = preferences.defaultUserAgent.get().trim()
}
```

- [ ] **Step 4: Compilar y ver qué más falla**

Run: `./gradlew :core:common:compileKotlin`
Expected: `BUILD SUCCESSFUL`.

Si un archivo no listado falla por una API `androidx.*` que `compat-shims` no cubre, añade el shim mínimo en `compat-shims` con el mismo FQN. Si el archivo solo lo usan la UI o el lector, añádelo a la lista `exclude` con un comentario que indique el subproyecto que lo recupera.

- [ ] **Step 5: Ejecutar los tests**

Run: `./gradlew :core:common:test`
Expected: `NetworkHelperTest` (2 tests) y `TallImageSplitCalculatorTest` en `PASSED`.

- [ ] **Step 6: Commit**

```bash
git add core/metro core/common gradle/libs.versions.toml settings.gradle.kts
git commit -m "build: compile core:metro and core:common for the JVM with a desktop NetworkHelper"
```

---

### Task 6: `source-api` en JVM

**Files:**
- Modify: `source-api/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Test: `source-api/src/test/kotlin/eu/kanade/tachiyomi/source/online/HttpSourceIdTest.kt`

**Interfaces:**
- Consumes: `:android-compat`, `:compat-shims`, `:core:common` (Task 5)
- Produces: módulo `:source-api` con la API pública intacta

- [ ] **Step 1: Escribir el test que falla**

`source-api/src/test/kotlin/eu/kanade/tachiyomi/source/online/HttpSourceIdTest.kt`:

```kotlin
package eu.kanade.tachiyomi.source.online

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HttpSourceIdTest {
    private class MangaDexLike : HttpSource() {
        override val name = "MangaDex"
        override val lang = "en"
        override val baseUrl = "https://example.org"
        override val supportsLatest = false
    }

    @Test
    fun `source ids match the ones Mihon generates`() {
        // Same id Mihon on Android gives MangaDex (en); backups and libraries depend on it.
        MangaDexLike().id shouldBe 2499283573021220255L
    }
}
```

- [ ] **Step 2: Reescribir `source-api/build.gradle.kts`**

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.common)
    api(projects.androidCompat)
    api(projects.compatShims)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.injekt)
    implementation(libs.rxJava)
    implementation(libs.jsoup)

    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.runtimeAnnotation)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

- [ ] **Step 3: Incluir el módulo**

Añadir `include(":source-api")` a `settings.gradle.kts`.

- [ ] **Step 4: Ejecutar el test**

Run: `./gradlew :source-api:test`
Expected: `HttpSourceIdTest > source ids match the ones Mihon generates() PASSED`.

Si Gradle informa de que `androidx.compose.runtime:runtime-annotation` no tiene variante JVM, sustitúyelo por `org.jetbrains.compose.runtime:runtime-annotation` con la versión de Compose Multiplatform compatible con Kotlin 2.4.21. Añade la entrada al catálogo y aplica el mismo cambio en la Task 7 (`domain`).

- [ ] **Step 5: Verificar que los nombres de paquetes no cambiaron**

Run: `git diff upstream-base --stat -- source-api/src`
Expected: solo aparece el archivo de test nuevo.

- [ ] **Step 6: Commit**

```bash
git add source-api settings.gradle.kts
git commit -m "build: compile source-api for the JVM"
```

---

### Task 7: `domain` y `data` en JVM (BD SQLDelight)

**Files:**
- Modify: `domain/build.gradle.kts`
- Modify: `data/build.gradle.kts`
- Modify: `gradle/libs.versions.toml` (paging-common y bundle `sqldelight-jvm`)
- Modify: `settings.gradle.kts`
- Test: `data/src/test/kotlin/tachiyomi/data/DatabaseSchemaTest.kt`
- Tests existentes: `domain/src/test/java/**`

**Interfaces:**
- Consumes: `:source-api`, `:core:common`, `:core:metro`
- Produces:
  - `tachiyomi.data.Database` y `Database.Schema` (versión 20)
  - Adapters `InstantColumnAdapter`, `StringListColumnAdapter`, `UpdateStrategyColumnAdapter`, `MemoColumnAdapter` e `IntColumnAdapter`
  - `mihon.data.database.DatabaseRepositoryImpl`
  - `mihon.data.extension.service.ExtensionStoreService`
  - `mihon.domain.extension.repository.ExtensionStoreRepository` (implementado por `ExtensionStoreRepositoryImpl`)

Archivo excluido: `data/src/main/java/tachiyomi/data/DatabaseBindings.kt`. Usa `Context` y `ActivityManager`; su equivalente de escritorio está en la Task 12.

- [ ] **Step 1: Escribir el test que falla**

`data/src/test/kotlin/tachiyomi/data/DatabaseSchemaTest.kt`:

```kotlin
package tachiyomi.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class DatabaseSchemaTest {
    @Test
    fun `schema matches Mihon so backups stay compatible`() {
        Database.Schema.version shouldBe 20L
    }

    @Test
    fun `fresh database accepts an extension store`() = runTest {
        val file = createTempDirectory("db José").resolve("app.db").toFile()
        val driver = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.File(file),
            schema = Database.Schema,
        )
        val db = Database(
            driver = driver,
            historyAdapter = History.Adapter(read_atAdapter = InstantColumnAdapter),
            mangaAdapter = Manga.Adapter(
                remote_genreAdapter = StringListColumnAdapter,
                remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_favorite_atAdapter = InstantColumnAdapter,
                state_chapter_last_updateAdapter = InstantColumnAdapter,
                state_chapter_next_updateAdapter = InstantColumnAdapter,
                state_cover_last_modifiedAdapter = InstantColumnAdapter,
            ),
            chapterAdapter = Chapter.Adapter(
                remote_date_uploadAdapter = InstantColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_last_page_readAdapter = IntColumnAdapter,
                state_date_fetchAdapter = InstantColumnAdapter,
            ),
            manga_trackAdapter = Manga_track.Adapter(
                start_dateAdapter = InstantColumnAdapter,
                finish_dateAdapter = InstantColumnAdapter,
            ),
        )

        db.extension_storeQueries.selectAll().awaitAsList().size shouldBe 0
        driver.close()
    }
}
```

Antes de ejecutar el test, abre `data/src/main/sqldelight/tachiyomi/data/extension_store.sq` y confirma el nombre de la query de listado. Si no se llama `selectAll`, usa el nombre real. Si la versión de `Database.Schema.version` no es 20, revisa el último `.sqm` en `data/src/main/sqldelight/tachiyomi/migrations/`: la versión es el número de ese archivo + 1.

- [ ] **Step 2: Catálogo**

En `[libraries]`:

```toml
androidx-paging-common = { module = "androidx.paging:paging-common", version.ref = "androidx-paging" }
```

En `[bundles]`:

```toml
sqldelight-jvm = ["sqldelight-androidxDriver", "sqldelight-async", "sqldelight-coroutines", "sqldelight-androidxPaging"]
```

- [ ] **Step 3: Reescribir `domain/build.gradle.kts`**

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.core.common)
    implementation(projects.core.metro)
    implementation(libs.metro.runtime)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.serialization)
    implementation(libs.kotlinx.datetime)

    api(libs.androidx.paging.common)

    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.runtimeAnnotation)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

- [ ] **Step 4: Reescribir `data/build.gradle.kts`**

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("tachiyomi.data")
            dialect(libs.sqldelight.sqliteDialect338)
            schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
            generateAsync.set(true)
        }
    }
}

sourceSets {
    main {
        kotlin {
            // Android driver setup; the desktop one lives in desktop-core (DesktopBindings)
            exclude("tachiyomi/data/DatabaseBindings.kt")
        }
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.core.metro)
    implementation(libs.metro.runtime)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.jsonOkio)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.injekt)
    implementation(libs.kotlinx.datetime)

    api(libs.androidx.sqlite.bundled)
    api(libs.bundles.sqldelight.jvm)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

Añadir a `settings.gradle.kts`:

```kotlin
include(":domain")
include(":data")
```

- [ ] **Step 5: Ejecutar los tests**

Run: `./gradlew :domain:test :data:test`
Expected: los tests existentes de `domain` (`ChapterRecognitionTest`, `MissingChaptersTest`, `LibraryFlagsTest`, `FetchIntervalTest` y `GetApplicationReleaseTest`) y los 2 de `DatabaseSchemaTest` en `PASSED`.

Si `AndroidxSqliteDatabaseType.File` no acepta `java.io.File`, pasa `file.absolutePath`. Es la única diferencia posible entre versiones del driver.

- [ ] **Step 6: Commit**

```bash
git add domain data gradle/libs.versions.toml settings.gradle.kts
git commit -m "build: compile domain and data for the JVM with the bundled SQLite driver"
```

---

### Task 8: `desktop-core` (I): rutas e instancia única

**Files:**
- Create: `desktop-core/build.gradle.kts`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/AppDirs.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/SingleInstance.kt`
- Modify: `settings.gradle.kts`
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/AppDirsTest.kt`
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/SingleInstanceTest.kt`

**Interfaces:**
- Consumes: nada de las tasks anteriores (stdlib + coroutines)
- Produces:
  - `AppDirs(data: Path, local: Path)` con `db`, `backups`, `settings`, `cache`, `extensions`, `logs`, `lockFile`, `createAll()`, `AppDirs.APP_NAME` y `AppDirs.fromEnvironment(env: Map<String, String> = System.getenv()): AppDirs`
  - `SingleInstance.acquire(lockFile: Path, args: List<String>): SingleInstance.Result`, con `Result.Primary(instance)` o `Result.Forwarded`
  - `SingleInstance.listen(scope: CoroutineScope, onArgs: (List<String>) -> Unit): Job`
  - `SingleInstance.close()`

- [ ] **Step 1: Escribir los tests que fallan**

`desktop-core/src/test/kotlin/mihon/desktop/core/AppDirsTest.kt`:

```kotlin
package mihon.desktop.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isDirectory

class AppDirsTest {
    @Test
    fun `uses roaming and local app data on Windows`() {
        val dirs = AppDirs.fromEnvironment(
            mapOf("APPDATA" to "C:\\Users\\José Pérez\\AppData\\Roaming", "LOCALAPPDATA" to "C:\\Users\\José Pérez\\AppData\\Local"),
        )
        dirs.data shouldBe Path("C:\\Users\\José Pérez\\AppData\\Roaming", AppDirs.APP_NAME)
        dirs.extensions shouldBe Path("C:\\Users\\José Pérez\\AppData\\Local", AppDirs.APP_NAME, "extensions")
    }

    @Test
    fun `home override puts everything under one folder`() {
        val dirs = AppDirs.fromEnvironment(mapOf("MANGAREADER_HOME" to "D:\\Portable"))
        dirs.data shouldBe Path("D:\\Portable", "data")
        dirs.local shouldBe Path("D:\\Portable", "local")
    }

    @Test
    fun `fails clearly without app data variables`() {
        shouldThrow<IllegalStateException> { AppDirs.fromEnvironment(emptyMap()) }
    }

    @Test
    fun `createAll creates every directory`() {
        val root = createTempDirectory("José Pérez")
        val dirs = AppDirs(root.resolve("data"), root.resolve("local"))
        dirs.createAll()
        listOf(dirs.db, dirs.backups, dirs.settings, dirs.cache, dirs.extensions, dirs.logs).forEach { it.isDirectory() shouldBe true }
    }
}
```

`desktop-core/src/test/kotlin/mihon/desktop/core/SingleInstanceTest.kt`:

```kotlin
package mihon.desktop.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class SingleInstanceTest {
    @Test
    fun `second instance forwards its arguments to the first`() = runBlocking {
        val lock = createTempDirectory("single").resolve(".lock")
        val first = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        val received = CompletableDeferred<List<String>>()
        val job = first.instance.listen(CoroutineScope(Dispatchers.IO)) { received.complete(it) }

        SingleInstance.acquire(lock, listOf("mangareader://anilist-auth?code=1")) shouldBe SingleInstance.Result.Forwarded

        withTimeout(5_000) { received.await() } shouldBe listOf("mangareader://anilist-auth?code=1")
        job.cancel()
        first.instance.close()
    }

    @Test
    fun `lock is released on close`() {
        val lock = createTempDirectory("single").resolve(".lock")
        val first = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        first.instance.close()

        val again = SingleInstance.acquire(lock, emptyList()).shouldBeInstanceOf<SingleInstance.Result.Primary>()
        again.instance.close()
    }
}
```

- [ ] **Step 2: Crear el módulo**

`desktop-core/build.gradle.kts` (las dependencias de las Tasks 12–13 se añaden ahí):

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

Añadir `include(":desktop-core")` a `settings.gradle.kts`.

- [ ] **Step 3: Implementar `AppDirs`**

`desktop-core/src/main/kotlin/mihon/desktop/core/AppDirs.kt`:

```kotlin
package mihon.desktop.core

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/** Where the app keeps its files: roaming data (synced with the Windows profile) and local machine data. */
data class AppDirs(val data: Path, val local: Path) {
    val db: Path get() = data.resolve("db")
    val backups: Path get() = data.resolve("backups")
    val settings: Path get() = data.resolve("settings")
    val lockFile: Path get() = data.resolve(".lock")
    val cache: Path get() = local.resolve("cache")
    val extensions: Path get() = local.resolve("extensions")
    val logs: Path get() = local.resolve("logs")

    fun createAll() {
        listOf(data, db, backups, settings, local, cache, extensions, logs).forEach { it.createDirectories() }
    }

    companion object {
        /** Provisional product name; the only place it is spelled out. */
        const val APP_NAME = "MangaReader"
        const val HOME_ENV = "MANGAREADER_HOME"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppDirs {
            env[HOME_ENV]?.let { home -> return AppDirs(Path(home, "data"), Path(home, "local")) }
            val roaming = env["APPDATA"] ?: error("APPDATA is not set")
            val local = env["LOCALAPPDATA"] ?: error("LOCALAPPDATA is not set")
            return AppDirs(Path(roaming, APP_NAME), Path(local, APP_NAME))
        }
    }
}
```

- [ ] **Step 4: Implementar `SingleInstance`**

`desktop-core/src/main/kotlin/mihon/desktop/core/SingleInstance.kt`:

```kotlin
package mihon.desktop.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * One process per user. The first one holds a file lock and listens on a loopback port; later ones send it their
 * arguments (OAuth redirects, "focus the window") and exit. The port goes in a separate file because Windows
 * file locks block other processes from reading the locked file.
 */
class SingleInstance private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
    private val server: ServerSocket,
) : AutoCloseable {

    sealed interface Result {
        data class Primary(val instance: SingleInstance) : Result
        data object Forwarded : Result
    }

    fun listen(scope: CoroutineScope, onArgs: (List<String>) -> Unit): Job = scope.launch {
        while (isActive && !server.isClosed) {
            val socket = runCatching { runInterruptible { server.accept() } }.getOrNull() ?: break
            socket.use { onArgs(it.getInputStream().bufferedReader().readLines()) }
        }
    }

    override fun close() {
        server.close()
        lock.release()
        channel.close()
    }

    companion object {
        fun acquire(lockFile: Path, args: List<String>): Result {
            val portFile = lockFile.resolveSibling("${lockFile.fileName}.port")
            val channel = FileChannel.open(lockFile, CREATE, WRITE)
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                forward(portFile.readText().trim().toInt(), args)
                return Result.Forwarded
            }
            val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            portFile.writeText(server.localPort.toString())
            return Result.Primary(SingleInstance(channel, lock, server))
        }

        private fun forward(port: Int, args: List<String>) {
            Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                socket.getOutputStream().bufferedWriter().use { writer -> args.forEach { writer.appendLine(it) } }
            }
        }
    }
}
```

- [ ] **Step 5: Ejecutar los tests**

Run: `./gradlew :desktop-core:test`
Expected: 6 tests en `PASSED`.

- [ ] **Step 6: Commit**

```bash
git add desktop-core settings.gradle.kts
git commit -m "feat(desktop-core): app directories and single-instance lock"
```

---

### Task 9: `extension-loader` (I): inspección de APK y manifiesto

**Files:**
- Create: `extension-loader/build.gradle.kts`
- Create: `extension-loader/src/main/kotlin/mihon/desktop/extension/ApkInspector.kt`
- Create: `extension-loader/src/main/kotlin/mihon/desktop/extension/ExtensionManifestParser.kt`
- Modify: `settings.gradle.kts`
- Test: `extension-loader/src/test/kotlin/mihon/desktop/extension/ApkInspectorTest.kt`
- Test: `extension-loader/src/test/kotlin/mihon/desktop/extension/ExtensionManifestParserTest.kt`

**Interfaces:**
- Consumes: `eu.kanade.tachiyomi.util.lang.Hash.sha256(ByteArray): String` (`:core:common`) y `mihon.domain.extension.model.ContentWarning` (`:domain`)
- Produces:
  - `data class ApkInfo(pkgName: String, label: String, versionName: String, versionCode: Long, metaData: Map<String, String>, signatures: List<String>)`
  - `class ApkSignatureException(message: String) : Exception`
  - `ApkInspector.inspect(apk: Path): ApkInfo`
  - `ApkInspector.verifiedSignatures(apk: Path): List<String>`
  - `ApkInspector.readMetaData(manifestXml: String): Map<String, String>`
  - `ExtensionManifestParser`:
    - `SUPPORTED_LIB_VERSIONS`
    - `name(ApkInfo): String`
    - `contentWarning(Map<String, String>): ContentWarning`
    - `libVersion(ApkInfo): Double?`
    - `sourceClasses(ApkInfo): List<String>`
    - `factory(ApkInfo): String?`

- [ ] **Step 1: Escribir los tests que fallan**

`extension-loader/src/test/kotlin/mihon/desktop/extension/ApkInspectorTest.kt`:

```kotlin
package mihon.desktop.extension

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream

class ApkInspectorTest {
    @Test
    fun `reads application meta-data`() {
        val xml = """
            <manifest package="eu.kanade.tachiyomi.extension.en.example">
              <application android:label="Tachiyomi: Example">
                <meta-data android:name="tachiyomi.extension.class" android:value=".Example"/>
                <meta-data android:name="tachiyomix.extensionLib" android:value="1.4"/>
              </application>
            </manifest>
        """.trimIndent()

        ApkInspector.readMetaData(xml) shouldBe mapOf(
            "tachiyomi.extension.class" to ".Example",
            "tachiyomix.extensionLib" to "1.4",
        )
    }

    @Test
    fun `rejects manifests with a doctype`() {
        val xml = """<!DOCTYPE x [<!ENTITY e SYSTEM "file:///C:/Windows/win.ini">]><manifest><application/></manifest>"""
        shouldThrowAny { ApkInspector.readMetaData(xml) }
    }

    @Test
    fun `unsigned apks are rejected`() {
        val apk = createTempFile(suffix = ".apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(3, 0, 8, 0))
            zip.closeEntry()
        }
        shouldThrow<ApkSignatureException> { ApkInspector.verifiedSignatures(apk) }
    }

    @Test
    fun `truncated files are rejected`() {
        val apk = createTempFile(suffix = ".apk")
        apk.outputStream().use { it.write(byteArrayOf(0x50, 0x4B, 0x03)) }
        shouldThrow<ApkSignatureException> { ApkInspector.verifiedSignatures(apk) }
    }
}
```

`extension-loader/src/test/kotlin/mihon/desktop/extension/ExtensionManifestParserTest.kt`:

```kotlin
package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import mihon.domain.extension.model.ContentWarning
import org.junit.jupiter.api.Test

class ExtensionManifestParserTest {
    private fun info(meta: Map<String, String>, versionName: String = "1.4.12", label: String = "Tachiyomi: Example") =
        ApkInfo("eu.kanade.tachiyomi.extension.en.example", label, versionName, 12, meta, listOf("abc"))

    @Test
    fun `relative source classes get the package prefix`() {
        ExtensionManifestParser.sourceClasses(info(mapOf("tachiyomi.extension.class" to ".A; com.other.B"))) shouldBe
            listOf("eu.kanade.tachiyomi.extension.en.example.A", "com.other.B")
    }

    @Test
    fun `lib version comes from metadata or falls back to the version name`() {
        ExtensionManifestParser.libVersion(info(mapOf("tachiyomix.extensionLib" to "1.6"))) shouldBe 1.6
        ExtensionManifestParser.libVersion(info(emptyMap(), versionName = "1.4.3")) shouldBe 1.4
        ExtensionManifestParser.libVersion(info(emptyMap(), versionName = "garbage")) shouldBe null
    }

    @Test
    fun `content warning follows Mihon's precedence`() {
        ExtensionManifestParser.contentWarning(mapOf("tachiyomix.contentWarning" to "1")) shouldBe ContentWarning.MIXED
        ExtensionManifestParser.contentWarning(mapOf("tachiyomix.contentWarning" to "2")) shouldBe ContentWarning.NSFW
        ExtensionManifestParser.contentWarning(mapOf("tachiyomi.extension.nsfw" to "1")) shouldBe ContentWarning.NSFW
        ExtensionManifestParser.contentWarning(emptyMap()) shouldBe ContentWarning.SAFE
    }

    @Test
    fun `name prefers metadata then label without the Tachiyomi prefix`() {
        ExtensionManifestParser.name(info(mapOf("tachiyomix.name" to "Nice"))) shouldBe "Nice"
        ExtensionManifestParser.name(info(emptyMap())) shouldBe "Example"
        ExtensionManifestParser.name(info(emptyMap(), label = "")) shouldBe "eu.kanade.tachiyomi.extension.en.example"
    }
}
```

- [ ] **Step 2: Crear el módulo**

`extension-loader/build.gradle.kts` (las dependencias de las Tasks 10–11 ya están incluidas):

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.core.metro)
    implementation(libs.metro.runtime)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.apksig)
    implementation(libs.apk.parser)
    implementation(libs.dex2jar.translator)
    implementation(libs.dex2jar.tools)
    implementation(libs.kotlin.logging)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

Añadir `include(":extension-loader")` a `settings.gradle.kts`.

- [ ] **Step 3: Implementar `ApkInspector`**

`extension-loader/src/main/kotlin/mihon/desktop/extension/ApkInspector.kt`:

```kotlin
package mihon.desktop.extension

import com.android.apksig.ApkVerifier
import eu.kanade.tachiyomi.util.lang.Hash
import net.dongliu.apk.parser.ApkFile
import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

data class ApkInfo(
    val pkgName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val metaData: Map<String, String>,
    /** SHA-256 hex digests of the signer certificates, the same format as a store's signingKey. */
    val signatures: List<String>,
)

class ApkSignatureException(message: String) : Exception(message)

object ApkInspector {

    /** Verifies the signature first, so nothing is read from an apk that was tampered with. */
    fun inspect(apk: Path): ApkInfo {
        val signatures = verifiedSignatures(apk)
        ApkFile(apk.toFile()).use { parsed ->
            val meta = parsed.apkMeta
            return ApkInfo(
                pkgName = meta.packageName,
                label = meta.label.orEmpty(),
                versionName = meta.versionName.orEmpty(),
                versionCode = meta.versionCode ?: 0L,
                metaData = readMetaData(parsed.manifestXml),
                signatures = signatures,
            )
        }
    }

    fun verifiedSignatures(apk: Path): List<String> {
        val result = try {
            ApkVerifier.Builder(apk.toFile()).build().verify()
        } catch (e: Exception) {
            throw ApkSignatureException("Could not verify the apk signature: ${e.message}")
        }
        if (!result.isVerified) throw ApkSignatureException("Invalid apk signature: ${result.errors.joinToString()}")
        return result.signerCertificates.map { Hash.sha256(it.encoded) }
    }

    fun readMetaData(manifestXml: String): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // The manifest comes from a downloaded file: no DTDs, no external entities
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(manifestXml.byteInputStream())
        val application = doc.getElementsByTagName("application").item(0) as? Element ?: return emptyMap()
        val nodes = application.getElementsByTagName("meta-data")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .associate { it.getAttribute("android:name") to it.getAttribute("android:value") }
    }
}
```

- [ ] **Step 4: Implementar `ExtensionManifestParser`**

`extension-loader/src/main/kotlin/mihon/desktop/extension/ExtensionManifestParser.kt`:

```kotlin
package mihon.desktop.extension

import mihon.domain.extension.model.ContentWarning

/** Reads the same manifest metadata Mihon's ExtensionLoader reads, with the same fallbacks. */
object ExtensionManifestParser {
    val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)

    const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    const val METADATA_NAME = "tachiyomix.name"
    const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"

    fun name(info: ApkInfo): String =
        info.metaData[METADATA_NAME]
            ?: info.label.substringAfter("Tachiyomi: ").takeIf { it.isNotBlank() }
            ?: info.pkgName

    fun contentWarning(metaData: Map<String, String>): ContentWarning = when {
        METADATA_CONTENT_WARNING in metaData -> when (metaData[METADATA_CONTENT_WARNING]?.toIntOrNull()) {
            1 -> ContentWarning.MIXED
            2 -> ContentWarning.NSFW
            else -> ContentWarning.SAFE
        }
        metaData[METADATA_NSFW]?.toIntOrNull() == 1 -> ContentWarning.NSFW
        else -> ContentWarning.SAFE
    }

    fun libVersion(info: ApkInfo): Double? =
        info.metaData[METADATA_EXTENSION_LIB]?.toDoubleOrNull()?.takeUnless { it == 0.0 }
            ?: info.versionName.substringBeforeLast('.').toDoubleOrNull()

    fun sourceClasses(info: ApkInfo): List<String> =
        info.metaData[METADATA_SOURCE_CLASS].orEmpty()
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) info.pkgName + it else it }

    fun factory(info: ApkInfo): String? = info.metaData[METADATA_SOURCE_FACTORY]
}
```

- [ ] **Step 5: Ejecutar los tests**

Run: `./gradlew :extension-loader:test`
Expected: 8 tests en `PASSED`.

- [ ] **Step 6: Commit**

```bash
git add extension-loader settings.gradle.kts
git commit -m "feat(extension-loader): verified apk inspection and manifest parsing"
```

---

### Task 10: `extension-loader` (II): dex2jar y ClassLoader (código vendorizado MPL)

**Files:**
- Create: `extension-loader/src/main/kotlin/mihon/desktop/extension/vendor/ChildFirstURLClassLoader.kt` (copia de Suwayomi `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/util/ChildFirstURLClassLoader.kt`)
- Create: `extension-loader/src/main/kotlin/mihon/desktop/extension/vendor/BytecodeEditor.kt` (copia de Suwayomi `.../impl/util/BytecodeEditor.kt`)
- Create: `extension-loader/src/main/kotlin/mihon/desktop/extension/vendor/Dex2Jar.kt` (derivado de `PackageTools.dex2jar`)
- Create: `extension-loader/src/main/kotlin/mihon/desktop/extension/SourceInstantiator.kt`
- Test: `extension-loader/src/test/kotlin/mihon/desktop/extension/ClassLoadingTest.kt`

**Interfaces:**
- Consumes: `eu.kanade.tachiyomi.source.Source` y `eu.kanade.tachiyomi.source.SourceFactory`
- Produces:
  - `mihon.desktop.extension.vendor.ChildFirstURLClassLoader(urls: Array<URL>, parent: ClassLoader? = null)`
  - `mihon.desktop.extension.vendor.Dex2Jar.convert(apk: Path, jar: Path)` (lanza `Dex2JarException` si la conversión falla)
  - `SourceInstantiator.instantiate(jar: Path, classNames: List<String>): List<Source>`
  - `SourceInstantiator.release(jar: Path)`

- [ ] **Step 1: Escribir el test que falla**

`extension-loader/src/test/kotlin/mihon/desktop/extension/ClassLoadingTest.kt`:

```kotlin
package mihon.desktop.extension

import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import mihon.desktop.extension.vendor.ChildFirstURLClassLoader
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream

class FakeSource : Source by mockk(relaxed = true) {
    override val id = 1L
    override val name = "Fake"
    override val lang = "en"
}

class ClassLoadingTest {
    private fun emptyClass(name: String): ByteArray = ClassWriter(0).run {
        visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null)
        visitEnd()
        toByteArray()
    }

    private fun jarWith(vararg classNames: String): Path {
        val jar = createTempFile(suffix = ".jar")
        JarOutputStream(jar.outputStream()).use { out ->
            classNames.forEach {
                out.putNextEntry(JarEntry(it.replace('.', '/') + ".class"))
                out.write(emptyClass(it))
                out.closeEntry()
            }
        }
        return jar
    }

    @Test
    fun `classes only in the extension load from the extension`() {
        ChildFirstURLClassLoader(arrayOf(jarWith("ext.only.Hello").toUri().toURL())).use { loader ->
            loader.loadClass("ext.only.Hello").classLoader shouldBe loader
        }
    }

    @Test
    fun `the app's copy of a shared library wins`() {
        ChildFirstURLClassLoader(arrayOf(jarWith("okhttp3.OkHttpClient").toUri().toURL())).use { loader ->
            loader.loadClass("okhttp3.OkHttpClient") shouldBe okhttp3.OkHttpClient::class.java
        }
    }

    @Test
    fun `instantiates sources by class name`() {
        val jar = jarWith("ext.only.Hello")
        SourceInstantiator.instantiate(jar, listOf(FakeSource::class.java.name)).map { it.name } shouldBe listOf("Fake")
        SourceInstantiator.release(jar)
    }
}
```

Añadir `testImplementation(libs.okhttp.core)` en `extension-loader/build.gradle.kts`. ASM ya viene como dependencia transitiva de `dex2jar-translator`; si Gradle no lo expone en tests, añade `testImplementation("org.ow2.asm:asm:9.9")`.

- [ ] **Step 2: Copiar los archivos de Suwayomi**

```bash
SRC="$TEMP/suwa-vendor"
cd "$SRC" && git sparse-checkout add server/src/main/kotlin/suwayomi/tachidesk/manga/impl/util && cd -
DST=extension-loader/src/main/kotlin/mihon/desktop/extension/vendor
mkdir -p "$DST"
cp "$SRC/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/util/ChildFirstURLClassLoader.kt" "$DST/"
cp "$SRC/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/util/BytecodeEditor.kt" "$DST/"
sed -i 's/^package suwayomi.tachidesk.manga.impl.util$/package mihon.desktop.extension.vendor/' "$DST/ChildFirstURLClassLoader.kt" "$DST/BytecodeEditor.kt"
```

Si `ChildFirstURLClassLoader.kt` no tiene cabecera MPL (el original no la trae), añade al inicio del archivo, tras la línea `package`:

```kotlin
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
```

Añade a `android-compat/README.md`, en "Local modifications":

```markdown
- `extension-loader/.../vendor/ChildFirstURLClassLoader.kt` and `BytecodeEditor.kt`: copied from
  `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/util/`, package renamed to `mihon.desktop.extension.vendor`.
- `extension-loader/.../vendor/Dex2Jar.kt`: derived from `PackageTools.dex2jar`; errors throw instead of being logged.
```

- [ ] **Step 3: Implementar `Dex2Jar` (MPL)**

`extension-loader/src/main/kotlin/mihon/desktop/extension/vendor/Dex2Jar.kt`:

```kotlin
package mihon.desktop.extension.vendor

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import java.nio.file.Path
import kotlin.io.path.readBytes

class Dex2JarException(message: String) : Exception(message)

object Dex2Jar {
    /** Converts the dex code inside [apk] into [jar] and rewrites java.* classes that differ from Android's. */
    fun convert(apk: Path, jar: Path) {
        val reader = MultiDexFileReader.open(apk.readBytes())
        val handler = BaksmaliBaseDexExceptionHandler()
        Dex2jar
            .from(reader)
            .withExceptionHandler(handler)
            .reUseReg(false)
            .topoLogicalSort()
            .skipDebug(true)
            .optimizeSynchronized(false)
            .printIR(false)
            .noCode(false)
            .skipExceptions(false)
            .dontSanitizeNames(true)
            .computeFrames(true)
            .to(jar)
        if (handler.hasException()) {
            val errorFile = jar.resolveSibling("${jar.fileName}.dex2jar-error.txt")
            handler.dump(errorFile, emptyArray<String>())
            throw Dex2JarException("dex2jar failed, details in $errorFile")
        }
        BytecodeEditor.fixAndroidClasses(jar)
    }
}
```

- [ ] **Step 4: Implementar `SourceInstantiator`**

`extension-loader/src/main/kotlin/mihon/desktop/extension/SourceInstantiator.kt`:

```kotlin
package mihon.desktop.extension

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import mihon.desktop.extension.vendor.ChildFirstURLClassLoader
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** One class loader per extension jar, kept so its sources keep working until the jar is released. */
object SourceInstantiator {
    private val loaders = ConcurrentHashMap<Path, ChildFirstURLClassLoader>()

    fun instantiate(jar: Path, classNames: List<String>): List<Source> {
        val loader = loaders.getOrPut(jar.toAbsolutePath()) { ChildFirstURLClassLoader(arrayOf(jar.toUri().toURL())) }
        return classNames.flatMap { className ->
            when (val obj = Class.forName(className, false, loader).getDeclaredConstructor().newInstance()) {
                is Source -> listOf(obj)
                is SourceFactory -> obj.createSources()
                else -> throw IllegalStateException("Unknown source class type: ${obj.javaClass}")
            }
        }
    }

    /** Closes the jar so Windows lets it be deleted. Its sources must not be used afterwards. */
    fun release(jar: Path) {
        loaders.remove(jar.toAbsolutePath())?.close()
    }
}
```

- [ ] **Step 5: Ejecutar los tests**

Run: `./gradlew :extension-loader:test`
Expected: 11 tests en `PASSED` (8 previos + 3 nuevos).

- [ ] **Step 6: Commit**

```bash
git add extension-loader android-compat/README.md
git commit -m "feat(extension-loader): dex2jar conversion and child-first class loading (MPL vendored)"
```

---

### Task 11: `extension-loader` (III): confianza, carga e instalación

**Files:**
- Create: `extension-loader/src/main/kotlin/mihon/desktop/extension/ExtensionTrust.kt`
- Create: `extension-loader/src/main/kotlin/mihon/desktop/extension/DesktopExtensionLoader.kt`
- Create: `extension-loader/src/main/kotlin/mihon/desktop/extension/ExtensionInstaller.kt`
- Test: `extension-loader/src/test/kotlin/mihon/desktop/extension/ExtensionTrustTest.kt`
- Test: `extension-loader/src/test/kotlin/mihon/desktop/extension/DesktopExtensionLoaderTest.kt`
- Test: `extension-loader/src/test/kotlin/mihon/desktop/extension/ExtensionInstallerTest.kt`

**Interfaces:**
- Consumes:
  - `ApkInfo`, `ApkInspector`, `ExtensionManifestParser` (Task 9)
  - `SourceInstantiator`, `Dex2Jar` (Task 10)
  - `PreferenceStore`, `Preference.appStateKey`, `getAndSet` (`:core:common`)
  - `Extension`, `ExtensionStore` (`:domain`)
- Produces:
  - `ExtensionTrust(preferenceStore: PreferenceStore)` con `isTrusted(pkgName, versionCode, fingerprints, stores): Boolean` y `trust(pkgName, versionCode, signatureHash)`
  - `data class InstalledExtension(apk: Path, jar: Path, info: ApkInfo)`
  - `DesktopExtensionLoader(trust: ExtensionTrust, instantiate: (Path, List<String>) -> List<Source> = SourceInstantiator::instantiate)` con `load(installed, stores): Extension.Installed`
  - `ExtensionInstaller(dir: Path, inspect: (Path) -> ApkInfo = ApkInspector::inspect, convert: (Path, Path) -> Unit = Dex2Jar::convert)` con `install(downloadedApk: Path): InstalledExtension`, `installed(): List<InstalledExtension>` y `uninstall(pkgName: String)`
  - `class SignatureMismatchException(pkgName: String) : Exception`

- [ ] **Step 1: Escribir los tests que fallan**

`extension-loader/src/test/kotlin/mihon/desktop/extension/ExtensionTrustTest.kt`:

```kotlin
package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class ExtensionTrustTest {
    private val store = ExtensionStore("https://repo/index.min.json", "Repo", "R", "storekey", ExtensionStore.Contact("https://repo", null), false, null)

    @Test
    fun `signed by a store key is trusted`() {
        ExtensionTrust(InMemoryPreferenceStore()).isTrusted("pkg", 1, listOf("storekey"), listOf(store)) shouldBe true
    }

    @Test
    fun `unknown key needs explicit trust for that exact version`() {
        val trust = ExtensionTrust(InMemoryPreferenceStore())
        trust.isTrusted("pkg", 1, listOf("other"), listOf(store)) shouldBe false
        trust.trust("pkg", 1, "other")
        trust.isTrusted("pkg", 1, listOf("other"), listOf(store)) shouldBe true
        trust.isTrusted("pkg", 2, listOf("other"), listOf(store)) shouldBe false
    }

    @Test
    fun `no signatures is never trusted`() {
        ExtensionTrust(InMemoryPreferenceStore()).isTrusted("pkg", 1, emptyList(), listOf(store)) shouldBe false
    }
}
```

`extension-loader/src/test/kotlin/mihon/desktop/extension/DesktopExtensionLoaderTest.kt`:

```kotlin
package mihon.desktop.extension

import eu.kanade.tachiyomi.extension.model.Extension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import kotlin.io.path.Path

class DesktopExtensionLoaderTest {
    private val store = ExtensionStore("https://repo/index.min.json", "Repo", "R", "storekey", ExtensionStore.Contact("https://repo", null), false, null)
    private val loader = DesktopExtensionLoader(ExtensionTrust(InMemoryPreferenceStore()))

    private fun installed(meta: Map<String, String>, signatures: List<String> = listOf("storekey"), versionName: String = "1.4.1") =
        InstalledExtension(
            apk = Path("x.apk"),
            jar = Path("x.jar"),
            info = ApkInfo("pkg.example", "Tachiyomi: Example", versionName, 1, meta, signatures),
        )

    @Test
    fun `loads trusted extensions with supported libs`() {
        val ext = loader.load(installed(mapOf("tachiyomi.extension.class" to FakeSource::class.java.name)), listOf(store))
        ext.shouldBeInstanceOf<Extension.Loaded>().sources.map { it.name } shouldBe listOf("Fake")
        ext.lang shouldBe "en"
        ext.store shouldBe store
    }

    @Test
    fun `unsupported lib versions are not loaded`() {
        val ext = loader.load(installed(mapOf("tachiyomix.extensionLib" to "1.2", "tachiyomi.extension.class" to "x")), listOf(store))
        ext.shouldBeInstanceOf<Extension.NotLoaded>().reason shouldBe Extension.NotLoaded.Reason.UnsupportedLibVersion
    }

    @Test
    fun `untrusted signatures are reported with the last signature`() {
        val ext = loader.load(installed(mapOf("tachiyomi.extension.class" to "x"), signatures = listOf("a", "b")), listOf(store))
        ext.shouldBeInstanceOf<Extension.NotLoaded>().reason shouldBe Extension.NotLoaded.Reason.Untrusted("b")
    }

    @Test
    fun `missing source class is malformed`() {
        val ext = loader.load(installed(emptyMap()), listOf(store))
        ext.shouldBeInstanceOf<Extension.NotLoaded>().reason shouldBe Extension.NotLoaded.Reason.Malformed
    }

    @Test
    fun `missing classes become a failed reason instead of crashing`() {
        val ext = loader.load(installed(mapOf("tachiyomi.extension.class" to "does.not.Exist")), listOf(store))
        ext.shouldBeInstanceOf<Extension.NotLoaded>().reason.shouldBeInstanceOf<Extension.NotLoaded.Reason.Failed>()
    }
}
```

`extension-loader/src/test/kotlin/mihon/desktop/extension/ExtensionInstallerTest.kt`:

```kotlin
package mihon.desktop.extension

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText

class ExtensionInstallerTest {
    private val infos = mutableMapOf<Path, ApkInfo>()

    private fun apk(versionCode: Long, signatures: List<String> = listOf("k")): Path =
        createTempFile(suffix = ".apk").also {
            it.writeText("apk $versionCode")
            infos[it] = ApkInfo("pkg.example", "Example", "1.4.$versionCode", versionCode, emptyMap(), signatures)
        }

    private fun installer(dir: Path, convert: (Path, Path) -> Unit = { _, jar -> jar.writeText("jar") }) =
        ExtensionInstaller(dir, inspect = { infos.getValue(it) }, convert = convert)

    @Test
    fun `installs apk and jar named by package and version`() {
        val dir = createTempDirectory("ext José")
        installer(dir).install(apk(3))
        dir.listDirectoryEntries().map { it.name }.sorted() shouldBe listOf("pkg.example-3.apk", "pkg.example-3.jar")
    }

    @Test
    fun `newest version wins and stale files are cleaned up`() {
        val dir = createTempDirectory("ext")
        val installer = installer(dir)
        installer.install(apk(3))
        // Simulates a jar Windows kept locked during the update: an older version is still on disk
        dir.resolve("pkg.example-2.apk").writeText("old")
        dir.resolve("pkg.example-2.jar").writeText("old")
        infos[dir.resolve("pkg.example-3.apk")] = infos.values.first { it.versionCode == 3L }

        installer.installed().map { it.info.versionCode } shouldContainExactly listOf(3L)
        dir.listDirectoryEntries().map { it.name }.sorted() shouldBe listOf("pkg.example-3.apk", "pkg.example-3.jar")
    }

    @Test
    fun `update signed with a different key is rejected and keeps the installed version`() {
        val dir = createTempDirectory("ext")
        val installer = installer(dir)
        installer.install(apk(3, listOf("k")))
        infos[dir.resolve("pkg.example-3.apk")] = infos.values.first { it.versionCode == 3L }

        shouldThrow<SignatureMismatchException> { installer.install(apk(4, listOf("evil"))) }
        dir.listDirectoryEntries().map { it.name }.sorted() shouldBe listOf("pkg.example-3.apk", "pkg.example-3.jar")
    }

    @Test
    fun `failed conversion leaves no partial files`() {
        val dir = createTempDirectory("ext")
        shouldThrow<IllegalStateException> {
            installer(dir, convert = { _, jar -> jar.writeText("partial"); error("corrupt dex") }).install(apk(3))
        }
        dir.listDirectoryEntries() shouldBe emptyList()
    }
}
```

- [ ] **Step 2: Implementar `ExtensionTrust`**

`extension-loader/src/main/kotlin/mihon/desktop/extension/ExtensionTrust.kt`:

```kotlin
package mihon.desktop.extension

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getAndSet

/** Mihon's trust rules: signed by a store's key, or explicitly trusted by the user for one exact version. */
@Inject
@SingleIn(AppScope::class)
class ExtensionTrust(preferenceStore: PreferenceStore) {

    // Same key as Mihon's SourcePreferences.trustedExtensions, so backups carry it over
    private val trusted = preferenceStore.getStringSet(Preference.appStateKey("trusted_extensions"), emptySet())

    fun isTrusted(pkgName: String, versionCode: Long, fingerprints: List<String>, stores: List<ExtensionStore>): Boolean {
        if (fingerprints.isEmpty()) return false
        val storeKeys = stores.mapTo(HashSet()) { it.signingKey }
        return fingerprints.any { it in storeKeys } || "$pkgName:$versionCode:${fingerprints.last()}" in trusted.get()
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        trusted.getAndSet { set ->
            set.filterNot { it.startsWith("$pkgName:") }.toSet() + "$pkgName:$versionCode:$signatureHash"
        }
    }
}
```

- [ ] **Step 3: Implementar `DesktopExtensionLoader`**

`extension-loader/src/main/kotlin/mihon/desktop/extension/DesktopExtensionLoader.kt`:

```kotlin
package mihon.desktop.extension

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import logcat.LogPriority
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.system.logcat
import java.nio.file.Path

data class InstalledExtension(val apk: Path, val jar: Path, val info: ApkInfo)

/** Desktop counterpart of Mihon's ExtensionLoader.loadExtension: same checks, same order, same reasons. */
class DesktopExtensionLoader(
    private val trust: ExtensionTrust,
    private val instantiate: (Path, List<String>) -> List<Source> = SourceInstantiator::instantiate,
) {
    fun load(installed: InstalledExtension, stores: List<ExtensionStore>): Extension.Installed {
        val info = installed.info
        val name = ExtensionManifestParser.name(info)
        val contentWarning = ExtensionManifestParser.contentWarning(info.metaData)
        val store = stores.firstOrNull { it.signingKey in info.signatures }

        fun notLoaded(reason: Extension.NotLoaded.Reason, libVersion: Double? = null) = Extension.NotLoaded(
            name = name,
            pkgName = info.pkgName,
            versionName = info.versionName,
            versionCode = info.versionCode,
            isShared = false,
            contentWarning = contentWarning,
            signatures = info.signatures,
            libVersion = libVersion,
            store = store,
            reason = reason,
        )

        if (info.versionName.isEmpty()) return notLoaded(Extension.NotLoaded.Reason.Malformed)

        val libVersion = ExtensionManifestParser.libVersion(info)
        if (libVersion == null || libVersion !in ExtensionManifestParser.SUPPORTED_LIB_VERSIONS) {
            return notLoaded(Extension.NotLoaded.Reason.UnsupportedLibVersion, libVersion)
        }

        if (info.signatures.isEmpty()) return notLoaded(Extension.NotLoaded.Reason.Unsigned, libVersion)
        if (!trust.isTrusted(info.pkgName, info.versionCode, info.signatures, stores)) {
            return notLoaded(Extension.NotLoaded.Reason.Untrusted(info.signatures.last()), libVersion)
        }

        val classNames = ExtensionManifestParser.sourceClasses(info)
        if (classNames.isEmpty()) return notLoaded(Extension.NotLoaded.Reason.Malformed, libVersion)

        val sources = try {
            instantiate(installed.jar, classNames)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $name (${info.pkgName})" }
            val root = generateSequence(e) { it.cause }.last()
            return notLoaded(Extension.NotLoaded.Reason.Failed(root.message ?: root.toString(), e.stackTraceToString()), libVersion)
        }

        val langs = sources.map { it.lang }.toSet()
        return Extension.Loaded(
            name = name,
            pkgName = info.pkgName,
            versionName = info.versionName,
            versionCode = info.versionCode,
            libVersion = libVersion,
            lang = when (langs.size) {
                0 -> ""
                1 -> langs.first()
                else -> "all"
            },
            contentWarning = contentWarning,
            isShared = false,
            signatures = info.signatures,
            pkgFactory = ExtensionManifestParser.factory(info),
            sources = sources,
            store = store,
        )
    }
}
```

- [ ] **Step 4: Implementar `ExtensionInstaller`**

`extension-loader/src/main/kotlin/mihon/desktop/extension/ExtensionInstaller.kt`:

```kotlin
package mihon.desktop.extension

import logcat.LogPriority
import mihon.desktop.extension.vendor.Dex2Jar
import tachiyomi.core.common.util.system.logcat
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.copyTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name

class SignatureMismatchException(pkgName: String) :
    Exception("The update for $pkgName is signed with a different key than the installed version")

/**
 * Keeps `<pkg>-<versionCode>.apk` and `.jar` side by side in [dir]. The apk stays because it is the signed
 * artifact: it is verified again on every start.
 */
class ExtensionInstaller(
    private val dir: Path,
    private val inspect: (Path) -> ApkInfo = ApkInspector::inspect,
    private val convert: (Path, Path) -> Unit = Dex2Jar::convert,
) {
    fun install(downloadedApk: Path): InstalledExtension {
        val info = inspect(downloadedApk)
        val current = installed().firstOrNull { it.info.pkgName == info.pkgName }
        if (current != null && !info.signatures.containsAll(current.info.signatures)) {
            throw SignatureMismatchException(info.pkgName)
        }

        val base = "${info.pkgName}-${info.versionCode}"
        val apk = dir.resolve("$base.apk")
        val jar = dir.resolve("$base.jar")
        val tmpJar = dir.resolve("$base.jar.tmp")
        try {
            convert(downloadedApk, tmpJar)
            tmpJar.moveTo(jar, ATOMIC_MOVE, REPLACE_EXISTING)
            downloadedApk.copyTo(apk, REPLACE_EXISTING)
        } catch (e: Throwable) {
            tmpJar.deleteIfExists()
            jar.deleteIfExists()
            throw e
        }

        if (current != null && current.info.versionCode != info.versionCode) deleteQuietly(current)
        return InstalledExtension(apk, jar, info)
    }

    /** Newest complete version of each package. Older or incomplete leftovers are deleted when possible. */
    fun installed(): List<InstalledExtension> {
        if (!dir.exists()) return emptyList()
        val versions = dir.listDirectoryEntries("*.apk").mapNotNull { apk ->
            val base = apk.name.removeSuffix(".apk")
            val separator = base.lastIndexOf('-')
            val versionCode = base.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
            Triple(base.substring(0, separator), versionCode, apk)
        }
        return versions.groupBy { it.first }.mapNotNull { (_, list) ->
            val sorted = list.sortedByDescending { it.second }
            sorted.drop(1).forEach { (_, _, apk) -> deleteQuietly(apk, apk.resolveSibling(apk.name.removeSuffix(".apk") + ".jar")) }
            val (_, _, apk) = sorted.first()
            val jar = apk.resolveSibling(apk.name.removeSuffix(".apk") + ".jar")
            if (!jar.exists()) return@mapNotNull null
            runCatching { InstalledExtension(apk, jar, inspect(apk)) }
                .onFailure { logcat(LogPriority.ERROR, it) { "Skipping invalid extension ${apk.name}" } }
                .getOrNull()
        }
    }

    fun uninstall(pkgName: String) {
        installed().filter { it.info.pkgName == pkgName }.forEach { deleteQuietly(it) }
    }

    private fun deleteQuietly(ext: InstalledExtension) {
        SourceInstantiator.release(ext.jar)
        deleteQuietly(ext.apk, ext.jar)
    }

    // A jar still mapped by a class loader can't be deleted on Windows; the next installed() call retries
    private fun deleteQuietly(vararg paths: Path) = paths.forEach { path ->
        runCatching { path.deleteIfExists() }
            .onFailure { logcat(LogPriority.WARN, it) { "Could not delete $path yet" } }
    }
}
```

- [ ] **Step 5: Ejecutar los tests**

Run: `./gradlew :extension-loader:test`
Expected: 23 tests en `PASSED` (11 previos + 12 nuevos).

- [ ] **Step 6: Commit**

```bash
git add extension-loader
git commit -m "feat(extension-loader): trust rules, loader reasons and atomic installs"
```

---

### Task 12: `desktop-core` (II): grafo Metro, Injekt y arranque de la plataforma

**Files:**
- Modify: `desktop-core/build.gradle.kts`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopBindings.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopGraph.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopInjektRegistrar.kt` (copia adaptada de `app/src/main/java/mihon/app/di/injekt/MetroInjektRegistrar.kt`)
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/Platform.kt`
- Create: `desktop-core/src/main/kotlin/eu/kanade/tachiyomi/AppInfo.kt`
- Create: `desktop-core/src/main/resources/logback.xml`
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/PlatformTest.kt`

**Interfaces:**
- Consumes:
  - `AppDirs` (Task 8)
  - `NetworkHelper`, `JavaScriptEngine`, `PreferenceStore` (Task 5)
  - `Database`, adapters, `DatabaseRepositoryImpl`, `ExtensionStoreRepository` (Task 7)
  - `ExtensionTrust` (Task 11)
  - AndroidCompat (Task 3)
- Produces:
  - `DesktopGraph`, que expone `context`, `json`, `protoBuf`, `xml`, `networkHelper`, `javaScriptEngine`, `preferenceStore`, `database`, `extensionStoreRepository`, `extensionTrust` y `appDirs`
  - `Platform.start(dirs: AppDirs, isDebugBuild: Boolean = false): DesktopGraph`
  - `eu.kanade.tachiyomi.AppInfo`
  - `DesktopBuildInfo.VERSION_CODE` y `DesktopBuildInfo.VERSION_NAME`

- [ ] **Step 1: Escribir el test que falla**

`desktop-core/src/test/kotlin/mihon/desktop/core/PlatformTest.kt`:

```kotlin
package mihon.desktop.core

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformTest {
    private lateinit var dirs: AppDirs
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        val root = createTempDirectory("José Pérez platform")
        dirs = AppDirs(root.resolve("data"), root.resolve("local"))
        graph = Platform.start(dirs)
    }

    @Test
    fun `database opens in the app data folder`() = runBlocking {
        graph.extensionStoreRepository.getAll() shouldBe emptyList()
        dirs.db.resolve("app.db").exists() shouldBe true
    }

    @Test
    fun `extensions resolve the same objects through Injekt`() {
        Injekt.get<NetworkHelper>() shouldBe graph.networkHelper
        Injekt.get<Context>() shouldBe graph.context
        Injekt.get<Application>().packageName.shouldNotBeBlank()
    }

    @Test
    fun `source preferences are stored under the app data folder`() = runBlocking {
        graph.context.getSharedPreferences("source_1", Context.MODE_PRIVATE).edit().putInt("x", 1).commit()
        eventually(5.seconds) { dirs.settings.resolve("source_1.xml").exists() shouldBe true }
    }

    @Test
    fun `app info reports the image formats the v1 reader supports`() {
        eu.kanade.tachiyomi.AppInfo.getSupportedImageMimeTypes() shouldBe
            listOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")
    }
}
```

- [ ] **Step 2: Completar `desktop-core/build.gradle.kts`**

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)
}

dependencies {
    api(projects.sourceApi)
    api(projects.domain)
    api(projects.data)
    api(projects.core.common)
    api(projects.extensionLoader)
    implementation(projects.core.metro)
    implementation(projects.androidCompat)

    implementation(libs.metro.runtime)
    implementation(libs.injekt)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.serialization)
    implementation(libs.logback)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Platform.start boots Koin and AndroidCompat's global config: once per JVM
tasks.test { forkEvery = 1 }
```

- [ ] **Step 3: Implementar `DesktopBindings` y `DesktopGraph`**

`desktop-core/src/main/kotlin/mihon/desktop/core/DesktopBindings.kt`:

```kotlin
package mihon.desktop.core

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConnectionFactory
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.core.metro.IsDebugBuild
import mihon.data.database.DatabaseRepositoryImpl
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Chapter
import tachiyomi.data.Database
import tachiyomi.data.History
import tachiyomi.data.InstantColumnAdapter
import tachiyomi.data.IntColumnAdapter
import tachiyomi.data.Manga
import tachiyomi.data.Manga_track
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter

/** Desktop versions of Mihon's AppBindings and DatabaseBindings. */
@BindingContainer
object DesktopBindings {

    @Provides
    @SingleIn(AppScope::class)
    @AppCoroutineScope
    fun providesAppCoroutineScope(@IsDebugBuild isDebugBuild: Boolean): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, throwable ->
            if (isDebugBuild) {
                val thread = Thread.currentThread()
                thread.uncaughtExceptionHandler?.uncaughtException(thread, throwable)
            } else {
                logcat(LogPriority.ERROR, throwable) { "Uncaught exception in the app scope" }
            }
        }
        return CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesXML(): XML = XML.v1 {
        policy {
            ignoreUnknownChildren()
            autoPolymorphic = true
        }
        xmlDeclMode = XmlDeclMode.Charset
        xmlVersion = XmlVersion.XML10
        setIndent(2)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesProtoBuf(): ProtoBuf = ProtoBuf

    @Provides
    @SingleIn(AppScope::class)
    fun providesSqlDriver(appDirs: AppDirs, databaseRepository: DatabaseRepositoryImpl): SqlDriver {
        return AndroidxSqliteDriver(
            connectionFactory = object : AndroidxSqliteConnectionFactory {
                override val driver: SQLiteDriver = BundledSQLiteDriver()

                override fun createConnection(name: String): SQLiteConnection {
                    return driver.open(name).apply {
                        execSQL("PRAGMA busy_timeout = 3000")
                    }
                }
            },
            databaseType = AndroidxSqliteDatabaseType.File(appDirs.db.resolve("app.db").toFile()),
            schema = Database.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
                concurrencyModel = MultipleReadersSingleWriter(isWal = true, walCount = 4),
            ),
            onConfigure = {
                val version = executePragmaQuery("user_version", { QueryResult.Value(it.apply { next() }.getLong(0)) })
                databaseRepository.isMigrating.value = version in 1..<Database.Schema.version
            },
            onOpen = {
                databaseRepository.isMigrating.value = false
            },
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesDatabase(driver: SqlDriver): Database {
        return Database(
            driver = driver,
            historyAdapter = History.Adapter(
                read_atAdapter = InstantColumnAdapter,
            ),
            mangaAdapter = Manga.Adapter(
                remote_genreAdapter = StringListColumnAdapter,
                remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_favorite_atAdapter = InstantColumnAdapter,
                state_chapter_last_updateAdapter = InstantColumnAdapter,
                state_chapter_next_updateAdapter = InstantColumnAdapter,
                state_cover_last_modifiedAdapter = InstantColumnAdapter,
            ),
            chapterAdapter = Chapter.Adapter(
                remote_date_uploadAdapter = InstantColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_last_page_readAdapter = IntColumnAdapter,
                state_date_fetchAdapter = InstantColumnAdapter,
            ),
            manga_trackAdapter = Manga_track.Adapter(
                start_dateAdapter = InstantColumnAdapter,
                finish_dateAdapter = InstantColumnAdapter,
            ),
        )
    }
}
```

Si `AndroidxSqliteDatabaseType.File` no acepta `java.io.File`, usa el mismo ajuste que en la Task 7 (`absolutePath`).

`desktop-core/src/main/kotlin/mihon/desktop/core/DesktopGraph.kt`:

```kotlin
package mihon.desktop.core

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.metro.IsDebugBuild
import mihon.desktop.extension.ExtensionTrust
import mihon.domain.extension.repository.ExtensionStoreRepository
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.Database

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [DesktopBindings::class],
)
interface DesktopGraph {
    val context: Context
    val appDirs: AppDirs

    val json: Json
    val protoBuf: ProtoBuf
    val xml: XML
    val networkHelper: NetworkHelper
    val javaScriptEngine: JavaScriptEngine
    val preferenceStore: PreferenceStore
    val database: Database

    val extensionStoreRepository: ExtensionStoreRepository
    val extensionTrust: ExtensionTrust

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
            @Provides appDirs: AppDirs,
            @Provides @IsDebugBuild isDebugBuild: Boolean,
        ): DesktopGraph
    }
}
```

- [ ] **Step 4: Crear `DesktopInjektRegistrar` copiando el de Mihon**

```bash
cp app/src/main/java/mihon/app/di/injekt/MetroInjektRegistrar.kt desktop-core/src/main/kotlin/mihon/desktop/core/DesktopInjektRegistrar.kt
```

Edita la copia para que su cabecera quede exactamente así. Desde `override fun <R : Any> getInstance(forType: Type)` hasta el final del archivo no cambia nada, salvo los dos textos de los mensajes `"Mihon's Injekt instance"`, que pasan a decir `"the desktop Injekt instance"`:

```kotlin
package mihon.desktop.core

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.serialization.XML
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.InjektionException
import uy.kohesive.injekt.api.TypeReference
import java.lang.reflect.Type

/** Read-only Injekt view over [DesktopGraph]: exactly the types extensions are allowed to inject. */
class DesktopInjektRegistrar(
    private val application: Application,
    private val graph: DesktopGraph,
) : InjektRegistrar {

    private val bindings = mapOf<Type, () -> Any>(
        Application::class.java to { application },
        Context::class.java to { graph.context },

        Json::class.java to { graph.json },
        ProtoBuf::class.java to { graph.protoBuf },
        XML::class.java to { graph.xml },

        NetworkHelper::class.java to { graph.networkHelper },
        JavaScriptEngine::class.java to { graph.javaScriptEngine },
    )
```

- [ ] **Step 5: Implementar `Platform`, `AppInfo` y logback**

`desktop-core/src/main/kotlin/mihon/desktop/core/Platform.kt`:

```kotlin
package mihon.desktop.core

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.androidcompat.androidCompatModule
import xyz.nulldev.ts.config.configManagerModule

object DesktopBuildInfo {
    // ponytail: hardcoded until packaging (subproject 5/6) generates it from the release tag
    const val VERSION_CODE = 1
    const val VERSION_NAME = "0.1.0"
}

private class DesktopApplication : Application()

object Platform {
    /** Boots AndroidCompat, then the Metro graph, then the Injekt view extensions use. Call once per process. */
    fun start(dirs: AppDirs, isDebugBuild: Boolean = false): DesktopGraph {
        dirs.createAll()
        // AndroidCompat resolves every path (settings/, android-compat/) from this root
        System.setProperty("suwayomi.tachidesk.config.server.rootDir", dirs.data.toString())

        startKoin { modules(androidCompatModule(), configManagerModule()) }
        AndroidCompatInitializer().init()
        val application = DesktopApplication()
        AndroidCompat().startApp(application)
        val context: Context = KoinPlatformTools.defaultContext().get().get()

        val graph = createGraphFactory<DesktopGraph.Factory>().create(context, dirs, isDebugBuild)
        Injekt = InjektScope(DesktopInjektRegistrar(application, graph))
        return graph
    }
}
```

`desktop-core/src/main/kotlin/eu/kanade/tachiyomi/AppInfo.kt`:

```kotlin
package eu.kanade.tachiyomi

import mihon.desktop.core.DesktopBuildInfo

/**
 * Used by extensions.
 */
@Suppress("UNUSED")
object AppInfo {
    /** @since extension-lib 1.3 */
    fun getVersionCode(): Int = DesktopBuildInfo.VERSION_CODE

    /** @since extension-lib 1.3 */
    fun getVersionName(): String = DesktopBuildInfo.VERSION_NAME

    /** Formats the v1 reader decodes (spec 4.3). @since extension-lib 1.5 */
    fun getSupportedImageMimeTypes(): List<String> =
        listOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")
}
```

`desktop-core/src/main/resources/logback.xml`:

```xml
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} %-5level %logger{20} - %msg%n</pattern>
    </encoder>
  </appender>
  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
  </root>
</configuration>
```

- [ ] **Step 6: Ejecutar los tests**

Run: `./gradlew :desktop-core:test`
Expected: 10 tests en `PASSED` (6 de la Task 8 + 4 nuevos).

Si Metro informa de un binding que falta (`Cannot find an @Inject constructor or @Provides-annotated function for: X`), comprueba si `X` estaba en `app/src/main/java/mihon/app/di/AppBindings.kt` o en el factory de `AppGraph`. Si es así, añade su `@Provides` a `DesktopBindings` con el mismo cuerpo. Si `X` es una clase de UI o de un worker, quita del grafo el accessor que la requiere.

- [ ] **Step 7: Commit**

```bash
git add desktop-core
git commit -m "feat(desktop-core): Metro graph, Injekt bridge and platform bootstrap"
```

---

### Task 13: `desktop-core` (III): gestor de extensiones y `SourceManager`

**Files:**
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopExtensionManager.kt`
- Create: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopSourceManager.kt`
- Modify: `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopGraph.kt` (añadir accessors)
- Modify: `desktop-core/build.gradle.kts` (`testImplementation(libs.okhttp.mockwebserver)`)
- Test: `desktop-core/src/test/kotlin/mihon/desktop/core/DesktopExtensionManagerTest.kt`

**Interfaces:**
- Consumes:
  - `ExtensionInstaller`, `DesktopExtensionLoader`, `ExtensionTrust`, `InstalledExtension` (Task 11)
  - `NetworkHelper`, `GET`, `awaitSuccess` (Task 5)
  - `ExtensionStoreRepository` (Task 7)
  - `AppDirs` (Task 8)
- Produces:
  - `DesktopExtensionManager` con:
    - `installed: StateFlow<List<Extension.Installed>>`
    - `addStore(indexUrl: String): Result<Unit>`
    - `available(): List<Extension.Available>`
    - `reload()`
    - `install(ext: Extension.Available): Extension.Installed`
    - `trust(ext: Extension.NotLoaded)`
    - `uninstall(pkgName: String)`
  - `DesktopSourceManager : SourceManager`
  - Accessors en el grafo: `DesktopGraph.extensionManager` y `DesktopGraph.sourceManager`

- [ ] **Step 1: Escribir el test que falla**

`desktop-core/src/test/kotlin/mihon/desktop/core/DesktopExtensionManagerTest.kt`:

```kotlin
package mihon.desktop.core

import eu.kanade.tachiyomi.extension.model.Extension
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopExtensionManagerTest {
    private lateinit var graph: DesktopGraph

    @BeforeAll
    fun start() {
        val root = createTempDirectory("José Pérez manager")
        graph = Platform.start(AppDirs(root.resolve("data"), root.resolve("local")))
    }

    private fun available(apkUrl: String) = Extension.Available(
        name = "Example", pkgName = "pkg.example", versionName = "1.4.1", versionCode = 1, libVersion = 1.4,
        lang = "en", contentWarning = ContentWarning.SAFE, sources = emptyList(), apkUrl = apkUrl, iconUrl = "",
        store = ExtensionStore("https://repo/index.min.json", "Repo", "R", "key", ExtensionStore.Contact("https://repo", null), false, null),
    )

    @Test
    fun `a corrupt download fails cleanly and leaves nothing behind`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("PK\u0003 truncated"))
        server.start()

        shouldThrow<Exception> { graph.extensionManager.install(available(server.url("/ext.apk").toString())) }

        graph.appDirs.extensions.listDirectoryEntries() shouldBe emptyList()
        graph.appDirs.cache.listDirectoryEntries("*.apk") shouldBe emptyList()
        graph.extensionManager.installed.value shouldBe emptyList()
        server.shutdown()
    }

    @Test
    fun `source manager starts empty and stubs unknown ids`() = runBlocking {
        graph.sourceManager.getAll() shouldBe emptyList()
        graph.sourceManager.getOrStub(42L).id shouldBe 42L
    }
}
```

- [ ] **Step 2: Implementar `DesktopExtensionManager`**

`desktop-core/src/main/kotlin/mihon/desktop/core/DesktopExtensionManager.kt`:

```kotlin
package mihon.desktop.core

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import mihon.desktop.extension.DesktopExtensionLoader
import mihon.desktop.extension.ExtensionInstaller
import mihon.desktop.extension.ExtensionTrust
import mihon.domain.extension.repository.ExtensionStoreRepository
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.deleteIfExists

/** Desktop counterpart of Mihon's ExtensionManager: stores, downloads, installs and keeps the loaded list. */
@Inject
@SingleIn(AppScope::class)
class DesktopExtensionManager(
    private val appDirs: AppDirs,
    private val network: NetworkHelper,
    private val storeRepository: ExtensionStoreRepository,
    private val trust: ExtensionTrust,
) {
    private val installer = ExtensionInstaller(appDirs.extensions)
    private val loader = DesktopExtensionLoader(trust)

    private val _installed = MutableStateFlow<List<Extension.Installed>>(emptyList())
    val installed: StateFlow<List<Extension.Installed>> = _installed.asStateFlow()

    suspend fun addStore(indexUrl: String): Result<Unit> = storeRepository.insert(indexUrl)

    suspend fun available(): List<Extension.Available> = storeRepository.fetchExtensions()

    suspend fun reload() = withContext(Dispatchers.IO) {
        val stores = storeRepository.getAll()
        _installed.value = installer.installed().map { loader.load(it, stores) }
    }

    suspend fun install(ext: Extension.Available): Extension.Installed = withContext(Dispatchers.IO) {
        val download = Files.createTempFile(appDirs.cache, ext.pkgName, ".apk")
        try {
            network.client.newCall(GET(ext.apkUrl)).awaitSuccess().use { response ->
                response.body.byteStream().use { Files.copy(it, download, REPLACE_EXISTING) }
            }
            val installedExt = installer.install(download)
            check(installedExt.info.pkgName == ext.pkgName) {
                "Store listed ${ext.pkgName} but the apk is ${installedExt.info.pkgName}"
            }
            val result = loader.load(installedExt, storeRepository.getAll())
            _installed.update { list -> list.filterNot { it.pkgName == result.pkgName } + result }
            result
        } finally {
            download.deleteIfExists()
        }
    }

    suspend fun trust(ext: Extension.NotLoaded) {
        val reason = ext.reason as? Extension.NotLoaded.Reason.Untrusted ?: return
        trust.trust(ext.pkgName, ext.versionCode, reason.signatureHash)
        reload()
    }

    suspend fun uninstall(pkgName: String) = withContext(Dispatchers.IO) {
        installer.uninstall(pkgName)
        _installed.update { list -> list.filterNot { it.pkgName == pkgName } }
    }
}
```

- [ ] **Step 3: Implementar `DesktopSourceManager`**

`desktop-core/src/main/kotlin/mihon/desktop/core/DesktopSourceManager.kt`:

```kotlin
package mihon.desktop.core

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopSourceManager(
    extensionManager: DesktopExtensionManager,
    @AppCoroutineScope scope: CoroutineScope,
) : SourceManager {

    override val sources: StateFlow<List<Source>> = extensionManager.installed
        .map { list -> list.filterIsInstance<Extension.Loaded>().flatMap { it.sources } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun get(sourceKey: Long): Source? = sources.value.find { it.id == sourceKey }

    override suspend fun getOrStub(sourceKey: Long): Source = get(sourceKey) ?: StubSource(sourceKey, "", "")

    override suspend fun getAll(): List<Source> = sources.value

    override suspend fun getOnlineSources(): List<HttpSource> = sources.value.filterIsInstance<HttpSource>()

    // ponytail: stubs for library entries whose extension is gone come with the library (subproject 3)
    override suspend fun getStubSources(): List<StubSource> = emptyList()
}
```

En `DesktopGraph`, añadir:

```kotlin
    val extensionManager: DesktopExtensionManager
    val sourceManager: tachiyomi.domain.source.service.SourceManager
```

- [ ] **Step 4: Ejecutar los tests**

Run: `./gradlew :desktop-core:test`
Expected: 12 tests en `PASSED`.

- [ ] **Step 5: Commit**

```bash
git add desktop-core
git commit -m "feat(desktop-core): extension manager and source manager"
```

---

### Task 14: `compat-harness`, CLI de compatibilidad

**Files:**
- Create: `compat-harness/build.gradle.kts`
- Create: `compat-harness/src/main/kotlin/mihon/desktop/harness/Report.kt`
- Create: `compat-harness/src/main/kotlin/mihon/desktop/harness/Probe.kt`
- Create: `compat-harness/src/main/kotlin/mihon/desktop/harness/Main.kt`
- Modify: `settings.gradle.kts`
- Test: `compat-harness/src/test/kotlin/mihon/desktop/harness/ReportTest.kt`
- Test: `compat-harness/src/test/kotlin/mihon/desktop/harness/ProbeTest.kt`

**Interfaces:**
- Consumes: `Platform.start`, `AppDirs`, `DesktopExtensionManager` (Tasks 12–13) y `HttpException` (Task 5)
- Produces:
  - CLI `./gradlew :compat-harness:run --args="--repo <url> [--limit 100] [--threshold 0.8] [--out compat-report.md] [--packages compat-harness/extensions.txt]"`
  - Código de salida `0` si `passRate >= threshold`; `1` en otro caso
  - `Outcome`, `ExtensionResult`, `Report.passRate`, `Report.toMarkdown()`
  - `OutcomeClassifier.classify(Throwable): Outcome`
  - `probe(source: Source, timeout: Duration): Throwable?`
  - `selectPackages(available: List<Extension.Available>, packages: List<String>?, limit: Int): List<Extension.Available>`

- [ ] **Step 1: Escribir los tests que fallan**

`compat-harness/src/test/kotlin/mihon/desktop/harness/ReportTest.kt`:

```kotlin
package mihon.desktop.harness

import eu.kanade.tachiyomi.network.HttpException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test

class ReportTest {
    @Test
    fun `blocked extensions are left out of the pass rate`() {
        val report = Report(
            listOf(
                ExtensionResult("a", Outcome.PASS, ""),
                ExtensionResult("b", Outcome.PASS, ""),
                ExtensionResult("c", Outcome.ERROR, "x"),
                ExtensionResult("d", Outcome.BLOCKED, "cloudflare"),
            ),
        )
        report.passRate shouldBe 2.0 / 3.0
        report.toMarkdown() shouldContain "66.7%"
    }

    @Test
    fun `empty report fails`() {
        Report(emptyList()).passRate shouldBe 0.0
    }

    @Test
    fun `classifies the failures the threshold depends on`() {
        OutcomeClassifier.classify(HttpException(403)) shouldBe Outcome.BLOCKED
        OutcomeClassifier.classify(HttpException(503)) shouldBe Outcome.BLOCKED
        OutcomeClassifier.classify(IllegalStateException("Failed to bypass Cloudflare")) shouldBe Outcome.BLOCKED
        OutcomeClassifier.classify(NoClassDefFoundError("android/graphics/Picture")) shouldBe Outcome.MISSING_ANDROID_API
        OutcomeClassifier.classify(RuntimeException("x", RuntimeException("Stub!"))) shouldBe Outcome.MISSING_ANDROID_API
        OutcomeClassifier.classify(HttpException(500)) shouldBe Outcome.ERROR
        val timeout = runCatching { runBlocking { withTimeout(1) { kotlinx.coroutines.delay(1_000) } } }.exceptionOrNull()
        (timeout is TimeoutCancellationException) shouldBe true
        OutcomeClassifier.classify(timeout!!) shouldBe Outcome.TIMEOUT
    }
}
```

`compat-harness/src/test/kotlin/mihon/desktop/harness/ProbeTest.kt`:

```kotlin
package mihon.desktop.harness

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class ProbeTest {
    @Test
    fun `a hanging source times out instead of blocking the run`() = runBlocking {
        val source = mockk<Source> { coEvery { getPopularManga(1) } coAnswers { delay(10_000); MangasPage(emptyList(), false) } }
        probe(source, 100.milliseconds).shouldBeInstanceOf<TimeoutCancellationException>()
    }

    @Test
    fun `a working source passes`() = runBlocking {
        val source = mockk<Source> { coEvery { getPopularManga(1) } returns MangasPage(emptyList(), false) }
        probe(source, 1_000.milliseconds) shouldBe null
    }

    @Test
    fun `selection prefers the packages file then english and multi-language by package`() {
        val store = ExtensionStore("u", "n", "b", "k", ExtensionStore.Contact("w", null), false, null)
        fun ext(pkg: String, lang: String) = Extension.Available(pkg, pkg, "1.4.1", 1, 1.4, lang, ContentWarning.SAFE, emptyList(), "", "", store)
        val all = listOf(ext("c", "en"), ext("a", "all"), ext("b", "ja"), ext("d", "en"))

        selectPackages(all, packages = null, limit = 2).map { it.pkgName } shouldBe listOf("a", "c")
        selectPackages(all, packages = listOf("b", "zzz"), limit = 2).map { it.pkgName } shouldBe listOf("b")
    }
}
```

- [ ] **Step 2: Crear el módulo**

`compat-harness/build.gradle.kts`:

```kotlin
plugins {
    alias(mihonx.plugins.kotlin.jvm)
    alias(mihonx.plugins.spotless)
    application
}

application {
    mainClass.set("mihon.desktop.harness.MainKt")
}

dependencies {
    implementation(projects.desktopCore)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

Añadir `include(":compat-harness")` a `settings.gradle.kts`.

- [ ] **Step 3: Implementar el informe y el clasificador**

`compat-harness/src/main/kotlin/mihon/desktop/harness/Report.kt`:

```kotlin
package mihon.desktop.harness

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.TimeoutCancellationException
import java.util.Locale

enum class Outcome { PASS, BLOCKED, INSTALL_FAILED, LOAD_FAILED, MISSING_ANDROID_API, TIMEOUT, ERROR }

data class ExtensionResult(val pkgName: String, val outcome: Outcome, val detail: String)

data class Report(val results: List<ExtensionResult>) {
    /** BLOCKED (Cloudflare) is left out until the desktop WebView exists (subproject 2). */
    private val considered get() = results.filter { it.outcome != Outcome.BLOCKED }

    val passRate: Double
        get() = if (considered.isEmpty()) 0.0 else considered.count { it.outcome == Outcome.PASS }.toDouble() / considered.size

    fun toMarkdown(): String = buildString {
        appendLine("# Extension compatibility")
        appendLine()
        appendLine("Pass rate: **${"%.1f".format(Locale.ROOT, passRate * 100)}%** (${considered.count { it.outcome == Outcome.PASS }}/${considered.size}, ${results.size - considered.size} blocked excluded)")
        appendLine()
        Outcome.entries.forEach { outcome -> appendLine("- $outcome: ${results.count { it.outcome == outcome }}") }
        appendLine()
        appendLine("| Package | Outcome | Detail |")
        appendLine("|---|---|---|")
        results.forEach { appendLine("| ${it.pkgName} | ${it.outcome} | ${it.detail.lineSequence().first().take(120).replace("|", "\\|")} |") }
    }
}

object OutcomeClassifier {
    fun classify(error: Throwable): Outcome {
        val chain = generateSequence(error) { it.cause }.toList()
        return when {
            chain.any { it is TimeoutCancellationException } -> Outcome.TIMEOUT
            chain.any { it is NoClassDefFoundError || it is AbstractMethodError || it.message == "Stub!" } -> Outcome.MISSING_ANDROID_API
            chain.any { it is HttpException && it.code in setOf(403, 503) } -> Outcome.BLOCKED
            chain.any { it.message?.contains("cloudflare", ignoreCase = true) == true } -> Outcome.BLOCKED
            else -> Outcome.ERROR
        }
    }
}
```

- [ ] **Step 4: Implementar el probe y la selección**

`compat-harness/src/main/kotlin/mihon/desktop/harness/Probe.kt`:

```kotlin
package mihon.desktop.harness

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/** Null when the source answered its first popular page in time, otherwise what went wrong. */
suspend fun probe(source: Source, timeout: Duration): Throwable? = try {
    withTimeout(timeout) { source.getPopularManga(1) }
    null
} catch (e: TimeoutCancellationException) {
    e
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    e
}

fun selectPackages(available: List<Extension.Available>, packages: List<String>?, limit: Int): List<Extension.Available> {
    if (packages != null) {
        val byPkg = available.associateBy { it.pkgName }
        return packages.mapNotNull { byPkg[it] }.take(limit)
    }
    return available.filter { it.lang == "all" || it.lang == "en" }.sortedBy { it.pkgName }.take(limit)
}
```

- [ ] **Step 5: Implementar `Main`**

`compat-harness/src/main/kotlin/mihon/desktop/harness/Main.kt`:

```kotlin
package mihon.desktop.harness

import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.runBlocking
import mihon.desktop.core.AppDirs
import mihon.desktop.core.Platform
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private fun Array<String>.option(name: String): String? = indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

fun main(args: Array<String>) {
    val repo = args.option("--repo") ?: run {
        System.err.println("Usage: --repo <index url> [--limit 100] [--threshold 0.8] [--out compat-report.md] [--packages file]")
        exitProcess(2)
    }
    val limit = args.option("--limit")?.toInt() ?: 100
    val threshold = args.option("--threshold")?.toDouble() ?: 0.8
    val out = Path(args.option("--out") ?: "compat-report.md")
    val packages = (args.option("--packages") ?: "compat-harness/extensions.txt").let(::Path)
        .takeIf { it.exists() }
        ?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }

    val home = createTempDirectory("compat-harness")
    val graph = Platform.start(AppDirs(home.resolve("data"), home.resolve("local")))
    val manager = graph.extensionManager

    val report = runBlocking {
        manager.addStore(repo).getOrThrow()
        val targets = selectPackages(manager.available(), packages, limit)
        Report(
            targets.map { available ->
                val installed = runCatching { manager.install(available) }.getOrElse {
                    return@map ExtensionResult(available.pkgName, Outcome.INSTALL_FAILED, it.toString())
                }
                if (installed !is Extension.Loaded) {
                    val reason = (installed as Extension.NotLoaded).reason
                    val outcome = (reason as? Extension.NotLoaded.Reason.Failed)
                        ?.let { if ("NoClassDefFoundError" in it.stackTrace || "Stub!" in it.stackTrace) Outcome.MISSING_ANDROID_API else null }
                        ?: Outcome.LOAD_FAILED
                    return@map ExtensionResult(available.pkgName, outcome, reason.toString())
                }
                val errors = installed.sources.map { probe(it, 30.seconds) }
                if (errors.any { it == null }) {
                    ExtensionResult(available.pkgName, Outcome.PASS, "")
                } else {
                    val first = errors.filterNotNull().first()
                    ExtensionResult(available.pkgName, OutcomeClassifier.classify(first), first.toString())
                }
            },
        )
    }

    out.writeText(report.toMarkdown())
    println(report.toMarkdown())
    exitProcess(if (report.passRate >= threshold) 0 else 1)
}
```

- [ ] **Step 6: Ejecutar los tests**

Run: `./gradlew :compat-harness:test`
Expected: 6 tests en `PASSED`.

- [ ] **Step 7: Ejecución real (manual, con red)**

Run: `./gradlew :compat-harness:run --args="--repo <URL de un index.min.json> --limit 10"`
Expected: se genera `compat-harness/compat-report.md` con 10 filas y el porcentaje. Anota en la descripción del PR el resultado y los fallos `MISSING_ANDROID_API`, que serán la lista de stubs a revisar. No se hace commit de ninguna URL de repo.

- [ ] **Step 8: Commit**

```bash
git add compat-harness settings.gradle.kts
git commit -m "feat(compat-harness): extension compatibility CLI and report"
```

---

### Task 15: CI en Windows y comprobación de licencias

**Files:**
- Replace: `.github/workflows/build.yml`
- Create: `.github/workflows/compat.yml`
- Delete: `.github/workflows/release.yml`, `.github/workflows/update_website.yml` (eran de Android; el release de Windows llega con el empaquetado)
- Create: `scripts/check-licenses.sh`

**Interfaces:**
- Consumes: todas las tasks anteriores
- Produces: checks de CI `build` y `compat`; variable de repo `COMPAT_REPO_URL`, que el mantenedor configura en GitHub

- [ ] **Step 1: Script de licencias**

`scripts/check-licenses.sh`:

```bash
#!/usr/bin/env bash
# Fails when vendored MPL-2.0 code loses its license notice.
set -euo pipefail

status=0
for required in android-compat/LICENSE android-compat/README.md NOTICE; do
  [[ -f "$required" ]] || { echo "missing $required"; status=1; }
done

for file in extension-loader/src/main/kotlin/mihon/desktop/extension/vendor/*.kt; do
  grep -q "Mozilla Public" "$file" || { echo "missing MPL header: $file"; status=1; }
done

exit $status
```

Run: `bash scripts/check-licenses.sh && echo OK`
Expected: `OK`.

Prueba negativa: quita temporalmente la cabecera de `vendor/Dex2Jar.kt`, ejecuta el script y verifica que imprime `missing MPL header: ...` con código 1. Después restaura el archivo con `git checkout -- extension-loader`.

- [ ] **Step 2: Workflow de build**

Reemplazar `.github/workflows/build.yml` por:

```yaml
name: build

on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: windows-latest
    defaults:
      run:
        shell: bash
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - run: bash scripts/check-licenses.sh
      - run: ./gradlew spotlessCheck test
```

- [ ] **Step 3: Workflow nightly de compatibilidad**

`.github/workflows/compat.yml`:

```yaml
name: compat

on:
  schedule:
    - cron: "0 4 * * *"
  workflow_dispatch:

jobs:
  compat:
    runs-on: windows-latest
    if: ${{ vars.COMPAT_REPO_URL != '' }}
    defaults:
      run:
        shell: bash
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - name: Run harness
        env:
          COMPAT_REPO_URL: ${{ vars.COMPAT_REPO_URL }}
        run: ./gradlew :compat-harness:run --args="--repo $COMPAT_REPO_URL --limit 100 --threshold 0.8 --out $GITHUB_WORKSPACE/compat-report.md"
      - name: Publish report
        if: always()
        run: cat compat-report.md >> "$GITHUB_STEP_SUMMARY"
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: compat-report
          path: compat-report.md
```

- [ ] **Step 4: Borrar los workflows de Android**

```bash
git rm .github/workflows/release.yml .github/workflows/update_website.yml
```

- [ ] **Step 5: Verificar en local lo mismo que CI**

Run: `bash scripts/check-licenses.sh && ./gradlew spotlessCheck test`
Expected: `BUILD SUCCESSFUL`. Si spotless falla en archivos nuevos, ejecuta `./gradlew spotlessApply` y repite. `android-compat` no aplica spotless.

- [ ] **Step 6: Commit**

```bash
git add .github scripts
git commit -m "ci: Windows build, nightly extension compatibility and license check"
```

---

## Cobertura del spec

| Requisito del spec | Task |
|---|---|
| Fork de Mihon con historial, LICENSE y avisos, sin `google-services.json` | 1 |
| Kotlin 2.x, JDK 21, módulos del stack | 2, 3, 5, 6, 7 |
| `android-compat` de Suwayomi fijado a un commit, MPL-2.0 aislado y comprobado en CI | 3, 10, 15 |
| Contrato binario de `source-api`, `network`, `Injekt` (`Application`, `Context`, `NetworkHelper`, `Json`) y `AppInfo` | 5, 6, 12 |
| Repos con `signingKeyFingerprint` y sin repos por defecto | 13 (reutiliza `ExtensionStoreService` de Mihon), 14 |
| Verificación de firma v2/v3 (`apksig`), lectura de manifiesto (`apk-parser`) y dex2jar | 9, 10 |
| Caché `%LOCALAPPDATA%\<App>\extensions\<pkg>-<versionCode>.jar` | 8, 11 |
| ClassLoader por extensión con librerías compartidas desde la app | 10 |
| Confianza por firma del repo o por aprobación; la actualización exige la misma firma | 11, 13 |
| Errores aislados por extensión con motivo | 11 |
| BD con los mismos `.sq` y migraciones | 7, 12 |
| Rutas de `%APPDATA%` y `%LOCALAPPDATA%` | 8, 12 |
| Instancia única con `FileLock` y socket local | 8 |
| Harness de compatibilidad ≥ 80 % en CI nightly | 14, 15 |
| CI `build.yml` en `windows-latest` | 15 |
| Cloudflare/KCEF, icono de extensión y filtro por content warning | Subproyecto 2 (desviaciones 5, 6 y 8) |
