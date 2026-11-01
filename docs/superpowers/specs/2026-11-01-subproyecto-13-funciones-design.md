# Subproyecto 13: inicio con Windows, Explorador, editar ficha, actualizar extensiones

## 1. Objetivo
Cuatro funciones de la lista de alto impacto del 2026-11-01 (opciones 1, 3, 4 y 6), en un solo PR.

## 2. Iniciar con Windows en la bandeja (13a)
- Ajustes › Biblioteca, junto a "cerrar a la bandeja": "Iniciar con Windows". Desactivado de serie. Solo se ve con
  la app empaquetada (`UpdateInstaller.isDevelopment()` falso): en desarrollo el ejecutable es `java.exe`.
- `TrayPreferences.startWithWindows`. `Autostart.apply(enabled, exe)` escribe o borra el valor `Turboyomi` de
  `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` con `"<exe>" --tray`. Se aplica en cada arranque (así sigue a
  la ruta actual del exe) y en cada cambio del interruptor.
- Con `--tray` y bandeja disponible, la ventana arranca oculta; sin bandeja, minimizada. Un `--tray` reenviado por una
  segunda instancia no trae la ventana al frente. `DeepLinks` ya ignora los argumentos que empiezan por `-`.

## 3. Mostrar en el Explorador (13b)
- `Explorer`: una carpeta se abre (`Desktop.open`); un archivo se abre en su carpeta, seleccionado
  (`explorer.exe /select, "<ruta>"`).
- Ficha del manga, menú de tres puntos: "Mostrar en el Explorador". Abre la carpeta de descargas del manga
  (`DownloadProvider.findMangaDir`), o la carpeta de la fuente local si el manga es local. Sin carpeta: mensaje
  "Aún no hay nada descargado".
- Capítulo descargado: el menú de su indicador (hoy solo "Borrar") gana "Mostrar en el Explorador"
  (`ChapterDownloadAction.SHOW_IN_EXPLORER`), en la ficha del manga y en Novedades.

## 4. Editar la ficha del manga (13c)
- Ficha de un manga de la biblioteca, menú de tres puntos: "Editar información". Diálogo con título, autor, artista,
  descripción, géneros (separados por comas) y estado. Botones: Guardar, Cancelar y Restablecer.
- **Sin cambiar el esquema** (backups compatibles con Mihon): lo guardado se escribe en las columnas `remote_*` de la
  base, así se ve en toda la app y viaja en los backups. Además, `MangaEdits` guarda lo editado en
  `<data>/manga-edits.json`, con la clave `"<sourceId>:<url>"`, que no cambia al restaurar un backup.
- `UpdateMangaFromRemote` (la única vía que refresca la ficha desde la fuente) aplica lo editado por encima de lo que
  llega, así una actualización no lo deshace. `insertNetworkManga` ya no toca los detalles de los mangas de la
  biblioteca; por eso solo se editan mangas de la biblioteca.
- Cambiar el título renombra la carpeta de descargas (`DownloadManager.renameManga`), como hace Mihon al actualizarlo.
- Restablecer borra lo editado, devuelve el título que tenía antes de la primera edición (guardado en la edición:
  Mihon no cambia al refrescar el título de un manga de la biblioteca) y refresca el resto desde la fuente.

## 5. Actualizar extensiones solas (13d)
- Ajustes › Explorar › Extensiones: "Actualizar extensiones automáticamente". Desactivado de serie, con la advertencia
  de que instala versiones nuevas sin preguntar.
- `ExtensionManager.periodicJob()`, cada 12 h (y en el primer chequeo tras activarlo): recarga los listados y actualiza
  las extensiones cargadas con versión nueva. Las reglas de confianza no cambian: una versión con otra firma queda sin
  cargar hasta que el usuario la confíe, igual que al actualizar a mano. Aviso por la bandeja con las que se
  actualizaron.

## 6. Pruebas
- `AutostartTest`: escribe y borra el valor en una clave de prueba de HKCU, con el exe entre comillas y `--tray`.
- `ExplorerTest`: el comando de selección con espacios en la ruta.
- `MangaEditsTest`: guardar, leer tras recargar, restablecer; lo editado se impone a lo que llega de la fuente.
- `ExtensionAutoUpdateTest`: solo las cargadas con actualización.
- A mano: reiniciar Windows con la opción activa, abrir el Explorador, editar y refrescar un manga.
