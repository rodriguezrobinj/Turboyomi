# Subproyecto 11a: limpiar base de datos y depuración

## 1. Objetivo
Primero de 11a–11f, las funciones de Mihon que aún faltaban en escritorio tras la revisión de 2026-10-29:

- **11a** limpiar base de datos, esquema del backup y tareas programadas (este documento).
- **11b** seguridad: bloqueo con Windows Hello, bloqueo por inactividad, pantalla segura, ocultar el contenido de las
  notificaciones.
- **11c** enlaces: `tachiyomi://add-repo`, `mihon://extension-store`, abrir `.tachibk` y enlaces de manga pasados como
  argumento.
- **11d** idioma de la app.
- **11e** primer arranque (onboarding), "Apóyanos" y licencias de las librerías.
- **11f** restricciones de dispositivo para la actualización de la biblioteca.

Cada uno lleva su spec, su rama y su PR, en ese orden.

### Fuera de alcance de 11a
- Nada de WorkManager: la pantalla de tareas muestra el `JobScheduler` de escritorio.

## 2. Diseño

### Limpiar base de datos
Gemelo de `ClearDatabaseScreen` (`eu.kanade.presentation.more.settings.screen.advanced`), copiado con los mínimos
cambios: lista de fuentes con manga fuera de la biblioteca (`GetSourcesWithNonLibraryManga`), selección, seleccionar
todo / invertir, diálogo con "conservar manga leídos" y borrado con `MangaRepository.deleteNonLibraryManga` +
`HistoryRepository.deleteResetHistory` en el `AppCoroutineScope`. Todo eso ya existe en `domain`/`data`.

Se abre desde Ajustes › Avanzado › Datos, debajo de "Invalidar índice de descargas", como en Mihon.

Escritorio: la lista lleva `keyboardScrollable`. Seleccionar todo responde también a Ctrl+A (`AppEvents.selectAll`).

### Esquema del backup
Gemelo de `BackupSchemaScreen` (`…screen.debug`): el texto de `ProtoBufSchemaGenerator` para `Backup`, en monoespaciada,
con botón de copiar. Título en inglés, como Mihon. El scroll vertical lleva `keyboardScrollable`.

### Tareas programadas
Equivale al `WorkerInfoScreen` de Mihon (título "Worker info" en inglés, igual que Mihon), pero lee el `JobScheduler`:
una fila por tarea con su nombre, intervalo (o "off"), última ejecución, próxima ejecución y si está en marcha.

- `JobScheduler` expone `jobs` y `isRunning(name)`. Pasa a crearse en `DesktopAppGraph` (`graph.jobScheduler`), y `Main`
  lo arranca, en lugar de construirlo allí.
- El texto de cada tarea se forma en una función pura (`jobSummary`) que se prueba sin UI.

Ambas pantallas de depuración se abren desde `DebugInfoScreen` (escritorio), arriba del todo, como en Mihon.

## 3. Pruebas
- `ClearDatabaseScreenTest`: con dos fuentes, el botón borrar empieza desactivado. Seleccionar todo lo activa. Al
  confirmar se llama a `deleteNonLibraryManga` con los ids y `keepRead = true`.
- `JobSummaryTest`: tarea apagada, tarea pendiente y tarea en marcha.
- `BackupSchemaScreen`: el texto contiene `message Backup`.
