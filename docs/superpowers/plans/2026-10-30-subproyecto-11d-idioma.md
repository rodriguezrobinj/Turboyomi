# Subproyecto 11d: idioma de la app — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ajustes › Apariencia › "Idioma de la aplicación", aplicado al momento.

**Spec:** `docs/superpowers/specs/2026-10-30-subproyecto-11d-idioma-design.md`

## Global Constraints
- Rama `feat/app-language-11d` desde `master`.
- Textos de Mihon ya traducidos (`pref_app_language`, `label_default`).

### Task 1: `AppLanguage` + `AppLanguageTest`
- [ ] `TAGS`, `apply`; la prueba compara con las carpetas de moko y comprueba un texto en `es` y en `en`.

### Task 2: Pantalla y preferencia
- [ ] `uiPreferences.appLanguage`; gemelo de `AppLanguageScreen` sin `AppCompatDelegate`; entrada en Apariencia.

### Task 3: `Main`
- [ ] `apply` al arrancar; cambio de preferencia → `apply` + `restarts++`.

### Task 4: Cierre
- [ ] Suite completa, scripts, `appImage`, prueba a mano, PR.
