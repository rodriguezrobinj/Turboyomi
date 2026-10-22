# Diseño: subproyecto 3b — migración entre fuentes, Próximos e intervalo por manga

- **Fecha:** 2026-10-21
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md`
- **Specs hermanas:** `2026-10-21-subproyecto-3a-biblioteca-design.md` (biblioteca y novedades, de donde sale la
  división 3a/3b), `2026-10-19-subproyecto-2a-ui-lector-design.md` (pauta de portado de pantallas)
- **Base:** `master` en `1ed7538d0` (subproyecto 3a integrado; 342 tests)

## 1. Objetivo y alcance

### Objetivo
Cerrar el subproyecto 3 con lo que 3a dejó en `null` y marcado "3b":
1. Migrar uno o varios mangas de la biblioteca de una fuente a otra, conservando el progreso.
2. Una pantalla Próximos con un calendario de las actualizaciones esperadas de la biblioteca.
3. Editar el intervalo de actualización de cada manga desde su ficha.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| Enfoque | Portar el flujo actual de Mihon tal cual (`mihon.feature.migration`, `mihon.feature.upcoming`). Se descartan una migración mínima de un solo manga (deja la selección múltiple sin migrar) y un flujo propio |
| Intervalo por manga | Entra en 3b: Próximos depende de `nextUpdate`, y `SetIntervalDialog` ya está portado |
| Portado | Como en 2a y 3a: los archivos de Mihon se copian con su paquete y se adaptan con los shims existentes |
| Notas | Se migran (es copiar una columna) aunque editarlas llegue en el subproyecto 5 |
| Trackers | El código de migración de tracks de Mihon se copia sin cambios. Con el stub de `TrackerManager` no hace nada hasta el subproyecto 5 |
| "Borrar descargas" | Se oculta en la config de migración: `DownloadManager` es un stub hasta el subproyecto 4 |

### Desviaciones respecto a Mihon
- `MigrateMangaUseCase` traga en silencio cualquier error. Aquí, además, lo escribe en el log con `logcat`.
- La opción "Borrar descargas" (`MigrationFlag.REMOVE_DOWNLOAD`) no se muestra.

### Fuera de alcance (3b)
| Qué | Dónde |
|---|---|
| Borrar las descargas del origen al migrar | Subproyecto 4 |
| Migrar trackers de verdad (`EnhancedTracker`) | Subproyecto 5 |
| Editar notas | Subproyecto 5 |

## 2. Arquitectura

### Piezas (copiadas de Mihon)
| Paquete | Archivos |
|---|---|
| `mihon.domain.migration.usecases` | `MigrateMangaUseCase` |
| `mihon.feature.migration.config` | `MigrationConfigScreen`, `MigrationConfigScreenSheet` |
| `mihon.feature.migration.list` | `MigrationListScreen`, `MigrationListScreenContent`, `MigrationListViewModel`, `models/MigratingManga`, `components/` (diálogos de salida, manga y progreso), `search/` (`BaseSmartSearchEngine`, `SmartSourceSearchEngine`) |
| `mihon.feature.migration.dialog` | `MigrateMangaDialog` |
| `eu.kanade.tachiyomi.ui.browse.migration` | `sources/MigrateSourceTab` y su ViewModel, `manga/MigrateMangaScreen` y su ViewModel, `search/MigrateSearchScreen`, `MigrateSearchViewModel`, `MigrateSourceSearchScreen` |
| `eu.kanade.presentation.browse` | `MigrateSourceScreen`, `MigrateSearchScreen` |
| `eu.kanade.domain.source.interactor` | `GetSourcesWithFavoriteCount` |
| `mihon.feature.upcoming` | `UpcomingScreen`, `UpcomingScreenContent`, `UpcomingViewModel`, `UpcomingUIModel`, `components/UpcomingItem`, `components/calendar/` (`Calendar`, `CalendarDay`, `CalendarHeader`, `CalendarIndicator`) |

Ya existen y se reutilizan: `MigrationFlag`, las preferencias de migración de `SourcePreferences`,
`SetMigrateSorting`, `GetUpcomingManga`, `UpcomingPreferences`, `SetIntervalDialog`, `UpdateManga`,
`UpdateMangaFromRemote`, `CoverCache`, `TabbedDialog`, `AdaptiveSheet` y `TwoPanelBox`.

### Adaptaciones
- `androidx.activity.compose.BackHandler` y `toast`: los shims de 2a.
- `R.drawable.cover_error` con `rememberResourceBitmapPainter`: el recurso de portada fallida que ya usa la app.
- `EnhancedTracker`: interfaz vacía en el stub de trackers (`desktop-app/src/stubs`), con lo que `MigrateMangaUseCase`
  compila sin cambios.
- Dependencias nuevas en `desktop-app`, ya declaradas en `libs.versions.toml` y solo JVM: `stringSimilarity`
  (Levenshtein normalizado de la búsqueda inteligente) y `composeGrid` (rejilla del calendario).

### Integración con lo existente
Se sustituyen los `null` y comentarios marcados "3b":
| Dónde | Qué hace |
|---|---|
| `MangaScreen` (ficha) › Migrar | `MigrationConfigScreen(manga.id)` |
| `MangaScreen` › intervalo | `SetIntervalDialog` (activa `onEditFetchIntervalClicked`) |
| `MangaScreen` › diálogo de duplicado | `MigrateMangaDialog` |
| `LibraryTab` › selección múltiple › Migrar | `MigrationConfigScreen(ids seleccionados)` |
| `BrowseSourceScreen` › diálogo de duplicado | `MigrateMangaDialog` |
| `HistoryTab` › diálogo de duplicado | `MigrateMangaDialog` |
| `BrowseTab` | Vuelve la pestaña "Migrar" (`migrateSourceTab()`) |
| `UpdatesTab` | Icono de calendario y "Ver próximos" en el menú abren `UpcomingScreen` |

## 3. Flujos

### A. Configuración de la migración (`MigrationConfigScreen`)
- Lista de fuentes instaladas, ordenable con arrastre. Se marcan las fuentes destino. Hay atajos para
  seleccionar todas, ninguna, las habilitadas o las fijadas, como en Mihon.
- Hoja de opciones:
  - Qué copiar: capítulos, categorías, portada personalizada, notas.
  - Cómo buscar: búsqueda profunda y "priorizar por capítulos".
  - Qué ocultar: los no encontrados y los que no tienen capítulos nuevos.
- Todo se guarda en `SourcePreferences`: `migrationSources`, `migrationFlags`, `migrationDeepSearchMode`,
  `migrationPrioritizeByChapters`, `migrationHideUnmatched` y `migrationHideWithoutUpdates`.
- Con un solo manga, "Búsqueda manual" va directo a `MigrateSearchScreen`.

### B. Lista de migración (`MigrationListScreen`)
- Los mangas se buscan de uno en uno. Para cada manga:
  - Sin "priorizar por capítulos": se prueba cada fuente destino en orden y se queda la primera coincidencia.
  - Con "priorizar por capítulos": busca en todas las fuentes, como máximo 5 a la vez, y se queda la de capítulo más alto.
- La coincidencia sale de `SmartSourceSearchEngine` (similitud de título).
- Estados de cada fila: buscando, encontrado (con número de capítulos) o no encontrado.
- Acciones por fila: buscar manualmente, migrar ahora, copiar ahora, quitar.
- Abajo: "Migrar" o "Copiar" todo. Un diálogo avisa de cuántos se saltan por no encontrados.
- Diálogo de progreso. Al acabar vuelve atrás. Salir a medias pide confirmación.

### C. Migrar o copiar (`MigrateMangaUseCase`)
1. Refresca el destino desde su fuente, con capítulos.
2. Con el flag de capítulos:
   - Marca como leídos los capítulos del destino cuyo número es ≤ el máximo leído en el origen.
   - Copia `bookmark` y `dateFetch` de los capítulos con el mismo número.
3. Con el flag de categorías, el destino recibe las categorías del origen.
4. Tracks: se copian al destino. Con el stub no hay ninguno.
5. Con el flag de portada y si el origen tiene portada personalizada, la copia a la caché del destino.
6. El destino pasa a favorito y recibe `chapterFlags` y `viewerFlags`. Con el flag de notas también recibe las notas.
   - Migrar: conserva el `favoriteAt` del origen y el origen sale de la biblioteca.
   - Copiar: los dos quedan en la biblioteca.

`MigrateMangaDialog` (duplicados de Explorar fuente, ficha e Historial) pregunta "Migrar" o "Copiar" y ejecuta el
mismo use case.

### D. Pestaña Migrar de Explorar
- `MigrateSourceTab` lista las fuentes con mangas en la biblioteca y cuántos tiene cada una, con el orden de
  `SetMigrateSorting`.
- Al elegir una fuente se abre `MigrateMangaScreen` con sus mangas. Se pueden seleccionar varios, y "Migrar"
  abre la config.

### E. Próximos (`UpcomingScreen`)
- Calendario mensual con un indicador por día que tiene mangas, más una lista agrupada por la fecha de `nextUpdate`
  (`GetUpcomingManga`).
- Clic en un día salta a su grupo de la lista. Clic en un manga abre su ficha.
- En ventana ancha, calendario y lista van lado a lado (`TwoPanelBox`), como en la tablet de Mihon.

### F. Intervalo por manga
- El botón de intervalo de la ficha abre `SetIntervalDialog`: automático o un número fijo de días.
- Guarda `fetchInterval` y recalcula `nextUpdate` con el `UpdateManga` ya portado.

## 4. Errores
| Caso | Comportamiento |
|---|---|
| Una fuente destino falla en la búsqueda (red, Cloudflare) | Esa fuente cuenta como sin resultado y se pasa a la siguiente. Si ninguna encuentra nada, la fila queda "No encontrado" |
| Falla el refresco del destino al migrar | Se para la migración de ese manga. Lo ya escrito se queda (como en Mihon) y el error va al log con `logcat` (desviación) |
| La fuente origen está desinstalada | Se migra igual: el origen solo se lee de la BD |
| El destino ya está en la biblioteca | Se actualiza el existente (Mihon) |
| Un manga sin `nextUpdate` | No sale en Próximos |
| Biblioteca vacía o sin próximos | `EmptyScreen` con el texto de Mihon |

## 5. Testing y aceptación

### Tests unitarios (en `./gradlew test`, BD real, `TestAppGraph`, `FakeSource` y `UpdatableSource`)
- `MigrateMangaUseCaseTest`:
  - Los capítulos ≤ el máximo leído quedan leídos. Se copian marcadores y `dateFetch`.
  - Categorías y notas se copian solo con su flag.
  - La portada personalizada se copia a la caché del destino.
  - Migrar quita el origen de favoritos. Copiar mantiene ambos.
  - `chapterFlags` y `viewerFlags` pasan al destino.
  - Si falla el refresco del destino, el origen queda intacto.
- `MigrationListViewModelTest`:
  - Si la primera fuente falla, encuentra el manga en la segunda.
  - Queda "No encontrado" si ninguna fuente tiene el manga.
  - "Migrar todo" salta los no encontrados.
- `UpcomingViewModel`: agrupa por día y marca los días del mes con indicador. Un manga sin `nextUpdate` queda fuera.
- Intervalo: fijar 3 días guarda `fetchInterval` y mueve `nextUpdate`.

### UI (`runComposeUiTest`)
- Explorar muestra la pestaña Migrar con la fuente del manga guardado.
- Migrar desde la selección múltiple de la biblioteca abre la config.
- El calendario de Novedades abre Próximos.
- `SpanishStringsTest` cubre las cadenas nuevas.

### Prueba manual (última tarea del plan)
1. Migrar un manga real de una fuente keiyoushi a otra. Se conservan los capítulos leídos, la categoría y la portada.
2. Migración en lote de 3 o más mangas desde la biblioteca, con uno no encontrado y una búsqueda manual.
3. Copiar en vez de migrar: los dos quedan en la biblioteca.
4. Próximos muestra mangas en el calendario. Clic en un día y en un manga.
5. Cambiar el intervalo de un manga y verlo moverse en Próximos.

### Criterios de aceptación
- Todos los tests verdes, `spotlessCheck` limpio, y pasan los checks de licencias y de "sin Android".
- La prueba manual pasa.

## 6. Riesgos
- La búsqueda inteligente lanza muchas peticiones en una migración en lote. Se mantiene el límite de Mihon
  (5 fuentes a la vez con "priorizar por capítulos"; si no, una tras otra). Con fuentes con Cloudflare interactivo
  habrá más "No encontrado"; la búsqueda manual lo cubre.
- `nextUpdate` solo se rellena al sincronizar capítulos. Una biblioteca recién importada ve Próximos vacío hasta la
  primera actualización. Es el comportamiento de Mihon.
- Unas 4000 líneas copiadas. El riesgo está en las APIs de Android o de CMP que falten (como `SliderState` en 2a). Se
  resuelven en el plan con los shims existentes o con desviaciones anotadas.
