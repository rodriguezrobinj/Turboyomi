# Subproyecto 9b: buscar en ajustes — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Buscar cualquier ajuste por su título o descripción desde la pestaña Ajustes.

**Architecture:** Gemelo de `SettingsSearchScreen` de Mihon; el índice sale de la lista de pantallas de `SettingsTab`.

**Tech Stack:** Kotlin, Compose Multiplatform desktop, Voyager, JUnit 5 + kotest.

**Spec:** `docs/superpowers/specs/2026-10-27-subproyecto-9b-buscar-ajustes-design.md`

## Global Constraints
- Rama `feat/settings-search-9b`, desde `feat/advanced-settings-9a`.
- Sin textos nuevos: `action_search_settings` y `no_results_found` ya tienen `es`.

## Review Focus
- Una pantalla de la lista que no es `SearchableSettings`: se omite del índice sin fallar. → Tarea 1.
- Escribir rápido: el índice se compone una vez, la consulta se lee con `snapshotFlow`. → Tarea 2 (como Mihon).

---

### Task 1: Lista de pantallas compartida
**Files:** Modify `desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsTab.kt`.
- [ ] `val screens` en `SettingsTab` (lista de `Triple` que hoy está dentro de `Content`); `Content` la usa.
- [ ] Acción de lupa en `AppBar` que hace `navigator.push(SettingsSearchScreen())`.

### Task 2: Pantalla de búsqueda
**Files:** Create `desktop-app/src/main/kotlin/eu/kanade/presentation/more/settings/screen/SettingsSearchScreen.kt`;
create `desktop-app/src/test/kotlin/mihon/desktop/app/settings/SettingsSearchTest.kt`.
- [ ] Copia de la de Mihon (`app/.../SettingsSearchScreen.kt`) con `settingScreens` =
  `SettingsTab.screens.map { it.third }.filterIsInstance<SearchableSettings>()`; sin teclado en pantalla (sin
  `LocalSoftwareKeyboardController`).
- [ ] Test del spec §3.
- [ ] `./gradlew :desktop-app:test --tests "*SettingsSearchTest*"`.

### Task 3: Cierre
- [ ] `spotlessApply`, `spotlessCheck test --continue`, `scripts/check-*.sh`, `graphify update .`, commit, push y PR
  contra `feat/advanced-settings-9a`.
