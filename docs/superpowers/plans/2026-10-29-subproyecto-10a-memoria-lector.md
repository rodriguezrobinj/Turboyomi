# Subproyecto 10a: memoria del lector — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Que la memoria nativa del lector quede acotada: páginas cerradas al soltarlas, tope de bytes y de tamaño.

**Architecture:** `DecodedPageCache` pasa a ser la dueña de las páginas decodificadas y sabe cuáles están en pantalla;
`PageDecoder` cierra lo intermedio y rechaza o simplifica lo desmedido.

**Tech Stack:** Kotlin, Compose Multiplatform desktop, Skia (skiko), JUnit 5 + kotest.

**Spec:** `docs/superpowers/specs/2026-10-29-subproyecto-10a-memoria-lector-design.md`

## Global Constraints
- Rama `feat/reader-memory-10a` desde `master`. Sin textos nuevos.

## Review Focus
- Nunca cerrar una página que se está pintando: `show`/`hide` y el orden olvidar-antes-de-recordar de Compose. → Tarea 2.
- Una decodificación cancelada (página pasada de largo) también acaba en la caché, no en el GC. → Tarea 2.

---

### Task 1: `PageDecoder`
**Files:** `desktop-app/src/main/kotlin/mihon/desktop/app/reader/image/PageDecoder.kt`, su prueba.
- [ ] Pruebas: cabecera de 156 Mpx rechazada; GIF de 272 MB sale `Still`; GIF pequeño sale `Animated`.
- [ ] `DecodedPage : AutoCloseable` con `bytes`; límites de píxeles y de animación; cerrar intermedios.

### Task 2: `DecodedPageCache` y su uso
**Files:** `DecodedPageCache.kt`, `viewer/PageContent.kt`, `ReaderScreen.kt`, `DecodedPageCacheTest.kt`.
- [ ] Pruebas de la spec §3.
- [ ] `put` devuelve la página a mostrar; `show`/`hide`; tope de bytes; `close()`.
- [ ] `PageContent`: `put` dentro del bloque IO y `DisposableEffect(cache, key)`. `ReaderScreen`: cierra la caché.

### Task 3: Cierre
- [ ] `./gradlew spotlessCheck test --continue`, `scripts/check-*.sh`, `:desktop-app:appImage`, PR.
