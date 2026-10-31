# Barra de título propia en Windows 10: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En Windows 10 la ventana principal dibuja su barra de título con el color del tema y conserva el comportamiento
de la barra de Windows.

**Architecture:** Una función pura (`titleBarHit`) decide qué es cada píxel de la franja superior. `CustomTitleBar`
(JNA) reemplaza el procedimiento de la ventana y del lienzo de Compose para quitar la barra nativa y contestar a
`WM_NCHITTEST` con esa función. `WindowTitleBar` (Compose) dibuja la barra y publica su geometría. `Main.kt` lo une
solo cuando `os.name` es `Windows 10`.

**Tech Stack:** Kotlin, Compose Desktop, JNA 5.17 (`jna-platform`), moko-resources, JUnit 5 + Kotest.

**Spec:** `docs/superpowers/specs/2026-10-30-barra-titulo-propia-design.md`

## Global Constraints

- Solo Windows 10 (`System.getProperty("os.name") == "Windows 10"`); Windows 11 no cambia.
- Textos en `MR.strings` con traducción `es` (`SpanishStringsTest`).
- Barra de 32 dp; botones de 46 × 32 dp; cerrar con hover `#E81123` e icono blanco.
- Nunca lanzar la app sin `TURBOYOMI_HOME` (skill `perfil-aislado`).

## Review Focus

- Ventana maximizada: el contenido no debe quedar cortado arriba (prueba a mano, tarea 4).
- Pantalla completa (F11) y vuelta: sin barra propia en pantalla completa y con barra al volver (a mano, tarea 4).
- Pantalla de bloqueo o pantalla de fallo: la barra sigue y deja mover y cerrar (a mano, tarea 4).
- Escala de pantalla distinta de 100 %: la franja de cambio de tamaño y la zona de arrastre coinciden con lo dibujado
  (`titleBarHit` trabaja en píxeles físicos; la geometría de Compose también; a mano si hay monitor a otra escala).
- Esquinas superiores: cambian el tamaño en diagonal (`TitleBarHitTest`, tarea 1).

---

### Task 1: `titleBarHit`

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/TitleBarHit.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/platform/TitleBarHitTest.kt`

**Interfaces:**
- Produces: `data class TitleBarGeometry(val height: Int, val buttonsStart: Int)`;
  `fun titleBarHit(x: Int, y: Int, width: Int, bar: TitleBarGeometry?, border: Int, maximized: Boolean): Int`;
  constantes `internal const val HTCLIENT = 1, HTCAPTION = 2, HTTOP = 12, HTTOPLEFT = 13, HTTOPRIGHT = 14`.

- [ ] **Step 1: prueba que falla**

```kotlin
class TitleBarHitTest {
    private val bar = TitleBarGeometry(height = 32, buttonsStart = 862)

    private fun hit(x: Int, y: Int, maximized: Boolean = false) =
        titleBarHit(x, y, width = 1000, bar = bar, border = 8, maximized = maximized)

    @Test
    fun `the top edge resizes, diagonally at its ends`() {
        hit(500, 0) shouldBe HTTOP
        hit(500, 7) shouldBe HTTOP
        hit(3, 2) shouldBe HTTOPLEFT
        hit(995, 2) shouldBe HTTOPRIGHT
        // Over the buttons too, as on Windows' own bar
        hit(950, 3) shouldBe HTTOP
    }

    @Test
    fun `maximized there's no edge to resize from`() {
        hit(500, 0, maximized = true) shouldBe HTCAPTION
        hit(950, 0, maximized = true) shouldBe HTCLIENT
    }

    @Test
    fun `the bar moves the window and leaves its buttons and the content to the app`() {
        hit(500, 8) shouldBe HTCAPTION
        hit(861, 31) shouldBe HTCAPTION
        hit(862, 20) shouldBe HTCLIENT
        hit(500, 32) shouldBe HTCLIENT
    }

    @Test
    fun `before the bar is laid out only the top edge is Windows'`() {
        titleBarHit(500, 20, width = 1000, bar = null, border = 8, maximized = false) shouldBe HTCLIENT
    }
}
```

- [ ] **Step 2:** `./gradlew :desktop-app:test --tests "*TitleBarHitTest"` → falla al compilar.
- [ ] **Step 3: implementación**

```kotlin
fun titleBarHit(x: Int, y: Int, width: Int, bar: TitleBarGeometry?, border: Int, maximized: Boolean): Int = when {
    !maximized && y < border -> when {
        x < border -> HTTOPLEFT
        x >= width - border -> HTTOPRIGHT
        else -> HTTOP
    }
    bar != null && y < bar.height && x < bar.buttonsStart -> HTCAPTION
    else -> HTCLIENT
}
```

- [ ] **Step 4:** la misma orden → pasa.
- [ ] **Step 5:** commit `feat: hit test for the app's own title bar`.

### Task 2: `CustomTitleBar` (JNA)

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/CustomTitleBar.kt`

**Interfaces:**
- Consumes: `titleBarHit`, `TitleBarGeometry`, constantes `HT*` (tarea 1).
- Produces: `object CustomTitleBar { val needed: Boolean; @Volatile var geometry: TitleBarGeometry?; fun install(window: java.awt.Window) }`.

- [ ] **Step 1:** escribir el objeto:
  - `needed = System.getProperty("os.name") == "Windows 10"`.
  - `install`: `HWND` de la ventana; `subclass(frame, frameProc)`; `EnumChildWindows(frame)` → `subclass(child, canvasProc)`;
    `SetWindowPos(SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE)`.
  - `subclass`: lee el procedimiento original con `GetWindowLongPtr(GWLP_WNDPROC)` **antes** de `SetWindowLongPtr`
    (la ventana corre en el hilo de AWT, que puede llamar al nuevo en cuanto se instala). Guarda el callback en una
    lista para que JNA no lo libere. Si el procedimiento lanza, registra en el log y llama al original.
  - `frameProc`, solo si la ventana tiene `WS_CAPTION` (si no, todo al original):
    - `WM_NCCALCSIZE` con `wParam != 0`: guarda `rgrc[0].top` (offset 4 del puntero `lParam`), llama al original y lo
      repone; si `IsZoomed`, le suma el grosor del marco.
    - `WM_NCHITTEST`: llama al original; si da `HTCLIENT`, devuelve `hit(frame, lParam)`.
  - `canvasProc`: `WM_NCHITTEST` con `WS_CAPTION` en la ventana principal y `hit(frame, lParam) != HTCLIENT` →
    `HTTRANSPARENT` (-1). Lo demás, al original.
  - `hit`: coordenadas con signo de `lParam` → `ScreenToClient(frame)` → `titleBarHit(x, y, clientRect.right,
    geometry, border(frame), IsZoomed(frame))`.
  - `border(hwnd)`: `GetSystemMetricsForDpi(SM_CYFRAME = 33) + GetSystemMetricsForDpi(SM_CXPADDEDBORDER = 92)` con
    `GetDpiForWindow`.
  - Interfaz JNA propia (`user32`, `W32APIOptions.DEFAULT_OPTIONS`): `GetWindowLongPtr`, `SetWindowLongPtr(hwnd, index,
    WinUser.WindowProc)`, `CallWindowProc`, `ScreenToClient`, `IsZoomed`, `GetDpiForWindow`, `GetSystemMetricsForDpi`.
- [ ] **Step 2:** `./gradlew :desktop-app:compileKotlin` → compila. Se prueba a mano en la tarea 4 (no hay ventana
  nativa en las pruebas).
- [ ] **Step 3:** commit `feat: the window's frame without Windows' title bar on Windows 10`.

### Task 3: `WindowTitleBar` (Compose) y textos

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/platform/WindowTitleBar.kt`
- Modify: `i18n/src/commonMain/moko-resources/base/strings.xml`, `.../es/strings.xml` (junto a `desktop_tray_hint`)
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/platform/WindowTitleBarTest.kt`

**Interfaces:**
- Consumes: `TitleBarGeometry`.
- Produces: `@Composable fun WindowTitleBar(title: String, icon: Painter, active: Boolean, maximized: Boolean,
  onMinimize: () -> Unit, onToggleMaximize: () -> Unit, onClose: () -> Unit, onGeometry: (TitleBarGeometry) -> Unit)`.

- [ ] **Step 1:** textos: `desktop_window_minimize` Minimize / Minimizar, `desktop_window_maximize` Maximize /
  Maximizar, `desktop_window_restore` Restore down / Restaurar.
- [ ] **Step 2: prueba que falla**

```kotlin
@OptIn(ExperimentalTestApi::class)
class WindowTitleBarTest {
    @Test
    fun `each button does its action and maximize turns into restore`() = runComposeUiTest {
        val graph = startTestApp()
        val done = mutableListOf<String>()
        var maximized by mutableStateOf(false)
        var geometry: TitleBarGeometry? = null
        setContent {
            AppProviders(graph) {
                WindowTitleBar(
                    title = "Turboyomi",
                    icon = ColorPainter(Color.Red),
                    active = true,
                    maximized = maximized,
                    onMinimize = { done += "minimize" },
                    onToggleMaximize = { done += "maximize"; maximized = !maximized },
                    onClose = { done += "close" },
                    onGeometry = { geometry = it },
                )
            }
        }
        onNodeWithText("Turboyomi").assertExists()
        onNodeWithContentDescription(str(MR.strings.desktop_window_minimize)).performClick()
        onNodeWithContentDescription(str(MR.strings.desktop_window_maximize)).performClick()
        onNodeWithContentDescription(str(MR.strings.desktop_window_restore)).performClick()
        onNodeWithContentDescription(str(MR.strings.action_close)).performClick()
        done shouldBe listOf("minimize", "maximize", "maximize", "close")
    }

    @Test
    fun `the geometry is the bar's height and where its buttons start`() = runComposeUiTest { ... }
}
```

  La segunda prueba compara `geometry` con `TitleBarGeometry(32.dp en px, left del nodo minimizar)`.
- [ ] **Step 3:** `./gradlew :desktop-app:test --tests "*WindowTitleBarTest"` → falla al compilar.
- [ ] **Step 4:** implementación: `Row` de 32 dp con fondo `surface`; `Image(icon)` 16 dp; `Text(title)` en
  `bodySmall`; fila de botones con `onGloballyPositioned { onGeometry(TitleBarGeometry(it.size.height,
  it.positionInWindow().x.toInt())) }`. `CaptionButton` 46 × 32 dp con `hoverable`, `clickable(role = Role.Button)`,
  `contentDescription` y un `Canvas` de 10 dp que dibuja el glifo con trazo de 1 dp (línea, cuadrado, dos cuadrados,
  aspa). Color `onSurface` al 60 % con la ventana inactiva; hover cerrar `#E81123` e icono blanco, otros `onSurface` 8 %.
- [ ] **Step 5:** la misma orden → pasa; `./gradlew :i18n:test` o la prueba `SpanishStringsTest` → pasa.
- [ ] **Step 6:** commit `feat: the app's own title bar, drawn in the theme's colors`.

### Task 4: unir en `Main.kt` y verificar en vivo

**Files:**
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt:270-332`

- [ ] **Step 1:** dentro de `Window { }`:

```kotlin
var ownTitleBar by remember { mutableStateOf(false) }
LaunchedEffect(Unit) {
    if (CustomTitleBar.needed) {
        // After a frame Compose's canvas has its native window, which install subclasses too
        withFrameNanos { }
        CustomTitleBar.install(window)
        ownTitleBar = true
    }
}
```

  y el contenido pasa a `Column(Modifier.fillMaxSize())` con la barra (en su propio `AppProviders`, oculta si
  `windowControls.isFullscreen`) y debajo el `Box(Modifier.weight(1f).fillMaxWidth())` de siempre. Acciones:
  `windowState.isMinimized = true`; alternar `windowState.placement` entre `Maximized` y `Floating`;
  `window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))`; `CustomTitleBar.geometry = it`.
  `active = LocalWindowInfo.current.isWindowFocused`.
- [ ] **Step 2:** `./gradlew :desktop-app:appImage`; lanzar con perfil aislado (skill `perfil-aislado`) y revisar con
  `verificacion-visual` la lista de la spec §3.
- [ ] **Step 3:** commit `feat: the main window uses its own title bar on Windows 10`.

### Task 5: cierre

- [ ] `./gradlew spotlessCheck test --continue`, `scripts/check-*.sh`, `graphify update .`.
- [ ] PR a `master`, merge con `--rebase` cuando `build` esté en verde.
