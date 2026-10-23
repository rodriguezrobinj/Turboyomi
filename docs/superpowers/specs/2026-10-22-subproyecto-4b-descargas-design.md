# Diseño: subproyecto 4b — descargas

- **Fecha:** 2026-10-22
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md` (requisito 3 de la sección 1, secciones 5.1 y 5.2)
- **Specs hermanas:** `2026-10-22-subproyecto-4a-lectura-local-design.md` (capa de archivos y almacenamiento),
  `2026-10-21-subproyecto-3a-biblioteca-design.md` (planificador, bandeja y notificaciones)
- **Base:** `master` en `0c00eaefb` (subproyecto 4a integrado; 394 tests)

## 1. Objetivo y alcance

### Objetivo
Descargar capítulos para leerlos sin conexión, con el motor de Mihon y su layout de carpetas:
1. Una cola de descargas con concurrencia por fuente, que se guarda entre sesiones.
2. Capítulos como carpeta o CBZ con `ComicInfo.xml`, en `downloads/` de la ubicación de almacenamiento (4a).
3. Todas las entradas de Mihon: ficha, Biblioteca, Novedades, lector, actualización de biblioteca y migración.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| Enfoque | Portar `data/download/` de Mihon con su paquete y sustituir solo lo que es de Android. Se descarta un descargador propio (rompe la compatibilidad con las carpetas de Android y duplica Mihon) |
| Dónde está la cola | Sexta entrada "Descargas" en el riel, con un badge con el nº en cola. Volver a pulsar Novedades también abre la cola (Mihon) |
| "Solo descargados" | Interruptor en el menú de la Biblioteca (en Mihon está en la pestaña "Más", que no existe en escritorio) |
| "Solo con Wi-Fi" | Se oculta: en escritorio no hay redes de datos medidos que distinguir |
| "Dividir imágenes altas" | Se oculta y no se divide: el lector de escritorio ya parte las páginas altas al decodificarlas (2a) y los archivos descargados quedan intactos |
| Cola al arrancar | Se restaura en pausa, como en Mihon |

### Cambios respecto a la spec padre
- §5.2 pide truncar cada segmento de ruta a 120 caracteres.
  - Mihon trunca a 240 bytes (`DiskUtil.MAX_FILE_NAME_BYTES`). Con otro límite, las carpetas `downloads/` de Android con nombres largos no se encontrarían.
  - Se mantiene el límite de Mihon. Las rutas de más de 260 caracteres las cubre la JVM, que añade el prefijo `\\?\` en las llamadas a Windows. Un test lo comprueba.
- §5.2 pide evitar `CON`, `PRN`, `AUX`, `NUL`, `COM1`–`COM9` y `LPT1`–`LPT9`: Mihon no lo hace y se añade, con un `_` al final (`CON` → `CON_`). Una carpeta de Android con ese nombre no puede existir en Windows, así que no rompe la compatibilidad.
- El resto del saneado de §5.2 (caracteres prohibidos, punto o espacio final) ya lo hace `DiskUtil.buildValidFilename` de Mihon.

### Fuera de alcance (4b)
| Qué | Dónde |
|---|---|
| Notificaciones con botones (pausar o reanudar desde la notificación) | No hay: el `SystemTray` de AWT no los tiene (limitación asumida en 3a) |
| Seguimiento con trackers al borrar o leer | Subproyecto 5 |
| Reintento automático de capítulos con error | No se pide; se reintentan a mano desde la cola |

## 2. Arquitectura

### Piezas de Mihon (copiadas con su paquete)
`eu/kanade/tachiyomi/data/download/`:
- `Downloader`, `DownloadManager`, `DownloadCache`, `DownloadProvider`, `DownloadStore`, `DownloadPendingDeleter`,
  `model/Download`;
- `eu/kanade/domain/download/interactor/DeleteDownload`;
- `ui/reader/loader/DownloadPageLoader`;
- `ui/download/DownloadQueueViewModel`;
- `presentation/more/settings/screen/SettingsDownloadScreen`.

### Sustituciones de escritorio
| Pieza de Mihon | En escritorio |
|---|---|
| `DownloadWorker` (WorkManager, servicio en primer plano) | `DownloadJob`: corrutina en el scope de la app que ejecuta el `Downloader` y expone `isRunning: StateFlow<Boolean>`. Sigue con la ventana en la bandeja |
| `DownloadNotifier` (notificaciones de Android) | Notificador de escritorio sobre `TrayNotifier` (3a): "Descargas terminadas", la pausa por error y los avisos. El progreso se ve en la cola |
| `ZipWriter` (libarchive) | Copia en `core/archive/src/desktop` con `java.util.zip` en modo STORED (como Mihon, sin compresión) |
| `DiskUtil.getAvailableStorageSpace` (`StatFs`) | `File.usableSpace` en la copia de escritorio de `DiskUtil` (4a) |
| `android.util.AtomicFile` en `DownloadCache` | Escribir en un temporal y moverlo con `Files.move(…, ATOMIC_MOVE)` |
| `ImageUtil.splitTallImage` | No se usa: la opción está oculta |
| Pantalla de la cola (`RecyclerView` dentro de Compose) | `LazyColumn` agrupada por fuente, ordenable con `sh.calvin.reorderable` (ya en desktop-app) |

`DownloadStore` y `DownloadPendingDeleter` usan `context.getSharedPreferences`, que AndroidCompat implementa sobre archivos.
Se comprueba en la primera tarea del plan.

### Saneado de nombres
`DiskUtil.buildValidFilename` (copia de escritorio) añade a lo de Mihon:
- los nombres reservados de Windows, sin distinguir mayúsculas y con o sin extensión (`con.txt`, `COM1`), que pasan a llevar `_` (`CON_`, `com1_.txt`).

El mismo saneado decide la ruta al escribir y al leer, así que un capítulo con un nombre así se sigue encontrando.

### Integración
| Dónde | Qué hace |
|---|---|
| Riel | Entrada "Descargas" con el icono de descarga y un badge con los capítulos en cola; abre la cola |
| `UpdatesTab` | `onReselect` abre la cola (Mihon); se activan descargar, borrar con confirmación (`UpdatesDeleteConfirmationDialog`), el filtro "Descargados" y el indicador |
| `MangaScreen` | Botón por capítulo (descargar, cancelar o borrar), menú de descarga (siguiente, siguientes 5/10/25, no leídos, todos), borrar los seleccionados, filtro e indicador "Descargado" |
| `LibraryTab` | Descargar la selección, filtro "Descargados", badge de descargados y el interruptor "Solo descargados" en el menú |
| `ChapterLoader` | La rama de descargados con `DownloadPageLoader`, antes que las demás (Mihon) |
| `ReaderViewModel` | Borrar tras leer (`removeAfterReadSlots`, aplazado con `DownloadPendingDeleter`) y descargar por adelantado (`autoDownloadWhileReading`) |
| `LibraryUpdateJob` | Descarga automática de capítulos nuevos (`downloadNewChapters` y sus categorías) |
| `MigrationConfigScreenSheet` | Vuelve el interruptor "Borrar descargas" |
| `Manga.getComicInfo` | Vuelve, para el `ComicInfo.xml` de los capítulos descargados |
| Ajustes | Entrada "Descargas" (`SettingsDownloadScreen`) tras "Lector" |
| Stubs | Se borran los de `desktop-app/src/stubs/.../download/` y `DeleteDownload` |

## 3. Flujos

### A. Descargar
1. Desde la ficha, la selección de la Biblioteca, Novedades, el lector (por adelantado) o la actualización de
   biblioteca (capítulos nuevos), los capítulos entran en la cola. `DownloadJob` arranca si no está en marcha.
2. Por cada capítulo:
   1. Comprueba el espacio libre.
   2. Pide la lista de páginas a la fuente.
   3. Descarga las imágenes a `<capítulo>_tmp`, con 5 páginas a la vez (ajustable) y 3 reintentos por página.
   4. Si "Guardar como CBZ" está activo (por defecto sí), comprime el capítulo con su `ComicInfo.xml`.
   5. Lo renombra a su nombre final.
3. Varias fuentes en paralelo, como máximo 5 (ajustable).
4. El progreso se ve en la cola y en el indicador del capítulo.
5. Al vaciarse la cola sale "Descargas terminadas": en la bandeja si la ventana está oculta, y como mensaje dentro de la app si no.

### B. Cola (riel › Descargas)
- Lista agrupada por fuente, con el progreso de cada capítulo.
- Acciones:
  - pausar o reanudar todo;
  - reordenar arrastrando, o mover un capítulo arriba o abajo;
  - ordenar por fecha de subida o por número de capítulo;
  - cancelar un capítulo, los de un manga o todos.
- El badge del riel muestra cuántos quedan.
- Al cerrar la app la cola se guarda; al volver aparece en pausa.

### C. Leer
- **Capítulo descargado:** el lector lo abre desde el disco (`DownloadPageLoader`), también sin red.
- **Borrar tras leer:**
  - al terminar un capítulo, el que está N capítulos por detrás (según el ajuste) se apunta para borrar;
  - se borra al salir del lector (`DownloadPendingDeleter`);
  - se respetan "No borrar los marcados" y las categorías excluidas.
- **Descarga por adelantado:** al avanzar, se ponen en cola los N siguientes, solo si el capítulo actual está descargado (Mihon).

### D. "Solo descargados"
Interruptor en el menú de la Biblioteca. Con él, Biblioteca, Novedades, Historial y la lista de capítulos muestran solo
lo descargado o local, como el modo de Mihon.

### E. Borrar
- Desde la ficha (capítulos seleccionados) o desde Novedades, con confirmación.
- Al quitar un manga de la Biblioteca, el diálogo de Mihon pregunta si borrar también sus descargas.
- En la migración, con "Borrar descargas".

## 4. Errores
| Caso | Comportamiento |
|---|---|
| Una página falla tras 3 intentos (red, 404) | El capítulo queda en "Error" en la cola con "Reintentar"; los demás siguen |
| La fuente pide Cloudflare interactivo | El capítulo da error con el mensaje de `ExceptionFormatter` (2b). Nunca se abre el WebView solo |
| Espacio insuficiente | El capítulo da error "Espacio insuficiente" (texto de Mihon) y la cola sigue con los demás |
| Fuente desinstalada | El capítulo da error "Fuente no encontrada" |
| La app se cierra a mitad de un capítulo | `<capítulo>_tmp` queda en disco; al reanudar se aprovechan las páginas ya bajadas (Mihon) |
| Nombre con caracteres prohibidos o reservado (`Tomo: 1?`, `CON`) | Se sanea (`Tomo_ 1_`, `CON_`) y se encuentra igual al leer |
| Ruta de más de 260 caracteres | Se escribe y se lee: la JVM añade `\\?\` |
| Carpeta `downloads/` copiada de Android | Se reconoce sin conversión |
| Capítulo borrado del disco por fuera de la app | La caché de descargas se renueva al arrancar y periódicamente (Mihon), y el capítulo deja de verse descargado |

## 5. Testing y aceptación

### Tests unitarios (en `./gradlew test`, BD real, `TestAppGraph`, fuente HTTP falsa con `MockWebServer`)
- `DownloaderTest`:
  - un capítulo se descarga como carpeta y como CBZ con `ComicInfo.xml`, con las páginas en orden;
  - la concurrencia por fuente se respeta;
  - una página que falla 3 veces deja el capítulo en "Error" y la cola sigue;
  - con espacio insuficiente simulado, sale el error y la cola sigue;
  - pausar y reanudar aprovecha las páginas de `_tmp`;
  - la cola se guarda y se restaura en pausa.
- `DownloadProviderTest`:
  - rutas con `José`, nombres reservados (`CON` → `CON_`) y caracteres prohibidos;
  - una ruta de más de 260 caracteres se escribe y se lee;
  - una carpeta con el layout de Android se reconoce.
- `DownloadCacheTest`: reconoce los capítulos en disco y deja de verlos al borrarlos por fuera.
- `core/archive`, `ZipWriterTest`: el CBZ escrito lo lee el `ArchiveReader` de 4a.
- Lector:
  - un capítulo descargado se abre sin red;
  - borrar tras leer deja sin descarga el capítulo N-atrás al salir del lector;
  - la descarga por adelantado pone en cola los siguientes.
- `LibraryUpdateJob`: con "Descargar capítulos nuevos", los nuevos entran en la cola, respetando las categorías.
- "Solo descargados": la Biblioteca solo muestra mangas con descargas o locales.

### UI (`runComposeUiTest`)
- El riel muestra "Descargas" con el badge del nº en cola.
- Descargar desde la ficha, y pausar y reanudar en la cola.
- Ajustes › Descargas existe.
- `SpanishStringsTest` cubre las cadenas nuevas.

### Prueba manual (última tarea del plan)
1. Descargar 3 capítulos de una fuente real como CBZ, y otro como carpeta.
2. Leerlos con la red desconectada.
3. Cerrar la app con la cola a medias, volver y reanudar.
4. Copiar una carpeta `downloads/` de Android y ver sus capítulos como descargados.
5. Con "Descargar capítulos nuevos" activo, actualizar la biblioteca: los nuevos se descargan.
6. Con "Borrar tras leer" activo, leer un capítulo descargado: se borra el que toca.

### Criterios de aceptación
- Todos los tests verdes, `spotlessCheck` limpio y los checks de licencias y de "sin Android" pasan.
- La prueba manual pasa.

## 6. Riesgos
- `Downloader.kt` (874 líneas) y `DownloadCache.kt` (538) son las piezas más grandes. Dependen de
  `context.getSharedPreferences` y del sistema de archivos de AndroidCompat. Se comprueban en la primera tarea del plan.
- Una cola grande contra fuentes con Cloudflare interactivo acumula errores. Se reintentan a mano desde la cola.
- `LibraryUpdateJob` (3a) y `ReaderViewModel` (2a) son reescrituras de escritorio: las piezas de descargas se añaden
  a mano siguiendo a Mihon, no se copian sus archivos.
