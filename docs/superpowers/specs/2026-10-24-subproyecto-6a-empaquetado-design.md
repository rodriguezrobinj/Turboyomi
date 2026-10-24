# Diseño: subproyecto 6a — empaquetado local (Turboyomi)

- **Fecha:** 2026-10-24
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md` (sección 6, "Empaquetado")
- **Spec hermana:** `2026-10-24-subproyecto-5b2-trackers-oauth-design.md` (protocolo, `ProtocolRegistrar`)
- **Base:** `master` en `cc3333dc3` (subproyecto 5 integrado; 492 tests)

## 1. Objetivo y alcance

### Objetivo
1. La app se llama **Turboyomi** y se instala con un MSI por usuario. También se distribuye en un ZIP sin instalador.
2. El MSI y el ZIP llevan su propio Java recortado: el usuario no necesita un JDK.
3. Quien ya usaba la versión de desarrollo "MangaReader" conserva su biblioteca, sus descargas y sus ajustes.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| División del subproyecto 6 | 6a: empaquetado local (este). 6b: repo en GitHub, CI, firma Authenticode, auto-actualización y winget, cuando haya repo y certificado |
| Nombre | Turboyomi, visible y por dentro: id `app.turboyomi`, carpetas `%APPDATA%\Turboyomi` y `%LOCALAPPDATA%\Turboyomi`, almacenamiento por defecto `Documentos\Turboyomi`, protocolos `turboyomi://` y `app.turboyomi://` (Comick) |
| Datos de MangaReader | Se migran solos en el primer arranque (§3 A) |
| Icono | Propio: un SVG en el repo (libro abierto con un rayo); el `.ico` multi-tamaño se genera en el build |
| Herramienta | `jpackage` del JDK del build, desde tareas de Gradle (sin el plugin de Compose, que el build no usa). El MSI necesita WiX 3.14 en la máquina que compila |
| ZIP | La misma app sin instalador. Usa las mismas carpetas de datos que la instalada |

### Cambios respecto a la spec padre
- §6 pide un JRE 21. Se recorta el JDK con el que compila el build (hoy el 23), porque es contra el que se prueba todo. `jpackage` del JDK 23 trabaja con WiX 3.
- §6 fija los módulos mínimos del runtime. Aquí son esos más los que pida `jdeps` y los que usan las extensiones (§2).
- El protocolo lo registra el instalador, además de la app al arrancar (5b-2), y el desinstalador lo borra.

### Fuera de alcance (6a)
| Qué | Dónde |
|---|---|
| Firma Authenticode del MSI y del exe | 6b |
| Auto-actualización (`AppUpdateChecker`, GitHub Releases) | 6b |
| CI (`build.yml`, `compat.yml`, `release.yml`) y winget | 6b |
| Modo portable con los datos junto al exe | No se pide |

## 2. Arquitectura

| Pieza | Qué hace |
|---|---|
| Renombrado | `AppDirs.APP_NAME = "Turboyomi"`, `DesktopBuildInfo.APPLICATION_ID = "app.turboyomi"`, `TrackLoginHandler.SCHEMES = {turboyomi, app.turboyomi}` y los redirects de los trackers (`turboyomi://<servicio>-auth`, `app.turboyomi://comick-auth`); también la plantilla `tracker-keys.example.properties` |
| `LegacyMigration` | En desktop-core, antes de `Platform.start`: trae los datos de MangaReader (§3 A) |
| Icono | `desktop-app/packaging/turboyomi.svg`. Una tarea de Gradle lo rasteriza (16, 24, 32, 48, 64 y 256 px) a `build/packaging/turboyomi.ico` y a PNG para la ventana y la bandeja |
| `DesktopBuildInfo` generado | Gradle escribe `VERSION_NAME`, `VERSION_CODE` y `COMMIT_SHA` desde `git describe --tags` (sin tag: `0.1.0`, código 1) |
| Runtime | Tarea `jlinkRuntime`: `jlink` con la unión de `jdeps --print-module-deps` sobre el classpath de la app y la lista fija `java.sql`, `java.net.http`, `jdk.crypto.ec`, `jdk.unsupported`, `jdk.charsets`, `jdk.localedata`, `java.naming`, `java.management`. Con `--strip-debug --no-header-files --no-man-pages` |
| `packageZip` | `jpackage --type app-image` con el runtime, el icono, `--java-options` (las `--add-exports` de JCEF de 2b) y el nombre Turboyomi; luego se comprime |
| `packageMsi` | `jpackage --type msi --win-per-user-install --win-menu --win-shortcut --win-upgrade-uuid <fijo>`, más un `main.wxs` propio en `--resource-dir` que añade las claves del protocolo |
| `--smoke` | Un argumento de `Main`: arranca el grafo con datos temporales, escribe `OK` y sale con 0, sin ventana |

## 3. Comportamiento

### A. Migración desde MangaReader
- Se ejecuta en cada arranque, antes de abrir la base de datos, y solo actúa si existe `%APPDATA%\MangaReader` y no
  existe `%APPDATA%\Turboyomi`.
- **Qué mueve:**
  - `%APPDATA%\MangaReader` a `%APPDATA%\Turboyomi`;
  - `%LOCALAPPDATA%\MangaReader` a `%LOCALAPPDATA%\Turboyomi`;
  - `settings\app.mangareader_preferences.xml` a `settings\app.turboyomi_preferences.xml`;
  - `Documentos\MangaReader` a `Documentos\Turboyomi`, solo si `storage_dir` es la carpeta por defecto. En ese caso
    se actualiza también `storage_dir`.
- Un `storage_dir` personalizado no se toca.
- **Si algo no se puede mover** (una carpeta en uso o sin permiso):
  - lo ya movido se devuelve a su sitio y no queda nada a medias;
  - se apunta en el log;
  - la app arranca con datos nuevos y avisa: "No se pudieron traer los datos de MangaReader; cierra otras copias y
    reinicia".
- `MANGAREADER_HOME`, la variable de desarrollo, pasa a `TURBOYOMI_HOME`.

### B. Instalar, actualizar, desinstalar
- **Instalar:** sin UAC, en `%LOCALAPPDATA%\Programs\Turboyomi`, con accesos en el menú Inicio y el escritorio.
- **Actualizar:** un MSI de versión mayor sustituye al instalado (mismo `UpgradeCode`). Los datos se quedan. Uno de
  versión menor se rechaza.
- **Desinstalar:** borra la app y las claves `HKCU\Software\Classes\turboyomi` y `app.turboyomi`. Los datos se
  quedan.

### C. Runtime
- Un módulo que falte a una extensión hace fallar la carga de esa extensión con su error. La app sigue funcionando.
- El harness de compatibilidad se ejecuta con el `java` del runtime empaquetado, así que lo detecta antes de
  publicar.

## 4. Errores
| Caso | Comportamiento |
|---|---|
| WiX ausente al compilar | `packageMsi` falla con un mensaje que dice cómo instalarlo (`winget install WiXToolset.WiXToolset`). `packageZip` funciona igual |
| Sin `git` o sin tags | Versión `0.1.0`, código 1, commit `unknown` |
| Migración a medias | Se deshace; aviso; se arranca con datos nuevos (§3 A) |
| MSI más viejo que el instalado | Lo rechaza Windows Installer |

## 5. Pruebas

### Automáticas (`./gradlew test`)
- **`LegacyMigration`**, con carpetas temporales con acentos:
  - mueve las tres carpetas y el fichero de ajustes;
  - no toca nada si ya existe `Turboyomi`;
  - respeta un `storage_dir` personalizado;
  - con una carpeta bloqueada, lo deja todo como estaba y avisa.
- **Nombre:** el título de la ventana y la bandeja dicen Turboyomi; el protocolo registrado es `turboyomi://` (y
  `app.turboyomi://`), y los redirects de los trackers lo usan.
- **Versión:** sin tag sale `0.1.0`; con `v1.2.3` sale `1.2.3`.
- **Icono:** el `.ico` generado tiene los 6 tamaños.

### Del build (verificación final)
- `packageZip`:
  - la imagen tiene `Turboyomi.exe` y `runtime\`;
  - `runtime\bin\java --list-modules` incluye los módulos requeridos;
  - `Turboyomi.exe --smoke` escribe `OK` y sale con 0.
- El harness de compatibilidad con el `java` del runtime empaquetado.
- `packageMsi`:
  - instalación silenciosa (`msiexec /i … /qn`);
  - existen el exe y las claves del protocolo;
  - desinstalación silenciosa: sin claves y con los datos intactos.

### Manual (al usuario)
1. Instalar el MSI con doble clic, abrir desde el menú Inicio y ver la biblioteca migrada desde MangaReader.
2. Instalar encima un MSI de versión mayor y comprobar que no se pierde nada.
3. Desinstalar y comprobar que los datos quedan.
4. Probar el ZIP descomprimido en otra carpeta.
