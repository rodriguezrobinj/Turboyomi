# Diseño: subproyecto 2b — navegador integrado (Cloudflare, WebView y logins)

- **Fecha:** 2026-10-21
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md` (sección «Cloudflare y WebView»)
- **Spec hermana:** `2026-10-19-subproyecto-2a-ui-lector-design.md` (2b sustituye su comportamiento provisional)
- **Base:** `master` en `8706583da` (subproyecto 2a integrado; 270 tests; harness al 89,5 %)

## 1. Objetivo y alcance

### Objetivo
Que la app tenga un navegador Chromium integrado para:
1. Que carguen las fuentes protegidas por Cloudflare. Hoy fallan 6 en el harness.
2. Que funcionen las extensiones que usan `WebView`. Hoy fallan cubari y mangaup.
3. Abrir una página dentro de la app con "Abrir en WebView".
4. Iniciar sesión en una fuente y que la sesión valga para la app y sobreviva al reinicio.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| Descarga del navegador | Bajo demanda. La primera vez que algo lo necesita se pregunta, con el tamaño real y progreso. La respuesta se recuerda. Ajustes › Avanzado permite instalarlo o borrarlo |
| Desafío interactivo de Cloudflare | Como Mihon: error con "Resolver en WebView". No se abre ninguna ventana sola |
| Navegador visible | Ventana propia: barra Compose más el navegador CEF con ventana en un `SwingPanel` |
| Origen de CEF | JetBrains Runtime con JCEF (`jbr_jcef`), como Suwayomi. KCEF se descarta: se archivó en octubre de 2025 y su README dice que está muy desactualizado |

### Cambios respecto a la spec padre
- La spec padre decía "KCEF, ~100 MB". Ahora es la build `jbr_jcef` de JetBrains Runtime. Pesa **281 MB** en Windows x64 (`.tar.gz`).
- El directorio de instalación sigue siendo `%LOCALAPPDATA%\<App>\cef`.
- Las cookies de OkHttp pasan de memoria a un almacén persistente cifrado. La spec padre no lo trataba, y sin él un login no sobrevive al reinicio.

### Fuera de alcance
| Qué | Motivo o destino |
|---|---|
| Linux y macOS | El port es solo Windows x64 |
| Abrir la ventana del desafío sola | Decidido: no aparecen ventanas desde trabajo en segundo plano |
| Descargas de archivos desde la ventana WebView | No las necesita ningún flujo; se ignoran |
| Proceso aislado para las extensiones | Mejora futura de la spec padre |

## 2. Arquitectura

### Piezas
| Pieza | Módulo | Origen | Responsabilidad |
|---|---|---|---|
| `CefInstaller` | `desktop-core` | Portado de `CEFManager` de Suwayomi (MPL-2.0) | Descargar, verificar, extraer e instalar la build JCEF; desinstalarla; informar del estado y del progreso |
| `CefRuntime` | `desktop-core` | Portado de `CEFManager` de Suwayomi | Cargar los nativos, arrancar y cerrar `CefApp`, publicarla en `CefHelper.cefApp` |
| `PersistentCookieStore` | `desktop-core` | Nuevo | `java.net.CookieStore` en un JSON cifrado con DPAPI; sustituye al almacén en memoria del `CookieHandler` de la JVM |
| `CefCookieSync` | `desktop-core` | Nuevo | Copiar cookies de OkHttp a CEF antes de cargar y de CEF a OkHttp tras cada carga |
| `CloudflareInterceptor` | `core/common` (desktop) | El de Mihon, adaptado | Resolver el desafío en el `WebView` sin ventana de AndroidCompat |
| `WebViewWindow` | `desktop-app` | Nuevo | Ventana con barra y navegador visible |
| `CefConsent` | `desktop-app` | Nuevo | Diálogo de consentimiento y de descarga |
| Sección "Navegador integrado" | `desktop-app` | Nuevo | Estado, instalar, borrar navegador y borrar cookies en Ajustes › Avanzado |

### Versión fijada
- JCEF (API Java): `org.jetbrains.intellij.deps.jcef:jcef:144.0.15-g72717cf-chromium-144.0.7559.172-api-1.21-262-b37`. Ya está en el catálogo.
- Nativos: release `jbr-release-25.0.4b508.27` de JetBrains Runtime, la misma que fija Suwayomi para ese JCEF.
- URL: `https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4-windows-x64-b508.27.tar.gz`.
- El `.checksum` (SHA-512) está en la misma URL con el sufijo `.checksum`.
- La URL es fija: no se consulta la API de GitHub.
- La versión y la URL son constantes del build. Actualizar Chromium consiste en cambiar las dos.

### Integración con lo existente
- `Platform.start` hoy marca `CefHelper.cefApp` como fallo para que todo `WebView` falle rápido. Con 2b:
  - el valor inicial es "no instalado", que sigue fallando rápido;
  - si `CefInstaller` dice que está instalado, `CefRuntime` arranca en segundo plano.
- `NetworkHelper` de escritorio usa el `CloudflareInterceptor` real en lugar del stub que deja pasar todo.
- El `CookieHandler` por defecto de la JVM pasa a usar el `PersistentCookieStore`. `CookieManagerImpl` de AndroidCompat y `AndroidCookieJar` no cambian: los dos ya leen de ese `CookieHandler`.
- `formattedMessage` deja de devolver "Este sitio requiere navegador (disponible pronto)" para 403 y 503. Las pantallas de error reciben las acciones nuevas (sección 3, A).
- Todos los "Abrir en WebView" (manga, explorar, lector, extensiones) llaman a `WebViewLauncher.open(url, sourceId)`. Hoy abren el navegador del sistema.

## 3. Flujos

### A. Cloudflare en segundo plano
1. Llega una respuesta con `cf-mitigated: challenge`. El interceptor crea un `WebView` sin ventana de AndroidCompat:
   - el User-Agent es el de la petición;
   - antes de cargar, `CefCookieSync` copia a CEF las cookies de OkHttp para ese sitio.
2. Al terminar cada carga, `CefCookieSync` copia a OkHttp las cookies de CEF para esa URL. El interceptor espera a que `cf_clearance` cambie, como en Mihon, y repite la petición.
3. Si la página avisa `interactiveBegin`, o si se agota el tiempo de Mihon, la petición falla con `information_cloudflare_bypass_failure` ("No se pudo saltar Cloudflare"). La pantalla muestra:
   - el error;
   - "Resolver en WebView", que abre la ventana en la URL de la fuente;
   - "Reintentar".
4. Si CEF no está instalado, la petición falla con `CefNotInstalledException` (un `IOException`). La pantalla muestra:
   - "Hace falta el navegador integrado";
   - "Instalar navegador integrado", que abre el diálogo de consentimiento.
5. Si CEF está instalado pero no arrancó, el error es "No se pudo iniciar el navegador integrado" y lleva su motivo al log.
6. Nunca se abre un diálogo ni una ventana desde un hilo de fondo: solo cuando el usuario pulsa algo.

### B. Extensiones que usan `WebView`
Siguen el mismo camino que A. AndroidCompat crea el `WebView` sobre CEF sin ventana, y los errores de "no instalado" o "no arrancó" son los mismos.

### C. "Abrir en WebView" y logins
1. Si CEF no está instalado, aparece el diálogo de consentimiento. Tras instalar, la ventana se abre sola con la URL pedida.
2. Antes de cargar, se copian a CEF las cookies de OkHttp del sitio.
3. El User-Agent es el de las cabeceras de la fuente, como en `WebViewActivity` de Mihon.
4. Tras cada carga, y al cerrar la ventana, se copian a OkHttp las cookies de CEF.
5. Un login de una fuente es abrir su web y entrar. La sesión queda en el `PersistentCookieStore`.

### D. Descarga
1. El diálogo muestra el tamaño (Content-Length de la descarga), "Descargar" y "Ahora no".
2. Durante la descarga hay barra de progreso y "Cancelar".
3. Se verifica el SHA-512, se extrae y se instala. Después `CefRuntime` arranca sin reiniciar la app.
4. "Ahora no" se guarda y no se vuelve a preguntar solo. "Instalar navegador integrado" y Ajustes › Avanzado siguen disponibles.

### E. Ventana WebView
- Barra Compose con atrás, adelante, recargar, título y URL, "Abrir en el navegador del sistema" y un menú con "Borrar cookies de este sitio".
- Una sola ventana a la vez: abrir otra URL la reutiliza y la trae al frente.
- Navegación:
  - `window.open` y los enlaces con `target=_blank` se abren en la misma ventana;
  - las descargas de archivos se ignoran;
  - los protocolos externos (`mailto:`, apps) se ignoran.
- Al cerrarla se liberan su navegador CEF y su cliente.

## 4. Errores y seguridad

### Instalación
- Solo HTTPS.
- Se verifica el SHA-512 del `.checksum`. Si no coincide, se borra la descarga y se informa del error.
- Se descarga y extrae en una carpeta temporal dentro de `%LOCALAPPDATA%\<App>`. Al terminar, se renombra a `cef`. Una instalación a medias nunca cuenta como instalada.
- La extracción rechaza las entradas cuya ruta sale del destino.
- La instalación es válida si el fichero `release` declara la versión JCEF que espera la app. Si no la declara (por ejemplo, tras actualizar la app), se trata como no instalada y se vuelve a ofrecer.
- Sin red o con el disco lleno: mensaje claro con "Reintentar". El detalle va al log.

### Arranque
- Se cargan `chrome_elf`, `libcef` y `jcef`.
- `CefApp` arranca con:
  - `windowless_rendering_enabled = true`, porque lo necesita el `WebView` de AndroidCompat (el modo con ventana sigue disponible);
  - la caché en `%LOCALAPPDATA%\<App>\cache\cef`;
  - los argumentos de Suwayomi que aplican a Windows.
- Si falla, `CefHelper.cefApp` queda en fallo con la causa, y todo lo que necesite CEF falla rápido sin bloquear el looper principal, como hoy.
- Al salir de la app, `CefApp` se cierra desde `ExitHooks`.

### Seguridad
- El archivo de cookies contiene sesiones de login. Se cifra con DPAPI (`Crypt32Util` de `jna-platform`, previsto en la spec padre), así que solo lo descifra el mismo usuario de Windows.
- Si el archivo no se puede descifrar (otro usuario, archivo dañado), se empieza con cookies vacías y se registra en el log. La app no falla.
- El puente JavaScript solo existe en el `WebView` del interceptor, como en Mihon. La ventana WebView no expone nada al JavaScript de la página.
- "Borrar cookies" borra a la vez las de CEF y las de OkHttp:
  - en la ventana, las de ese sitio;
  - en Ajustes › Avanzado, todas.

### Rendimiento
- Cada desafío usa un navegador sin ventana que se cierra al terminar.
- Los desafíos de un mismo host se serializan, como en Mihon.

## 5. Testing y aceptación

### Tests unitarios (en `./gradlew test`, sin CEF real)
| Qué | Casos |
|---|---|
| `CefInstaller` (MockWebServer con un `.tar.gz` sintético) | Instala y deja `release` válido; checksum erróneo → nada instalado; entrada con `../` rechazada; descarga cortada → no instalado; versión distinta en `release` → no instalado; progreso hasta el 100 %; cancelar; desinstalar |
| `PersistentCookieStore` | Otra instancia lee lo guardado; el archivo no contiene el valor en claro; caducadas descartadas; archivo ilegible → vacío sin fallar; borrar todo y por sitio |
| Conversión `HttpCookie` ⇄ `CefCookie` | Dominio, ruta, caducidad, `Secure` y `HttpOnly` de ida y vuelta |
| Sin CEF | El error de Cloudflare y el de `WebView` son `CefNotInstalledException`; la pantalla ofrece "Instalar navegador integrado" |
| UI (`runComposeUiTest`) | Diálogo de consentimiento con un instalador falso: tamaño, progreso, cancelar, "Ahora no" recordado; error de Cloudflare con "Resolver en WebView"; sección de Ajustes › Avanzado |

### Tests de integración con CEF real
- Llevan la etiqueta `cef` y corren en una tarea aparte, `./gradlew cefTest`, que descarga CEF una vez a una caché.
- CI la ejecuta en `windows-latest` con la descarga cacheada.

| Qué | Casos |
|---|---|
| `CefRuntime` | Arranca y se cierra limpio |
| `WebView` de AndroidCompat | Carga una página local que pone una cookie por JavaScript, y la cookie llega a OkHttp |
| `CloudflareInterceptor` | Desafío simulado: 403 con `cf-mitigated: challenge`, la página pone `cf_clearance` y luego responde 200. La petición original termina en 200 |

### Harness
- Opción nueva `--cef`: usa CEF de la caché o lo instala.
- Objetivos:
  - pasan las 6 fuentes que daban 403 de Cloudflare, cubari y mangaup, salvo las que exijan un desafío interactivo (se anotan);
  - el total no baja del 89,5 %.

### Prueba manual (última tarea del plan)
1. Con `MANGAREADER_HOME` en una carpeta nueva, primer "Abrir en WebView": diálogo con 281 MB, descarga con progreso, y la ventana se abre.
2. Una fuente protegida por Cloudflare carga sin intervenir.
3. Un desafío interactivo: "Resolver en WebView", se resuelve en la ventana, y "Reintentar" funciona.
4. Iniciar sesión en una fuente, cerrar la app y reabrirla: la sesión sigue.
5. "Borrar cookies" cierra la sesión.
6. Borrar el navegador en Ajustes: el siguiente "Abrir en WebView" vuelve a ofrecer la descarga.

### Criterios de aceptación
- Se cumplen todos los pasos de la prueba manual.
- La suite, `cefTest`, el harness (objetivos de arriba), el chequeo de licencias y spotless están en verde.
- Ningún archivo nuevo de `desktop-app` importa `android.*` salvo a través de los shims permitidos.

## 6. Riesgos
| Riesgo | Mitigación |
|---|---|
| Los nativos de JBR 25 no cargan en nuestro runtime (JDK 21) | Primera tarea del plan: arrancar `CefApp` con los nativos descargados en el JDK de la app. Si fallan, se usa el runtime de la build JBR descargada para CEF o se cambia el JDK de la app, y se anota como ruling |
| El modo con ventana de JCEF no se integra bien en `SwingPanel` de Compose | Comprobarlo en la misma primera tarea. Si falla, la ventana WebView pasa a ser un `JFrame` Swing con barra Swing |
| El `CloudflareInterceptor` de Mihon usa `androidx.webkit` (`WebViewCompat`, `WebViewFeature`) | Se usa su rama sin mundo aislado (`addJavascriptInterface`), que AndroidCompat soporta; lo demás se marca con `// desktop:` |
| 281 MB es mucha descarga | El diálogo lo dice antes; nada se descarga sin consentimiento |
| Licencias: Chromium, CEF y JBR | BSD/GPL-2.0-with-classpath-exception (JBR) y licencias de Chromium. Se descargan aparte, no se redistribuyen en el instalador. El chequeo de licencias revisa solo las dependencias del build; la sección "Navegador integrado" de Ajustes › Avanzado enlaza a sus licencias |
