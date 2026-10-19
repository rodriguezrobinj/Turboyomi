# Diseño: lector de manga para Windows basado en Mihon

- **Fecha:** 2026-10-18
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Nombre de la app:** `<App>` (provisional; se usa en rutas, protocolo e instalador)
- **Referencia:** `../mihon-main` (Mihon, Apache-2.0)

## 1. Objetivo y alcance

### Objetivo
Producto público para Windows 10/11 equivalente a Mihon (Android): explorar, leer y organizar manga desde
fuentes online (extensiones de Mihon) y archivos locales. Distribuido con instalador, auto-update y
mantenimiento a largo plazo.

### Requisitos acordados
- Compatibilidad con las extensiones APK existentes del ecosistema Mihon/Tachiyomi (`extensions-lib` 1.4 y 1.6).
- La v1 pública incluye las cinco áreas:
  1. Núcleo de lectura: explorar fuentes, búsqueda, detalle de manga, lector paginado/webtoon, historial.
  2. Biblioteca con categorías y actualización periódica de capítulos con notificación.
  3. Descargas offline y lectura local (CBZ/ZIP/CBR/CB7/EPUB/carpetas).
  4. Backup `.tachibk` compatible con Mihon en ambos sentidos.
  5. Trackers (AniList, MAL, Kitsu, MangaUpdates, Shikimori, Bangumi, MangaBaka, Hikka, Komga, Kavita, Suwayomi).

### Criterios de éxito
- El harness de CI carga y ejecuta `getPopularManga(1)` en al menos el 80 % de las 100 extensiones
  más usadas del repo de prueba. El umbral se puede revisar con datos reales del subproyecto 1.
- Un backup de Mihon Android se restaura en `<App>` y el backup generado por `<App>` se restaura en Mihon.
- Las carpetas de descargas y `local/` copiadas desde Android se leen sin conversión.
- El MSI firmado se instala sin permisos de administrador y sin aviso de SmartScreen por falta de firma.

### Fuera de alcance (v1)
- Decodificación AVIF/JXL.
- Sandbox de extensiones (fuera de proceso).
- Botones de acción y barras de progreso en notificaciones.
- Ejecución de jobs con la app cerrada (sin servicio ni tarea programada).
- Telemetría remota.
- Microsoft Store.
- Repositorios de extensiones preinstalados.

### Descomposición
Cada subproyecto tiene su propio ciclo de spec, plan e implementación. La v1 pública sale cuando estén los cinco.

1. Plataforma base + compatibilidad con extensiones (secciones 2 y 3)
2. Exploración + lector (secciones 4.2 y 4.3)
3. Biblioteca + actualizaciones (sección 5.1)
4. Descargas + lectura local (secciones 5.2 y 5.3)
5. Backup + trackers (secciones 5.4 y 5.5)

## 2. Arquitectura general

### Enfoque
Kotlin Multiplatform + Compose Multiplatform Desktop en **un solo proceso JVM**. Las extensiones obligan a
usar JVM. Un solo stack maximiza la reutilización del código de Mihon y reduce el coste de mantenimiento.

Alternativas descartadas:
- Backend Kotlin + UI nativa (WinUI 3/Tauri): dos stacks y un contrato de API que mantener.
- Fork de Suwayomi-Server: dependencia de una arquitectura ajena y dos stacks.

### Estrategia de repositorio
Fork de `mihon-main` con migración módulo a módulo, para conservar el historial y poder traer cambios de upstream.
Se conservan `LICENSE` y los avisos de autoría (Apache-2.0). El nombre y el logo son propios (marca), y
`app/google-services.json` no se publica.

### Stack
| Área | Tecnología |
|---|---|
| Lenguaje / runtime | Kotlin 2.x, JDK 21 (JRE embebido con `jlink`) |
| UI | Compose Multiplatform Desktop, Material3, `materialKolor` |
| Navegación | Voyager |
| DI | Metro (app) + Injekt (solo como registro para extensiones) |
| BD | SQLDelight + `JdbcSqliteDriver` (xerial `sqlite-jdbc`) |
| Red | OkHttp 5 (brotli, zstd, DoH) |
| Imágenes | Coil 3 (fetcher OkHttp), Skia (Skiko) para decodificar |
| Serialización | kotlinx-serialization (JSON, protobuf) |
| i18n | moko-resources (strings de Mihon/Weblate) |
| WebView | KCEF (JCEF para Compose), descargado en el primer uso |
| Archivos comprimidos | `java.util.zip` (CBZ/ZIP/EPUB), `sevenzipjbinding` (CBR/RAR5/CB7) |
| APK | `com.android.tools.build:apksig`, `net.dongliu:apk-parser`, dex2jar (fork ThexXTURBOXx) |
| Compat Android | AndroidCompat de Suwayomi (MPL-2.0) |
| Secretos | DPAPI vía `jna-platform` (`Crypt32Util`) |
| Tests | JUnit 5, kotest-assertions, mockk, `runComposeUiTest` |

### Módulos
| Módulo | Tipo | Origen | Responsabilidad |
|---|---|---|---|
| `core/common` | KMP | Mihon (`commonMain` + `jvmMain`) | utilidades, red, logging, preferencias |
| `core/archive` | JVM | reescrito | lectura de CBZ/ZIP/EPUB/CBR/CB7 |
| `domain` | KMP | Mihon, cambios mínimos | modelos e interactors |
| `data` | KMP | Mihon | SQLDelight, repositorios, backup |
| `core-metadata` | KMP | Mihon, sin cambios | `ComicInfo.xml`, `details.json` |
| `i18n` | KMP | Mihon, sin cambios | strings |
| `source-api` | JVM | Mihon | API de extensiones, binariamente compatible |
| `android-compat` | JVM | Suwayomi (MPL-2.0) | stubs de `android.*` / `androidx.preference.*` |
| `extension-loader` | JVM | nuevo | repos, descarga, firma, dex2jar, ClassLoader |
| `source-local` | KMP | Mihon | fuente local (`java.nio.file` en lugar de `unifile`) |
| `presentation-core` | CMP | Mihon portado | tema y componentes |
| `desktop-app` | CMP | nuevo, con pantallas de Mihon | ventana, navegación, lector, ajustes, tray, jobs |

Acoplamiento a Android medido (archivos con `import android`): `i18n` 0, `core-metadata` 0, `domain` 3,
`source-local` 3, `data` 7, `core/common` 17, `presentation-core` 40.

### Flujo
`desktop-app` (UI) → `domain` (interactors) → `data` (SQLite, preferencias) y fuentes (`source-api`,
implementadas por extensiones cargadas con `extension-loader` sobre `android-compat`, o por `source-local`).

## 3. Compatibilidad con extensiones

### Contrato binario
- Se mantienen los paquetes y firmas de `eu.kanade.tachiyomi.source.*` y `eu.kanade.tachiyomi.network.*`
  (`GET`, `NetworkHelper`, `awaitSuccess`, etc.).
- El registro `Injekt` provee `Application`/`Context`, `NetworkHelper` y `Json`
  (`HttpSource`: `network: NetworkHelper by injectLazy()`).
- La app provee las librerías compartidas: OkHttp, Jsoup, RxJava 1, kotlinx-serialization y Kotlin stdlib.
- Versiones soportadas: `SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)`, igual que `ExtensionLoader.kt` de Mihon.

### Flujo de instalación (`extension-loader`)
1. El usuario añade un repo (`index.min.json` + `repo.json` con `signingKeyFingerprint`). No hay repos por defecto.
2. Se descarga el APK.
3. Se verifica la firma v2/v3 con `apksig` y se calcula el SHA-256 del certificado.
4. Se lee el `AndroidManifest` binario con `apk-parser`: `tachiyomi.extension.class`, `tachiyomi.extension.factory`,
   `tachiyomix.extensionLib`, `tachiyomi.extension.nsfw`, `tachiyomix.contentWarning` y `tachiyomix.name`.
5. dex2jar genera `%LOCALAPPDATA%\<App>\extensions\<pkg>-<versionCode>.jar` y se extrae el icono.
6. Se crea un `URLClassLoader` por extensión y se instancian `Source` / `SourceFactory`.

### ClassLoader
Se cargan **desde el padre** (compartidos): `android.*`, `androidx.preference.*`, `eu.kanade.tachiyomi.*`,
`okhttp3.*`, `okio.*`, `kotlin.*`, `kotlinx.*`, `org.jsoup.*`, `rx.*`, `uy.kohesive.injekt.*`.
El resto se carga primero desde el hijo. Así se evitan `ClassCastException` por tipos duplicados.

### Confianza
- Una extensión es de confianza si su firma coincide con el `signingKey` de su repo.
- En otro caso, requiere aprobación explícita del usuario, que se guarda junto con la huella.
- Al actualizar, la firma nueva debe contener las firmas de la versión instalada; si no, se rechaza.

### `android-compat`
Se reutiliza AndroidCompat de Suwayomi como módulo separado, fijado a un commit o tag.
- Sus archivos y las modificaciones sobre ellos se publican bajo MPL-2.0. El resto de la app queda en Apache-2.0.
- CI comprueba que esos archivos conservan sus cabeceras MPL.
- Superficie mínima esperada:
  - `Context`/`Application` (`getSharedPreferences`, `filesDir`, `cacheDir`, `packageName`)
  - `SharedPreferences` (un JSON por fuente en `%APPDATA%\<App>\prefs\source_<id>.json`)
  - `androidx.preference.*` como modelos que la UI dibuja en Compose
  - `Bitmap`/`BitmapFactory`/`Canvas`/`Rect` sobre `BufferedImage`
  - `Base64`, `Log`, `TextUtils`, `Uri`, `Html` y `Build.VERSION`
  - `Toast` (convertido a notificación o log) y `Handler`/`Looper` (hilo dedicado)

### Cloudflare y WebView
- KCEF se descarga en el primer uso (~100 MB) en `%LOCALAPPDATA%\<App>\cef`.
- `CloudflareInterceptor` abre CEF, espera `cf_clearance` y copia las cookies al `CookieJar` compartido de OkHttp.
- El User-Agent por defecto de OkHttp es idéntico al de CEF.
- El mismo CEF sirve para "Abrir en WebView" y para los logins de las fuentes.
- Si una extensión usa `WebView` directamente, se cubre un subconjunto sobre CEF. Lo no soportado lanza
  `UnsupportedOperationException` con un mensaje claro.

### Seguridad
Las extensiones son código arbitrario en el mismo proceso. No hay `SecurityManager` desde JDK 24.
- Mitigación: confianza por firma y aprobación del usuario.
- Mejora futura: proceso hijo por extensión con IPC.

### Errores
Cada fallo de carga queda aislado en su extensión, que aparece como "No cargada: <motivo>". Motivos posibles:
- lib no soportada
- firma no confiable o cambiada
- fallo de dex2jar
- `NoClassDefFoundError`: se registra el nombre de la clase Android que falta

## 4. Datos, UI y lector

### 4.1 Almacenamiento
| Qué | Ruta |
|---|---|
| BD | `%APPDATA%\<App>\db\app.db` (mismos `.sq` y migraciones que `data` de Mihon) |
| Preferencias de app | `%APPDATA%\<App>\prefs\app.json` (`PreferenceStore` de Mihon sobre `SharedPreferences` de compat) |
| Preferencias de fuentes | `%APPDATA%\<App>\prefs\source_<id>.json` |
| Backups automáticos | `%APPDATA%\<App>\backups` |
| Caché | `%LOCALAPPDATA%\<App>\cache` |
| Extensiones / CEF | `%LOCALAPPDATA%\<App>\extensions`, `%LOCALAPPDATA%\<App>\cef` |
| Logs | `%LOCALAPPDATA%\<App>\logs` |
| Descargas y local | configurable; por defecto `Documentos\<App>` (layout de Mihon) |

**Instancia única:** `FileLock` sobre `%APPDATA%\<App>\.lock` y un socket en `127.0.0.1`, cuyo puerto se escribe en el lock.
Una segunda instancia reenvía sus argumentos (por ejemplo, URLs OAuth) y termina. La instancia principal enfoca su ventana.

### 4.2 UI
- Pantallas de Mihon portadas a Compose Multiplatform, con navegación Voyager.
- Layout ancho con rail de navegación (`material3NavSuite`).
- Menús contextuales con clic derecho.
- Atajos de teclado (Ctrl+F buscar, F5 actualizar).
- Tamaño y posición de la ventana persistidos entre sesiones.
- Tema claro/oscuro según Windows.
- Ajustes de cada fuente generados a partir de su `PreferenceScreen`.

### 4.3 Lector (reescrito; el de Mihon usa Views de Android)
- **Paginado** (izquierda-derecha, derecha-izquierda, vertical): `HorizontalPager` / `VerticalPager`.
- **Webtoon**: `LazyColumn`. Las imágenes más altas que el límite de textura se dividen en tiles al decodificar.
- **Zoom y paneo**: `net.engawapg.lib:zoomable`, doble clic y Ctrl+rueda.
- **Entrada**: flechas, Espacio, AvPág/RePág, rueda, zonas de clic laterales y F11 para pantalla completa.
- **Modo doble página.**
- **Carga de páginas**: `PageLoader` de Mihon portado (`HttpPageLoader`, `DownloadPageLoader`, `ArchivePageLoader`),
  con precarga de N páginas.
- **Formatos v1**: JPEG, PNG, WebP, GIF y BMP.

## 5. Tareas en segundo plano, descargas, backup y trackers

### 5.1 Scheduler y notificaciones
- Scheduler de corrutinas en proceso. Cada job guarda `lastRun` en las preferencias, y se evalúa al arrancar y cada minuto.
- Jobs: actualización de biblioteca, updates de extensiones, backup automático, descargas y update de la app.
- Opciones del usuario:
  - "Cerrar a la bandeja"
  - "Iniciar con Windows" (`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, arranca con `--minimized`)
- Notificaciones con Compose `Tray` + `TrayState.sendNotification`. El progreso se muestra dentro de la app.

### 5.2 Descargas
- `Downloader.kt` portado: concurrencia por fuente, cola persistida, formato carpeta o CBZ, y `ComicInfo.xml`.
- Sanitizador de nombres para Windows:
  - elimina `<>:"/\|?*` y los caracteres de control
  - evita `CON`, `PRN`, `AUX`, `NUL`, `COM1`–`COM9` y `LPT1`–`LPT9` (con o sin extensión)
  - quita el punto o espacio final
  - trunca cada segmento a 120 caracteres
- Antes de cada capítulo se comprueba el espacio libre con `File.usableSpace`.

### 5.3 Lectura local
Mismo layout que Mihon: `local/<Manga>/<capítulo>.cbz|.zip|.cbr|.cb7|.epub|carpeta`, con `cover.jpg` y
`details.json` o `ComicInfo.xml`.

### 5.4 Backup
- `BackupCreator` y su restorer de Mihon, portados al formato `.tachibk` (gzip + protobuf).
- Las preferencias exclusivas de Android se ignoran al restaurar.
- El backup automático conserva los N últimos.

### 5.5 Trackers
- `TrackerManager` y las APIs de Mihon, portadas.
- OAuth:
  1. El instalador registra el protocolo `<app>://` en `HKCU\Software\Classes`.
  2. El login se abre en el navegador del sistema (`Desktop.browse`).
  3. El redirect lanza una segunda instancia, que reenvía la URL por el socket de instancia única.
- Client IDs propios registrados en cada tracker. No se reutilizan los de Mihon.
- Tokens y credenciales (incluidos Komga, Kavita y Suwayomi) cifrados con DPAPI. Nunca se guardan en texto plano.

## 6. Empaquetado, distribución y operación

### Empaquetado
- Plugin Compose Desktop `nativeDistributions` (`jpackage`) genera un MSI y un ZIP portable.
- JRE 21 recortado con `jlink`. Incluye como mínimo `java.desktop`, `java.net.http`, `java.sql`,
  `jdk.crypto.ec` y `jdk.unsupported`; el harness de compatibilidad valida que no falte ninguno.
- MSI por usuario en `%LOCALAPPDATA%\Programs\<App>`, sin permisos de administrador.
- Sin ProGuard en la v1, porque las extensiones enlazan contra clases de la app.
- Firma Authenticode (Azure Trusted Signing o certificado OV) del MSI y del ejecutable.

### Auto-update
`AppUpdateChecker` portado:
1. Consulta GitHub Releases.
2. Descarga el MSI.
3. Verifica el SHA-256 y la firma Authenticode.
4. Ejecuta `msiexec /i <msi> /passive` y cierra la app.

Mejora futura: Conveyor para updates delta o MSIX.

### Distribución
- GitHub Releases: MSI, ZIP y `SHA256SUMS`.
- Manifest en winget.

### CI (GitHub Actions, `windows-latest`)
| Workflow | Disparador | Pasos |
|---|---|---|
| `build.yml` | PR / push | spotless/ktlint, tests, `packageMsi` |
| `compat.yml` | nightly | harness de extensiones; publica el % de compatibilidad |
| `release.yml` | tag `v*` | build, firma, checksums, GitHub Release, PR a `winget-pkgs` |

### Logs y errores
- Logger rotativo detrás de la función `logcat()` existente, para no cambiar las llamadas.
- Un `UncaughtExceptionHandler` escribe el crash report y muestra un diálogo con "Copiar informe" y "Abrir carpeta".
- Sin telemetría remota en la v1. Mejora futura: Sentry opt-in con `sentry-java`.

## 7. Testing
- Tests existentes de `domain`/`data` portados. Las migraciones SQLDelight se ejecutan sobre `sqlite-jdbc`.
- Tests nuevos:
  - sanitizador de nombres de Windows
  - roundtrip de un `.tachibk` real de Mihon
  - roundtrip de DPAPI
  - `extension-loader` con un APK de prueba firmado
  - reglas de delegación del ClassLoader
- Smoke de UI con `runComposeUiTest`: la app arranca y abre la biblioteca.
- El harness de compatibilidad de extensiones es la métrica de release.

## 8. Límites conocidos de la v1
- Sin AVIF/JXL. Mejora: `libavif`/`libjxl` vía JNI.
- Sin sandbox de extensiones. Mejora: proceso hijo por extensión.
- Notificaciones sin acciones. Mejora: toasts WinRT vía JNA.
- Jobs solo con la app abierta o en la bandeja. Mejora: Programador de tareas.
- Accesibilidad limitada de Compose Desktop en Windows (Java Access Bridge). Se garantiza navegación completa por teclado.
