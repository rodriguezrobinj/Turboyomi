# Subproyecto 8a: teclado del lector

## 1. Objetivo

Leer en PC sin soltar el teclado y descubrir qué teclas hay. Tres cosas:

1. Desplazamiento automático en webtoon, con velocidad ajustable.
2. Zoom con teclado en modo páginas (Ctrl+`+`, Ctrl+`-`, Ctrl+0).
3. Una ayuda de atajos (F1) en toda la app.

Es el primero de los subproyectos 8a–8e (lector: teclado, filtros de imagen, páginas anchas, visualización, por
manga y sistema), acordados con el usuario el 2026-10-26.

### Fuera de alcance
- Desplazamiento automático en modo páginas (pasar de página cada N segundos): el usuario eligió solo webtoon.
- Un botón de reproducción en la barra del lector: se controla con teclas y desde los ajustes rápidos.
- Abrir la ayuda con `?`: en el teclado español es Shift+' y la tecla cambia según la distribución.
- Cambiar los atajos o permitir redefinirlos.

## 2. Estado actual

- Teclas del lector: `readerActionFor` en `reader/ReaderAction.kt`, enviadas por `ReaderContent.perform`
  (`ReaderScreen.kt`). En webtoon, Ctrl+`+`/`-`/rueda cambian el espaciado lateral y Ctrl+↑/↓ el avance de las
  flechas, con un aviso de 1,5 s sobre la página (PR #6).
- Atajos de ventana: `Shortcut` y `shortcutFor` en `main/Shortcut.kt`, despachados por `AppEvents.send`.
- Scroll por teclado en listas: subproyecto 7 (`KeyboardScroll.kt`); Re Pág, Av Pág, Espacio, Inicio, Fin, ↑, ↓.
- Webtoon: `WebtoonController` (`viewer/WebtoonViewer.kt`) desplaza con `glideBy` dentro de `state.scroll { }`.
  Al final de un capítulo sigue con el siguiente (lectura continua, PR #4).
- Modo páginas: `PageContent` usa `net.engawapg.lib.zoomable` con `rememberZoomState(maxScale = 5f)`. Ya amplía
  con doble clic y con Ctrl+rueda (`MouseWheelZoom.EnabledWithCtrlKey`). El estado se crea de nuevo cada vez que
  la página pasa a ser la actual, así que el zoom empieza de cero en cada página. No hay teclas de zoom.

## 3. Diseño

### A. Desplazamiento automático (solo webtoon)

**Teclas.** `readerActionFor` añade, solo con `mode.type == Webtoon`:
- `S` sin modificadores: `ReaderAction.ToggleAutoScroll`.
- Ctrl+Shift+↑ / Ctrl+Shift+↓: `AutoScrollFaster` / `AutoScrollSlower`. Van antes que Ctrl+↑/↓ (avance de las
  flechas), que siguen sin Shift.

**Velocidad.** Preferencia nueva `ReaderPreferences.webtoonAutoScrollSpeed` (`"pref_webtoon_autoscroll_speed"`,
`Int`, por defecto 10): porcentaje del alto de la ventana por segundo. Rango
`WEBTOON_AUTOSCROLL_MIN = 2` a `WEBTOON_AUTOSCROLL_MAX = 50`, paso `WEBTOON_AUTOSCROLL_STEP = 2`.
- `AutoScrollFaster`/`Slower` suman o restan el paso, limitado al rango, y muestran el aviso
  "Desplazamiento automático: 12 %/s". No pausan.
- Slider en los ajustes rápidos (solo webtoon, debajo del avance de las flechas, paradas cada 2) y en
  Ajustes › Visor.

**Motor.** En `WebtoonController`:
- `suspend fun autoScroll(speed: () -> Float)`: bucle con `withFrameNanos` dentro de **un solo**
  `state.scroll(MutatePriority.Default) { }`; cada frame hace `scrollBy(viewport × speed() × dt)`.
  `speed()` se lee en cada frame, así que cambiar la velocidad no reinicia nada.
- Termina sola cuando `!state.canScrollForward` y no queda capítulo siguiente que cargar (`onPastEnd` se llama una
  vez; si la lista no crece en 2 s, se para).
- La rueda y el arrastre usan `MutatePriority.UserInput`, que cancela el `scroll` en marcha: esa cancelación es la
  pausa por rueda, sin código propio.

**Estado y pausa.** En `ReaderContent`:
- `autoScrollJob: Job?`. `ToggleAutoScroll` lo lanza (`scope.launch { controller.autoScroll(...) }`) o lo cancela.
  `isAutoScrolling` = el job está activo.
- Pausan, cancelando el job: cualquier otra `ReaderAction` salvo `AutoScrollFaster`/`Slower` y las de espaciado o
  avance (`ZoomIn`, `ZoomOut`, `ScrollStepUp`, `ScrollStepDown`); un clic en la página; mostrar los controles;
  abrir los ajustes rápidos; salir del lector; cambiar de modo de lectura.
- Indicador mientras corre: un icono de reproducción pequeño en la esquina superior derecha, con la misma
  superficie que el aviso (`inverseSurface`), `testTag("autoscroll on")`.

### B. Zoom con teclado (modo páginas)

**Teclas.** En modos que no son webtoon, Ctrl+`+` (`Plus`, `Equals`, `NumPadAdd`) es `ReaderAction.ZoomIn`,
Ctrl+`-` (`Minus`, `NumPadSubtract`) es `ZoomOut` y Ctrl+0 (`Zero`, `NumPad0`) es `ZoomReset` (nueva). En webtoon
`ZoomIn`/`ZoomOut` siguen siendo el espaciado lateral y Ctrl+0 no hace nada.

**Efecto.** `ViewerController` añade `suspend fun zoom(action: ReaderAction): Float?` (la escala nueva, o `null` si
el visor no tiene zoom). `PagerController` lo aplica al `ZoomState` de la página actual:
- `ZoomIn`: escala × 1,25; `ZoomOut`: escala ÷ 1,25; limitada a 1×–5×. `ZoomReset`: 1×.
- Con `ZoomState.changeScale(target, position = centro de la página)`, animado.
- Para que el controlador llegue al estado, `PageContent` lo publica con un callback `onZoomState` cuando la página
  es la actual (como `onController`).
- Aviso "Zoom: 156 %" (escala × 100 redondeada). Al cambiar de página el zoom vuelve a empezar, como ahora.

### C. Ayuda de atajos (F1)

**Una sola fuente.** Nuevo `main/ShortcutHelp.kt`:
- `data class ShortcutRow(val keys: String, val description: StringResource)`; las teclas se escriben como se ven
  en un teclado español ("Ctrl + F", "Alt + ←", "Re Pág").
- `appShortcutRows: List<Pair<Shortcut?, ShortcutRow>>` y
  `readerShortcutRows(mode: ReadingMode?): List<Pair<ReaderAction?, ShortcutRow>>`; con `mode == null`, las filas
  de ambos tipos de visor con una marca "(webtoon)" o "(páginas)". El `null` del par es para filas sin acción propia
  (Esc, el scroll de listas del subproyecto 7, el clic en los tercios de la página).

**Apertura.** `Shortcut.Help` con F1 sin modificadores (`beforeFocusedComponent = true`), y un ítem
"Atajos de teclado" en Ajustes › Acerca de. `AppEvents` emite `help`; `AppContent` muestra `ShortcutHelpDialog`.
- Si el lector está abierto, el lector pasa su modo (`LocalReaderMode`, un `CompositionLocal` que `ReaderContent`
  provee), y la sección del lector muestra solo ese modo. Si no, ambos.
- El diálogo usa `Dialog` + `Surface` como los ajustes rápidos, con dos encabezados y filas de dos columnas
  (teclas | descripción), con scroll y `keyboardScrollable` del subproyecto 7. Se cierra con Esc o clic fuera.
- Abrir la ayuda pausa el desplazamiento automático (es un diálogo, como los ajustes rápidos).

### D. Textos

Strings nuevos en `MR` con traducción `es` (skill `strings-i18n`): título y descripción del desplazamiento
automático y su velocidad, "Zoom", "Atajos de teclado", los dos encabezados de la ayuda, las marcas
"(webtoon)"/"(páginas)" y una descripción por fila que no tenga ya string. El formato del aviso sigue siendo
"<título>: <valor>".

## 4. Pruebas

- `ReaderInputTest`: `S` y Ctrl+Shift+↑/↓ solo en webtoon; Ctrl+±/0 en modo páginas dan `ZoomIn`/`ZoomOut`/
  `ZoomReset`; Ctrl+0 en webtoon da `null`; AltGr (Ctrl+Alt) sigue sin hacer nada.
- `ShortcutTest`: F1 es `Shortcut.Help`.
- `ShortcutHelpTest`: cada `ReaderAction` salvo `ToggleMenu` y cada `Shortcut` aparece en alguna fila (la ayuda no
  se queda atrás cuando se añade una tecla).
- Compose (`WebtoonAndDualTest`): `S` arranca (aparece "autoscroll on" y la página baja sola), `S` para; la rueda
  y ↓ lo pausan; Ctrl+Shift+↑ sube la velocidad con aviso sin pausarlo; al final del último capítulo se para solo.
- Compose (`ReaderControlsTest` o nuevo): en modo páginas Ctrl+`+` amplía la página actual (escala > 1) con aviso,
  Ctrl+0 la devuelve a 1×; pasar de página la deja a 1×.
- Compose: F1 abre la ayuda en la biblioteca (ambos visores) y en el lector (solo el modo actual); Esc la cierra.
- Verificación visual con perfil aislado y un webtoon local (skill `verificacion-visual`).

## 5. Rama y entrega

`feat/reader-keyboard-8a`, desde `feat/reader-webtoon-quick-settings` (usa el aviso y las acciones con Ctrl del
PR #6). PR apilado sobre #6; se rebasa sobre `master` cuando se mergeen #4 y #6.

## 6. Prueba manual (para el usuario)

1. Abre un webtoon, pulsa `S`: la página baja sola y aparece el icono de reproducción. Ctrl+Shift+↑ dos veces:
   más rápido, con aviso. Gira la rueda: se para.
2. Abre un manga en modo páginas: Ctrl+`+` tres veces amplía (aviso "Zoom: 195 %"), Ctrl+0 vuelve a 100 %.
3. Pulsa F1 en la biblioteca y luego dentro del lector: la sección del lector cambia según el modo.
