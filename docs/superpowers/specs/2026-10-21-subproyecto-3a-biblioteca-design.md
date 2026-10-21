# Diseño: subproyecto 3a — biblioteca, categorías, novedades y actualización periódica

- **Fecha:** 2026-10-21
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md` (requisito 2 de la sección 1 y sección 5.1)
- **Specs hermanas:** `2026-10-19-subproyecto-2a-ui-lector-design.md` (pauta de portado de pantallas),
  `2026-10-21-subproyecto-2b-navegador-integrado-design.md` (errores de Cloudflare)
- **Base:** `master` en `f271473e4` (subproyecto 2 integrado; 310 tests y 12 cefTest; harness al 91,6 % con CEF)

## 1. Objetivo y alcance

### Objetivo
Que la app tenga lo que Mihon llama biblioteca:
1. Una pestaña Biblioteca con los mangas guardados, organizados en categorías, con filtros, orden y búsqueda.
2. Una pantalla para gestionar las categorías.
3. Una pestaña Novedades con los capítulos nuevos de la biblioteca.
4. Una actualización periódica de la biblioteca que busca capítulos nuevos y avisa con una notificación,
   también con la ventana cerrada a la bandeja.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| División del subproyecto 3 | 3a (esta spec): biblioteca, categorías, novedades, actualización periódica, bandeja y notificaciones. 3b: migración entre fuentes y Próximos. Cada uno con su spec, plan y merge |
| Segundo plano | Icono en la bandeja, "Cerrar a la bandeja" y notificaciones. "Iniciar con Windows" pasa al empaquetado: necesita la ruta estable del `.exe` instalado |
| Planificador | Propio, con corrutinas, en desktop-core. Cada tarea guarda `lastRun` en las preferencias y se evalúa al arrancar y cada minuto (spec padre §5.1). Se descartan `ScheduledExecutorService` (pierde la cuenta tras suspender o reiniciar) y un shim de WorkManager (demasiado código sin uso) |
| Portado de la UI | Como en 2a: los archivos de Mihon se copian con su paquete y se adaptan con los shims existentes |

### Cambios respecto a la spec padre
- §5.1 lista cinco tareas para el planificador. 3a solo implementa la actualización de biblioteca (y la de
  metadatos, manual). Las demás llegan con su subproyecto y se registran en el mismo planificador.
- "Iniciar con Windows" y `--minimized` pasan al empaquetado.

### Fuera de alcance (3a)
| Qué | Dónde |
|---|---|
| Migración entre fuentes, pestaña "Migrar" de Explorar, Próximos (calendario) | 3b |
| Descargas: botones, filtro "Descargados", descarga automática de capítulos nuevos, borrar descargas | Subproyecto 4 |
| Filtro de seguimiento y trackers | Subproyecto 5 |
| Iniciar con Windows | Empaquetado |
| Comprobación periódica de actualizaciones de extensiones | Sin pedir; se añade al planificador cuando haga falta |
| Estadísticas | Sin asignar |
| Restricciones de dispositivo de Mihon (wifi, red sin medir, cargando) | No aplican en escritorio; el ajuste se oculta |
| Gestos de deslizar sobre capítulos | No existen en escritorio; el ajuste se oculta |

## 2. Arquitectura

### Piezas
| Pieza | Módulo y paquete | Responsabilidad |
|---|---|---|
| `JobScheduler`, `PeriodicJob` | desktop-core, `mihon.desktop.core.jobs` | Decide cuándo toca cada tarea periódica. Sin UI. Reloj inyectable |
| `LibraryUpdateJob` | desktop-app, `eu.kanade.tachiyomi.data.library` | Cuerpo de `LibraryUpdateWorker` de Mihon sin WorkManager. También la actualización de metadatos (`MetadataUpdateWorker`) |
| `TrayNotifier` | desktop-app, `mihon.desktop.app.tray` | Envía las notificaciones. Implementación real con `TrayState`; falsa en tests; `AppMessages` si no hay bandeja |
| Bandeja | desktop-app, `mihon.desktop.app.tray` | `Tray` de Compose con su menú, y el ocultar y mostrar de la ventana |
| Pantallas de Mihon | desktop-app, paquetes de Mihon | Biblioteca, Categorías, Novedades y Ajustes › Biblioteca |

`LibraryUpdateNotifier` de Mihon no se porta: sus notificaciones Android se sustituyen por `TrayNotifier`.

### Integración con lo existente
- `HomeScreen`: el riel pasa a Biblioteca, Novedades, Historial, Explorar y Ajustes (orden de Mihon).
  Biblioteca es la pestaña inicial. Novedades lleva la insignia `newUpdatesCount` si `newShowUpdatesCount`
  está activo.
- `Main.kt`:
  - Con "Cerrar a la bandeja" activo, la X oculta la ventana en vez de cerrar la app.
  - Un segundo arranque, o Abrir en la bandeja, muestra la ventana y la trae al frente.
  - Salir (X sin bandeja, o Salir en la bandeja) ejecuta lo de hoy: `ExitHooks`, guardar la ventana, cerrar la
    instancia. Además cancela la tarea en curso.
- `Platform.start`/grafo: el planificador arranca después del grafo y registra la actualización de biblioteca.
- "Añadir a la biblioteca" (2a hoy solo marca favorito): con categorías creadas abre `ChangeCategoryDialog`,
  como Mihon; si `defaultCategory` está fijada, usa esa sin preguntar.
- Domain y data: los interactors de categorías, biblioteca y novedades ya están en `domain`, y las vistas
  `libraryView` y `updatesView` en `data`. `UpdateMangaFromRemote` ya está portado.

## 3. Flujos

### A. Biblioteca
Archivos copiados: `LibraryTab`, `LibraryViewModel`, `LibrarySettingsViewModel`, `LibraryItem`,
`presentation/library/**`, `DeleteLibraryMangaDialog`, `LibrarySettingsDialog`, `LibraryBottomActionMenu`,
`mihon/feature/library/QueryNodeExtensions`.

- Pestañas por categoría; búsqueda con `QueryNode`; filtros, orden y vista (cuadrícula compacta, cómoda,
  solo portadas, lista); insignias; botón "Continuar leyendo", que abre el lector de 2a en el siguiente
  capítulo sin leer.
- Selección:
  - Clic derecho equivale a pulsación larga (`onSecondaryClick`) y selecciona.
  - Con selección activa: Ctrl+clic añade o quita un manga; Mayús+clic selecciona el rango
    (`selectionRange` de Mihon).
  - Ctrl+A selecciona toda la categoría; Esc y Atrás limpian la selección.
  - Acciones: cambiar categoría, marcar leído y no leído, quitar de la biblioteca.
- F5 actualiza la categoría actual. `PullRefresh` se mantiene.
- Ctrl+Tab y Ctrl+Mayús+Tab cambian de categoría.
- Columnas: `landscapeColumns` si la ventana es más ancha que alta, si no `portraitColumns`. 0 = automático.
- "Abrir aleatorio" se mantiene. "Migrar" se oculta hasta 3b.
- Quitar de la biblioteca: el diálogo de Mihon pierde la opción "borrar descargas" hasta el subproyecto 4.

### B. Categorías
Archivos copiados: `CategoryScreen`, `CategoryViewModel`, `presentation/category/**`, `ChangeCategoryDialog`.

- Crear, renombrar y borrar con diálogos; reordenar con flechas arriba y abajo.
- La categoría por defecto (id 0) no se edita. Las reglas de nombre son las de Mihon.
- Se abre desde el menú de la biblioteca ("Editar categorías") y desde Ajustes › Biblioteca.

### C. Novedades
Archivos copiados: `UpdatesTab`, `UpdatesViewModel`, `UpdatesSettingsViewModel`, `presentation/updates/**`.

- Capítulos de la biblioteca agrupados por día. Cabecera "Última actualización: hace X"
  (`lastUpdatedTimestamp`).
- Menú: Filtrar, Actualizar biblioteca, Seleccionar todo, Invertir selección. "Ver próximos" se oculta hasta 3b.
- Clic abre el lector de 2a; clic en la portada abre el manga. Selección como en la biblioteca.
  Acciones de la selección: marcar leído y no leído, marcador.
- F5 actualiza la biblioteca entera.
- Filtros (`UpdatesFilterDialog`): sin leer, empezados, con marcador, categorías, scanlators excluidos.
  "Descargados" se oculta.
- Abrir la pestaña pone `newUpdatesCount` a 0, como Mihon.
- Se ocultan: botón de descarga por capítulo, "borrar descargados" y `UpdatesDeleteConfirmationDialog`.

### D. Ajustes › Biblioteca
Se copia `SettingsLibraryScreen`:
- Categorías (editar, categoría por defecto).
- Actualización periódica: intervalo (desactivada, 12 h, 24 h, 48 h, 72 h, semanal), restricciones por manga
  (completados, con sin leer, no empezados, fuera del periodo de publicación), categorías incluidas y
  excluidas, actualizar metadatos.
- "Cerrar a la bandeja" (`desktop_close_to_tray`, desactivado por defecto), junto al intervalo. Se oculta si
  `SystemTray.isSupported()` es falso.
- Se ocultan las restricciones de dispositivo y los gestos de deslizar.

Ajustes › Avanzado gana "Actualizar metadatos de la biblioteca", como Mihon.

### E. Planificador
```kotlin
class PeriodicJob(
    val name: String,
    val interval: () -> Duration?,   // null = desactivada
    val lastRun: Preference<Long>,   // epoch ms
    val run: suspend () -> Unit,
)
```
- `JobScheduler.start(scope)` evalúa al arrancar y cada 60 s.
- Una tarea toca si `interval() != null` y `now - lastRun >= interval`.
- `lastRun` se guarda antes de correr (Mihon guarda `lastUpdatedTimestamp` al empezar). Una actualización
  manual también lo guarda y reinicia la cuenta.
- Una tarea en curso no se lanza otra vez.
- Vuelta de suspensión: si desde la revisión anterior pasaron más de 2 min, esa revisión no lanza nada y
  espera a la siguiente. Así la red tiene un minuto para volver tras despertar el equipo.
- La actualización de biblioteca usa `lastUpdatedTimestamp` como `lastRun` y `autoUpdateInterval` (horas) como
  intervalo.

### F. Actualización de biblioteca
- Mismo filtrado que `LibraryUpdateWorker.addMangaToQueue`: categoría pedida, o incluidas y excluidas;
  `ONLY_FETCH_ONCE` con capítulos; restricciones por manga; orden por título.
- Mismo bucle que `updateChapterList`: hasta 5 fuentes en paralelo (`Semaphore(5)`), y dentro de cada fuente
  un manga tras otro. Un manga que ya no es favorito se salta.
- Por manga: `UpdateMangaFromRemote(fetchDetails = autoUpdateMetadata, fetchChapters = true)`. Los capítulos
  nuevos suman a `newUpdatesCount`.
- API:
  - `start(categoryId: Long? = null): Boolean`: `false` si ya hay una en curso; la UI muestra
    "Ya se está actualizando".
  - `startMetadata(): Boolean`: actualización de metadatos; mismo hueco (no corren las dos a la vez).
  - `cancel()`.
  - `progress: StateFlow<UpdateProgress?>`: hechos, total y títulos en curso. `null` si no corre nada.
- Corre en el scope de la app sobre `Dispatchers.IO`.

### G. Progreso, bandeja y notificaciones
- Progreso: Biblioteca y Novedades muestran una barra fina bajo la barra superior con
  "Actualizando 12/80 · <título>" y un botón Cancelar. El tooltip de la bandeja muestra lo mismo.
- Bandeja: icono mientras `SystemTray.isSupported()`. Menú: Abrir, Actualizar biblioteca, Salir.
  Doble clic abre la ventana.
- La primera vez que la X oculta la ventana, notificación "Sigue en la bandeja; la biblioteca se actualizará".
- Notificación de capítulos nuevos: título "Capítulos nuevos"; texto "14 capítulos en 5 mangas" más los 3
  primeros títulos.
- Notificación de errores: "N mangas no se pudieron actualizar". El archivo de errores (formato de Mihon) se
  escribe en `logs/update_errors.txt`. Dentro de la app sale un mensaje con "Ver", que lo abre con
  `Desktop.open`.
- Clic en una notificación: AWT entrega todos los clics al mismo `onAction`, así que abre la ventana en
  Novedades.
- Aviso de cola grande (más de 60 mangas de una fuente, como Mihon): mensaje dentro de la app.
- Sin bandeja, las notificaciones salen como mensajes dentro de la app (`AppMessages`).

## 4. Errores

| Caso | Comportamiento |
|---|---|
| Fuente desinstalada (`SourceNotInstalledException`) | Entra en los errores con el texto de Mihon; la tarea sigue |
| Manga sin capítulos (`NoChaptersException`) | Igual |
| Cloudflare interactivo o navegador no disponible | Entra en los errores con el mensaje de `ExceptionFormatter` (2b). Nunca se abre el WebView solo |
| Manga quitado de la biblioteca durante la tarea | Se salta |
| Cancelar o Salir | Para en el siguiente manga. Lo guardado se queda. Sin notificación de error |
| Fallo de la tarea entera | Log y mensaje en la app. `lastRun` ya guardado: no hay reintento en bucle |
| Categoría borrada que estaba incluida o excluida | Se ignora |
| Sin bandeja del sistema | Sin "Cerrar a la bandeja"; notificaciones dentro de la app |

## 5. Testing y aceptación

### Tests unitarios (en `./gradlew test`)
- desktop-core, `JobSchedulerTest` (reloj falso, `runTest`): toca y no toca; intervalo `null`; `lastRun` se
  guarda antes de correr; espera extra tras suspensión; sin doble lanzamiento.
- desktop-app, `LibraryUpdateJobTest` (fuente falsa en memoria, BD real como en 2a): inserta los capítulos
  nuevos y suma `newUpdatesCount`; respeta categorías y restricciones; recoge errores y escribe el archivo;
  `start` con una en curso devuelve `false`; `cancel` para.
- ViewModels: la biblioteca filtra y ordena; Ctrl+A y Mayús+clic seleccionan bien; CRUD de categorías;
  Novedades agrupa por día.
- `TrayNotifier` falso: textos de las notificaciones.

### UI (`runComposeUiTest`)
- La app arranca en Biblioteca.
- Un manga añadido aparece en su categoría.
- Actualizar desde el menú muestra el progreso.

### Prueba manual (última tarea del plan)
1. Cerrar a la bandeja y volver a abrir (desde la bandeja y con un segundo arranque).
2. Notificación de capítulos nuevos tras una actualización.
3. Clic en la notificación abre Novedades.
4. Salir desde la bandeja cierra la app sin perder el progreso de lectura.
5. F5 en Biblioteca y en Novedades.

### Criterios de aceptación
- Todos los tests verdes, `spotlessCheck` limpio y los checks de licencias y de "sin Android" pasan.
- La prueba manual pasa.

## 6. Riesgos
- `Tray` de Compose sobre AWT `SystemTray`: notificaciones sin acciones ni imagen (limitación ya asumida en
  la spec padre). Mejora futura: toasts WinRT vía JNA.
- `LibraryViewModel` de Mihon pesa unas 850 líneas y depende de descargas y trackers; los stubs del 2a deben
  cubrirlo sin tocar su lógica.
- Una biblioteca grande contra fuentes con Cloudflare acumula errores. Se ve en el archivo de errores; no
  hay reintento automático por manga.
