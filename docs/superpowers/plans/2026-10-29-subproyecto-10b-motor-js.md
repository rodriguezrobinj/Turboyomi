# Subproyecto 10b: motor JS de las extensiones — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** `QuickJs` sin acceso a objetos Java y con límite de tiempo.

**Architecture:** Solo `android-compat/src/main/java/app/cash/quickjs/QuickJs.java`; su API pública no cambia.

**Tech Stack:** Java, GraalVM polyglot 25 (js-community), JUnit 5 + kotest.

**Spec:** `docs/superpowers/specs/2026-10-29-subproyecto-10b-motor-js-design.md`

## Global Constraints
- Rama `fix/quickjs-sandbox-10b` desde `master`. Zona sensible: `security-and-hardening`.

## Review Focus
- Una interrupción que llega tarde no debe cortar el script siguiente: `evaluate` espera al vigilante si ya disparó.

---

### Task 1: Pruebas
**Files:** `android-compat/src/test/kotlin/app/cash/quickjs/QuickJsTest.kt`.
- [ ] Las de la spec §3, en rojo contra `master`.

### Task 2: `QuickJs`
- [ ] `HostAccess.NONE`, `set` con `ProxyObject`, vigilante y `interrupt`, sin `enter`/`leave`.
- [ ] `:android-compat:test`, `./gradlew spotlessCheck test --continue`, `scripts/check-*.sh`, `appImage`, PR.
