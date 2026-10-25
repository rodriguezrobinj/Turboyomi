# Subproyecto 7: teclado y barra de scroll — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Las listas y grids de Turboyomi se recorren con Re Pág, Av Pág, Espacio, Inicio, Fin, ↑ y ↓, y tienen una barra de scroll siempre visible, arrastrable y con clic en el carril.

**Architecture:** Un registro (`KeyboardScrollRegistry`) en presentation-core guarda las listas compuestas; el modificador `keyboardScrollable(state)` registra cada una y marca la que está bajo el puntero. El handler de ventana de `Main.kt`, en el paso posterior al componente enfocado, manda la tecla a la lista activa. `VerticalFastScroller` y `VerticalGridFastScroller` comparten un `ScrollerBar` nuevo (carril + pulgar siempre visibles, arrastre, clic en el carril).

**Tech Stack:** Kotlin, Compose Multiplatform Desktop 1.12.2, JUnit 5 + kotest + `runComposeUiTest`.

**Spec:** `docs/superpowers/specs/2026-10-25-subproyecto-7-teclado-scroll-design.md`

## Global Constraints

- Esc / Alt+← siguen siendo Atrás; con Ctrl o Alt no hay mapeo de scroll (spec §3 B).
- Las teclas de scroll van solo en el paso **posterior** al componente enfocado (`beforeFocusedComponent = false`): un campo de texto enfocado se queda con Inicio, Fin, flechas y Espacio (spec §3 C).
- El lector no cambia: no registra nada y `ReaderScreen` sigue con `readerActionFor` (spec §1).
- Página = 0,9 del alto visible; línea = 48 dp (spec §3 A).
- Carril siempre visible cuando el contenido no cabe; el pulgar se ensancha con el puntero; clic en el carril salta una página, repetido mientras se mantiene pulsado (spec §3 D).
- Pruebas de UI con `runComposeUiTest`, JUnit 5 y kotest (`shouldBe`), como el resto del repo.
- Commits con prefijo `feat:`/`test:`/`docs:` y la línea `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` al final.
- Antes de cada commit: `./gradlew :presentation-core:spotlessApply :desktop-app:spotlessApply`.

## Review Focus

1. Campo de texto enfocado: Inicio, Fin y flechas se quedan en el campo y la lista no se mueve (Task 1).
2. Una lista que sale de la composición (pantalla cerrada con Atrás) ya no recibe teclas (Task 1).
3. Lista que cabe entera, o vacía: las teclas no se consumen y Fin con 0 elementos no falla (Task 1).
4. Pulsaciones rápidas y repetidas de Av Pág: sin excepción, y la lista acaba quieta (Task 1).
5. Clic en el carril sobre el pulgar no salta, y soltar corta la repetición (Task 3).

## Estructura de archivos

| Archivo | Acción | Responsabilidad |
|---|---|---|
| `presentation-core/src/main/java/tachiyomi/presentation/core/util/KeyboardScroll.kt` | Crear | `ScrollKey`, `scrollKeyFor`, `KeyboardScrollTarget`, `KeyboardScrollRegistry`, `keyboardScrollable`, `handleScrollKey` |
| `presentation-core/src/test/kotlin/tachiyomi/presentation/core/util/ScrollKeysTest.kt` | Crear | Mapeo puro tecla → acción |
| `presentation-core/src/test/kotlin/tachiyomi/presentation/core/util/KeyboardScrollTest.kt` | Crear | Registro, teclas y componentes compartidos en UI |
| `presentation-core/src/main/java/tachiyomi/presentation/core/components/LazyList.kt` | Modificar | `ScrollbarLazyColumn` delega en `FastScrollLazyColumn`; ambos con `keyboardScrollable` |
| `presentation-core/src/main/java/tachiyomi/presentation/core/components/LazyGrid.kt` | Modificar | `keyboardScrollable` en la grid |
| `presentation-core/src/main/java/tachiyomi/presentation/core/components/VerticalFastScroller.kt` | Modificar | `ScrollerBar` compartido; se quita el desvanecimiento |
| `presentation-core/src/test/kotlin/tachiyomi/presentation/core/components/ScrollerBarTest.kt` | Crear | Alpha en reposo, arrastre y clic en el carril |
| `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt` | Modificar | `handleShortcut` llama a `handleScrollKey` |
| 8 archivos de pantallas (Task 4) | Modificar | Pasan a los componentes compartidos o llaman a `keyboardScrollable` |
| `docs/superpowers/specs/2026-10-25-subproyecto-7-teclado-scroll-design.md` | Modificar | Decisiones tomadas en el plan (Task 5) |

---

### Task 1: Registro y teclas de scroll

**Files:**
- Create: `presentation-core/src/main/java/tachiyomi/presentation/core/util/KeyboardScroll.kt`
- Test: `presentation-core/src/test/kotlin/tachiyomi/presentation/core/util/ScrollKeysTest.kt`
- Test: `presentation-core/src/test/kotlin/tachiyomi/presentation/core/util/KeyboardScrollTest.kt`

**Interfaces:**
- Produces (todo en el paquete `tachiyomi.presentation.core.util`):
  - `enum class ScrollKey { PageUp, PageDown, Home, End, LineUp, LineDown }`
  - `fun scrollKeyFor(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean): ScrollKey?`
  - `class KeyboardScrollTarget` con `val canScroll: Boolean` y `suspend fun perform(key: ScrollKey)`
  - `object KeyboardScrollRegistry { fun dispatch(key: ScrollKey, scope: CoroutineScope): Boolean }` más `internal fun clear()` para los tests
  - `@Composable fun Modifier.keyboardScrollable(state: LazyListState / LazyGridState / ScrollState): Modifier`
  - `fun handleScrollKey(event: KeyEvent, scope: CoroutineScope): Boolean`
  - `internal const val PAGE_FRACTION = 0.9f` (lo usa también el scroller en Task 3)

- [ ] **Step 1: Escribir el test del mapeo**

`presentation-core/src/test/kotlin/tachiyomi/presentation/core/util/ScrollKeysTest.kt`:

```kotlin
package tachiyomi.presentation.core.util

import androidx.compose.ui.input.key.Key
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScrollKeysTest {
    private fun key(key: Key, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
        scrollKeyFor(key, ctrl, alt, shift)

    @Test
    fun `page, home, end and line keys`() {
        key(Key.PageUp) shouldBe ScrollKey.PageUp
        key(Key.PageDown) shouldBe ScrollKey.PageDown
        key(Key.MoveHome) shouldBe ScrollKey.Home
        key(Key.MoveEnd) shouldBe ScrollKey.End
        key(Key.DirectionUp) shouldBe ScrollKey.LineUp
        key(Key.DirectionDown) shouldBe ScrollKey.LineDown
    }

    @Test
    fun `space pages down and shift+space pages up`() {
        key(Key.Spacebar) shouldBe ScrollKey.PageDown
        key(Key.Spacebar, shift = true) shouldBe ScrollKey.PageUp
    }

    @Test
    fun `ctrl and alt combinations belong to other shortcuts`() {
        key(Key.PageDown, ctrl = true) shouldBe null
        key(Key.DirectionDown, alt = true) shouldBe null
        key(Key.MoveEnd, ctrl = true) shouldBe null
        key(Key.Spacebar, alt = true) shouldBe null
    }

    @Test
    fun `shift on other keys stays with text selection, left and right are not scroll keys`() {
        key(Key.MoveEnd, shift = true) shouldBe null
        key(Key.DirectionDown, shift = true) shouldBe null
        key(Key.DirectionLeft) shouldBe null
        key(Key.DirectionRight) shouldBe null
        key(Key.A) shouldBe null
    }
}
```

- [ ] **Step 2: Escribir el test de UI**

`presentation-core/src/test/kotlin/tachiyomi/presentation/core/util/KeyboardScrollTest.kt`:

```kotlin
package tachiyomi.presentation.core.util

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.ScrollbarLazyColumn

private const val FOCUS_TAG = "focus"

/** The window's key handler in miniature: the key reaches [handleScrollKey] only when the focused node ignores it. */
@Composable
private fun Host(focusTarget: @Composable (FocusRequester) -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val requester = remember { FocusRequester() }
    Box(Modifier.onKeyEvent { handleScrollKey(it, scope) }) {
        Column {
            focusTarget(requester)
            content()
        }
    }
    LaunchedEffect(Unit) { requester.requestFocus() }
}

private val Anchor: @Composable (FocusRequester) -> Unit = { requester ->
    Box(Modifier.testTag(FOCUS_TAG).size(1.dp).focusRequester(requester).focusable())
}

/** 200 rows of 50 dp in a 200 x 400 dp viewport, so a page is 360 dp. */
@Composable
private fun TestList(state: LazyListState, tag: String = "list", count: Int = 200) {
    LazyColumn(Modifier.size(200.dp, 400.dp).testTag(tag).keyboardScrollable(state), state) {
        items(count) { Box(Modifier.fillMaxWidth().height(50.dp)) { Text("fila $it") } }
    }
}

private fun LazyListState.scrolledDp(density: Float) =
    (firstVisibleItemIndex * 50f * density + firstVisibleItemScrollOffset) / density

private fun ComposeUiTest.press(vararg keys: Key) =
    onNodeWithTag(FOCUS_TAG).performKeyInput { keys.forEach { pressKey(it) } }

private fun near(actual: Float, expected: Float) = (actual - expected) in -1f..1f

@OptIn(ExperimentalTestApi::class)
class KeyboardScrollTest {
    @AfterEach
    fun reset() = KeyboardScrollRegistry.clear()

    @Test
    fun `page down scrolls 90 percent of the viewport, end and home jump`() = runComposeUiTest {
        val state = LazyListState()
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            Host(Anchor) { TestList(state) }
        }

        press(Key.PageDown)
        waitForIdle()
        near(state.scrolledDp(density), 360f) shouldBe true

        press(Key.MoveEnd)
        waitForIdle()
        state.canScrollForward shouldBe false

        press(Key.MoveHome)
        waitForIdle()
        state.firstVisibleItemIndex shouldBe 0
        state.firstVisibleItemScrollOffset shouldBe 0
    }

    @Test
    fun `space pages down, shift+space pages up, down arrow moves one line`() = runComposeUiTest {
        val state = LazyListState()
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            Host(Anchor) { TestList(state) }
        }

        press(Key.Spacebar)
        waitForIdle()
        near(state.scrolledDp(density), 360f) shouldBe true

        press(Key.DirectionDown)
        waitForIdle()
        near(state.scrolledDp(density), 408f) shouldBe true

        onNodeWithTag(FOCUS_TAG).performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Spacebar)
            keyUp(Key.ShiftLeft)
        }
        waitForIdle()
        near(state.scrolledDp(density), 48f) shouldBe true
    }

    @Test
    fun `a focused text field keeps home, end and the arrows`() = runComposeUiTest {
        val state = LazyListState()
        setContent {
            Host(
                focusTarget = { requester ->
                    BasicTextField(
                        value = "hola mundo",
                        onValueChange = {},
                        singleLine = true,
                        modifier = Modifier.testTag(FOCUS_TAG).focusRequester(requester),
                    )
                },
            ) { TestList(state) }
        }

        press(Key.MoveEnd, Key.MoveHome)
        waitForIdle()

        state.firstVisibleItemIndex shouldBe 0
        state.firstVisibleItemScrollOffset shouldBe 0
    }

    @Test
    fun `the list under the pointer is the active one, otherwise the last composed`() = runComposeUiTest {
        val a = LazyListState()
        val b = LazyListState()
        setContent {
            Host(Anchor) {
                Row {
                    TestList(a, tag = "a")
                    TestList(b, tag = "b")
                }
            }
        }

        press(Key.PageDown)
        waitForIdle()
        a.firstVisibleItemIndex shouldBe 0
        (b.firstVisibleItemIndex > 0) shouldBe true
        val bAfterFirst = b.firstVisibleItemIndex

        onNodeWithTag("a").performMouseInput { moveTo(center) }
        press(Key.PageDown)
        waitForIdle()
        (a.firstVisibleItemIndex > 0) shouldBe true
        b.firstVisibleItemIndex shouldBe bAfterFirst
    }

    @Test
    fun `a list that leaves the composition stops receiving keys`() = runComposeUiTest {
        val state = LazyListState()
        var shown by mutableStateOf(true)
        setContent { if (shown) TestList(state) }
        waitForIdle()
        KeyboardScrollRegistry.dispatch(ScrollKey.PageDown, CoroutineScope(Job())) shouldBe true

        shown = false
        waitForIdle()

        KeyboardScrollRegistry.dispatch(ScrollKey.PageDown, CoroutineScope(Job())) shouldBe false
    }

    @Test
    fun `a list that fits, or an empty one, does not consume keys`() = runComposeUiTest {
        val short = LazyListState()
        val empty = LazyListState()
        var showEmpty by mutableStateOf(false)
        setContent {
            if (showEmpty) TestList(empty, count = 0) else TestList(short, count = 3)
        }
        waitForIdle()
        val scope = CoroutineScope(Job())

        KeyboardScrollRegistry.dispatch(ScrollKey.PageDown, scope) shouldBe false

        showEmpty = true
        waitForIdle()
        KeyboardScrollRegistry.dispatch(ScrollKey.End, scope) shouldBe false
        empty.firstVisibleItemIndex shouldBe 0
    }

    @Test
    fun `fast repeated page downs end with the list at rest`() = runComposeUiTest {
        val state = LazyListState()
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            Host(Anchor) { TestList(state) }
        }

        press(Key.PageDown, Key.PageDown, Key.PageDown)
        waitForIdle()

        state.isScrollInProgress shouldBe false
        (state.scrolledDp(density) >= 359f) shouldBe true
    }

    @Test
    fun `grids and plain scroll states take the keys too`() = runComposeUiTest {
        val grid = LazyGridState()
        val plain = ScrollState(0)
        var showGrid by mutableStateOf(true)
        setContent {
            Host(Anchor) {
                if (showGrid) {
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.size(300.dp, 400.dp).keyboardScrollable(grid), grid) {
                        items(300) { Box(Modifier.height(50.dp)) { Text("$it") } }
                    }
                } else {
                    Column(Modifier.size(200.dp, 400.dp).keyboardScrollable(plain).verticalScroll(plain)) {
                        repeat(100) { Box(Modifier.fillMaxWidth().height(50.dp)) }
                    }
                }
            }
        }

        press(Key.MoveEnd)
        waitForIdle()
        grid.canScrollForward shouldBe false

        showGrid = false
        waitForIdle()
        press(Key.MoveEnd)
        waitForIdle()
        plain.value shouldBe plain.maxValue
    }

    @Test
    fun `the shared list components register themselves`() = runComposeUiTest {
        val column = LazyListState()
        val scrollbarColumn = LazyListState()
        val grid = LazyGridState()
        var which by mutableStateOf(0)
        setContent {
            Host(Anchor) {
                when (which) {
                    0 -> FastScrollLazyColumn(Modifier.size(200.dp, 400.dp), state = column) {
                        items(200) { Box(Modifier.fillMaxWidth().height(50.dp)) { Text("$it") } }
                    }
                    1 -> ScrollbarLazyColumn(Modifier.size(200.dp, 400.dp), state = scrollbarColumn) {
                        items(200) { Box(Modifier.fillMaxWidth().height(50.dp)) { Text("$it") } }
                    }
                    else -> FastScrollLazyVerticalGrid(GridCells.Fixed(3), Modifier.size(300.dp, 400.dp), state = grid) {
                        items(300) { Box(Modifier.height(50.dp)) { Text("$it") } }
                    }
                }
            }
        }

        press(Key.PageDown)
        waitForIdle()
        (column.firstVisibleItemIndex > 0) shouldBe true

        which = 1
        waitForIdle()
        press(Key.PageDown)
        waitForIdle()
        (scrollbarColumn.firstVisibleItemIndex > 0) shouldBe true

        which = 2
        waitForIdle()
        press(Key.PageDown)
        waitForIdle()
        (grid.firstVisibleItemIndex > 0) shouldBe true
    }
}
```

- [ ] **Step 3: Ejecutar los tests y ver que fallan**

Run: `./gradlew :presentation-core:test --tests "tachiyomi.presentation.core.util.*"`
Expected: FAIL de compilación, `Unresolved reference: scrollKeyFor` (y `keyboardScrollable`, `KeyboardScrollRegistry`, `handleScrollKey`).

- [ ] **Step 4: Escribir la implementación**

`presentation-core/src/main/java/tachiyomi/presentation/core/util/KeyboardScroll.kt`:

```kotlin
package tachiyomi.presentation.core.util

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A page is this much of the visible height; the scroller's track clicks use it too. */
internal const val PAGE_FRACTION = 0.9f

private val LineHeight = 48.dp

enum class ScrollKey { PageUp, PageDown, Home, End, LineUp, LineDown }

/** The keys that scroll the active list (spec 7, section 3 B). Ctrl and Alt belong to other shortcuts. */
fun scrollKeyFor(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean): ScrollKey? = when {
    ctrl || alt -> null
    key == Key.Spacebar -> if (shift) ScrollKey.PageUp else ScrollKey.PageDown
    // Shift + anything else is a text selection
    shift -> null
    key == Key.PageUp -> ScrollKey.PageUp
    key == Key.PageDown -> ScrollKey.PageDown
    key == Key.MoveHome -> ScrollKey.Home
    key == Key.MoveEnd -> ScrollKey.End
    key == Key.DirectionUp -> ScrollKey.LineUp
    key == Key.DirectionDown -> ScrollKey.LineDown
    else -> null
}

class KeyboardScrollTarget internal constructor(
    private val state: ScrollableState,
    private val linePx: Float,
    private val toStart: suspend () -> Unit,
    private val toEnd: suspend () -> Unit,
) {
    internal var viewportPx = 0f

    /** False for a list that fits or is empty: its keys go on to whoever else wants them. */
    val canScroll: Boolean get() = state.canScrollForward || state.canScrollBackward

    suspend fun perform(key: ScrollKey) {
        when (key) {
            ScrollKey.PageUp -> state.animateScrollBy(-PAGE_FRACTION * viewportPx)
            ScrollKey.PageDown -> state.animateScrollBy(PAGE_FRACTION * viewportPx)
            ScrollKey.LineUp -> state.animateScrollBy(-linePx)
            ScrollKey.LineDown -> state.animateScrollBy(linePx)
            ScrollKey.Home -> toStart()
            ScrollKey.End -> toEnd()
        }
    }
}

/**
 * The scrollable lists on screen. Only the main thread touches it: composition, pointer events and key events.
 * The active one is the last the pointer entered, and until then the last composed (the top screen's).
 */
object KeyboardScrollRegistry {
    private val targets = mutableListOf<KeyboardScrollTarget>()
    private var hovered: KeyboardScrollTarget? = null

    internal fun register(target: KeyboardScrollTarget) {
        targets += target
    }

    internal fun unregister(target: KeyboardScrollTarget) {
        targets -= target
        if (hovered === target) hovered = null
    }

    internal fun hover(target: KeyboardScrollTarget) {
        hovered = target
    }

    internal fun clear() {
        targets.clear()
        hovered = null
    }

    /** True when a list took the key, so the key event stops there. */
    fun dispatch(key: ScrollKey, scope: CoroutineScope): Boolean {
        val target = (hovered ?: targets.lastOrNull())?.takeIf { it.canScroll } ?: return false
        scope.launch { target.perform(key) }
        return true
    }
}

/** For the window's key handler, after the focused component had its turn. */
fun handleScrollKey(event: KeyEvent, scope: CoroutineScope): Boolean =
    event.type == KeyEventType.KeyDown &&
        scrollKeyFor(event.key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
            ?.let { KeyboardScrollRegistry.dispatch(it, scope) } == true

@Composable
fun Modifier.keyboardScrollable(state: LazyListState): Modifier {
    val linePx = with(LocalDensity.current) { LineHeight.toPx() }
    val target = remember(state) {
        KeyboardScrollTarget(
            state,
            linePx,
            toStart = { state.scrollToItem(0) },
            toEnd = { state.scrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) },
        )
    }
    return attach(target)
}

@Composable
fun Modifier.keyboardScrollable(state: LazyGridState): Modifier {
    val linePx = with(LocalDensity.current) { LineHeight.toPx() }
    val target = remember(state) {
        KeyboardScrollTarget(
            state,
            linePx,
            toStart = { state.scrollToItem(0) },
            toEnd = { state.scrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) },
        )
    }
    return attach(target)
}

@Composable
fun Modifier.keyboardScrollable(state: ScrollState): Modifier {
    val linePx = with(LocalDensity.current) { LineHeight.toPx() }
    val target = remember(state) {
        KeyboardScrollTarget(
            state,
            linePx,
            toStart = { state.scrollTo(0) },
            toEnd = { state.scrollTo(state.maxValue) },
        )
    }
    return attach(target)
}

@Composable
private fun Modifier.attach(target: KeyboardScrollTarget): Modifier {
    DisposableEffect(target) {
        KeyboardScrollRegistry.register(target)
        onDispose { KeyboardScrollRegistry.unregister(target) }
    }
    return this
        .onSizeChanged { target.viewportPx = it.height.toFloat() }
        .onPointerEvent(PointerEventType.Enter) { KeyboardScrollRegistry.hover(target) }
}
```

- [ ] **Step 5: Ejecutar los tests y ver que pasan**

Run: `./gradlew :presentation-core:test --tests "tachiyomi.presentation.core.util.*"`
Expected: PASS salvo `the shared list components register themselves`, que sigue en rojo hasta el Task 2 (las listas compartidas aún no se registran). Dos fallos posibles en los demás, con su arreglo:
- Los tests de `Home`/`End` fallan por `canScrollForward` o `scrollToItem` sin esperar: añade `waitForIdle()` entre la pulsación y la comprobación (ya está en el código; si el fallo persiste, sube a `mainClock.advanceTimeBy(1000)`).
- `the list under the pointer...` falla porque la lista `b` se mueve en vez de `a`: `onPointerEvent(Enter)` no llegó. Sustituye `moveTo(center)` por `moveTo(center); moveBy(Offset(1f, 1f))` en el test.

- [ ] **Step 6: Commit**

```bash
git add presentation-core/src/main/java/tachiyomi/presentation/core/util/KeyboardScroll.kt presentation-core/src/test/kotlin/tachiyomi/presentation/core/util
git commit -m "feat: keyboard scroll registry and keys for lists

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Componentes compartidos y handler de ventana

**Files:**
- Modify: `presentation-core/src/main/java/tachiyomi/presentation/core/components/LazyList.kt` (archivo entero)
- Modify: `presentation-core/src/main/java/tachiyomi/presentation/core/components/LazyGrid.kt:47-48`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt:226-231`, `:268-272` y los imports
- Test: el test `the shared list components register themselves` de Task 1 (ya escrito, falla hasta este task)

**Interfaces:**
- Consumes: `keyboardScrollable(LazyListState)`, `keyboardScrollable(LazyGridState)`, `handleScrollKey(KeyEvent, CoroutineScope): Boolean` de Task 1.
- Produces: `ScrollbarLazyColumn`, `FastScrollLazyColumn` y `FastScrollLazyVerticalGrid` registran su lista; `Main.kt` manda las teclas.

- [ ] **Step 1: Ejecutar el test de componentes compartidos y ver que falla**

Run: `./gradlew :presentation-core:test --tests "tachiyomi.presentation.core.util.KeyboardScrollTest.the shared list components register themselves"`
Expected: FAIL en `column.firstVisibleItemIndex > 0` (la lista no se registra todavía).

- [ ] **Step 2: Reescribir `LazyList.kt`**

Contenido completo del archivo `presentation-core/src/main/java/tachiyomi/presentation/core/components/LazyList.kt`:

```kotlin
package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.util.keyboardScrollable

/**
 * LazyColumn with the draggable scroller. Desktop: the touch-only scrollbar Mihon draws here is gone, the scroller
 * is [FastScrollLazyColumn]'s.
 */
@Composable
fun ScrollbarLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) = FastScrollLazyColumn(
    modifier = modifier,
    state = state,
    contentPadding = contentPadding,
    reverseLayout = reverseLayout,
    verticalArrangement = verticalArrangement,
    horizontalAlignment = horizontalAlignment,
    userScrollEnabled = userScrollEnabled,
    content = content,
)

/**
 * LazyColumn with fast scroller.
 */
@Composable
fun FastScrollLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    VerticalFastScroller(
        listState = state,
        modifier = modifier,
        topContentPadding = contentPadding.calculateTopPadding(),
        endContentPadding = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
    ) {
        LazyColumn(
            modifier = Modifier.keyboardScrollable(state),
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
    }
}
```

`Scrollbar.kt` (`drawVerticalScrollbar`) se queda como está: solo lo usa su propio `@Preview`.

- [ ] **Step 3: Registrar la grid en `LazyGrid.kt`**

En `presentation-core/src/main/java/tachiyomi/presentation/core/components/LazyGrid.kt`, añadir el import `import tachiyomi.presentation.core.util.keyboardScrollable` (después de `import androidx.compose.ui.unit.dp`) y cambiar el `LazyVerticalGrid`:

```kotlin
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.keyboardScrollable(state),
            state = state,
```

- [ ] **Step 4: Conectar `Main.kt`**

En `desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt`:

1. Añadir el import `import tachiyomi.presentation.core.util.handleScrollKey` junto a `import tachiyomi.presentation.core.common...` (orden alfabético: después de `import tachiyomi.i18n.MR`).
2. Tras `val exitScope = rememberCoroutineScope()` añadir:

```kotlin
        // The scroll keys' animations run here (spec 7 §3 C)
        val scrollScope = rememberCoroutineScope()
```

3. Cambiar las dos líneas de `Window`:

```kotlin
                onPreviewKeyEvent = { event -> handleShortcut(event, beforeFocusedComponent = true, scrollScope) },
                onKeyEvent = { event -> handleShortcut(event, beforeFocusedComponent = false, scrollScope) },
```

4. Sustituir `handleShortcut` (al final del archivo, antes de `WindowControls.toSaved`) por:

```kotlin
private fun handleShortcut(event: KeyEvent, beforeFocusedComponent: Boolean, scrollScope: CoroutineScope): Boolean {
    if (event.type == KeyEventType.KeyDown) {
        val shortcut = shortcutFor(event.key, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
        if (shortcut != null && shortcut.beforeFocusedComponent == beforeFocusedComponent && AppEvents.send(shortcut)) {
            return true
        }
    }
    // After the focused component: a text field keeps its own Home, End, arrows and Space (spec 7 §3 C)
    return !beforeFocusedComponent && handleScrollKey(event, scrollScope)
}
```

- [ ] **Step 5: Ejecutar tests**

Run: `./gradlew :presentation-core:test :desktop-app:compileKotlin`
Expected: PASS en presentation-core (incluido `the shared list components register themselves`) y compilación de desktop-app sin errores.

- [ ] **Step 6: Commit**

```bash
./gradlew :presentation-core:spotlessApply :desktop-app:spotlessApply
git add presentation-core desktop-app/src/main/kotlin/mihon/desktop/app/Main.kt
git commit -m "feat: shared lists take scroll keys, the window handler sends them

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Barra de scroll fija, arrastrable y con clic en el carril

**Files:**
- Modify: `presentation-core/src/main/java/tachiyomi/presentation/core/components/VerticalFastScroller.kt`
- Test: `presentation-core/src/test/kotlin/tachiyomi/presentation/core/components/ScrollerBarTest.kt`

**Interfaces:**
- Consumes: `PAGE_FRACTION` (internal, `tachiyomi.presentation.core.util`, Task 1).
- Produces: `ScrollerBar` privado, `internal fun thumbAlpha(allowed: Boolean, active: Boolean): Float` y las constantes `internal const val THUMB_TAG = "scroller-thumb"` y `TRACK_TAG = "scroller-track"`, ambas en `VerticalFastScroller.kt`.

- [ ] **Step 1: Escribir los tests**

`presentation-core/src/test/kotlin/tachiyomi/presentation/core/components/ScrollerBarTest.kt`:

```kotlin
package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ScrollerBarTest {
    @Test
    fun `the thumb rests half visible and is never hidden while the list can scroll`() {
        thumbAlpha(allowed = true, active = false) shouldBe 0.5f
        thumbAlpha(allowed = true, active = true) shouldBe 1f
        thumbAlpha(allowed = false, active = true) shouldBe 0f
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setList(state: LazyListState): () -> Float {
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            FastScrollLazyColumn(Modifier.size(200.dp, 400.dp), state = state) {
                items(200) { Box(Modifier.fillMaxWidth().height(50.dp)) { Text("fila $it") } }
            }
        }
        return { (state.firstVisibleItemIndex * 50f * density + state.firstVisibleItemScrollOffset) / density }
    }

    @Test
    fun `dragging the thumb scrolls the list`() = runComposeUiTest {
        val state = LazyListState()
        setList(state)
        waitForIdle()

        onNodeWithTag(THUMB_TAG).performMouseInput {
            press()
            moveBy(Offset(0f, 20f))
            moveBy(Offset(0f, 130f))
            release()
        }
        waitForIdle()

        (state.firstVisibleItemIndex > 20) shouldBe true
    }

    @Test
    fun `a click on the track below the thumb scrolls one page`() = runComposeUiTest {
        val state = LazyListState()
        val scrolledDp = setList(state)
        waitForIdle()

        onNodeWithTag(TRACK_TAG).performMouseInput { click(Offset(width / 2f, height - 10f)) }
        waitForIdle()

        (scrolledDp() in 300f..400f) shouldBe true
    }

    @Test
    fun `a click on the thumb itself does not page`() = runComposeUiTest {
        val state = LazyListState()
        setList(state)
        waitForIdle()

        onNodeWithTag(THUMB_TAG).performMouseInput { click() }
        waitForIdle()

        state.firstVisibleItemIndex shouldBe 0
        state.firstVisibleItemScrollOffset shouldBe 0
    }
}
```

Quitar el import `LaunchedEffect` si queda sin uso (spotless lo avisa).

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `./gradlew :presentation-core:test --tests "tachiyomi.presentation.core.components.ScrollerBarTest"`
Expected: FAIL de compilación, `Unresolved reference: thumbAlpha` / `THUMB_TAG` / `TRACK_TAG`.

- [ ] **Step 3: Añadir `ScrollerBar` y las constantes**

En `VerticalFastScroller.kt`, sustituir el bloque de constantes del final (desde `private val ThumbLength = 48.dp` hasta el cierre de `ImmediateFadeOutAnimationSpec`) por:

```kotlin
private val ThumbLength = 48.dp
private val TrackWidth = 14.dp
private val ThumbThickness = 6.dp
private val ThumbThicknessHover = 10.dp
private val ThumbShape = RoundedCornerShape(50)
private const val RestingThumbAlpha = 0.5f
private const val TrackAlpha = 0.08f
private val TrackRepeatDelay = 400.milliseconds
private val TrackRepeatInterval = 120.milliseconds

internal const val THUMB_TAG = "scroller-thumb"
internal const val TRACK_TAG = "scroller-track"

internal fun thumbAlpha(allowed: Boolean, active: Boolean): Float = when {
    !allowed -> 0f
    active -> 1f
    else -> RestingThumbAlpha
}

/**
 * The track and thumb both scrollers draw (spec 7 §3 D): always there when the content doesn't fit, wider under the
 * pointer, a thumb to drag, and a press on the track that pages towards the pointer, repeating while held.
 * [thumbOffsetY] is the thumb's top in the scroller's own pixels.
 */
@Composable
private fun ScrollerBar(
    thumbOffsetY: Float,
    scrollInProgress: Boolean,
    thumbAllowed: () -> Boolean,
    thumbColor: Color,
    endContentPadding: Dp,
    dragInteractionSource: MutableInteractionSource,
    onDrag: (Float) -> Unit,
    onTrackPress: (towardsEnd: Boolean) -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val dragged by dragInteractionSource.collectIsDraggedAsState()
    val alpha by animateFloatAsState(thumbAlpha(thumbAllowed(), hovered || dragged || scrollInProgress))
    val thickness by animateDpAsState(if (hovered || dragged) ThumbThicknessHover else ThumbThickness)
    val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
    val currentOffset by rememberUpdatedState(thumbOffsetY)
    val currentTrackPress by rememberUpdatedState(onTrackPress)

    Box(
        Modifier
            .fillMaxHeight()
            .padding(end = endContentPadding)
            .width(TrackWidth)
            .testTag(TRACK_TAG)
            .hoverable(hoverSource)
            .background(thumbColor.copy(alpha = TrackAlpha * alpha))
            .pointerInput(thumbHeightPx) {
                detectTapGestures(
                    onPress = { press ->
                        // null while the pointer is on the thumb, which has its own drag
                        fun towardsEnd(): Boolean? = when {
                            press.y < currentOffset -> false
                            press.y > currentOffset + thumbHeightPx -> true
                            else -> null
                        }
                        val first = towardsEnd() ?: return@detectTapGestures
                        currentTrackPress(first)
                        coroutineScope {
                            val repeat = launch {
                                delay(TrackRepeatDelay)
                                // Stops when the thumb reaches the pointer
                                while (towardsEnd() == first) {
                                    currentTrackPress(first)
                                    delay(TrackRepeatInterval)
                                }
                            }
                            tryAwaitRelease()
                            repeat.cancel()
                        }
                    },
                )
            },
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                .testTag(THUMB_TAG)
                .draggable(
                    interactionSource = dragInteractionSource,
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState(onDrag),
                )
                .height(ThumbLength)
                .width(thickness)
                .alpha(alpha)
                .background(color = thumbColor, shape = ThumbShape),
        )
    }
}
```

- [ ] **Step 4: Usar `ScrollerBar` en `VerticalFastScroller`**

En la función `VerticalFastScroller`:

1. Borrar el bloque `val scrolled = remember { MutableSharedFlow<Unit>(...) }` (líneas 90-95).
2. Borrar las tres líneas `scrolled.tryEmit(Unit)`: la de `if (thumbProportion <= 0.001f)` y la que cierra el `LaunchedEffect(thumbOffsetY)`, y la `if (stableScrollInProgress) scrolled.tryEmit(Unit)` de "When list scrolled".
3. Sustituir desde `// Thumb alpha` hasta el cierre del `Box(...)` del pulgar (líneas 171-223) por:

```kotlin
            val pageScope = rememberCoroutineScope()
            ScrollerBar(
                thumbOffsetY = thumbOffsetY,
                scrollInProgress = stableScrollInProgress,
                thumbAllowed = thumbAllowed,
                thumbColor = thumbColor,
                endContentPadding = endContentPadding,
                dragInteractionSource = dragInteractionSource,
                onDrag = { delta ->
                    thumbOffsetY = (thumbOffsetY + delta).coerceIn(thumbTopPadding, thumbTopPadding + trackHeightPx)
                },
                onTrackPress = { towardsEnd ->
                    val page = PAGE_FRACTION * contentHeight
                    pageScope.launch { listState.animateScrollBy(if (towardsEnd) page else -page) }
                },
            )
```

- [ ] **Step 5: Usar `ScrollerBar` en `VerticalGridFastScroller`**

1. Borrar su `val scrolled = remember { MutableSharedFlow<Unit>(...) }` (líneas 310-315) y las dos `scrolled.tryEmit(Unit)` (en el `LaunchedEffect(thumbOffsetY)` y en `LaunchedEffect(state.firstVisibleItemScrollOffset)`).
2. Sustituir desde `// Thumb alpha` hasta el cierre del `Box(...)` del pulgar (líneas 361-412) por:

```kotlin
            val pageScope = rememberCoroutineScope()
            ScrollerBar(
                thumbOffsetY = thumbOffsetY,
                scrollInProgress = state.isScrollInProgress,
                thumbAllowed = thumbAllowed,
                thumbColor = thumbColor,
                endContentPadding = endContentPadding,
                dragInteractionSource = dragInteractionSource,
                onDrag = { delta ->
                    thumbOffsetY = (thumbOffsetY + delta).coerceIn(thumbTopPadding, thumbTopPadding + trackHeightPx)
                },
                onTrackPress = { towardsEnd ->
                    val page = PAGE_FRACTION * contentHeight
                    pageScope.launch { state.animateScrollBy(if (towardsEnd) page else -page) }
                },
            )
```

- [ ] **Step 6: Arreglar imports**

En `VerticalFastScroller.kt`:
- Quitar: `androidx.compose.animation.core.Animatable`, `androidx.compose.animation.core.tween`, `kotlinx.coroutines.channels.BufferOverflow`, `kotlinx.coroutines.flow.MutableSharedFlow`, `kotlinx.coroutines.flow.collectLatest`, `kotlinx.coroutines.flow.sample`, `kotlin.time.Duration.Companion.seconds`.
- Añadir: `androidx.compose.animation.core.animateDpAsState`, `androidx.compose.animation.core.animateFloatAsState`, `androidx.compose.foundation.gestures.animateScrollBy`, `androidx.compose.foundation.gestures.detectTapGestures`, `androidx.compose.foundation.hoverable`, `androidx.compose.foundation.interaction.collectIsHoveredAsState`, `androidx.compose.foundation.layout.fillMaxHeight`, `androidx.compose.runtime.rememberCoroutineScope`, `androidx.compose.runtime.rememberUpdatedState`, `androidx.compose.ui.Alignment`, `androidx.compose.ui.input.pointer.pointerInput`, `androidx.compose.ui.platform.testTag`, `kotlinx.coroutines.coroutineScope`, `kotlinx.coroutines.launch`, `kotlin.time.Duration.Companion.milliseconds`, `tachiyomi.presentation.core.util.PAGE_FRACTION`.
- Se quedan: `delay`, `background`, `draggable`, `rememberDraggableState`, `collectIsDraggedAsState`, `offset`, `padding`, `width`, `height`, `alpha`, `RoundedCornerShape`.

Run: `./gradlew :presentation-core:spotlessApply :presentation-core:compileKotlin`
Expected: compila; si queda un import sin uso o falta uno, spotless/compilador lo dice.

- [ ] **Step 7: Ejecutar los tests**

Run: `./gradlew :presentation-core:test`
Expected: PASS, incluidos `ScrollerBarTest` y `KeyboardScrollTest`. Si `dragging the thumb scrolls the list` falla porque la lista no se mueve: el `LaunchedEffect(thumbOffsetY)` solo actúa con `isThumbDragged`; sube el primer `moveBy` a `Offset(0f, 40f)` para superar el touch slop.

- [ ] **Step 8: Commit**

```bash
git add presentation-core
git commit -m "feat: scrollers keep a visible track, draggable thumb and page-on-click

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Las pantallas principales

**Files (todas en `desktop-app/src/main/kotlin/`):**
- Modify: `eu/kanade/presentation/browse/components/BrowseSourceComfortableGrid.kt:29`
- Modify: `eu/kanade/presentation/browse/components/BrowseSourceCompactGrid.kt:29`
- Modify: `eu/kanade/presentation/browse/components/BrowseSourceList.kt:25`
- Modify: `eu/kanade/presentation/browse/GlobalSearchScreen.kt:73`
- Modify: `mihon/desktop/app/settings/SettingsTab.kt:64`
- Modify: `eu/kanade/presentation/manga/MangaScreen.kt:364` y `:641`
- Modify: `eu/kanade/presentation/category/CategoryScreen.kt:106`
- Modify: `mihon/desktop/app/download/DownloadsTab.kt:121`

**Interfaces:**
- Consumes: `FastScrollLazyColumn`, `FastScrollLazyVerticalGrid` (Task 2), `keyboardScrollable(LazyListState)` (Task 1).

Biblioteca (`LazyLibraryGrid`, `LibraryList`), historial, actualizaciones, extensiones y próximos ya usan `FastScrollLazyColumn`/`FastScrollLazyVerticalGrid`: quedan cubiertos por Task 2 sin tocarlos.

- [ ] **Step 1: Ejecutar la suite de pantallas afectadas antes de tocar nada**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.browse.*" --tests "mihon.desktop.app.download.DownloadsTabTest" --tests "mihon.desktop.app.library.CategoriesTest" --tests "mihon.desktop.app.main.*"`
Expected: PASS (línea base; anota el número de tests).

- [ ] **Step 2: Grids de explorar → `FastScrollLazyVerticalGrid`**

En `BrowseSourceComfortableGrid.kt` y `BrowseSourceCompactGrid.kt`: cambiar el import `androidx.compose.foundation.lazy.grid.LazyVerticalGrid` por `tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid` (en la posición alfabética que pida spotless) y la llamada `LazyVerticalGrid(` por `FastScrollLazyVerticalGrid(`. Los argumentos (`columns`, `contentPadding`, `verticalArrangement`, `horizontalArrangement` y el bloque) no cambian: es la misma forma que `LazyLibraryGrid`.

- [ ] **Step 3: Lista de explorar, búsqueda global y ajustes → `FastScrollLazyColumn`**

- `BrowseSourceList.kt`: import `androidx.compose.foundation.lazy.LazyColumn` → `tachiyomi.presentation.core.components.FastScrollLazyColumn`; `LazyColumn(` → `FastScrollLazyColumn(` (mismo argumento `contentPadding = ...`).
- `GlobalSearchScreen.kt`: igual; `LazyColumn(contentPadding = contentPadding,)` → `FastScrollLazyColumn(contentPadding = contentPadding,)`. Quitar el import de `LazyColumn` solo si no queda otro uso en el archivo.
- `SettingsTab.kt`: `LazyColumn(contentPadding = padding) {` → `FastScrollLazyColumn(contentPadding = padding) {`; mismo cambio de imports.

- [ ] **Step 4: Capítulos del manga, categorías y descargas → `keyboardScrollable`**

Añadir en cada archivo `import tachiyomi.presentation.core.util.keyboardScrollable`.

- `MangaScreen.kt` (dos sitios, `:364` y `:641`): ambos `LazyColumn` ya llevan `modifier = Modifier.fillMaxHeight()` y `state = chapterListState`. Cambiar a:

```kotlin
                LazyColumn(
                    modifier = Modifier.fillMaxHeight().keyboardScrollable(chapterListState),
                    state = chapterListState,
```
(y lo mismo con la indentación del segundo).
- `CategoryScreen.kt:106`: `modifier = Modifier.fillMaxSize(),` → `modifier = Modifier.fillMaxSize().keyboardScrollable(lazyListState),`.
- `DownloadsTab.kt:121`: `LazyColumn(state = listState, contentPadding = contentPadding) {` →

```kotlin
            LazyColumn(
                modifier = Modifier.keyboardScrollable(listState),
                state = listState,
                contentPadding = contentPadding,
            ) {
```

- [ ] **Step 5: Compilar y ejecutar las suites**

Run: `./gradlew :desktop-app:spotlessApply :desktop-app:test --tests "mihon.desktop.app.browse.*" --tests "mihon.desktop.app.download.DownloadsTabTest" --tests "mihon.desktop.app.library.CategoriesTest" --tests "mihon.desktop.app.main.*"`
Expected: mismos tests que el Step 1, PASS.

- [ ] **Step 6: Commit**

```bash
git add desktop-app/src/main/kotlin
git commit -m "feat: browse, search, settings, chapters, categories and downloads scroll by keyboard

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Spec al día, suite completa y entrega

**Files:**
- Modify: `docs/superpowers/specs/2026-10-25-subproyecto-7-teclado-scroll-design.md`

- [ ] **Step 1: Poner en el spec lo que decidió el plan**

En el spec, cambiar:
- §3 A: el archivo `KeyboardScroll.kt` vive en `presentation-core/.../util/`; el alto visible lo mide el modificador con `onSizeChanged` (sirve para los tres tipos de estado); `KeyboardScrollTarget.perform(key)` sustituye a las cuatro funciones sueltas; `dispatch` devuelve `false` si la lista activa no puede desplazarse (cabe entera o está vacía).
- §3 B: `ScrollKey` y `scrollKeyFor` están en presentation-core junto al registro, no en `main/ScrollKeys.kt`; Mayús con cualquier tecla que no sea Espacio no mapea (es selección de texto).
- §3 C, riesgo: sustituir el párrafo por «↑ y ↓ desplazan siempre que ningún componente las consuma. Esto sustituye el movimiento de foco con esas dos flechas; Tab, Mayús+Tab, ← y → siguen igual».
- §3 D: `Scrollbar.kt` (`drawVerticalScrollbar`) se queda: solo lo usa su `@Preview`. El carril mide 14 dp; el pulgar 6 dp en reposo y 10 dp con el puntero.
- §4: la lista de pantallas pasa a: ya cubiertas por componentes compartidos (biblioteca, historial, actualizaciones, extensiones, próximos, ajustes, fuentes, migración, detalles de extensión, seguimiento) y cambiadas en el plan (explorar, búsqueda global, `SettingsTab`, capítulos, categorías, descargas). `LibraryPager` y `UpcomingScreen` (`verticalScroll`) quedan fuera.
- §5: sustituir el test «↑ y ↓ ... no rompen el foco» por «un campo de texto enfocado conserva Inicio, Fin y las flechas».
- §6, punto 4: «Con un diálogo de lista abierto, las teclas no mueven la pantalla de debajo (si el diálogo es una ventana aparte, tampoco mueven la lista del diálogo: se anota, no se arregla)».

- [ ] **Step 2: Suite completa**

Run: `./gradlew :presentation-core:test :desktop-app:test`
Expected: PASS. La base era 502 tests en desktop-app (más los de presentation-core); este subproyecto suma 4 + 9 + 4 = 17 tests en presentation-core. Los tests de desktop-app dejan carpetas en `%TEMP%` (~300 MB por pasada): avisar al usuario si el disco va justo.

- [ ] **Step 3: Arrancar la app y mirar la barra**

Run: `./gradlew :desktop-app:run`
Comprobar a ojo: en la biblioteca y en la lista de capítulos el carril se ve sin tocar nada, el clic en el borde derecho de un elemento (botón de descarga de un capítulo) sigue funcionando, y Av Pág mueve la lista bajo el puntero. Si el carril de 14 dp tapa un botón del borde derecho, bajar `TrackWidth` a 10 dp en `VerticalFastScroller.kt`.

- [ ] **Step 4: Commit y entrega**

```bash
git add docs/superpowers/specs/2026-10-25-subproyecto-7-teclado-scroll-design.md
git commit -m "docs: subproject 7 spec follows the plan's decisions

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

Pasar al usuario la prueba manual del spec §6 (5 puntos), ya con el punto 4 corregido. La regla fija del usuario (2026-10-22): corregir los menores de inmediato, y con todo en verde, fusionar y pasar a lo siguiente sin preguntar.
