# Subproyecto 11f: restricciones de dispositivo — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** la actualización automática de la biblioteca respeta "sin datos móviles", "red sin límite" y "cargando".

**Spec:** `docs/superpowers/specs/2026-10-30-subproyecto-11f-restricciones-design.md`

## Global Constraints
- Rama `feat/update-restrictions-11f`, tras 11b (reutiliza su código WinRT).
- Un fallo al leer el estado del equipo nunca para las actualizaciones.

### Task 1: WinRT compartido y `DeviceState`
- [ ] `WinRt` (desktop-core) con lo genérico de `WindowsHello`; `DeviceState.internet()` y `onAcPower()`; `DeviceStateTest`.

### Task 2: Planificador
- [ ] `PeriodicJob.canRun`; `JobScheduler` no lanza ni toca `lastRun` mientras sea falso; `JobSchedulerTest`.

### Task 3: Restricciones y ajustes
- [ ] `restrictionsMet` + `UpdateRestrictionsTest`; `LibraryUpdateJob.periodicJob` lo usa; lista en Ajustes › Biblioteca.

### Task 4: Cierre
- [ ] Suite, scripts, `appImage`, PR.
