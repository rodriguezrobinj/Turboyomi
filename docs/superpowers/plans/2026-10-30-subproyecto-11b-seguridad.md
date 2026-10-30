# Subproyecto 11b: seguridad y privacidad — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ajustes › Seguridad y privacidad con bloqueo por Windows Hello, bloqueo por inactividad, pantalla segura y
notificaciones sin contenido.

**Spec:** `docs/superpowers/specs/2026-10-30-subproyecto-11b-seguridad-design.md`

## Global Constraints
- Rama `feat/security-11b` desde `master` (tras 11a).
- Sin dependencias nuevas: WinRT y `user32` por JNA (`jna-platform` ya está).
- Textos de Mihon ya traducidos. No hay claves nuevas.

## Review Focus
- Un fallo de COM nunca desbloquea: `verify` devuelve `false`. → Tarea 1.
- Sin Hello la app no se bloquea, para no dejar fuera al usuario. → Tarea 2.
- Bloqueada: ni clics ni teclas llegan a la app. → Tarea 4.

### Task 1: `WindowsHello` (desktop-core)
- [ ] `isAvailable` y `verify(hwnd, message)` con `RoGetActivationFactory`, vtables e `IAsyncInfo`.

### Task 2: `AppLock` + `AppLockTest`
- [ ] Lógica de `SecureActivityDelegate` con reloj y disponibilidad inyectados.

### Task 3: `SecureScreen` + notificaciones
- [ ] `isSecure` + `apply` (`SetWindowDisplayAffinity`), `SecureScreenTest`.
- [ ] `newChaptersNotice(hideContent)`, error de descarga sin título.

### Task 4: Pantallas y `Main`
- [ ] Gemelo de `SettingsSecurityScreen` sin Firebase; entrada en `SettingsTab`; `securityPreferences` en `AppGraph`.
- [ ] `UnlockScreen` sobre `AppContent`, segundo plano por `snapshotFlow`, atajos bloqueados.

### Task 5: Cierre
- [ ] Suite completa, `scripts/check-*.sh`, `appImage`, prueba a mano de Hello con perfil aislado, PR.
