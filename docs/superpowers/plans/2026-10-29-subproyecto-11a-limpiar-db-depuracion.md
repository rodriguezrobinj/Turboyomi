# Subproyecto 11a: limpiar base de datos y depuración — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ajustes › Avanzado gana "Limpiar base de datos"; Información de depuración gana el esquema del backup y las
tareas programadas.

**Spec:** `docs/superpowers/specs/2026-10-29-subproyecto-11a-limpiar-db-depuracion-design.md`

## Global Constraints
- Rama `feat/clear-database-11a` desde `master`.
- Gemelos con la misma ruta que en `app/` y cambios mínimos (skill `portar-pantalla`).
- Los textos de Mihon ya existen en `base` y `es`; no hay claves nuevas salvo que falte alguna en `es`.

### Task 1: Limpiar base de datos
- [ ] Copiar `ClearDatabaseScreen.kt` al gemelo y adaptar imports (toast, iconos).
- [ ] Enlazarlo en `AdvancedSettingsScreen.dataGroup`.
- [ ] `ClearDatabaseScreenTest` (spec §3) y pasarlo.

### Task 2: Tareas programadas
- [ ] `JobScheduler.jobs` + `isRunning`; `graph.jobScheduler`; `Main` lo arranca.
- [ ] `jobSummary` + `JobSummaryTest`.
- [ ] `WorkerInfoScreen` de escritorio y enlace en `DebugInfoScreen`.

### Task 3: Esquema del backup
- [ ] Copiar `BackupSchemaScreen.kt` al gemelo; enlace en `DebugInfoScreen`.

### Task 4: Cierre
- [ ] `./gradlew spotlessCheck test --continue`, `scripts/check-*.sh`, `:desktop-app:appImage`, PR.
