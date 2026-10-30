# Subproyecto 11d: idioma de la app

## 1. Objetivo
Ajustes › Apariencia › "Idioma de la app", como en Mihon. Hoy la app sigue siempre el idioma de Windows. Cuarto de
11a–11f (ver spec 11a §1).

## 2. Diseño
- **Preferencia:** `uiPreferences.appLanguage` (texto; vacío = el de Windows). La guarda la app: escritorio no tiene
  `AppCompatDelegate`. Va en `UiPreferences` de escritorio con la clave `desktop_app_language`, para no chocar con nada
  de Mihon (Mihon no la guarda en preferencias, así que los backups no la llevan).
- **Aplicar:** moko resuelve los textos con `Locale.getDefault()`. Por eso `AppLanguage.apply(tag)` hace
  `Locale.setDefault(tag o el locale de Windows guardado al arrancar)`.
  - Se llama al arrancar, en cuanto existe el grafo y antes de la primera ventana.
  - Al elegir otro idioma, `Main` ve el cambio de la preferencia, llama a `apply` y recompone la ventana desde cero con el
    mismo `restarts++` que usa la recuperación de errores. Así cambia sin reiniciar, como en Mihon. La navegación vuelve
    a la biblioteca, porque escritorio no guarda la pila de pantallas como Android al recrear la actividad.
  - Las fechas siguen el mismo locale, igual que en Android.
- **Lista:** gemelo de `AppLanguageScreen`. En lugar de `locales_config.xml`, la lista sale de las carpetas de
  `i18n/src/commonMain/moko-resources/`, escritas como etiquetas BCP 47 (`pt-rBR` → `pt-BR`). Va en una constante
  `AppLanguage.TAGS`, más `en` (los textos base). Una prueba compara esa lista con las carpetas para que no se quede atrás cuando el sync con Mihon
  traiga idiomas. Nombre y nombre localizado con `LocaleHelper`, que ya existe.
- El comentario "desktop: no app language" de `SettingsAppearanceScreen` se cambia por la entrada, en el mismo sitio
  que Mihon.

## 3. Pruebas
- `AppLanguageTest`: `TAGS` coincide con las carpetas de moko. `apply("es")` hace que `str(MR.strings.label_settings)`
  devuelva "Ajustes". `apply("")` vuelve al locale inicial.
- A mano: cambiar a otro idioma y volver a "Original" con el `.exe` y un perfil aislado.
