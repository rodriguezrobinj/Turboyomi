# Subproyecto 7: teclado y barra de scroll

## 1. Objetivo

Moverse por las listas y grids de Turboyomi sin depender de la rueda del ratón. Dos cosas:

1. Teclas que desplazan la lista activa (Re Pág, Av Pág, Espacio, Inicio, Fin, ↑, ↓).
2. Una barra de scroll siempre visible, que se arrastra y en cuyo carril se puede hacer clic.

Motivo (usuario, 2026-10-25): su ratón tiene una rueda libre que gira sin control, y hoy no hay otra forma
cómoda de recorrer una lista larga.

### Fuera de alcance
- Limitar o suavizar la rueda.
- Navegar entre elementos con flechas y Enter (resaltado de portada o capítulo).
- Barra en los `LazyColumn` sueltos y `verticalScroll` que no están en las pantallas de §4.
- El lector: ya tiene sus teclas (`ReaderAction`) y no cambia.

## 2. Estado actual

- Atajos de ventana en `main/Shortcut.kt`, enviados por `handleShortcut` en `Main.kt` (paso previo y posterior al
  componente enfocado). Ninguno desplaza listas.
- Compose no desplaza listas con el teclado. Tab y las flechas solo mueven el foco.
- `VerticalFastScroller` y `VerticalGridFastScroller` (presentation-core) ya tienen un pulgar arrastrable, pero
  solo aparece al desplazar y se desvanece (`alpha`), y solo se arrastra cuando ya es visible. Está pensado para táctil.
- `ScrollbarLazyColumn` (8 usos) solo dibuja la barra (`drawVerticalScrollbar`), sin arrastre.
- Contenedores con scroll: `LazyListState`, `LazyGridState` y `ScrollState`.

## 3. Diseño

### A. Registro de scroll por teclado
Nuevo archivo `presentation-core/.../util/KeyboardScroll.kt`:

- `KeyboardScrollTarget` con `perform(key: ScrollKey)` y `canScroll` (falso si la lista cabe entera o está vacía).
- Tres sobrecargas de `Modifier.keyboardScrollable`: `LazyListState`, `LazyGridState` y `ScrollState`.
  - Página = 0,9 del alto visible, medido por el propio modificador con `onSizeChanged`; línea = 48 dp; ambas con `animateScrollBy`.
  - Inicio y Fin: `scrollToItem(0)` y `scrollToItem(último)` en los lazy; `scrollTo(0)` y `scrollTo(maxValue)` en `ScrollState`.
- El modificador:
  - Se registra en `KeyboardScrollRegistry` al entrar en la composición y se da de baja al salir (`DisposableEffect`).
  - `onPointerEvent(Enter)` lo marca como activo.
- `KeyboardScrollRegistry`: pila de registrados. **Activo** = el último sobre el que pasó el puntero, si sigue
  registrado; si no, el último registrado (el de la pantalla más reciente). Un diálogo con lista se registra
  después, así que gana sobre la pantalla de debajo.

### B. Mapeo de teclas
Función pura `scrollKeyFor(key, ctrl, alt, shift): ScrollKey?` con `PageUp`, `PageDown`, `Home`, `End`, `LineUp`, `LineDown`.
Vive en presentation-core junto al registro (no en `main/`), para que `handleScrollKey` y sus tests no dependan de desktop-app.

| Tecla | Acción |
|---|---|
| Re Pág / Mayús+Espacio | `pageBy(-1)` |
| Av Pág / Espacio | `pageBy(+1)` |
| Inicio / Fin | `toStart` / `toEnd` |
| ↑ / ↓ | `lineBy(-1)` / `lineBy(+1)` |

Con Ctrl o Alt no hay mapeo (Alt+← sigue siendo Atrás). Mayús con cualquier tecla que no sea Espacio tampoco
mapea: es selección de texto.

### C. Despacho
- `Main.kt`: `handleShortcut` llama también a `scrollKeyFor`, solo en el paso **posterior** al componente
  enfocado (`beforeFocusedComponent = false`). Un campo de texto enfocado se queda con Inicio, Fin, flechas y Espacio.
- El handler lanza la acción en un `CoroutineScope` de la ventana y devuelve `true` solo si hay un objetivo activo.
- En el lector no hay nada registrado: el handler no consume y `ReaderScreen` sigue con sus teclas.
- **Decisión:** ↑ y ↓ desplazan siempre que ningún componente las consuma. Con esas dos flechas se pierde el
  movimiento de foco entre botones. Tab, Mayús+Tab, ← y → siguen igual. El handler de ventana no puede saber
  qué tiene el foco, así que no hay variante condicional.

### D. Barra de scroll
Cambios en `VerticalFastScroller` y `VerticalGridFastScroller` (la lógica está duplicada en los dos):

- **Siempre visible** cuando el contenido no cabe: el pulgar queda con `alpha = 0.5` en reposo, y 1 al desplazar, al pasar el puntero o al arrastrar. Sin desvanecimiento.
- **Carril:** una franja invisible de 18 dp que el scroller se reserva a la derecha: el contenido se mide sin ella, así que las filas y tarjetas nunca llegan al pulgar, que va centrado (sin fondo). El pulgar arranca bajo el padding superior de la lista (la barra de la app), también en las rejillas. Con hover el pulgar se ensancha (de 6 a 10 dp).
- **Arrastre:** `draggable` sin la condición de visibilidad actual.
- **Clic en el carril** fuera del pulgar: salta una página (0,9 del alto) hacia ese lado, repetido mientras se mantiene pulsado hasta que el pulgar alcanza el puntero.
- `ScrollbarLazyColumn` pasa a envolver el mismo scroller que `FastScrollLazyColumn`. `Scrollbar.kt` (`drawVerticalScrollbar`) se queda: solo lo usa su `@Preview`.
- Los componentes compartidos (`ScrollbarLazyColumn`, `FastScrollLazyColumn`, `FastScrollLazyVerticalGrid`) aplican `keyboardScrollable` por sí mismos.

## 4. Pantallas cubiertas

Ya cubiertas por los componentes compartidos (`FastScrollLazyColumn`, `ScrollbarLazyColumn`,
`FastScrollLazyVerticalGrid`): biblioteca (grid y lista), historial, actualizaciones, extensiones, próximos, ajustes
(`PreferenceScreen`), fuentes, migración, detalles de extensión y selectores de seguimiento.

Cambiadas en el plan: explorar (`BrowseSourceComfortableGrid`, `BrowseSourceCompactGrid`, `BrowseSourceList`),
`GlobalSearchScreen` y `SettingsTab` pasan a los componentes compartidos; la lista de capítulos de `MangaScreen` (dos
sitios), `CategoryScreen` y `DownloadsTab` llaman a `keyboardScrollable`. `LibraryPager` y `UpcomingScreen`
(`verticalScroll`) y los diálogos (filtros, `ChapterSettingsDialog`, `UpdateDialog`, etc.) quedan fuera: son cortos.

## 5. Pruebas

- `ScrollKeysTest`: mapeo puro tecla → acción, incluido que Ctrl/Alt no mapean.
- `KeyboardScrollRegistryTest`: el último con hover gana; si se da de baja, vuelve al último registrado.
- Test de UI en Compose: una lista de 200 elementos, Av Pág desplaza ~0,9 del alto, Fin llega al último, Inicio al primero.
- Test de UI: con un campo de texto enfocado, Inicio y Fin no desplazan la lista.
- Test de UI: ↓ desplaza una línea (48 dp); Espacio y Mayús+Espacio pasan página.
- Test de UI del pulgar: arrastrar mueve la lista; clic en el carril salta una página.

## 6. Prueba manual (para el usuario)

1. Biblioteca grande: Av Pág, Re Pág, Inicio y Fin, con el puntero encima.
2. Lista de capítulos de un manga con muchos capítulos: arrastrar el pulgar de punta a punta.
3. Clic en el carril, encima y debajo del pulgar.
4. Ajustes: con un diálogo de lista abierto, las teclas no mueven la pantalla de debajo. Si el diálogo es una
   ventana aparte, tampoco mueven la lista del diálogo: se anota, no se arregla.
5. Buscar en la biblioteca: Inicio y Fin mueven el cursor del campo, no la lista.
