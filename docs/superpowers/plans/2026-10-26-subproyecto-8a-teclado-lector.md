# Subproyecto 8a: teclado del lector — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Desplazamiento automático en webtoon, zoom con teclado en modo páginas y una ayuda de atajos (F1) en toda la app.

**Architecture:** Las teclas nuevas son `ReaderAction`s que da `readerActionFor`; `ReaderContent.perform` las envía al
`ViewerController` del visor (`autoScroll`, `zoom`) o cambia preferencias con el aviso que ya existe. La ayuda es
una tabla de filas (`ShortcutHelp`) que un test cruza con `Shortcut` y `ReaderAction`, y un diálogo que `AppContent`
muestra con F1.

**Tech Stack:** Kotlin, Compose Multiplatform (desktop), `net.engawapg.lib.zoomable` 2.16.0, moko-resources, JUnit 5 +
kotest, `runComposeUiTest`.

**Spec:** `docs/superpowers/specs/2026-10-26-subproyecto-8a-teclado-lector-design.md`

## Global Constraints

- Rama `feat/reader-keyboard-8a` (sale de `feat/reader-webtoon-quick-settings`, PR #6).
- Commits en inglés, `tipo: asunto`, sin `Co-Authored-By` ni firma. **Solo con orden del usuario** (CLAUDE.md): cada
  tarea termina con el mensaje propuesto; se commitea cuando el usuario lo pida.
- Todo texto visible en `MR.strings`, con traducción en `i18n/src/commonMain/moko-resources/es/strings.xml`
  (`SpanishStringsTest`). Los strings nuevos de escritorio llevan prefijo `desktop_` y van junto a
  `desktop_webtoon_scroll_step` en ambos archivos.
- `desktop-app` no importa APIs de Android (`scripts/check-no-android.sh`).
- Velocidad del desplazamiento automático: `pref_webtoon_autoscroll_speed`, por defecto 10, rango 2–50, paso 2,
  en % del alto de la ventana por segundo.
- Zoom con teclado: × o ÷ 1,25 por pulsación, entre 1× y 5×; Ctrl+0 vuelve a 1×.
- Probar el lector en vivo solo con `TURBOYOMI_HOME` temporal (skill `perfil-aislado`); y cada script de entrada
  fijado al PID de la ventana de prueba (el usuario puede tener su Turboyomi abierto).
- Tests Compose con el desplazamiento automático en marcha: `mainClock.autoAdvance = false` y
  `mainClock.advanceTimeBy(ms)`; con el reloj automático el test nunca está ocioso (comprobado el 2026-10-26).

## Review Focus

- La velocidad cambia (slider o Ctrl+Shift) con el desplazamiento en marcha: debe seguir, a la velocidad nueva. → Tarea 2, paso 1.
- Abrir la ayuda (F1) durante el desplazamiento automático: debe pausarlo, como los ajustes rápidos. → Tarea 4, paso 1.
- Ctrl+`+` en modo páginas sobre una página que aún carga o falló: no hay zoom que aplicar; sin aviso y sin error. → Tarea 3, paso 1.
- Ctrl+`+` con dos páginas (doble página): ambas se amplían a la vez. → Tarea 5 (verificación visual).
- Cambiar de modo de lectura o salir del lector con el desplazamiento en marcha: se para y no sigue sobre un visor que ya no está. → Tarea 2, paso 1.

---

### Task 1: Teclas nuevas del lector

**Files:**
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderAction.kt`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/ReaderInputTest.kt`

**Interfaces:**
- Produces: `ReaderAction.ZoomReset`, `ReaderAction.ToggleAutoScroll`, `ReaderAction.AutoScrollFaster`,
  `ReaderAction.AutoScrollSlower`; `readerActionFor` da `ZoomIn`/`ZoomOut`/`ZoomReset` también en modo páginas.

- [ ] **Step 1: Write the failing test**

En `ReaderInputTest.kt`, sustituir el test `ctrl with plus, minus and the arrows changes the webtoon's zoom and scroll step` por:

```kotlin
    @Test
    fun `ctrl with plus, minus and the arrows changes the webtoon's zoom and scroll step`() {
        fun ctrl(key: Key, mode: ReadingMode = ReadingMode.WEBTOON, shift: Boolean = false) =
            readerActionFor(key, shift = shift, ctrl = true, alt = false, mode = mode)
        ctrl(Key.Plus) shouldBe ReaderAction.ZoomIn
        ctrl(Key.Equals) shouldBe ReaderAction.ZoomIn
        ctrl(Key.NumPadAdd) shouldBe ReaderAction.ZoomIn
        ctrl(Key.Minus) shouldBe ReaderAction.ZoomOut
        ctrl(Key.NumPadSubtract) shouldBe ReaderAction.ZoomOut
        ctrl(Key.DirectionUp, ReadingMode.CONTINUOUS_VERTICAL) shouldBe ReaderAction.ScrollStepUp
        ctrl(Key.DirectionDown) shouldBe ReaderAction.ScrollStepDown
        // Webtoon has no zoom to reset, and the scroll step and speed are webtoon's only
        ctrl(Key.Zero) shouldBe null
        ctrl(Key.DirectionUp, ReadingMode.VERTICAL) shouldBe null
        ctrl(Key.DirectionUp, ReadingMode.VERTICAL, shift = true) shouldBe null
        // AltGr is Ctrl+Alt on Windows
        readerActionFor(Key.Plus, shift = false, ctrl = true, alt = true, mode = ReadingMode.WEBTOON) shouldBe null
    }

    @Test
    fun `ctrl with plus, minus and zero zooms the page in paged modes`() {
        fun ctrl(key: Key) = readerActionFor(key, shift = false, ctrl = true, alt = false, ReadingMode.LEFT_TO_RIGHT)
        ctrl(Key.Plus) shouldBe ReaderAction.ZoomIn
        ctrl(Key.Equals) shouldBe ReaderAction.ZoomIn
        ctrl(Key.NumPadAdd) shouldBe ReaderAction.ZoomIn
        ctrl(Key.Minus) shouldBe ReaderAction.ZoomOut
        ctrl(Key.NumPadSubtract) shouldBe ReaderAction.ZoomOut
        ctrl(Key.Zero) shouldBe ReaderAction.ZoomReset
        ctrl(Key.NumPad0) shouldBe ReaderAction.ZoomReset
    }

    @Test
    fun `S and ctrl+shift with the arrows run the webtoon's automatic scrolling`() {
        key(Key.S, ReadingMode.WEBTOON) shouldBe ReaderAction.ToggleAutoScroll
        key(Key.S, ReadingMode.CONTINUOUS_VERTICAL) shouldBe ReaderAction.ToggleAutoScroll
        key(Key.S, ReadingMode.LEFT_TO_RIGHT) shouldBe null
        key(Key.S, ReadingMode.WEBTOON, shift = true) shouldBe null
        readerActionFor(Key.DirectionUp, shift = true, ctrl = true, alt = false, ReadingMode.WEBTOON) shouldBe
            ReaderAction.AutoScrollFaster
        readerActionFor(Key.DirectionDown, shift = true, ctrl = true, alt = false, ReadingMode.WEBTOON) shouldBe
            ReaderAction.AutoScrollSlower
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.ReaderInputTest"`
Expected: FAIL to compile, `Unresolved reference 'ZoomReset'` (y `ToggleAutoScroll`, `AutoScrollFaster`, `AutoScrollSlower`).

- [ ] **Step 3: Write minimal implementation**

En `ReaderAction.kt`, el enum queda:

```kotlin
enum class ReaderAction {
    NextPage,
    PreviousPage,
    ScrollDown,
    ScrollUp,
    FirstPage,
    LastPage,
    NextChapter,
    PreviousChapter,
    ToggleMenu,

    // Webtoon: less or more side padding. Paged: the page's zoom, and back to 1×
    ZoomIn,
    ZoomOut,
    ZoomReset,

    // Webtoon only: a longer or shorter step for ↑ and ↓, and scrolling by itself at a speed of its own
    ScrollStepUp,
    ScrollStepDown,
    ToggleAutoScroll,
    AutoScrollFaster,
    AutoScrollSlower,
}
```

Y `readerActionFor` (KDoc incluido):

```kotlin
/**
 * The reader's keys (spec 2a §4, "Entrada"). ← and → follow the reading direction; ↑ and ↓ scroll in webtoon and
 * turn pages otherwise. Ctrl with +, - and 0 zooms (webtoon: the side padding); in webtoon Ctrl with ↑, ↓ changes
 * the scroll step, Ctrl+Shift with them the automatic scrolling's speed, and S starts or stops it (spec 8a). Other
 * keys with Ctrl or Alt are the window's (Alt+←, Ctrl+F); Esc is Compose's back key.
 */
fun readerActionFor(key: Key, shift: Boolean, ctrl: Boolean, alt: Boolean, mode: ReadingMode): ReaderAction? {
    val rightToLeft = mode == ReadingMode.RIGHT_TO_LEFT
    val webtoon = mode.type == ReadingMode.ViewerType.Webtoon
    if (ctrl && !alt) {
        return when (key) {
            // Equals: + is Shift+= on a US keyboard
            Key.Plus, Key.Equals, Key.NumPadAdd -> ReaderAction.ZoomIn
            Key.Minus, Key.NumPadSubtract -> ReaderAction.ZoomOut
            Key.Zero, Key.NumPad0 -> if (webtoon) null else ReaderAction.ZoomReset
            Key.DirectionUp -> when {
                !webtoon -> null
                shift -> ReaderAction.AutoScrollFaster
                else -> ReaderAction.ScrollStepUp
            }
            Key.DirectionDown -> when {
                !webtoon -> null
                shift -> ReaderAction.AutoScrollSlower
                else -> ReaderAction.ScrollStepDown
            }
            else -> null
        }
    }
    if (ctrl || alt) return null
    return when (key) {
        Key.DirectionRight -> if (rightToLeft) ReaderAction.PreviousPage else ReaderAction.NextPage
        Key.DirectionLeft -> if (rightToLeft) ReaderAction.NextPage else ReaderAction.PreviousPage
        Key.DirectionDown -> if (webtoon) ReaderAction.ScrollDown else ReaderAction.NextPage
        Key.DirectionUp -> if (webtoon) ReaderAction.ScrollUp else ReaderAction.PreviousPage
        Key.Spacebar -> if (shift) ReaderAction.PreviousPage else ReaderAction.NextPage
        Key.PageDown -> ReaderAction.NextPage
        Key.PageUp -> ReaderAction.PreviousPage
        Key.MoveHome -> ReaderAction.FirstPage
        Key.MoveEnd -> ReaderAction.LastPage
        Key.N -> ReaderAction.NextChapter
        Key.P -> ReaderAction.PreviousChapter
        Key.S -> if (webtoon && !shift) ReaderAction.ToggleAutoScroll else null
        else -> null
    }
}
```

`ReaderContent.perform` aún envía las acciones nuevas a `controller?.perform`, que las ignora (`else -> Unit`): compila y no hace nada hasta las tareas 2 y 3. En modo páginas `ZoomIn`/`ZoomOut` llegarían a `adjust` (espaciado lateral): la tarea 3 lo separa por modo; hasta entonces no hay que publicar la rama.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.ReaderInputTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit (con orden del usuario)**

```bash
git add desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderAction.kt desktop-app/src/test/kotlin/mihon/desktop/app/reader/ReaderInputTest.kt
git commit -q -F - <<'EOF'
feat: reader keys for the paged zoom and the webtoon's automatic scrolling
EOF
```

---

### Task 2: Desplazamiento automático en webtoon

**Files:**
- Modify: `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt` (preferencia y constantes junto a `webtoonScrollStep` y `WEBTOON_KEY_STEP`)
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/ViewerItems.kt` (`ViewerController`)
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/WebtoonViewer.kt` (`WebtoonController`)
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt` (`ReaderContent`)
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderQuickSettings.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsReaderScreen.kt`
- Modify: `i18n/src/commonMain/moko-resources/base/strings.xml`, `i18n/src/commonMain/moko-resources/es/strings.xml`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/WebtoonAndDualTest.kt`

**Interfaces:**
- Consumes: `ReaderAction.ToggleAutoScroll`, `AutoScrollFaster`, `AutoScrollSlower` (tarea 1).
- Produces: `ReaderPreferences.webtoonAutoScrollSpeed: Preference<Int>`; constantes `WEBTOON_AUTOSCROLL_MIN = 2`,
  `WEBTOON_AUTOSCROLL_MAX = 50`, `WEBTOON_AUTOSCROLL_STEP = 2`; `ViewerController.autoScroll(speed: () -> Float)`
  (por defecto no hace nada); `const val AUTOSCROLL_TAG = "autoscroll on"` en `ReaderScreen.kt`;
  `ReaderContent.showNotice(text: String)` (función local) que la tarea 3 reutiliza; strings `desktop_autoscroll`,
  `desktop_webtoon_autoscroll_speed`.

- [ ] **Step 1: Write the failing tests**

Añadir a `WebtoonAndDualTest.kt` (`assertDoesNotExist`, `onAllNodes` y `mainClock` son miembros y no se importan):

```kotlin
    @Test
    fun `S scrolls the webtoon by itself, faster with ctrl+shift+up, until the wheel stops it`() =
        runComposeUiTest {
            open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
            val preferences = startTestApp().readerPreferences
            preferences.webtoonAutoScrollSpeed.set(10)
            waitForTag("page 1 ready")
            fun top() = onNodeWithTag("page 1 ready").fetchSemanticsNode().positionInRoot.y

            // The scrolling runs every frame: with the clock moving by itself the test would never be idle
            mainClock.autoAdvance = false
            val start = top()
            onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
            mainClock.advanceTimeBy(1_000)
            onNodeWithTag(AUTOSCROLL_TAG).assertExists()
            val afterOneSecond = top()
            (afterOneSecond < start) shouldBe true

            // Faster, with a note, and still running
            onNodeWithTag(READER_TAG).performKeyInput {
                withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionUp) } }
            }
            mainClock.advanceTimeBy(100)
            preferences.webtoonAutoScrollSpeed.get() shouldBe 12
            onNodeWithText("${str(MR.strings.desktop_autoscroll)}: 12 %/s").assertExists()
            onNodeWithTag(AUTOSCROLL_TAG).assertExists()

            // A new speed from the quick settings' slider is taken up at once, without stopping
            preferences.webtoonAutoScrollSpeed.set(20)
            mainClock.advanceTimeBy(500)
            onNodeWithTag(AUTOSCROLL_TAG).assertExists()

            // The wheel takes over
            onNodeWithTag(READER_TAG).performMouseInput { scroll(1f) }
            mainClock.advanceTimeBy(100)
            onNodeWithTag(AUTOSCROLL_TAG).assertDoesNotExist()
            mainClock.autoAdvance = true
        }

    @Test
    fun `S stops the automatic scrolling, and so do the arrows and leaving the reader`() = runComposeUiTest {
        open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
        waitForTag("page 1 ready")
        mainClock.autoAdvance = false

        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
        mainClock.advanceTimeBy(300)
        onNodeWithTag(AUTOSCROLL_TAG).assertExists()
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag(AUTOSCROLL_TAG).assertDoesNotExist()

        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
        mainClock.advanceTimeBy(300)
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionDown) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag(AUTOSCROLL_TAG).assertDoesNotExist()

        // Leaving the reader: back to the screen below, and nothing keeps scrolling a list that's gone
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
        mainClock.advanceTimeBy(300)
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.Escape) }
        mainClock.autoAdvance = true
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText("pantalla anterior")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `the automatic scrolling stops by itself after the last chapter`() = runComposeUiTest {
        open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER, chapterIndex = 2)
        startTestApp().readerPreferences.webtoonAutoScrollSpeed.set(ReaderPreferences.WEBTOON_AUTOSCROLL_MAX)
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.MoveEnd) }
        waitForIdle()

        mainClock.autoAdvance = false
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
        mainClock.advanceTimeBy(300)
        onNodeWithTag(AUTOSCROLL_TAG).assertExists()
        // At the end with no chapter after it: stopped once the list hasn't grown for 2 s
        mainClock.advanceTimeBy(3_000)
        onNodeWithTag(AUTOSCROLL_TAG).assertDoesNotExist()
        mainClock.autoAdvance = true
    }
```

Import nuevo en `WebtoonAndDualTest.kt`: `androidx.compose.ui.test.hasText` (`AUTOSCROLL_TAG` está en el mismo paquete).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.WebtoonAndDualTest"`
Expected: FAIL to compile, `Unresolved reference 'webtoonAutoScrollSpeed'`, `'AUTOSCROLL_TAG'`, `'desktop_autoscroll'`.

- [ ] **Step 3: Preferencia, constantes y textos**

`ReaderPreferences.kt`, tras `webtoonScrollStep`:

```kotlin
    // desktop: how fast S scrolls the webtoon by itself, in percent of the window's height per second (spec 8a)
    val webtoonAutoScrollSpeed: Preference<Int> = preferenceStore.getInt("pref_webtoon_autoscroll_speed", 10)
```

y en el `companion object`, tras `WEBTOON_KEY_STEP`:

```kotlin
        const val WEBTOON_AUTOSCROLL_MIN = 2
        const val WEBTOON_AUTOSCROLL_MAX = 50
        const val WEBTOON_AUTOSCROLL_STEP = 2
```

`base/strings.xml`, tras `desktop_webtoon_scroll_step`:

```xml
    <string name="desktop_autoscroll">Automatic scrolling</string>
    <string name="desktop_webtoon_autoscroll_speed">Automatic scrolling speed (webtoon)</string>
```

`es/strings.xml`, tras `desktop_webtoon_scroll_step`:

```xml
    <string name="desktop_autoscroll">Desplazamiento automático</string>
    <string name="desktop_webtoon_autoscroll_speed">Velocidad del desplazamiento automático (webtoon)</string>
```

- [ ] **Step 4: El motor en el visor**

`ViewerItems.kt`, interfaz:

```kotlin
/** Turns, jumps and scrolls for the viewer on screen; the reader screen sends the keys and clicks here. */
interface ViewerController {
    suspend fun perform(action: ReaderAction)

    suspend fun goToPage(index: Int)

    /**
     * Scrolls by itself, [speed] of the window's height per second read on every frame, until cancelled or stuck
     * at the end. Webtoon only; the paged viewers do nothing.
     */
    suspend fun autoScroll(speed: () -> Float) {}
}
```

`WebtoonViewer.kt`, en `WebtoonController`, tras `goToPage`:

```kotlin
    // One scroll for the whole run: the wheel and a drag (MutatePriority.UserInput) cancel it, which is the pause
    override suspend fun autoScroll(speed: () -> Float) {
        state.scroll {
            var last = withFrameNanos { it }
            var stuckSince = -1L
            while (true) {
                val now = withFrameNanos { it }
                val delta = state.layoutInfo.viewportSize.height * speed() * (now - last) / NANOS_PER_SECOND
                last = now
                if (scrollBy(delta) > 0f) {
                    stuckSince = -1L
                } else if (stuckSince < 0) {
                    // The end: the next chapter comes on below, if there is one
                    stuckSince = now
                    onPastEnd()
                } else if (now - stuckSince > AUTOSCROLL_END_WAIT_NANOS) {
                    return@scroll
                }
            }
        }
    }
```

y junto a `LAST_PAGE_TRIES`:

```kotlin
private const val NANOS_PER_SECOND = 1_000_000_000f

// How long the automatic scrolling waits at the end for the next chapter to come on below
private const val AUTOSCROLL_END_WAIT_NANOS = 2_000_000_000L
```

- [ ] **Step 5: El lector: arranque, pausa, velocidad, aviso e indicador**

En `ReaderScreen.kt`, sustituir `fun adjust(...)` y `fun perform(...)` de `ReaderContent` por:

```kotlin
    fun showNotice(text: String) {
        notice = text to System.nanoTime()
    }

    fun adjust(action: ReaderAction) {
        val setting = when (action) {
            ReaderAction.ZoomIn, ReaderAction.ZoomOut -> WebtoonSetting.SidePadding
            ReaderAction.ScrollStepUp, ReaderAction.ScrollStepDown -> WebtoonSetting.ScrollStep
            else -> WebtoonSetting.AutoScrollSpeed
        }
        // Zooming in narrows the padding
        val up = action == ReaderAction.ZoomOut ||
            action == ReaderAction.ScrollStepUp ||
            action == ReaderAction.AutoScrollFaster
        val preference = setting.preference(preferences)
        val value = (preference.get() + (if (up) 1 else -1) * setting.step).coerceIn(setting.range)
        preference.set(value)
        showNotice("${context.stringResource(setting.title)}: $value ${setting.unit}")
    }

    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun toggleAutoScroll() {
        if (autoScrolling) return stopAutoScroll()
        val viewer = controller ?: return
        autoScrollJob = scope.launch {
            autoScrolling = true
            try {
                viewer.autoScroll { latestAutoScrollSpeed / 100f }
            } finally {
                autoScrolling = false
            }
        }
    }

    fun perform(action: ReaderAction) {
        // Reading on by hand stops the scrolling by itself; changing a setting doesn't
        if (action !in KeepsAutoScroll) stopAutoScroll()
        when (action) {
            ReaderAction.ToggleMenu -> viewModel.showMenus(!state.menuVisible)
            ReaderAction.ToggleAutoScroll -> toggleAutoScroll()
            ReaderAction.ZoomIn, ReaderAction.ZoomOut, ReaderAction.ZoomReset,
            ReaderAction.ScrollStepUp, ReaderAction.ScrollStepDown,
            ReaderAction.AutoScrollFaster, ReaderAction.AutoScrollSlower,
            -> adjust(action)
            ReaderAction.NextChapter -> scope.launch { viewModel.loadNextChapter() }
            ReaderAction.PreviousChapter -> scope.launch { viewModel.loadPreviousChapter() }
            else -> scope.launch { controller?.perform(action) }
        }
    }

    // The controls, a dialog (quick settings, the keyboard help) or another viewer stop it too
    LaunchedEffect(state.menuVisible, ShortcutHelp.open) {
        if (state.menuVisible || ShortcutHelp.open) stopAutoScroll()
    }
    DisposableEffect(mode) { onDispose { stopAutoScroll() } }
```

> `ShortcutHelp` llega en la tarea 4. Si la tarea 2 se ejecuta antes, escribir aquí `LaunchedEffect(state.menuVisible) { if (state.menuVisible) stopAutoScroll() }` y la tarea 4 añade `ShortcutHelp.open`.

Estado nuevo, junto a `var notice` en `ReaderContent`:

```kotlin
    val autoScrollSpeed by preferences.webtoonAutoScrollSpeed.collectAsState()
    val latestAutoScrollSpeed by rememberUpdatedState(autoScrollSpeed)
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    var autoScrolling by remember { mutableStateOf(false) }
```

El manejador de Ctrl+rueda (en el `Modifier` del `Box`) sigue llamando a `adjust(...)` directamente.

El indicador, justo antes de `notice?.let { ... }` dentro del `Box`:

```kotlin
        if (autoScrolling) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp).testTag(AUTOSCROLL_TAG),
            ) {
                Icon(
                    MaterialSymbols.RoundedFilled.PlayArrow,
                    contentDescription = stringResource(MR.strings.desktop_autoscroll),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
```

Al final de `ReaderScreen.kt`:

```kotlin
const val AUTOSCROLL_TAG = "autoscroll on"

private val KeepsAutoScroll = setOf(
    ReaderAction.ToggleAutoScroll,
    ReaderAction.AutoScrollFaster,
    ReaderAction.AutoScrollSlower,
    ReaderAction.ZoomIn,
    ReaderAction.ZoomOut,
    ReaderAction.ZoomReset,
    ReaderAction.ScrollStepUp,
    ReaderAction.ScrollStepDown,
)

/** The webtoon settings the reader's Ctrl keys change in place, with the note that says the new value. */
private enum class WebtoonSetting(val title: StringResource, val range: IntRange, val step: Int, val unit: String) {
    SidePadding(
        MR.strings.pref_webtoon_side_padding,
        ReaderPreferences.WEBTOON_PADDING_MIN..ReaderPreferences.WEBTOON_PADDING_MAX,
        ReaderPreferences.WEBTOON_KEY_STEP,
        "%",
    ),
    ScrollStep(
        MR.strings.desktop_webtoon_scroll_step,
        ReaderPreferences.WEBTOON_SCROLL_STEP_MIN..ReaderPreferences.WEBTOON_SCROLL_STEP_MAX,
        ReaderPreferences.WEBTOON_KEY_STEP,
        "%",
    ),
    AutoScrollSpeed(
        MR.strings.desktop_autoscroll,
        ReaderPreferences.WEBTOON_AUTOSCROLL_MIN..ReaderPreferences.WEBTOON_AUTOSCROLL_MAX,
        ReaderPreferences.WEBTOON_AUTOSCROLL_STEP,
        "%/s",
    ),
    ;

    fun preference(preferences: ReaderPreferences) = when (this) {
        SidePadding -> preferences.webtoonSidePadding
        ScrollStep -> preferences.webtoonScrollStep
        AutoScrollSpeed -> preferences.webtoonAutoScrollSpeed
    }
}
```

Imports nuevos en `ReaderScreen.kt`: `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.material3.Icon`,
`androidx.compose.runtime.rememberUpdatedState`, `dev.icerock.moko.resources.StringResource`,
`kotlinx.coroutines.Job`, `mihon.icons.materialsymbols.roundedfilled.PlayArrow`,
`tachiyomi.presentation.core.i18n.stringResource` (el composable; convive con el `stringResource` de `Context` ya
importado), y `mihon.desktop.app.main.ShortcutHelp` cuando exista (tarea 4).

- [ ] **Step 6: Sliders**

`ReaderQuickSettings.kt`: leer `val autoScrollSpeed by preferences.webtoonAutoScrollSpeed.collectAsState()` junto a
`scrollStep`, y dentro del `if (mode.type == ReadingMode.ViewerType.Webtoon)`, tras el slider del avance:

```kotlin
                    BaseSliderItem(
                        value = autoScrollSpeed,
                        valueRange =
                        ReaderPreferences.WEBTOON_AUTOSCROLL_MIN..ReaderPreferences.WEBTOON_AUTOSCROLL_MAX,
                        // Stops every 2 %/s, like Ctrl+Shift+↑ and ↓
                        steps = (ReaderPreferences.WEBTOON_AUTOSCROLL_MAX - ReaderPreferences.WEBTOON_AUTOSCROLL_MIN) /
                            ReaderPreferences.WEBTOON_AUTOSCROLL_STEP - 1,
                        title = stringResource(MR.strings.desktop_webtoon_autoscroll_speed),
                        valueString = "$autoScrollSpeed %/s",
                        onChange = preferences.webtoonAutoScrollSpeed::set,
                        titleStyle = MaterialTheme.typography.titleSmall,
                    )
```

Y actualizar la KDoc de `ReaderQuickSettings` a "...the webtoon's side padding, scroll step and automatic scrolling speed."

`SettingsReaderScreen.kt`, tras el `SliderPreference` de `webtoonScrollStep`:

```kotlin
            Preference.PreferenceItem.SliderPreference(
                preference = preferences.webtoonAutoScrollSpeed,
                title = stringResource(MR.strings.desktop_webtoon_autoscroll_speed),
                valueText = { "$it %/s" },
                valueRange = ReaderPreferences.WEBTOON_AUTOSCROLL_MIN..ReaderPreferences.WEBTOON_AUTOSCROLL_MAX,
            ),
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.*"`
Expected: PASS, incluidos los tres tests nuevos y `the quick settings have the webtoon's side padding and scroll step`.

- [ ] **Step 8: Commit (con orden del usuario)**

```bash
git add desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/ViewerItems.kt desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/WebtoonViewer.kt desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderQuickSettings.kt desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsReaderScreen.kt i18n/src/commonMain/moko-resources/base/strings.xml i18n/src/commonMain/moko-resources/es/strings.xml desktop-app/src/test/kotlin/mihon/desktop/app/reader/WebtoonAndDualTest.kt
git commit -q -F - <<'EOF'
feat: S scrolls the webtoon by itself, at a speed set with ctrl+shift+up/down or the quick settings
EOF
```

---

### Task 3: Zoom con teclado en modo páginas

**Files:**
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/PageContent.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/PagerViewer.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/ViewerItems.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt`
- Modify: `i18n/src/commonMain/moko-resources/base/strings.xml`, `.../es/strings.xml`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/ReaderControlsTest.kt`

**Interfaces:**
- Consumes: `ReaderAction.ZoomIn`, `ZoomOut`, `ZoomReset` (tarea 1); `showNotice` (tarea 2).
- Produces: `fun interface PageZoomer { suspend fun zoom(action: ReaderAction): Float }` en `PageContent.kt`;
  parámetro `PageContent(onZoomer: (PageZoomer?) -> Unit = {})`; `ViewerController.zoom(action): Float?` (por
  defecto `null`); string `desktop_zoom`.

- [ ] **Step 1: Write the failing test**

En `ReaderControlsTest.kt` (abre `LEFT_TO_RIGHT`):

```kotlin
    @Test
    fun `ctrl plus and minus zoom the page with a note, ctrl+0 goes back, and a new page starts at 100 %`() =
        runComposeUiTest {
            open()
            fun ctrl(key: Key) = onNodeWithTag(READER_TAG).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(key) } }
            fun zoomNote(percent: Int) = "${str(MR.strings.desktop_zoom)}: $percent %"

            // Before the page is drawn there's nothing to zoom: no note, no error
            ctrl(Key.Plus)
            waitForTag("page 1 ready")
            onAllNodes(hasText(zoomNote(125))).fetchSemanticsNodes().size shouldBe 0

            ctrl(Key.Plus)
            waitFor(zoomNote(125))
            ctrl(Key.Plus)
            waitFor(zoomNote(156))
            ctrl(Key.Minus)
            waitFor(zoomNote(125))
            ctrl(Key.Zero)
            waitFor(zoomNote(100))

            // Never past 5×
            repeat(10) { ctrl(Key.Plus) }
            waitFor(zoomNote(500))

            // The next page starts over
            onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
            waitForTag("page 2 ready")
            ctrl(Key.Plus)
            waitFor(zoomNote(125))
        }
```

Imports: `androidx.compose.ui.test.withKeyDown`, `io.kotest.matchers.shouldBe`.

> El primer `ctrl(Key.Plus)` antes de `page 1 ready` puede llegar tarde y ver la página ya dibujada en una máquina rápida; si el test falla solo por eso, quitar esas dos líneas y dejar el caso en la Review Focus para la verificación visual.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.ReaderControlsTest"`
Expected: FAIL to compile, `Unresolved reference 'desktop_zoom'`.

- [ ] **Step 3: Texto**

`base/strings.xml` tras `desktop_webtoon_autoscroll_speed`: `<string name="desktop_zoom">Zoom</string>`;
`es/strings.xml`: `<string name="desktop_zoom">Zoom</string>`.

- [ ] **Step 4: La página publica su zoom**

`PageContent.kt`:
- Parámetro nuevo al final de `PageContent`: `onZoomer: (PageZoomer?) -> Unit = {},`.
- Sustituir `var width by remember { mutableIntStateOf(1) }` por `var size by remember { mutableStateOf(IntSize(1, 1)) }`,
  `.onSizeChanged { width = it.width }` por `.onSizeChanged { size = it }` y `onTap(offset.x, width.toFloat())` por
  `onTap(offset.x, size.width.toFloat())`.
- `rememberZoomState(maxScale = 5f)` pasa a `rememberZoomState(maxScale = MAX_ZOOM)`.
- Tras `val zoomState = key(page, isCurrent) { ... }`:

```kotlin
                if (zoom && isCurrent) {
                    DisposableEffect(zoomState) {
                        onZoomer(PageZoomer { action -> zoomState.zoomBy(action, size) })
                        onDispose { onZoomer(null) }
                    }
                }
```

- Al final del archivo:

```kotlin
/** Zooms the current page from the keyboard (Ctrl+±, Ctrl+0); returns the new scale. */
fun interface PageZoomer {
    suspend fun zoom(action: ReaderAction): Float
}

private const val MAX_ZOOM = 5f

// Each Ctrl+± multiplies or divides the scale by this
private const val KEY_ZOOM_FACTOR = 1.25f

private suspend fun ZoomState.zoomBy(action: ReaderAction, layout: IntSize): Float {
    val target = when (action) {
        ReaderAction.ZoomIn -> scale * KEY_ZOOM_FACTOR
        ReaderAction.ZoomOut -> scale / KEY_ZOOM_FACTOR
        else -> 1f
    }.coerceIn(1f, MAX_ZOOM)
    changeScale(target, Offset(layout.width / 2f, layout.height / 2f))
    return target
}
```

Imports: `androidx.compose.runtime.DisposableEffect`, `androidx.compose.runtime.mutableStateOf`,
`mihon.desktop.app.reader.ReaderAction`, `net.engawapg.lib.zoomable.ZoomState`; quitar `mutableIntStateOf` si queda
sin uso.

- [ ] **Step 5: El visor y el controlador**

`ViewerItems.kt`, en `ViewerController`:

```kotlin
    /** Paged: zooms the page or pages on screen and returns the new scale; null where there's nothing to zoom. */
    suspend fun zoom(action: ReaderAction): Float? = null
```

`PagerViewer.kt`:
- Junto a `sizes`: `val zoomers = remember { mutableMapOf<PageKey, PageZoomer>() }`.
- `PagerController(state, { latestItems }, { latestCurrent }, { zoomers.values.toList() }) { latestOnPastEnd(it) }`.
- En `PageContent(...)`: `onZoomer = { zoomer -> if (zoomer == null) zoomers.remove(page.pageKey) else zoomers[page.pageKey] = zoomer },`.
- `PagerController` recibe `private val zoomers: () -> List<PageZoomer>,` antes de `onPastEnd`, y añade:

```kotlin
    // Both pages of a spread at once
    override suspend fun zoom(action: ReaderAction): Float? = coroutineScope {
        zoomers().map { async { it.zoom(action) } }.awaitAll().firstOrNull()
    }
```

Imports: `kotlinx.coroutines.async`, `kotlinx.coroutines.awaitAll`, `kotlinx.coroutines.coroutineScope`.

- [ ] **Step 6: El lector separa espaciado y zoom por modo**

`ReaderScreen.kt`, en `ReaderContent`, nueva función junto a `adjust`:

```kotlin
    fun zoom(action: ReaderAction) {
        scope.launch {
            val scale = controller?.zoom(action) ?: return@launch
            showNotice("${context.stringResource(MR.strings.desktop_zoom)}: ${(scale * 100).roundToInt()} %")
        }
    }
```

y en `perform`, la rama de ajustes pasa a:

```kotlin
            ReaderAction.ZoomIn, ReaderAction.ZoomOut, ReaderAction.ZoomReset ->
                if (mode.type == ReadingMode.ViewerType.Webtoon) adjust(action) else zoom(action)
            ReaderAction.ScrollStepUp, ReaderAction.ScrollStepDown,
            ReaderAction.AutoScrollFaster, ReaderAction.AutoScrollSlower,
            -> adjust(action)
```

Import: `kotlin.math.roundToInt`.

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.*"`
Expected: PASS (también los de webtoon: Ctrl+`+` sigue siendo el espaciado allí).

- [ ] **Step 8: Commit (con orden del usuario)**

```bash
git add desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/PageContent.kt desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/PagerViewer.kt desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/ViewerItems.kt desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt i18n/src/commonMain/moko-resources/base/strings.xml i18n/src/commonMain/moko-resources/es/strings.xml desktop-app/src/test/kotlin/mihon/desktop/app/reader/ReaderControlsTest.kt
git commit -q -F - <<'EOF'
feat: ctrl+plus, ctrl+minus and ctrl+0 zoom the page in paged reading, with a note
EOF
```

---

### Task 4: Ayuda de atajos (F1)

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/app/main/ShortcutHelp.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/Shortcut.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/AppEvents.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/main/AppContent.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/settings/AboutScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt`
- Modify: `i18n/src/commonMain/moko-resources/base/strings.xml`, `.../es/strings.xml`
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/main/ShortcutsTest.kt`, Create: `desktop-app/src/test/kotlin/mihon/desktop/app/main/ShortcutHelpTest.kt`, Modify: `desktop-app/src/test/kotlin/mihon/desktop/app/reader/WebtoonAndDualTest.kt`

**Interfaces:**
- Consumes: todas las `ReaderAction` (tareas 1–3); `stopAutoScroll` (tarea 2).
- Produces: `Shortcut.Help`; `object ShortcutHelp { var open: Boolean; var readerViewer: ReadingMode.ViewerType?; val appRows; val readerRows; fun readerRowsFor(viewer) }`; `data class ShortcutRow`; `@Composable fun ShortcutHelpDialog(viewer, onDismissRequest)`; `const val SHORTCUT_HELP_TAG = "keyboard help"`.

> Desviación del spec §3.C, a anotar en el spec en la tarea 5: el modo del lector va en `ShortcutHelp.readerViewer`
> (estado global), no en un `CompositionLocal`, porque el diálogo vive en la raíz de `AppContent`, por encima del
> lector; y las teclas de cada fila son también `StringResource` ("Re Pág" / "Page Up"), no un `String` fijo.

- [ ] **Step 1: Write the failing tests**

`ShortcutsTest.kt`, en `desktop shortcuts from the spec`:

```kotlin
        shortcutFor(Key.F1, ctrl = false, alt = false, shift = false) shouldBe Shortcut.Help
```

y en `back waits for the focused component, the others go first`: `Shortcut.Help.beforeFocusedComponent shouldBe true`.

`ShortcutHelpTest.kt` (nuevo):

```kotlin
package mihon.desktop.app.main

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class ShortcutHelpTest {
    @Test
    fun `every window shortcut and reader action has a line in the help`() {
        val covered = (ShortcutHelp.appRows + ShortcutHelp.readerRows).flatMap { it.covers }.toSet()
        (Shortcut.entries.toSet() - covered) shouldBe emptySet()
        (ReaderAction.entries.toSet() - covered) shouldBe emptySet()
    }

    @Test
    fun `in a reader the help has its viewer's lines only, without one both kinds`() {
        val webtoonOnly = ShortcutHelp.readerRows.first { it.viewer == ReadingMode.ViewerType.Webtoon }
        val pagedOnly = ShortcutHelp.readerRows.first { it.viewer == ReadingMode.ViewerType.Pager }
        ShortcutHelp.readerRowsFor(ReadingMode.ViewerType.Webtoon) shouldContain webtoonOnly
        ShortcutHelp.readerRowsFor(ReadingMode.ViewerType.Webtoon) shouldNotContain pagedOnly
        ShortcutHelp.readerRowsFor(null) shouldContain webtoonOnly
        ShortcutHelp.readerRowsFor(null) shouldContain pagedOnly
    }

    @Test
    fun `F1 opens the help over the library, with both viewers' keys, and Esc closes it`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }
        try {
            runOnIdle { AppEvents.send(Shortcut.Help) }
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(SHORTCUT_HELP_TAG)).fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(hasText(str(MR.strings.desktop_help_autoscroll), substring = true))
                .fetchSemanticsNodes().isNotEmpty() shouldBe true
            onAllNodes(hasText(str(MR.strings.desktop_help_only_webtoon), substring = true))
                .fetchSemanticsNodes().isNotEmpty() shouldBe true

            onNodeWithTag(SHORTCUT_HELP_TAG).performKeyInput { pressKey(Key.Escape) }
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(SHORTCUT_HELP_TAG)).fetchSemanticsNodes().isEmpty() }
        } finally {
            ShortcutHelp.open = false
        }
    }
}
```

`WebtoonAndDualTest.kt`:

```kotlin
    @Test
    fun `the reader tells the help its viewer, and opening the help stops the automatic scrolling`() =
        runComposeUiTest {
            open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
            waitForTag("page 1 ready")
            ShortcutHelp.readerViewer shouldBe ReadingMode.ViewerType.Webtoon
            try {
                mainClock.autoAdvance = false
                onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
                mainClock.advanceTimeBy(300)
                onNodeWithTag(AUTOSCROLL_TAG).assertExists()
                ShortcutHelp.open = true
                mainClock.advanceTimeBy(100)
                onNodeWithTag(AUTOSCROLL_TAG).assertDoesNotExist()
            } finally {
                ShortcutHelp.open = false
                mainClock.autoAdvance = true
            }
        }
```

Import: `mihon.desktop.app.main.ShortcutHelp`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.main.*" --tests "mihon.desktop.app.reader.WebtoonAndDualTest"`
Expected: FAIL to compile, `Unresolved reference 'Help'`, `'ShortcutHelp'`, `'SHORTCUT_HELP_TAG'`.

- [ ] **Step 3: Textos**

`base/strings.xml`, tras `desktop_zoom`:

```xml
    <string name="desktop_help_title">Keyboard shortcuts</string>
    <string name="desktop_help_app">App</string>
    <string name="desktop_help_reader">Reader</string>
    <string name="desktop_help_only_webtoon">(webtoon)</string>
    <string name="desktop_help_only_paged">(paged)</string>
    <string name="desktop_help_keys_help">F1</string>
    <string name="desktop_help_help">Show these shortcuts</string>
    <string name="desktop_help_keys_global_search">Ctrl + F</string>
    <string name="desktop_help_global_search">Search every source</string>
    <string name="desktop_help_keys_refresh">F5</string>
    <string name="desktop_help_refresh">Refresh the screen</string>
    <string name="desktop_help_keys_fullscreen">F11</string>
    <string name="desktop_help_fullscreen">Fullscreen</string>
    <string name="desktop_help_keys_settings">Ctrl + ,</string>
    <string name="desktop_help_settings">Open the settings</string>
    <string name="desktop_help_keys_select_all">Ctrl + A</string>
    <string name="desktop_help_select_all">Select everything in the library</string>
    <string name="desktop_help_keys_categories">Ctrl + Tab · Ctrl + Shift + Tab</string>
    <string name="desktop_help_categories">Next / previous library category</string>
    <string name="desktop_help_keys_back">Alt + ← · Esc</string>
    <string name="desktop_help_back">Go back</string>
    <string name="desktop_help_keys_scroll_lists">Page Up · Page Down · Home · End · ↑ ↓ · Space</string>
    <string name="desktop_help_scroll_lists">Scroll lists and grids</string>
    <string name="desktop_help_keys_turn_paged">→ ← · ↓ ↑ · Page Down · Page Up · Space · Shift + Space</string>
    <string name="desktop_help_keys_turn_webtoon">→ ← · Page Down · Page Up · Space · Shift + Space</string>
    <string name="desktop_help_turn">Next / previous page (in the reading direction)</string>
    <string name="desktop_help_keys_scroll">↓ ↑</string>
    <string name="desktop_help_scroll">Scroll down / up</string>
    <string name="desktop_help_keys_first_last">Home · End</string>
    <string name="desktop_help_first_last">First / last page of the chapter</string>
    <string name="desktop_help_keys_chapters">N · P</string>
    <string name="desktop_help_chapters">Next / previous chapter</string>
    <string name="desktop_help_keys_menu">Click in the middle</string>
    <string name="desktop_help_menu">Show the controls</string>
    <string name="desktop_help_keys_zoom">Ctrl + + · Ctrl + − · Ctrl + 0 · Ctrl + wheel · double click</string>
    <string name="desktop_help_zoom">Zoom in / out, back to 100 %</string>
    <string name="desktop_help_keys_side_padding">Ctrl + + · Ctrl + − · Ctrl + wheel</string>
    <string name="desktop_help_side_padding">Less / more side padding</string>
    <string name="desktop_help_keys_scroll_step">Ctrl + ↑ · Ctrl + ↓</string>
    <string name="desktop_help_scroll_step">Longer / shorter arrow scroll</string>
    <string name="desktop_help_keys_autoscroll">S</string>
    <string name="desktop_help_autoscroll">Start / stop automatic scrolling</string>
    <string name="desktop_help_keys_autoscroll_speed">Ctrl + Shift + ↑ · Ctrl + Shift + ↓</string>
    <string name="desktop_help_autoscroll_speed">Faster / slower automatic scrolling</string>
```

`es/strings.xml`, tras `desktop_zoom`:

```xml
    <string name="desktop_help_title">Atajos de teclado</string>
    <string name="desktop_help_app">Aplicación</string>
    <string name="desktop_help_reader">Lector</string>
    <string name="desktop_help_only_webtoon">(webtoon)</string>
    <string name="desktop_help_only_paged">(páginas)</string>
    <string name="desktop_help_keys_help">F1</string>
    <string name="desktop_help_help">Mostrar estos atajos</string>
    <string name="desktop_help_keys_global_search">Ctrl + F</string>
    <string name="desktop_help_global_search">Buscar en todas las fuentes</string>
    <string name="desktop_help_keys_refresh">F5</string>
    <string name="desktop_help_refresh">Actualizar la pantalla</string>
    <string name="desktop_help_keys_fullscreen">F11</string>
    <string name="desktop_help_fullscreen">Pantalla completa</string>
    <string name="desktop_help_keys_settings">Ctrl + ,</string>
    <string name="desktop_help_settings">Abrir los ajustes</string>
    <string name="desktop_help_keys_select_all">Ctrl + A</string>
    <string name="desktop_help_select_all">Seleccionar todo en la biblioteca</string>
    <string name="desktop_help_keys_categories">Ctrl + Tab · Ctrl + Mayús + Tab</string>
    <string name="desktop_help_categories">Categoría siguiente / anterior de la biblioteca</string>
    <string name="desktop_help_keys_back">Alt + ← · Esc</string>
    <string name="desktop_help_back">Volver</string>
    <string name="desktop_help_keys_scroll_lists">Re Pág · Av Pág · Inicio · Fin · ↑ ↓ · Espacio</string>
    <string name="desktop_help_scroll_lists">Desplazar listas y cuadrículas</string>
    <string name="desktop_help_keys_turn_paged">→ ← · ↓ ↑ · Av Pág · Re Pág · Espacio · Mayús + Espacio</string>
    <string name="desktop_help_keys_turn_webtoon">→ ← · Av Pág · Re Pág · Espacio · Mayús + Espacio</string>
    <string name="desktop_help_turn">Página siguiente / anterior (en el sentido de lectura)</string>
    <string name="desktop_help_keys_scroll">↓ ↑</string>
    <string name="desktop_help_scroll">Bajar / subir</string>
    <string name="desktop_help_keys_first_last">Inicio · Fin</string>
    <string name="desktop_help_first_last">Primera / última página del capítulo</string>
    <string name="desktop_help_keys_chapters">N · P</string>
    <string name="desktop_help_chapters">Capítulo siguiente / anterior</string>
    <string name="desktop_help_keys_menu">Clic en el centro</string>
    <string name="desktop_help_menu">Mostrar los controles</string>
    <string name="desktop_help_keys_zoom">Ctrl + + · Ctrl + − · Ctrl + 0 · Ctrl + rueda · doble clic</string>
    <string name="desktop_help_zoom">Ampliar / reducir, volver al 100 %</string>
    <string name="desktop_help_keys_side_padding">Ctrl + + · Ctrl + − · Ctrl + rueda</string>
    <string name="desktop_help_side_padding">Menos / más espaciado lateral</string>
    <string name="desktop_help_keys_scroll_step">Ctrl + ↑ · Ctrl + ↓</string>
    <string name="desktop_help_scroll_step">Más / menos avance con las flechas</string>
    <string name="desktop_help_keys_autoscroll">S</string>
    <string name="desktop_help_autoscroll">Iniciar / parar el desplazamiento automático</string>
    <string name="desktop_help_keys_autoscroll_speed">Ctrl + Mayús + ↑ · Ctrl + Mayús + ↓</string>
    <string name="desktop_help_autoscroll_speed">Desplazamiento automático más rápido / más lento</string>
```

- [ ] **Step 4: Atajo y apertura**

`Shortcut.kt`: añadir `Help(true),` tras `Fullscreen(true),` y en `shortcutFor`, antes de `else -> null`:

```kotlin
    key == Key.F1 && !ctrl && !alt -> Shortcut.Help
```

`AppEvents.kt`, en `send`:

```kotlin
        Shortcut.Help -> {
            ShortcutHelp.open = true
            true
        }
```

- [ ] **Step 5: La tabla y el diálogo**

`ShortcutHelp.kt` (nuevo):

```kotlin
package mihon.desktop.app.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import mihon.desktop.app.reader.ReaderAction
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.keyboardScrollable

const val SHORTCUT_HELP_TAG = "keyboard help"

/** One line of the keyboard help (spec 8a §3 C): the keys as they're pressed and what they do. */
data class ShortcutRow(
    val keys: StringResource,
    val description: StringResource,
    /** The window shortcuts or reader actions it explains, so a test can tell none is missing. */
    val covers: Set<Enum<*>> = emptySet(),
    /** A reader line for one kind of viewer only; null for both. */
    val viewer: ReadingMode.ViewerType? = null,
)

/** Whether the keyboard help is open, and the open reader's kind of viewer, whose keys it then shows. */
object ShortcutHelp {
    var open by mutableStateOf(false)
    var readerViewer by mutableStateOf<ReadingMode.ViewerType?>(null)

    val appRows = listOf(
        ShortcutRow(MR.strings.desktop_help_keys_help, MR.strings.desktop_help_help, setOf(Shortcut.Help)),
        ShortcutRow(
            MR.strings.desktop_help_keys_global_search,
            MR.strings.desktop_help_global_search,
            setOf(Shortcut.GlobalSearch),
        ),
        ShortcutRow(MR.strings.desktop_help_keys_refresh, MR.strings.desktop_help_refresh, setOf(Shortcut.Refresh)),
        ShortcutRow(
            MR.strings.desktop_help_keys_fullscreen,
            MR.strings.desktop_help_fullscreen,
            setOf(Shortcut.Fullscreen),
        ),
        ShortcutRow(MR.strings.desktop_help_keys_settings, MR.strings.desktop_help_settings, setOf(Shortcut.Settings)),
        ShortcutRow(
            MR.strings.desktop_help_keys_select_all,
            MR.strings.desktop_help_select_all,
            setOf(Shortcut.SelectAll),
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_categories,
            MR.strings.desktop_help_categories,
            setOf(Shortcut.NextCategory, Shortcut.PreviousCategory),
        ),
        ShortcutRow(MR.strings.desktop_help_keys_back, MR.strings.desktop_help_back, setOf(Shortcut.Back)),
        // Subproyecto 7's keys: no Shortcut of their own
        ShortcutRow(MR.strings.desktop_help_keys_scroll_lists, MR.strings.desktop_help_scroll_lists),
    )

    val readerRows = listOf(
        ShortcutRow(
            MR.strings.desktop_help_keys_turn_paged,
            MR.strings.desktop_help_turn,
            setOf(ReaderAction.NextPage, ReaderAction.PreviousPage),
            ReadingMode.ViewerType.Pager,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_turn_webtoon,
            MR.strings.desktop_help_turn,
            setOf(ReaderAction.NextPage, ReaderAction.PreviousPage),
            ReadingMode.ViewerType.Webtoon,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_scroll,
            MR.strings.desktop_help_scroll,
            setOf(ReaderAction.ScrollDown, ReaderAction.ScrollUp),
            ReadingMode.ViewerType.Webtoon,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_first_last,
            MR.strings.desktop_help_first_last,
            setOf(ReaderAction.FirstPage, ReaderAction.LastPage),
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_chapters,
            MR.strings.desktop_help_chapters,
            setOf(ReaderAction.NextChapter, ReaderAction.PreviousChapter),
        ),
        ShortcutRow(MR.strings.desktop_help_keys_menu, MR.strings.desktop_help_menu, setOf(ReaderAction.ToggleMenu)),
        ShortcutRow(
            MR.strings.desktop_help_keys_zoom,
            MR.strings.desktop_help_zoom,
            setOf(ReaderAction.ZoomIn, ReaderAction.ZoomOut, ReaderAction.ZoomReset),
            ReadingMode.ViewerType.Pager,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_side_padding,
            MR.strings.desktop_help_side_padding,
            setOf(ReaderAction.ZoomIn, ReaderAction.ZoomOut),
            ReadingMode.ViewerType.Webtoon,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_scroll_step,
            MR.strings.desktop_help_scroll_step,
            setOf(ReaderAction.ScrollStepUp, ReaderAction.ScrollStepDown),
            ReadingMode.ViewerType.Webtoon,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_autoscroll,
            MR.strings.desktop_help_autoscroll,
            setOf(ReaderAction.ToggleAutoScroll),
            ReadingMode.ViewerType.Webtoon,
        ),
        ShortcutRow(
            MR.strings.desktop_help_keys_autoscroll_speed,
            MR.strings.desktop_help_autoscroll_speed,
            setOf(ReaderAction.AutoScrollFaster, ReaderAction.AutoScrollSlower),
            ReadingMode.ViewerType.Webtoon,
        ),
    )

    /** The reader lines for [viewer], or for both kinds when no reader is open. */
    fun readerRowsFor(viewer: ReadingMode.ViewerType?): List<ShortcutRow> =
        readerRows.filter { viewer == null || it.viewer == null || it.viewer == viewer }
}

/** The keyboard help: the app's shortcuts, then the reader's for [viewer] (both kinds when null). */
@Composable
fun ShortcutHelpDialog(viewer: ReadingMode.ViewerType?, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 720.dp).testTag(SHORTCUT_HELP_TAG),
        ) {
            val scroll = rememberScrollState()
            Column(
                Modifier.keyboardScrollable(scroll).verticalScroll(scroll).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(MR.strings.desktop_help_title), style = MaterialTheme.typography.headlineSmall)
                Section(MR.strings.desktop_help_app, ShortcutHelp.appRows, markViewer = false)
                Section(MR.strings.desktop_help_reader, ShortcutHelp.readerRowsFor(viewer), markViewer = viewer == null)
            }
        }
    }
}

@Composable
private fun Section(title: StringResource, rows: List<ShortcutRow>, markViewer: Boolean) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(row.keys), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(280.dp))
            val mark = when {
                !markViewer || row.viewer == null -> ""
                row.viewer == ReadingMode.ViewerType.Webtoon -> " " + stringResource(MR.strings.desktop_help_only_webtoon)
                else -> " " + stringResource(MR.strings.desktop_help_only_paged)
            }
            Text(stringResource(row.description) + mark, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

- [ ] **Step 6: Mostrarla y conectarla**

`AppContent.kt`, dentro del `Box(Modifier.weight(1f)...)`, tras `EmbeddedBrowserConsent(...) { ... }`:

```kotlin
                    // F1 or Settings › About (spec 8a §3 C), over whatever screen is open
                    if (ShortcutHelp.open) {
                        ShortcutHelpDialog(ShortcutHelp.readerViewer) { ShortcutHelp.open = false }
                    }
```

`AboutScreen.kt`, tras la preferencia `desktop_update_check_auto`:

```kotlin
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.desktop_help_title),
                subtitle = stringResource(MR.strings.desktop_help_keys_help),
                onClick = { ShortcutHelp.open = true },
            ),
```

con `import mihon.desktop.app.main.ShortcutHelp`.

`ReaderScreen.kt`, en `ReaderContent`: el `DisposableEffect(mode)` de la tarea 2 pasa a:

```kotlin
    // The keyboard help shows this viewer's keys while the reader is open
    DisposableEffect(mode) {
        ShortcutHelp.readerViewer = mode.type
        onDispose {
            stopAutoScroll()
            ShortcutHelp.readerViewer = null
        }
    }
```

y el `LaunchedEffect` de pausa incluye `ShortcutHelp.open` como en la tarea 2 (paso 5). Import `mihon.desktop.app.main.ShortcutHelp`.

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.main.*" --tests "mihon.desktop.app.reader.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

- [ ] **Step 8: Commit (con orden del usuario)**

```bash
git add desktop-app/src/main/kotlin/mihon/desktop/app/main/ShortcutHelp.kt desktop-app/src/main/kotlin/mihon/desktop/app/main/Shortcut.kt desktop-app/src/main/kotlin/mihon/desktop/app/main/AppEvents.kt desktop-app/src/main/kotlin/mihon/desktop/app/main/AppContent.kt desktop-app/src/main/kotlin/mihon/desktop/app/settings/AboutScreen.kt desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt i18n/src/commonMain/moko-resources/base/strings.xml i18n/src/commonMain/moko-resources/es/strings.xml desktop-app/src/test/kotlin/mihon/desktop/app/main/ShortcutsTest.kt desktop-app/src/test/kotlin/mihon/desktop/app/main/ShortcutHelpTest.kt desktop-app/src/test/kotlin/mihon/desktop/app/reader/WebtoonAndDualTest.kt
git commit -q -F - <<'EOF'
feat: F1 shows the keyboard shortcuts, the reader's for the viewer that's open
EOF
```

---

### Task 5: Cierre: spec, verificación completa y en vivo

**Files:**
- Modify: `docs/superpowers/specs/2026-10-26-subproyecto-8a-teclado-lector-design.md` (§3.C)

- [ ] **Step 1: Ajustar el spec a lo construido**

En §3.C sustituir la línea de `ShortcutRow` por:
"`data class ShortcutRow(val keys: StringResource, val description: StringResource, val covers: Set<Enum<*>>, val viewer: ReadingMode.ViewerType?)`: las teclas también se traducen ("Re Pág" / "Page Up")."
y la de `LocalReaderMode` por:
"El lector guarda su tipo de visor en `ShortcutHelp.readerViewer` mientras está abierto: el diálogo vive en la raíz de `AppContent`, por encima del lector, y un `CompositionLocal` del lector no le llega."

- [ ] **Step 2: Verificación completa**

Run: `./gradlew spotlessApply` y después `./gradlew spotlessCheck test --continue`; luego `bash scripts/check-licenses.sh && bash scripts/check-no-android.sh`.
Expected: todo en verde.

- [ ] **Step 3: Verificación en vivo (skills `perfil-aislado` y `verificacion-visual`)**

Perfil temporal con un webtoon local (`storage/local/<manga>/<capítulo>/NNN.png`, como en la sesión del 2026-10-26)
y un manga de dos capítulos en modo páginas. Script de entrada fijado al PID de la ventana de prueba (`APP_PID`).
Comprobar con capturas:
1. Webtoon: `S` → la página baja sola y aparece el icono; Ctrl+Shift+↑ → aviso "Desplazamiento automático: 12 %/s"; rueda → se para.
2. Modo páginas con **doble página** ("Siempre"): Ctrl+`+` amplía **las dos** páginas; Ctrl+0 vuelve; aviso "Zoom: 125 %".
3. F1 en la biblioteca (ambos visores, con "(webtoon)"/"(páginas)") y dentro del lector webtoon (solo sus filas).
4. Ajustes › Acerca de › "Atajos de teclado" abre la misma ayuda.
Cerrar la app por PID y borrar el perfil.

- [ ] **Step 4: Grafo**

Run: `graphify update .`

- [ ] **Step 5: Commit y PR (con orden del usuario)**

```bash
git add docs/superpowers/specs/2026-10-26-subproyecto-8a-teclado-lector-design.md docs/superpowers/plans/2026-10-26-subproyecto-8a-teclado-lector.md
git commit -q -F - <<'EOF'
docs: subproyecto 8a spec and plan, reader keyboard
EOF
```

PR apilado: `gh pr create --base feat/reader-webtoon-quick-settings --head feat/reader-keyboard-8a`, título y cuerpo
en inglés sin firma, enlazando spec y plan.
