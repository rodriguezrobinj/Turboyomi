# Diseño: subproyecto 5b-1 — seguimiento (sin OAuth), notas e incógnito

- **Fecha:** 2026-10-23
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md` (requisito 5 de la sección 1, sección 5.5)
- **Specs hermanas:**
  - `2026-10-23-subproyecto-5a-backup-datos-design.md`: tracks y notas en el backup; `PreferenceStore` con tipos.
  - `2026-10-21-subproyecto-3a-biblioteca-design.md`: `JobScheduler`, bandeja y avisos.
  - `2026-10-21-subproyecto-2b-navegador-integrado-design.md`: el `Dpapi` de las cookies.
- **Base:** `master` en `ffaee9758` (subproyecto 5a integrado; 450 tests)

## 1. Objetivo y alcance

### Objetivo
1. La infraestructura de seguimiento de Mihon: `TrackerManager`, vincular, sincronizar al leer y reintentar sin red.
2. Los 5 trackers que no necesitan OAuth:
   - Kitsu y MangaUpdates, con usuario y contraseña;
   - Komga, Kavita y Suwayomi, que son enhanced: se vinculan con su fuente instalada.
3. Las notas de manga de Mihon.
4. El modo incógnito global.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| División del 5b | 5b-1: la base, los 5 trackers sin OAuth, notas e incógnito. 5b-2: el protocolo `mangareader://`, el reenvío de la URL entre instancias y los 7 trackers OAuth (AniList, MyAnimeList, Shikimori, Bangumi, Comick, MangaBaka, Hikka) con client IDs propios |
| Enfoque | Portar `data/track/`, `domain/track/` y su UI de Mihon con su paquete, cambiando solo lo de Android |
| Trackers OAuth en 5b-1 | No se registran en `TrackerManager`: no aparecen en ningún sitio. Sus tracks restaurados de un backup se quedan en la base de datos y aparecerán en 5b-2 |
| IDs de los trackers | Los de Mihon (Kitsu 3, Komga 6, MangaUpdates 7, Kavita 8, Suwayomi 9), para que backups y tracks sean compatibles |
| Credenciales | Cifradas con DPAPI (spec padre §5.5), nunca en texto plano |
| Interruptor de incógnito | En el menú ⋮ de la Biblioteca, junto a "Solo descargados", y en el menú de la bandeja. Mihon lo tiene en la pestaña "Más", que aquí no existe |
| Notas | `MangaNotesScreen` de Mihon con su editor de texto rico (compose-rich-editor, KMP con versión desktop) |

### Cambios respecto a la spec padre
- §5.5 trata los 11 trackers juntos, con OAuth vía protocolo. Ahora son 12, porque Mihon añadió Comick, y se reparten: los 7 con OAuth quedan para 5b-2.
- Las notas y el incógnito no estaban en la spec padre. Venían de los subproyectos 2a y 3a, que los dejaron para el 5.

### Fuera de alcance (5b-1)
| Qué | Dónde |
|---|---|
| Protocolo `mangareader://`, reenvío de la URL entre instancias y los 7 trackers OAuth | 5b-2 |
| Registro de los client IDs en cada servicio | 5b-2, lo hace el usuario |

## 2. Arquitectura

### Piezas de Mihon (copiadas con su paquete)
- `eu/kanade/tachiyomi/data/track/`:
  - `Tracker`, `BaseTracker`, `EnhancedTracker`, `DeletableTracker`, `TrackerManager` y `model/`;
  - `kitsu/`, `mangaupdates/`, `komga/`, `kavita/` y `suwayomi/`.
- `eu/kanade/domain/track/`:
  - `interactor/`: `AddTracks`, `TrackChapter`, `RefreshTracks` y `SyncChapterProgressWithTrack`;
  - `model/`, `service/TrackPreferences` y `store/DelayedTrackingStore`.
- UI:
  - `presentation/track/*`, `ui/manga/track/TrackInfoDialog` y `TrackItem`;
  - `presentation/more/settings/screen/SettingsTrackingScreen`;
  - el `TrackerPreference` de `Preference` y `PreferenceItem`;
  - el diálogo de login con usuario y contraseña.
- Notas: `presentation/manga/MangaNotesScreen`, `ui/manga/notes/MangaNotesScreen` y `components/MangaNotesTextArea`. `MangaNotesSection` y `MangaNotesDisplay` ya están portados.

Se borran los stubs de `desktop-app/src/stubs` para `Tracker`, `TrackerManager`, `TrackInteractors` y `TrackPreferences`.

### Sustituciones de escritorio
| Pieza de Mihon | En escritorio |
|---|---|
| `TrackerManager` con los 12 trackers | La misma clase, solo con los 5 de 5b-1 en `trackers`. 5b-2 añade el resto |
| `DelayedTrackingUpdateWorker` (WorkManager) | Un `PeriodicJob` del `JobScheduler` (3a) que envía los cambios pendientes mientras la app está abierta o en la bandeja |
| Credenciales en `TrackPreferences` como claves privadas en texto | Las mismas claves, con el valor cifrado con DPAPI (`Dpapi` de 2b). `TrackPreferences` es la única puerta: cifra al guardar y descifra al leer |
| Interruptor de incógnito en la pestaña "Más" | Menú ⋮ de la Biblioteca y menú de la bandeja |
| Banner de incógnito | El de Mihon en `AppStateBanners`: `AppContent` deja de pasarle `false` fijo |

## 3. Comportamiento

### A. Vincular
1. El botón Seguimiento de la ficha abre la hoja. Muestra cada tracker con sesión, y para cada uno "Añadir" y buscar.
2. Al elegir un resultado se vincula y se escribe el estado inicial.
3. Los enhanced (Komga, Kavita, Suwayomi) se vinculan solos (`bindEnhancedTrackers`) al añadir a la biblioteca un manga de su fuente: desde la ficha, Explorar o Historial.

### B. Sincronizar
- **Al terminar un capítulo en el lector:** si "Actualizar al leer" está activo, `TrackChapter` sube el último capítulo leído a cada tracker vinculado.
- **Al marcar como leído desde la ficha:** `autoUpdateTrackOnMarkRead` decide si se sube siempre, se pregunta o nunca.
- **Al refrescar la ficha:** `RefreshTracks` baja el estado remoto.
- **Progreso de los enhanced:** `SyncChapterProgressWithTrack` aplica el progreso remoto a los capítulos locales, como en Mihon.

### C. Sin red o con el servidor caído
- El cambio se guarda en `DelayedTrackingStore`, como en Mihon.
- El job periódico lo reintenta cada pocos minutos.
- No se muestra error en cada lectura. Solo se avisa si la sesión caducó.

### D. Sesión
- **Kitsu y MangaUpdates:** diálogo de usuario y contraseña. Si el login falla, se muestra el mensaje del servicio y no se guarda nada.
- **Un 401 marca `trackAuthExpired`:** Ajustes › Seguimiento muestra "volver a iniciar sesión", como en Mihon.
- **Cerrar sesión:** borra las credenciales. Los vínculos de la base de datos se quedan.

### E. Credenciales y backup
- Usuario, contraseña y token se guardan cifrados con DPAPI. Ningún fichero de preferencias los contiene en claro.
- El backup solo los incluye con la opción "privadas", como Mihon. En ese caso viajan descifrados, porque un blob DPAPI no sirve en otro equipo ni en Android. Al restaurarlos se vuelven a cifrar.
- `BackupFileValidator` vuelve a avisar de "trackers sin sesión", para los trackers registrados en `TrackerManager`.

### F. Migración
`MigrateMangaUseCase` (3b) ya copia los tracks y re-vincula los enhanced contra la fuente nueva. Con trackers reales, eso se activa.

### G. Incógnito
- **Al activarlo** (desde la Biblioteca o la bandeja) se ve el banner de Mihon en toda la ventana.
- **Mientras está activo:**
  - no se guarda historial (`GetIncognitoState`, que ya existe desde 2a);
  - no se sincroniza con trackers, como en Mihon (`ReaderViewModel.updateTrackChapterRead`).
- El incógnito por extensión (2a) sigue igual.

### H. Notas
- Se editan en `MangaNotesScreen`, con formato.
- Se guardan en `manga.notes` y viajan en el backup (5a).
- La ficha las muestra en su sección de notas, y la barra de la ficha tiene "Editar notas".

## 4. Errores
| Caso | Comportamiento |
|---|---|
| Login fallido | Mensaje del servicio; no se guarda ninguna credencial |
| Sin red al sincronizar | Cambio en `DelayedTrackingStore`; reintento por el job; sin aviso |
| 401 | `trackAuthExpired`; aviso en Ajustes › Seguimiento |
| Servidor de Komga, Kavita o Suwayomi caído | Como sin red |
| No se puede descifrar con DPAPI (el perfil de Windows cambió) | Se trata como sesión cerrada: el usuario vuelve a iniciar sesión |
| Track de un tracker no registrado (OAuth, de un backup) | Se queda en la base de datos sin mostrarse |

## 5. Pruebas

### Automáticas
- **Flujo, con un tracker falso** registrado en el grafo de test:
  - vincular desde la hoja;
  - terminar un capítulo sube el capítulo leído;
  - con "Actualizar al leer" apagado no sube nada;
  - en incógnito no sube nada ni guarda historial.
- **Sin red:** el cambio queda en `DelayedTrackingStore` y el job lo envía cuando el tracker vuelve a responder.
- **Kitsu y MangaUpdates contra `MockWebServer`.** Un interceptor de test redirige sus hosts reales al servidor local, sin tocar su código. Cubre:
  - login correcto;
  - contraseña incorrecta: error y nada guardado;
  - buscar, vincular y actualizar;
  - un 401 marca la sesión como caducada.
- **Enhanced:** un manga de una fuente que Komga acepta se vincula solo al añadirlo a la biblioteca.
- **Credenciales:** el fichero de preferencias no contiene el usuario, la contraseña ni el token en claro. Un backup sin "privadas" no los lleva; con "privadas" los lleva en claro, y al restaurar se vuelven a cifrar.
- **UI:**
  - Ajustes › Seguimiento lista solo los 5 trackers;
  - la ficha muestra el botón Seguimiento;
  - una nota se edita y se guarda;
  - el interruptor de incógnito en la Biblioteca enciende el banner, y el de la bandeja también.
- **Backup:** el validador avisa de "trackers sin sesión" para un backup con tracks de Kitsu sin login.

### Manual (al usuario)
1. Login en Kitsu o MangaUpdates con una cuenta real.
2. Vincular un manga, leer un capítulo y verlo actualizado en la web del tracker.
3. Con la red desconectada, leer, reconectar y ver el cambio subido.
4. Con Komga, Kavita o Suwayomi: añadir un manga de esa fuente y verlo vinculado solo.
5. Escribir una nota con formato y verla en la ficha.
6. Activar incógnito desde la bandeja, leer y comprobar que no queda historial.
