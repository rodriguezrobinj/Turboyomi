# Subproyecto 12: experiencia de escritorio (ratón, ayuda, tooltips, arrastrar, barra de tareas)

## 1. Objetivo
Seis mejoras propias de un PC con ratón y teclado que Mihon (Android) no necesita, en un solo PR.
El punto 7 (ir a un capítulo) queda solo documentado en §8.

## 2. Botones atrás y adelante del ratón (12a)
- Los botones laterales del ratón (`PointerButton.Back` / `Forward` de Compose; botones 4 y 5 de AWT) navegan como en
  un navegador. Se escuchan en la raíz de `AppContent` en la pasada `Initial`, para que ninguna pantalla se los quede.
- **Atrás** entra por `WindowBackInput.back()`, el mismo camino que Esc y Alt+←: cierra antes un diálogo que la
  pantalla de detrás.
- **Adelante** (y Alt+→, nuevo `Shortcut.Forward`): `ForwardHistory` guarda las pantallas que se quitaron de la pila de
  Voyager. Vuelve a poner la última. Reglas, comparando la pila de antes con la de ahora:
  - la nueva es un prefijo de la anterior (se volvió atrás): las pantallas quitadas pasan a la historia, la más cercana
    arriba;
  - la nueva es la anterior más la cima de la historia (se fue adelante): se saca de la historia;
  - cualquier otro cambio (abrir otra pantalla, `replaceAll`, el reinicio por idioma): la historia se vacía.
- La ayuda (F1) añade "Botón atrás/adelante del ratón · Alt + ←/→".

## 3. Ayuda de atajos más visible (12b)
La ayuda de F1 ya existe (spec 8a §3 C), pero solo se llega por F1 o Ajustes › Acerca de. Se añade:
- "Atajos de teclado (F1)" en el menú de tres puntos de la biblioteca.
- Un botón de ayuda en la barra superior del lector, junto a ajustes rápidos y pantalla completa.

## 4. Tooltips en los botones de solo icono (12c)
- Un `IconTooltip(text) { … }` en `desktop-app` (`mihon.desktop.app.platform`) envuelve el botón con `TooltipBox` + `PlainTooltip`, como ya
  hace `AppBar`. `AppBar` (gemelo de Mihon) se queda como está, para no alejarlo de upstream.
- Lo usan los botones de solo icono con `contentDescription` que el usuario ve a diario: indicador de descarga del
  capítulo, continuar leyendo en la biblioteca, historial (borrar y reanudar), lector (ajustes, pantalla completa,
  capítulo anterior/siguiente), descargas, categorías, fijar fuente, extensiones, portada, calendario, migración,
  seguimiento. La ventana WebView no: un popup de Compose queda debajo del navegador nativo. El texto es el mismo
  `contentDescription`; no hay textos nuevos.

## 5. Arrastrar y soltar a la fuente local (12d)
- La ventana acepta archivos soltados (`dragAndDropTarget` con `DragData.FilesList`).
- Cada elemento se copia a la carpeta de la fuente local (`StorageManager.getLocalSourceDirectory()`):
  - una carpeta: entra como un manga, con su nombre;
  - un archivo de capítulo (`Archive.isSupported`: zip, cbz, rar, cbr, 7z, cb7, tar, cbt): entra como capítulo de un
    manga con el nombre del archivo sin extensión;
  - cualquier otro archivo se ignora y se avisa.
- **Nunca se sobrescribe:** un archivo que ya existe en el destino se salta. Se copia, no se mueve: el original queda.
- La copia corre en IO. Un mensaje al empezar y otro al terminar ("N añadidos a la fuente local", con la acción
  "Ver" que abre la fuente local buscando el nombre si fue uno solo).
- Sin carpeta de almacenamiento configurada: mensaje de error, no se copia nada.

## 6. Progreso en la barra de tareas (12e)
- `java.awt.Taskbar` (`setWindowProgressValue` / `setWindowProgressState`), que en Windows usa `ITaskbarList3`. Sin
  JNA. Si la función no está soportada, no hace nada.
- Fuente del progreso, por orden:
  1. Actualización de la biblioteca (`LibraryUpdateJob.progress`): `done / total`.
  2. Descargas: la cola desde que empezó (`peak` = el mayor tamaño visto desde que dejó de estar vacía);
     `(peak − restantes + progreso del capítulo en curso) / peak`. Cola parada con elementos: estado pausado (amarillo).
  3. Nada: sin progreso.
- El cálculo es una función pura (`taskbarProgress(...)`) con prueba.

## 7. Pantalla completa sobre la barra de tareas (12f)
- Al entrar en pantalla completa, `ITaskbarList2::MarkFullscreenWindow(hwnd, TRUE)`; al salir, `FALSE`. Es lo que hacen
  los navegadores: Windows no vuelve a mostrar la barra de tareas sobre la ventana aunque pierda el foco un momento.
- COM por JNA: `CoCreateInstance(CLSID_TaskbarList, IID_ITaskbarList2)`, `HrInit` (slot 3), `MarkFullscreenWindow`
  (slot 8), `Release`. En el hilo de AWT, que ya está en un apartamento COM. Un fallo se registra y no impide la
  pantalla completa.

## 8. Pendiente, solo documentado: ir a un capítulo
Con listas largas (cientos de capítulos), un campo en la ficha del manga para escribir un número y saltar a ese
capítulo en la lista. Ideas: icono de búsqueda en la cabecera de la lista, Ctrl+G, coincidencia por
`chapterNumber` y si no por nombre. No se implementa en este subproyecto.

## 9. Pruebas
- `ForwardHistoryTest`: atrás, adelante, atrás de varias pantallas, abrir otra pantalla vacía la historia.
- `MouseNavigationTest` (Compose): el botón atrás del ratón cierra la pantalla de arriba; el de adelante la recupera.
- `LocalDropTest`: carpeta, archivo de capítulo, archivo no soportado, destino existente no sobrescrito.
- `TaskbarProgressTest`: biblioteca primero, cola de descargas, pausa, nada.
- `ShortcutHelpTest` (existente): cubre `Shortcut.Forward`.
- A mano: botones del ratón reales, arrastrar desde el Explorador, barra de tareas durante una descarga, pantalla
  completa y Alt+Tab.
