# Subproyecto 9a: ajustes avanzados — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ajustes › Avanzado con las opciones de red, datos, biblioteca y depuración de Mihon.

**Architecture:** La lógica nueva va en piezas pequeñas y probadas por separado: `httpLoggingInterceptor` en el
`NetworkHelper` de escritorio, `DesktopLogging` y `CefData` en `desktop-core`, y `CrashLogUtil` en `desktop-app`. La
pantalla solo las llama, junto con las preferencias que ya existen.

**Tech Stack:** Kotlin, Compose Multiplatform desktop, OkHttp 5, logback, JUnit 5 + kotest.

**Spec:** `docs/superpowers/specs/2026-10-27-subproyecto-9a-ajustes-avanzados-design.md`

## Global Constraints
- Rama `feat/advanced-settings-9a`, desde `sync-upstream-2026-10-27` (PR #13): usa `redactSensitiveHeaders`, que
  llega con el sync.
- Sin imports de Android en `desktop-app`, aparte de las shims permitidas (`android.content.Context`).
- Textos nuevos solo `desktop_dump_logs_failed`; los cuatro de Mihon que no tienen `es` se traducen.

## Review Focus
- User agent vacío o con salto de línea: no se guarda, sale `error_user_agent_string_invalid`. → Tarea 6.
- Borrar datos del navegador con Chromium en marcha: no se borra nada en caliente; se borra en el siguiente arranque,
  antes de iniciarlo. → Tarea 3.
- Volcado sin extensiones con problemas: el archivo solo lleva la información de depuración. → Tarea 4.
- Registro detallado apagado: el cliente HTTP no lleva el interceptor (como hoy). → Tarea 1.

---

### Task 1: Registro HTTP con cabeceras ocultas
**Files:** Modify `core/common/src/desktop/kotlin/eu/kanade/tachiyomi/network/NetworkHelper.kt`; create
`core/common/src/test/kotlin/eu/kanade/tachiyomi/network/HttpLoggingTest.kt`.
- [ ] Test: `httpLoggingInterceptor(redact = true, logger)` contra un `MockWebServer`, con la petición
  `Authorization: secreto`: ninguna línea registrada contiene `secreto`. Con `redact = false`, sí.
- [ ] `internal fun httpLoggingInterceptor(redact: Boolean, logger: HttpLoggingInterceptor.Logger)`: nivel HEADERS y
  `redactHeader("Authorization")`/`redactHeader("Auth")` si `redact`. En `client`, logger =
  `{ logcat(LogPriority.INFO, "OkHttp") { it } }`.
- [ ] `./gradlew :core:common:test --tests "*HttpLoggingTest*"`.

### Task 2: Nivel de log detallado
**Files:** Create `desktop-core/src/main/kotlin/mihon/desktop/core/DesktopLogging.kt`; modify
`desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt`.
- [ ] `object DesktopLogging { fun setVerbose(on: Boolean) }`: pone el logger raíz de logback en DEBUG o INFO.
- [ ] En `Main.kt`, justo después de `DesktopApp.graph = graph`:
  `DesktopLogging.setVerbose(graph.networkPreferences.verboseLogging.get())` (añadir `networkPreferences` al grafo si
  no está).

### Task 3: Borrar datos del navegador
**Files:** Create `desktop-core/src/main/kotlin/mihon/desktop/core/cef/CefData.kt` and
`desktop-core/src/test/kotlin/mihon/desktop/core/cef/CefDataTest.kt`; modify
`desktop-core/src/main/kotlin/mihon/desktop/core/Platform.kt`.
- [ ] Test: `CefData(dir).clear(running = false)` borra `dir` y devuelve `true`; `clear(running = true)` deja
  `dir` y la marca `<dir>.clear`, devuelve `false`; `applyPending()` borra `dir` y la marca.
- [ ] `class CefData(private val dir: Path)` con `clear(running): Boolean` y `applyPending()`, como la desinstalación
  de `CefInstaller` (marca hermana; si borrar falla con `IOException`, deja la marca).
- [ ] `Platform.start`: `CefData(dirs.cefCache).applyPending()` junto a `applyPendingRemoval()`, antes de
  `CefRuntime.startAsync`.

### Task 4: Volcado de registros
**Files:** Create `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/util/CrashLogUtil.kt` and
`desktop-app/src/test/kotlin/eu/kanade/tachiyomi/util/CrashLogUtilTest.kt`.
- [ ] Test con el grafo de prueba: `debugInfo()` contiene `DesktopBuildInfo.VERSION_NAME`; `extensionsInfo()` es null
  sin extensiones con problemas.
- [ ] `@Inject class CrashLogUtil(appDirs, extensionManager, basePreferences, cefInstaller)`, el gemelo de Mihon:
  `debugInfo()` (texto en inglés: app, versión y commit, ID de instalación, carpeta de datos, Windows, Java, navegador,
  hora con zona), `extensionsInfo()` y `suspend fun dumpLogs(): File` que escribe `logs\debug_info.txt`. Expuesto en
  `AppGraph` como `crashLogUtil`.

### Task 5: Pantalla de información de depuración
**Files:** Create `desktop-app/src/main/kotlin/mihon/desktop/app/settings/DebugInfoScreen.kt`.
- [ ] `object DebugInfoScreen : SearchableSettings`, título `pref_debug_info`: una fila por línea de `debugInfo()`
  (título = clave, subtítulo = valor) y una fila "Copy" que copia todo con `copyToClipboard`.

### Task 6: Pantalla Avanzado
**Files:** Modify `desktop-app/src/main/kotlin/mihon/desktop/app/settings/AdvancedSettingsScreen.kt`,
`i18n/.../base/strings.xml`, `i18n/.../es/strings.xml`; modify
`desktop-app/src/test/kotlin/mihon/desktop/app/settings/SettingsTest.kt`.
- [ ] Grupos del spec §2. Volcar: `scope.launch { runCatching { crashLogUtil.dumpLogs() } }` y, con el archivo,
  `ProcessBuilder("explorer.exe", "/select,${file}")`; si falla, `desktop_dump_logs_failed`.
- [ ] Strings: `desktop_dump_logs_failed` (base + es) y `es` de `pref_redact_sensitive_headers(_summary)` y
  `pref_enable_chapter_name_hash(_details)`.
- [ ] Test Compose: aparecen "DNS over HTTPS" y "Registro detallado"; escribir un user agent con `\n` no cambia la
  preferencia; con uno válido y "Restablecer", vuelve al de serie.
- [ ] `./gradlew :desktop-app:test --tests "*SettingsTest*" --tests "*CrashLogUtilTest*"`.

### Task 7: Cierre
- [ ] `./gradlew spotlessApply`, `./gradlew spotlessCheck test --continue`, `scripts/check-*.sh`.
- [ ] `graphify update .`, commit, push y PR contra `sync-upstream-2026-10-27`.
