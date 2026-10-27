# Subproyecto 8b: filtros de imagen del lector

## 1. Objetivo

Los filtros de color de Mihon en el lector de escritorio: brillo propio (atenuar), filtro de color con modo de mezcla,
escala de grises e invertir colores. Sirven para leer de noche o con páginas muy blancas.

Segundo de los subproyectos 8a–8e acordados el 2026-10-26.

### Fuera de alcance
- Subir el brillo de la pantalla: Windows no deja a una app cambiar el brillo del monitor. El brillo propio solo atenúa.
- Atajos de teclado para los filtros.
- Filtros por manga: son globales, como en Mihon.

## 2. Estado actual

- `ReaderPreferences` ya tiene `customBrightness`, `customBrightnessValue` (Mihon: −75…100), `colorFilter`,
  `colorFilterValue` (ARGB), `colorFilterMode` (índice en `ColorFilterMode`, ya portado con `BlendMode` de Compose),
  `grayscale` e `invertedColors`. Nada los lee en `desktop-app`.
- Textos de Mihon con traducción `es`: `pref_custom_brightness`, `pref_custom_color_filter`, `color_filter_r_value`…
  `color_filter_a_value`, `pref_color_filter_mode`, `pref_grayscale`, `pref_inverted_colors`, `custom_filter`.
- Mihon: `ReaderContentOverlay` (rectángulo negro con alfa para atenuar; rectángulo del color con su `BlendMode`) y,
  para grises e inversión, una `ColorMatrix` en la capa del visor.
- Escritorio: las páginas se dibujan con `Image` en `DecodedPageImage` (`viewer/PageContent.kt`).

## 3. Diseño

### A. Grises e inversión
- `readerColorMatrix(grayscale, inverted): ColorMatrix?` (nuevo `reader/ReaderColorFilter.kt`): saturación 0 para
  grises, la matriz de inversión de Mihon (−1 en la diagonal, +255 de desplazamiento) para invertir, las dos
  encadenadas si ambas; `null` si ninguna.
- `LocalPageColorFilter: ProvidableCompositionLocal<ColorFilter?>`; `ReaderContent` lo provee alrededor de los visores
  con `ColorFilter.colorMatrix(...)`. `DecodedPageImage` lo pasa como `colorFilter` a cada `Image` (páginas fijas,
  tiras y animadas). Solo cambian las páginas: el fondo del lector, las transiciones y los controles no.

### B. Brillo y filtro de color
- `ReaderContentOverlay(brightness, color, colorBlendMode)`: gemelo de Mihon en
  `desktop-app/.../eu/kanade/presentation/reader/ReaderContentOverlay.kt`, sin las anotaciones de Android y con
  `testTag("brightness overlay")` y `testTag("color overlay")`.
- `ReaderContent` lo dibuja encima de los visores y debajo de los controles y del aviso:
  - brillo `= customBrightnessValue.coerceAtMost(0)` si `customBrightness`, si no 0 (un valor positivo de un backup de
    Android no hace nada aquí);
  - color `= colorFilterValue` si `colorFilter`, con el `BlendMode` de `ColorFilterMode[colorFilterMode]`.

### C. Interfaz
En los ajustes rápidos del lector, grupo nuevo "Filtro personalizado" (`custom_filter`), en todos los modos:
- Chip "Utilizar brillo personalizado" y, si está activo, slider −75…0.
- Chip "Filtro de color personalizado" y, si está activo, sliders R, G, B, A (0…255) y los chips de modo de mezcla.
- Chips "Escala de grises" e "Invertir colores".
- El diálogo pasa a tener scroll vertical (con `keyboardScrollable`): ya no cabe en una ventana baja.

## 4. Pruebas
- `ReaderColorFilterTest`: `null` sin filtros; inversión con −1 y 255 donde toca; grises con las tres filas iguales.
- Compose (`ReaderControlsTest`): con brillo −50 aparece "brightness overlay" y sin él no; con filtro de color
  aparece "color overlay"; en los ajustes rápidos, activar el filtro de color muestra el slider "Rojo".
- Verificación visual: grises, inversión, atenuar y un filtro de color sobre un manga local.
