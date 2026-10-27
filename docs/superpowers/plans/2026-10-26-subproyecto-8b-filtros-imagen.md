# Subproyecto 8b: filtros de imagen — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Brillo propio, filtro de color, grises e inversión en el lector de escritorio.

**Architecture:** Una matriz de color pura (`readerColorMatrix`) que las páginas reciben por un `CompositionLocal`, y
el overlay de Mihon portado encima de los visores. La interfaz va en los ajustes rápidos.

**Tech Stack:** Kotlin, Compose Multiplatform (desktop), moko-resources, JUnit 5 + kotest, `runComposeUiTest`.

**Spec:** `docs/superpowers/specs/2026-10-26-subproyecto-8b-filtros-imagen-design.md`

## Global Constraints
- Rama `feat/reader-filters-8b`, desde `feat/reader-keyboard-8a` (PR #8).
- Sin strings nuevos: los de Mihon ya existen con `es`.
- Sin imports de Android (`check-no-android.sh`): nada de `androidx.annotation`.
- Brillo en escritorio solo −75…0.

## Review Focus
- Un backup de Android con brillo positivo: el lector no se oscurece ni falla. → Tarea 2 (coerceAtMost(0)), test.
- Grises + invertir a la vez: se aplican los dos. → Tarea 1, test.
- Página animada (GIF): también filtrada. → Tarea 1, paso de `DecodedPageImage`.
- Ajustes rápidos en ventana baja: se puede llegar al final. → Tarea 3 (scroll), verificación visual.
- Modo de mezcla fuera de rango (preferencia corrupta): cae en `SrcOver`. → Tarea 2 (`getOrNull`).

---

### Task 1: Matriz de color y páginas filtradas

**Files:** Create `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderColorFilter.kt`; Modify
`desktop-app/src/main/kotlin/mihon/desktop/app/reader/viewer/PageContent.kt` (`DecodedPageImage`); Test: Create
`desktop-app/src/test/kotlin/mihon/desktop/app/reader/ReaderColorFilterTest.kt`.

**Produces:** `fun readerColorMatrix(grayscale: Boolean, inverted: Boolean): ColorMatrix?`;
`val LocalPageColorFilter: ProvidableCompositionLocal<ColorFilter?>`.

- [ ] Test (falla: no existe `readerColorMatrix`):

```kotlin
class ReaderColorFilterTest {
    @Test
    fun `no filter without grayscale or inverted colors`() {
        readerColorMatrix(grayscale = false, inverted = false) shouldBe null
    }

    @Test
    fun `inverted colors flip each channel`() {
        val m = readerColorMatrix(grayscale = false, inverted = true).shouldNotBeNull().values
        m[0] shouldBe -1f
        m[4] shouldBe 255f
        m[6] shouldBe -1f
        m[12] shouldBe -1f
        m[18] shouldBe 1f
    }

    @Test
    fun `grayscale gives the three channels the same mix, and goes with inverted colors`() {
        val gray = readerColorMatrix(grayscale = true, inverted = false).shouldNotBeNull().values
        gray.copyOfRange(0, 3).toList() shouldBe gray.copyOfRange(5, 8).toList()
        gray.copyOfRange(0, 3).toList() shouldBe gray.copyOfRange(10, 13).toList()
        val both = readerColorMatrix(grayscale = true, inverted = true).shouldNotBeNull().values
        both.copyOfRange(0, 3).forEach { (it < 0f) shouldBe true }
        both[4] shouldBe 255f
    }
}
```

- [ ] Implementación:

```kotlin
/** Grayscale and inverted colors for the pages (Mihon's ReaderActivity.getCombinedPaint); null when neither. */
fun readerColorMatrix(grayscale: Boolean, inverted: Boolean): ColorMatrix? {
    if (!grayscale && !inverted) return null
    return ColorMatrix().apply {
        if (grayscale) setToSaturation(0f)
        if (inverted) timesAssign(InvertMatrix)
    }
}

private val InvertMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** The filter the reader puts on its pages; the background, transitions and controls stay as they are. */
val LocalPageColorFilter: ProvidableCompositionLocal<ColorFilter?> = staticCompositionLocalOf { null }
```

> `timesAssign` aplica la inversión **después** de los grises (como `postConcat` en Mihon). Si el test de "both"
> sale al revés, usar `ColorMatrix(InvertMatrix.values.copyOf()).apply { timesAssign(gray) }` y anotar la regla.

- [ ] `DecodedPageImage`: `val filter = LocalPageColorFilter.current` y `colorFilter = filter` en los tres `Image`.
- [ ] Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.ReaderColorFilterTest"` → PASS.
- [ ] Commit: `feat: grayscale and inverted colors for the reader's pages`.

### Task 2: Overlay de brillo y color en el lector

**Files:** Create `desktop-app/src/main/kotlin/eu/kanade/presentation/reader/ReaderContentOverlay.kt`; Modify
`desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderScreen.kt`; Test: `ReaderControlsTest.kt`.

- [ ] Test (falla: no hay nodos "brightness overlay"/"color overlay"):

```kotlin
    @Test
    fun `custom brightness dims the page, a color filter goes over it, and grayscale doesn't break drawing`() =
        runComposeUiTest {
            val preferences = startTestApp().readerPreferences
            preferences.customBrightness.set(true)
            preferences.customBrightnessValue.set(-50)
            preferences.colorFilter.set(true)
            preferences.colorFilterValue.set(0x40FF8800)
            preferences.grayscale.set(true)
            preferences.invertedColors.set(true)
            try {
                open()
                waitForTag("page 1 ready")
                onNodeWithTag(BRIGHTNESS_OVERLAY_TAG).assertExists()
                onNodeWithTag(COLOR_OVERLAY_TAG).assertExists()

                // A brighter value from an Android backup: the desktop can't brighten the screen, so no overlay
                preferences.customBrightnessValue.set(40)
                waitForIdle()
                onNodeWithTag(BRIGHTNESS_OVERLAY_TAG).assertDoesNotExist()
            } finally {
                preferences.customBrightness.set(false)
                preferences.colorFilter.set(false)
                preferences.grayscale.set(false)
                preferences.invertedColors.set(false)
            }
        }
```

(`startTestApp()` devuelve el mismo grafo que `open()` usa; si `open()` lo reinicia, fijar las preferencias tras
`open()` y antes de `waitForTag`.)

- [ ] `ReaderContentOverlay.kt` (gemelo de Mihon sin anotaciones Android, con tags `BRIGHTNESS_OVERLAY_TAG =
  "brightness overlay"` y `COLOR_OVERLAY_TAG = "color overlay"`).
- [ ] `ReaderContent`: leer las seis preferencias; `CompositionLocalProvider(LocalPageColorFilter provides
  remember(grayscale, inverted) { readerColorMatrix(grayscale, inverted)?.let(ColorFilter::colorMatrix) })`
  alrededor del `when` de los visores; `ReaderContentOverlay(brightness = if (customBrightness)
  customBrightnessValue.coerceAtMost(0) else 0, color = colorFilterValue.takeIf { colorFilter }, colorBlendMode =
  ReaderPreferences.ColorFilterMode.getOrNull(colorFilterMode)?.second)` justo antes de `ReaderControls`.
- [ ] Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.*"` → PASS.
- [ ] Commit: `feat: custom brightness and color filter over the reader's pages`.

### Task 3: Filtros en los ajustes rápidos

**Files:** Modify `desktop-app/src/main/kotlin/mihon/desktop/app/reader/ReaderQuickSettings.kt`; Test:
`ReaderControlsTest.kt`.

- [ ] Test (falla: no hay chip "Filtro de color personalizado"):

```kotlin
    @Test
    fun `the quick settings switch the color filter on and show its sliders`() = runComposeUiTest {
        open()
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performMouseInput { click(center) }
        waitForTag(QUICK_SETTINGS_TAG)
        onNodeWithTag(QUICK_SETTINGS_TAG).performClick()
        try {
            onNodeWithText(str(MR.strings.pref_custom_color_filter)).performScrollTo().performClick()
            waitFor(str(MR.strings.color_filter_r_value))
            startTestApp().readerPreferences.colorFilter.get() shouldBe true
        } finally {
            startTestApp().readerPreferences.colorFilter.set(false)
        }
    }
```

- [ ] Grupo `custom_filter` al final del diálogo: chips de brillo, filtro de color, grises e invertir; sliders de brillo
  (−75…0, `steps = 0`) y RGBA (0…255, `steps = 0`, con el `getColorValue` de Mihon); chips de `ColorFilterMode`.
  `Column` del diálogo con `Modifier.keyboardScrollable(scroll).verticalScroll(scroll)`.
- [ ] Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.reader.*"` → PASS.
- [ ] Commit: `feat: the reader's quick settings set the image filters`.

### Task 4: Cierre
- [ ] `./gradlew spotlessApply`, `./gradlew spotlessCheck test --continue`, `scripts/check-*.sh`.
- [ ] Verificación visual con perfil temporal: grises, invertir, brillo −50, filtro naranja en multiplicar.
- [ ] `graphify update .`; commit de spec y plan; push; PR apilado sobre #8.
