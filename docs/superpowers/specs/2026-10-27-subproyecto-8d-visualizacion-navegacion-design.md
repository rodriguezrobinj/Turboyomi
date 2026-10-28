# Subproyecto 8d: visualización y navegación del lector

## 1. Objetivo
Tres ajustes de Mihon que el lector de escritorio no aplicaba: mostrar u ocultar el número de página, la transición
entre capítulos siempre visible o no, y las zonas de toque configurables (con su inversión). Cuarto de 8a–8e.

### Fuera de alcance
- La capa que dibuja las zonas sobre la página al elegirlas (`showNavigationOverlay*` de Mihon).
- Leer con toque largo o con las teclas de volumen (sin equivalente en escritorio).

## 2. Diseño
- **Número de página** (`showPageNumber`): `ReaderControls` solo dibuja el indicador "N / M" si está activo.
- **Transición entre capítulos** (`alwaysShowChapterTransition`, por defecto activa): sin ella, `viewerItems` omite la
  transición hacia un capítulo vecino ya cargado sin capítulos que falten entre medias (`calculateChapterGap`); se
  conserva hacia un vecino sin cargar, con huecos, y al principio y final del manga.
- **Zonas de toque** (`navigationModePager` / `navigationModeWebtoon`, índice en `ReaderPreferences.TapZones`;
  `pagerNavInverted` / `webtoonNavInverted`): `TapNavigation(mode, invert)` y
  `tapActionFor(x, y, width, height, mode, navigation)`. "Original" conserva los tercios del escritorio (izquierda y
  derecha según el sentido de lectura); L, estilo Kindle, borde y derecha e izquierda son las regiones de Mihon; fuera de
  ellas, o con "Desactivado", el clic muestra los controles. Invertir refleja horizontal, vertical o ambos.
  `PageContent` pasa ahora la posición y el tamaño del clic; los visores leen `LocalViewerSettings`.
- **Interfaz:** en los ajustes rápidos, el grupo "Visualización" gana "Mostrar el número de página" y "Hacer transición
  entre capítulos"; grupo nuevo "Zonas de toque" con los seis modos e "Invertir zonas de toque" (salvo con
  "Desactivado"). Sin strings nuevos.

## 3. Pruebas
- `ReaderInputTest`: cada modo de zonas, derecha e izquierda en ambos sentidos, desactivado, inversión horizontal y
  vertical, y "Original" igual que antes.
- `ViewerItemsTest`: sin la transición, vecino cargado sin hueco sin transición; con hueco y al final, con transición;
  vecino sin cargar, con transición.
