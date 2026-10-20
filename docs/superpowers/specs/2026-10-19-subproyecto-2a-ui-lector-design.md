# Diseño: subproyecto 2a — UI y lector

- **Fecha:** 2026-10-19
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md` (secciones 4.2 y 4.3)
- **Base:** `master` en `44d167379` (subproyectos 1 y 1.1 integrados; harness al 89,4 %)

## 1. Objetivo y alcance

### Objetivo
Primera versión usable de la app. El usuario instala extensiones desde un repositorio que él elige, confía en
ellas, busca en una fuente, abre un manga y lee sus capítulos desde la red con un lector de escritorio completo.

### División del subproyecto 2
- **2a (esta spec):** ventana, navegación, gestión de extensiones (incluye iconos y filtro de contenido para
  adultos), explorar y buscar, detalles del manga, historial, ajustes y lector.
- **2b (spec aparte):** KCEF para Cloudflare, WebView de las extensiones y logins de las fuentes.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| Estilo visual | Mihon adaptado a escritorio: componentes y tema Material 3 de Mihon, con rail, cuadrículas adaptativas, dos paneles en ventanas anchas, hover y clic derecho |
| Enfoque | Módulo nuevo `desktop-app` que porta de forma selectiva las pantallas y ViewModels de Mihon; el lector es nuevo |
| Lector | Toda la sección 4.3 de la spec padre |

### Supuestos
- Al abrir un manga se guardan en la BD el manga, los capítulos, el progreso (capítulo leído y última página) y
  el historial, igual que en Mihon. "Añadir a biblioteca" marca el manga como favorito; la pantalla de
  biblioteca llega en el subproyecto 3.
- La app se ejecuta con `gradlew :desktop-app:run`. El instalador MSI pertenece a la fase de empaquetado.

### Fuera de alcance (2a)
| Qué | Dónde |
|---|---|
| Biblioteca, novedades, categorías, migración | Subproyecto 3 |
| Descargas y lectura local (`DownloadPageLoader`, `ArchivePageLoader`, `DirectoryPageLoader`) | Subproyecto 4 |
| Seguimiento (trackers), notas, backup | Subproyecto 5 |
| WebView, Cloudflare, "Abrir en WebView" | Subproyecto 2b; hasta entonces se abre el navegador del sistema |

## 2. Arquitectura y módulos

```
:desktop-app (nuevo)        ventana, navegación, pantallas y lector
   ├── :presentation-core   pasa a JVM con Compose Multiplatform
   ├── :desktop-core        grafo, extensiones, red y BD (existente)
   └── :domain / :data / :source-api   (ya portados)
```

- **Compose Multiplatform (CMP).** Un convention plugin nuevo, `mihonx.plugins.compose.desktop`, aplica
  `org.jetbrains.compose` y el plugin de compilador de Compose. `presentation-core` sustituye los artefactos
  `androidx.compose.*` por los de CMP. Solo 2 de sus 40 archivos importan `android.*`; esos usos se sustituyen
  por equivalentes de escritorio. Paging usa los artefactos KMP de AndroidX (3.3 o superior), que publican JVM.
- **ViewModels.** Mihon usa `androidx.lifecycle.ViewModel` y `collectAsStateWithLifecycle`. En escritorio se
  usan los artefactos KMP `org.jetbrains.androidx.lifecycle`, así que los ViewModels portados cambian poco.
- **Navegación.** Voyager, como en Mihon. La versión de Mihon (`2.2.21-1.10.3`) es una compilación propia y
  puede no publicar un artefacto JVM. La primera tarea del plan lo comprueba; si no hay artefacto JVM, se usa
  Voyager 1.1.0-beta upstream, que sí tiene soporte de escritorio.
- **Imágenes.** Coil 3 en JVM, con el `OkHttpClient` de `NetworkHelper`. Se porta `MangaCoverFetcher` de Mihon,
  que usa el `client` y las `headers` de la fuente. La caché de disco de portadas va en
  `%LOCALAPPDATA%\<App>\cache\covers`.
- **Flujo de datos.** ViewModel → interactores de `domain` (por Injekt, desde `DesktopGraph`) → `data` y
  fuentes. Es el mismo patrón que en Mihon.

### Paquetes de `desktop-app`
| Paquete | Contenido |
|---|---|
| `main` | `main()`, `application { Window }`, tema, rail de navegación, atajos globales |
| `ui.browse` | Pestañas Fuentes y Extensiones, detalles de extensión, navegación de una fuente, filtros, búsqueda global |
| `ui.manga` | Detalles del manga y lista de capítulos |
| `ui.history` | Historial |
| `ui.settings` | Ajustes generales, de explorar, del lector, avanzados y de cada fuente |
| `ui.reader` | ViewModel y loaders portados; `viewer` nuevo en Compose |
| `platform` | Portapapeles AWT, abrir URL en el navegador del sistema, snackbar en lugar de toast, Esc como atrás |

### Rail de navegación en 2a
Explorar, Historial y Ajustes. Biblioteca y Novedades se añaden en el subproyecto 3; no se muestran entradas
vacías.

## 3. Pantallas

| Pantalla | Origen en Mihon | Cambios para escritorio |
|---|---|---|
| Explorar › Fuentes | `SourcesTab`, `SourcesFilterScreen` (idiomas) | Clic derecho sobre una fuente: fijar u ocultar |
| Explorar › Extensiones | `ExtensionsTab`, `ExtensionFilterScreen` | Instalar, actualizar y desinstalar; diálogo para confiar en la firma; los repositorios se gestionan desde Ajustes |
| Detalles de extensión | `ExtensionDetailsScreen` | `SourcePreferencesScreen` se reescribe en Compose (el original usa `PreferenceFragmentCompat`, basado en Views). Dibuja los modelos `androidx.preference` de AndroidCompat: `EditTextPreference`, `ListPreference`, `MultiSelectListPreference`, `SwitchPreferenceCompat` y `CheckBoxPreference` |
| Navegar una fuente | `BrowseSourceScreen`, `SourceFilterDialog` | Populares, recientes y búsqueda con filtros; vista en cuadrícula o lista; clic derecho: añadir a biblioteca; F5 refresca |
| Búsqueda global | `GlobalSearchScreen` | Se abre con Ctrl+F desde cualquier pantalla |
| Detalles del manga | `MangaScreen` (presentation y ui) | Dos paneles cuando la ventana es ancha; selección múltiple de capítulos con Ctrl+clic y Shift+clic; clic derecho: marcar como leído o no leído y añadir o quitar marcador; portada a pantalla completa con "Guardar como…" (`FileDialog`) |
| Historial | `HistoryScreen` | Continuar la lectura y borrar entradas |
| Ajustes | Pantallas de ajustes de Mihon, recortadas | General (tema sistema/claro/oscuro, idioma), Explorar (repositorios, contenido para adultos), Lector (valores por defecto), Avanzado (borrar caché, abrir carpeta de logs) |

### Iconos de extensión
- **Disponibles:** el `iconUrl` del repositorio, cargado con Coil.
- **Instaladas:** al instalar, se extrae del APK el icono `res/mipmap*/ic_launcher.png` de mayor densidad y se
  guarda junto al `.jar` como `<pkg>.png`. Si el APK no tiene icono, se muestra uno genérico.

### Filtro de contenido para adultos
- Las extensiones y fuentes con `isNsfw` muestran la etiqueta `ContentWarningLabel` de Mihon.
- El ajuste "Mostrar contenido para adultos" está **desactivado por defecto**. Mientras está desactivado, esas
  extensiones se ocultan en Disponibles y sus fuentes se ocultan en Fuentes y en la búsqueda global.
- Las extensiones ya instaladas siguen visibles en Instaladas, para poder desinstalarlas.

### Atajos globales
| Tecla | Acción |
|---|---|
| Esc / Alt+← | Atrás |
| Ctrl+F | Búsqueda global |
| F5 | Refrescar la pantalla actual |
| Ctrl+, | Ajustes |

## 4. Lector

### Lo que se porta y lo que es nuevo
- **Se porta** (unas 2.000 líneas de Mihon con solo 4 imports de Android): `ReaderViewModel`,
  `ChapterLoader`, `PageLoader`, `HttpPageLoader`, `ChapterCache` (`DiskLruCache` en
  `%LOCALAPPDATA%\<App>\cache\chapters`), los modelos `ReaderChapter`, `ReaderPage` y `ChapterTransition`, y
  `ReaderPreferences`.
- **Es nuevo:** el paquete `ui.reader.viewer`, en Compose.

### Pantalla
El lector es una pantalla de Voyager que ocupa toda la ventana. F11 alterna la pantalla completa real
(`WindowPlacement.Fullscreen`).

### Modos de lectura
El modo por defecto se elige en Ajustes. Cada manga puede tener su propio modo, guardado en sus `viewer_flags`
como en Mihon.

| Modo | Implementación |
|---|---|
| Izquierda a derecha / derecha a izquierda | `HorizontalPager`; derecha a izquierda usa `reverseLayout` |
| Vertical paginado | `VerticalPager` |
| Webtoon | `LazyColumn` sin separación entre páginas; margen lateral ajustable |
| Doble página (sobre izquierda a derecha y derecha a izquierda) | Empareja páginas; una página más ancha que alta se muestra sola; el botón "desplazar 1" corrige la paridad |

### Imágenes
- Se decodifican con Skia (`Image.makeFromEncoded`), que ya admite JPEG, PNG, WebP, GIF y BMP. Los GIF animados
  se reproducen con `Codec`.
- Una imagen de más de 8192 px de alto se divide al decodificar en tiles de 4096 px como máximo, para no
  superar el límite de textura de la GPU.
- Solo se guardan decodificadas las páginas entre la actual −2 y la actual +4. El resto queda en `ChapterCache`
  como bytes.
- Se precargan 4 páginas por defecto (configurable), como en Mihon.

### Zoom
`net.engawapg.lib:zoomable` (compatible con CMP). Doble clic y Ctrl+rueda hacen zoom; arrastrar mueve la
imagen. El zoom vuelve a 1 al cambiar de página.

### Entrada
| Entrada | Acción |
|---|---|
| ← / → | Página anterior o siguiente según la dirección del modo (se invierten en derecha a izquierda) |
| ↑ / ↓, rueda | Desplazamiento en webtoon; página anterior o siguiente en modos paginados |
| Espacio / Shift+Espacio, AvPág / RePág | Página siguiente o anterior |
| Inicio / Fin | Primera o última página |
| N / P | Capítulo siguiente o anterior |
| Clic en el tercio izquierdo o derecho | Página anterior o siguiente (según la dirección) |
| Clic en el centro | Mostrar u ocultar los controles |
| F11 | Pantalla completa |
| Esc | Sale de la pantalla completa; si no está en pantalla completa, cierra el lector |
| Clic derecho sobre una página | Guardar imagen o copiar imagen |

### Controles en pantalla
- Barra superior: título del manga y del capítulo.
- Barra inferior: deslizador de páginas, capítulo anterior y siguiente, modo de lectura y panel de ajustes
  rápidos (modo, escala, color de fondo).
- Los controles se ocultan tras 3 s sin mover el ratón.

### Paso entre capítulos
Entre dos capítulos aparece una página `ChapterTransition`, como en Mihon. Al seguir avanzando se carga el
capítulo siguiente.

### Progreso
- La última página leída se guarda al cambiar de página, con los cambios agrupados cada 500 ms.
- El capítulo se marca como leído al llegar a su última página.
- El historial se actualiza al cambiar de capítulo y al salir del lector.

### Errores del lector
- Si falla una página, se muestra el error con "Reintentar" en esa página; las demás siguen funcionando.
- Si falla la lista de páginas del capítulo, se muestra una pantalla de error con "Reintentar" y "Abrir en
  navegador".

## 5. Errores e integración con la plataforma

### Arranque (`main()`)
1. `SingleInstance`. Si el resultado es `Forwarded`, la instancia principal enfoca su ventana y esta termina con
   código 0. Si es `Unreachable`, se muestra el diálogo "Ya hay una instancia que no responde" y la app termina.
2. `Platform.start(dirs)`. Si falla (BD corrupta, migración, disco), se muestra un diálogo Swing con el mensaje
   y la ruta del log, y la app termina con código 1. Nunca queda un proceso sin ventana.
3. `application { Window }`.

### Ventana
- Al cerrar se guardan tamaño, posición y estado maximizado en las preferencias de la app.
- Al abrir se validan contra las pantallas actuales (`GraphicsEnvironment`). Si la ventana quedaría fuera de
  todas, se centra en la pantalla principal con 1280×800.
- Tamaño mínimo: 800×600.

### Tema
Sistema, claro u oscuro. "Sistema" usa `isSystemInDarkTheme()` de CMP. Si esa función no detecta el modo
oscuro de Windows, se lee `AppsUseLightTheme` en
`HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize` con `reg query`. El plan comprueba qué
camino hace falta.

### Hilos
- El hilo de UI es el EDT de Swing. Compose y `Dispatchers.Main` (coroutines-swing) comparten ese hilo.
- El `Looper` principal de Android sigue en su propio hilo, `android-main`, sin cambios.

### Mensajes de las extensiones
Los `Toast` de AndroidCompat se publican en un `SharedFlow` que el `SnackbarHost` de la ventana muestra.

### Fallos no capturados
- Si falla una coroutine de un ViewModel, se registra en el log y se muestra el snackbar "Error: <mensaje>". La
  pantalla sigue funcionando.
- Si falla la composición, se muestra una pantalla de error (como `CrashScreen` de Mihon) con "Copiar log" y
  "Reiniciar".
- `Thread.setDefaultUncaughtExceptionHandler` registra en el log cualquier otro fallo.

### Logs
Se añade un `logback.xml` con un fichero rotativo en `%LOCALAPPDATA%\<App>\logs\app.log`: 7 días y 10 MB por
fichero como máximo. Ajustes › Avanzado › "Abrir carpeta de logs" usa `Desktop.open`.

### Red
- Las portadas y las páginas usan el `client` y las `headers` de la fuente (`HttpSource.getImage`), como en
  Mihon.
- Una respuesta 403 o 503 de Cloudflare muestra "Este sitio requiere navegador (disponible pronto)" con un
  botón "Abrir en navegador". El subproyecto 2b sustituye este comportamiento.

### Extensiones
- Las extensiones `NotLoaded` aparecen en Instaladas con su motivo (firma, librería no soportada,
  `NoClassDefFoundError`) y un botón "Desinstalar".
- Si cambia la firma de una extensión, la actualización pide confirmación de nuevo. Nunca se actualiza en
  silencio.

### Primera ejecución
No se preinstala ningún repositorio: es un producto público y el repositorio lo elige el usuario. Explorar
muestra un estado vacío con el botón "Añadir repositorio", que acepta la URL de un `index.min.json` o de un
`index.pb`.

## 6. Testing y aceptación

### Tests unitarios (JUnit, sin UI)
Usan `FakeSource`, un `HttpSource` en memoria que sirve imágenes desde `MockWebServer`.

| Qué se prueba | Casos |
|---|---|
| `BrowseSourceViewModel` | Paginación de populares, recientes y búsqueda; filtros; error de página con reintento |
| `MangaViewModel` | Al abrir: guarda el manga y sincroniza los capítulos; marcar leído y no leído; marcadores; selección múltiple |
| `ReaderViewModel` | Guarda la última página (agrupada); marca el capítulo como leído en la última página; actualiza el historial; pasa al capítulo siguiente y al anterior; reintenta una página fallida |
| Emparejamiento de doble página (función pura) | Número de páginas par e impar; página ancha sola; "desplazar 1"; derecha a izquierda |
| División en tiles (función pura) | Imagen bajo el límite (1 tile); imagen de 30.000 px de alto (tiles de 4096 px o menos que suman el alto original) |
| Mapeo de teclas (tecla + modo → acción) | Flechas invertidas en derecha a izquierda; Espacio y AvPág; webtoon frente a paginado |
| Estado de la ventana | Posición fuera de pantalla → centrada; pantalla desconectada |
| Filtro de contenido para adultos | Oculta disponibles y fuentes con `isNsfw`; deja visibles las instaladas |
| Icono del APK | Elige la mayor densidad de `mipmap`; sin icono → `null`; zip sintético |

### Tests de UI (`compose.uiTest`, `runComposeUiTest`)
- Lector: → avanza de página, F11 alterna la pantalla completa, Esc cierra el lector.
- Preferencias de fuente: cada tipo de `androidx.preference` se dibuja y guarda su valor.
- Explorar sin repositorios: muestra el estado vacío con "Añadir repositorio".

### Sin regresiones
- La suite existente (173 tests), el harness (≥ 80 %), el chequeo de licencias y spotless siguen en verde.
- CI en `windows-latest`; los tests de UI corren con Skiko sin pantalla.

### Prueba manual (última tarea del plan)
1. `gradlew :desktop-app:run`; añadir el repositorio de keiyoushi; instalar MangaDex y confiar en ella.
2. Buscar, abrir un manga y leer un capítulo en cada modo: izquierda a derecha, derecha a izquierda, vertical,
   webtoon y doble página.
3. Zoom con doble clic y con Ctrl+rueda; F11.
4. Cerrar y reabrir la app: se conservan el progreso, el historial y la ventana.
5. Abrir un webtoon con imágenes muy largas: sin cortes ni errores de textura.
6. Lanzar una segunda instancia: se enfoca la primera.

### Criterios de aceptación
- Todos los pasos de la prueba manual se cumplen.
- La suite, el harness y CI están en verde.
- Ninguna pantalla de 2a usa `android.*` salvo a través de `compat-shims` o de AndroidCompat.

## 7. Riesgos
| Riesgo | Mitigación |
|---|---|
| La versión de Voyager de Mihon no publica un artefacto JVM | Comprobarlo en la primera tarea; si falta, usar Voyager 1.1.0-beta upstream |
| CMP no tiene versión compatible con Kotlin 2.4.21 | Comprobarlo en la primera tarea; si no la hay, fijar la versión de CMP más reciente compatible y documentarlo |
| `isSystemInDarkTheme()` no detecta el modo oscuro de Windows | Leer el registro con `reg query` |
| Consumo de memoria con capítulos de webtoon largos | Ventana de páginas decodificadas (de −2 a +4) y tiles; se mide en la prueba manual 5 |
| Los ViewModels de Mihon dependen de APIs de Android no previstas | Sustituirlas en el paquete `platform`; si una pantalla exige más de lo previsto, se recorta y se anota como ruling en el ledger |
