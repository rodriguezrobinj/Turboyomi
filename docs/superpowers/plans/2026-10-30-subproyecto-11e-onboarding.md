# Subproyecto 11e: primer arranque, "Apóyanos" y licencias — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** onboarding en el primer arranque, pantalla "Apóyanos" de Mihon y lista de licencias de las dependencias.

**Spec:** `docs/superpowers/specs/2026-10-30-subproyecto-11e-onboarding-design.md`

## Global Constraints
- Rama `feat/onboarding-11e` desde `master`.
- Texto nuevo solo `desktop_support_mihon_note`, en `base` y `es`.

### Task 1: Onboarding
- [ ] Gemelos de `presentation/more/onboarding/*` (sin `PermissionStep`) y de `ui/more/OnboardingScreen`.
- [ ] `AppContent` lo abre si no se ha visto; `skipOnboardingForExistingUsers` al arrancar; `startTestApp` lo marca.
- [ ] `OnboardingTest`.

### Task 2: "Apóyanos"
- [ ] Gemelo de `SupportUsScreen` con la nota del fork; entrada en Acerca de; `SupportUsScreenTest`.

### Task 3: Licencias
- [ ] Plugin JVM de AboutLibraries y export a los recursos; gemelo de `OpenSourceLicensesScreen`; `OpenSourceLicensesTest`.

### Task 4: Cierre
- [ ] Suite completa, scripts (incluido `check-licenses.sh`), `appImage`, PR.
