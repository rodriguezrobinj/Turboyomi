# Subproyecto 9a: ajustes avanzados de Mihon

## 1. Objetivo
Primero de 9a–9c, los pendientes de portar tras el sync con Mihon de 2026-10-27. Ajustes › Avanzado gana las opciones
de Mihon que tienen sentido en escritorio y cuya lógica ya está en los módulos compartidos: red, datos, biblioteca y
depuración.

### Fuera de alcance
- Limpiar base de datos, onboarding, gestionar notificaciones, batería, instalador de extensiones, Shizuku,
  "renderizador de alta calidad" (Android).
- Pantallas de depuración de Mihon de WorkManager y del esquema de backup.

## 2. Diseño
`AdvancedSettingsScreen` queda en grupos, en el orden de Mihon:

- **Arriba:** "Registro detallado" (`networkPreferences.verboseLogging`), "Ocultar cabeceras sensibles"
  (`redactSensitiveHeaders`), ambos con aviso de reinicio (`requires_app_restart`). Después "Información de depuración"
  (pantalla nueva), "Volcar registros de errores" y "Abrir carpeta de logs" (ya existía).
- **Datos:** "Invalidar índice de descargas" (`downloadCache.invalidateCache()`, aviso `download_cache_invalidated`).
- **Red:** "Borrar cookies" (pasa aquí desde el grupo del navegador), "Borrar datos del navegador", DNS sobre HTTPS
  (lista de Mihon), user agent (texto; se valida con `Headers.Builder().add`, como Mihon) y "Restablecer user agent"
  (visible si no es el de serie).
- **Biblioteca:** "Actualizar portadas" (ya existía), "Restablecer ajustes de lector por manga"
  (`ResetViewerFlags`), "Actualizar títulos de la biblioteca", "Prohibir nombres no ASCII", "Sufijo hash en nombres de
  capítulo". Las tres últimas son preferencias de `LibraryPreferences` que el escritorio ya respeta.
- **Navegador integrado:** como hoy, sin "Borrar cookies".

Efectos en escritorio:
- **Registro detallado:** baja el nivel raíz de logback a DEBUG al arrancar (`DesktopLogging.setVerbose`, en
  `desktop-core`) y el `NetworkHelper` de escritorio (`core/common/src/desktop`) registra las cabeceras HTTP en
  `app.log` mediante `logcat`, no por `java.util.logging`.
- **Ocultar cabeceras:** el `NetworkHelper` de escritorio no tenía el cambio de Mihon. El interceptor se construye en
  `httpLoggingInterceptor(preferences, logger)`, que oculta `Authorization` y `Auth` cuando la preferencia está activa.
- **Borrar datos del navegador** (`CefData`, en `desktop-core`): borra `cache\cef`. Si Chromium está en marcha, deja
  una marca y el borrado ocurre en el siguiente arranque, antes de iniciarlo, igual que la desinstalación de
  `CefInstaller`. Avisa con `webview_data_deleted` o con `requires_app_restart`.
- **Información de depuración** (`DebugInfoScreen`): versión y commit, ID de instalación, carpeta de datos, Windows
  (`os.name`, `os.version`, `os.arch`), Java, navegador (versión instalada o "no instalado") y hora con zona. Botón para
  copiar el texto. Los textos de las filas van en inglés, como en Mihon, porque se pegan en reportes.
- **Volcar registros** (`CrashLogUtil` de escritorio): escribe `logs\debug_info.txt` con la información de
  depuración y las extensiones con problemas (desactualizadas, obsoletas o sin cargar, con la razón), y abre el
  Explorador con el archivo seleccionado. Si falla, avisa con `desktop_dump_logs_failed`.

Textos: los de Mihon. Faltan en `es` `pref_redact_sensitive_headers`, `pref_redact_sensitive_headers_summary`,
`pref_enable_chapter_name_hash` y `pref_enable_chapter_name_hash_details`, que se añaden. Nuevo `desktop_dump_logs_failed`
con `es`.

## 3. Pruebas
- `HttpLoggingTest`: con ocultar activo, la línea de `Authorization` sale como `██`; con ocultar inactivo, sale el valor.
- `CefDataTest`: sin Chromium en marcha borra la carpeta; en marcha deja la marca y `applyPending` la borra.
- `CrashLogUtilTest`: el volcado contiene la versión y una extensión sin cargar con su razón.
- `AdvancedSettingsTest` (Compose): aparecen los grupos; un user agent con salto de línea no se guarda; restablecer
  vuelve al de serie.
