# Subproyecto 9c: estadísticas — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Pantalla de estadísticas de Mihon, abierta desde la lista de Ajustes.

**Architecture:** Port literal de los seis archivos de Mihon (`ui/stats`, `presentation/more/stats`); solo cambia la
entrada.

**Tech Stack:** Kotlin, Compose Multiplatform desktop, Metro (`metroViewModel`), JUnit 5 + kotest.

**Spec:** `docs/superpowers/specs/2026-10-27-subproyecto-9c-estadisticas-design.md`

## Global Constraints
- Rama `feat/stats-9c`, desde `feat/settings-search-9b` (las dos tocan `SettingsTab`).
- Sin imports de Android; sin textos nuevos.

## Review Focus
- Sin trackers con sesión: puntuación media sin valor (`NaN` se muestra como "Ninguno", como en Mihon). → Tarea 1.
- Biblioteca vacía: todo a 0, sin dividir por cero. → Tarea 1 (lógica de Mihon).

---

### Task 1: Port
**Files:** Create `desktop-app/src/main/kotlin/eu/kanade/tachiyomi/ui/stats/{StatsViewModel,StatsScreen}.kt` and
`desktop-app/src/main/kotlin/eu/kanade/presentation/more/stats/{StatsScreenContent,StatsScreenState}.kt`,
`.../stats/components/StatsItem.kt`, `.../stats/data/StatsData.kt`.
- [ ] Copiar de `app/src/main/java/` con la misma ruta; ajustar solo lo que no compile en escritorio.

### Task 2: Entrada y prueba
**Files:** Modify `desktop-app/src/main/kotlin/mihon/desktop/app/settings/SettingsTab.kt`; create
`desktop-app/src/test/kotlin/mihon/desktop/app/settings/StatsTest.kt`.
- [ ] "Estadísticas" primera en `SettingsTab.screens`; la búsqueda de 9b la omite porque no es `SearchableSettings`.
- [ ] Test del spec §3; `./gradlew :desktop-app:test --tests "*StatsTest*"`.

### Task 3: Cierre
- [ ] `spotlessApply`, `spotlessCheck test --continue`, `scripts/check-*.sh`, `graphify update .`, commit, push y PR
  contra `feat/settings-search-9b`.
