# Diseño: subproyecto 5a — backup y pantalla de datos

- **Fecha:** 2026-10-23
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md` (requisito 4 de la sección 1, criterio de éxito del backup, sección 5.4)
- **Specs hermanas:** `2026-10-22-subproyecto-4a-lectura-local-design.md` (almacenamiento, `FileDialogs`),
  `2026-10-21-subproyecto-3a-biblioteca-design.md` (jobs en el scope de la app, bandeja, log de errores)
- **Base:** `master` en `b5c081c6d` (subproyecto 4b integrado; 418 tests)

## 1. Objetivo y alcance

### Objetivo
1. Un `.tachibk` de Mihon Android se restaura aquí, y uno creado aquí se restaura en Mihon Android.
2. Backup manual y automático, con las opciones de Mihon.
3. La pantalla Ajustes › Datos de Mihon completa: almacenamiento, backup, uso del disco, caché de capítulos y exportar la
   biblioteca a CSV.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| División del subproyecto 5 | 5a backup y pantalla Datos; 5b trackers (`TrackerManager`, OAuth y los 11 trackers) |
| Enfoque | Portar `data/backup/` de Mihon con su paquete y cambiar solo lo de Android. Se descarta un formato o un serializador propio: la compatibilidad byte a byte es el requisito |
| Pantalla Datos | La de Mihon completa, sin el aviso de MIUI |
| Backups automáticos | Como Mihon: `<almacenamiento>\autobackup` (por defecto `Documentos\MangaReader\autobackup`), los 4 últimos, cada 12 h por defecto |
| Fixture de compatibilidad | Un `.tachibk` real que exporta el usuario desde Mihon Android (§5) |

### Cambios respecto a la spec padre
- §5.4 situaba los backups automáticos en `%APPDATA%\<App>\backups`. Van a `autobackup` dentro de la ubicación de
  almacenamiento (4a), como en Mihon. Se ven junto a `local` y `downloads`, se copian al móvil sin buscarlos y siguen a
  la ubicación si el usuario la cambia.
- §5.4 dice "las preferencias exclusivas de Android se ignoran al restaurar". Se concreta en §3 D: solo se excluye
  `storage_dir`, que rompería el almacenamiento. Las demás se guardan sin efecto, igual que Mihon guarda las que no
  conoce.

### Fuera de alcance (5a)
| Qué | Dónde |
|---|---|
| Iniciar sesión en trackers, sincronizar y el aviso de "trackers sin sesión" al restaurar | 5b |
| Backups en la nube o sincronización entre equipos | No se pide |
| Backup automático con la app cerrada (tarea programada de Windows) | No: los jobs corren con la app abierta o en la bandeja (3a) |

## 2. Arquitectura

### Piezas de Mihon (copiadas con su paquete)
`eu/kanade/tachiyomi/data/backup/`:
- `models/` (`Backup`, `BackupManga`, `BackupChapter`, `BackupCategory`, `BackupHistory`, `BackupTracking`,
  `BackupPreference`, `BackupSource`, `BackupExtensionStore`);
- `BackupDecoder`, `BackupFileValidator`;
- `create/BackupCreator`, `create/BackupOptions` y `create/creators/*`;
- `restore/BackupRestorer`, `restore/RestoreOptions` y `restore/restorers/*`.

Además:
- `data/export/LibraryExporter`;
- `presentation/more/settings/screen/SettingsDataScreen` (sustituye a la versión reducida de 4a) y
  `screen/data/CreateBackupScreen`, `RestoreBackupScreen` y `StorageInfo`.

### Sustituciones de escritorio
| Pieza de Mihon | En escritorio |
|---|---|
| `BackupCreateWorker` (WorkManager, periódico) | `BackupJob`: corrutina en el scope de la app. `create(uri, options)` para el manual; el automático lo programa `backupInterval` |
| `BackupRestoreWorker` | `RestoreJob`: corrutina en el scope de la app, con `progress: StateFlow` para la pantalla |
| `BackupNotifier` (notificaciones con progreso) | Notificador sobre `TrayNotifier` (3a): terminado y errores. El progreso se ve en la pantalla |
| SAF (`ActivityResultContracts.CreateDocument` / `OpenDocument`) | `FileDialogs` (4a): guardar con el nombre de `BackupCreator.getFilename()`; abrir filtrado a `*.tachibk` |
| `android.text.format.Formatter` en `StorageInfo` | Formato de tamaño propio (B, KB, MB, GB), en la copia de escritorio |
| `DiskUtil.getExternalStorages` | El disco de la ubicación de almacenamiento |
| Borrado automático de caché en `MainActivity` (`isLaunch`) | Al arrancar la app, en `Main` |
| `Uri` en `BackupCreator.backup` y el restorer | `UniFile` sobre `java.io.File`, como en 4a |

## 3. Comportamiento

### A. Crear
1. El usuario elige las opciones de `BackupOptions` y luego el destino en el diálogo de guardar.
2. `BackupJob.create(uri, options)` escribe el fichero: gzip y protobuf de kotlinx-serialization, como Mihon.
3. Se escribe a `<destino>.tmp` y se mueve al destino al terminar. Si falla (disco lleno, sin permiso), se borra el
   temporal y se avisa con el mensaje del error. Nunca queda un `.tachibk` a medias.
4. Al terminar, aviso "Backup creado".

### B. Automático
- Intervalos de Mihon: apagado, 6, 12, 24, 48 h o semanal. Por defecto 12 h.
- Mientras la app está abierta o en la bandeja, `BackupJob` comprueba `lastAutoBackupTimestamp`. Si toca, crea uno con
  las opciones por defecto en `autobackup` y borra los más viejos hasta dejar 4 (`MAX_AUTO_BACKUPS` de Mihon).
- Al arrancar, si el intervalo ya se cumplió, se crea uno.
- `lastAutoBackupTimestamp` solo cambia si el backup sale bien. Si falla, aviso de error y se reintenta en el siguiente
  intervalo.

### C. Restaurar
1. El usuario elige el fichero. `BackupFileValidator` lo lee antes de tocar la base de datos.
2. Un fichero corrupto, que no es gzip o que no decodifica: error y no se restaura nada.
3. Fuentes que faltan: se listan como en Mihon. Si el usuario restaura igual, esos mangas quedan con la fuente stub.
4. El usuario elige qué restaurar (`RestoreOptions`). `RestoreJob` aplica, en el orden de Mihon: categorías,
   preferencias, repos de extensiones, preferencias de fuentes y mangas (con capítulos, historial y tracks).
5. Cada manga se restaura por separado: uno con datos malos se apunta en el log y los demás siguen.
6. Al terminar: "Restaurado en X s, N errores", con "Abrir log" si hay errores (el fichero de errores de 3a).

### D. Preferencias
- `storage_dir` no se incluye al crear y se ignora al restaurar. En Android es una URI `content://` y aquí es una ruta
  de Windows: restaurarla dejaría la app sin almacenamiento.
- Las demás claves se restauran como en Mihon (`PreferenceRestorer`). Las que solo usa Android quedan guardadas sin
  efecto.
- Las claves privadas (`Preference.isPrivate`) solo viajan con la opción "privadas" activada, como en Mihon. Las de
  estado (`Preference.isAppState`) nunca viajan.
- Las preferencias de fuente van en los `SharedPreferences` `source_<id>` de AndroidCompat, el mismo nombre que en
  Android.
- Tras restaurar, los intervalos de actualización de biblioteca y de backup se aplican sin reiniciar: los jobs leen la
  preferencia.

### E. Exclusión
- Solo una operación de backup o restauración a la vez.
- Mientras se restaura no corren ni el backup automático ni la actualización de biblioteca. Los botones de crear y
  restaurar muestran `restore_in_progress` de Mihon.

### F. Tracks antes de 5b
- Los tracks del backup se guardan en la base de datos, como en Mihon, aunque no haya sesión en el tracker.
- El validador no avisa de "trackers sin sesión" hasta 5b, porque todavía no hay trackers.

### G. Resto de la pantalla Datos
- **Uso del disco:** `StorageInfo` muestra el espacio libre y el total del disco de la ubicación de almacenamiento.
- **Caché de capítulos:** "Borrar caché" llama a `ChapterCache.clear()` y dice cuántos ficheros borró.
  "Borrar al arrancar" la vacía al iniciar la app.
- **Exportar a CSV:** `LibraryExporter` de Mihon. El usuario elige columnas y destino, y se escribe en UTF-8 con BOM.
  Mihon escribe sin BOM, y Excel en Windows abre ese CSV como ANSI y rompe los acentos. El BOM no estorba a otros
  lectores de CSV.

## 4. Errores
| Caso | Comportamiento |
|---|---|
| Disco lleno o sin permiso al crear | Se borra el `.tmp`, aviso de error con el mensaje; el destino anterior, si existía, queda intacto |
| Fichero corrupto, truncado o no gzip | Error de Mihon (`invalid_backup_file` y el detalle), sin cambios en la base de datos |
| Manga con datos malos | Se apunta en el log de errores, la restauración sigue |
| Fuente que falta | Se avisa antes de restaurar; el manga queda con fuente stub |
| Backup automático falla | Aviso por la bandeja, reintento en el siguiente intervalo |
| Carpeta `autobackup` no se puede crear | Aviso de error; el siguiente intervalo lo vuelve a intentar |

## 5. Pruebas

### Automáticas
- **Ida y vuelta:** crear un backup y decodificarlo da los mismos mangas, capítulos, categorías, historial, tracks,
  preferencias y fuentes.
- **Formato:** el fichero empieza con la cabecera gzip (`1f 8b`) y sus campos protobuf llevan los números de Mihon.
- **Compatibilidad real:** el fixture `desktop-app/src/test/resources/backup/mihon-android.tachibk`, exportado por el
  usuario desde Mihon Android, se restaura y deja la biblioteca, las categorías, lo leído y el historial que contiene.
  Un backup creado aquí se decodifica con el `BackupDecoder` de Mihon sin cambios.
- **Preferencias:** `storage_dir` ni sale ni entra; las privadas solo con la opción; una clave desconocida se guarda.
- **Errores:** un fichero corrupto da error y la base de datos queda igual; un manga malo va al log y los demás se
  restauran; con disco lleno (seam `DiskUtil.usableSpace`) no queda ningún `.tachibk` a medias.
- **Automático:** con el intervalo cumplido se crea uno en `autobackup` y quedan los 4 últimos; durante una
  restauración no se crea ninguno.
- **UI:** la pantalla Datos muestra las secciones de Mihon sin el aviso de MIUI; "Borrar caché" la vacía; el CSV sale con BOM y
  con las columnas elegidas.

### Manual (al usuario)
1. Restaurar un backup completo del móvil y comprobar biblioteca, categorías, lo leído, historial y ajustes.
2. Crear un backup aquí, copiarlo al móvil y restaurarlo en Mihon Android.
3. Con el intervalo a 6 h, dejar la app en la bandeja y ver aparecer el backup en `autobackup`.
4. Exportar la biblioteca a CSV y abrirla en Excel: los acentos se ven bien.
5. Borrar la caché de capítulos y ver bajar el tamaño mostrado.
