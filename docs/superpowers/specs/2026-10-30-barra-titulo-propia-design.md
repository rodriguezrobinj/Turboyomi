# Barra de título propia en Windows 10

## 1. Objetivo
En Windows 10 la barra de título de la ventana principal sale negra (o blanca), no del color del tema:
`DwmSetWindowAttribute(DWMWA_CAPTION_COLOR)` da `E_INVALIDARG` antes de Windows 11. La app dibuja allí su propia barra,
del color `surface` del tema, sin perder lo que da la barra de Windows: arrastrar, Aero Snap, doble clic para
maximizar, menú del sistema (clic derecho y Alt+Espacio), cambio de tamaño por los cuatro bordes y la sombra.

En Windows 11 no cambia nada: la barra nativa ya toma el color del tema (`ThemedTitleBar`) y conserva el menú de
diseños de ajuste del botón maximizar.

## 2. Diseño
- **Cuándo:** solo si `os.name` es `Windows 10`. Desde el JDK 18 Windows 11 se llama `Windows 11`, y es la primera
  versión que acepta `DWMWA_CAPTION_COLOR`. Probar el atributo en vivo no sirve: `ThemedTitleBar` lo pone a la vez y un
  valor de prueba podría pisar el color del tema.
- **Marco nativo sin barra** (`mihon.desktop.app.platform.CustomTitleBar`, JNA): se reemplaza el procedimiento de la
  ventana (`SetWindowLongPtr(GWLP_WNDPROC)`), que reenvía todo al original de AWT salvo:
  - `WM_NCCALCSIZE`: llama al original y devuelve la parte de arriba del área cliente a donde estaba. Desaparece la
    barra y quedan los bordes laterales e inferior, la sombra y Aero Snap. Maximizada, la ventana sale de la pantalla
    por el grosor del marco, que se suma arriba para que el contenido no quede cortado. El grosor sale de
    `GetSystemMetricsForDpi(SM_CYFRAME) + GetSystemMetricsForDpi(SM_CXPADDEDBORDER)` con el DPI de la ventana. AWT
    calcula sus insets desde el área cliente real, así que el contenido de Compose ocupa también la franja de arriba.
  - `WM_NCHITTEST`: si el original da `HTCLIENT`, decide la función pura `titleBarHit`.
  - Al instalar, `SetWindowPos(SWP_FRAMECHANGED)` recalcula el marco.
- **Lienzo de Compose:** el lienzo de Skia es una ventana hija que tapa toda el área cliente, y Windows le pregunta a
  ella por el ratón. Su procedimiento también se reemplaza: donde `titleBarHit` no da `HTCLIENT`, devuelve
  `HTTRANSPARENT` y Windows pregunta a la ventana principal. Ella contesta `HTCAPTION` o `HTTOP*`, y desde ahí Windows
  hace el resto: mover, ajustar, doble clic, menú del sistema.
- **`titleBarHit(x, y, width, geometry, maximized)`**, en píxeles del área cliente:
  - Franja superior del alto del marco, solo sin maximizar: `HTTOPLEFT`, `HTTOP` o `HTTOPRIGHT` según la x (las
    esquinas, del mismo ancho que el marco).
  - Dentro del alto de la barra y fuera de los botones: `HTCAPTION`.
  - El resto: `HTCLIENT`.
- **La barra** (`WindowTitleBar`, Compose): fila de 32 dp con el icono de la app (16 dp), el título y los botones
  minimizar, maximizar o restaurar y cerrar (46 × 32 dp, como Windows 10). Fondo `surface`, texto e iconos `onSurface`.
  Con la ventana inactiva, el texto y los iconos bajan al 60 % de opacidad. Al pasar el ratón, el botón cerrar se pone
  rojo (`#E81123`) con el icono blanco; los otros, `onSurface` al 8 %. Los botones tienen descripción para lectores de
  pantalla: `MR.strings` nuevos `desktop_window_minimize`, `desktop_window_maximize` y `desktop_window_restore`, con
  traducción al español. Cerrar reutiliza `action_close`. La barra publica su alto y la zona de los botones en
  `CustomTitleBar` con `onGloballyPositioned` (un valor `@Volatile` inmutable, porque el procedimiento de ventana corre
  en el hilo de AWT).
- **Dónde:** en `Main.kt`, encima del contenido y de la pantalla de desbloqueo, fuera del `key(restarts)`. Así se puede
  mover y cerrar la ventana bloqueada o tras un fallo. Va dentro de su propio `AppProviders`, como `UnlockScreen`. En
  pantalla completa (`windowControls.isFullscreen`) no se dibuja. `CustomTitleBar` deja pasar los mensajes sin tocarlos
  si la ventana no tiene `WS_CAPTION`, que es como la deja `BorderlessFullscreen`.
- **Ventana del navegador integrado:** lleva la misma barra (`AppTitleBar`, que instala `CustomTitleBar` en su ventana y
  dibuja `WindowTitleBar`). Cada ventana tiene su propia instancia de `CustomTitleBar` con su geometría.
- **Fuera de alcance:** no hay línea de borde arriba: Windows 10 dibuja 1 px de borde a los lados y abajo, y arriba queda la barra. Si el lienzo de Skia se recrea
  (cambio de renderizador), la barra deja de arrastrar hasta reiniciar; los botones siguen funcionando.

## 3. Pruebas
- `TitleBarHitTest`: bordes y esquinas sin maximizar, sin franja de borde maximizada, barra, botones y contenido.
- `WindowTitleBarTest` (Compose): cada botón llama a su acción, maximizar cambia a restaurar según el estado, y la
  geometría publicada coincide con la barra y sus botones.
- A mano en Windows 10 (skill `verificacion-visual`, perfil aislado): color en tema claro y oscuro, arrastrar, Aero
  Snap a un lado, doble clic, clic derecho en la barra, cambio de tamaño por los cuatro bordes y esquinas, maximizar y
  restaurar sin contenido cortado, pantalla completa y vuelta, y la ventana inactiva.
