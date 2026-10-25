# Diseño: subproyecto 6b — publicación y auto-actualización

- **Fecha:** 2026-10-24
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md` (sección 6, "Empaquetado, distribución y operación")
- **Spec hermana:** `2026-10-24-subproyecto-6a-empaquetado-design.md` (Turboyomi, `packageMsi`, `packageZip`, versión desde el tag)
- **Base:** `master` en `bf186683d` (subproyecto 6a integrado; 502 tests)

## 1. Objetivo y alcance

### Objetivo
1. Turboyomi se publica en un repo público de GitHub. Cada tag `v*` produce una release con el MSI, el ZIP y `SHA256SUMS`.
2. El CI compila, prueba y empaqueta en cada push. Mide la compatibilidad de extensiones cada noche.
3. La app avisa de una versión nueva y la instala con un clic.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| Firma | Sin firma por ahora. SmartScreen avisará "editor desconocido". `release.yml` deja preparado un paso de firma que se salta sin secretos |
| Repo | `rodriguezrobinj/turboyomi`, público, remote `origin`. Se sube `master` con todo el historial (incluye el de Mihon, Apache 2.0). Nunca push a `upstream` |
| Auto-actualización | Avisa e instala con un clic: diálogo con las notas, descarga del MSI, verificación SHA-256, `msiexec /passive`, cierre de la app |
| winget | Fuera |
| Enfoque | GitHub Actions y el `ReleaseService` / `GetApplicationRelease` de Mihon (ya en `data` y `domain`), con piezas de escritorio para descargar e instalar |

### Cambios respecto a la spec padre
- §6 pide verificar la firma Authenticode del MSI descargado. Sin firma, se verifica solo el SHA-256 contra `SHA256SUMS`, que viene de la misma release por HTTPS.
- §6 pone `packageMsi` en `build.yml`. Además de ese MSI, se sube el ZIP como artefacto.
- §6 incluye winget. Queda fuera.

### Fuera de alcance (6b)
| Qué | Dónde |
|---|---|
| Firma Authenticode | Cuando haya certificado: secretos del paso preparado en `release.yml` |
| winget | Más adelante, si se pide |
| Actualizaciones delta, reabrir la app tras instalar | Mejora futura |

## 2. Arquitectura

### Repo y CI
| Pieza | Qué hace |
|---|---|
| Repo | `gh repo create rodriguezrobinj/turboyomi --public`, remote `origin`, push de `master`. Variable `COMPAT_REPO_URL` = repo de keiyoushi (el que ya usa el harness) |
| Secreto `TRACKER_KEYS` | El contenido de `tracker-keys.properties`. El CI lo escribe en la raíz antes de compilar. Sin él, los trackers OAuth no aparecen en esa build. Las claves quedan dentro del binario público, como en Mihon |
| Instalación de herramientas en el CI | JDK 23 (Temurin) y WiX 3.14 del zip oficial en `%LOCALAPPDATA%\Programs\WiX314\bin`, la ruta que ya busca `packageMsi` |
| `build.yml` | Push a `master` y PR: `check-licenses`, `spotlessCheck`, `test`, `check-no-android`, `cefTest`, `packageMsi` y `packageZip`; MSI y ZIP como artefactos |
| `compat.yml` | Cada noche y a mano: el harness con el runtime empaquetado (`-PharnessJava`) y `--cef`; publica el porcentaje en el resumen |
| `release.yml` | Tag `v*`: `fetch-depth: 0` (la versión sale del tag), suite, `packageMsi`, `packageZip`, `SHA256SUMS`, paso de firma condicional y GitHub Release con notas generadas |

### App
| Pieza | Qué hace |
|---|---|
| `DesktopReleaseService` | Sustituye la binding de `ReleaseServiceImpl`, que elige el asset por ABI de Android. Lee `releases/latest` de `rodriguezrobinj/turboyomi` y devuelve la versión, las notas, la página, la URL del `Turboyomi-<v>.msi` y la de `SHA256SUMS` |
| Comparación de versiones | SemVer numérica, con o sin `v`. Un tag que no es `x.y.z` da "sin actualización". Corrige la de Mihon (`0.10.0` frente a `0.9.0`) |
| `AppUpdateChecker` | Comprueba al arrancar (30 s después) y cada 24 h, como job del `JobScheduler` existente. Respeta el interruptor y está apagado en desarrollo |
| `AppUpdater` | Descarga el MSI a `local\cache\updates` con progreso y cancelación, verifica el SHA-256, lanza `msiexec` y cierra la app por la ruta de salida normal |
| Diálogo de actualización | Notas en markdown, "Actualizar" y "Ahora no" |
| Ajustes › Acerca de | El `AboutScreen` de Mihon con lo mínimo: versión y commit, "Buscar actualizaciones", el interruptor "Buscar actualizaciones automáticamente" (activado por defecto), enlace al repo y licencias |

## 3. Comportamiento

### A. Comprobar
- **Automático:** al arrancar y cada 24 h. Sin red, con el límite de la API de GitHub (60 peticiones/h) o si GitHub falla, no avisa; lo apunta en el log.
- **Manual:** "Buscar actualizaciones" muestra siempre el resultado: "Estás en la última versión", el diálogo o el error.
- Los drafts y las prereleases no cuentan (`releases/latest` no los devuelve).

### B. Actualizar
1. Descarga el MSI con barra de progreso, cancelable. Al cancelar, se borra el parcial.
2. Descarga `SHA256SUMS` y compara el hash del MSI. Si no coincide, borra el MSI y avisa "La descarga está dañada; inténtalo de nuevo".
3. Lanza `msiexec /i "<msi>" /passive /norestart` y sale de la app por la ruta normal: espera a los trabajos en curso y cierra la base de datos.
4. El MSI no reabre la app. El usuario la abre desde el menú Inicio o el escritorio.

### C. ZIP y desarrollo
- La app instalada es la que corre desde `%LOCALAPPDATA%\Programs\Turboyomi\Turboyomi.exe`.
- Si no corre desde ahí (el ZIP en otra carpeta) o no hay exe (desarrollo), "Actualizar" abre la página de la release en el navegador.
- En desarrollo, la comprobación automática está apagada.

## 4. Errores
| Caso | Comportamiento |
|---|---|
| Sin red, límite de la API o error de GitHub | Silencio en la comprobación automática; mensaje en la manual |
| La release no tiene `.msi` o `SHA256SUMS` | "Sin actualización", y se apunta en el log |
| Hash distinto | Se borra el MSI y se avisa |
| `msiexec` no arranca | Aviso con el error; la app sigue abierta |
| Una descarga ya en curso | "Actualizar" desactivado |
| Falta el secreto `TRACKER_KEYS` en el CI | Compila igual, sin trackers OAuth |
| Faltan los secretos de firma | El paso de firma se salta; la release sale sin firmar |

## 5. Pruebas

### Automáticas (`./gradlew test`)
- **`DesktopReleaseService`, contra `MockWebServer`:** elige el `.msi` y `SHA256SUMS`; sin alguno de los dos, null.
- **Versiones:** `0.10.0 > 0.9.0`; igual no es nueva; acepta `v`; un tag que no es `x.y.z` da "sin actualización".
- **`AppUpdater`:**
  - hash correcto: OK;
  - hash distinto: borra y da error;
  - cancelar: borra el parcial.
- **Lanzador:** el comando con una ruta con espacios y acentos, comprobado sin ejecutar `msiexec`.
- **Instalación:** la detección de MSI frente a ZIP según la ruta del exe.
- **Job:** respeta el interruptor y está apagado en desarrollo.
- **UI (Compose):** el diálogo muestra las notas y "Actualizar" llama al instalador; la búsqueda manual muestra "Estás en la última versión".

### En GitHub (verificación final)
- `build.yml` verde en el primer push.
- `release.yml` con el tag `v0.1.0`, la primera release real. Debe publicar MSI, ZIP y `SHA256SUMS`, y el hash del MSI descargado debe coincidir.
- `compat.yml` lanzado a mano una vez.

### Manual (al usuario)
1. Con la 0.1.0 instalada se publica una `v0.1.1` de prueba. Al abrir Turboyomi, avisa; "Actualizar" instala y la biblioteca sigue ahí.
2. Desde el ZIP, "Actualizar" abre la página de la release.
