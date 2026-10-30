# Subproyecto 10a: memoria del lector

## 1. Objetivo
Primero de 10a–10d (rendimiento, estabilidad y seguridad; auditoría del 2026-10-28). Que leer muchos capítulos, tiras
webtoon largas o GIF enormes no haga crecer la memoria sin límite hasta cerrar la app.

Evidencia de la auditoría:
- `DecodedPageCache` suelta las páginas fuera de su ventana, pero no cierra sus `Bitmap` de Skia. Sus píxeles son
  memoria nativa que solo se libera cuando el GC recoge el objeto Java, que pesa unos bytes y no le mete prisa: una
  tira de 800×30000 son ~96 MB nativos por página leída.
- `PageDecoder.still` deja vivos los bitmaps intermedios (el original antes de girar, la página entera tras partirla
  en tiras) hasta el GC.
- Un GIF/WebP animado se decodifica entero: 500 fotogramas de 1000×1500 son 3 GB.
- La caché cuenta páginas (2 atrás, 4 delante), no bytes: siete páginas enormes pasan del giga.
- Una imagen con cabecera de 30000×30000 (fuente rota o maliciosa) pide 3,6 GB antes de fallar.

### Fuera de alcance
- Reducir la resolución de las páginas normales: el zoom necesita sus píxeles.
- Decodificar los fotogramas de una animación a demanda.

## 2. Diseño
- **`DecodedPage` es `AutoCloseable`**: `close()` cierra sus bitmaps de Skia (`asSkiaBitmap`). `bytes` = ancho × alto ×
  4 (× fotogramas si es animada).
- **La caché es la dueña** de lo que decodifica el lector y cierra cada página cuando ya no la necesita nadie:
  - `put(key, page)` devuelve la página a mostrar; si ya había una con esa clave, cierra la nueva y devuelve la que
    había (dos decodificaciones a la vez de la misma página).
  - `show(key)`/`hide(key)`: cada `PageContent` en pantalla avisa (`DisposableEffect`). Una página que sale de la
    ventana mientras se ve no se cierra hasta su `hide`: cerrar lo que se está pintando rompería Skia.
  - `retainAround` además respeta un tope de **512 MB**: si la ventana pasa, suelta primero las más lejanas a la
    actual (nunca la actual).
  - `get` recupera también una página soltada que sigue en pantalla, sin volver a decodificarla.
  - `close()`: el lector la llama al salir o al cambiar de opciones (nueva caché); cierra todo lo que no está en
    pantalla y lo demás al ocultarse.
- **`PageDecoder`**:
  - Cierra los bitmaps intermedios (original antes de recortar o girar; la página entera tras partirla en tiras, que
    comparten píxeles con ella).
  - Rechaza (`UnreadableImageException`, el error con "Reintentar" de siempre) imágenes de más de **150 Mpx** (600 MB) antes
    de decodificarlas: el tamaño sale de la cabecera.
  - Una animación que ocupe más de **256 MB** se muestra quieta (su primer fotograma).

## 3. Pruebas
- `DecodedPageCacheTest`: las páginas soltadas se cierran; una que se ve no se cierra hasta ocultarse; `put` repetido
  conserva la primera y cierra la segunda; tope de bytes suelta la más lejana y nunca la actual; `close()`.
- `PageDecoderTest`: imagen de cabecera enorme rechazada sin decodificar; animación grande sale quieta; animación
  pequeña sigue animada; tiras y giro siguen igual.
- Las pruebas del lector (`ReaderTest`, visores) siguen en verde.
