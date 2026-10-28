# Subproyecto 8c: páginas anchas y bordes

## 1. Objetivo

Tres opciones de Mihon que el lector de escritorio aún no tiene, en los modos por páginas y webtoon:

1. **Recortar bordes** (`cropBorders` en páginas, `cropBordersWebtoon` en webtoon): quitar los márgenes blancos o
   negros de cada página.
2. **Partir las páginas anchas** (`dualPageSplitPaged` / `dualPageSplitWebtoon`, con `dualPageInvert*`): una página
   más ancha que alta (un escaneo de doble página) se lee como dos mitades, en el sentido de lectura.
3. **Girar las páginas anchas** (`dualPageRotateToFit` / `dualPageRotateToFitWebtoon`, con `*Invert*`): una página ancha
   se gira 90° para ocupar la pantalla.

Tercero de los subproyectos 8a–8e (2026-10-26).

### Fuera de alcance
- Zoom en una mitad de página partida (la mitad se dibuja sin el `zoomable`).
- Partir o girar páginas animadas (GIF): se muestran tal cual.
- Recortar bordes de colores que no sean casi blanco o casi negro.

## 2. Estado actual
- Las preferencias existen; nada las lee en escritorio. El recorte de Mihon lo hace su decodificador nativo y el
  partido/giro su `ImageUtil` con `android.graphics`: nada reutilizable aquí.
- Escritorio: `PageDecoder.decode(bytes)` (Skia) da `DecodedPage.Still` (tiras) o `Animated`; `DecodedPageCache` guarda
  por `PageKey`. `PagerViewer` arma `Spread`s (`pairPages` en doble página, una por página si no); la clave de un ítem
  incluye su `Spread`. `WebtoonViewer` dibuja una `PageContent` por página.
- Textos de Mihon con `es`: `pref_crop_borders`, `pref_dual_page_split`, `pref_dual_page_invert`, `pref_page_rotate`,
  `pref_page_rotate_invert`, `pref_category_display`.

## 3. Diseño

### A. Recorte y giro al decodificar
- `DecodeOptions(crop: Boolean = false, rotation: Int = 0)`; `PageDecoder.decode(bytes, options)`. Solo imágenes fijas.
- `contentBounds(width, height, pixel): IntRect` (puro, `image/ContentBounds.kt`): el color del borde es el del píxel
  de la esquina si es casi blanco o casi negro (cada canal ≥ 230 o ≤ 25); se quitan filas y columnas desde cada borde
  mientras todos sus píxeles (muestreados cada 4) estén a ≤ 25 por canal de ese color. Si lo que queda es menos del
  25 % del área, no se recorta (página casi vacía).
- `wideRotation(width, height, rotate, invert): Int`: 0 si no está activo o la página no es ancha; 90 (o −90 con
  invertir) si lo es. El giro se hace con un `Canvas` de Skia sobre un `Bitmap` nuevo, después del recorte.
- `ReaderContent` calcula las opciones del modo actual y las da por `LocalDecodeOptions`; la caché de páginas se
  rehace al cambiar (`remember(options) { DecodedPageCache() }`) y `PageContent` las incluye en la clave de su
  decodificación. Partir tiene prioridad sobre girar: con las dos activas, no se gira.

### B. Partir en el modo por páginas
- `Spread.Single(page, half: Half? = null)`, `enum class Half { Left, Right }`.
- `splitPages(count, isWide, halves: List<Half>)`: cada página ancha da dos `Single` con su mitad en `halves`
  (orden de lectura); las demás, una. `readingHalves(rightToLeft, invert)`: `[Left, Right]` de izquierda a derecha,
  `[Right, Left]` de derecha a izquierda, al revés con invertir.
- `PagerViewer` usa `splitPages` cuando partir está activo y no hay doble página (con doble página, la página ancha ya
  va sola). `PageContent(half = ...)` dibuja esa mitad con un `Canvas` (`drawImage` con `srcOffset/srcSize`), centrada y
  ajustada a la pantalla, con el filtro de color del 8b.

### C. Partir en webtoon
- La página ancha se dibuja como dos `PageContent` de su mitad, una debajo de otra en el orden de lectura (webtoon lee
  de arriba abajo; `invert` las cambia). Cada mitad toma `aspectRatio((w/2)/h)`.

### D. Interfaz
Ajustes rápidos, grupo "Visualización" (`pref_category_display`), según el modo: chips "Recortar bordes",
"Partir en dos las páginas anchas" (+ "Dividir… en la dirección contraria" si está activo) y "Girar las páginas anchas…"
(+ "…en la dirección opuesta"). Activar partir apaga girar y al revés, como en Mihon.

## 4. Pruebas
- `ContentBoundsTest`: margen blanco y negro recortados; página sin margen intacta; página casi vacía intacta; color de
  borde que no es blanco ni negro intacto.
- `WidePagesTest`: `readingHalves` en los cuatro casos; `splitPages` con una página ancha en medio; `wideRotation`.
- `PageDecoderTest` (o en el mismo): una imagen ancha girada sale alta; con recorte sale más pequeña.
- Compose (`ReaderControlsTest`): con partir activo y una página ancha, el visor tiene un ítem más y la tecla → pasa a la
  segunda mitad de la misma página.
- Verificación visual con un manga local que tiene una página doble y márgenes blancos.
