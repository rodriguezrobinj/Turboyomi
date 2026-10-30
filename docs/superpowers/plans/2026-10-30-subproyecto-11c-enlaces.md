# Subproyecto 11c: enlaces externos — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** `tachiyomi://add-repo`, `mihon://extension-store`, los `.tachibk` y los enlaces o búsquedas de manga abren su
pantalla, también con la app ya abierta.

**Spec:** `docs/superpowers/specs/2026-10-30-subproyecto-11c-enlaces-design.md`

## Global Constraints
- Rama `feat/deep-links-11c` desde `master`.
- Añadir un repo siempre pasa por el diálogo de confirmación que ya trae `ExtensionStoresScreen`.

### Task 1: `DeepLinks` + `DeepLinksTest`
- [ ] `parse` y `route`; canal que recoge `AppContent`.

### Task 2: Registro
- [ ] `ProtocolRegistrar` registra `tachiyomi`, `mihon` y `.tachibk`; `ProtocolRegistrarTest` lo comprueba.

### Task 3: Pantallas
- [ ] Gemelos de `DeepLinkScreen` y `DeepLinkViewModel` (lector como pantalla); enrutado en `AppContent`.

### Task 4: Cierre
- [ ] Suite completa, scripts, `appImage`, prueba a mano, PR.
