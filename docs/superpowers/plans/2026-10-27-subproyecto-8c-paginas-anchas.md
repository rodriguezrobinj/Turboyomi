# Subproyecto 8c: páginas anchas y bordes — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Recortar bordes, partir y girar páginas anchas en los modos por páginas y webtoon.

**Architecture:** Funciones puras (`contentBounds`, `wideRotation`, `readingHalves`, `splitPages`) probadas por
separado; el decodificador aplica recorte y giro según `DecodeOptions` que el lector da por un `CompositionLocal`; el
partido es una mitad en `Spread.Single` (páginas) o dos mitades apiladas (webtoon).

**Tech Stack:** Kotlin, Skia (skiko), Compose Multiplatform desktop, JUnit 5 + kotest.

**Spec:** `docs/superpowers/specs/2026-10-27-subproyecto-8c-paginas-anchas-design.md`

## Global Constraints
- Rama `feat/reader-wide-pages-8c`, desde `feat/reader-filters-8b` (PR #9).
- Sin strings nuevos; sin imports de Android.
- Umbrales: casi blanco/negro = canal ≥ 230 / ≤ 25; tolerancia 25 por canal; muestreo cada 4 px; mínimo 25 % del área.

## Review Focus
- Página totalmente blanca (separador): no se recorta a nada. → Tarea 1, test "casi vacía".
- Página con borde de color (portada): intacta. → Tarea 1, test.
- Doble página activa + partir: la página ancha sigue sola, sin partir. → Tarea 3 (solo sin doble página).
- Partir y girar activos a la vez (preferencias de un backup): gana partir. → Tarea 2 (`rotation = 0` si partir).
- GIF ancho con girar: se muestra sin girar ni fallar. → Tarea 2 (solo `still`).

---

### Task 1: Funciones puras
**Files:** Create `desktop-app/src/main/kotlin/mihon/desktop/app/reader/image/ContentBounds.kt`,
`desktop-app/src/main/kotlin/mihon/desktop/app/reader/WidePages.kt`; Modify `.../reader/Spread.kt` (`Single(page, half)`);
Tests: `.../reader/image/ContentBoundsTest.kt`, `.../reader/WidePagesTest.kt`.
- [ ] Tests primero (rojo), luego implementación, verde: `./gradlew :desktop-app:test --tests "*ContentBoundsTest" --tests "*WidePagesTest" --tests "*SpreadTest*"`.
- [ ] Commit: `feat: content bounds, wide page rotation and split order for the reader`.

### Task 2: Decodificador y opciones
**Files:** Modify `.../reader/image/PageDecoder.kt` (`DecodeOptions`, recorte con `extractSubset`, giro con `Canvas`),
`.../reader/viewer/PageContent.kt` (`LocalDecodeOptions`, clave de decodificación), `.../reader/ReaderScreen.kt`
(opciones por modo, caché por opciones). Test: `.../reader/image/PageDecoderTest.kt` (crear o ampliar).
- [ ] Test: PNG 300×100 con `rotation = 90` → `Still` de 100×300; PNG con margen blanco y `crop` → más pequeño.
- [ ] Commit: `feat: the reader crops page borders and turns wide pages to fit`.

### Task 3: Partir en páginas y webtoon
**Files:** Modify `PagerViewer.kt` (spreads con `splitPages`), `PageContent.kt` (`half`, `HalfPageImage`),
`WebtoonViewer.kt` (dos mitades). Test: `ReaderControlsTest` con una página ancha (`TestSources.http.images` sirve un
PNG ancho para esa ruta: añadir `wide: MutableSet<String>` en `FakeImages`, 1200×600).
- [ ] Test: con `dualPageSplitPaged`, página 2 ancha → `page 2 ready` aparece dos veces seguidas al pulsar → dos veces.
- [ ] Commit: `feat: wide pages split in two halves in paged and webtoon reading`.

### Task 4: Ajustes rápidos
**Files:** Modify `ReaderQuickSettings.kt`. Test: `ReaderControlsTest` (chip "Recortar bordes" cambia `cropBorders`).
- [ ] Commit: `feat: the reader's quick settings set crop, split and rotate for wide pages`.

### Task 5: Cierre
- [ ] `spotlessApply`, `spotlessCheck test --continue`, `check-*.sh`; verificación visual; `graphify update .`; push; PR
  apilado sobre #9.
