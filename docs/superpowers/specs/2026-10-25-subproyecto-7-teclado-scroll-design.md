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
Nuevo archivo en presentation-core, `KeyboardScroll.kt`:

- `interface KeyboardScrollTarget { suspend fun pageBy(pages: Float); suspend fun lineBy(lines: Float); suspend fun toStart(); suspend fun toEnd() }`
- Tres fábricas: `LazyListState`, `LazyGridState` y `ScrollState`.
  - `pageBy` = 0,9 del alto visible; `lineBy` = 48 dp; ambos con `animateScrollBy`.
  - `toStart`/`toEnd`: `scrollToItem(0)` y `scrollToItem(último)` en los lazy; `scrollTo(0)` y `scrollTo(maxValue)` en `ScrollState`.
- `Modifier.keyboardScrollable(target)`:
  - Se registra en `KeyboardScrollRegistry` al entrar en la composición y se da de baja al salir (`DisposableEffect`).
  - `onPointerEvent(Enter)` lo marca como activo.
- `KeyboardScrollRegistry`: pila de registrados. **Activo** = el último sobre el que pasó el puntero, si sigue
  registrado; si no, el último registrado (el de la pantalla más reciente). Un diálogo con lista se registra
  después, así que gana sobre la pantalla de debajo.

### B. Mapeo de teclas
Nueva función pura en `main/ScrollKeys.kt`, junto a `shortcutFor`:

`scrollKeyFor(key, ctrl, alt, shift): ScrollKey?` con `PageUp`, `PageDown`, `Home`, `End`, `LineUp`, `LineDown`.

| Tecla | Acción |
|---|---|
| Re Pág / Mayús+Espacio | `pageBy(-1)` |
| Av Pág / Espacio | `pageBy(+1)` |
| Inicio / Fin | `toStart` / `toEnd` |
| ↑ / ↓ | `lineBy(-1)` / `lineBy(+1)` |

Con Ctrl o Alt no hay mapeo (Alt+← sigue siendo Atrás).

### C. Despacho
- `Main.kt`: `handleShortcut` llama también a `scrollKeyFor`, solo en el paso **posterior** al componente
  enfocado (`beforeFocusedComponent = false`). Un campo de texto enfocado se queda con Inicio, Fin, flechas y Espacio.
- El handler lanza la acción en un `CoroutineScope` de la ventana y devuelve `true` solo si hay un objetivo activo.
- En el lector no hay nada registrado: el handler no consume y `ReaderScreen` sigue con sus teclas.
- **Riesgo conocido:** Compose mueve el foco entre botones con las flechas. Si consumir ↑ y ↓ lo rompe, las
  flechas solo desplazan cuando el foco no está en un botón u otro elemento enfocable. Re Pág, Av Pág, Inicio, Fin y
  Espacio no se ven afectados. Se comprueba con un test (§5) antes de decidir.

### D. Barra de scroll
Cambios en `VerticalFastScroller` y `VerticalGridFastScroller` (la lógica está duplicada en los dos):

- **Siempre visible** cuando el contenido no cabe: el pulgar queda con `alpha = 0.5` en reposo, y 1 al desplazar, al pasar el puntero o al arrastrar. Sin desvanecimiento.
- **Carril:** una franja de 12 dp a la derecha, con fondo tenue. Con hover el pulgar y el carril se ensanchan (de 4 a 8 dp).
- **Arrastre:** `draggable` sin la condición de visibilidad actual.
- **Clic en el carril** fuera del pulgar: salta una página hacia ese lado con `pageBy(±1)`, repetido mientras se mantiene pulsado.
- `ScrollbarLazyColumn` pasa a envolver el mismo scroller que `FastScrollLazyColumn`, y se borra `drawVerticalScrollbar` si queda sin usos.
- Los componentes compartidos (`ScrollbarLazyColumn`, `FastScrollLazyColumn`, `FastScrollLazyVerticalGrid`) aplican `keyboardScrollable` por sí mismos.

## 4. Pantallas cubiertas

Ya quedan por los componentes compartidos: ajustes (`PreferenceScreen`), fuentes (`SourcesScreen`), migración
(`MigrateSourceScreen`), detalles de extensión, selectores de seguimiento y los grids con
`FastScrollLazyVerticalGrid`.

Se añade `keyboardScrollable` a mano (y barra, donde falte) en los contenedores sueltos de estas pantallas:
`LazyLibraryGrid`, `LibraryList`, `LibraryPager`, `BrowseSourceComfortableGrid`, `BrowseSourceCompactGrid`,
`BrowseSourceList`, `HistoryScreen`, `UpdatesScreen`, `ExtensionsScreen`, `GlobalSearchScreen`, `MangaScreen`
(lista de capítulos y cabecera), `DownloadsTab`, `UpcomingScreen`/`UpcomingScreenContent`, `CategoryScreen` y
`SettingsTab`. Las pantallas de diálogo (filtros, `ChapterSettingsDialog`, `UpdateDialog`, etc.) quedan fuera:
son cortas. El plan fija el orden y cierra la lista tras abrir cada archivo.

## 5. Pruebas

- `ScrollKeysTest`: mapeo puro tecla → acción, incluido que Ctrl/Alt no mapean.
- `KeyboardScrollRegistryTest`: el último con hover gana; si se da de baja, vuelve al último registrado.
- Test de UI en Compose: una lista de 200 elementos, Av Pág desplaza ~0,9 del alto, Fin llega al último, Inicio al primero.
- Test de UI: con un campo de texto enfocado, Inicio y Fin no desplazan la lista.
- Test de UI: ↑ y ↓ desplazan con la lista sin foco, y no rompen el foco entre dos botones (decide §3 C).
- Test de UI del pulgar: arrastrar mueve la lista; clic en el carril salta una página.

## 6. Prueba manual (para el usuario)

1. Biblioteca grande: Av Pág, Re Pág, Inicio y Fin, con el puntero encima.
2. Lista de capítulos de un manga con muchos capítulos: arrastrar el pulgar de punta a punta.
3. Clic en el carril, encima y debajo del pulgar.
4. Ajustes: teclas con un diálogo de lista abierto (las teclas van al diálogo, no a la pantalla de debajo).
5. Buscar en la biblioteca: Inicio y Fin mueven el cursor del campo, no la lista.
